---
name: deepline-knowledge
description: Produce an up-to-date snapshot of Deepline's architecture, server contracts, client flows, crypto boundary, runtime modes, and current blockers. Use when the user asks for a project overview, onboarding context, or "what's the state of Deepline".
---

# Deepline knowledge skill

When invoked, read the current state of the repo and emit a concise knowledge dump. Do not rely on memory — always re-read the authoritative files so the snapshot reflects reality.

## Sources of truth

Read these files fresh each run and extract only what has actually changed from what you'd expect:

1. [README.md](../../../README.md) — high-level layout, server contracts, build commands.
2. [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) — layered architecture, crypto boundary, data path.
3. [SECURITY_BLOCKERS.md](../../../SECURITY_BLOCKERS.md) — what prevents "production secure messenger" claim today.
4. [TODO_SECURITY.md](../../../TODO_SECURITY.md) — the active security backlog.
5. [.env.sample](../../../.env.sample) — runtime configuration surface.
6. [settings.gradle.kts](../../../settings.gradle.kts) — module wiring.
7. [server/src/main/kotlin/dev/hyo/deepline/server/DeeplineServerConfig.kt](../../../server/src/main/kotlin/dev/hyo/deepline/server/DeeplineServerConfig.kt) — all runtime-tunable values the server reads.
8. [server/src/main/kotlin/dev/hyo/deepline/server/routes/](../../../server/src/main/kotlin/dev/hyo/deepline/server/routes/) — actual mounted routes (list file names, do not expand every route).
9. [shared/src/commonMain/kotlin/dev/hyo/deepline/shared/model/ProtocolModels.kt](../../../shared/src/commonMain/kotlin/dev/hyo/deepline/shared/model/ProtocolModels.kt) — DTO shapes shared between clients and server.
10. [clients/android/app/src/main/java/dev/hyo/deepline/android/ui/DeeplineAppModel.kt](../../../clients/android/app/src/main/java/dev/hyo/deepline/android/ui/DeeplineAppModel.kt) and [clients/ios/DeeplineIOS/Sources/DeeplineAppModel.swift](../../../clients/ios/DeeplineIOS/Sources/DeeplineAppModel.swift) — client-side state model.

## Output format

Emit a single markdown document with these sections:

```
## Snapshot taken
- Branch, HEAD short SHA, any uncommitted changes (from `git status --short` if useful)

## Modules
- shared / server / clients.android / clients.ios (one line each — what it owns)

## Server runtime surface
- env vars actually read, their defaults, and valid values

## Mounted routes
- grouped by concern (account, conversation, message, attachment, invite, ws, health)

## Client flow
- Android: one paragraph
- iOS:     one paragraph
- Anything where the two have diverged from the shared DTOs

## Open blockers
- Summary of SECURITY_BLOCKERS.md + TODO_SECURITY.md, grouped by theme, with any items that now look closed flagged separately.

## Suggested next 3 concrete tasks
- Each: one sentence, links to the files it would touch.
```

Keep the full output under ~300 lines. If any file on the sources-of-truth list is missing, say so explicitly — do not guess.

## Non-goals

- Do not propose architecture changes. This skill reports state, it does not editorialise.
- Do not include credentials, secrets, or the contents of any `.env.local`-style file.
- Do not load data from the archived Expo prototype under `prototype/expo-rn/`.
