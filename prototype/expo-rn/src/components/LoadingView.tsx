import styled from '@emotion/native';
import {ActivityIndicator, Text, View} from 'react-native';

import {useAppTheme} from '@/theme/theme';

export function LoadingView({label}: {label: string}) {
  const theme = useAppTheme();

  return (
    <Wrap>
      <ActivityIndicator color={theme.color.primary} />
      <Label>{label}</Label>
    </Wrap>
  );
}

const Wrap = styled(View)`
  flex: 1;
  align-items: center;
  justify-content: center;
  background-color: ${({theme}) => theme.color.background};
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const Label = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
`;
