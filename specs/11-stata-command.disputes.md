# Disputes — spec 11 (Stata command)

## Dispute 1: default output directories that differ only by case

`test_options_paths.do` runs the command on `café XML.XML`, then on
`café xMl.xMl`, both without `saving()` or `replace`, and expects each run
to succeed in its own default directory (`ctosim-out/café XML`, then
`ctosim-out/café xMl`). On Windows, file names are not case-sensitive:
both are the same directory, which already holds the first run's
`run-report.txt` when the second run starts, so the spec requires error
602 ("the output directory already contains a run-report.txt and replace
is not given"). The test fails on Windows for that reason only; the
questions file (point 6) already set filesystem case sensitivity aside.

Requested change: give each iteration names that differ by more than
case (for example `café XML 1.XML`, `café xMl 2.xMl`), or pass `replace`.

## Dispute 2: Stata's own Java is no longer always the one used (Amendment 1)

`test_runtime_selection.do` asserts that the simulator always runs with
the Java that Stata uses (`c(java_home)`), the original rule of "Finding
the programs". Amendment 1 replaced that rule: the first Java 17 or newer
among `CTOSIM_JAVA`, Stata's Java, `JAVA_HOME` and the system path. With
Stata 16 (Java 11) the command must not run Stata's Java. The test author
noted this (Amendment 1 questions, A2) and kept the test unchanged.

Requested change: remove the test, or expect Stata's Java only when it is
Java 17 or newer (the Amendment 1 tests already cover the full order).

## Dispute 3: compiled jars committed as test fixtures

The project's rule is to never commit build output. The tests commit two
compiled jars, `src/test/stata/fixtures/stata-test-support.jar` and
`src/test/stata/fixtures/amendment-test-support.jar`, built from sources
that are committed beside them (`StataTestSupport.java`,
`AmendmentTestSupport.java`, `build-test-fixtures.ps1`,
`build-amendment-fixtures.ps1`). The simulator double
(`SimulatorDouble.java`) is already compiled at test time with
`JAVA_HOME`'s compiler.

Requested change: build the two support jars at test time as well (for
example in `launch.ps1` / `amendment-launch.ps1`, or from `launch.do`
before the first `javacall`, into a directory under `.codex-runs/` that is
then put on the ado-path), and remove the committed `.jar` files.

## Adjudication (2026-10-11)

### Dispute 1 — accepted; fix the test

The spec derives the default output directory from the form's filename and
requires error 602 when that directory already contains `run-report.txt`
without `replace`. It does not require case-sensitive filesystem behavior.
The original loop therefore cannot expect both case-only variants to succeed
on Windows. `test_options_paths.do` now gives each form basename a distinct
numeric suffix and checks its corresponding default directory. This retains
coverage of uppercase/mixed-case extensions, Unicode, spaces, absolute input
paths, and default directory naming without colliding with a preceding run.
The existing overwrite assertions in `test_overwrite.do` remain unchanged.

### Dispute 2 — accepted; remove only the obsolete Java assertion

Amendment 1 explicitly replaces the original Java discovery rule. Even an
eligible Stata Java need not win when an eligible `CTOSIM_JAVA` is present;
Stata Java older than 17 must be skipped. The unconditional comparison with
`c(java_home)` is therefore wrong and has been removed from
`test_runtime_selection.do`. Its configured-Python precedence and successful
XLSX-run assertions remain valid and are retained. The existing
`test_amendment_java_*.do` tests retain their conditional Stata-Java oracle
and checks of override, Stata, JAVA_HOME, and PATH precedence. No amendment
expectations were relaxed.

### Dispute 3 — accepted as a test-infrastructure change

Spec 11 requires batch-executable do-file tests, but does not require compiled
test-support jars to be versioned. It explicitly excludes production packaging
from unit tests. Thus this point identifies no incorrect command assertion;
building the support jars from their existing sources preserves all test
expectations and implements the requested repository hygiene change.

Both checked-in support jars have been removed. Their build scripts now put
classes and jars under `.codex-runs/stata-support/`. The prescribed
`scripts/stata-tests.ps1` runner builds both jars before starting any Stata
test; parent launchers and child helpers use that directory on the ado-path.
Building in the runner avoids a circular bootstrap dependency: the launchers
need a support jar for their first `javacall`, and Windows Stata ignores
`shell` in batch mode. The Java fixture sources and behavioral assertions are
unchanged. No production code was read or changed.

### Validation

Ran `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`.
Both support jars compiled from source; all 23 non-amendment tests passed,
including `test_options_paths.do` and `test_runtime_selection.do`. The seven
amendment tests initially failed in harness cleanup because preexisting Java
temporary directories denied access, before their assertions ran.

Preserved that old scratch tree by moving it to a uniquely named sibling
under `.codex-runs/`, then ran the same runner with
`-Filter 'test_amendment_*.do'`. Result: **7 tests, 0 failed**, including all
42 child scenarios across Stata 16 and Stata 19. Combined latest results:
**30 tests passed**. No test expectations were changed to address the scratch
permissions issue.

Separately, Stata 16 parsed all 30 test do-files as program definitions and
successfully loaded and called both rebuilt support jars. The test PowerShell
scripts parsed successfully, and `git diff --check` passed. Runner logs are
under `target/stata-tests/`; the syntax/jar-loading check is
`.codex-runs/stata-dispute-check/check.log`. No commit was made.

## Dispute 3: an `assert` example written with Stata's prompt

`test_help_use_cases_advanced.do` fails with "Advanced: missing assert
of an answer's constraint". The help file has the example

```
{phang2}{cmd:. assert inrange(people_never_respect, 0, 10) | inlist(people_never_respect, 88, 99) if !missing(people_never_respect)}{p_end}
```

which asserts an answer's constraint, as Amendment 3 asks. Every command
of the help file is written after Stata's `. ` prompt, as Stata's own
help files do and as the existing examples of this help file did before
the amendment. The check finds assertions with `^\s*assert`, which does
not allow the prompt, while the same file's check for `ctosim using`
commands allows it (`^\s*\.?\s*ctosim`). Requested: let the assertion
check allow the optional `. ` prompt, as the command check does.

## Adjudication (2026-10-12)

### Dispute 1 — prior correction retained

The default output directory follows the form basename, and an existing
`run-report.txt` requires error 602 without `replace`. The spec does not
require case-sensitive paths. The current test already uses distinct numeric
suffixes for the four extension variants, so no further change is needed.

### Dispute 2 — prior correction retained

Amendment 1 replaces unconditional use of Stata's Java with the first eligible
Java 17-or-newer candidate in its specified order. The current runtime test
already omits the obsolete comparison with `c(java_home)` and retains its
configured-Python assertions. Keep it unchanged.

### Dispute 3 (compiled support jars) — prior infrastructure correction retained

The spec requires runnable batch tests, not versioned compiled fixtures.
The current runner builds both support jars from source under
`.codex-runs/stata-support/`; neither jar is tracked in the fixture directory.
This preserves the behavioral expectations, so no further change is needed.

### Dispute 3 (prompt-prefixed assert example) — accepted; fix the checker

Amendment 3 requires an example checking an answer's constraint with `assert`,
displayed in `{cmd:}` form. It does not prohibit a displayed `. ` prompt.
Rejecting the quoted example solely because of that prefix therefore adds a
formatting requirement absent from the spec. The assertion matcher in
`fixtures/help_use_cases_checks.py`, used by
`test_help_use_cases_advanced.do`, now accepts the same optional prompt as the
existing `ctosim using` matcher. The answer-predicate check remains unchanged.

Added focused oracle checks for assertions without a prompt, with `. `, and
with surrounding whitespace. Negative cases still reject prompted assertions
about `_N`, `r(N)` or `c(N)`, and prompted `summarize` commands. The positive
prompt cases failed before the matcher fix; all 16 oracle tests pass after it.

### Validation for this adjudication

Ran `.venv\Scripts\python -B src/test/stata/fixtures/help_use_cases_oracle_tests.py`:
**16 tests passed**. Ran the prescribed
`powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`:
**40 do-files, 28 passed, 12 failed** (exit status 1). Test-support Java
fixtures compiled successfully. All five help-use-case do-files, including
the disputed advanced example check, passed in Stata. The previously disputed
`test_options_paths.do` and `test_runtime_selection.do` also passed.

The seven `test_amendment_*.do` and five `test_converter_name_*.do` failures
occur in their existing launchers while removing pre-existing
`temp/hsperfdata_manil` directories: access is denied before the child command
assertions run. Their launcher logs under `.codex-runs/stata-amendment/` and
`.codex-runs/stata-converter-name/` confirm this cause. These failures do not
establish command behavior or a syntax/import failure in the changed checker.
The unrelated launchers and directory permissions were left unchanged.
Per-do-file runner logs are under `target/stata-tests/`.

`git diff --check` passed. No production implementation was read or modified,
and no commit was made.
