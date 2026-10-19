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

## Dispute 4: the fixture's standard output is not UTF-8 (Amendment 5)

`test_fakepreload_display.do` (case `many`) and
`test_fakepreload_template_failure.do` fail in the fixture's own check,
`FakepreloadFixture.checkLog`, with `java.nio.charset.MalformedInputException`
while reading the Stata log as UTF-8. The log holds the fixture's stdout
line `Invented content for Zebra.csv, alpha.csv, ménages.csv`, which the
fixture prints with `System.out` in the platform encoding (é as the single
byte 0xE9 on Windows), next to the report line that the command shows in
UTF-8. The real simulator writes its output in UTF-8 (spec 10,
Clarifications: "main writes out and err in UTF-8"), so a faithful test
double should print UTF-8 too (for example through a `PrintStream` with
`StandardCharsets.UTF_8`); the command shows the simulator's lines as it
receives them.

## Adjudication (2026-10-17)

### Dispute 1 - prior correction retained

The current `test_options_paths.do` uses distinct numeric suffixes, so its
default output directories do not collide on a case-insensitive filesystem.
This matches the basename rule and preserves the required error 602 for an
existing run without `replace`. No further change is needed.

### Dispute 2 - prior correction retained

The current `test_runtime_selection.do` no longer requires Stata's Java
unconditionally. Amendment 1's ordered selection of an eligible Java replaces
that original rule; configured-Python assertions remain valid. Keep the test.

### Dispute 3 (compiled support jars) - prior correction retained

The support jars are absent from the fixture directory and are built from
source under `.codex-runs/stata-support/` by the runner's build hooks. The
spec requires runnable batch tests, not checked-in compiled fixtures. No
behavioral assertion or further infrastructure change is warranted.

### Dispute 3 (prompt-prefixed assert example) - prior correction retained

Amendment 3 requires an answer-constraint assertion in a command example,
without forbidding Stata's displayed prompt. The current assertion matcher
already accepts the optional prompt while retaining the answer-predicate
check. Keep that correction.

### Dispute 4 - accepted; correct the simulator fixture's output encoding

Spec 11 invokes the spec 10 simulator and displays its messages. Spec 10's
Clarifications explicitly require `main` to write both stdout and stderr in
UTF-8 so accented filenames reach calling programs intact. Amendment 5 does
not replace that contract. The fixture's platform-default `System.out`
encoding therefore supplies invalid simulator output on Windows; the command
is not required to repair that output or guess its encoding.

`FakepreloadFixture.main` now wraps stdout and stderr in explicitly UTF-8,
auto-flushing `PrintStream`s before emitting any messages. The constructor
uses the charset name to preserve the fixture's Java 8 compatibility.
`test_fakepreload_display.do`, `test_fakepreload_template_failure.do`, their
Unicode filenames, and the strict UTF-8 log reader remain unchanged. Their
report-content, ordering, error-code, cwd, and partial-data assertions are
supported by the spec and are retained.

### Validation for this adjudication

A process-level probe compiled both the original and corrected fixture with
`javac --release 8`, then ran their `many` case with Windows-1252 default
file/stdout/stderr encodings. The original emitted single-byte `E9` in
`ménages.csv`; the corrected fixture emitted UTF-8 `C3 A9` for its `é`.
Strict UTF-8 decoding and the exact accented-filename assertion passed for
the corrected output. Probe artifacts are under
`.codex-runs/stata-dispute4-encoding/`.

The prescribed `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`
rebuilt all test support successfully, including the fixture's display-oracle
self-checks. Both disputed do-files passed in Stata, as did all eleven
`test_fakepreload_*.do` files and the five help-use-case do-files. The separate
`help_use_cases_oracle_tests.py` run passed all 16 tests.

Full-suite result: **58 do-files, 46 passed, 12 failed** (runner exit status 1).
The earlier disputed `test_options_paths.do` and `test_runtime_selection.do`
also passed. The seven `test_amendment_*.do` and five
`test_converter_name_*.do` failures occur in launcher cleanup: removing
pre-existing `temp/hsperfdata_manil` directories is denied before the child
command assertions run. The launcher logs under `.codex-runs/stata-amendment/`
and `.codex-runs/stata-converter-name/` confirm this cause. These are the same
environmental failures recorded in the previous adjudication; no unrelated
cleanup logic, permissions, or assertions were changed. Per-do-file runner
logs are under `target/stata-tests/`.

`git diff --check` passed. Only this disputes file and the test fixture source
were changed. No production implementation was read or modified, and no commit
was made.

## Dispute 5: Amendment 6 tests that read r() after closing a log

Three Amendment 6 tests run `ctosim` inside a named log and read the
returned results with `a6_results` only after `quietly log close`:

- `test_form_problems_final_plain.do`, lines 14-17;
- `test_form_problems_template.do`, lines 15-22;
- `test_form_problems_absent.do`, lines 14-17, 23-26 and 29-32.

Each fails with `assertion is false` (r(9)) in `a6_results`, right after
a run whose output is otherwise as expected. `log close` is itself an
r-class command and clears `r()`. A three-line check in StataSE 19 (batch
mode) shows it: a program returning `r(x) = 5` gives `5` when `r(x)` is read
with no log and with a named log open, and `.` once `quietly log close` has
run. No implementation of `ctosim` can make its results survive a later
command that replaces them; the spec makes no such promise.

`test_form_problems_results.do` and `test_form_problems_display.do`, which
read `r()` before closing their logs (or open none), pass.

Requested: in these three tests, read the results (`a6_results`, or a copy
of `r()` into locals) before `quietly log close`, keeping the other
assertions, and the log checks, unchanged.

## Adjudication (2026-10-18)

### Dispute 1 - prior correction retained

The spec derives default output paths from form basenames and requires error
602 for an existing report without `replace`. The current options-paths test
uses distinct numeric suffixes, avoiding case-only collisions. Keep it.

### Dispute 2 - prior correction retained

Amendment 1 replaces unconditional use of Stata's Java with ordered selection
of an eligible Java 17 or newer. The runtime-selection test already omits the
obsolete Java assertion and retains its specified Python checks. Keep it.

### Dispute 3 (compiled support jars) - prior correction retained

This is an infrastructure concern, not a disputed command expectation: the
spec requires executable batch tests, not committed build output. Both support
jars remain absent from the fixture directory; the runner's build hooks compile
them under `.codex-runs/stata-support/`. No further change is needed.

### Dispute 3 (prompt-prefixed assert example) - prior correction retained

Amendment 3 requires an answer-constraint assertion in a command example, but
does not forbid Stata's displayed prompt. The existing matcher accepts an
optional prompt while retaining its answer-predicate check. Keep it.

### Dispute 4 - prior correction retained

The simulator fixture already sets stdout and stderr to UTF-8 explicitly,
matching the referenced simulator contract and preserving the Unicode and
strict-decoding assertions. No further change is needed.

### Dispute 5 - accepted; check returned results before closing logs

Step 5 and Amendment 6 promise report-derived results when `ctosim` returns
successfully. They do not promise those results after a subsequent command
replaces `r()`. Closing a log before reading the results therefore makes the
test measure the wrong command's returned state. The confirmed F1 assumption
about results surviving the import template applies inside `ctosim`, before
it returns; it does not extend the lifetime of results across caller commands.

Moved `a6_results` immediately after the successful `ctosim` call at all five
disputed sites: one in `test_form_problems_final_plain.do`, one in
`test_form_problems_template.do`, and three in `test_form_problems_absent.do`.
The helper snapshots every checked result before making its own assertions.
Logs are still closed before `a6_log` reads them. All count, path, data,
directory-restoration, styling, ordering, final-message, and absence checks
are retained unchanged. The results and display tests need no correction.

### Validation for this adjudication

A standalone batch-mode Stata probe returned `r(x) = 5`, captured it while
the named log was open, closed the log, and confirmed that `r(x)` was then
missing. Both assertions passed. Its do-file and log are under
`.codex-runs/stata-dispute5/`.

Ran `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`.
All test-support fixtures compiled, and all 40 Amendment 6 output-oracle
self-checks passed. All three corrected do-files passed, including their
unchanged log checks; all eight `test_form_problems_*.do` files passed.
The previously disputed path-options, runtime-selection, advanced help,
fake-preload display, and fake-preload template-failure tests also passed.

Full-suite result: **66 do-files, 54 passed, 12 failed** (runner exit status 1).
The seven `test_amendment_*.do` and five `test_converter_name_*.do` failures
remain launcher cleanup failures: access is denied while removing pre-existing
`temp/hsperfdata_manil` directories, before child assertions run. The launcher
logs under `.codex-runs/stata-amendment/` and
`.codex-runs/stata-converter-name/` confirm the same environmental cause as
the prior adjudications. No cleanup logic, directory permissions, or unrelated
expectations were changed. Runner logs are under `target/stata-tests/`.

`git diff --check` passed. Only the three disputed do-files and this disputes
file were changed. No production implementation was read or modified, and
no commit was made.

## Dispute 6: the name Stata gives the GPS columns (Amendment 7)

`test_id_real_schedule.do` (lines 17-18 and the loop below them) reads
the geopoint `gps` of `fixtures/id_plan.xml` as the variables
`gps_latitude` and `gps_longitude`, and fails with `variable
gps_latitude not found` (r(111)) after `ctosim` has run and loaded the
data; every assertion before it passes, including `a7_plan_times`.

Spec 08 writes a geopoint's columns as SurveyCTO does: `gps-Latitude`,
`gps-Longitude`, `gps-Altitude`, `gps-Accuracy` (the run's wide CSV has
them). Without `dofile()`, the command loads that CSV with `import
delimited, varnames(1)` (the step "Load the data" of spec 11), and Stata
turns a column name that is not a valid Stata name into one by dropping
the characters it cannot use: `gps-Latitude` becomes `gpslatitude`,
`gps-Longitude` `gpslongitude`. Nothing in specs 08, 11 or 13 renames
them `gps_latitude`.

Requested: read `gpslatitude` and `gpslongitude` (or rename them in the
test before use), keeping the distance checks unchanged.
