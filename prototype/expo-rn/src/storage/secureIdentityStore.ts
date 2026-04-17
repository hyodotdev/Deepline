import * as SecureStore from 'expo-secure-store';

import type {LocalSessionRecord, StoredIdentityMaterial} from '@/crypto/types';

const SecureKey = {
  IdentityMaterial: 'deepline.identityMaterial',
  SessionRecords: 'deepline.localSessionRecords',
} as const;

export async function loadStoredIdentity(): Promise<StoredIdentityMaterial | null> {
  const rawValue = await SecureStore.getItemAsync(SecureKey.IdentityMaterial);

  if (!rawValue) {
    return null;
  }

  return JSON.parse(rawValue) as StoredIdentityMaterial;
}

export async function saveStoredIdentity(
  identity: StoredIdentityMaterial,
): Promise<void> {
  await SecureStore.setItemAsync(
    SecureKey.IdentityMaterial,
    JSON.stringify(identity),
  );
}

export async function patchStoredIdentity(
  patch: Partial<StoredIdentityMaterial>,
): Promise<StoredIdentityMaterial | null> {
  const current = await loadStoredIdentity();

  if (!current) {
    return null;
  }

  const nextValue = {
    ...current,
    ...patch,
  };

  await saveStoredIdentity(nextValue);

  return nextValue;
}

export async function clearStoredIdentity(): Promise<void> {
  await SecureStore.deleteItemAsync(SecureKey.IdentityMaterial);
}

export async function loadSessionRecords(): Promise<
  Record<string, LocalSessionRecord>
> {
  const rawValue = await SecureStore.getItemAsync(SecureKey.SessionRecords);

  if (!rawValue) {
    return {};
  }

  return JSON.parse(rawValue) as Record<string, LocalSessionRecord>;
}

export async function saveSessionRecord(
  record: LocalSessionRecord,
): Promise<void> {
  const records = await loadSessionRecords();

  records[record.peerDeviceId] = record;

  await SecureStore.setItemAsync(
    SecureKey.SessionRecords,
    JSON.stringify(records),
  );
}
