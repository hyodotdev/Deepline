import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import path from 'node:path';

import {
  assertNoPlaintextFields,
  assertOpaqueTransportValue,
} from '../src/lib/plaintext';

assert.throws(() =>
  assertNoPlaintextFields('sendEncryptedMessage', {
    body: 'plaintext body',
  }),
);

assert.throws(() =>
  assertNoPlaintextFields('uploadEncryptedAttachmentMetadata', {
    fileName: 'notes.txt',
  }),
);

assert.throws(() => assertOpaqueTransportValue('ciphertext', 'plain text'));

const schema = readFileSync(path.join(process.cwd(), 'convex/schema.ts'), 'utf8');
const messenger = readFileSync(
  path.join(process.cwd(), 'convex/messenger.ts'),
  'utf8',
);

assert.match(schema, /ciphertext:\s*v\.string\(\)/);
assert.doesNotMatch(schema, /body:\s*v\./);
assert.doesNotMatch(messenger, /privateKey/);
assert.doesNotMatch(messenger, /secretKey/);

console.log('Security validation checks passed.');
