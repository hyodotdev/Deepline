import styled from '@emotion/native';
import {Pressable, Text, View} from 'react-native';

import {StatusPill} from './StatusPill';

type Props = {
  onPress: () => void;
  subtitle: string;
  title: string;
  unreadCount: number;
};

export function ConversationRow({
  onPress,
  subtitle,
  title,
  unreadCount,
}: Props) {
  return (
    <Row onPress={onPress} style={({pressed}) => ({opacity: pressed ? 0.86 : 1})}>
      <TextWrap>
        <Title>{title}</Title>
        <Subtitle numberOfLines={2}>{subtitle}</Subtitle>
      </TextWrap>
      <Meta>
        {unreadCount > 0 ? (
          <StatusPill text={`${unreadCount} unread`} tone="success" />
        ) : (
          <StatusPill text="Synced" />
        )}
      </Meta>
    </Row>
  );
}

const Row = styled(Pressable)`
  flex-direction: row;
  align-items: flex-start;
  gap: ${({theme}) => theme.spacing.md}px;
  border-width: 1px;
  border-color: ${({theme}) => theme.color.border};
  background-color: ${({theme}) => theme.color.surface};
  border-radius: ${({theme}) => theme.radius.lg}px;
  padding: ${({theme}) => theme.spacing.lg}px;
`;

const TextWrap = styled(View)`
  flex: 1;
  gap: ${({theme}) => theme.spacing.xs}px;
`;

const Title = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 18px;
  font-weight: 700;
`;

const Subtitle = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 14px;
  line-height: 20px;
`;

const Meta = styled(View)`
  padding-top: 2px;
`;
