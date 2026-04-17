import styled from '@emotion/native';
import {useMutation} from 'convex/react';
import {useRouter} from 'expo-router';
import {useState} from 'react';
import {Text, View} from 'react-native';

import {PrimaryButton} from '@/components/PrimaryButton';
import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {TextField} from '@/components/TextField';
import {
  buildMutationAuth,
  getOneToOneCryptoProvider,
} from '@/crypto';
import {isProductionAppEnv} from '@/lib/runtime';
import {useSessionStore} from '@/stores/sessionStore';
import {api} from '../convex/_generated/api';

export default function LocalIdentityScreen() {
  const router = useRouter();
  const provider = getOneToOneCryptoProvider();
  const productionBlocked = isProductionAppEnv() && !provider.isProductionReady;
  const completeLocalSetup = useSessionStore((state) => state.completeLocalSetup);
  const attachServerRegistration = useSessionStore(
    (state) => state.attachServerRegistration,
  );
  const [displayName, setDisplayName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const createOrGetUser = useMutation(api.messenger.createOrGetUser);
  const registerDevice = useMutation(api.messenger.registerDevice);
  const publishPreKeyBundle = useMutation(api.messenger.publishPreKeyBundle);

  const handleCreate = async () => {
    if (!displayName.trim() || submitting || productionBlocked) {
      if (productionBlocked) {
        setError(
          'Production app env is blocked until Deepline integrates a production Signal runtime.',
        );
      }
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const identity = await provider.createIdentity();
      const createdUser = await createOrGetUser({
        identityFingerprint: identity.userFingerprint,
      });

      if (!createdUser?._id) {
        throw new Error('Failed to create the Deepline user record.');
      }

      const registeredIdentity = {
        ...identity,
        sessionProof: provider.createSessionProof(identity, createdUser._id),
        userId: createdUser._id,
      };

      const preKeyBundle = await provider.getPreKeyBundle(registeredIdentity);

      await registerDevice({
        deviceId: registeredIdentity.deviceId,
        identityKey: registeredIdentity.identityKeyPair.publicKey,
        oneTimePreKeys: preKeyBundle.oneTimePreKeys,
        protocolVersion: preKeyBundle.protocolVersion,
        signedPreKey: registeredIdentity.signedPreKeyPair.publicKey,
        signedPreKeySignature: registeredIdentity.signedPreKeySignature,
        signingPublicKey: registeredIdentity.signingKeyPair.publicKey,
        userId: createdUser._id,
      });

      const publishPayload = {
        bundleId: preKeyBundle.bundleId,
        identityKey: preKeyBundle.identityKey,
        oneTimePreKeys: preKeyBundle.oneTimePreKeys,
        protocolVersion: preKeyBundle.protocolVersion,
        signedPreKey: preKeyBundle.signedPreKey,
        signedPreKeySignature: preKeyBundle.signedPreKeySignature,
        signingPublicKey: preKeyBundle.signingPublicKey,
      };

      await publishPreKeyBundle({
        ...publishPayload,
        auth: buildMutationAuth(
          registeredIdentity,
          'publishPreKeyBundle',
          {
            ...publishPayload,
            deviceId: registeredIdentity.deviceId,
            userId: createdUser._id,
          },
        ),
      });

      await completeLocalSetup({
        displayName: displayName.trim(),
        identity: registeredIdentity,
      });
      await attachServerRegistration({
        sessionProof: registeredIdentity.sessionProof!,
        userId: createdUser._id,
      });

      router.replace('/(tabs)/chats');
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to create a local identity.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Screen
      scroll
      footer={
        <PrimaryButton
          disabled={productionBlocked}
          loading={submitting}
          onPress={handleCreate}
          text={productionBlocked ? 'Production build is blocked' : 'Register this device'}
        />
      }
    >
      <Header>
        <StatusPill
          text={productionBlocked ? 'PRODUCTION BUILD BLOCKED' : 'NOT PRODUCTION SAFE'}
          tone={productionBlocked ? 'danger' : 'warning'}
        />
        <Title>Set up a local-only identity for this device.</Title>
        <Lead>
          Your name stays local unless you intentionally put it into an encrypted
          invite or message. The server only sees the public device bundle needed
          to route encrypted payloads.
        </Lead>
      </Header>

      <TextField
        autoCapitalize="words"
        hint="This label stays on the device and helps you recognize your own profile."
        label="Local display name"
        onChangeText={setDisplayName}
        placeholder="Morning notebook"
        value={displayName}
      />

      <SectionCard>
        <SectionTitle>Current crypto mode</SectionTitle>
        <SectionBody>{provider.warning}</SectionBody>
      </SectionCard>

      {error ? <ErrorText>{error}</ErrorText> : null}
    </Screen>
  );
}

const Header = styled(View)`
  gap: ${({theme}) => theme.spacing.md}px;
`;

const Title = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 28px;
  font-weight: 800;
  line-height: 34px;
`;

const Lead = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 15px;
  line-height: 22px;
`;

const ErrorText = styled(Text)`
  color: ${({theme}) => theme.color.danger};
  font-size: 14px;
  line-height: 20px;
`;
