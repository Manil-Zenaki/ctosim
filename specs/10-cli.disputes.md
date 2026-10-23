# Disputes — spec 10 (command line)

## Dispute 1: the runaway fixture cannot fail within the test's 45 seconds

`CliFailureTest.walkNavigationLimitFailureReturnsOneWithoutAReport` runs
the CLI on `runaway.xml`, a counted repeat of 100 005 instances of one
read-only question, in a child JVM that must finish within 45 seconds.
The walker stops at the 100 000-step limit (spec 07) as required, at
`/data/row[50000]/note[1]`, but reaching it takes about 50 seconds on the
owner's machine: JavaRosa's navigation slows down with the size of a
repeat (each step costs more as instances accumulate), so the time grows
roughly with the square of the number of instances crossed. The result
(status 1, a message naming the submission, no report) is correct; only
the harness timeout is exceeded.

Requested change: keep the test's assertions but reach the step limit
with fewer repeat instances, for example several read-only questions per
instance (ten notes per row and a count of 10 001 reach 100 000 steps
around row 5 000), so that the run is well within the timeout.

## Adjudication

### Dispute 1: accept the fixture change; keep the assertions

Spec 10 requires status 1 when a walk fails, an error naming the failed
file or step, no standard output on failure, and a run report only when
the run completes. The disputed test's assertions enforce those rules
and remain unchanged. The referenced spec 07 requires a walk exceeding
100000 navigation steps to fail, but neither spec imposes a 45-second
runtime limit. That timeout is a harness safeguard, as already stated in
question 12; timing out alone does not establish a contract violation.

Changed `runaway.xml` to 10001 counted repeat instances containing ten
read-only notes each, and updated its field catalog to match. The 100010
question visits alone exceed the navigation limit without depending on
an exact count of repeat-navigation events or a particular failure path.
The fixture still exercises the CLI's handling of a walk failure while
reducing the repeat-instance workload. The shared timeout is unchanged.
Question 12 now describes the revised fixture. No implementation source
was read or modified.

Validation: `mvnw.cmd -o -q test` was attempted but could not complete
test compilation because of `AccessDeniedException` on the cached
`commons-io-2.5.jar`; an `-e` retry confirmed the cause. The existing
diagnostic compiler successfully compiled all seven CLI test/support
sources with `--release 17` against cached binaries. The existing
diagnostic JUnit launcher then ran all 138 CLI tests: all passed, none
were skipped or aborted, including the disputed test with the unchanged
45-second child-process timeout. Logs are under `.codex-runs/`:
`cli-dispute-maven.log`, `cli-dispute-compile.log`, and
`cli-dispute-junit.log`. The diagnostic run is supplementary validation,
not a successful Maven build.

## Dispute 5: fictitious files read with the export parser; integer keys (Amendment 2)

1. Ten tests of `CliAmendment2PipelineTest` and `CliAmendment2ReportTest`
   (for example `generatedFilesAreWrittenExactlyWithTheDefaultSeed`,
   `anExistingFileWinsOverInventionWhileTheOtherMissingFileIsStillBuilt`,
   the four `anyExplicitAnswerOverrideTakesPrecedenceOverSuggestedKeys`
   cases) read `attachments/<name>.csv` through
   `SurveyCtoExporterTestSupport.csv`, which asserts "CSV must not have a
   final line feed". That rule belongs to SurveyCTO exports (spec 08).
   The fictitious files follow spec 12, "Content of a file": "lines end
   with LF", confirmed by assumption 3 of `12-fake-attachments.questions.md`
   ("the final CSV record is terminated by LF"), and the spec 12 tests
   require that final LF. The CLI tests should read these files with a
   parser that accepts the final LF, as spec 12 requires them to have it.
2. `integerKeySuggestionsHaveExactlyTheSpecifiedTextsOverrideSemantics`:
   assumption 21 of `10-cli.questions.md` pointed out that `texts` has no
   effect on an integer question, so integer keys could never match. The
   amendment's step 3 is now amended: a question whose keys are row
   numbers `1` to `N` is answered as if the configuration gave it
   `{"min": 1, "max": N}`. The test should check that semantics (answers
   within 1 to N, lookups succeeding).

## Adjudication of Dispute 5 (2026-10-17)

### Point 1: accept; use the fictitious-file CSV contract

Spec 10 Amendment 2 writes the content supplied by spec 12. Spec 12's
"Content of a file" and its clarification explicitly require a final LF.
The export helper's prohibition on a final LF therefore imposed the wrong
contract on these attachments.

Added `CliAmendment2Support.fictitiousCsv`: it requires the final LF,
removes just that record terminator, and delegates cell parsing to the
existing independent CSV reader. BOM, quoting, embedded cell line breaks,
and row-width checks remain intact. Both `lookup` (used by the pipeline
and report tests) and the direct attachment row-count assertion now use
this adapter. Export parsing remains unchanged, and attachment bytes are
still compared exactly against `FakeAttachments.files()`.

### Point 2: accept; replace the superseded integer-text expectation

Amendment 2 step 3 now expressly gives integer row-number keys a field
range of `min: 1, max: N`; its clarification rejects the former assumption
21. Renamed the test to
`integerKeySuggestionsUseOneThroughNRangeAndLookupsSucceed`. It checks
that the no-level attachment has 50 data rows, every exported integer key
is in `1..N`, and each calculated lookup matches its generated CSV row.
The global integer range remains fixed at 700, outside the generated keys,
so ignoring the injected range cannot satisfy these assertions.

The public-pipeline oracle now accepts the fixture's integer key question
names and supplies the numeric range for those questions, retaining
`texts` for text questions and preserving any existing field entry. The
test still compares all export bytes against that public pipeline. Updated
question 21 to record the amended contract instead of the obsolete assumption.

### Previously adjudicated Dispute 1: retain the current test and fixture

The current runaway fixture already contains 10001 repeat instances with
ten read-only questions each, as the earlier adjudication requested. Its
status-1, error-step, empty-stdout and absent-report assertions follow
spec 10's failure rules. No further change is needed; the 45-second timeout
remains a harness safeguard rather than a specification requirement.

### Validation

- `mvnw.cmd -o -q test` stopped during test compilation. A diagnostic
  `-e` retry confirms `AccessDeniedException` for the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Logs are
  `.codex-runs/cli-dispute5-maven.log` and
  `.codex-runs/cli-dispute5-maven-detail.log`.
- The existing diagnostic compiler successfully compiled all 12 CLI and
  support sources with `--release 17` against cached binary dependencies.
  Log: `.codex-runs/cli-dispute5-compile.log`.
- The existing diagnostic JUnit launcher executed all 48 Amendment 2 cases:
  47 passed, one failed, none skipped or aborted, and no container failures.
  The corrected integer-key test passed. Log:
  `.codex-runs/cli-dispute5-junit.log`.
- The remaining failure is
  `xlsformConversionPrecedesGenerationAndItsWarningsAndFileOrderArePreserved`:
  the report lists `form.xml` and `form.fields.json` before the generated
  attachment paths. Keep this assertion: Amendment 2 inserts the generated
  attachment entries after the long exports, ahead of the converted-form
  entries in the otherwise unchanged base order. The CSV fix lets this test
  reach that independent ordering assertion.

The diagnostic compilation and execution are supplementary validation,
not a successful Maven build. No production source was read or modified,
no dependency or build configuration was changed, and no commit was made.

## Dispute 6: tests that rebuild a run with `FormWalker.walk(k)` (Amendment 4)

Amendment 4 makes a run simulate through the fieldwork plan:
`new Fieldwork(form, config).walkAll()` instead of `FormWalker.walk(k)`
for each `k`, so the submissions now have planned enumerators, start
times and locations. Four earlier tests build their expected exports by
calling `FormWalker.walk(n)` for `n` = 1 to N, and compare them byte for
byte with the run's:

- `CliRunTest.exportedBytesEqualWalkingSubmissionsOneThroughNWithEffectiveConfiguration`
  (line 196; its oracle is lines 192-194);
- `CliAmendment2PipelineTest.injectingKeysPreservesOtherSettingsAndExplicitFieldsExactly` (line 145),
  `integerKeySuggestionsUseOneThroughNRangeAndLookupsSucceed` (line 176) and
  `merelyNamingAFieldInConfigurationSuppressesSuggestedTexts` (line 131),
  whose oracle is `CliAmendment2Support` lines 113-115.

Each fails on the first differing line of the wide export (the
`SubmissionDate`, `starttime`, `endtime` and enumerator columns). The
Amendment 4 tests already compare runs with `Fieldwork.walkAll()` on the
same inputs (assumption 31), and they pass.

Requested: build these oracles with `new Fieldwork(form,
config).walkAll()`, keeping what each test checks.

## Adjudication of Dispute 6 (2026-10-23)

Accept all four oracle corrections. Amendment 4 explicitly replaces the
CLI's independent `FormWalker.walk(k)` calls with
`new Fieldwork(form, config).walkAll()`. Its clarification confirms the
public plan as the comparison oracle on the same inputs (assumption 31).
Thus the old expected submissions are no longer specified CLI behavior;
requiring their export bytes would reject the newly required plan.

- `exportedBytesEqualWalkingSubmissionsOneThroughNWithEffectiveConfiguration`:
  replace the walker and loop with a `Fieldwork` using the same loaded
  form and configuration with submissions 3 and seed 42. Rename the test
  to `exportedBytesEqualFieldworkPlanWithEffectiveConfiguration`. Keep
  all wide/long export comparisons, the server setting, and the distinct
  instance-ID assertion.
- `injectingKeysPreservesOtherSettingsAndExplicitFieldsExactly`: correct
  its shared `assertPublicPipeline` oracle to call `Fieldwork.walkAll()`.
  Preserve suggested-key injection before constructing the plan, all
  unrelated settings and explicit fields, effective seed/count, the
  independent media reload, all export comparisons, and the assertions
  that the input configuration is unchanged and its settings are reported.
- `integerKeySuggestionsUseOneThroughNRangeAndLookupsSucceed`: use that
  same corrected oracle. Keep the 50-row attachment assertion, every
  integer answer's `1..N` range check, successful lookup checks, and export
  comparisons. Amendment 4 does not repeal Amendment 2's integer-key rule.
- `merelyNamingAFieldInConfigurationSuppressesSuggestedTexts`: use that
  same corrected oracle. Keep the independent check that exported keys
  are nonempty and outside the generated keys, and the export comparisons.
  The confirmed Amendment 2 rule that any existing `fields` entry blocks
  suggested answers remains unchanged.

Only the two oracle locations needed code changes. Updated the questions
file's renamed-test reference and noted that Amendment 4 supersedes its
earlier walker-oracle description. No new contract assumptions are needed.

### Earlier disputed points: retain the adjudicated tests

Dispute 1 already has the reduced-repeat fixture (10001 rows of ten
read-only notes); keep its failure assertions and existing timeout. Spec
10 requires status 1 for a failed walk and no report for an incomplete run,
without specifying a runtime limit. Dispute 5 point 1 already uses the
fictitious-file CSV adapter accepting and checking the final LF; keep it,
since generated attachments follow spec 12, not the export CSV contract.
Dispute 5 point 2 already checks the amended integer range and successful
lookups; keep those assertions. Its only further change is the shared
fieldwork oracle described above. No fixture changes are needed.

### Validation

- `mvnw.cmd -o -q test` stopped during test compilation. An `-e` retry
  confirms `java.nio.file.AccessDeniedException` for the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Logs:
  `.codex-runs/cli-dispute6-maven.log` and
  `.codex-runs/cli-dispute6-maven-detail.log`.
- The existing diagnostic compiler successfully compiled all 19
  CLI/support sources with `--release 17` against cached binary APIs and
  dependencies. Log: `.codex-runs/cli-dispute6-compile.log`.
- The diagnostic JUnit launcher ran `CliRunTest`, `CliFailureTest`,
  `CliAmendment2PipelineTest`, and `CliAmendment2ReportTest`: all 80 tests
  passed, with none skipped or aborted and no container failures. This
  includes all four tests in Dispute 6 and the earlier runaway, attachment
  CSV, integer-key, and file-order checks. Log:
  `.codex-runs/cli-dispute6-junit.log`.
- `git diff --check` passes. Diagnostic compilation and execution are
  supplementary validation, not a successful Maven build or full-suite run.

No production source was read or modified, no dependencies or build
configuration were changed, and no commit was made.
