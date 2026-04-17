import {Redirect} from 'expo-router';

import {getOneToOneCryptoProvider} from '@/crypto';
import {isProductionAppEnv} from '@/lib/runtime';
import {useSessionStore} from '@/stores/sessionStore';

export default function Index() {
  const identity = useSessionStore((state) => state.identity);
  const provider = getOneToOneCryptoProvider();

  if (isProductionAppEnv() && !provider.isProductionReady) {
    return <Redirect href="/production-blocked" />;
  }

  if (identity?.userId && identity.sessionProof) {
    return <Redirect href="/(tabs)/chats" />;
  }

  return <Redirect href="/welcome" />;
}
