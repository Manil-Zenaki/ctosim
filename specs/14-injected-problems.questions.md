# Spec 14: questions, assumptions, and verification

Expectations come from spec 14 and its explicitly referenced contracts in
specs 04, 07, 08, and 13. Production source was not opened, searched, or
changed. Existing test utilities are reused only for fixtures, XML parsing,
and independent assertions. No commit was made.

## 1. What is a geopoint's single answer-key value?

**Question:** Travel requests one record per location question with its value
as written in the wide export, but spec 08 exports a geopoint into four
columns and explicitly has no combined-value column. Which representation
belongs in `InjectedProblem.value`?

**Assumption:** No combined representation is assumed. Tests check geopoint
record presence, field, related key, nonmissing answer, and actual coordinates;
they check exact record values for geotraces and geoshapes, which have a
single wide-export column. Exact geopoint record-value assertions are deferred.

**Tests:** `InjectedProblemsTravelTest.anyLocationQuestionEnablesTravelAndOverridesItsMissingRate`,
`travelRecordsAllAnsweredLocationsInFormOrderButNotIrrelevantLocations`.

## 2. Which unspecified clean values should duplicate and travel records use?

**Question:** Their record descriptions do not specify `cleanValue`, unlike
the duration and start-record sections. Should a duplicate give its ordinary
unique ID and travel its ordinary location, or should these be empty?

**Assumption:** Neither value is required by these tests. The tests assert
exact clean values only for durations and the explicitly empty clean values
for offhours/overlap. CSV tests faithfully serialize whatever the caller supplies.

**Tests:** Duplicate/travel record checks; `InjectedProblemsDurationTest`;
`InjectedProblemsScheduleTest`; `AnswerKeyContractTest`.

## 3. What constitutes form order for hidden metadata and calculations?

**Question:** Start and duration fields may have no body control, and bind
order may differ from primary-instance order. Which order defines their records?

**Assumption:** Primary-instance document order expresses form order for these
fields, consistent with the document-order rule in spec 07. Fixtures deliberately
put `start_z` before `start_a` and `duration_z` before `duration_a`, with reversed
bind order. For location questions, instance and body order agree.

**Tests:** `everyDurationFieldGetsARecordInFormOrderWithItsSameVisitCounterfactual`,
`everyStartFieldProducesAnOrderedRecordUsingWideExportFormatting`,
`travelRecordsAllAnsweredLocationsInFormOrderButNotIrrelevantLocations`.

## 4. What counts as a duration calculation, and what if none holds a value?

**Question:** Does “whose calculation is duration()” include expressions such
as `2 * duration()`, `once(duration())`, or `ct:calculate-here="duration()"`?
If duration fields exist but all are irrelevant/blank, is a fallback record required?

**Assumption:** Exact XForms `calculate="duration()"` fields are tested.
Calculate-here timing probes are questions, not those calculated fields; they
do not prevent the fallback elapsed-seconds record. Expression variants and
the all-blank-calculation case are left untested. An irrelevant duration field
must not produce a record when another relevant duration field does.

**Tests:** `InjectedProblemsDurationTest`, especially
`everyQuestionAdvanceUsesOneCommonFactorWithNearestSecondRounding` and
`irrelevantDurationFieldsDoNotProduceRecords`.

## 5. Is the minimum applied before or after multiplication?

**Question:** For a large minimum, does long duration multiply the original
5–60-second draw, or the advance after the ordinary minimum has been applied?
How does an exact half-second tie round?

**Assumption:** Exact scaling and factor-distribution tests use no minimum.
The short-duration minimum test has a 90-second minimum, for which either
interpretation must still yield 90 seconds. No exact scaling expectation for
long duration with a binding minimum, or random half-tie, is imposed. Tests
intersect the allowable rounding intervals for all questions to check that
one submission-wide factor explains every advance.

**Tests:** `everyQuestionAdvanceUsesOneCommonFactorWithNearestSecondRounding`,
`shortDurationHonorsEachQuestionsMinimumAfterScaling`,
`factorsAreSpreadUniformlyAcrossTheirSpecifiedInterval`.

## 6. Are all targets reserved before duplicate originals are chosen?

**Question:** Must an original be excluded even if it is selected for a
problem later in the table? Do failed targets become available again?

**Assumption:** “No problem's target” excludes targets of every family,
including later duration allocations. A forced two-submission case reserves
submission 2 for resent and submission 1 for short, so resent has no original.
There is no assertion about recycling a failed target into later draws;
the spec gives no recycling/retry rule. Tests with several duplicates require
unique originals but do not prescribe duplicate processing order.

**Tests:** `aReservedDurationTargetCannotServeAsADuplicateOriginal`,
`originalsAreEarlierUntargetedSubmissionsAndAreNeverReused`,
`missingOriginalLeavesAnOrdinaryVisitAndWarnsForEachSkippedTarget`.

## 7. How many warnings arise when reinterview has no configured ID?

**Question:** Does “one warning that names the problem” mean one for the
configuration, or one per target? Where do count warnings sit relative to
per-submission warnings within the same problem?

**Assumption:** Missing configuration-wide ID produces one warning, as does
travel's explicitly configuration-wide absence of location questions. A blank
original ID is tested with one forced target only. Per-target missing-original
warnings are checked in increasing submission order, without simultaneously
triggering a count shortage. Count-warning wording/position relative to such
warnings is not prescribed. Warnings are matched by required names and integer
tokens, not complete wording.

**Tests:** `reinterviewWithoutConfiguredIdWarnsOnceAndInjectsNothing`,
`reinterviewWhoseOriginalIdIsBlankIsSkipped`,
`missingOriginalLeavesAnOrdinaryVisitAndWarnsForEachSkippedTarget`,
`noLocationQuestionSkipsAllTravelAndGivesOneWarning`, allocation-warning tests.

## 8. What is an ordinary fallback and a duration counterfactual?

**Question:** Should an unsuccessful injection consume an altered schedule or
compare clean duration against the original, globally clean plan?

**Assumption:** A failed injection leaves the ordinary visit. Isolated fallback
tests compare the whole run with the same configuration with problems absent.
Duration clean values instead compare the *same actual visit* via the documented
`FormWalker.walk(number, visit)` API with problems absent. This preserves any
schedule changes caused by earlier targets, as required by “same visit.”

**Tests:** All skipped offhours/overlap/travel tests; duration record tests;
the helpers `Run.clean` and `ordinary`.

## 9. Which time boundaries and units are prescribed?

**Question:** Offhours specifies a uniform two-to-four-hour interval but no
discrete unit. How should exact workday boundaries or very late prior ends behave?

**Assumption:** Offhours tests allow any resolution inside that interval;
they do not require whole minutes. Resent upload offsets and travel gaps must
be whole minutes, and overlap offsets whole seconds. Exactly 300 seconds
permits overlap with zero offset; 299 seconds does not. Exactly closing time
permits travel; one second later does not. Offhours collision tests place the
candidate strictly inside the next day's work, avoiding unspecified endpoint
details and multi-day collision rules. Workdays are measured from config.start,
as in spec 13, rather than by calendar midnight.

**Tests:** Schedule boundary tests; `travelAtExactlyClosingIsAllowedButOneSecondLaterIsSkipped`;
upload/travel offset tests. Standard fixtures start at 08:17:23 to retain seconds.

## 10. What if an overlap target itself lasts under five minutes?

**Question:** The offset formula reserves five minutes before the previous
interview ends, but a short target could itself last less than five minutes.
Should it be extended, skipped, or allowed?

**Assumption:** No resolution is imposed. Successful overlap fixtures give
both interviews at least 300 seconds, so the promised five-minute shared
interval is feasible. Only the explicitly specified previous-duration failure
is tested. Mixed-problem tests allow documented fallback and do not demand all
requested injections succeed when their schedule effects interact.

**Tests:** `overlapSharesAtLeastFiveMinutesWithPreviousInterviewAndKeepsOwnId`,
`previousDurationExactlyFiveMinutesAllowsOnlyItsStart`,
`untargetedUnrelatedInterviewsKeepCleanScheduleLocationsAndDistinctIds`.

## 11. How does reinterview expose a cascade ID through Visit?

**Question:** Spec 13 ordinarily leaves a cascade visit's ID null, while
spec 14 says a reinterview visit's ID is the original's ID. Which takes precedence?

**Assumption:** Spec 14's specific rule gives the target visit the original's
accepted survey-ID answer. The target also repeats all upper answers through
the region/district/village chain. Tests do not prescribe how that state is
implemented or how rejected cascades update internal ID history.

**Tests:** `InjectedProblemsCascadeTest.reinterviewCopiesTheIdAndEveryUpperQuestionFromItsOriginal`.
The tests reuse read-only CSV fixtures from spec 13, copied for execution under
`.codex-runs/` by the existing test support.

## 12. What geographic rules apply to travel edge cases?

**Question:** What does “in its new village” require for the interview after
travel? Do per-question area overrides override injected travel? What if all
location questions are irrelevant, or live in repeats?

**Assumption:** The next interview must be the first of the next day; no
unwritten relation to the target's point is asserted. Tests have no per-field
area override and at least one relevant, nonrepeat location question. The
distance threshold is checked on the public Visit coordinates with the spec 13
6,371-km sphere; printed answer coordinates may differ by up to 30 m plus
six-decimal rounding. Repeat record naming, all-irrelevant location fallback,
and override precedence are deferred.

**Tests:** `InjectedProblemsTravelTest`; geographic assertions in schedule and
mixed-plan tests.

## 13. What exact random sequence or statistical tolerance is required?

**Question:** The specification prescribes draws and independence, not a PRNG,
stream layout, exact seed snapshots, or a statistical test.

**Assumption:** Tests do not hard-code selected submission numbers for a seed.
Forced eligibility sets establish exact boundaries; fixed ensembles exercise
otherwise randomly selected middle targets and assert that the scenario was
actually observed. Target/original selection uses 240 fixed seeds. Factor,
upload-delay, schedule-offset, and travel-area tests use 400–600 observations
and broad seven-standard-deviation bin tolerances. Travel's coarse area-grid
test uses a large rectangle and allows the small excluded-distance region.
Factor tests allow bounded whole-second rounding error. Assertions compare
identical seed/config runs, and compare changed forms only where the spec says
draws must not depend on answers. No specific alternative seed pair is required
to differ by chance.

**Tests:** Statistical tests in contract, duplicate, duration, schedule, and
travel classes; locale/time-zone replay and answer-independent targeting tests.

## 14. What additional constructor/input/lifetime contracts should be tested?

**Question:** Does InjectedProblem validate null components, unknown labels,
or submission numbers? Must returned lists remain snapshots after later calls?

**Assumption:** No constructor validation or old-list lifetime contract is
invented. CSV rejects a null list and null records, exactly as specified.
Returned lists are unmodifiable, and fresh calls replace rather than accumulate
the last results. Zero submissions is not a valid configuration under spec 04;
empty-list coverage is therefore at the CSV API and pre-walk problems list.

**Tests:** `AnswerKeyContractTest`, lifecycle and immutability contract tests.

## Build verification

**Question:** Can the requested offline Maven compilation complete in this sandbox?

**Observation:** `.\mvnw.cmd -o -q test-compile` reaches `testCompile` but fails
with `java.nio.file.AccessDeniedException` for the existing
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, in the JDK ZIP
filesystem's `WindowsPath.toRealPath` during close. An offline `-e` run confirms
the cause. Forked javac also reports unresolved existing symbols and the same
path-access error. No repository was copied, drive mapped, permission changed,
dependency installed, or production/build configuration modified.

Logs: `.codex-runs/spec14-test-compile.log`,
`.codex-runs/spec14-fork-compile.log`, and the exact-command final retry
`.codex-runs/spec14-test-compile-final.log`.

**Supplementary check:** The existing diagnostic compiler approach was adapted
under `.codex-runs/spec14-check/`. It reads existing compiled API classes and
cached JAR bytes into memory without inspecting production implementation, and
uses JavaCompiler with `--release 17` and annotation processing disabled. This
checks the eight new Java sources and five existing test-support dependencies.
Its successful result is supplementary compilation evidence, not a successful
Maven build. Compile/runtime logs are `compile.log` and `junit.log` in that
directory. The suite is also executed through the available JUnit Platform to
check test discovery and fixture loading; implementation failures are expected.

The final suite contains 106 JUnit invocations. The diagnostic execution starts
all 106, with 2 passing and 104 failing (99 `UnsupportedOperationException`
failures from unimplemented APIs and 5 assertion failures for their missing
behavior). There are no aborted/skipped tests or fixture-loading failures.

## Dispute verification (2026-10-24)

**Question:** Can the offline Maven suite verify the cascade-fixture correction?

**Observation:** `mvnw.cmd -o -q test` failed at `testCompile`, reporting
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The Maven suite
therefore did not run. See `.codex-runs/spec14-dispute-maven.log`.

**Verification approach:** Reused the existing diagnostic compiler against
compiled API classes and cached dependencies, without reading production
source or copying Maven repositories. All 13 spec 14 test/support sources
compiled successfully. A diagnostic JUnit runner selected only
`InjectedProblemsCascadeTest`: both disputed tests passed, with no skips
or aborted tests. This is supplementary verification, not a successful
full Maven run. Logs: `.codex-runs/spec14-dispute-compile.log` and
`.codex-runs/spec14-dispute-junit.log`.

**Tests:** `reinterviewCopiesTheIdAndEveryUpperQuestionFromItsOriginal`
and `resentCopiesACascadeSubmissionWithoutConsumingAnotherHouseholdId`.
