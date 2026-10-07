# Development workflow: spec → independent tests → implementation

Two agents with separated roles:

- **Claude** (Claude Code) writes specifications and production code.
- **Codex** (OpenAI Codex CLI, ChatGPT login) writes the unit tests, in a
  **fresh session for every run**, from the specification only.

Claude never writes or edits tests. Codex never writes production code.
This keeps the tests an independent check on the implementation rather than
a mirror of it.

## The loop, per module

1. **Spec** (Claude) — `specs/NN-module.md`: public API, behaviour, error
   cases, determinism requirements. Behaviour only: no implementation
   hints, no suggested test cases. Commit: `docs(spec): ...`.
2. **Tests** (Codex, fresh session) — `scripts/codex-tests.ps1 specs/NN-module.md`.
   Codex reads the spec and `AGENTS.md`, writes tests under `src/test/`, and
   lists ambiguities and assumptions in `specs/NN-module.questions.md`. The
   script commits the result with a `Test-Author: codex` trailer.
3. **Red** — the new tests are compiled against API stubs and must fail.
   Stubs throw `UnsupportedOperationException`; they are committed before
   step 2 so Codex's tests compile.
4. **Ambiguities** — Claude answers Codex's questions by **amending the
   spec**, never the tests. If an answer contradicts a test, Codex is
   rerun on the amended spec.
5. **Implementation** (Claude) — production code until the suite is green.
   Commit: `feat(module): ...`.
6. **Disputes** — if Claude believes a test is wrong, Claude writes the
   argument in `specs/NN-module.disputes.md` and runs
   `scripts/codex-tests.ps1 -Dispute specs/NN-module.md`. A fresh Codex
   session reads the spec, the dispute and the test, and decides: fix the
   test or keep it. Codex's decision stands; Claude adapts the code or
   escalates to the user.

## Guard rails

- `.githooks/commit-msg` rejects a commit that changes `src/test/` without
  a `Test-Author: codex` trailer, and a commit that mixes `src/test/` and
  `src/main/` changes. Enable once per clone:
  `git config core.hooksPath .githooks`.
- Codex transcripts are saved to `.codex-runs/` (not versioned) and the
  session id is recorded in the commit trailer.

## Commit conventions

Conventional Commits, one logical change per commit:
`docs(spec)`, `test(module)`, `feat(module)`, `fix(module)`,
`refactor(module)`, `build`, `chore`.
