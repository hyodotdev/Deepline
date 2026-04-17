# CI Loop Skill

Run the full CI pipeline locally and iterate until all tests pass.

## Trigger

Use this skill when:
- Starting work on a feature or bugfix
- Before creating a commit or PR
- When asked to "run tests" or "make sure everything passes"
- After making changes to verify they work

## Workflow

### 1. Initial Assessment
```bash
# Check current state
./gradlew assemble --dry-run
```

### 2. Run Full Test Suite
```bash
# Server tests
./gradlew :server:test --info

# Shared module tests (JVM/server)
./gradlew :shared:serverTest --info

# Android tests
./gradlew :clients:android:app:testDebugUnitTest --info
```

### 3. Parse Failures
For each failure in the output:
1. Extract the test class and method name
2. Extract the error message and stack trace
3. Identify the source file and line number

### 4. Fix Loop
```
while failures exist:
    for each failure:
        1. Read the failing test file
        2. Read the source file under test
        3. Analyze the failure using failure-patterns.json
        4. Apply the fix
        5. Re-run the specific test:
           ./gradlew :module:test --tests "FullyQualifiedTestClass.testMethod"
        6. If still failing, try alternative fix
        7. If fixed, move to next failure
```

### 5. Final Verification
```bash
# Run full suite to catch regressions
./gradlew check

# If all pass, report success
# If new failures, add to queue and continue
```

## Quick Commands

```bash
# Run specific test
./gradlew :server:test --tests "dev.hyo.deepline.server.routes.UserRoutesTest.testCreateUser"

# Run tests with stacktrace
./gradlew :server:test --stacktrace

# Continue after failure
./gradlew :server:test --continue
```

## Success Criteria

- All tests pass (exit code 0)
- No compilation errors
- Security validation passes (see security-scan skill)
