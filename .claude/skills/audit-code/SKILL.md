---
name: audit-code
description: Sweep the Deepline monorepo for security smells, insecure patterns, dead code, and reuse opportunities across server (Ktor), shared (KMP), Android Compose, and iOS SwiftUI modules. Use when the user asks to "audit", "review the project", "check for issues", or before shipping a feature.
---

# Deepline audit-code skill

Run a structured audit across the Deepline monorepo and produce a prioritised report. This skill encodes the things that are easy to miss when reviewing ad hoc.

## Scope

Audit in this order. Stop and report early if the repo is in a broken state (build fails, tests red).

1. **Crypto boundary violations** — the server must only see opaque ciphertext and public key material. The shared module must only expose bridge **interfaces**, not Double Ratchet / MLS implementations.
2. **Input validation on server routes** — every `POST` under [server/src/main/kotlin/dev/hyo/deepline/server/routes/](../../../server/src/main/kotlin/dev/hyo/deepline/server/routes/) must validate payload size, length limits, and required fields before touching the store.
3. **Rate limiting coverage** — every write route that accepts user input should go through a `RateLimiter` check. Cross-reference with [server/src/main/kotlin/dev/hyo/deepline/server/rate/](../../../server/src/main/kotlin/dev/hyo/deepline/server/rate/).
4. **SQL / injection / path traversal** — inspect [JdbcDeeplineStore.kt](../../../server/src/main/kotlin/dev/hyo/deepline/server/store/JdbcDeeplineStore.kt) and [LocalFilesystemBlobStore.kt](../../../server/src/main/kotlin/dev/hyo/deepline/server/blob/LocalFilesystemBlobStore.kt) for unparameterised SQL and unchecked path components.
5. **Auth & device identity** — confirm device registration, prekey publication, and invite flows never leak identities the caller has not proven ownership of.
6. **Websocket lifecycle** — confirm `ConversationSocketHub` removes sessions on disconnect and never buffers plaintext.
7. **Android client** — inspect [DeeplineServerClient.kt](../../../clients/android/app/src/main/java/dev/hyo/deepline/android/ui/DeeplineServerClient.kt) for hardcoded secrets, unsafe JSON parsing, missing TLS configuration, and storage of unencrypted state.
8. **iOS client** — same for [DeeplineServerClient.swift](../../../clients/ios/DeeplineIOS/Sources/DeeplineServerClient.swift) and [CryptoGate.swift](../../../clients/ios/DeeplineIOS/Sources/CryptoGate.swift).
9. **Dead code / reuse** — flag duplicated DTOs, dead routes, commented-out code, and cases where Android and iOS clients have diverged when they should share behaviour through `shared/`.
10. **Open blockers** — reconcile findings with [SECURITY_BLOCKERS.md](../../../SECURITY_BLOCKERS.md) and [TODO_SECURITY.md](../../../TODO_SECURITY.md). If an item there is already closed, suggest removing it.

## Method

1. Use `Grep` / `Glob` — do not run `./gradlew` unless you need to verify a specific code path compiles.
2. For each finding, record: file:line, what the issue is, severity (critical/high/medium/low/cleanup), and a concrete suggested fix in one sentence.
3. Do not propose rewriting large modules. Prefer minimal, targeted patches.
4. Do not patch anything automatically. This skill is read-only — report findings and let the user authorise changes.

## Output format

Produce a markdown report with these sections (omit sections with no findings):

- `## Critical` — anything that breaks the crypto boundary or exposes plaintext/PII.
- `## High` — missing validation, missing rate limiting, SQL/path injection risk, auth gaps.
- `## Medium` — client-side correctness, websocket lifecycle, error handling.
- `## Low / cleanup` — dead code, duplicated DTOs, stale TODOs, doc drift.
- `## Blocker reconciliation` — items in [SECURITY_BLOCKERS.md](../../../SECURITY_BLOCKERS.md) / [TODO_SECURITY.md](../../../TODO_SECURITY.md) that now appear closed or that need new sub-items.

Close with a 3-line summary: how many findings per severity, and which single item should be fixed first.

## Non-goals

- Do not run a dependency vulnerability scan — out of scope for this skill.
- Do not rewrite documentation.
- Do not touch the archived Expo prototype under `prototype/expo-rn/`.
