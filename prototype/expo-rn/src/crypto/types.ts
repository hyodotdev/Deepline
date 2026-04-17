import type {Id} from '../../convex/_generated/dataModel';

export type ProtocolType = 'signal_1to1' | 'mls_group' | 'demo_dev_only';

export type KeyPairBase64 = {
  publicKey: string;
  secretKey: string;
};

export type PublishedOneTimePreKey = {
  keyId: string;
  publicKey: string;
};

export type StoredOneTimePreKey = PublishedOneTimePreKey & {
  secretKey: string;
};

export type DeviceBundle = {
  bundleId: string;
  deviceId: string;
  identityKey: string;
  oneTimePreKeys: PublishedOneTimePreKey[];
  protocolVersion: string;
  provider: 'demo' | 'signal';
  signingPublicKey: string;
  signedPreKey: string;
  signedPreKeySignature: string;
  userId?: Id<'users'>;
};

export type MessageEncryptionMetadata = {
  mode: string;
  nonce: string;
  senderIdentityKey: string;
  senderSigningKey: string;
  recipientDeviceId?: string;
  attachmentKeyEnvelope?: string;
  attachmentMetadataCiphertext?: string;
  isDemo?: boolean;
};

export type PreparedMessage = {
  ciphertext: string;
  encryptionMetadata: MessageEncryptionMetadata;
  protocolVersion: string;
};

export type StoredIdentityMaterial = {
  createdAt: number;
  deviceId: string;
  identityKeyPair: KeyPairBase64;
  isProductionReady: boolean;
  kind: 'demo' | 'signal';
  oneTimePreKeys: StoredOneTimePreKey[];
  protocolType: ProtocolType;
  sessionProof?: string;
  signedPreKeyPair: KeyPairBase64;
  signedPreKeySignature: string;
  signingKeyPair: KeyPairBase64;
  userFingerprint: string;
  userId?: Id<'users'>;
};

export type LocalSessionRecord = {
  lastOpenedAt: number;
  peerDeviceId: string;
  peerUserId: Id<'users'>;
  protocolType: ProtocolType;
  safetyNumber: string;
};

export type EncryptMessageArgs = {
  attachmentKeyEnvelope?: string;
  attachmentMetadataCiphertext?: string;
  identity: StoredIdentityMaterial;
  plaintext: string;
  recipientBundle: DeviceBundle;
};

export type DecryptMessageArgs = {
  ciphertext: string;
  encryptionMetadata: MessageEncryptionMetadata;
  identity: StoredIdentityMaterial;
};

export type EncryptedFileResult = {
  ciphertextByteLength: number;
  ciphertextDigest: string;
  ciphertextFileUri: string;
  keyBase64: string;
  metadataCiphertext: string;
  mimeType: string;
  nonceBase64: string;
  originalFileName: string;
  plaintextByteLength: number;
  protocolVersion: string;
};

export interface CryptoProvider {
  readonly isProductionReady: boolean;
  readonly kind: 'demo' | 'signal';
  readonly protocolType: ProtocolType;
  readonly warning?: string;
  createIdentity(): Promise<StoredIdentityMaterial>;
  createSessionProof(
    identity: StoredIdentityMaterial,
    userId: Id<'users'>,
  ): string;
  decryptMessage(args: DecryptMessageArgs): Promise<string>;
  encryptMessage(args: EncryptMessageArgs): Promise<PreparedMessage>;
  getDeviceBundle(identity: StoredIdentityMaterial): Promise<DeviceBundle>;
  getPreKeyBundle(identity: StoredIdentityMaterial): Promise<DeviceBundle>;
  getSafetyNumber(
    identity: StoredIdentityMaterial,
    peerBundle: DeviceBundle,
  ): string;
  signMutation(
    identity: StoredIdentityMaterial,
    userId: Id<'users'>,
    operation: string,
    payload: unknown,
  ): string;
}

export interface GroupCryptoProvider {
  readonly blocker?: string;
  readonly isSupported: boolean;
  readonly protocolType: 'mls_group';
}

export interface FileCryptoProvider {
  decryptFile(
    ciphertextBase64: string,
    keyBase64: string,
    nonceBase64: string,
    destinationUri: string,
  ): Promise<string>;
  decryptFileFromUri(args: {
    ciphertextUri: string;
    destinationUri: string;
    keyBase64: string;
    nonceBase64: string;
  }): Promise<string>;
  encryptFile(args: {
    fileName: string;
    mimeType?: string | null;
    uri: string;
  }): Promise<EncryptedFileResult>;
}
