import {demoProvider} from './demoProvider';
import {fileCryptoProvider} from './fileCrypto';
import {groupCryptoProvider} from './groupProvider';
import {isProductionAppEnv} from '@/lib/runtime';
import {signalProvider} from './signalProvider';
import type {StoredIdentityMaterial} from './types';

export function getOneToOneCryptoProvider() {
  return isProductionAppEnv() ? signalProvider : demoProvider;
}

export function getSignalProvider() {
  return signalProvider;
}

export function getGroupCryptoProvider() {
  return groupCryptoProvider;
}

export function getFileCryptoProvider() {
  return fileCryptoProvider;
}

export function buildSessionAuth(identity: StoredIdentityMaterial) {
  if (!identity.userId || !identity.sessionProof) {
    throw new Error('Device is not fully registered with Convex yet.');
  }

  return {
    deviceId: identity.deviceId,
    sessionProof: identity.sessionProof,
    userId: identity.userId,
  };
}

export function buildMutationAuth(
  identity: StoredIdentityMaterial,
  operation: string,
  payload: unknown,
) {
  const provider = getOneToOneCryptoProvider();
  const auth = buildSessionAuth(identity);

  return {
    ...auth,
    requestSignature: provider.signMutation(
      identity,
      auth.userId,
      operation,
      payload,
    ),
  };
}
