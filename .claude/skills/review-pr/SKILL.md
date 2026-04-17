---
name: review-pr
description: Review a GitHub pull request for the Deepline project. Analyzes code quality, security, test coverage, and architectural alignment. Use when given a PR number or URL.
---

# Deepline review-pr skill

Perform a structured code review of a GitHub pull request against Deepline project standards.

## Usage

```
/review-pr 123
/review-pr https://github.com/hyodotdev/DeepLine/pull/123
```

## Critical Rules

1. **Fix ALL review comments NOW** — Every inline comment must be addressed with a code fix and committed before replying. No "will address in follow-up" or "will fix later".

2. **Reply to inline comments using the proper API** — NEVER use `gh pr comment` for review items. Always use the comment-specific reply endpoint.

3. **NEVER wrap commit hashes in backticks** — GitHub only auto-links plain text commit hashes. Write `Fixed in abc1234.` not `Fixed in \`abc1234\`.`

4. **Resolve threads only after code is pushed** — Don't resolve threads for suggestions or items awaiting clarification.

## Review Process

### 1. Fetch PR Information

```bash
gh pr view <number> --json title,body,author,baseRefName,headRefName,files,additions,deletions,commits
gh pr diff <number>
gh pr checks <number>
```

### 2. Check for Existing Review Comments

```bash
# Get all inline review comments with their IDs
gh api repos/hyodotdev/DeepLine/pulls/<PR_NUMBER>/comments \
  --jq '.[] | {id: .id, path: .path, line: .line, body: .body[:200]}'
```

### 3. Address Each Comment

For each inline review comment:

1. **Read the comment** — Understand what needs to be fixed
2. **Fix the code** — Make the necessary changes
3. **Test locally** — Run `./gradlew :server:test`
4. **Commit the fix** — Create a focused commit
5. **Push to the branch** — `git push`
6. **Reply to the comment** — Use the reply API (see below)

### 4. Reply to Inline Comments

```bash
# Reply to a specific inline comment
gh api repos/hyodotdev/DeepLine/pulls/<PR_NUMBER>/comments/<COMMENT_ID>/replies \
  -X POST -f body="Fixed in <COMMIT_HASH>. <DESCRIPTION>"
```

**Example replies:**
```
Fixed in abc1234. Added HMAC-SHA256 with server-side secret for OTP hashing.
Fixed in def5678. Added IP rate limiting on /verify endpoint.
Already addressed in 6a8abd2. SecureRandom is now used for OTP generation.
```

### 5. Review Checklist

#### Security (Critical)
- [ ] No plaintext secrets, API keys, or credentials
- [ ] Server only handles opaque ciphertext (crypto boundary intact)
- [ ] No SQL injection vectors (parameterized queries only)
- [ ] No path traversal in file operations
- [ ] Rate limiting on write endpoints
- [ ] Input validation on all user-provided data
- [ ] Cryptographic operations use SecureRandom
- [ ] OTP/tokens hashed with HMAC or bcrypt, not plain SHA-256

#### Architecture
- [ ] Changes align with module boundaries (`shared/`, `server/`, `clients/`)
- [ ] No crypto implementations in `shared/` (interfaces only)
- [ ] DTOs use kotlinx.serialization with explicit field names
- [ ] iOS/Android clients follow established patterns

#### Code Quality
- [ ] Functions have single responsibility
- [ ] No commented-out code
- [ ] No debug logging left in production paths
- [ ] Error handling is appropriate
- [ ] No unnecessary dependencies added

#### Testing
- [ ] New features have corresponding tests
- [ ] Server changes include `DeeplineServerTest` coverage
- [ ] Breaking changes are documented

#### Compatibility
- [ ] Database migrations are backward compatible
- [ ] Migration versions don't conflict with existing ones
- [ ] API changes are versioned appropriately
- [ ] Client changes work with current server version

## Deepline-Specific Checks

### Server (`server/`)
- Routes follow REST conventions
- Store interface extended if new data operations
- Both `InMemoryDeeplineStore` and `JdbcDeeplineStore` updated
- WebSocket changes handle connection lifecycle
- Rate limiting on all write endpoints

### Shared (`shared/`)
- Models are `@Serializable`
- No platform-specific code in `commonMain`
- Crypto bridge interfaces only (no implementations)

### Android (`clients/android/`)
- Compose follows state hoisting pattern
- `DeeplineAppModel` is single source of truth
- No hardcoded server URLs outside config

### iOS (`clients/ios/`)
- SwiftUI follows `@Observable` pattern
- `DeeplineAppModel` mirrors Android structure
- XcodeGen `project.yml` updated if new files added

## Commands Reference

```bash
# View PR details
gh pr view 123

# View PR diff
gh pr diff 123

# Get inline review comments
gh api repos/hyodotdev/DeepLine/pulls/123/comments \
  --jq '.[] | {id: .id, path: .path, line: .line, body: .body[:100]}'

# Reply to inline comment
gh api repos/hyodotdev/DeepLine/pulls/123/comments/COMMENT_ID/replies \
  -X POST -f body="Fixed in COMMIT_HASH. Description."

# View check status
gh pr checks 123

# Checkout PR locally for testing
gh pr checkout 123

# Run tests on PR
./gradlew :server:test :shared:allTests
./gradlew :androidApp:assembleDebug
cd clients/ios && xcodegen generate && xcodebuild -scheme DeeplineIOS build
```

## Non-Goals

- Do not auto-approve PRs
- Do not merge PRs (leave that to maintainers)
- Do not run full security audit (use `/audit-code` for that)
- Do not use `gh pr comment` for review item responses
