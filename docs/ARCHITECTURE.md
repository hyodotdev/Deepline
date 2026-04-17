# Deepline Production Rebuild

## Layers

1. Native clients
   Android and iOS own crypto execution because production Signal/MLS runtimes are platform-native concerns.
2. Shared KMP layer
   Shared business rules, DTOs, storage contracts, sync policies, and feature gating.
3. Ktor backend
   Authentication, encrypted routing, websocket delivery, attachment metadata, prekey directory, and device state.
4. Edge and infrastructure
   WAF/CDN, load balancing, abuse protection, metrics, tracing, and object storage.

## Crypto Boundary

The shared module defines interfaces only:

- `OneToOneCryptoBridge`
- `GroupCryptoBridge`
- `AttachmentCryptoBridge`

The server must only handle opaque ciphertext, public device material, routing metadata, and encrypted attachment metadata.

## Server Data Path

1. Client creates or updates device bundle.
2. Client establishes or resumes encrypted session locally.
3. Client encrypts message and attachment references locally.
4. Ktor receives only opaque payloads and fanouts delivery through websocket sessions and push scheduling.
5. Recipients decrypt on-device.

## Realtime

Ktor websockets are the primary low-latency delivery path.
Push is the wake-up path when devices are offline.

## Current Backend Scope

The current Ktor scaffold exposes contracts for:

- user registration with encrypted profile ciphertext
- device registration and published pre-key bundle lookup
- conversation creation and per-user conversation listing
- encrypted message append, receipt writes, and websocket fanout
- encrypted attachment metadata registration
- invite-code contact bootstrap

The backend now supports:

- `memory` mode for fast local contract testing
- `postgres` mode through JDBC + Flyway schema migrations
- `memory` or `redis` rate limiting for message and upload control
- encrypted blob upload sessions with local filesystem blob persistence

Redis-backed multi-instance fanout is still a separate rollout item.

## Prototype Migration

The old Expo + Convex implementation remains in `prototype/expo-rn/` as a reference, not the release target.
