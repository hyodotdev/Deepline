# AI Iterate Skill

Autonomous iteration loop for fixing failures without human intervention.

## Trigger

Use this skill when:
- CI has failed and needs fixing
- Multiple test failures need resolution
- Asked to "fix all the tests" or "make it work"

## Core Algorithm

```
FAILURE_QUEUE = parse_failures(test_output)
ITERATION = 0
MAX_ITERATIONS = 5

while FAILURE_QUEUE not empty AND ITERATION < MAX_ITERATIONS:
    ITERATION++

    failure = FAILURE_QUEUE.pop()

    # Analyze
    pattern = match_failure_pattern(failure, ".claude/failure-patterns.json")
    source_file = identify_source_file(failure)
    test_file = identify_test_file(failure)

    # Read context
    read(source_file)
    read(test_file)

    # Generate fix
    fix = generate_fix(failure, pattern, context)

    # Apply fix
    apply_edit(source_file, fix)

    # Verify
    result = run_test(test_file, failure.test_method)

    if result.passed:
        log("Fixed: " + failure.description)
    else if result.new_failure:
        FAILURE_QUEUE.push(result.new_failure)
        log("New failure discovered, adding to queue")
    else:
        # Same failure - try alternative approach
        alternative_fix = generate_alternative_fix(failure, pattern)
        apply_edit(source_file, alternative_fix)
        # Re-test...

# Final verification
run_full_suite()
```

## Failure Pattern Matching

Reference `.claude/failure-patterns.json` for common patterns:

| Pattern | Strategy |
|---------|----------|
| `Unresolved reference: X` | Search codebase for X, add import |
| `Type mismatch` | Analyze types, add cast/conversion |
| `expected:<A> but was:<B>` | Compare test vs implementation |
| `NullPointerException` | Add null safety |
| `Suspend function` | Add coroutine context |

## Iteration Limits

- **Per failure**: Max 3 fix attempts before escalating
- **Total iterations**: Max 5 rounds through the queue
- **Time limit**: None (complete the task)

## When to Escalate

If after MAX_ITERATIONS failures remain:
1. Document what was tried
2. Identify the root cause pattern
3. Suggest architectural changes if needed
4. Create a detailed issue for human review

## Example Session

```
[Iteration 1]
Failure: UserRoutesTest.testCreateUser - expected 201 but was 400
Pattern: http-status-mismatch
Action: Read UserRoutes.kt, found missing validation
Fix: Added email format validation
Result: PASS

[Iteration 2]
Failure: MessageRoutesTest.testSendMessage - NullPointerException
Pattern: null-pointer
Action: Read MessageRoutes.kt, conversation lookup returns null
Fix: Added null check with proper error response
Result: PASS

[Final] All tests passing ✓
```

## Integration

This skill works with:
- `ci-loop` - Provides the test execution
- `test-gaps` - Identifies missing coverage
- `security-scan` - Validates crypto boundary
