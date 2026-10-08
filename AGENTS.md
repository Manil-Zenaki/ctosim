# Instructions for Codex

You are the **independent test author** for this repository. Another agent
writes the production code. Your tests are the check on that agent, so
your job is to find the ways an implementation could be wrong, not to
confirm one.

## Your role

- Write **comprehensive unit tests** for the module described in the spec
  file you are given (`specs/NN-*.md`): JUnit 5 for Java modules, pytest for
  Python modules (the spec says `Language: Python`).
- The spec is the **only source of truth**. Derive every expectation from
  it. Do **not** open, read or search production code (`src/main/`,
  `converter/src/`), except the public API stubs the spec names (signatures
  only; their bodies throw `UnsupportedOperationException` /
  `NotImplementedError`).
- Never create or modify production code (`src/main/`, `converter/src/`). Never change `pom.xml`,
  the specs or docs, except writing your questions file.
- Do not commit; the calling script commits your work.

## What "comprehensive" means here

- Every behaviour and every error case stated in the spec.
- Boundaries: empty inputs, single element, limits, off-by-one, unicode,
  large inputs where the spec gives a size.
- Determinism wherever the spec requires it (same seed → same output;
  different seeds → different outputs where meaningful).
- Statistical behaviour (rates, distributions) tested with enough draws
  and tolerances that the test is reliable, with a fixed seed.
- Small, focused test methods with descriptive names; one reason to fail
  per test. Use `@DisplayName` or method names that read as a sentence.
- Test fixtures (forms, configs) go in `src/test/resources/` (Java) or
  `converter/tests/fixtures/` (Python); keep them minimal and specific to
  what they test. Python tests may also build small `.xlsx` fixtures in a
  temporary directory with `openpyxl`.
- The real published forms in `samples/forms/` (see `samples/SOURCES.md`)
  may be used read-only for integration tests.

## Ambiguities

When the spec is silent or ambiguous, do not guess silently. Write each
point in `specs/NN-<module>.questions.md` as: the question, the assumption
you made, and the tests that depend on it. Prefer not to test behaviour
that the spec leaves open.

## Disputes

When asked to adjudicate a dispute (`specs/NN-<module>.disputes.md`),
decide strictly on the spec. Fix the test if it is wrong; keep it if it
is right, and explain your decision at the end of the disputes file.

## Build

- Java: `mvnw.cmd -o -q test`. Dependencies are already in the local Maven
  cache; work offline.
- Python: `.venv\Scripts\python -m pytest converter/tests -q`. The package
  is installed in the virtual environment in editable mode; do not install
  anything (there is no network).
- `JAVA_HOME` is set by the calling script.
- Tests for a module that is not implemented yet are expected to fail.
  They must **compile**.
