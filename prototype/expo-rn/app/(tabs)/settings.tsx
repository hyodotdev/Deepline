import styled from '@emotion/native';
import {useRouter} from 'expo-router';
import {Text, View} from 'react-native';

import {PrimaryButton} from '@/components/PrimaryButton';
import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {
  getGroupCryptoProvider,
  getOneToOneCryptoProvider,
} from '@/crypto';
import {maskId} from '@/lib/security';
import {useSessionStore} from '@/stores/sessionStore';

export default function SettingsScreen() {
  const router = useRouter();
  const clearLocalIdentity = useSessionStore((state) => state.clearLocalIdentity);
  const displayName = useSessionStore((state) => state.displayName);
  const identity = useSessionStore((state) => state.identity);
  const oneToOneProvider = getOneToOneCryptoProvider();
  const groupProvider = getGroupCryptoProvider();

  return (
    <Screen
      scroll
      footer={
        <View style={{gap: 12}}>
          <PrimaryButton onPress={() => router.push('/devices')} text="Devices & safety" />
          <PrimaryButton
            onPress={async () => {
              await clearLocalIdentity();
              router.replace('/welcome');
            }}
            secondary
            text="Clear local identity"
          />
        </View>
      }
    >
      <Header>
        <Title>Settings</Title>
        <Subtitle>
          Deepline is currently configured as an encrypted-storage MVP with a demo
          1:1 provider and a blocked MLS group layer.
        </Subtitle>
      </Header>

      <SectionCard>
        <SectionTitle>This device</SectionTitle>
        <SectionBody>Local display name: {displayName || 'Unset'}</SectionBody>
        <SectionBody>User ID: {identity?.userId ? maskId(identity.userId) : 'Not registered'}</SectionBody>
        <SectionBody>Device ID: {identity ? maskId(identity.deviceId) : 'Unknown'}</SectionBody>
      </SectionCard>

      <SectionCard>
        <SectionTitle>Security status</SectionTitle>
        <StatusPill
          text={oneToOneProvider.isProductionReady ? 'Signal ready' : 'Demo 1:1 provider'}
          tone={oneToOneProvider.isProductionReady ? 'success' : 'warning'}
        />
        <SectionBody>{oneToOneProvider.warning}</SectionBody>
      </SectionCard>

      <SectionCard>
        <SectionTitle>Group layer</SectionTitle>
        <StatusPill text={groupProvider.isSupported ? 'MLS ready' : 'Blocked'} tone="danger" />
        <SectionBody>{groupProvider.blocker}</SectionBody>
      </SectionCard>
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
