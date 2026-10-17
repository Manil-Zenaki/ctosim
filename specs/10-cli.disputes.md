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
