import styled from '@emotion/native';
import {useQuery} from 'convex/react';
import {useRouter} from 'expo-router';
import {Text, View} from 'react-native';

import {ConversationRow} from '@/components/ConversationRow';
import {PrimaryButton} from '@/components/PrimaryButton';
import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {buildSessionAuth, getOneToOneCryptoProvider} from '@/crypto';
import {maskId} from '@/lib/security';
import {useSessionStore} from '@/stores/sessionStore';
import {api} from '../../convex/_generated/api';

export default function ChatsScreen() {
  const router = useRouter();
  const identity = useSessionStore((state) => state.identity);
  const aliases = useSessionStore((state) => state.contactAliases);
  const provider = getOneToOneCryptoProvider();
  const auth = identity?.userId && identity.sessionProof ? buildSessionAuth(identity) : null;
  const conversations = useQuery(
    api.messenger.listConversations,
    auth ? {auth} : 'skip',
  );
  const visibleConversations = (conversations ?? []).filter(
    Boolean,
  ) as NonNullable<NonNullable<typeof conversations>[number]>[];

  const content =
    visibleConversations.length > 0 ? (
      visibleConversations.map((conversation) => {
        const counterpartUserId = conversation.counterpartUserIds[0];
        const title =
          aliases[counterpartUserId] ?? `Contact ${maskId(counterpartUserId, 5)}`;

        return (
          <ConversationRow
            key={conversation._id}
            onPress={() =>
              router.push({
                pathname: '/chat/[conversationId]',
                params: {
                  conversationId: conversation._id,
                  peerLabel: title,
                  peerUserId: counterpartUserId,
                },
              })
            }
            subtitle={
              conversation.latestMessage
                ? 'Encrypted message available on device'
                : 'Conversation ready for first message'
            }
            title={title}
            unreadCount={conversation.unreadCount}
          />
        );
      })
    ) : (
      <SectionCard>
        <SectionTitle>No chats yet</SectionTitle>
        <SectionBody>
          Generate an invite QR, paste a friend code, then start a 1:1 encrypted
          thread.
        </SectionBody>
        <PrimaryButton
          onPress={() => router.push('/new-chat')}
          secondary
          text="Add your first friend"
        />
      </SectionCard>
    );

  return (
    <Screen
      scroll
      footer={<PrimaryButton onPress={() => router.push('/new-chat')} text="New chat" />}
    >
      <Header>
        <View style={{gap: 10}}>
          <Title>Chats</Title>
          <Subtitle>
            Convex stores only ciphertext. Decryption happens with keys from this
            device.
          </Subtitle>
        </View>
        <StatusPill
          text={provider.isProductionReady ? 'Signal ready' : 'Demo crypto only'}
          tone={provider.isProductionReady ? 'success' : 'warning'}
        />
      </Header>
      <ConversationStack>{content}</ConversationStack>
    </Screen>
  );
}

const Header = styled(View)`
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const Title = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 30px;
  font-weight: 800;
`;

const Subtitle = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
`;

const ConversationStack = styled(View)`
  gap: ${({theme}) => theme.spacing.md}px;
`;
