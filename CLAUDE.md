# Instructions for Claude

Read `docs/WORKFLOW.md` and `docs/ARCHITECTURE.md` first.

## Absolute rules (from the project owner)

1. **Git discipline.** Atomic commits, Conventional Commit messages, one
   logical change per commit. Never commit build output.
2. **Tests before code.** Before writing any function, module or feature,
   its spec must exist and Codex's comprehensive tests for it must be
   committed and failing.
3. **Independent tests.** Claude never creates, edits or deletes files
   under `src/test/`. Tests are written by Codex in a fresh session via
   `scripts/codex-tests.ps1`. Disagreements go through the dispute
   procedure in `docs/WORKFLOW.md`.

## Writing specs

- Describe observable behaviour and the public API; no implementation
  hints and no suggested test cases (do not lead the test author).
- Answer each question in Codex's `.questions.md` on its own merits. Before
  confirming an assumption, check it is achievable with the engine
  (probe JavaRosa if unsure); never confirm a batch wholesale.
- Commit public API stubs (methods throw `UnsupportedOperationException`)
  in the same `docs(spec)` step so tests compile — stubs only, no logic.

## Build

```
JAVA_HOME="/c/Program Files/StataNow19/utilities/java/windows-x64/zulu-jdk21.0.11" ./mvnw -o -q test
```
