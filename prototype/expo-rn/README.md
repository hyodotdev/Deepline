# Deepline

Deepline is an Expo + Convex encrypted messenger prototype built around one rule: Convex is an encrypted blob store and realtime sync layer, not a plaintext chat server.

## Architecture

- Expo Router drives the mobile app shell.
- Convex stores users, devices, conversations, ciphertext messages, attachment upload sessions, receipts, attachment metadata, prekey bundles, and future-facing session records.
- Private identity keys stay in `expo-secure-store`.
- Local UI state and aliases stay in AsyncStorage.
- 1:1 messaging is abstracted behind `CryptoProvider`.
- Group messaging is abstracted behind `GroupCryptoProvider`.
- File encryption is abstracted behind `FileCryptoProvider`.
- Encrypted file bytes upload into Convex file storage only after a signed upload session is created on the server.
- Message send, invite creation, contact linking, and attachment transfer paths are rate limited on the server.

## Why Convex only stores encrypted blobs

Deepline does not send plaintext message bodies, private keys, raw contacts, plaintext file names, or plaintext group titles to Convex.

The server stores:

- ciphertext payloads
- public device bundle material
- opaque encrypted metadata
- membership and receipt routing metadata

The server does not store:

- plaintext chat text
- raw phone numbers
- raw address-book contacts
- unencrypted attachment names
- private identity or session keys

## 1:1 E2EE

The app is structured for Signal-style 1:1 messaging, but the production libsignal path is currently blocked in Expo React Native. Deepline therefore ships a clearly labeled demo provider using NaCl box primitives from `tweetnacl`.

This demo flow is **NOT PRODUCTION SAFE** because it does not provide Signal Double Ratchet forward secrecy or post-compromise security.

## Group E2EE

The group layer is hidden behind `GroupCryptoProvider`.

MLS group creation is intentionally blocked until a production-ready audited MLS runtime is available for Expo React Native. The schema is present, but the runtime is not enabled.

## File encryption

- Each file gets a random per-file content key on the client.
- File bytes are encrypted locally before any upload metadata is created.
- The client uploads ciphertext bytes to Convex file storage using a short-lived upload URL from a signed mutation.
- Convex stores only the storage handle, ciphertext digest, ciphertext size, and encrypted attachment metadata.
- The file key and original file name travel only inside the encrypted message payload.

## Current security limitations

- Signal/libsignal is blocked in Expo React Native.
- MLS is blocked pending a suitable audited runtime.
- Multi-device registration is schema-ready but functionally restricted to prevent unsafe device takeover.
- The current 1:1 crypto provider is demo-only.
- App-layer rate limits are present, but network-edge DDoS protection still depends on deployment infrastructure such as WAF/CDN and abuse monitoring.
- Release builds should set `EXPO_PUBLIC_APP_ENV=production` and Convex should set `DEEPLINE_CRYPTO_ENFORCEMENT_MODE=strict` so demo protocols are rejected both in the client shell and on the backend.

See [SECURITY_BLOCKERS.md](./SECURITY_BLOCKERS.md) and [TODO_SECURITY.md](./TODO_SECURITY.md).

## Project structure

- `app/`: Expo Router screens
- `src/crypto/`: crypto interfaces and providers
- `src/storage/`: secure local identity storage
- `src/stores/`: app session state
- `convex/`: schema, auth guards, and encrypted-only functions
- `src/lib/messagePayload.ts`: encrypted message envelope for text and attachments
- `test/`: security-focused validation

## Run the app

1. Install dependencies with `yarn install`.
2. Start or attach Convex with `npx convex dev`.
3. Use the generated `.env.local` or copy `.env.sample` and fill `EXPO_PUBLIC_CONVEX_URL`.
4. Start Expo with `yarn start`.
5. Verify the codebase with `yarn typecheck`, `yarn lint`, and `yarn test`.
