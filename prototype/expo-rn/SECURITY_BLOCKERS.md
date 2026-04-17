# SECURITY_BLOCKERS

As of April 15, 2026, Deepline is blocked from claiming production-grade end-to-end security for three core reasons.

## 1. Signal Protocol in Expo React Native

- The official Signal `libsignal` project exposes Java, Swift, and TypeScript APIs, but the maintained TypeScript package is oriented around Node-native bindings rather than a clean Expo React Native runtime.
- Deepline does not ship a homemade Double Ratchet replacement.
- The app therefore uses a clearly labeled demo NaCl box provider for current 1:1 flows.

Impact:

- No production Signal Double Ratchet
- No forward secrecy guarantees comparable to Signal
- No post-compromise security comparable to Signal

## 2. MLS group runtime

- Deepline includes the group schema and `GroupCryptoProvider` abstraction only.
- MLS group creation is intentionally blocked because a production-ready audited MLS runtime suitable for Expo React Native was not integrated.

Impact:

- No production group E2EE
- No group message sending path enabled

## 3. Multi-device security

- The schema is multi-device-ready, but device registration is intentionally restricted to avoid unsafe account takeover.
- Verified device linking and key handoff are still TODO items.

## Current fallback

The current app is **NOT PRODUCTION SAFE**.

It is safe only as a demo architecture showing:

- encrypted-only Convex storage
- signed attachment upload sessions and attachment ownership checks
- app-layer rate limits on chat, invites, and attachments
- production env guards that block release boot when only demo crypto is available
- local private-key handling
- invite-based contacts
- pluggable crypto boundaries

It is not safe to market as a production secure messenger until the blockers above are resolved.
