import styled from '@emotion/native';
import {Text, View} from 'react-native';

import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {getOneToOneCryptoProvider} from '@/crypto';
import {useSessionStore} from '@/stores/sessionStore';

export default function DevicesScreen() {
  const identity = useSessionStore((state) => state.identity);
  const provider = getOneToOneCryptoProvider();

  const fingerprint =
    identity && provider.getSafetyNumber(identity, {
      bundleId: `${identity.deviceId}:${identity.createdAt}`,
      deviceId: identity.deviceId,
      identityKey: identity.identityKeyPair.publicKey,
      oneTimePreKeys: [],
      protocolVersion: identity.protocolType,
      provider: identity.kind,
      signedPreKey: identity.signedPreKeyPair.publicKey,
      signedPreKeySignature: identity.signedPreKeySignature,
      signingPublicKey: identity.signingKeyPair.publicKey,
      userId: identity.userId,
    });

  return (
    <Screen scroll>
      <Header>
        <Title>Devices and safety</Title>
        <Subtitle>
          Deepline keeps private device keys in secure local storage. This screen
          is the placeholder for future cross-device verification flows.
        </Subtitle>
      </Header>

      <SectionCard>
        <SectionTitle>Current device</SectionTitle>
        <SectionBody>Device ID: {identity?.deviceId ?? 'Unknown'}</SectionBody>
        <SectionBody>User ID: {identity?.userId ?? 'Not registered'}</SectionBody>
        {fingerprint ? (
          <>
            <StatusPill text="Demo safety fingerprint" />
            <Fingerprint selectable>{fingerprint}</Fingerprint>
          </>
        ) : null}
      </SectionCard>

      <SectionCard>
        <SectionTitle>Verification roadmap</SectionTitle>
        <SectionBody>
          Production safety-number verification, multi-device re-linking, sealed
          sender, and audited Signal integration remain in TODO_SECURITY.md.
        </SectionBody>
      </SectionCard>
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

const Fingerprint = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 14px;
  line-height: 22px;
`;
