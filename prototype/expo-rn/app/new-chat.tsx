import styled from '@emotion/native';
import * as Clipboard from 'expo-clipboard';
import {useMutation} from 'convex/react';
import {useRouter} from 'expo-router';
import QRCode from 'react-native-qrcode-svg';
import {useEffect, useState} from 'react';
import {Text, View} from 'react-native';

import {PrimaryButton} from '@/components/PrimaryButton';
import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {TextField} from '@/components/TextField';
import {
  buildMutationAuth,
  getOneToOneCryptoProvider,
} from '@/crypto';
import {maskId} from '@/lib/security';
import {useSessionStore} from '@/stores/sessionStore';
import {useAppTheme} from '@/theme/theme';
import {api} from '../convex/_generated/api';

export default function NewChatScreen() {
  const router = useRouter();
  const theme = useAppTheme();
  const identity = useSessionStore((state) => state.identity);
  const lastInviteCode = useSessionStore((state) => state.lastInviteCode);
  const lastInviteCodeCreatedAt = useSessionStore(
    (state) => state.lastInviteCodeCreatedAt,
  );
  const setInviteCode = useSessionStore((state) => state.setInviteCode);
  const setContactAlias = useSessionStore((state) => state.setContactAlias);
  const provider = getOneToOneCryptoProvider();

  const [friendCode, setFriendCode] = useState('');
  const [friendAlias, setFriendAlias] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createInviteCode = useMutation(api.messenger.createInviteCode);
  const addContactByInviteCode = useMutation(api.messenger.addContactByInviteCode);
  const createConversation = useMutation(api.messenger.createConversation);

  useEffect(() => {
    if (!identity?.userId || !identity.sessionProof || lastInviteCode) {
      return;
    }

    void (async () => {
      const payload = {
        userId: identity.userId,
      };

      const result = await createInviteCode({
        auth: buildMutationAuth(identity, 'createInviteCode', payload),
      });

      await setInviteCode(result.inviteCode);
    })();
  }, [createInviteCode, identity, lastInviteCode, setInviteCode]);

  const handleCopy = async () => {
    if (!lastInviteCode) {
      return;
    }

    await Clipboard.setStringAsync(lastInviteCode);
  };

  const handleAdd = async () => {
    if (!identity?.userId || !identity.sessionProof || !friendCode.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const addPayload = {
        inviteCode: friendCode.trim(),
        localAliasCiphertext: null,
      };
      const contact = await addContactByInviteCode({
        auth: buildMutationAuth(identity, 'addContactByInviteCode', addPayload),
        inviteCode: friendCode.trim(),
      });

      if (friendAlias.trim()) {
        await setContactAlias(contact.contactUserId, friendAlias.trim());
      }

      const conversationPayload = {
        encryptedTitle: null,
        participantUserIds: [contact.contactUserId],
        protocolType: provider.protocolType,
      };

      const conversation = await createConversation({
        auth: buildMutationAuth(
          identity,
          'createConversation',
          conversationPayload,
        ),
        participantUserIds: [contact.contactUserId],
        protocolType: provider.protocolType,
      });

      router.replace({
        pathname: '/chat/[conversationId]',
        params: {
          conversationId: conversation?._id ?? '',
          peerLabel:
            friendAlias.trim() || `Contact ${maskId(contact.contactUserId, 5)}`,
          peerUserId: contact.contactUserId,
        },
      });
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to add the friend by invite code.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const inviteAgeLabel =
    lastInviteCodeCreatedAt &&
    Date.now() - lastInviteCodeCreatedAt > 1000 * 60 * 60 * 24
      ? 'older than one day'
      : 'fresh';

  return (
    <Screen
      scroll
      footer={
        <PrimaryButton
          loading={submitting}
          onPress={handleAdd}
          text="Add friend and start chat"
        />
      }
    >
      <Header>
        <Title>Invite or paste a friend code</Title>
        <Subtitle>
          Share the QR directly, or paste the same invite code manually. Deepline
          does not upload raw contacts.
        </Subtitle>
      </Header>

      <SectionCard>
        <SectionTitle>Your current invite</SectionTitle>
        {lastInviteCode ? (
          <>
            <StatusPill text={`Invite is ${inviteAgeLabel}`} />
            <QrWrap>
              <QRCode
                backgroundColor={theme.color.qrBackground}
                color={theme.color.qrForeground}
                size={180}
                value={lastInviteCode}
              />
            </QrWrap>
            <InviteCode selectable>{lastInviteCode}</InviteCode>
            <PrimaryButton onPress={handleCopy} secondary text="Copy invite code" />
          </>
        ) : (
          <SectionBody>Generating a fresh invite code for this device…</SectionBody>
        )}
      </SectionCard>

      <SectionCard>
        <SectionTitle>Add by code</SectionTitle>
        <TextField
          autoCapitalize="characters"
          label="Friend invite code"
          onChangeText={setFriendCode}
          placeholder="DL-ABC123DEF456"
          value={friendCode}
        />
        <TextField
          autoCapitalize="words"
          hint="Stored locally only for your own chat list."
          label="Local alias"
          onChangeText={setFriendAlias}
          placeholder="Jisoo"
          value={friendAlias}
        />
        <StatusPill text="QR scanning UI is still a TODO" tone="warning" />
      </SectionCard>

      {error ? <ErrorText>{error}</ErrorText> : null}
    </Screen>
  );
}

const Header = styled(View)`
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const Title = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 28px;
  font-weight: 800;
`;

const Subtitle = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
`;

const QrWrap = styled(View)`
  align-items: center;
  justify-content: center;
  padding: ${({theme}) => theme.spacing.md}px 0;
`;

const InviteCode = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.6px;
`;

const ErrorText = styled(Text)`
  color: ${({theme}) => theme.color.danger};
  font-size: 14px;
  line-height: 20px;
`;
