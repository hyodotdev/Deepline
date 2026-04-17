import styled from '@emotion/native';
import {useRouter} from 'expo-router';
import {Text, View} from 'react-native';

import {PrimaryButton} from '@/components/PrimaryButton';
import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {getOneToOneCryptoProvider} from '@/crypto';
import {isProductionAppEnv} from '@/lib/runtime';

export default function WelcomeScreen() {
  const router = useRouter();
  const provider = getOneToOneCryptoProvider();
  const productionBlocked = isProductionAppEnv() && !provider.isProductionReady;

  return (
    <Screen
      scroll
      footer={
        <PrimaryButton
          disabled={productionBlocked}
          onPress={() =>
            router.push(productionBlocked ? '/production-blocked' : '/local-identity')
          }
          text={productionBlocked ? 'Production build is blocked' : 'Create local identity'}
        />
      }
    >
      <Hero>
        <StatusPill
          text={productionBlocked ? 'Release blocked' : 'Encrypted blobs only'}
          tone={productionBlocked ? 'danger' : 'success'}
        />
        <Title>Deepline keeps plaintext on the device, not in Convex.</Title>
        <Lead>
          Private keys, session secrets, raw contacts, and plaintext messages
          stay on the client. Production release mode is blocked until the app
          stops depending on demo cryptography.
        </Lead>
      </Hero>

      <SectionCard>
        <SectionTitle>What works in this MVP</SectionTitle>
        <SectionBody>
          Local identity setup, QR or invite-code contact linking, encrypted 1:1
          messaging with delivery and read state, and encrypted attachment
          metadata plumbing.
        </SectionBody>
      </SectionCard>

      <SectionCard>
        <SectionTitle>What is blocked</SectionTitle>
        <SectionBody>
          Production Signal Protocol and MLS are intentionally blocked until an
          audited Expo-compatible runtime is available. Deepline falls back to a
          clearly labeled demo provider instead of shipping homemade ratchets.
        </SectionBody>
      </SectionCard>
    </Screen>
  );
}

const Hero = styled(View)`
  gap: ${({theme}) => theme.spacing.md}px;
  padding-top: ${({theme}) => theme.spacing.xxl}px;
`;

const Title = styled(Text)`
  color: ${({theme}) => theme.color.text};
  font-size: 34px;
  font-weight: 800;
  line-height: 40px;
`;

const Lead = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 16px;
  line-height: 24px;
`;
