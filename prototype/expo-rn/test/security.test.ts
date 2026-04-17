import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import test from 'node:test';

import {
  assertNoPlaintextFields,
  assertOpaqueTransportValue,
} from '../src/lib/plaintext';
import {
  deserializeMessageEnvelope,
  serializeMessageEnvelope,
} from '../src/lib/messagePayload';

const root = process.cwd();

test('sendEncryptedMessage rejects obvious plaintext fields', () => {
  assert.throws(
    () =>
      assertNoPlaintextFields('sendEncryptedMessage', {
        body: 'hello in plaintext',
        ciphertext: 'ZmFrZWNpcGhlcnRleHQ=',
      }),
    /forbidden plaintext fields/,
  );
});

test('attachment metadata rejects plaintext file names', () => {
  assert.throws(
    () =>
      assertNoPlaintextFields('uploadEncryptedAttachmentMetadata', {
        fileName: 'holiday.jpg',
        metadataCiphertext: 'ZmFrZW1ldGFkYXRhY2lwaGVy',
      }),
    /forbidden plaintext fields/,
  );
});

test('ciphertext guard rejects human-readable text', () => {
  assert.throws(
    () => assertOpaqueTransportValue('ciphertext', 'hello world'),
    /opaque encrypted or encoded value/,
  );
});

test('messages schema stores ciphertext and not plaintext body fields', () => {
  const schema = readFileSync(path.join(root, 'convex/schema.ts'), 'utf8');

  assert.match(schema, /ciphertext:\s*v\.string\(\)/);
  assert.match(schema, /storageId:\s*v\.id\('_storage'\)/);
  assert.doesNotMatch(schema, /body:\s*v\./);
  assert.doesNotMatch(schema, /messageBody:\s*v\./);
});

test('convex mutations do not send private keys', () => {
  const messenger = readFileSync(path.join(root, 'convex/messenger.ts'), 'utf8');

  assert.doesNotMatch(messenger, /privateKey/);
  assert.doesNotMatch(messenger, /secretKey/);
});

test('message envelopes carry attachment keys only inside ciphertext payloads', () => {
  const serialized = serializeMessageEnvelope({
    attachments: [
      {
        attachmentId: 'attachment_123',
        ciphertextByteLength: 128,
        ciphertextDigest: 'ZmFrZURpZ2VzdEZha2VEaWdlc3Q=',
        fileName: 'statement.pdf',
        keyBase64: 'ZmFrZUtleUZha2VLZXlGYWtlS2V5',
        mimeType: 'application/pdf',
        nonceBase64: 'ZmFrZU5vbmNlRmFrZU5vbmNlRmFrZQ==',
        plaintextByteLength: 120,
        protocolVersion: 'demo-file-secretbox-v1',
      },
    ],
    text: 'see attachment',
  });
  const parsed = deserializeMessageEnvelope(serialized);

  assert.equal(parsed.attachments[0]?.fileName, 'statement.pdf');
  assert.equal(parsed.attachments[0]?.attachmentId, 'attachment_123');
  assert.equal(parsed.text, 'see attachment');
});
