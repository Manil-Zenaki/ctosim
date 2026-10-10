# Spec 10 CLI questions and test assumptions

The tests use spec 10 as the CLI contract and the public contracts of the
specifications it references for fixture formats and pipeline expectations.
No production source was opened, searched, or changed.

1. **Question:** What are the exact usage text and success/error sentences?
   **Assumption:** Their wording is not specified. Usage must show the
   `ctosim FORM` invocation; success has two lines containing the specified
   counts, form ID, directory, and report name. Errors identify the problem,
   and status 1 can identify a file or the failing step. The run report is
   tested exactly because its format is specified. No stack-trace prohibition
   or requirement for empty stderr on successful warning-bearing runs is added.
   **Tests:** `CliUsageTest`, `CliFailureTest`,
   `successfulSummaryHasTwoLinesWithCountsFormDirectoryAndReportName`, and
   `defaultsProduceOneHundredSubmissionsAndTheExactReport`.

2. **Question:** Does "a null argument" include an element of the args array,
   and does help override null inputs?
   **Assumption:** It clearly includes each of the three Java parameters.
   Tests require NPE for a null array, out, or err. Null elements and the
   interaction between help and null parameters are left untested.
   **Tests:** The three `null*ThrowsNullPointerException` tests.

3. **Question:** Does a help token consumed as another option's value still
   activate help? Are `--`, `--option=value`, combined short flags, or a FORM
   beginning with `-` supported?
   **Assumption:** These parsing details are not specified, so no expectations
   are imposed. Standalone help is tested before/after other arguments and
   with otherwise invalid arguments, as expressly required.
   **Tests:** The help tests in `CliUsageTest`; the unlisted syntax is excluded.

4. **Question:** If an invalid numeric value is followed by a valid duplicate,
   must the earlier value still be rejected? What integer spellings are valid?
   **Assumption:** Duplicate tests use valid numeric values and require the
   last one, including mixed short/long aliases. A superseded config/media path
   or Python executable need not exist because its value is not selected.
   Fractional/exponent notation, empty values, nonnumeric text, and overflow
   are rejected. Whitespace, leading `+`, and non-ASCII digits are not tested.
   **Tests:** `invalidNumericOptionsAreUsageErrorsBeforeAnyIo`,
   `duplicateOptionsKeepLastValuesAcrossAliases`,
   `explicitPythonOverridesEnvironmentAndRepeatedPythonKeepsLastExecutable`,
   and `everyBoundaryLongSeedIsAcceptedExactly`.

5. **Question:** Must the upper submissions limit be exercised by generating
   one million actual submissions in a unit test?
   **Assumption:** The CLI boundary can be tested without that workload: use
   `-n 1000000` and a missing config, which must reach status 1 rather than
   status 2. Actual successful runs test one, multiple, and the default 100
   submissions; byte comparison against the specified component pipeline
   checks the exact sequence 1 through N and the effective configuration.
   **Tests:** `submissionLimitsAreAcceptedByArgumentValidation`, the XML suffix
   test, defaults test, and
   `exportedBytesEqualWalkingSubmissionsOneThroughNWithEffectiveConfiguration`.

6. **Question:** Is CRLF one line break or two replacements? Must unusual
   Unicode line separators, blank converter stderr lines, and whitespace be
   normalized?
   **Assumption:** Test embedded isolated CR and LF, each becoming a space.
   Do not decide CRLF or unusual separators, nor whether empty stderr lines
   count as warnings. Repeated nonempty converter warning lines are retained
   because the specification says one entry per warning in its order.
   **Tests:** `warningsAreCollectedByCategoryThenSubmissionWithCorrectCountAndNoEmbeddedLineBreaks`
   and `converterWarningsComeBeforeAllLoaderAndWalkerWarnings`.

7. **Question:** How should title/version disagreements between the form and
   catalog, invalid server names, invalid native path syntax, and multiple
   simultaneous runtime errors be classified?
   **Assumption:** Spec 10 does not assign these all an unambiguous status or
   precedence. Fixtures keep form/catalog metadata consistent; invalid server
   names, NUL paths, and mismatched form IDs are not tested. Tests impose only
   the explicit usage-before-I/O and config-before-output/form ordering.
   **Tests:** All successful fixture tests and
   `missingConfigIsReportedBeforeFormOrOutputIsProcessed`;
   the unspecified cases are excluded.

8. **Question:** Does determinism across working directories mean that input
   basenames may change too?
   **Assumption:** No: report Source and Configuration explicitly contain
   basenames. Determinism tests keep names and contents equal while moving
   inputs/outputs or changing the actual process working directory and JVM
   time zone. They compare every generated file, including report bytes.
   **Tests:** Both `*WorkingDirectoriesAndTimeZones`/working-directory
   determinism tests in `CliRunTest` and `CliConverterTest`, plus the repeated
   run overwrite test.

9. **Question:** Is a previous successful report deleted when a rerun fails?
   **Assumption:** No deletion is specified, and only completed runs write
   reports. On a fresh failure no report is produced; on a failed rerun a
   pre-existing report is unchanged. Partial other output is allowed and its
   presence/absence is not constrained after failure.
   **Tests:** `failedRerunDoesNotRewriteAnEarlierSuccessfulReport` and the fresh
   failure tests in `CliFailureTest`/`CliConverterTest`.

10. **Question:** Must CLI unit tests perform real XLSForm conversion?
    **Assumption:** Conversion semantics belong to spec 02. The resource
    `ctosim/cli/converter/ctosim_xlsform` is a test-only process double, exposed
    only to child processes using PYTHONPATH. Its `.xlsx` input contains a JSON
    scenario, not a real workbook. It verifies the exact `-m ctosim_xlsform`
    invocation, argument boundaries, output paths, stderr warnings, exit
    status, interpreter selection, and integration with valid XML/catalog
    fixtures. No production converter is imported. Tests use the already
    installed repository `.venv` Python; they install nothing.
    **Tests:** `CliConverterTest`.

11. **Question:** Can component APIs be used as an integration oracle, and how
    is the requirement to use one FormWalker checked without implementation
    inspection or mock injection?
    **Assumption:** The published component contracts are suitable for checking
    CLI orchestration. Tests compare the result with one publicly constructed
    walker, submissions 1..N, and the exporter. They also assert independent
    report, row-count, metadata, attachment, and warning-category expectations.
    Construction count itself is not observable through Cli's public API
    because spec 07 makes walks independent of prior calls; no reflection into
    implementation internals is used to count constructors.
    **Tests:** The direct pipeline comparison in `CliRunTest` and warning
    collection tests in `CliWarningsTest`/`CliConverterTest`.

12. **Question:** How can a walk failure and filesystem write failures be
    triggered reliably without altering production or depending on permissions?
    **Assumption:** A counted repeat of 10001 instances with ten read-only
    questions per instance exceeds the 100000-step limit from spec 07 while
    requiring fewer repeat instances. It runs in a child JVM, as do main/working
    directory/environment tests. Child processes have a 45-second harness
    timeout and are forcibly stopped if stuck; this is a test safeguard, not
    a proposed product performance requirement. Nonempty directories at output
    file paths and regular files at directory paths provide deterministic I/O
    failures on Windows and other platforms. ACL-dependent unreadability is
    not assumed. All temporary inputs, outputs, and logs are under `.codex-runs`.
    **Tests:** `walkNavigationLimitFailureReturnsOneWithoutAReport`,
    `unwritableExportOrReportIsAnOutputError`,
    `outputDirectoryOrItsParentBeingAFileIsAnOutputError`, and child-process tests.

## Validation and environment blocker

- Requested command: `mvnw.cmd -o -q test-compile`. It reaches test compilation
  but fails with `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closes its
  ZIP filesystem (`WindowsPath.toRealPath`). An `-e` retry confirms the cause.
  The final diagnostic retry has the same failure; its log is
  `.codex-runs/cli-test-compile.log`.
  This is a build-environment failure, not an expected failing test. No Maven
  repository was copied, drive mapped, dependency installed, or build file or
  permissions changed.
- A diagnostic compiler adapted from the repository's existing probe reads
  cached binary classes directly in memory. With `--release 17`, the six new
  CLI Java sources and the existing exporter CSV helper compile successfully
  against the actual public APIs and JUnit 5.11.4. This is supplementary
  compilation evidence, not a successful Maven compile. Probe/output/logs are
  under `.codex-runs/CompileCli.java`, `.codex-runs/cli-classes`, and
  `.codex-runs/cli-memory-compile.log`.
- All four XML fixtures parse and initialize through the existing public
  one-argument FormLoader API. See `.codex-runs/cli-fixture-check.log`.
- The converter process double independently passes success, argument-boundary,
  warning-order, and nonzero-exit checks using the existing Python environment
  (`.codex-runs/check_cli_converter.py`).
- The final diagnostic JUnit run discovers and executes **138 cases**, with
  zero skipped/aborted cases and 138 expected failures at the unimplemented
  Cli APIs. There are no container/discovery failures. See
  `.codex-runs/cli-junit.log`. Downstream CLI behavior cannot yet be validated.

## Dispute adjudication validation (2026-10-09)

- `mvnw.cmd -o -q test` still stops during test compilation. An `-e` retry
  confirms `java.nio.file.AccessDeniedException` for the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`; see
  `.codex-runs/cli-dispute-maven.log`. Maven cannot execute the tests in
  this environment.
- Reused the existing in-memory diagnostic compiler with `--release 17`:
  all seven CLI test/support sources compile successfully against cached
  binary dependencies. See `.codex-runs/cli-dispute-compile.log`. This is
  supplementary compilation evidence, not a successful Maven build.
- The existing diagnostic JUnit launcher ran all 138 CLI tests against
  cached binaries and the current fixtures: all passed, with no skipped
  or aborted tests. This includes the revised runaway fixture within the
  unchanged child-process timeout. See `.codex-runs/cli-dispute-junit.log`.
