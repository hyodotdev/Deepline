# Security Scan Skill

Continuous security validation for E2EE compliance. Run this after any change to server, shared, or crypto-related code.

## Trigger

Use this skill when:
- Making changes to server routes
- Modifying shared module crypto interfaces
- Before any PR that touches message handling
- As part of `/audit-code`

## Security Checks

### 1. Server Never Sees Plaintext

The server must only handle opaque ciphertext. No decryption operations.

```bash
# Should return NO results
grep -rn "fun decrypt\|\.decrypt(" server/src/main --include="*.kt"
grep -rn "plaintext\s*=" server/src/main --include="*.kt"
```

✅ Pass: No matches
❌ Fail: Any decrypt operation in server code

### 2. Crypto Interfaces Only in Shared

The `shared/` module defines interfaces, never implementations.

```bash
# Check for interface definitions (expected)
grep -rn "interface.*CryptoBridge" shared/src/commonMain --include="*.kt"

# Should return NO results - no implementations
grep -rn "class.*:.*CryptoBridge\|object.*:.*CryptoBridge" shared/src --include="*.kt"
```

✅ Pass: Only interfaces exist
❌ Fail: Any CryptoBridge implementation in shared/

### 3. SecureRandom for Cryptographic Operations

All randomness in crypto contexts must use SecureRandom.

```bash
# Find all Random usage
grep -rn "Random\(\)" server/src/main --include="*.kt"

# Verify it's SecureRandom
grep -rn "SecureRandom" server/src/main --include="*.kt"
```

✅ Pass: All random is SecureRandom
⚠️ Warning: kotlin.random.Random found (verify not used for crypto)
❌ Fail: java.util.Random used for token/key generation

### 4. No Hardcoded Secrets

```bash
# Check for hardcoded credentials
grep -rniE "(api_key|apikey|secret|password|token)\s*=\s*['\"][^'\"]{8,}['\"]" \
  server/src shared/src clients/android --include="*.kt"
```

✅ Pass: No hardcoded secrets (test/example values OK)
❌ Fail: Real credentials in source

### 5. Rate Limiting on All Routes

Every public route must have rate limiting.

```bash
# Find all route definitions
grep -rn "route\|get\|post\|put\|delete" server/src/main/kotlin --include="*Routes.kt"

# Find rate limit calls
grep -rn "enforceRateLimit\|rateLimit" server/src/main/kotlin --include="*Routes.kt"
```

✅ Pass: Rate limiting present on routes
⚠️ Warning: Some routes may be missing rate limiting

### 6. Input Validation

Check that user input is validated before use.

```bash
# Find request body parsing
grep -rn "call.receive<" server/src/main --include="*.kt"

# Look for validation
grep -rn "require\|check\|validate" server/src/main --include="*.kt"
```

## Validation Script

Run all checks in sequence:

```bash
#!/bin/bash
ERRORS=0

echo "=== DeepLine Security Scan ==="

echo -n "1. Server plaintext check... "
if grep -rq "fun decrypt\|\.decrypt(" server/src/main --include="*.kt" 2>/dev/null; then
  echo "❌ FAIL"
  ((ERRORS++))
else
  echo "✅ PASS"
fi

echo -n "2. Crypto interfaces check... "
if grep -rq "class.*:.*CryptoBridge" shared/src --include="*.kt" 2>/dev/null; then
  echo "❌ FAIL"
  ((ERRORS++))
else
  echo "✅ PASS"
fi

echo -n "3. SecureRandom check... "
if grep -rq "java\.util\.Random\(\)" server/src/main --include="*.kt" 2>/dev/null; then
  echo "❌ FAIL"
  ((ERRORS++))
else
  echo "✅ PASS"
fi

echo ""
if [ $ERRORS -eq 0 ]; then
  echo "✅ All security checks passed"
else
  echo "❌ $ERRORS security check(s) failed"
  exit 1
fi
```

## E2EE Architecture Reference

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client A  │     │   Server    │     │   Client B  │
│             │     │             │     │             │
│ Plaintext   │     │ Ciphertext  │     │ Plaintext   │
│    ↓        │     │   ONLY      │     │    ↑        │
│ Encrypt     │────▶│   Store     │────▶│ Decrypt     │
│ (on-device) │     │   Route     │     │ (on-device) │
└─────────────┘     └─────────────┘     └─────────────┘
```

The server NEVER has access to encryption keys or plaintext. It only:
- Stores opaque ciphertext blobs
- Routes messages by conversation ID
- Manages prekeys (public keys only)
- Enforces rate limits

## Files to Monitor

Critical security files:
- `server/src/main/kotlin/*/routes/MessageRoutes.kt`
- `server/src/main/kotlin/*/routes/ConversationRoutes.kt`
- `server/src/main/kotlin/*/store/*Store.kt`
- `shared/src/commonMain/kotlin/*/crypto/*Bridge.kt`
