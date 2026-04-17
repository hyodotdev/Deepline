# Collaborating with Claude on Deepline

This guide is for humans driving Claude Code on this repo. [CLAUDE.md](../CLAUDE.md) is the machine-readable counterpart Claude loads automatically.

## What Claude already knows

- The module layout and which files own which concerns.
- The crypto boundary (server = opaque ciphertext only, no homegrown crypto in `shared/`).
- Runtime config surface (env vars, store modes, rate-limiter modes).
- The two project-specific skills below.

You do not need to re-explain any of this. Just give the task.

## Project-specific skills

### `/audit-code`

Runs a structured read-only audit across server, shared, Android, and iOS. Produces a prioritised report (Critical / High / Medium / Low). Use before shipping a feature or when onboarding onto an unfamiliar part of the repo.

Good inputs:
- "Audit the attachment upload flow end-to-end."
- "Audit the invite + contact bootstrap path for auth gaps."

Bad inputs:
- "Rewrite the server for production." (too open — audit first, then scope a follow-up.)
- "Fix everything." (the skill does not mutate files; it reports.)

### `/deepline-knowledge`

Emits a fresh architecture + contract snapshot by re-reading the source-of-truth files. Use when you want a current picture without skimming a dozen files yourself, or when handing the repo to a new collaborator.

## Tasks that play to Claude's strengths here

1. Filling in route handlers that match an existing DTO in `shared/`.
2. Porting a behaviour implemented on one client to the other (Android ↔ iOS), keeping DTO usage consistent.
3. Writing server-side contract tests against [server/src/test/](../server/src/test/).
4. Small, well-scoped refactors inside a single module.

## Tasks that need a human in the loop

1. **Anything that touches the crypto boundary.** Integrating the Signal runtime on Android/iOS, MLS work, sealed-sender transport. These require threat-model judgement, not just code.
2. **Production infrastructure.** Multi-instance websocket fanout, object storage hardening, push metadata minimisation. Claude can scaffold, but the infra choices need a human owner.
3. **External audit prep.** Claude can help draft the scope, not sign off on it.

## Workflow tips

- Start broad with `/deepline-knowledge` if you have not touched the repo in a while.
- Run `/audit-code` before opening a PR on anything security-sensitive.
- Prefer asking Claude to produce a plan first on multi-file changes; review the plan before you let it edit.
- Keep the Expo prototype under [prototype/expo-rn/](../prototype/expo-rn/) off-limits to new work — it is archived.

## When Claude gets it wrong

Common failure modes on this repo:

- Re-introducing plaintext fields into server DTOs. Always check [ProtocolModels.kt](../shared/src/commonMain/kotlin/dev/hyo/deepline/shared/model/ProtocolModels.kt) — the server should never receive decrypted content.
- Drifting Android and iOS clients apart. If you change the wire format on one side, update the other and the shared DTO in the same change.
- Adding "temporary" crypto to `shared/`. Push back — the only acceptable place for Double Ratchet / MLS is a platform-native bridge module.

If you see any of these in a diff, reject and ask for a boundary-preserving version.
