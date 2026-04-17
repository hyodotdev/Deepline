import styled from '@emotion/native';
import type {ReactNode} from 'react';
import {ActivityIndicator, Pressable, Text, View} from 'react-native';

import {useAppTheme} from '@/theme/theme';

type Props = {
  disabled?: boolean;
  loading?: boolean;
  onPress?: () => void | Promise<void>;
  secondary?: boolean;
  text: string;
  trailing?: ReactNode;
};

export function PrimaryButton({
  disabled,
  loading,
  onPress,
  secondary,
  text,
  trailing,
}: Props) {
  const theme = useAppTheme();

  return (
    <ButtonPressable
      disabled={disabled || loading}
      onPress={onPress}
      secondary={secondary}
      style={({pressed}) => ({opacity: pressed ? 0.85 : 1})}
    >
      <ButtonContent>
        {loading ? (
          <ActivityIndicator
            color={secondary ? theme.color.text : theme.color.textInverse}
          />
        ) : (
          <>
            <ButtonText secondary={secondary}>{text}</ButtonText>
            {trailing}
          </>
        )}
      </ButtonContent>
    </ButtonPressable>
  );
}

const ButtonPressable = styled(Pressable)<{secondary?: boolean}>`
  min-height: 54px;
  border-radius: ${({theme}) => theme.radius.md}px;
  align-items: center;
  justify-content: center;
  background-color: ${({theme, secondary}) =>
    secondary ? theme.color.surfaceMuted : theme.color.primary};
  border-width: ${({secondary}) => (secondary ? 1 : 0)}px;
  border-color: ${({theme}) => theme.color.borderStrong};
  padding: 0 ${({theme}) => theme.spacing.lg}px;
`;

const ButtonContent = styled(View)`
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const ButtonText = styled(Text)<{secondary?: boolean}>`
  color: ${({theme, secondary}) =>
    secondary ? theme.color.text : theme.color.textInverse};
  font-size: 16px;
  font-weight: 700;
`;
