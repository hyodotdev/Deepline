import * as FileSystem from 'expo-file-system/legacy';
import nacl from 'tweetnacl';

import {decodeBase64, decodeUtf8, encodeBase64} from '@/lib/encoding';
import {hashString} from '@/lib/security';

import type {EncryptedFileResult, FileCryptoProvider} from './types';

const FILE_CRYPTO_PROTOCOL = 'demo-file-secretbox-v1';
const ENCRYPTED_CACHE_DIR = `${FileSystem.cacheDirectory ?? ''}deepline-encrypted`;

async function ensureEncryptedCacheDir(): Promise<void> {
  if (!FileSystem.cacheDirectory) {
    throw new Error('No cache directory is available for encrypted attachment staging.');
  }

  await FileSystem.makeDirectoryAsync(ENCRYPTED_CACHE_DIR, {
    intermediates: true,
  });
}

export class DeeplineFileCryptoProvider implements FileCryptoProvider {
  async encryptFile(args: {
    fileName: string;
    mimeType?: string | null;
    uri: string;
  }): Promise<EncryptedFileResult> {
    const base64Payload = await FileSystem.readAsStringAsync(args.uri, {
      encoding: FileSystem.EncodingType.Base64,
    });
    const fileBytes = decodeBase64(base64Payload);

    const fileKey = nacl.randomBytes(nacl.secretbox.keyLength);
    const fileNonce = nacl.randomBytes(nacl.secretbox.nonceLength);
    const metadataNonce = nacl.randomBytes(nacl.secretbox.nonceLength);
    const ciphertext = nacl.secretbox(fileBytes, fileNonce, fileKey);

    const metadata = JSON.stringify({
      fileName: args.fileName,
      mimeType: args.mimeType ?? 'application/octet-stream',
      size: fileBytes.byteLength,
    });

    const metadataCiphertext = nacl.secretbox(
      decodeUtf8(metadata),
      metadataNonce,
      fileKey,
    );

    await ensureEncryptedCacheDir();

    const ciphertextBase64 = encodeBase64(ciphertext);
    const nonceBase64 = encodeBase64(fileNonce);
    const metadataEnvelope = encodeBase64(
      decodeUtf8(
        JSON.stringify({
          ciphertext: encodeBase64(metadataCiphertext),
          nonce: encodeBase64(metadataNonce),
        }),
      ),
    );
    const ciphertextFileUri = `${ENCRYPTED_CACHE_DIR}/${crypto.randomUUID()}.bin`;

    await FileSystem.writeAsStringAsync(ciphertextFileUri, ciphertextBase64, {
      encoding: FileSystem.EncodingType.Base64,
    });

    return {
      ciphertextByteLength: ciphertext.byteLength,
      ciphertextDigest: hashString(ciphertextBase64),
      ciphertextFileUri,
      keyBase64: encodeBase64(fileKey),
      metadataCiphertext: metadataEnvelope,
      mimeType: args.mimeType ?? 'application/octet-stream',
      nonceBase64,
      originalFileName: args.fileName,
      plaintextByteLength: fileBytes.byteLength,
      protocolVersion: FILE_CRYPTO_PROTOCOL,
    };
  }

  async decryptFile(
    ciphertextBase64: string,
    keyBase64: string,
    nonceBase64: string,
    destinationUri: string,
  ): Promise<string> {
    const plaintext = nacl.secretbox.open(
      decodeBase64(ciphertextBase64),
      decodeBase64(nonceBase64),
      decodeBase64(keyBase64),
    );

    if (!plaintext) {
      throw new Error('Unable to decrypt attachment with the supplied file key.');
    }

    await FileSystem.writeAsStringAsync(destinationUri, encodeBase64(plaintext), {
      encoding: FileSystem.EncodingType.Base64,
    });

    return destinationUri;
  }

  async decryptFileFromUri(args: {
    ciphertextUri: string;
    destinationUri: string;
    keyBase64: string;
    nonceBase64: string;
  }): Promise<string> {
    const ciphertextBase64 = await FileSystem.readAsStringAsync(args.ciphertextUri, {
      encoding: FileSystem.EncodingType.Base64,
    });

    return this.decryptFile(
      ciphertextBase64,
      args.keyBase64,
      args.nonceBase64,
      args.destinationUri,
    );
  }
}

export const fileCryptoProvider = new DeeplineFileCryptoProvider();
