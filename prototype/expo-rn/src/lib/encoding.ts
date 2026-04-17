import naclUtil from 'tweetnacl-util';

export function encodeBase64(bytes: Uint8Array): string {
  return naclUtil.encodeBase64(bytes);
}

export function decodeBase64(value: string): Uint8Array {
  return naclUtil.decodeBase64(value);
}

export function encodeUtf8(bytes: Uint8Array): string {
  return naclUtil.encodeUTF8(bytes);
}

export function decodeUtf8(value: string): Uint8Array {
  return naclUtil.decodeUTF8(value);
}

export function encodeJsonBase64(value: unknown): string {
  return encodeBase64(decodeUtf8(JSON.stringify(value)));
}

export function decodeJsonBase64<T>(value: string): T {
  return JSON.parse(encodeUtf8(decodeBase64(value))) as T;
}
