import type {GroupCryptoProvider} from './types';

export const MLS_BLOCKER =
  'A production-ready audited MLS client that integrates cleanly with Expo React Native was not available during implementation. Deepline keeps the group crypto layer behind an interface and blocks MLS group creation for now.';

export const groupCryptoProvider: GroupCryptoProvider = {
  blocker: MLS_BLOCKER,
  isSupported: false,
  protocolType: 'mls_group',
};
