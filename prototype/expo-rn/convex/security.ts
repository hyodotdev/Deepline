import type {Id} from './_generated/dataModel';
import type {MutationCtx, QueryCtx} from './_generated/server';
import {
  buildMutationChallenge,
  buildSessionChallenge,
  verifyDetachedBase64,
} from '../src/lib/security';

export type SessionAuth = {
  userId: Id<'users'>;
  deviceId: string;
  sessionProof: string;
};

export type MutationAuth = SessionAuth & {
  requestSignature: string;
};

type AnyCtx = QueryCtx | MutationCtx;

export async function requireSession(
  ctx: AnyCtx,
  auth: SessionAuth,
): Promise<{
  userId: Id<'users'>;
  deviceId: string;
  signingPublicKey: string;
}> {
  const device = await ctx.db
    .query('devices')
    .withIndex('by_user_device', (query) =>
      query.eq('userId', auth.userId).eq('deviceId', auth.deviceId),
    )
    .unique();

  if (!device) {
    throw new Error('Unknown device session.');
  }

  const isValid = verifyDetachedBase64(
    device.signingPublicKey,
    buildSessionChallenge(String(auth.userId), auth.deviceId),
    auth.sessionProof,
  );

  if (!isValid) {
    throw new Error('Invalid session proof.');
  }

  return {
    userId: device.userId,
    deviceId: device.deviceId,
    signingPublicKey: device.signingPublicKey,
  };
}

export async function requireSignedMutation(
  ctx: MutationCtx,
  operation: string,
  auth: MutationAuth,
  payload: unknown,
): Promise<void> {
  const session = await requireSession(ctx, auth);
  const signedPayload = {
    deviceId: auth.deviceId,
    payload,
    userId: auth.userId,
  };

  const isValid = verifyDetachedBase64(
    session.signingPublicKey,
    buildMutationChallenge(operation, signedPayload),
    auth.requestSignature,
  );

  if (!isValid) {
    throw new Error('Invalid request signature.');
  }

  const device = await ctx.db
    .query('devices')
    .withIndex('by_user_device', (query) =>
      query.eq('userId', auth.userId).eq('deviceId', auth.deviceId),
    )
    .unique();

  if (device) {
    await ctx.db.patch(device._id, {lastSeenAt: Date.now()});
  }
}
