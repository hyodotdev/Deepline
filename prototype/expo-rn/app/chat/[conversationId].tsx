import styled from '@emotion/native';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import {Ionicons} from '@expo/vector-icons';
import {useHeaderHeight} from '@react-navigation/elements';
import {useMutation, useQuery} from 'convex/react';
import {useLocalSearchParams} from 'expo-router';
import {useEffect, useRef, useState} from 'react';
import {
  Alert,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

import type {Id} from '../../convex/_generated/dataModel';
import {LoadingView} from '@/components/LoadingView';
import {MessageBubble} from '@/components/MessageBubble';
import {
  buildMutationAuth,
  buildSessionAuth,
  getFileCryptoProvider,
  getOneToOneCryptoProvider,
} from '@/crypto';
import {uploadEncryptedFile, downloadEncryptedFile, buildDecryptedAttachmentPath} from '@/lib/fileTransfer';
import {
  deserializeMessageEnvelope,
  serializeMessageEnvelope,
  type AttachmentEnvelope,
} from '@/lib/messagePayload';
import {useSessionStore} from '@/stores/sessionStore';
import {useAppTheme} from '@/theme/theme';
import {api} from '../../convex/_generated/api';

type RouteParams = {
  conversationId: string;
  peerLabel?: string;
  peerUserId?: string;
};

type AttachmentDownloadState = {
  localUri?: string;
  status: 'downloading' | 'error' | 'ready';
};

type DecryptedMessage = {
  attachments: AttachmentEnvelope[];
  id: string;
  isMine: boolean;
  receipt?: string;
  text: string;
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unitIndex = 0;

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }

  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unitIndex]}`;
}

function attachmentStatusLabel(
  attachment: AttachmentEnvelope,
  state?: AttachmentDownloadState,
): string {
  if (state?.status === 'downloading') {
    return 'Decrypting locally';
  }

  if (state?.status === 'ready') {
    return `Saved locally • ${formatFileSize(attachment.plaintextByteLength)}`;
  }

  if (state?.status === 'error') {
    return `Retry download • ${formatFileSize(attachment.plaintextByteLength)}`;
  }

  return `Tap to decrypt • ${formatFileSize(attachment.plaintextByteLength)}`;
}

export default function ChatRoomScreen() {
  const {conversationId, peerLabel, peerUserId} = useLocalSearchParams<RouteParams>();
  const theme = useAppTheme();
  const {bottom} = useSafeAreaInsets();
  const headerHeight = useHeaderHeight();
  const identity = useSessionStore((state) => state.identity);
  const provider = getOneToOneCryptoProvider();
  const fileCrypto = getFileCryptoProvider();
  const listRef = useRef<FlatList<DecryptedMessage>>(null);
  const conversationIdValue = conversationId as Id<'conversations'>;
  const peerUserIdValue = peerUserId as Id<'users'> | undefined;

  const [composerValue, setComposerValue] = useState('');
  const [messages, setMessages] = useState<DecryptedMessage[]>([]);
  const [sending, setSending] = useState(false);
  const [uploadingAttachment, setUploadingAttachment] = useState(false);
  const [pendingAttachments, setPendingAttachments] = useState<AttachmentEnvelope[]>([]);
  const [attachmentDownloads, setAttachmentDownloads] = useState<
    Record<string, AttachmentDownloadState>
  >({});
  const [removingAttachmentId, setRemovingAttachmentId] = useState<string | null>(null);

  const auth = identity?.userId && identity.sessionProof ? buildSessionAuth(identity) : null;
  const messageData = useQuery(
    api.messenger.listMessages,
    auth
      ? {
          auth,
          conversationId: conversationIdValue,
        }
      : 'skip',
  );
  const peerBundle = useQuery(
    api.messenger.getDeviceBundle,
    auth && peerUserId
      ? {
          auth,
          targetUserId: peerUserIdValue!,
        }
      : 'skip',
  );

  const sendEncryptedMessage = useMutation(api.messenger.sendEncryptedMessage);
  const markMessageRead = useMutation(api.messenger.markMessageRead);
  const createAttachmentUploadSession = useMutation(
    api.messenger.createAttachmentUploadSession,
  );
  const uploadEncryptedAttachmentMetadata = useMutation(
    api.messenger.uploadEncryptedAttachmentMetadata,
  );
  const createAttachmentDownloadUrl = useMutation(
    api.messenger.createAttachmentDownloadUrl,
  );
  const discardEncryptedAttachment = useMutation(
    api.messenger.discardEncryptedAttachment,
  );

  useEffect(() => {
    if (!identity || !messageData) {
      return;
    }

    void (async () => {
      const receiptByMessage = new Map<string, string>();

      messageData.receipts.forEach((receipt) => {
        if (receipt.receiptType === 'read') {
          receiptByMessage.set(receipt.messageId, 'Read');
        }
      });

      const nextMessages = await Promise.all(
        messageData.messages.map(async (message) => {
          const plaintext = await provider.decryptMessage({
            ciphertext: message.ciphertext,
            encryptionMetadata: message.encryptionMetadata,
            identity,
          });
          const envelope = deserializeMessageEnvelope(plaintext);

          return {
            attachments: envelope.attachments,
            id: message._id,
            isMine: message.senderUserId === identity.userId,
            receipt: receiptByMessage.get(message._id),
            text: envelope.text,
          };
        }),
      );

      setMessages(nextMessages);

      const latestInbound = [...messageData.messages]
        .reverse()
        .find((message) => message.senderUserId !== identity.userId);

      if (latestInbound) {
        const payload = {
          conversationId: conversationIdValue,
          messageId: latestInbound._id,
        };

        await markMessageRead({
          auth: buildMutationAuth(identity, 'markMessageRead', payload),
          conversationId: conversationIdValue,
          messageId: latestInbound._id,
        });
      }
    })();
  }, [conversationIdValue, identity, markMessageRead, messageData, provider]);

  useEffect(() => {
    if (messages.length === 0) {
      return;
    }

    const timer = setTimeout(() => {
      listRef.current?.scrollToEnd({animated: true});
    }, 40);

    return () => clearTimeout(timer);
  }, [messages.length]);

  const handlePickAttachment = async () => {
    if (!identity || uploadingAttachment || pendingAttachments.length >= 5) {
      return;
    }

    const result = await DocumentPicker.getDocumentAsync({
      copyToCacheDirectory: true,
      multiple: false,
    });

    if (result.canceled || result.assets.length === 0) {
      return;
    }

    const [asset] = result.assets;

    if (!asset.uri || !asset.name) {
      Alert.alert('Attachment failed', 'The selected file is missing a stable URI.');
      return;
    }

    let ciphertextFileUri: string | undefined;

    setUploadingAttachment(true);

    try {
      const encryptedFile = await fileCrypto.encryptFile({
        fileName: asset.name,
        mimeType: asset.mimeType,
        uri: asset.uri,
      });
      ciphertextFileUri = encryptedFile.ciphertextFileUri;

      const uploadSessionPayload = {
        ciphertextByteLength: encryptedFile.ciphertextByteLength,
        conversationId: conversationIdValue,
      };
      const uploadSession = await createAttachmentUploadSession({
        auth: buildMutationAuth(
          identity,
          'createAttachmentUploadSession',
          uploadSessionPayload,
        ),
        ...uploadSessionPayload,
      });
      const storageId = await uploadEncryptedFile(
        uploadSession.uploadUrl,
        encryptedFile.ciphertextFileUri,
      );
      const finalizePayload = {
        ciphertextByteLength: encryptedFile.ciphertextByteLength,
        ciphertextDigest: encryptedFile.ciphertextDigest,
        conversationId: conversationIdValue,
        metadataCiphertext: encryptedFile.metadataCiphertext,
        protocolVersion: encryptedFile.protocolVersion,
        storageId: storageId as Id<'_storage'>,
        uploadSessionId: uploadSession.uploadSessionId,
      };
      const attachment = await uploadEncryptedAttachmentMetadata({
        auth: buildMutationAuth(
          identity,
          'uploadEncryptedAttachmentMetadata',
          finalizePayload,
        ),
        ...finalizePayload,
      });

      if (!attachment?._id) {
        throw new Error('Attachment metadata finalization did not return an identifier.');
      }

      setPendingAttachments((current) => [
        ...current,
        {
          attachmentId: attachment._id,
          ciphertextByteLength: encryptedFile.ciphertextByteLength,
          ciphertextDigest: encryptedFile.ciphertextDigest,
          fileName: encryptedFile.originalFileName,
          keyBase64: encryptedFile.keyBase64,
          mimeType: encryptedFile.mimeType,
          nonceBase64: encryptedFile.nonceBase64,
          plaintextByteLength: encryptedFile.plaintextByteLength,
          protocolVersion: encryptedFile.protocolVersion,
        },
      ]);
    } catch (caughtError) {
      Alert.alert(
        'Attachment failed',
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to encrypt and upload this attachment.',
      );
    } finally {
      setUploadingAttachment(false);

      if (ciphertextFileUri) {
        await FileSystem.deleteAsync(ciphertextFileUri, {idempotent: true}).catch(
          () => undefined,
        );
      }
    }
  };

  const handleRemovePendingAttachment = async (attachmentId: string) => {
    if (!identity || removingAttachmentId) {
      return;
    }

    setRemovingAttachmentId(attachmentId);

    try {
      const payload = {
        attachmentId: attachmentId as Id<'encryptedAttachments'>,
      };

      await discardEncryptedAttachment({
        attachmentId: payload.attachmentId,
        auth: buildMutationAuth(identity, 'discardEncryptedAttachment', payload),
      });

      setPendingAttachments((current) =>
        current.filter((attachment) => attachment.attachmentId !== attachmentId),
      );
    } catch (caughtError) {
      Alert.alert(
        'Unable to remove attachment',
        caughtError instanceof Error ? caughtError.message : 'Try again in a moment.',
      );
    } finally {
      setRemovingAttachmentId(null);
    }
  };

  const handleDownloadAttachment = async (attachment: AttachmentEnvelope) => {
    if (!identity) {
      return;
    }

    const existing = attachmentDownloads[attachment.attachmentId];

    if (existing?.status === 'ready' && existing.localUri) {
      Alert.alert('Attachment already decrypted', existing.localUri);
      return;
    }

    let ciphertextUri: string | undefined;

    setAttachmentDownloads((current) => ({
      ...current,
      [attachment.attachmentId]: {
        ...current[attachment.attachmentId],
        status: 'downloading',
      },
    }));

    try {
      const payload = {
        attachmentId: attachment.attachmentId as Id<'encryptedAttachments'>,
      };
      const downloadResult = await createAttachmentDownloadUrl({
        attachmentId: payload.attachmentId,
        auth: buildMutationAuth(identity, 'createAttachmentDownloadUrl', payload),
      });

      ciphertextUri = await downloadEncryptedFile(downloadResult.downloadUrl);

      const destinationUri = await buildDecryptedAttachmentPath(attachment.fileName);
      const localUri = await fileCrypto.decryptFileFromUri({
        ciphertextUri,
        destinationUri,
        keyBase64: attachment.keyBase64,
        nonceBase64: attachment.nonceBase64,
      });

      setAttachmentDownloads((current) => ({
        ...current,
        [attachment.attachmentId]: {
          localUri,
          status: 'ready',
        },
      }));

      Alert.alert('Attachment decrypted', localUri);
    } catch (caughtError) {
      setAttachmentDownloads((current) => ({
        ...current,
        [attachment.attachmentId]: {
          localUri: current[attachment.attachmentId]?.localUri,
          status: 'error',
        },
      }));

      Alert.alert(
        'Download failed',
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to download and decrypt this attachment.',
      );
    } finally {
      if (ciphertextUri) {
        await FileSystem.deleteAsync(ciphertextUri, {idempotent: true}).catch(
          () => undefined,
        );
      }
    }
  };

  const handleSend = async () => {
    if (!identity || !peerBundle || sending) {
      return;
    }

    const trimmedComposer = composerValue.trim();

    if (!trimmedComposer && pendingAttachments.length === 0) {
      return;
    }

    setSending(true);

    try {
      const recipientBundle = {
        ...peerBundle,
        provider: 'demo' as const,
      };
      const prepared = await provider.encryptMessage({
        identity,
        plaintext: serializeMessageEnvelope({
          attachments: pendingAttachments,
          text: trimmedComposer,
        }),
        recipientBundle,
      });

      const payload = {
        attachmentIds: pendingAttachments.map(
          (attachment) => attachment.attachmentId as Id<'encryptedAttachments'>,
        ),
        ciphertext: prepared.ciphertext,
        clientMessageId: crypto.randomUUID(),
        conversationId: conversationIdValue,
        encryptionMetadata: prepared.encryptionMetadata,
        protocolVersion: prepared.protocolVersion,
      };

      await sendEncryptedMessage({
        attachmentIds: payload.attachmentIds,
        auth: buildMutationAuth(identity, 'sendEncryptedMessage', payload),
        ciphertext: prepared.ciphertext,
        clientMessageId: payload.clientMessageId,
        conversationId: conversationIdValue,
        encryptionMetadata: prepared.encryptionMetadata,
        protocolVersion: prepared.protocolVersion,
      });

      setComposerValue('');
      setPendingAttachments([]);
    } catch (caughtError) {
      Alert.alert(
        'Send failed',
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to send the encrypted message.',
      );
    } finally {
      setSending(false);
    }
  };

  if (!identity || !messageData) {
    return <LoadingView label="Decrypting conversation…" />;
  }

  const trimmedComposer = composerValue.trim();
  const canSend = Boolean(
    peerBundle &&
      !sending &&
      !uploadingAttachment &&
      (trimmedComposer || pendingAttachments.length > 0),
  );
  const peerReady = Boolean(peerBundle);

  return (
    <Container>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={
          Platform.OS === 'ios' ? Math.max(headerHeight - bottom, 0) : 0
        }
        style={{flex: 1}}
      >
        <HeaderCard>
          <PeerLabel>{peerLabel || 'Encrypted chat'}</PeerLabel>
          <PeerHint>
            {peerReady
              ? 'Message text and attachment keys decrypt only on participating devices.'
              : 'Waiting for the peer device bundle before sending.'}
          </PeerHint>
          <ProtocolPill>
            <ProtocolPillText>
              {provider.isProductionReady ? 'Signal ready' : 'Demo transport only'}
            </ProtocolPillText>
          </ProtocolPill>
        </HeaderCard>

        <ThreadList
          ref={listRef}
          contentContainerStyle={{
            flexGrow: 1,
            paddingHorizontal: theme.spacing.lg,
            paddingTop: theme.spacing.md,
            paddingBottom: theme.spacing.xl,
          }}
          data={messages}
          ItemSeparatorComponent={MessageGap}
          keyboardDismissMode={Platform.OS === 'ios' ? 'interactive' : 'on-drag'}
          keyboardShouldPersistTaps="handled"
          keyExtractor={(item) => item.id}
          ListEmptyComponent={
            <EmptyStateCard>
              <EmptyTitle>No messages yet</EmptyTitle>
              <EmptyBody>
                Your first message will be encrypted on this device before Convex
                sees it.
              </EmptyBody>
            </EmptyStateCard>
          }
          onContentSizeChange={() => {
            if (messages.length > 0) {
              listRef.current?.scrollToEnd({animated: true});
            }
          }}
          renderItem={({item}) => (
            <MessageBubble
              attachments={item.attachments.map((attachment) => ({
                detail: attachmentStatusLabel(
                  attachment,
                  attachmentDownloads[attachment.attachmentId],
                ),
                id: attachment.attachmentId,
                label: attachment.fileName,
                loading:
                  attachmentDownloads[attachment.attachmentId]?.status ===
                  'downloading',
                onPress: () => {
                  void handleDownloadAttachment(attachment);
                },
              }))}
              isMine={item.isMine}
              receipt={item.receipt}
              text={item.text}
            />
          )}
          showsVerticalScrollIndicator={false}
        />

        <ComposerShell>
          {pendingAttachments.length > 0 ? (
            <PendingAttachmentStack>
              {pendingAttachments.map((attachment) => (
                <PendingAttachmentCard key={attachment.attachmentId}>
                  <PendingAttachmentCopy>
                    <PendingAttachmentTitle>{attachment.fileName}</PendingAttachmentTitle>
                    <PendingAttachmentMeta>
                      Ready to send • {formatFileSize(attachment.plaintextByteLength)}
                    </PendingAttachmentMeta>
                  </PendingAttachmentCopy>
                  <PendingAttachmentRemove
                    accessibilityRole="button"
                    disabled={removingAttachmentId === attachment.attachmentId || sending}
                    onPress={() => {
                      void handleRemovePendingAttachment(attachment.attachmentId);
                    }}
                  >
                    <Ionicons
                      color={theme.color.textMuted}
                      name="close"
                      size={18}
                    />
                  </PendingAttachmentRemove>
                </PendingAttachmentCard>
              ))}
            </PendingAttachmentStack>
          ) : null}

          {uploadingAttachment ? (
            <UploadHint>Encrypting and uploading attachment…</UploadHint>
          ) : null}

          <ComposerRow>
            <AttachPressable
              accessibilityRole="button"
              disabled={uploadingAttachment || sending || pendingAttachments.length >= 5}
              onPress={() => {
                void handlePickAttachment();
              }}
            >
              <AttachButton
                disabled={
                  uploadingAttachment || sending || pendingAttachments.length >= 5
                }
              >
                <Ionicons
                  color={theme.color.text}
                  name="attach"
                  size={18}
                />
              </AttachButton>
            </AttachPressable>
            <ComposerInput
              editable={peerReady && !sending && !uploadingAttachment}
              multiline
              onChangeText={setComposerValue}
              onSubmitEditing={() => {
                void handleSend();
              }}
              placeholder={
                peerReady
                  ? pendingAttachments.length > 0
                    ? 'Add an optional message'
                    : 'Write an encrypted message'
                  : 'Peer device bundle is still loading'
              }
              placeholderTextColor={theme.color.placeholder}
              returnKeyType="send"
              value={composerValue}
            />
            <SendPressable
              accessibilityRole="button"
              disabled={!canSend}
              onPress={() => {
                void handleSend();
              }}
            >
              <SendButton disabled={!canSend}>
                <Ionicons
                  color={canSend ? theme.color.textInverse : theme.color.placeholder}
                  name="arrow-up"
                  size={20}
                />
              </SendButton>
            </SendPressable>
          </ComposerRow>
        </ComposerShell>
        <View style={{height: bottom, backgroundColor: theme.color.surface}} />
      </KeyboardAvoidingView>
    </Container>
  );
}

const Container = styled(View)`
  flex: 1;
  background-color: ${({theme}) => theme.color.background};
`;

const PeerLabel = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 20px;
  font-weight: 700;
`;

const PeerHint = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 14px;
`;

const HeaderCard = styled(View)`
  gap: ${({theme}) => theme.spacing.xs}px;
  margin: ${({theme}) => theme.spacing.lg}px;
  margin-bottom: ${({theme}) => theme.spacing.sm}px;
  padding: ${({theme}) => theme.spacing.lg}px;
  border-width: 1px;
  border-color: ${({theme}) => theme.color.border};
  border-radius: ${({theme}) => theme.radius.lg}px;
  background-color: ${({theme}) => theme.color.surface};
`;

const ProtocolPill = styled(View)`
  align-self: flex-start;
  margin-top: ${({theme}) => theme.spacing.sm}px;
  padding: 8px 12px;
  border-radius: ${({theme}) => theme.radius.pill}px;
  background-color: ${({theme}) => theme.color.pillDefaultBg};
`;

const ProtocolPillText = styled(Text)`
  color: ${({theme}) => theme.color.pillDefaultText};
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.3px;
`;

const ThreadList = styled(FlatList<DecryptedMessage>)`
  flex: 1;
`;

const MessageGap = styled(View)`
  height: ${({theme}) => theme.spacing.sm}px;
`;

const EmptyStateCard = styled(View)`
  flex: 1;
  justify-content: center;
  padding: ${({theme}) => theme.spacing.xl}px;
  border-width: 1px;
  border-color: ${({theme}) => theme.color.border};
  border-style: dashed;
  border-radius: ${({theme}) => theme.radius.lg}px;
  background-color: ${({theme}) => theme.color.surface};
`;

const EmptyTitle = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 18px;
  font-weight: 700;
  margin-bottom: ${({theme}) => theme.spacing.sm}px;
`;

const EmptyBody = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
`;

const ComposerShell = styled(View)`
  padding: ${({theme}) => theme.spacing.sm}px ${({theme}) => theme.spacing.lg}px 0;
  background-color: ${({theme}) => theme.color.surface};
  border-top-width: 1px;
  border-top-color: ${({theme}) => theme.color.border};
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const PendingAttachmentStack = styled(View)`
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const PendingAttachmentCard = styled(View)`
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: ${({theme}) => theme.spacing.md}px;
  border-width: 1px;
  border-color: ${({theme}) => theme.color.border};
  border-radius: ${({theme}) => theme.radius.md}px;
  background-color: ${({theme}) => theme.color.background};
  gap: ${({theme}) => theme.spacing.md}px;
`;

const PendingAttachmentCopy = styled(View)`
  flex: 1;
  gap: 2px;
`;

const PendingAttachmentTitle = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 14px;
  font-weight: 700;
`;

const PendingAttachmentMeta = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 12px;
`;

const PendingAttachmentRemove = styled(Pressable)`
  width: 32px;
  height: 32px;
  border-radius: ${({theme}) => theme.radius.pill}px;
  align-items: center;
  justify-content: center;
  background-color: ${({theme}) => theme.color.surfaceMuted};
`;

const UploadHint = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 13px;
`;

const ComposerRow = styled(View)`
  flex-direction: row;
  align-items: flex-end;
  gap: ${({theme}) => theme.spacing.sm}px;
`;

const AttachPressable = styled(Pressable)`
  align-self: flex-end;
`;

const AttachButton = styled(View)<{disabled: boolean}>`
  width: 48px;
  height: 48px;
  border-radius: ${({theme}) => theme.radius.pill}px;
  align-items: center;
  justify-content: center;
  background-color: ${({theme, disabled}) =>
    disabled ? theme.color.surfaceMuted : theme.color.background};
  border-width: 1px;
  border-color: ${({theme}) => theme.color.borderStrong};
`;

const ComposerInput = styled(TextInput)`
  flex: 1;
  min-height: 48px;
  max-height: 132px;
  border-radius: ${({theme}) => theme.radius.lg}px;
  background-color: ${({theme}) => theme.color.background};
  border-width: 1px;
  border-color: ${({theme}) => theme.color.borderStrong};
  padding: 14px ${({theme}) => theme.spacing.lg}px;
  color: ${({theme}) => theme.color.text};
  font-size: 16px;
  line-height: 22px;
  text-align-vertical: top;
`;

const SendPressable = styled(Pressable)`
  align-self: flex-end;
`;

const SendButton = styled(View)<{disabled: boolean}>`
  width: 48px;
  height: 48px;
  border-radius: ${({theme}) => theme.radius.pill}px;
  align-items: center;
  justify-content: center;
  background-color: ${({theme, disabled}) =>
    disabled ? theme.color.surfaceMuted : theme.color.primary};
`;
