import {useTheme} from '@emotion/react';
import 'react-native-get-random-values';

import Constants from 'expo-constants';
import {Stack} from 'expo-router';
import {ConvexProvider, ConvexReactClient} from 'convex/react';
import {useEffect} from 'react';
import {GestureHandlerRootView} from 'react-native-gesture-handler';

import {LoadingView} from '@/components/LoadingView';
import {ProductionBlockedView} from '@/components/ProductionBlockedView';
import {getOneToOneCryptoProvider} from '@/crypto';
import RootProvider from '@/providers/RootProvider';
import {isProductionCryptoBlocked} from '@/lib/runtime';
import {useSessionStore} from '@/stores/sessionStore';
import type {AppTheme} from '@/theme/theme';

const convexUrl =
  process.env.EXPO_PUBLIC_CONVEX_URL ??
  String(Constants.expoConfig?.extra?.convexUrl ?? '');

const convexClient = convexUrl
  ? new ConvexReactClient(convexUrl, {
      unsavedChangesWarning: false,
    })
  : null;

function MissingConvexConfig() {
  return <LoadingView label="Set EXPO_PUBLIC_CONVEX_URL to boot Deepline." />;
}

function ThemedAppShell() {
  const theme = useTheme() as AppTheme;
  const provider = getOneToOneCryptoProvider();

  if (isProductionCryptoBlocked(provider.isProductionReady)) {
    return <ProductionBlockedView />;
  }

  return (
    <ConvexProvider client={convexClient!}>
      <Stack
        screenOptions={{
          contentStyle: {backgroundColor: theme.color.background},
          headerShadowVisible: false,
          headerStyle: {backgroundColor: theme.color.background},
          headerTintColor: theme.color.text,
          headerTitleStyle: {
            color: theme.color.text,
            fontWeight: '700',
          },
        }}
      >
        <Stack.Screen name="index" options={{headerShown: false}} />
        <Stack.Screen name="welcome" options={{headerShown: false}} />
        <Stack.Screen
          name="local-identity"
          options={{title: 'Local Identity'}}
        />
        <Stack.Screen name="(tabs)" options={{headerShown: false}} />
        <Stack.Screen name="new-chat" options={{title: 'Add Friend'}} />
        <Stack.Screen name="devices" options={{title: 'Devices & Safety'}} />
        <Stack.Screen
          name="production-blocked"
          options={{title: 'Production Blocked'}}
        />
        <Stack.Screen
          name="chat/[conversationId]"
          options={{title: 'Encrypted Chat'}}
        />
      </Stack>
    </ConvexProvider>
  );
}

export default function RootLayout() {
  const hydrate = useSessionStore((state) => state.hydrate);
  const hydrated = useSessionStore((state) => state.hydrated);

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  return (
    <GestureHandlerRootView style={{flex: 1}}>
      <RootProvider>
        {!convexClient ? (
          <MissingConvexConfig />
        ) : !hydrated ? (
          <LoadingView label="Loading local identity…" />
        ) : (
          <ThemedAppShell />
        )}
      </RootProvider>
    </GestureHandlerRootView>
  );
}
