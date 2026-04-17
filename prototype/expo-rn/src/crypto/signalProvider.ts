import type {
  CryptoProvider,
  DecryptMessageArgs,
  EncryptMessageArgs,
  StoredIdentityMaterial,
} from './types';

export const SIGNAL_PROVIDER_BLOCKER =
  'Official libsignal packages are maintained for Java, Swift, and Node TypeScript, but not as a clean Expo React Native client runtime. Deepline falls back to a clearly labeled demo provider instead of shipping a homemade Double Ratchet.';

class BlockedSignalProvider implements CryptoProvider {
  readonly isProductionReady = false;
  readonly kind = 'signal' as const;
  readonly protocolType = 'signal_1to1' as const;
  readonly warning = SIGNAL_PROVIDER_BLOCKER;

  async createIdentity(): Promise<never> {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  createSessionProof(): never {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  async decryptMessage(_args: DecryptMessageArgs): Promise<never> {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  async encryptMessage(_args: EncryptMessageArgs): Promise<never> {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  async getDeviceBundle(_identity: StoredIdentityMaterial): Promise<never> {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  async getPreKeyBundle(_identity: StoredIdentityMaterial): Promise<never> {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  getSafetyNumber(): never {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }

  signMutation(): never {
    throw new Error(SIGNAL_PROVIDER_BLOCKER);
  }
}

export const signalProvider = new BlockedSignalProvider();
