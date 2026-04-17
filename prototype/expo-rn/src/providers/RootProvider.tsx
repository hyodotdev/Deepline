import {ThemeProvider} from '@emotion/react';
import styled from '@emotion/native';
import {StatusBar} from 'expo-status-bar';
import type {PropsWithChildren} from 'react';
import ErrorBoundary from 'react-native-error-boundary';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {Text, useColorScheme, View} from 'react-native';

import {darkTheme, lightTheme} from '@/theme/theme';

function FallbackComponent() {
  return (
    <FallbackWrap>
      <FallbackTitle>Deepline hit an unexpected error.</FallbackTitle>
      <FallbackBody>
        Restart the app after checking the current screen state.
      </FallbackBody>
    </FallbackWrap>
  );
}

export default function RootProvider({
  children,
}: PropsWithChildren) {
  const colorScheme = useColorScheme();
  const theme = colorScheme === 'dark' ? darkTheme : lightTheme;

  return (
    <ThemeProvider theme={theme}>
      <SafeAreaProvider>
        <StatusBar style={theme.statusBarStyle} />
        <ErrorBoundary FallbackComponent={FallbackComponent}>
          {children}
        </ErrorBoundary>
      </SafeAreaProvider>
    </ThemeProvider>
  );
}

const FallbackWrap = styled(View)`
  flex: 1;
  justify-content: center;
  align-items: center;
  padding: 24px;
  background-color: ${({theme}) => theme.color.background};
  gap: 8px;
`;

const FallbackTitle = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 22px;
  font-weight: 700;
  text-align: center;
`;

const FallbackBody = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
  text-align: center;
`;
