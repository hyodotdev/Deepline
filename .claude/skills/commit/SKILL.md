---
name: commit
description: Create a well-formatted git commit following Deepline project conventions. Use when the user asks to "commit", "save changes", or after completing a feature/fix.
---

# Deepline commit skill

Create a conventional commit for the Deepline monorepo with proper formatting, scope detection, and co-authorship.

## Workflow

1. **Check status** — Run `git status` to see all changed files (never use `-uall` flag).
2. **Review changes** — Run `git diff --staged` and `git diff` to understand what was modified.
3. **Check recent commits** — Run `git log --oneline -10` to match existing commit style.
4. **Detect scope** — Determine the primary module affected:
   - `server` — changes in `server/`
   - `shared` — changes in `shared/`
   - `android` — changes in `clients/android/`
   - `ios` — changes in `clients/ios/`
   - `docs` — changes in `docs/` or `*.md` files
   - `ci` — changes in `.github/`, `docker-compose.yml`
   - `deps` — dependency updates in `*.gradle.kts`, `libs.versions.toml`
   - No scope for cross-cutting changes
5. **Determine type**:
   - `feat` — new feature
   - `fix` — bug fix
   - `refactor` — code restructuring without behavior change
   - `test` — adding or updating tests
   - `docs` — documentation only
   - `chore` — maintenance, deps, configs
   - `security` — security-related changes
6. **Stage files** — Add specific files by name (avoid `git add -A` or `git add .`)
7. **Commit** — Use conventional commit format with HEREDOC

## Commit Message Format

```
<type>(<scope>): <short description>

<optional body explaining why, not what>

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

## Examples

```bash
git commit -m "$(cat <<'EOF'
feat(server): add group member management endpoints

Support batch add/remove members and role updates for
conversations with up to 1000 participants.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

```bash
git commit -m "$(cat <<'EOF'
fix(android): resolve titlebar showing in chat screen

Apply NoActionBar theme to application manifest.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
EOF
)"
```

## Rules

- **NEVER** amend commits unless explicitly requested
- **NEVER** use `--no-verify` unless explicitly requested
- **NEVER** force push to main/master
- **NEVER** commit `.env`, credentials, or secrets
- Keep subject line under 72 characters
- Use imperative mood ("add" not "added")
- If pre-commit hook fails, fix the issue and create a NEW commit
- Verify with `git status` after commit completes

## Security Files

Never commit these without explicit user approval:
- `.env*` files
- `credentials*.json`
- Private keys (`*.pem`, `*.key`)
- `secrets.yml` / `secrets.json`

## Post-Commit

After successful commit:
1. Show the commit hash and summary
2. Run `git status` to confirm clean state
3. Ask if user wants to push to remote
