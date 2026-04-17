import styled from '@emotion/native';
import {Text, View} from 'react-native';

import {Screen, SectionBody, SectionCard, SectionTitle} from '@/components/Screen';
import {StatusPill} from '@/components/StatusPill';
import {getGroupCryptoProvider, getOneToOneCryptoProvider} from '@/crypto';
import {getAppEnv} from '@/lib/runtime';

export function ProductionBlockedView() {
  const oneToOneProvider = getOneToOneCryptoProvider();
  const groupProvider = getGroupCryptoProvider();

  return (
    <Screen scroll>
      <Hero>
        <StatusPill text="Production Build Blocked" tone="danger" />
        <Title>Deepline cannot boot a production build with demo cryptography.</Title>
        <Lead>
          App env `{getAppEnv()}` is treated as a production release target. The
          current runtime still lacks an official Signal client integration, and
          group MLS remains blocked.
        </Lead>
      </Hero>

      <SectionCard>
        <SectionTitle>1:1 blocker</SectionTitle>
        <SectionBody>{oneToOneProvider.warning}</SectionBody>
      </SectionCard>

      <SectionCard>
        <SectionTitle>Group blocker</SectionTitle>
        <SectionBody>{groupProvider.blocker}</SectionBody>
      </SectionCard>

      <SectionCard>
        <SectionTitle>Required next step</SectionTitle>
        <SectionBody>
          Replace the demo 1:1 provider with a production Signal runtime and
          replace the blocked group layer with a production MLS runtime before
          shipping a release build.
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
  font-size: 32px;
  font-weight: 800;
  line-height: 38px;
`;

const Lead = styled(Text)`
  color: ${({theme}) => theme.color.textMuted};
  font-size: 16px;
  line-height: 24px;
`;
