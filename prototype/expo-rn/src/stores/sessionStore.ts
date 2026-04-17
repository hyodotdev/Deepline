import {create} from 'zustand';

import type {StoredIdentityMaterial} from '@/crypto/types';
import {
  clearStoredIdentity,
  loadStoredIdentity,
  patchStoredIdentity,
  saveStoredIdentity,
} from '@/storage/secureIdentityStore';
import {
  getStoredString,
  removeStoredString,
  setStoredString,
  StorageKey,
} from '@/utils/storage';

type SessionState = {
  contactAliases: Record<string, string>;
  displayName: string;
  hydrated: boolean;
  hydrating: boolean;
  identity: StoredIdentityMaterial | null;
  lastInviteCode: string | null;
  lastInviteCodeCreatedAt: number | null;
  attachServerRegistration: (args: {
    sessionProof: string;
    userId: NonNullable<StoredIdentityMaterial['userId']>;
  }) => Promise<void>;
  clearLocalIdentity: () => Promise<void>;
  completeLocalSetup: (args: {
    displayName: string;
    identity: StoredIdentityMaterial;
  }) => Promise<void>;
  hydrate: () => Promise<void>;
  setContactAlias: (contactUserId: string, alias: string) => Promise<void>;
  setInviteCode: (inviteCode: string) => Promise<void>;
};

async function loadAliasMap(): Promise<Record<string, string>> {
  const rawValue = await getStoredString(StorageKey.ContactAliases);

  if (!rawValue) {
    return {};
  }

  return JSON.parse(rawValue) as Record<string, string>;
}

export const useSessionStore = create<SessionState>((set, get) => ({
  contactAliases: {},
  displayName: '',
  hydrated: false,
  hydrating: false,
  identity: null,
  lastInviteCode: null,
  lastInviteCodeCreatedAt: null,

  hydrate: async () => {
    if (get().hydrating || get().hydrated) {
      return;
    }

    set({hydrating: true});

    const [identity, displayName, aliases, inviteCode, inviteCreatedAt] =
      await Promise.all([
        loadStoredIdentity(),
        getStoredString(StorageKey.LocalDisplayName),
        loadAliasMap(),
        getStoredString(StorageKey.LastInviteCode),
        getStoredString(StorageKey.LastInviteCodeCreatedAt),
      ]);

    set({
      contactAliases: aliases,
      displayName: displayName ?? '',
      hydrated: true,
      hydrating: false,
      identity,
      lastInviteCode: inviteCode,
      lastInviteCodeCreatedAt: inviteCreatedAt
        ? Number(inviteCreatedAt)
        : null,
    });
  },

  completeLocalSetup: async ({displayName, identity}) => {
    await Promise.all([
      saveStoredIdentity(identity),
      setStoredString(StorageKey.DidFinishOnboarding, 'true'),
      setStoredString(StorageKey.LocalDisplayName, displayName),
    ]);

    set({
      displayName,
      identity,
    });
  },

  attachServerRegistration: async ({sessionProof, userId}) => {
    const nextIdentity = await patchStoredIdentity({
      sessionProof,
      userId,
    });

    if (!nextIdentity) {
      throw new Error('No local identity is available to register.');
    }

    await Promise.all([
      setStoredString(StorageKey.ActiveDeviceId, nextIdentity.deviceId),
      setStoredString(StorageKey.ActiveUserId, userId),
    ]);

    set({identity: nextIdentity});
  },

  setInviteCode: async (inviteCode) => {
    const createdAt = Date.now();

    await Promise.all([
      setStoredString(StorageKey.LastInviteCode, inviteCode),
      setStoredString(
        StorageKey.LastInviteCodeCreatedAt,
        String(createdAt),
      ),
    ]);

    set({
      lastInviteCode: inviteCode,
      lastInviteCodeCreatedAt: createdAt,
    });
  },

  setContactAlias: async (contactUserId, alias) => {
    const nextAliases = {
      ...get().contactAliases,
      [contactUserId]: alias,
    };

    await setStoredString(
      StorageKey.ContactAliases,
      JSON.stringify(nextAliases),
    );

    set({contactAliases: nextAliases});
  },

  clearLocalIdentity: async () => {
    await Promise.all([
      clearStoredIdentity(),
      removeStoredString(StorageKey.ActiveDeviceId),
      removeStoredString(StorageKey.ActiveUserId),
      removeStoredString(StorageKey.ContactAliases),
      removeStoredString(StorageKey.LastInviteCode),
      removeStoredString(StorageKey.LastInviteCodeCreatedAt),
      removeStoredString(StorageKey.LocalDisplayName),
      removeStoredString(StorageKey.DidFinishOnboarding),
    ]);

    set({
      contactAliases: {},
      displayName: '',
      identity: null,
      lastInviteCode: null,
      lastInviteCodeCreatedAt: null,
    });
  },
}));
