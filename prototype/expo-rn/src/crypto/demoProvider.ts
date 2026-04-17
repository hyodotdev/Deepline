import nacl from 'tweetnacl';

import {
  buildMutationChallenge,
  buildSessionChallenge,
  createIdentityFingerprint,
  hashString,
  signDetachedBase64,
} from '@/lib/security';
import {
  decodeBase64,
  decodeUtf8,
  encodeBase64,
  encodeUtf8,
} from '@/lib/encoding';

import type {
  CryptoProvider,
  DecryptMessageArgs,
  DeviceBundle,
  EncryptMessageArgs,
  KeyPairBase64,
  StoredIdentityMaterial,
  StoredOneTimePreKey,
} from './types';

const DEMO_PROTOCOL_VERSION = 'demo-nacl-box-v1';

function encodeKeyPair(keyPair: nacl.BoxKeyPair | nacl.SignKeyPair): KeyPairBase64 {
  return {
    publicKey: encodeBase64(keyPair.publicKey),
    secretKey: encodeBase64(keyPair.secretKey),
  };
}

function randomDeviceId(): string {
  return `device_${crypto.randomUUID().replace(/-/g, '')}`;
}

function generateOneTimePreKeys(count = 5): StoredOneTimePreKey[] {
  return Array.from({length: count}, () => {
    const keyPair = nacl.box.keyPair();

    return {
      keyId: crypto.randomUUID(),
      publicKey: encodeBase64(keyPair.publicKey),
      secretKey: encodeBase64(keyPair.secretKey),
    };
  });
}

class DemoProvider implements CryptoProvider {
  readonly isProductionReady = false;
  readonly kind = 'demo' as const;
  readonly protocolType = 'demo_dev_only' as const;
  readonly warning =
    'NOT PRODUCTION SAFE: Deepline currently encrypts 1:1 messages with a demo NaCl box provider instead of Signal Double Ratchet.';

  async createIdentity(): Promise<StoredIdentityMaterial> {
    const identityKeyPair = encodeKeyPair(nacl.box.keyPair());
    const signingKeyPair = encodeKeyPair(nacl.sign.keyPair());
    const signedPreKeyPair = encodeKeyPair(nacl.box.keyPair());
    const signedPreKeySignature = signDetachedBase64(
      signingKeyPair.secretKey,
      signedPreKeyPair.publicKey,
    );

    return {
      createdAt: Date.now(),
      deviceId: randomDeviceId(),
      identityKeyPair,
      isProductionReady: false,
      kind: 'demo',
      oneTimePreKeys: generateOneTimePreKeys(),
      protocolType: 'demo_dev_only',
      signedPreKeyPair,
      signedPreKeySignature,
      signingKeyPair,
      userFingerprint: createIdentityFingerprint([
        identityKeyPair.publicKey,
        signingKeyPair.publicKey,
      ]),
    };
  }

  createSessionProof(
    identity: StoredIdentityMaterial,
    userId: NonNullable<StoredIdentityMaterial['userId']>,
  ): string {
    return signDetachedBase64(
      identity.signingKeyPair.secretKey,
      buildSessionChallenge(userId, identity.deviceId),
    );
  }

  signMutation(
    identity: StoredIdentityMaterial,
    userId: NonNullable<StoredIdentityMaterial['userId']>,
    operation: string,
    payload: unknown,
  ): string {
    return signDetachedBase64(
      identity.signingKeyPair.secretKey,
      buildMutationChallenge(operation, {
        deviceId: identity.deviceId,
        payload,
        userId,
      }),
    );
  }

  async getDeviceBundle(identity: StoredIdentityMaterial): Promise<DeviceBundle> {
    return this.getPreKeyBundle(identity);
  }

  async getPreKeyBundle(identity: StoredIdentityMaterial): Promise<DeviceBundle> {
    return {
      bundleId: `${identity.deviceId}:${identity.createdAt}`,
      deviceId: identity.deviceId,
      identityKey: identity.identityKeyPair.publicKey,
      oneTimePreKeys: identity.oneTimePreKeys.map(({keyId, publicKey}) => ({
        keyId,
        publicKey,
      })),
      protocolVersion: DEMO_PROTOCOL_VERSION,
      provider: 'demo',
      signedPreKey: identity.signedPreKeyPair.publicKey,
      signedPreKeySignature: identity.signedPreKeySignature,
      signingPublicKey: identity.signingKeyPair.publicKey,
      userId: identity.userId,
    };
  }

  async encryptMessage(args: EncryptMessageArgs) {
    const nonce = nacl.randomBytes(nacl.box.nonceLength);
    const ciphertext = nacl.box(
      decodeUtf8(args.plaintext),
      nonce,
      decodeBase64(args.recipientBundle.identityKey),
      decodeBase64(args.identity.identityKeyPair.secretKey),
    );

    return {
      ciphertext: encodeBase64(ciphertext),
      encryptionMetadata: {
        attachmentKeyEnvelope: args.attachmentKeyEnvelope,
        attachmentMetadataCiphertext: args.attachmentMetadataCiphertext,
        isDemo: true,
        mode: 'demo_box',
        nonce: encodeBase64(nonce),
        recipientDeviceId: args.recipientBundle.deviceId,
        senderIdentityKey: args.identity.identityKeyPair.publicKey,
        senderSigningKey: args.identity.signingKeyPair.publicKey,
      },
      protocolVersion: DEMO_PROTOCOL_VERSION,
    };
  }

  async decryptMessage(args: DecryptMessageArgs): Promise<string> {
    const opened = nacl.box.open(
      decodeBase64(args.ciphertext),
      decodeBase64(args.encryptionMetadata.nonce),
      decodeBase64(args.encryptionMetadata.senderIdentityKey),
      decodeBase64(args.identity.identityKeyPair.secretKey),
    );

    if (!opened) {
      throw new Error(
        'Failed to decrypt message with the current demo identity keys.',
      );
    }

    return encodeUtf8(opened);
  }

  getSafetyNumber(
    identity: StoredIdentityMaterial,
    peerBundle: DeviceBundle,
  ): string {
    return hashString(
      [
        identity.identityKeyPair.publicKey,
        identity.signingKeyPair.publicKey,
        peerBundle.identityKey,
        peerBundle.signingPublicKey,
      ]
        .sort()
        .join(':'),
    ).slice(0, 60);
  }
}

export const demoProvider = new DemoProvider();
