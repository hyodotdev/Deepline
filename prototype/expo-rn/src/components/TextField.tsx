import styled from '@emotion/native';
import type {ComponentProps} from 'react';
import {Text, TextInput, View} from 'react-native';

import {useAppTheme} from '@/theme/theme';

type Props = ComponentProps<typeof TextInput> & {
  hint?: string;
  label: string;
};

export function TextField({hint, label, ...props}: Props) {
  const theme = useAppTheme();

  return (
    <Wrap>
      <Label>{label}</Label>
      <Input {...props} placeholderTextColor={theme.color.placeholder} />
      {hint ? <Hint>{hint}</Hint> : null}
    </Wrap>
  );
}

const Wrap = styled(View)`
  gap: ${({theme}) => theme.spacing.xs}px;
`;

const Label = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 14px;
  font-weight: 600;
`;

const Input = styled(TextInput)`
  min-height: 52px;
  border-radius: ${({theme}) => theme.radius.md}px;
  border-width: 1px;
  border-color: ${({theme}) => theme.color.borderStrong};
  background-color: ${({theme}) => theme.color.surface};
  padding: 0 ${({theme}) => theme.spacing.lg}px;
  color: ${({theme}) => theme.color.text};
  font-size: 16px;
`;

const Hint = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 13px;
  line-height: 18px;
`;
