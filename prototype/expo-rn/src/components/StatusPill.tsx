import styled from '@emotion/native';

import {useAppTheme} from '@/theme/theme';

export function StatusPill({
  text,
  tone = 'default',
}: {
  text: string;
  tone?: 'default' | 'danger' | 'success' | 'warning';
}) {
  const theme = useAppTheme();
  const toneMap = {
    danger: {
      background: theme.color.pillDangerBg,
      color: theme.color.pillDangerText,
    },
    default: {
      background: theme.color.pillDefaultBg,
      color: theme.color.pillDefaultText,
    },
    success: {
      background: theme.color.pillSuccessBg,
      color: theme.color.pillSuccessText,
    },
    warning: {
      background: theme.color.pillWarningBg,
      color: theme.color.pillWarningText,
    },
  } as const;

  return (
    <Wrap background={toneMap[tone].background}>
      <Label color={toneMap[tone].color}>{text}</Label>
    </Wrap>
  );
}

const Wrap = styled.View<{background: string}>`
  align-self: flex-start;
  padding: 6px 10px;
  border-radius: ${({theme}) => theme.radius.pill}px;
  background-color: ${({background}) => background};
`;

const Label = styled.Text<{color: string}>`
  color: ${({color}) => color};
  font-size: 12px;
  font-weight: 700;
`;
