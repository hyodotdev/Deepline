import nacl from 'tweetnacl';

import {decodeBase64, decodeUtf8, encodeBase64} from './encoding';

type JsonObject = {[key: string]: JsonValue};
type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonObject
  | JsonValue[];

export function stableStringify(value: unknown): string {
  return JSON.stringify(sortValue(value));
}

function sortValue(value: unknown): JsonValue {
  if (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean' ||
    value === null
  ) {
    return value;
  }

  if (Array.isArray(value)) {
    return value.map(sortValue);
  }

  if (typeof value === 'object' && value) {
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<JsonObject>((accumulator, key) => {
        accumulator[key] = sortValue((value as Record<string, unknown>)[key]);
        return accumulator;
      }, {});
  }

  return String(value);
}

export function hashString(value: string): string {
  return encodeBase64(nacl.hash(decodeUtf8(value)));
}

export function createIdentityFingerprint(values: string[]): string {
  return hashString(values.join(':')).slice(0, 32);
}

export function buildSessionChallenge(userId: string, deviceId: string): string {
  return `deepline.session.v1:${userId}:${deviceId}`;
}

export function buildMutationChallenge(
  operation: string,
  payload: unknown,
): string {
  return `deepline.request.v1:${operation}:${stableStringify(payload)}`;
}

export function signDetachedBase64(
  secretKeyBase64: string,
  message: string,
): string {
  const signature = nacl.sign.detached(
    decodeUtf8(message),
    decodeBase64(secretKeyBase64),
  );

  return encodeBase64(signature);
}

export function verifyDetachedBase64(
  publicKeyBase64: string,
  message: string,
  signatureBase64: string,
): boolean {
  return nacl.sign.detached.verify(
    decodeUtf8(message),
    decodeBase64(signatureBase64),
    decodeBase64(publicKeyBase64),
  );
}

export function maskId(value: string, visible = 6): string {
  if (value.length <= visible * 2) {
    return value;
  }

  return `${value.slice(0, visible)}…${value.slice(-visible)}`;
}
