# Deepline

Deepline is being rebuilt as a production-oriented secure messaging monorepo.

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

## Claude Code Skills

This project includes custom skills for Claude Code:

- `/audit-code` — Security audit across all modules
- `/commit` — Create conventional commits with proper formatting
- `/review-pr` — Review pull requests against project standards
- `/deepline-knowledge` — Project architecture overview

## Development Guidelines

### Commit Convention

Use conventional commits:
```
feat(server): add group member management
fix(android): resolve titlebar display issue
refactor(shared): extract common DTOs
test(server): add conversation route tests
docs: update API reference
```

### Code Style

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Swift**: Follow [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)
- **Crypto boundary**: Server handles opaque ciphertext only. No crypto implementations in `shared/`.

### Pull Request Checklist

- [ ] Tests pass (`./gradlew check`)
- [ ] Android builds (`./gradlew :androidApp:assembleDebug`)
- [ ] iOS builds (`xcodegen generate && xcodebuild build`)
- [ ] No secrets committed
- [ ] CHANGELOG updated (for releases)

## License

See [LICENSE](LICENSE) for details.
