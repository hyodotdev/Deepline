# Auto-Fix Skill

Automatically fix CI failures from GitHub Issues labeled `ai-fix`.

## Trigger

This skill activates when:
- A GitHub Issue is created with label `ai-fix`
- The issue contains CI failure information
- Asked to "fix the CI failure" with an issue reference

## Workflow

### 1. Read the Failure Issue

Parse the issue body for:
- Workflow name (Server CI, Android CI, etc.)
- Failed job/step
- Error messages and stack traces
- Affected files

### 2. Reproduce Locally

```bash
# Run the same command that failed in CI
./gradlew :server:test --info  # or whichever failed
```

### 3. Analyze the Failure

Using failure-patterns.json, identify:
- Error type (compilation, test, runtime)
- Root cause
- Affected source file(s)

### 4. Apply Fix

1. Read the source file
2. Read the test file (if test failure)
3. Generate the fix based on pattern strategy
4. Apply the edit
5. Re-run the failing test locally

### 5. Verify Fix

```bash
# Run full suite to catch regressions
./gradlew check
```

### 6. Create PR

```bash
# Create branch
git checkout -b fix/ci-failure-$(date +%Y%m%d-%H%M%S)

# Commit
git add -A
git commit -m "fix: resolve CI failure in [module]

Fixes #[issue-number]

- [Description of what was wrong]
- [Description of the fix]

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"

# Push and create PR
git push -u origin HEAD
gh pr create --title "fix: resolve CI failure" \
  --body "Fixes #[issue-number]

## Summary
- [What failed]
- [Root cause]
- [Fix applied]

## Test Results
All tests passing locally.

🤖 Generated with [Claude Code](https://claude.com/claude-code)" \
  --label "ai-generated-fix"
```

## Issue Format Expected

The AI Feedback Loop workflow creates issues with this format:

```markdown
## CI Failure Report

**Workflow**: Server CI
**Run**: https://github.com/.../actions/runs/12345
**Branch**: feature/xyz
**Commit**: abc123

### Next Steps for Claude
1. Read the workflow run logs...
2. Identify the failing test(s)...

### Failure Artifacts
[Test XML output if available]
```

## PR Labels

- `ai-generated-fix` - Indicates PR was created by autonomous fix
- `ci-failure` - Links back to the failure type
- `automated` - General automation label

## Limitations

The auto-fix will NOT:
- Make architectural changes
- Modify more than 3 files without confirmation
- Change public API contracts
- Remove tests (only fix or add)

If the fix requires these, create a detailed issue instead of a PR.

## Integration

This skill is triggered by:
- `.github/workflows/ai-feedback-loop.yml` creating issues
- Manual invocation with issue reference

Works with:
- `ci-loop` - For local test execution
- `ai-iterate` - For multi-failure resolution
- `security-scan` - Validates fixes don't break security
