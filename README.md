<div align="center">

# Deepline

### The AI-Native Secure Messenger

[![Built with Claude](https://img.shields.io/badge/Built%20with-Claude%20Code-blueviolet?style=for-the-badge&logo=anthropic)](https://claude.ai/code)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Swift](https://img.shields.io/badge/Swift-5.9-FA7343?style=for-the-badge&logo=swift)](https://swift.org)
[![Ktor](https://img.shields.io/badge/Ktor-3.4.2-087CFA?style=for-the-badge)](https://ktor.io)

**Production-grade E2EE messaging built entirely with AI pair programming.**

[Features](#features) | [Quick Start](#quick-start) | [Architecture](#architecture) | [Contributing](#contributing)

</div>

---

## What Makes Deepline Different?

**Deepline is an AI-Native project** — every line of code, from server routes to native UI, was written collaboratively with [Claude Code](https://claude.ai/code). This isn't just a messenger; it's a proof-of-concept that production-quality software can emerge from human-AI collaboration.

### Why This Matters

- **Full transparency**: Every architectural decision, every bug fix, every refactor — done with AI assistance and documented
- **Real security**: Signal Protocol + MLS for E2EE, not toy crypto
- **Native performance**: Kotlin Multiplatform + SwiftUI, no cross-platform compromises
- **Open development**: Watch how AI-assisted development actually works at scale

---

## Features

- **End-to-End Encryption** — Signal Protocol for 1:1, MLS for groups (platform-native bindings)
- **Real-Time Messaging** — WebSocket delivery with sub-100ms latency
- **Push Notifications** — FCM (Android) + APNs (iOS) for background alerts
- **Encrypted Attachments** — Files encrypted client-side, stored as opaque blobs
- **Group Chats** — Up to 1000 members with role-based permissions
- **Read Receipts** — Per-message delivery and read status
- **Mentions** — @mention support with notification routing
- **Invite System** — Share invite codes to add contacts

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Clients                               │
│  ┌─────────────────┐              ┌─────────────────┐       │
│  │  Android        │              │  iOS            │       │
│  │  Kotlin/Compose │              │  SwiftUI        │       │
│  │  Signal Bindings│              │  Signal Swift   │       │
│  └────────┬────────┘              └────────┬────────┘       │
│           │                                │                 │
│           └──────────┬─────────────────────┘                 │
│                      │ HTTPS + WSS                           │
└──────────────────────┼───────────────────────────────────────┘
                       │
┌──────────────────────┼───────────────────────────────────────┐
│                      ▼                                       │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Ktor Server (Zero-Knowledge)            │    │
│  │  • Opaque ciphertext only — never sees plaintext     │    │
│  │  • WebSocket fanout for real-time delivery           │    │
│  │  • Rate limiting + abuse prevention                  │    │
│  └─────────────────────────────────────────────────────┘    │
│                      │                                       │
│         ┌────────────┼────────────┐                         │
│         ▼            ▼            ▼                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │ Postgres │ │  Redis   │ │  Blobs   │                    │
│  │ Messages │ │  Rate    │ │ Encrypted│                    │
│  │ Metadata │ │  Limits  │ │ Files    │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
│                     Server                                   │
└──────────────────────────────────────────────────────────────┘
```

## Layout

- `shared/`
  Kotlin Multiplatform shared domain models, encrypted payload contracts, and crypto bridge interfaces.
- `server/`
  Ktor backend for device registration, encrypted message fanout, attachment metadata, and websocket delivery.
- `clients/android/`
  Native Android app scaffold built with Kotlin + Compose.
- `clients/ios/`
  Native iOS SwiftUI app scaffold generated with XcodeGen.
- `prototype/expo-rn/`
  Archived Expo/Convex prototype retained for reference and migration work only.
- `docs/`
  Architecture and migration notes for the native rebuild.

## Why This Rebuild Exists

The Expo prototype proved the encrypted-only storage model, but it is not a sufficient base for a production secure messenger because the official Signal and MLS runtimes do not currently fit cleanly into the managed Expo stack.

The new direction is:

- native Android and iOS clients
- Kotlin Multiplatform shared app/domain layer
- Ktor backend with websocket realtime delivery
- platform-native crypto bindings instead of homemade protocol code
- encrypted attachments stored as opaque blobs

## Quick Start

### Prerequisites

- JDK 17+
- Android Studio (for Android development)
- Xcode 15+ (for iOS development)
- Docker & Docker Compose (for Postgres/Redis)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

### Environment Setup

```bash
# Clone and setup
git clone https://github.com/hyodotdev/DeepLine.git
cd DeepLine

# Copy environment template
cp .env.sample .env.local

# Start infrastructure (Postgres + Redis)
docker compose up -d postgres redis

# Load environment
set -a && source .env.local && set +a
```

### Run Server

```bash
# Run Ktor server on port 9091
./gradlew :server:run
```

### Build Clients

```bash
# Android
./gradlew :androidApp:assembleDebug

# iOS
cd clients/ios
xcodegen generate
open DeeplineIOS.xcodeproj
```

## Testing

### Run All Tests

```bash
# Full test suite
./gradlew check

# Or run individually:
./gradlew :server:test          # Server unit tests
./gradlew :shared:allTests      # Shared module tests (JVM + iOS Simulator + Android)
```

### Server Tests

```bash
# Run server tests with detailed output
./gradlew :server:test --info

# Run specific test class
./gradlew :server:test --tests "dev.hyo.deepline.server.DeeplineServerTest"
```

### Shared Module Tests

```bash
# All platforms
./gradlew :shared:allTests

# Specific platforms
./gradlew :shared:serverTest              # JVM/Server tests
./gradlew :shared:iosSimulatorArm64Test   # iOS Simulator
./gradlew :shared:testDebugUnitTest       # Android unit tests
```

### Android Tests

```bash
# Unit tests
./gradlew :androidApp:testDebugUnitTest

# Instrumented tests (requires emulator or device)
./gradlew :androidApp:connectedDebugAndroidTest
```

### iOS Tests

```bash
cd clients/ios

# Generate Xcode project
xcodegen generate

# Run tests on simulator
xcodebuild -project DeeplineIOS.xcodeproj \
  -scheme DeeplineIOS \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  test
```

### Test Coverage

```bash
# Generate Kover coverage report for server
./gradlew :server:koverHtmlReport

# Report available at: server/build/reports/kover/html/index.html
```

## Deployment

### Server Deployment

#### Docker Build

```bash
# Build server Docker image
docker build -t deepline-server:latest -f server/Dockerfile .

# Run with environment variables
docker run -d \
  -p 9091:9091 \
  -e DEEPLINE_STORE_MODE=postgres \
  -e DEEPLINE_DATABASE_URL=jdbc:postgresql://host:5432/deepline \
  -e DEEPLINE_DATABASE_USER=deepline \
  -e DEEPLINE_DATABASE_PASSWORD=secret \
  -e DEEPLINE_RATE_LIMITER_MODE=redis \
  -e DEEPLINE_REDIS_URL=redis://host:6379 \
  -e DEEPLINE_BLOB_STORAGE_ROOT=/data/blobs \
  deepline-server:latest
```

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DEEPLINE_STORE_MODE` | `memory` or `postgres` | `memory` |
| `DEEPLINE_DATABASE_URL` | PostgreSQL JDBC URL | - |
| `DEEPLINE_DATABASE_USER` | Database username | - |
| `DEEPLINE_DATABASE_PASSWORD` | Database password | - |
| `DEEPLINE_RATE_LIMITER_MODE` | `memory` or `redis` | `memory` |
| `DEEPLINE_REDIS_URL` | Redis connection URL | - |
| `DEEPLINE_BLOB_STORAGE_ROOT` | Directory for encrypted blobs | `/tmp/deepline-blobs` |
| `DEEPLINE_STRICT_CRYPTO_ENFORCEMENT` | Block dev-mode crypto in production | `false` |

#### Database Migrations

Flyway migrations run automatically on server startup. Migration files are in:
```
server/src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__attachment_upload_sessions.sql
├── V3__group_member_roles.sql
└── V4__mentions_and_media.sql
```

### Android Release Build

```bash
# Debug APK
./gradlew :androidApp:assembleDebug
# Output: clients/android/app/build/outputs/apk/debug/app-debug.apk

# Release APK (requires signing config)
./gradlew :androidApp:assembleRelease

# Bundle for Play Store
./gradlew :androidApp:bundleRelease
```

#### Signing Configuration

Create `clients/android/app/keystore.properties`:
```properties
storeFile=path/to/keystore.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

### iOS Release Build

```bash
cd clients/ios

# Generate Xcode project
xcodegen generate

# Archive for App Store
xcodebuild -project DeeplineIOS.xcodeproj \
  -scheme DeeplineIOS \
  -configuration Release \
  -archivePath build/DeeplineIOS.xcarchive \
  archive

# Export IPA
xcodebuild -exportArchive \
  -archivePath build/DeeplineIOS.xcarchive \
  -exportPath build/ipa \
  -exportOptionsPlist ExportOptions.plist
```

## Server API Reference

### Account & Device

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/users` | Register new user |
| POST | `/v1/devices` | Register device |
| POST | `/v1/prekeys` | Publish pre-key bundle |
| GET | `/v1/prekeys/{userId}/{deviceId}` | Fetch pre-key bundle |

### Conversations

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/conversations` | Create conversation |
| GET | `/v1/users/{userId}/conversations` | List user's conversations |
| GET | `/v1/conversations/{id}` | Get conversation details |
| PATCH | `/v1/conversations/{id}` | Update settings |
| GET | `/v1/conversations/{id}/members` | List members (paginated) |
| POST | `/v1/conversations/{id}/members` | Add members |
| DELETE | `/v1/conversations/{id}/members` | Remove members |
| PATCH | `/v1/conversations/{id}/members/{userId}` | Update member role |
| POST | `/v1/conversations/{id}/leave` | Leave conversation |

### Messages

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/messages` | Send encrypted message |
| GET | `/v1/conversations/{id}/messages` | List messages |
| POST | `/v1/messages/read-receipts` | Mark as read |
| GET | `/v1/messages/{id}/receipts` | Get read receipts |
| GET | `/v1/messages/{id}/receipts/aggregated` | Aggregated read count |
| GET | `/v1/users/{userId}/mentions` | Messages mentioning user |

### Attachments

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/attachments/upload-sessions` | Create upload session |
| PUT | `/v1/uploads/{sessionId}` | Upload blob |
| POST | `/v1/attachments/metadata` | Store attachment metadata |
| GET | `/v1/blobs/{storageKey}` | Download blob |
| GET | `/v1/conversations/{id}/attachments` | List attachments |

### Invites & Contacts

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/v1/invites` | Create invite code |
| POST | `/v1/contacts/by-invite` | Add contact by invite |

### WebSocket

| Endpoint | Description |
|----------|-------------|
| `/v1/ws/conversations/{id}` | Real-time message stream |

### Health

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/healthz` | Health check |

---

## AI-Native Development

This project demonstrates what's possible when humans and AI build software together. We use [Claude Code](https://claude.ai/code) for:

### Claude Code Skills

Custom skills that make development faster:

| Skill | Description |
|-------|-------------|
| `/audit-code` | Security audit across all modules |
| `/commit` | Conventional commits with auto-formatting |
| `/review-pr` | PR review against project standards |
| `/deepline-knowledge` | Full architecture context dump |

### How We Work

```
Human: "Add WebSocket support for real-time messaging"
    ↓
Claude: Explores codebase → Designs architecture → Implements across server + clients
    ↓
Human: Reviews, tests, ships
```

Every feature follows this loop. The AI handles the boilerplate and cross-module consistency; humans focus on decisions and verification.

---

## Contributing

We welcome contributions! This is an AI-native project, so feel free to:

1. **Fork & Clone**
2. **Use Claude Code** (or your preferred AI assistant) to explore the codebase
3. **Open a PR** with your changes

### Commit Convention

```
feat(server): add group member management
fix(android): resolve titlebar display issue
refactor(shared): extract common DTOs
```

### Code Style

- **Kotlin**: [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Swift**: [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)
- **Crypto boundary**: Server handles opaque ciphertext only

### PR Checklist

- [ ] Tests pass (`./gradlew check`)
- [ ] Android builds (`./gradlew :androidApp:assembleDebug`)
- [ ] iOS builds (`xcodegen generate && xcodebuild build`)
- [ ] No secrets committed

---

## Roadmap

- [ ] Signal Protocol integration (Android + iOS)
- [ ] MLS group encryption
- [ ] Voice messages
- [ ] Disappearing messages
- [ ] Multi-device sync
- [ ] Desktop client (Compose Multiplatform)

---

## Star History

If you find this interesting, give us a star! It helps others discover AI-native development.

---

<div align="center">

**Built with AI. Secured by design. Open for everyone.**

[Report Bug](https://github.com/hyodotdev/DeepLine/issues) | [Request Feature](https://github.com/hyodotdev/DeepLine/issues) | [Discussions](https://github.com/hyodotdev/DeepLine/discussions)

</div>

## License

MIT License — See [LICENSE](LICENSE) for details.
