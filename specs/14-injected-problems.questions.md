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

## Amendment 1 additions (2026-10-24)

This run adds only new `InjectedAmendment1*` classes and the resource
`ctosim/injected-problems/amendment1/all-faults.xml`. All previously committed
tests and fixtures remain unchanged. Expectations below are from Amendment 1
and its explicitly referenced specifications. No production source was read,
searched, or modified.

### A1. Can outlier targets overlap targets from the original table?

**Question:** The outlier allocation paragraph inherits the original rule of
drawing without replacement across problems, but the next paragraph explicitly
allows an outlier on an original-table target. Which rule governs allocation,
short-count warnings, and duplicate-original eligibility?

**Assumption:** Do not decide that conflict. Outlier allocation/count tests use
zero original-table rates. Coexistence tests cover faults with original-table
targets and faults with outliers. The warning-order test requests only one
reinterview and one outlier among four submissions, so there is enough room
under either allocation interpretation. Exhausted allocation and outliers
combined with duplicate targets are deliberately not asserted.

**Tests:** `InjectedAmendment1OutlierTest` count/target tests;
`InjectedAmendment1InteractionsTest` coexistence and warning-order tests.

### A2. Which warnings apply when there is just one enumerator?

**Question:** For nonempty F and E=1, both `m < |F|` and `m=0` are true.
Should both the cumulation warning and the no-fault warning appear?

**Assumption:** No exact warning count or literal sentence is prescribed in
that case. Require no injected fault, unchanged interviews, and an enumerator
warning. For positive m, enforce the specified cumulation threshold and the
number `2 * |F|`. The fewer-than-ten rule includes zero submissions because the
spec says "each faulty enumerator", without excluding unused ones. Counts
for resent copies are not asserted because whose sample they count toward is
not explicit.

**Tests:** `oneEnumeratorCannotReceiveAnyFault`,
`faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder`,
`aSubsetOfFaultsUsesOnlyItsOwnMembersAndItsOwnCumulationThreshold`,
`smallSampleWarningNamesEachFaultyEnumeratorAndItsExactCount`,
`anAssignedEnumeratorWithZeroSubmissionsStillGetsASmallSampleWarning`.

### A3. Does fault assignment use configuration order or canonical order?

**Question:** Spec 04 preserves the input fault array's order, whereas
Amendment 1 defines F "in the order tooFast, skipModules, dontKnow".

**Assumption:** Assignment uses that explicit canonical order, after selecting
only configured faults. Reordering the array does not change assignments or
results. No particular seed-to-enumerator mapping is assumed.

**Tests:** `faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder`,
`orderingTheConfiguredFaultListDifferentlyDoesNotChangeAssignmentsOrResults`,
`aSubsetOfFaultsUsesOnlyItsOwnMembersAndItsOwnCumulationThreshold`.

### A4. Can faults replace ordinary answers supplied by overrides?

**Question:** Is an answer from a fixed-value override still the answer drawn
"as usual" for skipModules/dontKnow, or is it protected?

**Assumption:** Yes: fixed overrides are not among "Questions left alone".
Some fixtures fix the ordinary answer to make every eligible Bernoulli
opportunity observable. Replacement must still satisfy the form engine.
Outlier tests do not use fixed overrides for the selected numeric question,
because its distribution D in that case is not defined.

**Tests:** Module/roster tests in `InjectedAmendment1SkipModulesTest`, select
tests in `InjectedAmendment1DontKnowTest`, all-fault assignment and interaction
fixtures.

### A5. What lower limit applies to an uncounted roster?

**Question:** For an enumerator-controlled repeat, "drawn the same way" refers
to a low derived from a count question's hints, but there is no count question.
Does the configured repeat minimum constrain the shortened count?

**Assumption:** Without a count question, there are no count-question hints,
so low=1. The ordinary configured range draws a; it does not prohibit the
specified faulty shortening below that range. Zero/one are retained, and a
counted question's lower hints are rounded to the smallest allowed integer.

**Tests:** `rostersAreShortenedHalfTheTimeUniformlyBelowTheOrdinaryCount`,
`anUncountedRosterAlreadyAtZeroOrOneIsNotShortened`,
`countLowerBoundUsesTheSmallestAllowedWholeNumber`,
`aCountAtOrBelowItsLowerBoundIsKept`.

### A6. How do multiple problems on the same answer compose?

**Question:** When dontKnow and an outlier replace the same numeric answer,
which value wins, and which records remain? Do skipModules and dontKnow use
the same original answer when they both act on a filter? Does the new
"in every record" cleanValue rule supersede the earlier explicitly empty
values for original-table problems?

**Assumption:** Do not impose unspecified same-field conflict precedence or
revise old tests. Mixed-answer tests give dontKnow and outliers different
eligible fields. The unambiguous timing composition multiplies factors before
rounding. Each duration counterfactual retains the other duration modifier or
the already skipped modules. Resent copies have only their resent record.

**Tests:** `durationAndFaultFactorsMultiplyBeforeRoundingAndEachCleanValueKeepsTheOtherProblem`,
`tooFastCleanDurationKeepsTheSkippedModulesOfTheSameSubmission`,
`dontKnowAndAnOutlierCanAffectDifferentAnswersOnTheSameSubmission`,
`resentCopiesKeepFaultyOriginalAnswersAndTimesButHaveOnlyTheirResentRecord`.

### A7. Which distribution/rounding details are fixed for outliers?

**Question:** Are uniform integer quartiles discrete or continuous, how are
decimal tail draws serialized, and what happens for a degenerate distribution?

**Assumption:** Exact uniform fences are tested with decimals only. Integer
tests use normal overrides with known continuous theoretical fences and
require whole-number injections within inward-rounded constraints. Normal
and lognormal moments ignore override bounds as stated. Decimal comparisons
allow half a hundredth at interval boundaries, consistent with spec 05.
Zero-variance distributions, initially blank numeric answers, and the
unspecified distribution of fixed/conditional-expression overrides are left
open. Relevance and calculations may change after an injected answer; other
numeric answers must remain ordinary, without imposing their precise draw.

**Tests:** Distribution, hint clipping, integer tail, singleton-fence,
eligibility, and replay tests in `InjectedAmendment1OutlierTest`.

### A8. How can the twenty-refusal limit be observed?

**Question:** The public API exposes the completed submission and warnings,
not the count of attempted outlier answers. How should an independent test
observe exactly twenty attempts without implementation hooks?

**Assumption:** Assert the public fallback contract: a nonlinear constraint
rejects every outlier candidate, the ordinary answer survives, no outlier
record appears, and a warning names the submission and question. No private
state inspection or timing-based proxy for exactly twenty attempts is used.
Other warning assertions match required names/numeric tokens rather than
inventing message wording. A no-eligible-question warning need only name the
submission, as the spec states.

**Tests:** `twentyRejectedOutlierDrawsFallBackToTheUsualAnswerAndNameTheQuestion`,
no-eligible-question tests, `warningsForFaultsAreBetweenOriginalProblemWarningsAndOutlierWarnings`.

### A9. What statistical assurance is required without a prescribed PRNG?

**Question:** What exact sequences or tolerances should tests demand for
uniform selections and Bernoulli fault probabilities?

**Assumption:** No sequence is prescribed. Use fixed seeds, exact boundary
assertions, and up to 800 assigned-enumerator opportunities. Frequencies use
seven binomial standard deviations plus two observations. Assignment/target
uniformity uses 200-240 fixed seeds. Tests require the sampled cases to occur
instead of silently skipping them. Record ordering is checked by submission,
then problem-table order, then field order where multiple records occur.

**Tests:** Statistical methods across all new classes; assignment replay,
outlier replay, multiple dontKnow records, and interaction metadata checks.

### Amendment 1 verification

**Question:** Can the requested offline compilation verify these additions in
this sandbox?

**Observation:** The final `.\mvnw.cmd -o -q test-compile` exits 1 at
`testCompile`. An offline `-e` retry confirms
`java.nio.file.AccessDeniedException` for
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` in
`WindowsPath.toRealPath`. This is the same environment limitation recorded
above. Maven compilation cannot be reported as successful.

**Supplementary verification:** Adapted the existing diagnostic compiler to
compile the eight new Java sources and seven existing test/support sources
against compiled public classes and cached dependencies, using JavaCompiler
with `--release 17` and annotation processing disabled. All 15 sources compile
successfully. This reads compiled classes as compiler inputs, not production
source or implementation details.

A diagnostic JUnit run selects only the seven new test classes: all 103
invocations are discovered and started; 29 pass and 74 fail with assertion
failures against the current implementation. There are no skipped/aborted
tests and no fixture-loading exceptions. Failures are expected for the
unimplemented amendment; this run is not represented as a passing test suite.

**Logs:** `.codex-runs/spec14-amendment1/maven-test-compile.log`,
`maven-test-compile-detail.log`, `diagnostic-compile.log`, and
`diagnostic-junit.log` in the same directory. All diagnostic code and output
remain under `.codex-runs/`; no Maven repository was copied, no drive mapped,
no dependency installed, and no production/build configuration changed.

## Amendment 2 additions (2026-10-24)

This run adds only `InjectedAmendment2Support`, four new test classes
(`RefusalTest`, `RosterTest`, `ConstraintTest`, `MovementTest`, each with the
`InjectedAmendment2` prefix), and two XML resources under
`src/test/resources/ctosim/injected-problems/amendment2/`. Previously committed
tests and fixtures are preserved. Production source was not opened, searched,
or modified; no commit was made.

### B1. What happens to committed Amendment 1 expectations that Amendment 2 replaces?

**Question:** Should earlier half-probability roster tests and twenty-refusal
diagnostic tests be changed to agree with Amendment 2?

**Assumption:** No: this run explicitly requires new files/classes only and
preservation of committed tests. New tests require quarter-probability roster
shortening and the final failed-origin warning instead of the twenty-refusal
warning. Earlier contradictory tests need a separate authorized dispute/update.

**Tests:** `countedAndEnumeratorControlledRostersAreShortenedOneQuarterOfTheTime`,
`exclusiveRealFenceWithNoAcceptedOutlierProducesOnlyTheFinalTargetWarning`,
`exhaustedMovementWarnsOnlyOnceAndNamesTheOriginalTarget`.

### B2. How is the fake-refusal probability interpreted at rate zero?

**Question:** `(1-r)/r` is undefined at zero. Should it cause an error or an
extra problem record even though no respondent would consent?

**Assumption:** The rule applies only to would-consent submissions. At zero
there are no eligible submissions and no fake-refusal records; at one the
replacement probability is zero. For rates at or below one half every
would-consent interview of the assigned enumerator is converted. Natural
refusals never acquire a fake-refusal record. These expectations follow the
eligibility sentence without prescribing how the implementation computes it.

**Tests:** `zeroAndCertainConsentProduceNoInventedRefusalRecords`,
`onlyWouldConsentInterviewsAreConvertedAtTheSpecifiedConditionalRate`.

### B3. What if a fake refusal cannot be applied to its last consent field?

**Question:** What record, if any, is required when that field is absent,
irrelevant, read-only, has no offered refusing choice, or rejects the refusal?
What wins when dontKnow also wants to replace the same consent answer?

**Assumption:** Do not invent record or same-field conflict rules. The consent
fixtures have a reachable last field, both offered values, and no rejecting
constraint. The combined-fault fixture has no special code on either consent
question, and gives dontKnow its own independent field. Original consent
refusal semantics and unchanged earlier consent answers are checked explicitly.
Unicode values are exact, as in spec 04 Amendment 8.

**Tests:** `InjectedAmendment2RefusalTest`, especially the chain, Unicode,
canonical-assignment, ordering, and resent-copy tests. Fallback/conflict cases
described above remain open, rather than asserting undocumented records.

### B4. Which fault warnings remain when fakeRefusal has no consent configuration?

**Question:** Is an inactive fakeRefusal included in the fault count used for
assignment and cumulation warnings, and where is its missing-consent warning?

**Assumption:** "The fault takes no enumerator" excludes it from active fault
allocation. Adding it to tooFast at E=2 leaves assignments and submissions
unchanged and adds only its one missing-consent warning. Its warning belongs
between original-table warnings and outlier warnings. Ordering relative to
other fault warnings and exact prose are not imposed. A sole enumerator uses
the already clarified no-fault rule, not an additional cumulation warning.

**Tests:** `withoutConsentTheFaultWarnsOnceAndDoesNotConsumeAnEnumeratorSlot`,
`fakeRefusalAloneWithoutConsentDoesNotGuessConsentFromTheForm`,
`missingConsentWarningSitsBetweenOriginalProblemAndOutlierWarnings`,
`oneEnumeratorCannotBeAssignedFakeRefusal`.

### B5. How should a direct cyclic add-another count be initialized?

**Question:** If jr:count refers directly to a question inside that same
repeat, how should the first instance be created before that question can be
answered? How does this interact with Amendment 1's count-question rule?

**Assumption:** Do not prescribe bootstrap semantics for this circular case.
An initial diagnostic fixture with a direct reference created zero instances
even with an initial row/default; it was removed rather than asserting an
undocumented initialization rule. The committed fixtures use one or two
calculated count nodes, initialized to a bounded count before any answers.
They still meet the extender definition: offered choices give different
counts, with a minimum-producing choice closing the roster. jr:count is a
node reference, not an inline XPath expression. Ordinary counts are two,
three or four, and every offered stopping choice can be accepted. No
assumption is made about an all-special set of minimizing choices or a
shorter choice the engine rejects. Direct cyclic bootstrap remains open.

**Tests:** `oneOrSeveralCalculationsConnectAnAddAnotherQuestionToItsCount`,
`replacementChoicesMinimizeCountAreUniformAndNeverUseSpecialValues`,
`replacementChoosesTheFewestInstancesRatherThanJustAnySmallerCount`.

### B6. Does a roster get a new fault draw at each extender opportunity?

**Question:** Is 0.25 a probability per roster or per encountered answer? How
are its record and counterfactual interpreted after several opportunities?

**Assumption:** "When ... answers such a question" gives one opportunity per
answer that can extend the roster. The bounded four-person fixture has three
such opportunities: final counts 1, 2, 3, 4 have probabilities .25,
.75*.25, .75^2*.25, .75^3. Its record is still one per roster, with final
count and clean count 4. Tests use no other skipModules effects in these
rosters, so disabling only roster shortening has the same count as the
same-visit ordinary walk. Two independent rosters get separate records in
form order. Nested repeats with ambiguous record names are left open.

**Tests:** `successiveAddAnotherDecisionsStopTheRosterAndReportItsFinalCounterfactualCount`,
`twoShortenedAddAnotherRostersGetSeparateRecordsInFormOrder`, replay tests.

### B7. Does constraint-aware sampling still have to be uniform?

**Question:** Amendment 2 requires an accepted value drawn from seed and
submission, but does not specify a measure for disconnected accepted subsets,
singleton points, or decimal precision. Does failure of the real upper
interval also reopen the lower interval?

**Assumption:** Require a value in the chosen interval that the engine accepts,
without imposing uniformity on the accepted subset. Uniformity is tested for
the eligible-question order, which is explicit. Normal fixtures have mean
100, sd 10, ordinary bounds 90..110, hence upper interval [130,160] and lower
[40,70]. Decimal narrow-interval cases have hundredth-aligned endpoints.
Lower-tail tests explicitly eliminate the upper tail with hints. No test
decides whether a real-engine rejection of the entire upper interval alone
permits switching to the lower tail.

**Tests:** `InjectedAmendment2ConstraintTest`, including narrow, singleton,
nonlinear, disjoint-value, uniform-order and lower-bound cases.

### B8. How can second-walk retry attempts be observed through the public API?

**Question:** No public API exposes candidate order, attempted answers or a
hook to make a constraint change specifically between probing and rewalking.
How should a test count retries or force only the first rewalk to fail?

**Assumption:** Assert observable outcomes rather than private attempt counts:
engine-rejected questions cannot prevent an accepted question from receiving
the outlier, accepted-question selection is uniform, accepted values agree
with the final instance/calculations, and exhausted visits move the outlier.
Do not invent PRNG consumption, instrument production internals, or assert a
specific second-walk failure trace. A deliberately state-changing constraint
with a defined public contract would be needed for that additional trace test.

**Tests:** `rejectedOutlierQuestionsDoNotPreventAnotherQuestionFromReceivingTheOutlier`,
`orderIsUniformAmongQuestionsThatPassTheRealConstraint`,
`InjectedAmendment2MovementTest`.

### B9. In which order do several failed outlier origins claim destinations?

**Question:** Amendment 2 specifies ascending destination search but does not
explicitly define the processing order of several original targets. Are failed
destinations reserved, and may movement use a duplicate's original?

**Assumption:** Avoid those unresolved choices. Single-origin tests assert the
first later eligible unreserved destination exactly. Multi-origin fixtures
place all possible destinations after both originals, then assert occupancy,
uniqueness and the total final warning count; they do not require a particular
origin to win. A final warning must name one of the actual failed original
targets. Originally drawn table targets stay reserved; enumerator faults do
not reserve their submissions. Movement onto duplicate originals and reuse
of unsuccessfully tried destinations are not asserted.

**Tests:** `InjectedAmendment2MovementTest`. Original targets are obtained by a
control run with all amount questions eligible, using the spec's seed/N-only
allocation contract. The protected survey id identifies the same visits in a
restricted form. No seed-to-target snapshot is assumed, and every bounded
seed search must actually exercise its requested case.

### B10. What statistical tolerances and replay contracts are assumed?

**Question:** No PRNG sequence, exact frequencies, or statistical tolerance is
prescribed. How can probabilistic failures stay reliable?

**Assumption:** Use fixed seeds and seven binomial standard deviations plus
two observations, with up to 1,000 assigned-enumerator opportunities per
roster test and 800 per consent-rate test. Exact zero/one probabilities are
also checked directly. The ordinary comparison identifies which interviews
would consent, so conversion probability is tested conditionally rather than
confounded with natural refusals. Replay compares submissions, visits,
warnings and records and verifies an unmodifiable problems list; a separate
constraint case changes JVM locale/time zone and restores both afterward.

**Tests:** Statistical, randomness and replay methods across all four new
test classes; `rate` reuses the existing test-only tolerance helper.

### Amendment 2 build verification

**Question:** Can the requested offline Maven test compilation complete in
this sandbox?

**Observation:** `.\mvnw.cmd -o -q test-compile` fails in `testCompile` on the
cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. An offline `-e`
retry confirms `java.nio.file.AccessDeniedException`. This is the environment
limitation recorded for earlier runs, not a successful Maven compilation.

**Supplementary verification:** The existing in-memory diagnostic compiler
was adapted under `.codex-runs/spec14-amendment2/`. It checks the five new Java
files and eight existing test/support dependencies against compiled API
classes and cached dependency bytes, with `--release 17` and annotation
processing disabled. No production source or implementation was inspected.
No repository was copied, dependency installed, permissions changed, or drive
mapped. All temporary code and logs are under `.codex-runs/`.

**Logs:** `maven-test-compile.log`, `maven-test-compile-detail.log`,
`diagnostic-compile.log` and `diagnostic-junit.log` in
`.codex-runs/spec14-amendment2/`.

**Final result:** The supplementary compiler reports all 13 new/support
sources compiled successfully. The diagnostic JUnit run discovers and starts
all 61 cases: 17 pass, 44 fail, with no skipped/aborted tests, failed containers,
or form-loading exceptions. Of the failures, 18 reject the unimplemented
`consent` configuration, 3 reject `fakeRefusal`, and 23 are assertion failures
for the new behavior. Assertions beyond those dependency failures remain
unexercised. The final exact Maven command still exits 1 on the cached JAR;
the supplementary compiler/run is not presented as a passing Maven build.
