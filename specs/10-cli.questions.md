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

## Amendment 1 questions and test assumptions (2026-10-17)

The additional tests are in `CliAmendment1ReportTest`, with five new
XML/catalog fixture pairs under `src/test/resources/ctosim/cli/amendment1/`.
Existing tests and fixtures are unchanged. Only the published specifications,
existing test support, and public API calls were used; production sources
were not opened, searched, or modified.

13. **Question:** What exact sentence and singular/plural wording should the
    third stdout line use?
    **Assumption:** As in question 1, there is no exact sentence contract.
    Tests require the question count as a number, ordered names separated by
    `, `, `run-report.txt`, and wording about unsatisfied constraints. The
    semantic check accepts `not ... satisf...`, `unsatisf...`, `unable ...
    satisf...`, or `couldn't ... satisf...`, without fixing punctuation or
    the rest of the sentence. Other equivalent phrasing can require adjusting
    this matcher, without changing the report's exact format assertions.
    **Tests:** `thirdSummaryLineNamesQuestionCountOrderedNamesAndReportAfterTheOriginalTwoLines`,
    `thirdSummaryCountsQuestionsRatherThanWarningsAndRetainsEqualNamesAtDistinctPaths`,
    `oneQuestionSummaryIsPresentEvenWhenOnlyOneSubmissionFailed`,
    `unicodeQuestionNamesArePreservedInTheSectionAndThirdSummaryLine`, and
    `aQuestionFirstFailingInALaterSubmissionFollowsQuestionsAlreadyRecorded`.

14. **Question:** Does a repeated question count once for every repeat
    instance, and should distinct questions with equal names be merged or
    have their names deduplicated on stdout?
    **Assumption:** The explicit grouping key is the unindexed path, not
    the leaf name. Four failures across two nested repeat levels are one
    question and one membership per submission. Equal names at three
    distinct unindexed paths remain three questions, and stdout lists all
    three names in section order. Catalog lookup also uses the complete
    unindexed path; a same-name entry at another path cannot supply its row.
    **Tests:** `allNestedRepeatIndicesAreRemovedAndEachSubmissionIsCountedOnce`
    (including 21 submissions, so duplicates cannot consume the 20-item
    limit), the equal-name summary test, and
    `catalogEntryWithSameNameAtAnotherPathDoesNotSupplyTheRow`.

15. **Question:** Does "each line break" settle the earlier question about
    CRLF and unusual Unicode line separators, and can it be tested in names?
    **Assumption:** Question 6 still applies. The fixture uses isolated CR
    and LF, encoded as XML character references so XML attribute whitespace
    normalization cannot erase them. Both must become spaces in constraint
    text and reference values. Valid XML field names cannot contain those
    line breaks, so no artificial invalid-name records are injected. Unicode
    names and values are covered separately. Expressions use unpadded,
    parenthesized references to avoid guessing about converter whitespace.
    **Tests:** `isolatedCrAndLfInConstraintAndReferenceValuesEachBecomeOneSpace`,
    `referencesPreserveExpressionOrderUnicodeAndZeroAndLabelOnlyEmptyValuesBlank`,
    and the Unicode question-name test.

16. **Question:** How should the first-record rule be tested without a CLI
    injection API for synthetic submissions, especially if two records for
    one question had different constraint strings?
    **Assumption:** Exercise the real public pipeline, as confirmed for
    question 11. A question has one static constraint in these XForms; the
    tests assert that exact recorded expression, and do not invent a way
    to change it between records. Distinct repeat-instance values, changing
    values across submissions, and a reference overwritten after a failure
    distinguish the first snapshot from later/final values. An end-of-form
    failure distinguishes record order from body order. The partial-failure
    fixture derives membership from exported gate values, not from CLI
    report text or a hard-coded pseudorandom sequence; seed 1827 is fixed.
    Its coverage guards require both outcomes, not particular draw numbers.
    **Tests:** The nested-repeat, first-submission-reference, final-export
    snapshot, end-of-form ordering, partial-failure, and later-submission
    ordering tests in `CliAmendment1ReportTest`.

17. **Question:** Does the new section change exit status, existing warnings,
    the first two stdout lines, or behavior when there are no records?
    **Assumption:** The amendment expressly leaves everything else unchanged.
    Successful runs still return 0; warnings retain every repeat instance
    and required-field warning, and the first two lines retain the original
    counts. Satisfied, irrelevant, deliberately blank, and unconvertible
    answers produce no new section. A converter warning merely containing
    constraint-failure wording does not create a structured record. The
    converter test double from confirmed question 10 is reused unchanged.
    **Tests:** `groupingDoesNotDeduplicateTheOriginalWarningCases`,
    `requiredWarningsDoNotCreateExtraQuestionsOrSubmissionMemberships`, the
    no-section parameterized test, both converter tests, the summary test,
    and `repeatedCallsDoNotLeakConstraintGroupsAndOverwriteTheOldSection`.

## Amendment 1 validation

- `mvnw.cmd -o -q test-compile` reaches test compilation but fails on the
  cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
  The `-e` retry confirms `java.nio.file.AccessDeniedException`, the same
  environment issue documented above. Logs:
  `.codex-runs/cli-amendment1-test-compile.log` and
  `.codex-runs/cli-amendment1-test-compile-detail.log`. No repository was
  copied, drive mapped, dependency installed, or build configuration changed.
- Supplementary compilation with the existing in-memory compiler succeeds
  for all eight CLI/support Java sources, including the new class, against
  cached binaries and JUnit 5 with `--release 17`. This does not constitute
  a successful Maven build. Log:
  `.codex-runs/cli-amendment1-memory-compile.log`.
- The public FormLoader/FormWalker fixture probe loads and walks all five
  fixture pairs, confirming the intended warning counts/order, nested repeat
  failures, and end-of-form failure. Structured record inspection itself
  is unavailable because `Submission.unsatisfiedConstraints()` currently
  throws `UnsupportedOperationException`. The warning-only fixture probe log
  is `.codex-runs/cli-amendment1-fixtures.log`.
- The final diagnostic JUnit launch executes all **34 new cases** with no
  skipped/aborted tests or discovery failures: **7 pass, 27 fail**. Every
  failure is the expected missing section or missing third stdout line;
  there are no remaining fixture-load or setup failures. Log:
  `.codex-runs/cli-amendment1-junit.log`. This launch uses cached binaries
  and the supplementary compiled tests, not a successful Maven build.
- The final requested `mvnw.cmd -o -q test-compile` retry still fails on
  the same cached JAR. `git diff --check` passes, and the only tracked-file
  change is this questions file; the test class and fixture directory are
  new. Existing tests, production sources, specifications and build files
  are unchanged.

## Amendment 2 questions and test assumptions (2026-10-17)

New tests are isolated in `CliAmendment2PipelineTest`,
`CliAmendment2ReportTest`, and `CliAmendment2LifecycleTest`, with new
`CliAmendment2Support` helpers and fixtures under
`src/test/resources/ctosim/cli/amendment2/`. Existing tests and resources
are unchanged. Production sources were not opened, searched, or modified.

18. **Question:** When `files()` is empty, must the report/stdout omit the
    fictitious-attachment line, or print an empty line/list?
    **Assumption:** The amendment does not specify the empty-list presentation
    clearly enough to choose one. No assertion constrains that presentation.
    Step 2 explicitly applies only to a nonempty map: with no missing files,
    the existing exports/warnings remain the same and the attachments path
    must not be written or required to be a directory.
    **Tests:** `noMissingFilesLeavesExportsUnchangedAndDoesNotTouchAnAttachmentsPath`
    and `formWithNoAttachmentReferencesAlsoNeedsNoAttachmentsDirectory`.

19. **Question:** Must copied real CSVs also appear in the report's `Files:`
    list, and where would they appear relative to generated files?
    **Assumption:** The amendment mandates entries for generated files but
    does not specify copied-file entries. Mixed-media tests check the ordered
    projection of all mandatory entries and the final `run-report.txt`,
    allowing copied-file entries in between. The search fixture has no copied
    files and therefore asserts the complete exact list. Only generated files
    belong in `Fictitious attachments:`; that line uses the map's natural order,
    not expression discovery order.
    **Tests:** `generatedPathsFollowEveryLongExportAndPrecedeTheReport`,
    `xlsformConversionPrecedesGenerationAndItsWarningsAndFileOrderArePreserved`,
    `singleUnicodeAttachmentKeepsUtf8LfAndRelativeReportPaths`, and
    `fictitiousNamesUseFilesMapOrderImmediatelyAfterEnumerators`.

20. **Question:** What precise wording and filename ordering does the additional
    stdout line require?
    **Assumption:** There is no prescribed sentence or filename order on stdout.
    The tests require every generated basename including `.csv`, the line's
    position after all other summaries, and disclosure that the content is
    invented. The semantic matcher accepts wording containing `invent`,
    `fictiti`, `fabricat`, or `synthetic`, case-insensitively; other equivalent
    wording may require adjusting this matcher. Report wording/order is exact.
    **Tests:** The stdout assertions in `CliAmendment2ReportTest`, through
    `CliAmendment2Support.inventedSummary`.

21. **Question:** How should suggestions for integer key questions behave?
    Spec 12 includes INTEGER questions in `keyAnswers()`, spec 10 explicitly
    applies suggestions as `texts`, and spec 05 explicitly ignores `texts`
    for integer questions.
    **Assumption:** Follow the precise orchestration instruction: apply
    `texts`, without silently introducing a numeric-range, fixed-value, or
    integer-sampling rule. Test equivalence with the published pipeline
    configured with those same `texts`, rather than requiring integer lookups
    to succeed. The core lookup-success fixture uses two TEXT key questions.
    Clarification is needed if integer keys are intended to match generated rows.
    **Tests:** `integerKeySuggestionsHaveExactlyTheSpecifiedTextsOverrideSemantics`
    and the public pipeline helper. The integer fixture is an isolated temporary
    variant of the resource XForm; no production inputs are changed.

22. **Question:** Does an existing `fields` entry block suggested keys even if
    it contains only `missingRate`, and do injected keys bypass global blanking?
    **Assumption:** The stated condition is whether configuration names the
    question, not whether it provides an explicit answer. Any existing entry
    prevents injection. An injected `texts` entry otherwise behaves exactly
    like user-supplied `texts`, including replacing an existing instance
    default, respecting global missing rate, and leaving required-field rules
    unchanged. Fixed values, texts, expressions, and conditional rules retain
    their normal precedence. Suppressing a suggestion does not suppress file
    creation.
    **Tests:** The explicit-override parameterized test,
    `merelyNamingAFieldInConfigurationSuppressesSuggestedTexts`,
    `suggestedTextsStillObeyGlobalMissingRateForOptionalQuestions`, and
    `generatedKeysReplaceAnUnconfiguredInstanceDefaultAndReloadedCalculationsMatchRows`.

23. **Question:** How can byte output, seed forwarding, and configuration
    preservation be checked without inspecting implementation or fixing an
    unspecified pseudorandom algorithm?
    **Assumption:** Continue confirmed assumption 11: independently load the
    original input/media, call the published `FakeAttachments.of(form, seed)`,
    persist its output in a separate oracle directory, add only absent field
    entries to independently parsed JSON, reload, walk, and export through
    public APIs. Independently check CSV key/value joins, activated branches,
    repeat row counts, real-file bytes, exact report sections, and search codes
    so orchestration assertions are not only API-to-API equality. Determinism
    snapshots recurse into attachments; child JVMs change actual working
    directories and time zones. All temporary files stay under `.codex-runs`.
    **Tests:** `CliAmendment2PipelineTest` and the determinism/rerun tests in
    `CliAmendment2LifecycleTest`. The converter test reuses the existing,
    explicitly approved process double without changing it.

24. **Question:** What happens to an existing destination CSV on a rerun, and
    are copying failures ordinary output failures?
    **Assumption:** The unchanged base rules apply: files the run writes replace
    existing files, unrelated files remain, and a write/copy failure returns 1,
    prints no success output, and writes no fresh report. Missing-file detection
    uses the selected source media directory, not stale output files. Real
    source CSVs are copied byte-for-byte, including unread CSVs. The tests do
    not specify `.CSV` case handling, recursive media copying, symlinks, or
    source-media/output-directory aliasing, which the amendment leaves open.
    **Tests:** `rerunReplacesGeneratedAndCopiedFilesButPreservesUnrelatedOutputAndInputs`,
    `allRealCsvFilesAreCopiedByteForByteAndRemainReadableAfterReload`,
    `anExistingFileWinsOverInventionWhileTheOtherMissingFileIsStillBuilt`,
    and `attachmentWriteOrCopyFailureReturnsOneWithNoSuccessOutputOrReport`.

## Amendment 2 validation

- `mvnw.cmd -o -q test-compile` reaches test compilation but cannot complete:
  the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` raises
  `java.nio.file.AccessDeniedException`. The `-e` retry confirms the same
  existing Windows file-access issue recorded for previous test batches.
  Logs: `.codex-runs/cli-amendment2-test-compile.log` and
  `.codex-runs/cli-amendment2-test-compile-detail.log`. No dependency was
  installed, repository copied, drive mapped, or build configuration changed.
- The repository's existing in-memory diagnostic compiler successfully
  compiles all **12 CLI/support sources**, including the four new Amendment 2
  sources, against cached binary APIs and JUnit 5 with `--release 17`.
  Log: `.codex-runs/cli-amendment2-memory-compile.log`. This is supplementary
  compilation evidence, not a successful Maven build.
- The public-API fixture probe loads both new XForms and their catalogs, then
  walks and exports them with controlled media. It verifies that the fixture
  can activate a preload-dependent branch, create two counted repeat rows,
  produce the intended impossible-constraint warning, and resolve cascading
  search choices/labels. The repeat count references a calculated node, as
  required by the XForm engine. Log: `.codex-runs/cli-amendment2-fixtures.log`.
- A diagnostic JUnit run executes all **48 new cases**, with no skipped or
  aborted tests and no discovery/container failures: **4 pass, 44 fail**.
  Every failure is due to the current CLI rejecting `--fake-attachments` as
  an unknown option, either directly or instead of the expected downstream
  diagnostic. Log: `.codex-runs/cli-amendment2-junit.log`. Consequently the
  successful fake-attachment paths cannot yet be validated end to end.
- `git diff --check` passes. Only this questions file is modified; all Java
  and resource additions are new files. Existing tests/resources, production
  code, specifications, and build files are unchanged. No commit was made.
