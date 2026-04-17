import styled from '@emotion/native';
import type {PropsWithChildren, ReactNode} from 'react';
import {KeyboardAvoidingView, Platform, ScrollView, View} from 'react-native';
import {SafeAreaView, useSafeAreaInsets} from 'react-native-safe-area-context';

type Props = PropsWithChildren<{
  footer?: ReactNode;
  scroll?: boolean;
}>;

export function Screen({children, footer, scroll = false}: Props) {
  const {bottom} = useSafeAreaInsets();
  const content = scroll ? (
    <Scrollable
      contentContainerStyle={{
        flexGrow: 1,
        paddingBottom: 24 + bottom,
      }}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </Scrollable>
  ) : (
    <Body>{children}</Body>
  );

  return (
    <Container edges={['top', 'left', 'right']}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{flex: 1}}
      >
        {content}
        {footer ? <Footer style={{paddingBottom: 16 + bottom}}>{footer}</Footer> : null}
      </KeyboardAvoidingView>
    </Container>
  );
}

export const SectionCard = styled(View)`
  border-width: 1px;
  border-color: ${({theme}) => theme.color.border};
  background-color: ${({theme}) => theme.color.surface};
  border-radius: ${({theme}) => theme.radius.lg}px;
  padding: ${({theme}) => theme.spacing.lg}px;
  gap: ${({theme}) => theme.spacing.sm}px;
`;

export const SectionTitle = styled.Text`
  color: ${({theme}) => theme.color.text};
  font-size: 18px;
  font-weight: 700;
`;

export const SectionBody = styled.Text`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
`;

const Container = styled(SafeAreaView)`
  flex: 1;
  background-color: ${({theme}) => theme.color.background};
`;

const Scrollable = styled(ScrollView)`
  flex: 1;
  padding: ${({theme}) => theme.spacing.lg}px;
`;

const Body = styled(View)`
  flex: 1;
  padding: ${({theme}) => theme.spacing.lg}px;
  gap: ${({theme}) => theme.spacing.lg}px;
`;

const Footer = styled(View)`
  padding: ${({theme}) => theme.spacing.lg}px;
  padding-top: 0;
  background-color: ${({theme}) => theme.color.background};
`;
