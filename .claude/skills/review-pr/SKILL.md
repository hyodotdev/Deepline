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

## Review Process

### 1. Fetch PR Information

```bash
gh pr view <number> --json title,body,author,baseRefName,headRefName,files,additions,deletions,commits
gh pr diff <number>
gh pr checks <number>
```

### 2. Review Checklist

#### Security (Critical)
- [ ] No plaintext secrets, API keys, or credentials
- [ ] Server only handles opaque ciphertext (crypto boundary intact)
- [ ] No SQL injection vectors (parameterized queries only)
- [ ] No path traversal in file operations
- [ ] Rate limiting on write endpoints
- [ ] Input validation on all user-provided data

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
- [ ] API changes are versioned appropriately
- [ ] Client changes work with current server version

### 3. Review Comments

Categorize findings:

- **BLOCKER** — Must fix before merge (security, data loss, crash)
- **MAJOR** — Should fix before merge (bugs, missing validation)
- **MINOR** — Nice to fix (style, naming, minor improvements)
- **NIT** — Optional (formatting, personal preference)

### 4. Output Format

```markdown
## PR Review: #<number> - <title>

### Summary
<1-2 sentence summary of what the PR does>

### Changes Overview
- **Files changed**: X
- **Additions**: +Y
- **Deletions**: -Z
- **Modules affected**: server, android, ios, shared

### Findings

#### Blockers (X)
- [ ] `file:line` - Description of issue

#### Major Issues (X)
- [ ] `file:line` - Description of issue

#### Minor Issues (X)
- [ ] `file:line` - Description of issue

### Tests
- [ ] Tests pass locally
- [ ] New tests added for new functionality
- [ ] Test coverage adequate

### Recommendation
**APPROVE** | **REQUEST CHANGES** | **COMMENT**

<Reasoning for recommendation>
```

## Deepline-Specific Checks

### Server (`server/`)
- Routes follow REST conventions
- Store interface extended if new data operations
- Both `InMemoryDeeplineStore` and `JdbcDeeplineStore` updated
- WebSocket changes handle connection lifecycle

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

# View PR comments
gh api repos/hyodotdev/DeepLine/pulls/123/comments

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
