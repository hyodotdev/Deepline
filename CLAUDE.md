# Deepline — Claude Code Project Context

Deepline is a secure messenger being rebuilt as a native Android + iOS monorepo backed by a Ktor server, with a Kotlin Multiplatform `shared/` module for app/domain contracts. Long-term goal: "national messenger" level usability with production-grade E2EE.

## Modules

- [shared/](shared/) — KMP (`commonMain`, `androidMain`): domain models, crypto bridge interfaces, protocol DTOs. Never contain crypto implementations here; only interfaces.
- [server/](server/) — Ktor backend (`dev.hyo.deepline.server`): account/device/prekey/conversation/message/attachment routes, in-memory + Postgres store, in-memory + Redis rate limiter, local filesystem blob store, websocket fanout.
- [clients/android/app/](clients/android/app/) — Native Compose app (`dev.hyo.deepline.android`). Single `DeeplineAppModel` + `DeeplineServerClient` talk to the Ktor server.
- [clients/ios/DeeplineIOS/](clients/ios/DeeplineIOS/) — SwiftUI app generated via XcodeGen. Matching `DeeplineAppModel` + `DeeplineServerClient` + `CryptoGate`.
- [prototype/expo-rn/](prototype/expo-rn/) — Archived Expo/Convex prototype. Read-only reference, do not extend.

## Server contracts (opaque ciphertext only)

See [README.md](README.md) for the full list. Key routes: `POST /v1/users`, `POST /v1/devices`, `POST /v1/prekeys`, `POST /v1/conversations`, `POST /v1/messages`, `POST /v1/invites`, `POST /v1/contacts/by-invite`, `GET /v1/conversations/{id}/messages`, websocket at `/v1/ws/conversations/{id}`.

## Crypto boundary (non-negotiable)

- Server only ever handles opaque ciphertext + public key material + routing metadata.
- Shared module defines `OneToOneCryptoBridge`, `GroupCryptoBridge`, `AttachmentCryptoBridge` interfaces in [shared/src/commonMain/kotlin/dev/hyo/deepline/shared/crypto/](shared/src/commonMain/kotlin/dev/hyo/deepline/shared/crypto/). **Do not** implement Double Ratchet or MLS in the shared module — those must live in platform-native bridges (Signal runtime on Android, Signal Swift bindings on iOS).
- Current open blockers tracked in [SECURITY_BLOCKERS.md](SECURITY_BLOCKERS.md) and [TODO_SECURITY.md](TODO_SECURITY.md).

## Build & run

```bash
cp .env.sample .env.local
set -a; source .env.local; set +a
./gradlew :server:run                       # Ktor on :9091
./gradlew :server:test :shared:serverTest   # backend tests
./gradlew :androidApp:assembleDebug         # debug APK
cd clients/ios && xcodegen generate         # regenerate Xcode project after project.yml edits
```

Android emulator talks to the host Ktor via `http://10.0.2.2:9091`. iOS simulator talks via `http://127.0.0.1:9091`.

Override the server URL at runtime through the in-app Settings screen, or on Android by launching with:
```bash
adb shell am start -n dev.hyo.deepline.android/.MainActivity \
  --ez deepline.reset_state true \
  --es deepline.server_url http://10.0.2.2:9091
```

## Runtime modes

- `DEEPLINE_STORE_MODE=memory|postgres` — memory is fine for local dev; postgres uses JDBC + Flyway migrations under [server/src/main/resources/db/migration/](server/src/main/resources/db/migration/).
- `DEEPLINE_RATE_LIMITER_MODE=memory|redis`
- `DEEPLINE_BLOB_STORAGE_ROOT` — directory for encrypted blob sink.

## Conventions

- Kotlin: explicit visibility, no unused `@OptIn`, keep route handlers thin and delegate to store/hub.
- Swift: keep `DeeplineAppModel` the single source of truth for UI state; do not introduce a second store.
- Never add homegrown crypto in `shared/`. If a feature needs cryptographic primitives, route through a bridge interface and implement on-platform.
- Do not extend the Expo prototype. New work goes into native clients + shared + server.
- Tests: backend and shared tests must pass before you claim a feature works. UI changes require a build **and** a manual run on an emulator/simulator when the change touches user-visible behaviour.

## Project-specific skills

- `/audit-code` — sweep this repo for security smells, dead code, and reuse opportunities. Defined in [.claude/skills/audit-code/SKILL.md](.claude/skills/audit-code/SKILL.md).
- `/commit` — create well-formatted git commits following project conventions. Defined in [.claude/skills/commit/SKILL.md](.claude/skills/commit/SKILL.md).
- `/review-pr` — review GitHub pull requests against project standards. Defined in [.claude/skills/review-pr/SKILL.md](.claude/skills/review-pr/SKILL.md).
- `/deepline-knowledge` — dump an up-to-date snapshot of the architecture, contracts, and current blockers. Defined in [.claude/skills/deepline-knowledge/SKILL.md](.claude/skills/deepline-knowledge/SKILL.md).

See [docs/CLAUDE_GUIDE.md](docs/CLAUDE_GUIDE.md) for how to collaborate with Claude on this repo.
