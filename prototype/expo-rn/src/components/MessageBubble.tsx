import styled from '@emotion/native';
import {Pressable, Text, View} from 'react-native';

type AttachmentChip = {
  detail: string;
  id: string;
  label: string;
  loading?: boolean;
  onPress?: () => void;
};

type Props = {
  attachments?: AttachmentChip[];
  isMine: boolean;
  receipt?: string;
  text: string;
};

export function MessageBubble({attachments, isMine, receipt, text}: Props) {
  return (
    <Wrap isMine={isMine}>
      <Bubble isMine={isMine}>
        {text ? <BubbleText isMine={isMine}>{text}</BubbleText> : null}
        {attachments?.length ? (
          <AttachmentStack>
            {attachments.map((attachment) => (
              <AttachmentCard
                disabled={!attachment.onPress}
                isMine={isMine}
                key={attachment.id}
                onPress={attachment.onPress}
              >
                <AttachmentLabel isMine={isMine}>
                  {attachment.loading ? 'Decrypting…' : attachment.label}
                </AttachmentLabel>
                <AttachmentDetail isMine={isMine}>
                  {attachment.loading ? 'Preparing local file' : attachment.detail}
                </AttachmentDetail>
              </AttachmentCard>
            ))}
          </AttachmentStack>
        ) : null}
      </Bubble>
      {receipt ? <Receipt isMine={isMine}>{receipt}</Receipt> : null}
    </Wrap>
  );
}

const Wrap = styled(View)<{isMine: boolean}>`
  align-items: ${({isMine}) => (isMine ? 'flex-end' : 'flex-start')};
  gap: 6px;
`;

const Bubble = styled(View)<{isMine: boolean}>`
  max-width: 82%;
  border-radius: ${({theme}) => theme.radius.lg}px;
  padding: ${({theme}) => theme.spacing.md}px;
  background-color: ${({theme, isMine}) =>
    isMine ? theme.color.surfaceStrong : theme.color.surfaceMuted};
`;

const BubbleText = styled(Text)<{isMine: boolean}>`
  color: ${({theme, isMine}) =>
    isMine ? theme.color.textInverse : theme.color.text};
  font-size: 16px;
  line-height: 22px;
`;

const AttachmentStack = styled(View)`
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const AttachmentCard = styled(Pressable)<{isMine: boolean}>`
  padding: ${({theme}) => theme.spacing.md}px;
  border-radius: ${({theme}) => theme.radius.md}px;
  background-color: ${({theme, isMine}) =>
    isMine ? 'rgba(255,255,255,0.12)' : theme.color.surface};
  border-width: 1px;
  border-color: ${({theme, isMine}) =>
    isMine ? 'rgba(255,255,255,0.22)' : theme.color.border};
`;

const AttachmentLabel = styled(Text)<{isMine: boolean}>`
  color: ${({theme, isMine}) =>
    isMine ? theme.color.textInverse : theme.color.text};
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 4px;
`;

const AttachmentDetail = styled(Text)<{isMine: boolean}>`
  color: ${({theme, isMine}) =>
    isMine ? 'rgba(244,250,255,0.82)' : theme.color.textMuted};
  font-size: 12px;
  line-height: 18px;
`;

const Receipt = styled(Text)<{isMine: boolean}>`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 12px;
  padding: 0 4px;
`;
