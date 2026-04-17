import {v} from 'convex/values';

import type {Id} from './_generated/dataModel';
import {mutation, query} from './_generated/server';
import type {MutationCtx, QueryCtx} from './_generated/server';
import {hashString} from '../src/lib/security';
import {
  assertNoPlaintextFields,
  assertOpaqueTransportValue,
} from '../src/lib/plaintext';
import {requireSession, requireSignedMutation} from './security';

const protocolType = v.union(
  v.literal('signal_1to1'),
  v.literal('mls_group'),
  v.literal('demo_dev_only'),
);

const sessionAuth = v.object({
  userId: v.id('users'),
  deviceId: v.string(),
  sessionProof: v.string(),
});

const mutationAuth = v.object({
  userId: v.id('users'),
  deviceId: v.string(),
  sessionProof: v.string(),
  requestSignature: v.string(),
});

const oneTimePreKey = v.object({
  keyId: v.string(),
  publicKey: v.string(),
});

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

const MAX_MESSAGE_PAGE_SIZE = 200;
const MAX_ATTACHMENTS_PER_MESSAGE = 5;
const MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024;
const ATTACHMENT_UPLOAD_TTL_MS = 1000 * 60 * 10;
const ATTACHMENT_CONTENT_TYPE = 'application/octet-stream';
const PRODUCTION_ENV_NAMES = new Set(['prod', 'production']);
const RATE_LIMITS = {
  addContactByInviteCode: {maxRequests: 12, windowMs: 60_000},
  createAttachmentDownloadUrl: {maxRequests: 120, windowMs: 60_000},
  createAttachmentUploadSession: {maxRequests: 20, windowMs: 10 * 60_000},
  createConversation: {maxRequests: 20, windowMs: 60_000},
  createInviteCode: {maxRequests: 6, windowMs: 60_000},
  sendEncryptedMessage: {maxRequests: 45, windowMs: 60_000},
  uploadEncryptedAttachmentMetadata: {maxRequests: 20, windowMs: 10 * 60_000},
} as const;

function isStrictCryptoEnforcementEnabled(): boolean {
  const explicitMode = String(
    process.env.DEEPLINE_CRYPTO_ENFORCEMENT_MODE ?? '',
  ).toLowerCase();

  if (explicitMode === 'strict') {
    return true;
  }

  if (explicitMode === 'allow_demo') {
    return false;
  }

  const appEnv = String(
    process.env.DEEPLINE_APP_ENV ??
      process.env.APP_ENV ??
      process.env.EXPO_PUBLIC_APP_ENV ??
      '',
  ).toLowerCase();

  return PRODUCTION_ENV_NAMES.has(appEnv);
}

function assertAllowedConversationProtocol(protocol: string): void {
  if (
    isStrictCryptoEnforcementEnabled() &&
    protocol === 'demo_dev_only'
  ) {
    throw new Error(
      'Production crypto enforcement rejected a demo conversation protocol.',
    );
  }
}

function assertAllowedPreKeyProtocol(protocolVersion: string): void {
  if (
    isStrictCryptoEnforcementEnabled() &&
    protocolVersion.startsWith('demo-')
  ) {
    throw new Error(
      'Production crypto enforcement rejected a demo device bundle protocol.',
    );
  }
}

function assertAllowedMessageProtocol(args: {
  isDemo?: boolean;
  protocolVersion: string;
}): void {
  if (
    isStrictCryptoEnforcementEnabled() &&
    (args.isDemo || args.protocolVersion.startsWith('demo-'))
  ) {
    throw new Error(
      'Production crypto enforcement rejected a demo message protocol.',
    );
  }
}

function uniqueParticipantHash(ids: Id<'users'>[]): string {
  return hashString(
    ids
      .map((entry) => String(entry))
      .sort()
      .join(':'),
  );
}

type DbCtx = Pick<QueryCtx, 'db'>;
type AuthContext = {
  deviceId: string;
  userId: Id<'users'>;
};

async function enforceRateLimit(
  ctx: MutationCtx,
  auth: AuthContext,
  operation: keyof typeof RATE_LIMITS,
): Promise<void> {
  const {maxRequests, windowMs} = RATE_LIMITS[operation];
  const now = Date.now();
  const windowStartedAt = now - (now % windowMs);
  const windowEndsAt = windowStartedAt + windowMs;
  const scopeKey = `${operation}:${String(auth.userId)}:${auth.deviceId}:${windowStartedAt}`;
  const existing = await ctx.db
    .query('operationRateLimits')
    .withIndex('by_scopeKey', (query) => query.eq('scopeKey', scopeKey))
    .unique();

  if (existing) {
    if (existing.count >= maxRequests) {
      throw new Error(`Rate limit exceeded for ${operation}. Try again shortly.`);
    }

    await ctx.db.patch(existing._id, {
      count: existing.count + 1,
      updatedAt: now,
      windowEndsAt,
    });
    return;
  }

  await ctx.db.insert('operationRateLimits', {
    count: 1,
    operation,
    ownerDeviceId: auth.deviceId,
    ownerUserId: auth.userId,
    scopeKey,
    updatedAt: now,
    windowEndsAt,
    windowStartedAt,
  });
}

async function ensureContact(
  ctx: DbCtx,
  ownerUserId: Id<'users'>,
  contactUserId: Id<'users'>,
): Promise<void> {
  const contact = await ctx.db
    .query('contacts')
    .withIndex('by_owner_contact', (query) =>
      query.eq('ownerUserId', ownerUserId).eq('contactUserId', contactUserId),
    )
    .unique();

  if (!contact) {
    throw new Error('Users must be contacts before starting a conversation.');
  }
}

async function ensureConversationMember(
  ctx: DbCtx,
  conversationId: Id<'conversations'>,
  userId: Id<'users'>,
): Promise<void> {
  const membership = await ctx.db
    .query('conversationMembers')
    .withIndex('by_conversation_user', (query) =>
      query.eq('conversationId', conversationId).eq('userId', userId),
    )
    .unique();

  if (!membership) {
    throw new Error('Conversation membership is required.');
  }
}

async function getPrimaryDeviceId(
  ctx: DbCtx,
  userId: Id<'users'>,
): Promise<string | undefined> {
  const devices = await ctx.db
    .query('devices')
    .withIndex('by_userId', (query) => query.eq('userId', userId))
    .collect();

  return devices.sort((left, right) => right.lastSeenAt - left.lastSeenAt)[0]
    ?.deviceId;
}

async function ensureOutboundAttachments(
  ctx: MutationCtx,
  args: {
    attachmentIds?: Id<'encryptedAttachments'>[];
    conversationId: Id<'conversations'>;
    deviceId: string;
    userId: Id<'users'>;
  },
) {
  const attachmentIds = args.attachmentIds ?? [];

  if (attachmentIds.length > MAX_ATTACHMENTS_PER_MESSAGE) {
    throw new Error(`A single message can include at most ${MAX_ATTACHMENTS_PER_MESSAGE} attachments.`);
  }

  const attachments = await Promise.all(
    attachmentIds.map(async (attachmentId) => {
      const attachment = await ctx.db.get(attachmentId);

      if (!attachment) {
        throw new Error('Attachment metadata was not found.');
      }

      if (
        attachment.conversationId !== args.conversationId ||
        attachment.ownerUserId !== args.userId ||
        attachment.senderDeviceId !== args.deviceId
      ) {
        throw new Error('Attachment ownership check failed.');
      }

      if (attachment.messageId) {
        throw new Error('Attachment metadata has already been bound to a message.');
      }

      return attachment;
    }),
  );

  return attachments;
}

export const createOrGetUser = mutation({
  args: {
    identityFingerprint: v.string(),
    profileCiphertext: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    if (args.profileCiphertext) {
      assertOpaqueTransportValue('profileCiphertext', args.profileCiphertext);
    }

    const existing = await ctx.db
      .query('users')
      .withIndex('by_userFingerprint', (query) =>
        query.eq('userFingerprint', args.identityFingerprint),
      )
      .unique();

    if (existing) {
      return existing;
    }

    const now = Date.now();
    const userId = await ctx.db.insert('users', {
      createdAt: now,
      profileCiphertext: args.profileCiphertext,
      updatedAt: now,
      userFingerprint: args.identityFingerprint,
    });

    return ctx.db.get(userId);
  },
});

export const registerDevice = mutation({
  args: {
    userId: v.id('users'),
    deviceId: v.string(),
    identityKey: v.string(),
    signingPublicKey: v.string(),
    signedPreKey: v.string(),
    signedPreKeySignature: v.string(),
    oneTimePreKeys: v.array(oneTimePreKey),
    protocolVersion: v.string(),
  },
  handler: async (ctx, args) => {
    assertAllowedPreKeyProtocol(args.protocolVersion);
    assertOpaqueTransportValue('identityKey', args.identityKey);
    assertOpaqueTransportValue('signingPublicKey', args.signingPublicKey);
    assertOpaqueTransportValue('signedPreKey', args.signedPreKey);
    assertOpaqueTransportValue(
      'signedPreKeySignature',
      args.signedPreKeySignature,
    );

    const now = Date.now();
    const existingDevice = await ctx.db
      .query('devices')
      .withIndex('by_user_device', (query) =>
        query.eq('userId', args.userId).eq('deviceId', args.deviceId),
      )
      .unique();

    const userDevices = await ctx.db
      .query('devices')
      .withIndex('by_userId', (query) => query.eq('userId', args.userId))
      .collect();

    if (!existingDevice && userDevices.length > 0) {
      throw new Error(
        'Additional device registration is blocked until a verified multi-device handoff is implemented.',
      );
    }

    if (existingDevice) {
      await ctx.db.patch(existingDevice._id, {
        identityKey: args.identityKey,
        lastSeenAt: now,
        oneTimePreKeys: args.oneTimePreKeys,
        protocolVersion: args.protocolVersion,
        signedPreKey: args.signedPreKey,
        signedPreKeySignature: args.signedPreKeySignature,
        signingPublicKey: args.signingPublicKey,
      });

      return ctx.db.get(existingDevice._id);
    }

    const deviceId = await ctx.db.insert('devices', {
      createdAt: now,
      deviceId: args.deviceId,
      identityKey: args.identityKey,
      lastSeenAt: now,
      oneTimePreKeys: args.oneTimePreKeys,
      protocolVersion: args.protocolVersion,
      signedPreKey: args.signedPreKey,
      signedPreKeySignature: args.signedPreKeySignature,
      signingPublicKey: args.signingPublicKey,
      userId: args.userId,
    });

    return ctx.db.get(deviceId);
  },
});

export const publishPreKeyBundle = mutation({
  args: {
    auth: mutationAuth,
    bundleId: v.string(),
    identityKey: v.string(),
    signingPublicKey: v.string(),
    signedPreKey: v.string(),
    signedPreKeySignature: v.string(),
    oneTimePreKeys: v.array(oneTimePreKey),
    protocolVersion: v.string(),
  },
  handler: async (ctx, args) => {
    assertAllowedPreKeyProtocol(args.protocolVersion);
    const payload = {
      bundleId: args.bundleId,
      deviceId: args.auth.deviceId,
      identityKey: args.identityKey,
      oneTimePreKeys: args.oneTimePreKeys,
      protocolVersion: args.protocolVersion,
      signedPreKey: args.signedPreKey,
      signedPreKeySignature: args.signedPreKeySignature,
      signingPublicKey: args.signingPublicKey,
      userId: args.auth.userId,
    };

    await requireSignedMutation(ctx, 'publishPreKeyBundle', args.auth, payload);

    const existing = await ctx.db
      .query('preKeyBundles')
      .withIndex('by_user_device', (query) =>
        query.eq('userId', args.auth.userId).eq('deviceId', args.auth.deviceId),
      )
      .unique();

    const now = Date.now();

    if (existing) {
      await ctx.db.patch(existing._id, {
        bundleId: args.bundleId,
        identityKey: args.identityKey,
        oneTimePreKeys: args.oneTimePreKeys,
        protocolVersion: args.protocolVersion,
        publishedAt: now,
        signedPreKey: args.signedPreKey,
        signedPreKeySignature: args.signedPreKeySignature,
        signingPublicKey: args.signingPublicKey,
      });

      const device = await ctx.db
        .query('devices')
        .withIndex('by_user_device', (query) =>
          query
            .eq('userId', args.auth.userId)
            .eq('deviceId', args.auth.deviceId),
        )
        .unique();

      if (device) {
        await ctx.db.patch(device._id, {
          oneTimePreKeys: args.oneTimePreKeys,
          preKeyBundleId: existing._id,
          signedPreKey: args.signedPreKey,
          signedPreKeySignature: args.signedPreKeySignature,
          signingPublicKey: args.signingPublicKey,
        });
      }

      return ctx.db.get(existing._id);
    }

    const bundleId = await ctx.db.insert('preKeyBundles', {
      bundleId: args.bundleId,
      deviceId: args.auth.deviceId,
      identityKey: args.identityKey,
      oneTimePreKeys: args.oneTimePreKeys,
      protocolVersion: args.protocolVersion,
      publishedAt: now,
      signedPreKey: args.signedPreKey,
      signedPreKeySignature: args.signedPreKeySignature,
      signingPublicKey: args.signingPublicKey,
      userId: args.auth.userId,
    });

    const device = await ctx.db
      .query('devices')
      .withIndex('by_user_device', (query) =>
        query.eq('userId', args.auth.userId).eq('deviceId', args.auth.deviceId),
      )
      .unique();

    if (device) {
      await ctx.db.patch(device._id, {
        oneTimePreKeys: args.oneTimePreKeys,
        preKeyBundleId: bundleId,
        signedPreKey: args.signedPreKey,
        signedPreKeySignature: args.signedPreKeySignature,
        signingPublicKey: args.signingPublicKey,
      });
    }

    return ctx.db.get(bundleId);
  },
});

export const getDeviceBundle = query({
  args: {
    auth: sessionAuth,
    targetUserId: v.id('users'),
    targetDeviceId: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    await requireSession(ctx, args.auth);

    if (args.targetUserId !== args.auth.userId) {
      await ensureContact(ctx, args.auth.userId, args.targetUserId);
    }

    let bundle;

    if (args.targetDeviceId) {
      const targetDeviceId = args.targetDeviceId;
      bundle = await ctx.db
        .query('preKeyBundles')
        .withIndex('by_user_device', (query) =>
          query.eq('userId', args.targetUserId).eq('deviceId', targetDeviceId),
        )
        .unique();
    } else {
      const bundles = await ctx.db
        .query('preKeyBundles')
        .withIndex('by_user_device', (query) => query.eq('userId', args.targetUserId))
        .collect();

      bundle = bundles.sort((left, right) => right.publishedAt - left.publishedAt)[0];
    }

    if (!bundle) {
      throw new Error('No published device bundle found.');
    }

    return bundle;
  },
});

export const createConversation = mutation({
  args: {
    auth: mutationAuth,
    participantUserIds: v.array(v.id('users')),
    protocolType,
    encryptedTitle: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const payload = {
      encryptedTitle: args.encryptedTitle ?? null,
      participantUserIds: [...args.participantUserIds].map(String),
      protocolType: args.protocolType,
    };

    await requireSignedMutation(ctx, 'createConversation', args.auth, payload);
    await enforceRateLimit(ctx, args.auth, 'createConversation');
    assertAllowedConversationProtocol(args.protocolType);

    if (args.protocolType === 'mls_group') {
      throw new Error(
        'MLS group creation is blocked until a production-ready MLS runtime is available for Expo.',
      );
    }

    const uniqueParticipants = Array.from(
      new Set([...args.participantUserIds, args.auth.userId].map(String)),
    ) as string[];

    if (uniqueParticipants.length !== 2) {
      throw new Error('The current MVP supports only 1:1 conversations.');
    }

    const participantIds = uniqueParticipants as Id<'users'>[];

    await Promise.all(
      participantIds
        .filter((entry) => entry !== args.auth.userId)
        .map((entry) => ensureContact(ctx, args.auth.userId, entry)),
    );

    const participantHash = uniqueParticipantHash(participantIds);
    const existing = await ctx.db
      .query('conversations')
      .withIndex('by_participantHash', (query) =>
        query.eq('participantHash', participantHash),
      )
      .unique();

    if (existing) {
      return existing;
    }

    const now = Date.now();
    const conversationId = await ctx.db.insert('conversations', {
      createdAt: now,
      createdByUserId: args.auth.userId,
      encryptedTitle: args.encryptedTitle,
      participantHash,
      protocolType: args.protocolType,
      updatedAt: now,
    });

    const members = await Promise.all(
      participantIds.map(async (userId) => ({
        conversationId,
        deviceId: await getPrimaryDeviceId(ctx, userId),
        joinedAt: now,
        userId,
      })),
    );

    await Promise.all(
      members.map((member) => ctx.db.insert('conversationMembers', member)),
    );

    return ctx.db.get(conversationId);
  },
});

export const listConversations = query({
  args: {
    auth: sessionAuth,
  },
  handler: async (ctx, args) => {
    await requireSession(ctx, args.auth);

    const memberships = await ctx.db
      .query('conversationMembers')
      .withIndex('by_user', (query) => query.eq('userId', args.auth.userId))
      .collect();

    const conversations = await Promise.all(
      memberships.map(async (membership) => {
        const conversation = await ctx.db.get(membership.conversationId);

        if (!conversation) {
          return null;
        }

        const members = await ctx.db
          .query('conversationMembers')
          .withIndex('by_conversation', (query) =>
            query.eq('conversationId', conversation._id),
          )
          .collect();

        const latestMessage = conversation.lastMessageId
          ? await ctx.db.get(conversation.lastMessageId)
          : null;

        const receipts = await ctx.db
          .query('messageReceipts')
          .withIndex('by_conversation', (query) =>
            query.eq('conversationId', conversation._id),
          )
          .collect();

        const messageList = await ctx.db
          .query('messages')
          .withIndex('by_conversation_createdAt', (query) =>
            query.eq('conversationId', conversation._id),
          )
          .collect();

        const unreadCount = messageList.filter((message) => {
          if (message.senderUserId === args.auth.userId) {
            return false;
          }

          return !receipts.some(
            (receipt) =>
              receipt.messageId === message._id &&
              receipt.userId === args.auth.userId &&
              receipt.receiptType === 'read',
          );
        }).length;

        return {
          ...conversation,
          counterpartUserIds: members
            .map((member) => member.userId)
            .filter((userId) => userId !== args.auth.userId),
          latestMessage,
          unreadCount,
        };
      }),
    );

    return conversations
      .filter(Boolean)
      .sort((left, right) => right!.updatedAt - left!.updatedAt);
  },
});

export const sendEncryptedMessage = mutation({
  args: {
    auth: mutationAuth,
    conversationId: v.id('conversations'),
    clientMessageId: v.string(),
    ciphertext: v.string(),
    encryptionMetadata,
    protocolVersion: v.string(),
    attachmentIds: v.optional(v.array(v.id('encryptedAttachments'))),
  },
  handler: async (ctx, args) => {
    const payload = {
      attachmentIds: args.attachmentIds ?? [],
      ciphertext: args.ciphertext,
      clientMessageId: args.clientMessageId,
      conversationId: String(args.conversationId),
      encryptionMetadata: args.encryptionMetadata,
      protocolVersion: args.protocolVersion,
    };

    await requireSignedMutation(ctx, 'sendEncryptedMessage', args.auth, payload);
    await enforceRateLimit(ctx, args.auth, 'sendEncryptedMessage');
    await ensureConversationMember(ctx, args.conversationId, args.auth.userId);
    assertAllowedMessageProtocol({
      isDemo: args.encryptionMetadata.isDemo,
      protocolVersion: args.protocolVersion,
    });
    const attachments = await ensureOutboundAttachments(ctx, {
      attachmentIds: args.attachmentIds,
      conversationId: args.conversationId,
      deviceId: args.auth.deviceId,
      userId: args.auth.userId,
    });

    assertNoPlaintextFields('sendEncryptedMessage', args);
    assertOpaqueTransportValue('ciphertext', args.ciphertext);
    assertOpaqueTransportValue('encryptionMetadata.nonce', args.encryptionMetadata.nonce);
    assertOpaqueTransportValue(
      'encryptionMetadata.senderIdentityKey',
      args.encryptionMetadata.senderIdentityKey,
    );
    assertOpaqueTransportValue(
      'encryptionMetadata.senderSigningKey',
      args.encryptionMetadata.senderSigningKey,
    );

    const existing = await ctx.db
      .query('messages')
      .withIndex('by_clientMessageId', (query) =>
        query.eq('clientMessageId', args.clientMessageId),
      )
      .unique();

    if (existing) {
      return existing;
    }

    const now = Date.now();
    const messageId = await ctx.db.insert('messages', {
      attachmentIds: args.attachmentIds,
      ciphertext: args.ciphertext,
      clientMessageId: args.clientMessageId,
      conversationId: args.conversationId,
      createdAt: now,
      encryptionMetadata: args.encryptionMetadata,
      protocolVersion: args.protocolVersion,
      senderDeviceId: args.auth.deviceId,
      senderUserId: args.auth.userId,
    });

    await ctx.db.patch(args.conversationId, {
      lastMessageId: messageId,
      updatedAt: now,
    });

    await ctx.db.insert('messageReceipts', {
      conversationId: args.conversationId,
      createdAt: now,
      deviceId: args.auth.deviceId,
      messageId,
      receiptType: 'delivered',
      userId: args.auth.userId,
    });

    await Promise.all(
      attachments.map((attachment) =>
        ctx.db.patch(attachment._id, {
          messageId,
        }),
      ),
    );

    return ctx.db.get(messageId);
  },
});

export const listMessages = query({
  args: {
    auth: sessionAuth,
    conversationId: v.id('conversations'),
    limit: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    await requireSession(ctx, args.auth);
    await ensureConversationMember(ctx, args.conversationId, args.auth.userId);

    const messages = await ctx.db
      .query('messages')
      .withIndex('by_conversation_createdAt', (query) =>
        query.eq('conversationId', args.conversationId),
      )
      .collect();

    const receipts = await ctx.db
      .query('messageReceipts')
      .withIndex('by_conversation', (query) =>
        query.eq('conversationId', args.conversationId),
      )
      .collect();

    const take = Math.min(args.limit ?? 100, MAX_MESSAGE_PAGE_SIZE);

    return {
      messages: messages.slice(-take),
      receipts,
    };
  },
});

export const markMessageRead = mutation({
  args: {
    auth: mutationAuth,
    conversationId: v.id('conversations'),
    messageId: v.id('messages'),
  },
  handler: async (ctx, args) => {
    const payload = {
      conversationId: String(args.conversationId),
      messageId: String(args.messageId),
    };

    await requireSignedMutation(ctx, 'markMessageRead', args.auth, payload);
    await ensureConversationMember(ctx, args.conversationId, args.auth.userId);

    const existing = await ctx.db
      .query('messageReceipts')
      .withIndex('by_message_user_type', (query) =>
        query
          .eq('messageId', args.messageId)
          .eq('userId', args.auth.userId)
          .eq('receiptType', 'read'),
      )
      .unique();

    if (!existing) {
      await ctx.db.insert('messageReceipts', {
        conversationId: args.conversationId,
        createdAt: Date.now(),
        deviceId: args.auth.deviceId,
        messageId: args.messageId,
        receiptType: 'read',
        userId: args.auth.userId,
      });
    }

    const membership = await ctx.db
      .query('conversationMembers')
      .withIndex('by_conversation_user', (query) =>
        query.eq('conversationId', args.conversationId).eq('userId', args.auth.userId),
      )
      .unique();

    if (membership) {
      await ctx.db.patch(membership._id, {lastReadAt: Date.now()});
    }

    return {success: true};
  },
});

export const createInviteCode = mutation({
  args: {
    auth: mutationAuth,
  },
  handler: async (ctx, args) => {
    await requireSignedMutation(ctx, 'createInviteCode', args.auth, {
      userId: String(args.auth.userId),
    });
    await enforceRateLimit(ctx, args.auth, 'createInviteCode');

    const inviteCode = `DL-${crypto.randomUUID().replace(/-/g, '').slice(0, 12).toUpperCase()}`;
    const codeHash = hashString(inviteCode);
    const now = Date.now();

    await ctx.db.insert('inviteCodes', {
      codeHash,
      createdAt: now,
      expiresAt: now + 1000 * 60 * 60 * 24 * 7,
      ownerDeviceId: args.auth.deviceId,
      ownerUserId: args.auth.userId,
    });

    return {
      inviteCode,
      inviteUri: `deepline://invite/${inviteCode}`,
    };
  },
});

export const addContactByInviteCode = mutation({
  args: {
    auth: mutationAuth,
    inviteCode: v.string(),
    localAliasCiphertext: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const payload = {
      inviteCode: args.inviteCode,
      localAliasCiphertext: args.localAliasCiphertext ?? null,
    };

    await requireSignedMutation(ctx, 'addContactByInviteCode', args.auth, payload);
    await enforceRateLimit(ctx, args.auth, 'addContactByInviteCode');

    if (args.localAliasCiphertext) {
      assertOpaqueTransportValue(
        'localAliasCiphertext',
        args.localAliasCiphertext,
      );
    }

    const invite = await ctx.db
      .query('inviteCodes')
      .withIndex('by_codeHash', (query) =>
        query.eq('codeHash', hashString(args.inviteCode)),
      )
      .unique();

    if (!invite || invite.expiresAt < Date.now()) {
      throw new Error('Invite code is invalid or expired.');
    }

    if (invite.ownerUserId === args.auth.userId) {
      throw new Error('You cannot add yourself as a contact.');
    }

    const now = Date.now();
    const forward = await ctx.db
      .query('contacts')
      .withIndex('by_owner_contact', (query) =>
        query
          .eq('ownerUserId', args.auth.userId)
          .eq('contactUserId', invite.ownerUserId),
      )
      .unique();

    if (!forward) {
      await ctx.db.insert('contacts', {
        contactUserId: invite.ownerUserId,
        createdAt: now,
        inviteCodeHash: invite.codeHash,
        localAliasCiphertext: args.localAliasCiphertext,
        ownerUserId: args.auth.userId,
        updatedAt: now,
      });
    }

    const reverse = await ctx.db
      .query('contacts')
      .withIndex('by_owner_contact', (query) =>
        query
          .eq('ownerUserId', invite.ownerUserId)
          .eq('contactUserId', args.auth.userId),
      )
      .unique();

    if (!reverse) {
      await ctx.db.insert('contacts', {
        contactUserId: args.auth.userId,
        createdAt: now,
        inviteCodeHash: invite.codeHash,
        ownerUserId: invite.ownerUserId,
        updatedAt: now,
      });
    }

    return {
      contactUserId: invite.ownerUserId,
      inviteCode: args.inviteCode,
    };
  },
});

export const createAttachmentUploadSession = mutation({
  args: {
    auth: mutationAuth,
    conversationId: v.id('conversations'),
    ciphertextByteLength: v.number(),
  },
  handler: async (ctx, args) => {
    const payload = {
      ciphertextByteLength: args.ciphertextByteLength,
      conversationId: String(args.conversationId),
    };

    await requireSignedMutation(
      ctx,
      'createAttachmentUploadSession',
      args.auth,
      payload,
    );
    await enforceRateLimit(ctx, args.auth, 'createAttachmentUploadSession');
    await ensureConversationMember(ctx, args.conversationId, args.auth.userId);

    if (
      !Number.isFinite(args.ciphertextByteLength) ||
      args.ciphertextByteLength <= 0
    ) {
      throw new Error('Attachment ciphertext size must be a positive number.');
    }

    if (args.ciphertextByteLength > MAX_ATTACHMENT_BYTES) {
      throw new Error(`Attachments larger than ${MAX_ATTACHMENT_BYTES} bytes are rejected.`);
    }

    const now = Date.now();
    const uploadSessionId = await ctx.db.insert('attachmentUploadSessions', {
      conversationId: args.conversationId,
      createdAt: now,
      expiresAt: now + ATTACHMENT_UPLOAD_TTL_MS,
      maxCiphertextBytes: args.ciphertextByteLength,
      ownerDeviceId: args.auth.deviceId,
      ownerUserId: args.auth.userId,
      status: 'pending_upload',
      updatedAt: now,
    });

    return {
      expiresAt: now + ATTACHMENT_UPLOAD_TTL_MS,
      maxCiphertextBytes: args.ciphertextByteLength,
      uploadSessionId,
      uploadUrl: await ctx.storage.generateUploadUrl(),
    };
  },
});

export const createAttachmentDownloadUrl = mutation({
  args: {
    auth: mutationAuth,
    attachmentId: v.id('encryptedAttachments'),
  },
  handler: async (ctx, args) => {
    const payload = {
      attachmentId: String(args.attachmentId),
    };

    await requireSignedMutation(ctx, 'createAttachmentDownloadUrl', args.auth, payload);
    await enforceRateLimit(ctx, args.auth, 'createAttachmentDownloadUrl');

    const attachment = await ctx.db.get(args.attachmentId);

    if (!attachment) {
      throw new Error('Attachment metadata was not found.');
    }

    await ensureConversationMember(ctx, attachment.conversationId, args.auth.userId);

    const downloadUrl = await ctx.storage.getUrl(attachment.storageId);

    if (!downloadUrl) {
      throw new Error('Encrypted attachment blob is no longer available.');
    }

    return {
      attachmentId: attachment._id,
      ciphertextByteLength: attachment.ciphertextByteLength,
      ciphertextDigest: attachment.ciphertextDigest,
      downloadUrl,
      protocolVersion: attachment.protocolVersion,
    };
  },
});

export const discardEncryptedAttachment = mutation({
  args: {
    auth: mutationAuth,
    attachmentId: v.id('encryptedAttachments'),
  },
  handler: async (ctx, args) => {
    const payload = {
      attachmentId: String(args.attachmentId),
    };

    await requireSignedMutation(ctx, 'discardEncryptedAttachment', args.auth, payload);

    const attachment = await ctx.db.get(args.attachmentId);

    if (!attachment) {
      return {success: true};
    }

    if (
      attachment.ownerUserId !== args.auth.userId ||
      attachment.senderDeviceId !== args.auth.deviceId
    ) {
      throw new Error('Attachment ownership check failed.');
    }

    if (attachment.messageId) {
      throw new Error('Attachments already attached to a message cannot be discarded.');
    }

    await ctx.storage.delete(attachment.storageId);
    await ctx.db.delete(attachment._id);

    return {success: true};
  },
});

export const uploadEncryptedAttachmentMetadata = mutation({
  args: {
    auth: mutationAuth,
    conversationId: v.id('conversations'),
    uploadSessionId: v.id('attachmentUploadSessions'),
    storageId: v.id('_storage'),
    ciphertextDigest: v.string(),
    ciphertextByteLength: v.number(),
    metadataCiphertext: v.string(),
    protocolVersion: v.string(),
  },
  handler: async (ctx, args) => {
    const payload = {
      ciphertextByteLength: args.ciphertextByteLength,
      ciphertextDigest: args.ciphertextDigest,
      conversationId: String(args.conversationId),
      metadataCiphertext: args.metadataCiphertext,
      protocolVersion: args.protocolVersion,
      uploadSessionId: String(args.uploadSessionId),
      storageId: args.storageId,
    };

    await requireSignedMutation(
      ctx,
      'uploadEncryptedAttachmentMetadata',
      args.auth,
      payload,
    );
    await enforceRateLimit(ctx, args.auth, 'uploadEncryptedAttachmentMetadata');
    await ensureConversationMember(ctx, args.conversationId, args.auth.userId);

    assertNoPlaintextFields('uploadEncryptedAttachmentMetadata', args);
    assertOpaqueTransportValue('ciphertextDigest', args.ciphertextDigest);
    assertOpaqueTransportValue('metadataCiphertext', args.metadataCiphertext);

    if (
      !Number.isFinite(args.ciphertextByteLength) ||
      args.ciphertextByteLength <= 0
    ) {
      throw new Error('Attachment ciphertext size must be a positive number.');
    }

    if (args.ciphertextByteLength > MAX_ATTACHMENT_BYTES) {
      throw new Error(`Attachments larger than ${MAX_ATTACHMENT_BYTES} bytes are rejected.`);
    }

    const uploadSession = await ctx.db.get(args.uploadSessionId);

    if (!uploadSession) {
      throw new Error('Attachment upload session was not found.');
    }

    if (
      uploadSession.ownerUserId !== args.auth.userId ||
      uploadSession.ownerDeviceId !== args.auth.deviceId ||
      uploadSession.conversationId !== args.conversationId
    ) {
      throw new Error('Attachment upload session ownership check failed.');
    }

    if (uploadSession.status !== 'pending_upload') {
      throw new Error('Attachment upload session can no longer be finalized.');
    }

    if (uploadSession.expiresAt < Date.now()) {
      await ctx.db.patch(uploadSession._id, {
        status: 'expired',
        updatedAt: Date.now(),
      });
      throw new Error('Attachment upload session expired before finalization.');
    }

    if (args.ciphertextByteLength > uploadSession.maxCiphertextBytes) {
      throw new Error('Attachment payload exceeded the permitted upload session size.');
    }

    const storageMetadata = await ctx.storage.getMetadata(args.storageId);

    if (!storageMetadata) {
      throw new Error('Encrypted attachment blob was not found in storage.');
    }

    if (storageMetadata.size !== args.ciphertextByteLength) {
      throw new Error('Stored attachment byte length does not match the finalized metadata.');
    }

    if (
      storageMetadata.contentType &&
      storageMetadata.contentType !== ATTACHMENT_CONTENT_TYPE
    ) {
      throw new Error('Encrypted attachments must be stored as generic binary content.');
    }

    const now = Date.now();
    const attachmentId = await ctx.db.insert('encryptedAttachments', {
      ciphertextByteLength: args.ciphertextByteLength,
      ciphertextDigest: args.ciphertextDigest,
      conversationId: args.conversationId,
      createdAt: now,
      metadataCiphertext: args.metadataCiphertext,
      ownerUserId: args.auth.userId,
      protocolVersion: args.protocolVersion,
      senderDeviceId: args.auth.deviceId,
      storageId: args.storageId,
    });

    await ctx.db.patch(uploadSession._id, {
      attachmentId,
      status: 'metadata_finalized',
      storageId: args.storageId,
      updatedAt: now,
    });

    return ctx.db.get(attachmentId);
  },
});
