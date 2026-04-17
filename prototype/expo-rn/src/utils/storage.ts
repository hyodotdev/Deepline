import AsyncStorage from '@react-native-async-storage/async-storage';

export const StorageKey = {
  DidFinishOnboarding: 'deepline.didFinishOnboarding',
  LocalDisplayName: 'deepline.localDisplayName',
  ActiveUserId: 'deepline.activeUserId',
  ActiveDeviceId: 'deepline.activeDeviceId',
  ContactAliases: 'deepline.contactAliases',
  LastInviteCode: 'deepline.lastInviteCode',
  LastInviteCodeCreatedAt: 'deepline.lastInviteCodeCreatedAt',
} as const;

export async function getStoredString(
  key: (typeof StorageKey)[keyof typeof StorageKey],
): Promise<string | null> {
  return AsyncStorage.getItem(key);
}

export async function setStoredString(
  key: (typeof StorageKey)[keyof typeof StorageKey],
  value: string,
): Promise<void> {
  await AsyncStorage.setItem(key, value);
}

export async function removeStoredString(
  key: (typeof StorageKey)[keyof typeof StorageKey],
): Promise<void> {
  await AsyncStorage.removeItem(key);
}
