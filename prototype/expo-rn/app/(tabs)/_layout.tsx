import {useTheme} from '@emotion/react';
import {Ionicons} from '@expo/vector-icons';
import {Tabs} from 'expo-router';
import {View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

import type {AppTheme} from '@/theme/theme';

export default function TabsLayout() {
  const theme = useTheme() as AppTheme;
  const {bottom} = useSafeAreaInsets();

  return (
    <View style={{flex: 1, backgroundColor: theme.color.background}}>
      <Tabs
        safeAreaInsets={{bottom: 0}}
        screenOptions={{
          headerShadowVisible: false,
          headerStyle: {backgroundColor: theme.color.background},
          headerTintColor: theme.color.text,
          tabBarActiveTintColor: theme.color.primary,
          tabBarInactiveTintColor: theme.color.textMuted,
          tabBarStyle: {
            backgroundColor: theme.color.surface,
            borderTopColor: theme.color.border,
            height: 58 + bottom,
            paddingBottom: Math.max(bottom, 10),
            paddingTop: 6,
          },
        }}
      >
        <Tabs.Screen
          name="chats"
          options={{
            title: 'Chats',
            tabBarIcon: ({color, size}) => (
              <Ionicons color={color} name="chatbubbles-outline" size={size} />
            ),
          }}
        />
        <Tabs.Screen
          name="settings"
          options={{
            title: 'Settings',
            tabBarIcon: ({color, size}) => (
              <Ionicons color={color} name="shield-checkmark-outline" size={size} />
            ),
          }}
        />
      </Tabs>
    </View>
  );
}
