import {defineSchema, defineTable} from 'convex/server';
import {v} from 'convex/values';

const protocolType = v.union(
  v.literal('signal_1to1'),
  v.literal('mls_group'),
  v.literal('demo_dev_only'),
);

const oneTimePreKey = v.object({
  keyId: v.string(),
  publicKey: v.string(),
});

const attachmentUploadStatus = v.union(
  v.literal('pending_upload'),
  v.literal('metadata_finalized'),
  v.literal('expired'),
);

const encryptionMetadata = v.object({
  mode: v.string(),
  nonce: v.string(),
  senderIdentityKey: v.string(),
  senderSigningKey: v.string(),
  recipientDeviceId: v.optional(v.string()),
  attachmentKeyEnvelope: v.optional(v.string()),
  attachmentMetadataCiphertext: v.optional(v.string()),
  isDemo: v.optional(v.boolean()),
});

export default defineSchema({
  users: defineTable({
    userFingerprint: v.string(),
    profileCiphertext: v.optional(v.string()),
    createdAt: v.number(),
    updatedAt: v.number(),
  }).index('by_userFingerprint', ['userFingerprint']),

  devices: defineTable({
    userId: v.id('users'),
    deviceId: v.string(),
    identityKey: v.string(),
    signingPublicKey: v.string(),
    signedPreKey: v.string(),
    signedPreKeySignature: v.string(),
    oneTimePreKeys: v.array(oneTimePreKey),
    preKeyBundleId: v.optional(v.id('preKeyBundles')),
    protocolVersion: v.string(),
    createdAt: v.number(),
    lastSeenAt: v.number(),
  })
    .index('by_deviceId', ['deviceId'])
    .index('by_userId', ['userId'])
    .index('by_user_device', ['userId', 'deviceId']),

  contacts: defineTable({
    ownerUserId: v.id('users'),
    contactUserId: v.id('users'),
    localAliasCiphertext: v.optional(v.string()),
    inviteCodeHash: v.string(),
    createdAt: v.number(),
    updatedAt: v.number(),
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_owner_contact', ['ownerUserId', 'contactUserId']),

  conversations: defineTable({
    protocolType,
    participantHash: v.optional(v.string()),
    encryptedTitle: v.optional(v.string()),
    createdByUserId: v.id('users'),
    lastMessageId: v.optional(v.id('messages')),
    createdAt: v.number(),
    updatedAt: v.number(),
  })
    .index('by_participantHash', ['participantHash'])
    .index('by_updatedAt', ['updatedAt']),

  conversationMembers: defineTable({
    conversationId: v.id('conversations'),
    userId: v.id('users'),
    deviceId: v.optional(v.string()),
    memberCiphertext: v.optional(v.string()),
    joinedAt: v.number(),
    lastReadAt: v.optional(v.number()),
  })
    .index('by_conversation', ['conversationId'])
    .index('by_user', ['userId'])
    .index('by_conversation_user', ['conversationId', 'userId']),

  messages: defineTable({
    conversationId: v.id('conversations'),
    senderUserId: v.id('users'),
    senderDeviceId: v.string(),
    clientMessageId: v.string(),
    ciphertext: v.string(),
    encryptionMetadata,
    protocolVersion: v.string(),
    attachmentIds: v.optional(v.array(v.id('encryptedAttachments'))),
    createdAt: v.number(),
    editedAt: v.optional(v.number()),
    deletedAt: v.optional(v.number()),
  })
    .index('by_conversation_createdAt', ['conversationId', 'createdAt'])
    .index('by_clientMessageId', ['clientMessageId']),

  messageReceipts: defineTable({
    conversationId: v.id('conversations'),
    messageId: v.id('messages'),
    userId: v.id('users'),
    deviceId: v.string(),
    receiptType: v.union(v.literal('delivered'), v.literal('read')),
    createdAt: v.number(),
  })
    .index('by_message', ['messageId'])
    .index('by_message_user_type', ['messageId', 'userId', 'receiptType'])
    .index('by_conversation', ['conversationId'])
    .index('by_conversation_user', ['conversationId', 'userId']),

  encryptedAttachments: defineTable({
    conversationId: v.id('conversations'),
    ownerUserId: v.id('users'),
    senderDeviceId: v.string(),
    storageId: v.id('_storage'),
    ciphertextDigest: v.string(),
    ciphertextByteLength: v.number(),
    metadataCiphertext: v.string(),
    messageId: v.optional(v.id('messages')),
    protocolVersion: v.string(),
    createdAt: v.number(),
  })
    .index('by_conversation', ['conversationId'])
    .index('by_owner', ['ownerUserId']),

  attachmentUploadSessions: defineTable({
    conversationId: v.id('conversations'),
    ownerUserId: v.id('users'),
    ownerDeviceId: v.string(),
    maxCiphertextBytes: v.number(),
    status: attachmentUploadStatus,
    storageId: v.optional(v.id('_storage')),
    attachmentId: v.optional(v.id('encryptedAttachments')),
    createdAt: v.number(),
    updatedAt: v.number(),
    expiresAt: v.number(),
  })
    .index('by_owner', ['ownerUserId'])
    .index('by_conversation', ['conversationId'])
    .index('by_expiresAt', ['expiresAt']),

  operationRateLimits: defineTable({
    scopeKey: v.string(),
    operation: v.string(),
    ownerUserId: v.id('users'),
    ownerDeviceId: v.string(),
    windowStartedAt: v.number(),
    windowEndsAt: v.number(),
    count: v.number(),
    updatedAt: v.number(),
  })
    .index('by_scopeKey', ['scopeKey'])
    .index('by_owner', ['ownerUserId'])
    .index('by_updatedAt', ['updatedAt']),

  preKeyBundles: defineTable({
    userId: v.id('users'),
    deviceId: v.string(),
    bundleId: v.string(),
    identityKey: v.string(),
    signingPublicKey: v.string(),
    signedPreKey: v.string(),
    signedPreKeySignature: v.string(),
    oneTimePreKeys: v.array(oneTimePreKey),
    protocolVersion: v.string(),
    publishedAt: v.number(),
  })
    .index('by_user_device', ['userId', 'deviceId'])
    .index('by_bundleId', ['bundleId']),

  sessionRecords: defineTable({
    ownerUserId: v.id('users'),
    ownerDeviceId: v.string(),
    peerUserId: v.id('users'),
    peerDeviceId: v.string(),
    encryptedState: v.optional(v.string()),
    protocolVersion: v.string(),
    createdAt: v.number(),
    updatedAt: v.number(),
  })
    .index('by_owner_device', ['ownerDeviceId'])
    .index('by_owner_peer', ['ownerUserId', 'peerUserId']),

  inviteCodes: defineTable({
    ownerUserId: v.id('users'),
    ownerDeviceId: v.string(),
    codeHash: v.string(),
    expiresAt: v.number(),
    createdAt: v.number(),
  })
    .index('by_codeHash', ['codeHash'])
    .index('by_owner', ['ownerUserId']),
});
