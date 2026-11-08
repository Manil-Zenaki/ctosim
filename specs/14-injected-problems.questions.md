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

### Dispute adjudication build verification (2026-10-25)

**Question:** Can the required offline Maven suite run after adjudicating
disputes 1-4?

**Observation:** `mvnw.cmd -o -q test` again stops at `testCompile`, reporting
the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The full
Maven suite could not run; no production source or build configuration was
read or changed to address this limitation.

**Assumption:** Supplementary compilation and execution against the existing
compiled API classes can verify the edited tests, but does not establish a
successful Maven build. The adapted diagnostic compiler compiled all 26 spec
14 test/support sources. JUnit ran all 74 tests in the five affected classes:
all passed, with none skipped or aborted.

**Tests:** `InjectedProblemsCascadeTest`, `InjectedAmendment1SkipModulesTest`,
`InjectedAmendment1OutlierTest`, `InjectedAmendment2RosterTest` and
`InjectedAmendment2MovementTest`.

**Logs:** `.codex-runs/spec14-adjudication-maven.log`,
`.codex-runs/spec14-adjudication/compile.log` and
`.codex-runs/spec14-adjudication/junit.log`.

## Amendment 3 additions (2026-10-25)

Only new `InjectedAmendment3*` classes and
`src/test/resources/ctosim/injected-problems/amendment3/timeline-template.xml`
are added. Existing tests, helpers, and fixtures are unchanged. Expectations
come from Amendment 3 and its expressly inherited contracts in specs 04,
07, and 13. No production source was opened, searched, or modified.

### C1. Where can the pause be observed without a new public pause API?

**Question:** The amendment adds no public pause accessor or method to supply
one. Does a calculate-here observation immediately after the triggering
question run before or after the pause?

**Assumption:** The pause is after that question's handling and before the next
body control. Paired calculate-here snapshots of `now()` and `duration()`
consume no screen time, under spec 07. Their difference identifies exactly
one jump. The snapshot after the triggering question observes resumption.
The public final `duration()` calculation supplies screen duration; no new
Submission accessor or private hook is invented. A one-question form pauses
after its only question even though no ordinary questions remain: its handling
is necessarily the first to reach every fraction of its positive duration.

**Tests:** `InjectedAmendment3Support.interruption`, timing tests in
`InjectedAmendment3ScheduleTest`, and the fraction/gap distribution tests.

### C2. What happens with zero screen duration or exceptionally long questions?

**Question:** The promise that no target is skipped includes forms with no
handled question, but the pause rule requires a first question. A question
lasting multiple days may put the pause's beginning after the prescribed
next-day resumption. How should these cases be resolved?

**Assumption:** Leave those conflicting/unspecified cases untested rather
than invent placement or negative-pause behavior. Every leftOpen fixture has
positive screen time and a pause that can finish after it begins. Boundaries
include one question, first/last submissions, every submission targeted,
more enumerators than submissions, overnight workdays, and a ten-hour
question whose interruption still precedes its next-day resumption.

**Tests:** `singleQuestionCanTriggerItsPauseAtTheEndOfHandling`,
`everyEnumeratorsFirstAndOnlyInterviewMayBeLeftOpen`,
`pauseAfterClosingPlacesTheEnglobedInterviewAtNextWorkingDaysOpening`, and
`nextDayIsCountedFromWorkOpeningRatherThanTheCalendarDateOfThePause`.

### C3. Are reserved but unsuccessful problems still exclusions for B?

**Question:** Is a submission originally targeted for travel still excluded
as B when travel cannot be injected? Is an unsuccessful resent target still
skipped as though it were a copy?

**Assumption:** The explicit restriction concerns B being a *target* of
offhours, overlap, travel, or leftOpen. The earlier clarification says failed
targets remain reserved, so a reserved travel target is excluded even without
location questions. In contrast, the successor search skips actual resent
*copies*. Tests of skipping resent use successful copies with a distinct,
available original. No assertion decides how an unsuccessful resent target
is classified for successor selection. A conflicting next interview does
not cause a search for a later B: the defined next non-copy is tested once.

**Tests:** `aReservedTravelSuccessorIsExcludedEvenWhenItCannotInjectALocationProblem`,
`conflictingNextTargetPreventsEnglobingAndIsNotBypassedForALaterInterview`,
`resentCopiesAreSkippedWhenChoosingTheNextRealInterview`,
`onlyResentCopiesRemainingMeansThereIsNoEnglobedInterview`, and consecutive
leftOpen tests. Duplicate-original exclusion and moved-outlier reservation
are tested separately.

### C4. How do quotas, next working days, and resumed villages interact?

**Question:** Does the ordinary daily quota prevent B starting on A's day?
How is an interrupted interview counted toward quotas after resumption,
and must A's recorded point change when it resumes in another day's village?

**Assumption:** The explicit B branch tests only its proposed start against
closing, so B can start that day even at quota 1. Workdays are measured from
`config.start()`, as clarified in spec 13; no weekend/holiday calendar is
introduced. Subsequent-visit tests use a high quota to avoid guessing how
resumed interviews count. They assert the required gap after A's end and the
workday of that end. On the same day, A and B's public Visit points must be
within the specified 3 km village diameter. After overnight B, B and the next
ordinary interview share the new day's village. No unwritten relocation of
A's original Visit is asserted.

**Tests:** `englobedInterviewMayStartOnTheSameDayEvenWhenTheDailyQuotaIsOne`,
`afterEnglobingTheFollowingInterviewStartsAfterTheTargetsFinalisation`,
`anOvernightResumptionContinuesSchedulingOnTheDayTheTargetEnded`,
`consecutiveLeftOpenTargetsAreNeverSkippedAndDoNotEnglobeEachOther`, and
same-day/overnight scheduling tests.

### C5. Which random draws can be compared without prescribing a PRNG?

**Question:** No random sequence, exact seed snapshot, fraction precision, or
statistical significance level is prescribed. How should boundary and
distribution tests remain independent?

**Assumption:** Use the spec's seed/submission-only draws, not particular seed
outputs. Scenario searches scan a fixed range of 160 seeds, assert a leftOpen
record exists on each run, and fail if the requested allocation is not found;
they never silently skip. Target frequencies use 240 fixed seeds. Fraction
and no-successor resume tests use 480 targets. Gap tests create 1,000 visits
and require at least 50 englobed interviews. Frequencies use the existing
seven-binomial-standard-deviation-plus-two tolerance. Whole-minute ranges
are inclusive; uneven bins account for the 31 or 61 possible minutes.
Fraction bins allow triggering-question quantization, with no requirement to
observe an exact continuous endpoint.

For closing-boundary tests, first observe the same seed's englobed start-gap
on a single-question form, then change only its minimum handling time so the
candidate start is exactly closing or one second later. This uses the inherited
spec 13 rule that start-minute draws depend on seed and submission number,
not form answers or durations. The scaling test likewise compares the same
seed/number fraction after all screen advances double.

**Tests:** `InjectedAmendment3RandomnessTest`,
`englobedStartAtClosingIsAllowedButOneSecondLaterMovesToTheNextDay`, and
all tests using `InjectedAmendment3Support.find`.

### C6. How should other faults and time-dependent answers enter clean duration?

**Question:** Could pausing change later relevance through `now()`, and thus
change final screen duration? Does the clean visit for D retain enumerator
faults? What should an unrelated fault's cleanValue mean during a pause?

**Assumption:** "Without the problem" removes leftOpen only; other problems
remain. tooFast tests compare screen durations with runs retaining the same
faults and seed, using answers and relevance independent of clock time.
They verify that the interruption threshold uses the resulting screen time,
not ordinary elapsed wall time. Englobed short/long/outlier/reinterview/fault
targets retain their own records and actual changed values. This run does
not invent how pause-triggered relevance changes should be reconciled with
the preliminary walk's D or demand a particular counterfactual for two
conflicting changes on one answer.

**Tests:** `faultyEnumeratorStillAppliesTooFastAndItsRecordsFollowLeftOpen`,
`anEnglobedInterviewOfAFaultyEnumeratorKeepsItsFault`, the four englobed-problem
tests, and `pausePreservesOrdinaryAnswersScreenDurationAndFinalisationMetadata`.

### Amendment 3 build verification

**Question:** Can the requested offline Maven compilation complete with the
cached dependencies accessible from this sandbox?

**Observation:** `.\mvnw.cmd -o -q test-compile` stops at `testCompile` on
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The offline `-e`
retry confirms `java.nio.file.AccessDeniedException`. This is the previously
recorded environment limitation; the Maven command is not a successful build.

**Assumption:** The existing supplementary in-memory compiler can verify the
new sources against compiled public APIs and cached dependency bytes without
reading implementation source or copying a Maven repository. It successfully
compiles the five new Java files and three existing test-support files using
`--release 17`. No dependencies were installed, drives mapped, permissions
changed, or build/production files modified. All probes and logs are under
`.codex-runs/spec14-amendment3/`.

**Tests:** The four new test classes. Logs are `maven-test-compile.log`,
`maven-test-compile-detail.log`, `diagnostic-compile.log`, and
`diagnostic-junit.log` in that directory. The final exact-command Maven retry
is in `maven-test-compile-final.log`; a diagnostic run does not establish a
successful Maven build.

**Final diagnostic result:** All eight new/support sources compile. JUnit
discovers and starts all 53 test invocations: 2 pass and 51 fail with assertion
failures for the absent leftOpen behavior. No tests are skipped or aborted,
and no container, fixture-loading, or configuration failures occur. Assertions
after the missing-record/pause checks remain unexercised until the amendment
is implemented. Existing committed tests are untouched; no commit was made.

## Amendment 4 questions and test assumptions

### D1. Village-first wording versus form order

**Question:** The records paragraph says "one for the village question ...;
then one for each location question", but ends with "all in the form's
order". Which wins when a location question precedes the village?

**Assumption:** The explicit final ordering rule wins. Records are in
instance/form order, including a location asked before the village. Only
the village record gets the previous interview's village as `cleanValue`;
the location records retain the earlier travel rule's empty `cleanValue`.
The previous interview means the same enumerator's actual predecessor,
including when that predecessor is itself a travel target.

**Tests:** `recordsUseFormOrderActualValuesAndThePreviousVillagesCleanValue`,
`travelRelatesToItsOwnEnumeratorsPreviousInterviewAndRecordsAreSorted`, and
`consecutiveTravelTargetsMeasureFromTheActualPreviousVillage`.

### D2. What is uniformly drawn: viable places or ordinary weighted choices?

**Question:** Do ordinary district/village weights restrict the travel
candidate pool or change its probabilities? Do exhausted or special-only
household pools count as candidates?

**Assumption:** "Candidate" retains spec 13 Amendment 4's offered-and-viable
place definition. Ordinary zero weights do not remove offered choices.
The explicit uniform travel draw overrides ordinary weights and nearest
village selection. Exhausted, empty, and special-only household pools are
not viable. The fixtures have one unambiguous place per village, so they
do not assume how duplicate place paths or duplicate village codes would
be weighted. Village override `value`/`texts`/expression/rule conflicts,
location area overrides, and village questions in repeats are not tested.

**Tests:** `locatedVillagesEnableTravelWithOrWithoutGpsAndIgnoreTheDailyQuota`,
`eligibleVillagesAreUniformDespiteTheirOrdinaryChoiceWeightsAndDifferentDistances`,
and `aDistantVillageWhoseHouseholdsHaveAlreadyBeenReceivedIsNotACandidate`.

### D3. Which route applies when village coordinates are unusable?

**Question:** With both GPS and a configured village question, may travel
fall back to the old arbitrary-point route when the previous village is
unlocated or no distant candidate exists?

**Assumption:** No. The amendment explicitly prescribes an ordinary visit
and a submission-specific warning in those cases. The old GPS route is
retained only when there is no village question or no villages setting.
No exact warning prose is assumed. Failure warnings must name the target;
the global unsupported-form warning must name travel and occur once.
Unrelated config warnings are not counted as travel warnings.

**Tests:** `InjectedAmendment4FallbackTest`.

### D4. First-day reinterviews and the scope of village preference

**Question:** Does "the original's place is the enumerator's place of the
day" establish the day from the original? Does village preference also
apply without `config.villages()`?

**Assumption:** A first interview that is a reinterview establishes the
original's entire place (district and village), which subsequent ordinary
interviews retain while viable. The reinterview subsection requires a
village question, not coordinate configuration, so it applies with and
without locations. A later different-village reinterview neither replaces
the day's place nor charges a move, including on returning to that place.
Preference does not override the exclusion of problem targets or reuse an
original. The tests use ample free IDs, short forms, and no exhaustion on
these paths, so a later move is not required for another reason.

**Tests:** `InjectedAmendment4ReinterviewTest`, including parameterized
located/unlocated cases and a mixed short-duration/reinterview scenario
where the only same-day predecessor is a reserved target.

### D5. Observing that travel is not a move

**Question:** How can the unchanged place of the travel day be tested when
the next ordinary interview is expressly on the next day?

**Assumption:** Test observable consequences: no move surcharge, same-day
travel despite quota, a newly drawn ordinary place the following day,
and distances/clean values based on the actual previous interview. Do not
inspect hidden planner state or require an ordinary interview later on the
travel day. Consecutive travel targets retain their explicit same-day rule
even though an ordinary successor would start the next day.

**Tests:** `locatedVillagesEnableTravelWithOrWithoutGpsAndIgnoreTheDailyQuota`,
`theNextInterviewStartsTheNextDayWithANewOrdinaryPlace`, and
`consecutiveTravelTargetsMeasureFromTheActualPreviousVillage`.

### D6. Boundaries and seed-dependent draws without a prescribed PRNG

**Question:** How should tests exercise boundary distances and closing time
without assuming specific random outputs or testing floating-point noise?

**Assumption:** Gap draws depend on seed/submission number, as required by
the inherited travel rule. Observe the gap first, then change only a
village coordinate or a question's minimum time. The distance cases place
the only eligible destination one metre below, at, and one metre above
the resulting fence; at equality, advance longitude by machine ulps if
needed so the independent great-circle oracle is not below the fence.
Closing is tested exactly and one second later. Candidate comparisons use
village coordinates, not jittered GPS answers. The unchanged seed/number
draws also justify comparing forms with and without GPS when the same
candidate pool is offered.

Scenario searches use fixed seeds 100 through 339, fail if no scenario is
found, and never skip. The uniform-candidate test uses 320 independent
fixed seeds with tolerance seven binomial standard deviations plus two.
The gap-dependent candidate test uses 160 fixed seeds and explicitly
requires observations on both sides of its 15-minute cutoff. Preference
tests use 80 fixed seeds and require mixed candidate pools to be observed.
No exact target permutation, random stream, or seed snapshot is asserted.

**Tests:** The distance/closing boundary tests in
`InjectedAmendment4TravelTest`, `InjectedAmendment4RandomnessTest`, and
tests using `InjectedAmendment4Support.find`.

### Amendment 4 build verification

**Question:** Can the requested offline Maven compilation run in this
sandbox with the existing cached dependencies?

**Observation:** `.\mvnw.cmd -o -q test-compile` stops in `testCompile` at
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The `-e` retry
confirms `java.nio.file.AccessDeniedException`. This is an environment
failure, not a successful Maven build.

**Assumption:** Use the existing supplementary in-memory Java compiler
approach to check the new tests and their test helpers against compiled
public APIs and the cached dependency bytes, with `--release 17`. This
does not replace the requested Maven result. No production sources are
read or changed, dependencies installed, repositories copied, or drives
mapped. Probes and logs are confined to `.codex-runs/spec14-amendment4/`.

**Tests:** The four new Amendment 4 test classes and their new support
class. The supplementary compiler also compiles six unchanged supporting
test sources. Diagnostic runtime failures at configuration parsing are
expected for the current unimplemented `villages` prerequisite and do not
validate assertions beyond that point.

**Final result:** All eleven new/support sources compile with the
supplementary compiler. JUnit discovers and starts 42 invocations: 3 pass,
2 fail on reinterview behavior assertions, and 37 fail because the current
parser reports `villages is not a known setting`. No tests or containers
are skipped or aborted, and no containers fail. The final exact-command
Maven retry still fails on the cached JAR access described above. See
`diagnostic-compile.log`, `diagnostic-junit.log`,
`maven-test-compile-detail.log`, and `maven-test-compile-final.log` in the
run directory. Existing committed tests are unchanged; no commit was made.

## Amendment 5: user-named don't-know codes

Only new `InjectedAmendment5*` test classes and a new filtered-choice fixture
are added for this amendment. Existing committed tests remain unchanged.
Expectations use Amendment 5, the inherited fault rules in Amendments 1 and
2, and the explicitly referenced specs 04 Amendment 11 and 05. No production
sources were opened, searched, or changed.

### E1. How is an empty code list represented, and what about earlier tests?

**Question:** Amendment 5 describes an empty `dontKnowCodes()` list, while
spec 04 Amendment 11 rejects an explicitly supplied empty JSON array. How
should inactivity be exercised? Earlier committed tests inject `dontKnow`
using only `specialValues`; should those be changed in this run?

**Assumption:** Omit `dontKnowCodes` to get the empty accessor list. Explicit
empty-array validation belongs to spec 04 and is not retested here. The
user explicitly requires keeping earlier tests, so this run does not revise
them, even where their pre-Amendment-5 configuration no longer activates
`dontKnow`. Those tests would require separate adjudication or updating.

**Tests:** `InjectedAmendment5DisabledTest`, especially
`omittedCodesDisableTheFaultAndWarnOnceWithoutReservingAnEnumerator`.

### E2. What is removed when an inactive fault takes no enumerator?

**Question:** Does an inactive `dontKnow` remain in the fault list used to
compute `m`, wrap assignments, or issue cumulation and small-sample warnings?
Does a nonempty list that happens to be unusable in this form also disable it?

**Assumption:** As for inactive `fakeRefusal`, remove the inactive fault before
assignment and its associated diagnostics. The active faults behave as in
the configuration with that inactive fault omitted. A nonempty code list is
still active even if no question in this form can use it; the amendment's
disabling condition is specifically that the configuration list is empty.
If the fault is not requested, absence of codes is not a warning condition.
Supplying codes alone does not enable any problems.

**Tests:** `aDisabledDontKnowDoesNotCauseCumulationOrExtraSmallSampleWarnings`,
`laterFaultsAreAssignedAsIfTheInactiveDontKnowWereAbsent`,
`twoInactiveFaultsProduceOnlyTheirOwnConfigurationWarnings`,
`aNonemptyButUnusableCodeListStillReservesItsFaultyEnumerator`, and the two
opt-in tests in `InjectedAmendment5DisabledTest`.

### E3. Numeric spelling, constraints, and later questions

**Question:** Must a numeric record preserve the literal spelling of its
configured code? Are fractional codes coerced to integers? Must a code also
leave all future dependent questions answerable?

**Assumption:** Numeric assertions compare numeric values where spelling
is not prescribed; select codes use exact strings (case, leading zeros,
and Unicode included). A fractional code is not silently rounded to an
unlisted integer code. The engine must accept the code for the question
being answered, including its constraint referencing a preceding answer.
The ordinary numeric drawing range is not an eligibility constraint for
the fault. Future-constraint lookahead is not required by this amendment,
so it is not tested. Whitespace, exponent spelling, nonfinite numeric text,
and several different spellings of the same numeric value are left open.

**Tests:** The numeric, fractional, out-of-range, and exact-string cases in
`InjectedAmendment5CodesTest`. The constraint fixture references an earlier
read-only value so it cannot itself be changed by the fault. The filtered
choice fixture checks offered choices at answer time, not the entire list.

### E4. Warning content and ordering

**Question:** What exact warning text is required, and where inside the
fault-warning block must the missing-codes warning appear?

**Assumption:** Require one warning identifiable by `dontKnow` when the
requested fault is disabled. Do not prescribe prose, order relative to
other fault warnings, or a literal `outlier` token in an outlier warning
(the latter is only required to name its submission). Check the specified
placement after original-table warnings and before failed-outlier warnings.
Small fixtures with sufficient fieldwork days avoid unrelated warnings;
comparisons involving other faults retain their pre-existing warnings.

**Tests:** `theMissingCodesWarningAppearsAfterTableWarningsAndBeforeOutlierWarnings`
and the warning-count/allocation tests in `InjectedAmendment5DisabledTest`.

### E5. What does unchanged use of specialValues require?

**Question:** Does naming a code in `dontKnowCodes` also classify it as
special for ordinary answers or make roster shortening avoid it?

**Assumption:** No. The lists are independent. Ordinary special-answer draws
still use only `specialValues`; a nonspecial team code remains an ordinary
choice. Roster shortening may use that code when it is a count-minimizing
non-special choice, but must avoid `specialValues` even when the team names
different codes. Ordinary generation with an identical random-generator
state is unchanged merely by supplying `dontKnowCodes`. The lists may overlap;
membership in `specialValues` does not disqualify a configured fault code.

**Tests:** `InjectedAmendment5SpecialValuesTest`,
`aCodeMayBelongToBothListsWithoutBeingExcludedFromTheFault`, and
`aSingleTeamCodeWorksEvenWhenSpecialValuesIsEmpty`.

### E6. Random draws and counterfactual records

**Question:** Which random sequence and exact number of replacements should
tests require? How are ordinary answers and records compared?

**Assumption:** No PRNG sequence is prescribed. Fixed-seed statistical tests
use 200-800 eligible faulty-enumerator opportunities per field and the
existing seven-binomial-standard-deviations-plus-two tolerance. Uniform code
choice is measured only among accepted/offered configured codes. Fixtures
with fixed ordinary answers make every replacement observable and avoid
ordinary matches hiding probability events. Separate tests cover matching
codes (no record), blank answers, and independent ordinary numeric draws.
Clean values come from the same actual visit walked without the fault;
record values, KEYs, enumerators, and field order are checked against returned
submissions. Replay checks compare snapshots, fresh plans, locales and time
zones; an eight-seed check requires different target patterns without fixing
any pattern. Resent copies must preserve codes but gain no fault record.

**Tests:** `InjectedAmendment5CodesTest`, `InjectedAmendment5RecordsTest`, and
the statistical roster and ordinary-special regression tests.

### Amendment 5 build verification

**Question:** Can the requested offline Maven compilation run here?

**Observation:** Both the baseline and the post-addition invocation of
`.\mvnw.cmd -o -q test-compile` fail in `testCompile` accessing
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. This is a build
environment failure, not a Java source diagnostic. The `-e` retry confirms
`java.nio.file.AccessDeniedException`; its trace is recorded in
`maven-test-compile-detail.log` in the run directory.

### Disputes 5–6 build verification (2026-11-01)

**Question:** Can the prescribed offline Maven run compile and execute the
adjudicated tests in this environment?

**Observation:** `mvnw.cmd -o -q test` fails at `testCompile`, reporting the
cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` path. The Maven
suite cannot run; this is not a successful Maven compilation.

**Assumption:** Reuse the existing supplementary compiler and JUnit launcher,
adapted under `.codex-runs/spec14-adjudication-20261101/`, against compiled
public API classes and cached dependencies. This checks test compilation and
behavior separately without reading production source, changing the build,
installing dependencies or copying Maven repositories.

**Tests:** All 49 spec 14 test/support sources compile with `--release 17`.
The affected classes are `InjectedAmendment1DontKnowTest`,
`InjectedAmendment1CascadeTest`, `InjectedAmendment1InteractionsTest`,
`InjectedAmendment1AssignmentTest`, `InjectedAmendment2RefusalTest`,
`InjectedAmendment5RecordsTest` and `InjectedAmendment6RecordsTest`.
The Maven log is `.codex-runs/spec14-disputes-20261101-maven.log`; supplementary
logs are `compile.log` and `junit.log` in the adjudication run directory.

**Result:** The seven-class JUnit run passed 79 of 80 tests. Its sole failure
was a migrated no-op fixture using an explicit empty JSON code list. Spec 04
Amendment 11 requires omission for an empty accessor list, so that fixture
was corrected. All 49 sources compiled again (`compile-final.log`), and the
corrected test passed its targeted rerun (`junit-empty-codes.log`). All 80
affected cases have passing results across the two JUnit runs, with no skips
or aborts; this supplementary verification does not replace the blocked Maven
suite.

**Assumption:** Use the existing supplementary in-memory Java compiler
approach, with `--release 17`, to check all five new Java files and six
unchanged test support files against compiled public APIs and cached
dependency bytes. This does not replace a successful requested Maven build.
No dependencies are installed or repositories copied, and all probes/logs
are under `.codex-runs/spec14-amendment5/`.

**Result:** The supplementary compilation succeeds for all eleven sources.
The diagnostic JUnit run discovers and starts 52 invocations: 3 pass and 49
fail, with no skips, aborts, or container failures. Of those failures, 41
report the unimplemented `dontKnowCodes` configuration key, 3 report an
unimplemented accessor, and 5 fail assertions about disabled faults. These
expected failures do not validate the code-dependent assertions beyond
configuration parsing. See `diagnostic-compile.log`, `diagnostic-junit.log`,
and `maven-test-compile.log` in the run directory. Existing tests are kept;
no commit is made.

## Amendment 6: refused answers as a separate fault

This addition covers Amendment 6 only, using the inherited fault contracts
in Amendments 1, 2, and 5 and the configuration contract of spec 04
Amendment 12. All existing committed test files and fixtures are retained
unchanged. New tests are in six `InjectedAmendment6*Test` classes, with one
new support class and two fixtures under
`src/test/resources/ctosim/injected-problems/amendment6/`.
No production source was read, searched, or modified.

### F1. How is an empty refusal-code list represented?

**Question:** Amendment 6 disables the fault when the accessor is empty,
but spec 04 prohibits an explicitly empty JSON array. Does an unavailable
code in a nonempty list also disable the fault?

**Assumption:** Omit `refuseCodes` to exercise an empty accessor. Only
an empty accessor disables assignment; a nonempty unusable list remains
an active fault, even if it changes no answers. Remove disabled faults
before computing m, assignment, cumulation, and small-sample warnings.
Supplying codes alone does not request injection. Configuration validation,
including disjoint code lists and default fault lists, belongs to spec 04
and is not duplicated here. Earlier tests are not changed or adjudicated.

**Tests:** `InjectedAmendment6DisabledTest`;
`twoMissingCodeListsGiveExactlyOneWarningEachAndAllocateNoFaults`;
`disabledDontKnowDoesNotStealAnEnumeratorFromActiveRefuse`.

### F2. What does "dontKnow left unchanged" mean?

**Question:** Does a dontKnow draw that finds no usable code, or selects
the answer already present, block refuse? Is the precedence per answer
or per submission?

**Assumption:** Unchanged means the actual answer is unchanged, not that
a Bernoulli draw was never attempted. Refuse remains eligible after either
no-op case, at probability 0.25. A changed answer cannot be changed again
by refuse, but other eligible answers in that interview remain candidates.
With fixed ordinary answers and disjoint offered codes, dontKnow changes
25 percent and refuse changes 0.75 * 0.25 of the faulty enumerator's
answers on average.

**Tests:** `dontKnowWinsAndRefuseHasAQuarterChanceAmongUnchangedAnswers`;
`anUnusableOrAlreadyPresentDontKnowCodeDoesNotBlockRefuse`;
`refusalPrecedenceIsPerAnswerAndRecordsAreGroupedByFaultBeforeFieldOrder`.

### F3. What random sequence and independence are required?

**Question:** No exact PRNG stream is specified. How can independent draws,
stable assignment, and the fifth fault's ordering be checked?

**Assumption:** Do not hard-code any seed's outcomes. The stated
seed-and-E assignment rule permits comparing configurations whose m is
unchanged, including one active fault versus two cumulated faults at E=2.
Separate fault draws mean adding the later refuse fault does not consume
dontKnow's draws. Check overlap of the two faults' single-fault runs
against probability 0.25 squared, and conditional refusal frequency in
the combined run. Neither test demands the exact refusal target set stay
unchanged when its eligibility is changed by dontKnow.

Fixed-seed frequency checks use the existing tolerance of seven binomial
standard deviations plus two. The combined precedence tests use 3,000
eligible faulty-enumerator answers per select kind; the separate-stream
test uses 2,000. Other code tests use 200-800 opportunities, and uniform
enumerator assignment uses 200 seeds (100 through 299), with 60 interviews
per enumerator. Assertions require the relevant fault records and mixed
cases to exist; tests never skip for an unobserved scenario. Replay checks
compare actual submissions, visits, records, warnings, and CSV output.

**Tests:** `InjectedAmendment6PrecedenceTest`;
`refusalAssignmentIsUniformOverEnumeratorsAcrossFixedSeeds`;
`InjectedAmendment6RecordsTest` replay/seed/locale tests.

### F4. Which inherited eligibility and protection rules apply?

**Question:** Does "the same eligible questions" carry over all exclusions
and the rules preventing multiple modifications to an answer?

**Assumption:** Yes: blank, irrelevant, nonnumeric/nonselect, read-only,
calculated, survey-ID, cascade-ancestor, and repeat-count answers are not
changed by refuse. The repeat-count exclusion also applies to an
add-another choice whose answer controls an indirect count. Copied
reinterview cascade answers remain protected, and resent submissions
receive no additional fault records. The inherited protection for answers
already changed by skipModules or outliers also applies to refuse.
Tests avoid unspecified interactions between refuse and a forced consent
answer; the five-fault fixture offers refusal codes only on an independent
code question.

**Tests:** `InjectedAmendment6CascadeTest`;
`teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone`;
`refusalDoesNotChangeAnAddAnotherAnswerThatControlsAnIndirectRepeatCount`;
`resentCopiesKeepTheirOriginalTeamCodeWithoutAnExtraFaultRecord`;
`outlierAnswersAreProtectedFromTheLaterRefusalFault`;
`answersAlreadyChangedBySkipModulesAreProtectedFromRefuse`;
blank/irrelevant/noneligible cases in `InjectedAmendment6CodesTest`.

### F5. How should numeric and select code identity be tested?

**Question:** Must numeric codes preserve their spelling, and can an
unusable refusal code fall back to a dontKnow or ordinary special code?

**Assumption:** Numeric identity is by numeric value, following the
Amendment 5 clarification inherited by refuse. Fractional codes are never
rounded to integers, and a code numerically equal to the ordinary answer
does not create a changed-answer record. Select codes preserve exact
strings, including case, leading zeros, and Unicode. Selection is uniform
among currently offered codes, or numeric codes accepted by the current
question's engine constraint; ordinary drawing bounds are not constraints.
There is no fallback to another code list. A refuse code may also be an
ordinary specialValue, but the dontKnow and refuse lists in these fixtures
are disjoint. No later-question lookahead, nonfinite numeric text, exponent
spelling, or duplicate numeric spellings in a list are prescribed by
these tests.

**Tests:** `InjectedAmendment6CodesTest`;
`eachFaultUsesOnlyItsOwnCodesEvenWhenBothListsAndSpecialsArePresent`;
`anUnusableOwnListNeverFallsBackToTheOtherFaultsOfferedCodes`.

### F6. What does cleanValue mean when both faults are active?

**Question:** The general counterfactual keeps other problems, but removing
a successful dontKnow could newly enable refuse on that same answer.
Does a dontKnow record need to simulate that newly enabled draw?

**Assumption:** Do not impose a resolution on dontKnow records in a
combined-fault run. Exact cleanValue checks there are restricted to refuse
records, which restore the unchanged incoming answer. Standalone refuse
records compare against the same actual visit walked without the fault,
retaining actual visit timing and independent ordinary answer draws.
The tests check returned field values, keys, enumerators, statistical
classification, empty relatedKey, per-field uniqueness, and ordering.
Mixed records are grouped by fault first, then form order, even when
bind order or lexical field order differs.

**Tests:** `eachChangedFieldHasItsOrdinaryCleanValueAndRecordsFollowFormOrder`;
`refusalPrecedenceIsPerAnswerAndRecordsAreGroupedByFaultBeforeFieldOrder`;
`refusalAnswerKeyRowsExposeTheNewProblemWithTheStatisticalKind`;
the new `metadata6` and `truthfulCodeRecords` oracles.

### F7. Which warnings and assignment boundaries are prescribed?

**Question:** What warning wording is required, and where does an inactive
refuse warning belong among other fault warnings?

**Assumption:** Require exactly one configuration-wide warning identifiable
by `refuse` when requested without codes, after original-table warnings
and before failed-outlier warnings. Do not prescribe prose or its position
relative to another fault's warning. Active faults follow canonical order,
whatever the input list's order. With five active faults, a cumulation
warning gives 10 as the required enumerator count. At E=1 only the
no-injection warning applies. Small-sample warnings count interviews,
not replacement records, with 9 versus 10 exercising the boundary.
The four earlier faults and their warnings are unchanged when refuse is
absent or inactive; merely supplying its code list changes neither.

**Tests:** `InjectedAmendment6AssignmentTest`;
`theMissingCodesWarningAppearsAfterTableWarningsAndBeforeOutlierWarnings`;
disabled-fault warning comparisons.

### Amendment 6 build verification

**Question:** Can the requested offline Maven test compilation run here?

**Observation:** The baseline and post-addition Maven invocations fail in
`testCompile` accessing
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
The `-e` retry identifies `java.nio.file.AccessDeniedException`.
This is an environment failure, not a successful requested Maven build.

**Assumption:** Use the existing supplementary in-memory Java compiler
probe, with `--release 17`, to check the seven new Java files and nine
unchanged supporting test sources against compiled public APIs and cached
dependency bytes. This does not replace Maven verification. No repositories
are copied, drives mapped, dependencies installed, or production sources
inspected. Logs and probes remain under `.codex-runs/spec14-amendment6/`.

**Result:** All 16 new/support sources compile with the supplementary
compiler. The diagnostic JUnit run discovers and starts 81 invocations:
2 pass and 79 fail, with no skips, aborts, or failed containers. Failures
are 68 configuration exceptions for unimplemented code-list settings,
8 illegal-argument exceptions for the unsupported refuse fault, and
3 unsupported-operation exceptions from stubs. These expected failures
do not exercise assertions beyond the missing support.
See `diagnostic-compile.log`, `diagnostic-junit.log`, and
`maven-test-compile-detail.log` in the run directory.

## Amendment 7: text codes and explicit exclusions

This addition is limited to Amendment 7. It adds six new test classes,
`InjectedAmendment7TextTest`, `InjectedAmendment7PrecedenceTest`,
`InjectedAmendment7ExclusionsTest`, `InjectedAmendment7InteractionsTest`,
`InjectedAmendment7CascadeTest`, and `InjectedAmendment7RecordsTest`, plus
`InjectedAmendment7Support`. The new static fixture is
`src/test/resources/ctosim/injected-problems/amendment7/other-specify.xml`.
Small parameterized forms use the existing resource-backed form template;
cascade tests reuse the existing read-only CSV fixtures and test helpers.
No production source was opened, searched, or modified. All previously
committed tests and fixtures are retained unchanged.

### G1. What identifies a text code, including a numeric-looking code?

**Question:** Should numeric-looking text codes be compared numerically or
normalized, as numeric question codes are in earlier amendments?

**Assumption:** A text answer uses the exact string supplied in the team's
list. Thus `097` differs from `97`, and leading zeros, decimal spelling,
case, Unicode, commas, and quotes survive. No normalization, conversion to
a number, or fallback to the other team's list or `specialValues` is
assumed. The engine, rather than a text appearance or ordinary drawing
range, decides whether a code is accepted. Configuration parsing errors
are covered by spec 04 tests and are not duplicated here.

**Tests:** `textCodesAreUniformAtOneQuarterRateAndKeepExactStrings`,
`textCodesDoNotUseNumericEquality`,
`textWithNumericAppearanceStillTakesAcceptedNonnumericTeamCodes`,
`eachTextFaultUsesItsOwnListDespiteOtherAvailableCodes`, and
`answerKeyQuotesTextCodesAndTheReplacedTextWithoutLosingUnicode`.

### G2. When and among which candidates is text-code selection made?

**Question:** Does the quarter-probability apply before trying an arbitrary
code (possibly losing the injection), or to replacement by a uniformly
chosen currently accepted code? Do earlier injected answers count when
evaluating a later text constraint?

**Assumption:** When at least one code is accepted, there is a 0.25 chance
of choosing uniformly from the accepted subset. Rejected candidates do
not reduce that rate. Acceptance is evaluated at the current question,
with current prior answers, including prior injected answers. The tests
do not prescribe a search implementation or random sequence. Fixtures
make the ordinary answer valid and distinct from accepted codes. There is
no extra assertion about look-ahead at future answers.

**Tests:** `regexFiltersCodesBeforeUniformSelectionRatherThanReducingTheInjectionRate`,
`acceptanceUsesTheOtherAnswersOfThisInterview`,
`aTextConstraintSeesEarlierInjectedAnswersRatherThanTheCleanInterview`, and
`noAcceptedCodeKeepsTheOrdinaryAnswerWithoutFallingBackToOtherLists`.

### G3. Which unchanged-answer and relevance rules carry over to text?

**Question:** Does the extension override missingness, relevance, or the
rule that records describe actual changes only?

**Assumption:** All inherited rules continue to apply. Blank or irrelevant
text stays blank; a draw of the already present exact code creates no
record. An already present code still belongs to the uniform accepted
candidate set, so one unchanged and one changing candidate give a 0.125
observable replacement rate. A dontKnow attempt with no accepted code, or the same code already
present, leaves that answer available to refuse. The other-specify fixture
offers ordinary/other choices that are not team codes, so relevance is
controlled solely by the actual gate answer. Supplying codes alone does
not request faults, and missing code lists are represented by omitted
configuration keys, as required by spec 04.

**Tests:** Blank, irrelevant, unchanged-code, missing-list, and no-request
tests in `InjectedAmendment7TextTest`; `otherSpecifyReceivesCodesOnlyInInterviewsWhereOtherWasSelected`;
`anAcceptedCodeEqualToTheOrdinaryTextKeepsItsShareOfUniformDraws`;
`aDontKnowDrawThatLeavesTheTextUnchangedDoesNotBlockRefuse`.

### G4. Does Amendment 7 settle combined-fault dontKnow cleanValue?

**Question:** Amendment 7 says that text records contain the answer
replaced, but also says records are as for the other questions. Amendment
6 explicitly leaves dontKnow's cleanValue unspecified when both faults
are active. Is that earlier exception now removed?

**Assumption:** Do not assume that the exception has been removed.
Combined-fault tests require exact cleanValue for refuse only, and verify
the final value, field, metadata, exclusivity and ordering for both faults.
Standalone tests for each fault verify the exact replaced text against
the same actual visit walked without faults, including nonconstant
ordinary text draws. This avoids imposing an unclarified counterfactual
on combined dontKnow records.

**Tests:** `InjectedAmendment7PrecedenceTest`,
`cleanValuesAreTheActualGeneratedTextReplacedAndRecordsFollowFormOrder`,
and the `textRecords` and `metadata` helpers.

### G5. How are protected kinds and protected roles represented?

**Question:** Some exclusions are defined by the control rather than its
data type (range, media, acknowledge), and some questions have no ordinary
answer (notes, rank). Must tests require those controls to produce a value?
Does protecting a calculation mean freezing its dependencies too?

**Assumption:** Protection means no replacement record for that field and
no direct change from its same-visit ordinary behavior. A rank may remain
blank with its ordinary unsupported-control warning; a note may remain
blank. No nonblank output is required for such controls. Media fixtures
use upload controls, with image uploads bearing `signature` and `draw`
appearances. Both integer and decimal ranges are tested. Every type case
also includes an eligible ordinary text companion to demonstrate active
injection. Calculations and read-only fixtures have independent fixed
values, so the tests do not prohibit normal recalculation after an input
changes. Consent tests exercise both consenting and refusing submissions.
Count tests cover numeric counts, an indirect count controlled by text,
and an add-another select. Repeat-record path spelling is not prescribed;
the repeated-answer tests check actual instance counts/values and the
absence of changes to controllers.

**Tests:** `InjectedAmendment7ExclusionsTest`,
`addAnotherAnswersThatControlRepeatCountsRemainProtected`,
`InjectedAmendment7CascadeTest`, and the skipModules, outlier, and resent
tests in `InjectedAmendment7InteractionsTest`.

### G6. What randomness is prescribed and what evidence is sufficient?

**Question:** The spec prescribes probabilities, independence and replay,
but not a PRNG or seed-specific replacement pattern.

**Assumption:** Use fixed seeds without expected snapshots. With two
enumerators and one active fault (or two cumulated faults), exactly one
enumerator is faulty. Even submission counts give half as many potential
faulty interviews. Rate tests use the existing tolerance of seven binomial
standard deviations plus two; the main text/code test has 1,600 eligible
answers, regex selection 1,200, combined precedence 3,000, and independent
fault streams 2,000. Conditional relevance/constraint tests count actual
eligible opportunities. Code bins and mixed-field tests require every
scenario they assert to be observed; no missing scenario causes a skip.
Same-seed checks include submissions, records, visits, warnings and CSV;
different-seed checks require more than one pattern across four seeds.
Locale/time-zone tests restore JVM defaults in finally blocks.

**Tests:** Rate tests in `InjectedAmendment7TextTest` and
`InjectedAmendment7PrecedenceTest`; replay, seed and locale tests in
`InjectedAmendment7RecordsTest`.

### G7. How are earlier conflicting text expectations handled in this run?

**Question:** Amendment 7 says earlier tests expecting unchanged text are
superseded and go through a dispute, but this task explicitly requires all
committed tests to be kept and new files/classes only.

**Assumption:** Preserve all earlier tests without rewriting or disabling
them. In particular, earlier whole-run no-change assertions with text
companions and assertions that records must contain only numeric fields
may need separate adjudication, as well as the explicit
`InjectedAmendment6CodesTest.textAndDatesKeepTheirOrdinaryAnswersDespiteMatchingTeamCodeKinds`.
This run neither edits disputes nor claims the complete old suite should
pass after Amendment 7 is implemented.

**Tests:** All new Amendment 7 classes; no changes to existing test files.

### Amendment 7 build verification

**Question:** Can the requested `mvnw.cmd -o -q test-compile` verify the
new tests in this sandbox?

**Observation:** The initial no-change baseline invocation exited zero.
After new test sources were added, the requested command failed in
`testCompile` while opening
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
The detailed `-e` retry identifies `java.nio.file.AccessDeniedException`.
The earlier zero exit does not demonstrate compilation of the new tests.

**Assumption:** Use the previously established supplementary in-memory
Java compiler against compiled public API classes and cached dependency
bytes, with `--release 17`. No source under production directories is
read, no dependencies are installed or copied, and no build configuration
is changed. This check is supplementary and does not turn the blocked
Maven invocation into a success. Probes and logs are confined to
`.codex-runs/spec14-amendment7/`.

**Result:** All seven new Java files and nine unchanged support sources
compile successfully with the supplementary compiler. The final diagnostic
JUnit run discovers and starts 107 invocations: 22 pass and 85 fail, with
no skipped or aborted tests and no failed containers. The failures are
assertions about absent text replacements (including required positive
controls) and range fields receiving team codes. No fixture-loading or
configuration exception occurs. These are expected implementation gaps;
assertions beyond a failing positive control remain unverified at runtime.
See `diagnostic-compile-final.log`, `diagnostic-junit-final.log`,
`maven-test-compile.log`, and `maven-test-compile-detail.log` under
`.codex-runs/spec14-amendment7/`. The requested Maven compilation remains
blocked; supplementary compilation does not replace it. No commit was made.

### Disputes 7–8 build verification (2026-11-01)

**Question:** Can the required offline Maven test run verify these adjudicated
tests in this sandbox?

**Observation:** `mvnw.cmd -o -q test`, with `MAVEN_USER_HOME` set to
`.tools/maven-home`, fails at `testCompile` on the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. It does not reach
JUnit. See `.codex-runs/spec14-disputes-7-8-maven.log`.

**Assumption:** Retain the existing supplementary compiler and JUnit approach,
using compiled public API classes and cached dependencies, without reading
production sources, copying repositories or changing build configuration.
All temporary probes and logs are under `.codex-runs/spec14-disputes-7-8/`.
The supplementary compiler successfully compiles all 56 spec 14 test/support
sources with `--release 17`; this does not establish a successful Maven run.

**Tests:** The 12 classes containing the tests in Disputes 7–8, including
the assignment and interaction classes affected by shared fixture changes.
Runtime results are recorded at the end of the disputes file.

## Amendment 8 test-author questions and assumptions

### H1. How is a qualifying specify question identified?

**Question:** Must a specify question become newly relevant only for Other,
or is it enough that its relevance refers to the select and is true with
the chosen Other value? What about a question controlled by a future answer?

**Assumption:** Follow the stated conditions literally: a later text
question whose relevance refers to this select and is true with Other
qualifies, even if it is also relevant for real choices. A text without
such a reference, an earlier text, an unrendered instance node, a numeric
question, and a text relevant only for a real choice do not qualify.
When several qualify, instance and control order agree in the fixtures;
the first wins even when bind order and alphabetical order differ. No
expectation is imposed on unresolved future-answer dependencies, inherited
group relevance without a direct reference, or matching across repeat instances.

**Tests:** `InjectedAmendment8EligibilityTest`;
`otherChoiceIsUniformAmongOnlyThoseWithSpecifyQuestions`.

### H2. What exactly is the "unaltered label" used on text rejection?

**Question:** Does retry mean the original displayed label before
normalization, or the normalized label before the optional deletion?

**Assumption:** Leave that distinction open. Fallback fixtures use an
already lowercase, unaccented, whitespace-normalized label, so both
interpretations agree. A rejected deletion must retry that label; if both
attempts fail, ordinary text is kept while the successful select change
still has its record. No particular subsequent code-fault treatment is
asserted for an ordinary text fallback.

**Tests:** `rejectedTypoRetriesTheUnalteredLabel`,
`rejectingBothLabelAttemptsUsesTheUsualTextButKeepsTheSelectChangeRecord`,
`specifyConstraintUsesTheChangedSelectAnswer`.

### H3. Which normalization and typo details are prescribed?

**Question:** How are letters counted and deleted when there are spaces,
digits or punctuation? Are deletion positions uniform? What is the base
letter of a ligature or a non-Latin character?

**Assumption:** Test Latin accented letters with unambiguous bases, ASCII
whitespace, case conversion independent of locale, and the displayed
default-language label (including an embedded output). Four letters is an
inclusive threshold, not four characters. On an eligible label exactly
half the texts lose one letter; the first letter, spaces, punctuation and
digits are retained. Tests intentionally do not require uniform deletion
positions or prescribe ligature transliteration, empty labels, supplementary
Unicode letter counting, or scripts with no obvious base-letter mapping.

**Tests:** `InjectedAmendment8TextTest` and
`labelLowercasingAndRandomnessIgnoreMachineLocaleAndTimeZone`.

### H4. How are real choices and unchanged earlier attempts treated?

**Question:** Does membership in specialValues alone disqualify a real
choice? Does a failed earlier code-fault attempt reserve the answer?

**Assumption:** Real-choice exclusion uses exactly the three named lists,
with the exact string rules of spec 04 Amendment 13. specialValues alone
does not exclude a choice. A select_multiple retains every unchosen real
choice and every selected don't-know/refusal choice; just one real choice
is replaced, and the new answer follows the offered choices' order.
Earlier attempts with no usable code leave the answer eligible. An
already-present don't-know/refusal code is not itself a real choice, so
no test requires Other to replace that code. Fixed ordinary-answer
overrides provide clean opportunities, as confirmed in Amendment 1.

**Tests:** `InjectedAmendment8ChoiceTest` and
`anEarlierAttemptWithoutAnOfferedCodeDoesNotBlockOther`.

The select rejection rule is a draw followed by acceptance, with the clean
answer retained on rejection. It does not specify retrying a different
Other choice or filtering by the select's constraint before the draw.
`aRejectedDrawDoesNotRetryAnotherOtherChoiceOrFilterItOutBeforeDrawing`
uses two qualifying Other choices, one rejected, to check the resulting
one-eighth observable replacement probability.

### H5. Which protections and assignments carry over?

**Question:** Is Other disabled only by an empty code list, or also by a
form without any eligible pair? Does it inherit read-only/calculation
protection and indirect repeat-controller protection?

**Assumption:** Only an empty list disables and removes the fault before
assignment, as with don't-know/refusal faults. A nonempty unusable list
still occupies its fault slot. The inherited protections include read-only
and calculated answers, consent, survey-id cascades, direct and indirect
repeat counts and add-another controllers, copied reinterview answers,
resent submissions, and answers already changed by skipModules. Outliers
target integer/decimal questions whereas Other targets selects, so there
is no artificial same-field outlier/select fixture. The six faults use
canonical order irrespective of configured list order, require twelve
enumerators to avoid cumulation, and leave at least half clean.

**Tests:** `InjectedAmendment8AssignmentTest`,
`InjectedAmendment8ExclusionsTest`, `InjectedAmendment8CascadeTest`, and the
skipModules/resent tests in `InjectedAmendment8InteractionsTest`.

### H6. What randomness and record counterfactual are tested?

**Question:** Is any seed-specific PRNG sequence prescribed? Does adding
Other change earlier faults' draws? What is a sufficient statistical sample?

**Assumption:** No PRNG sequence is prescribed. Use fixed seeds, per-answer
probabilities, independent earlier-fault streams, and seven binomial
standard deviations plus two as tolerance. Main rates use 1,600–3,000
faulty-interview opportunities; uniform real-choice selection uses 2,400,
and specify typo tests several hundred actual replacements. Positive
controls are mandatory, never skipped when missing. The simple one-fault
case compares cleanValue to the same actual visit walked without faults;
combined tests use fixed clean selects and verify Other's exact replaced
answer. They do not constrain the earlier unresolved combined-fault
dontKnow cleanValue. Replays include instances, visits, warnings, records
and CSV, and restore JVM defaults after locale testing.

**Tests:** Statistical checks throughout the new classes,
`InjectedAmendment8InteractionsTest`, and `InjectedAmendment8RecordsTest`.

### H7. How are existing five-fault tests handled?

**Question:** Amendment 8 supersedes earlier five-fault default assertions,
but this task requires all committed tests to remain intact.

**Assumption:** Preserve every existing test and support file. Add only
new Amendment 8 classes and a new resource fixture. Do not adjudicate old
defaults, modify disputes, or claim the entire old suite should pass.
Configuration parse-error cases belong to spec 04; these tests use its
documented JSON API so they compile before new configuration accessors are
implemented. No production source was read or changed.

**Tests:** All `InjectedAmendment8*` classes.

### Amendment 8 build verification (2026-11-01)

**Question:** Can `mvnw.cmd -o -q test-compile` verify these additions in
this sandbox?

**Observation:** Both the initial invocation and the invocation after
adding tests fail in `testCompile` when opening the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. A detailed offline
retry identifies `java.nio.file.AccessDeniedException`. Maven does not
establish successful compilation of the new tests. `MAVEN_USER_HOME` was
set to `.tools/maven-home`; dependencies, repositories and build
configuration were not changed.

**Assumption:** Reuse the earlier supplementary in-memory Java compiler
approach, against compiled public API classes and cached dependency bytes,
without reading production source or copying dependency repositories.
This verifies Java compilation independently but does not replace or
claim success for the requested Maven command. All probes and logs are
under `.codex-runs/spec14-amendment8/`.

**Result:** The final supplementary compilation succeeds with `--release
17` for all nine new Java files and eleven unchanged test-support sources.
The diagnostic JUnit run discovers and starts 85 invocations across eight
test classes, with no skipped/aborted tests or failed containers. All 85
currently fail before reaching their behavioral assertions: 80 because
`otherCodes` is not a recognized configuration key, five because `other`
is not a recognized fault. No fixture-loading failure occurs. The actual
fault behavior and downstream assertions cannot yet be verified at runtime.
See `compile-final.log`, `junit-final.log`, `maven-test-compile.log`, and
`maven-test-compile-detail.log` in that directory.

Existing tests/supports are preserved. The only tracked-file edit is this
questions-file addition; the tests and the displayed-label fixture are new
files. No production code, spec text, build configuration or disputes file
was modified, and no commit was made.

### Disputes 9–10 build verification (2026-11-07)

**Question:** Can the required offline Maven run verify the adjudicated
Amendment 8 exclusions tests in this sandbox?

**Observation:** `mvnw.cmd -o -q test` fails in `testCompile`, reporting
the cached `.tools/m2/com/fasterxml/jackson/core/jackson-databind/2.13.2.1/jackson-databind-2.13.2.1.jar`,
before JUnit runs. See `.codex-runs/spec14-disputes-9-10-maven.log`.

**Assumption:** Supplementary compilation against existing compiled API
classes and cached dependency bytes can verify the changed tests without
reading production source or modifying dependencies. It does not establish
a successful full Maven suite.

**Tests and result:** The in-memory compiler compiled
`InjectedAmendment8ExclusionsTest` and its seven supporting test sources
with `--release 17`. A separate JUnit run passed all nine invocations,
including the decimal and direct-select repeat-count cases, with no skips,
aborts or failures. Logs are `.codex-runs/spec14-disputes-9-10-compile.log`
and `.codex-runs/spec14-disputes-9-10-junit.log`. Temporary compiler and
runner artifacts are confined to `.codex-runs/`.

## Amendment 9 test-author questions and assumptions

### I1. Which version source can Fieldwork observe?

**Question:** Amendment 9 calls the new version the form's catalog version,
but the public Fieldwork constructor takes a LoadedForm and configuration,
not a FieldCatalog. What if those versions disagree?

**Assumption:** Fixtures put the new version on the primary instance root,
as specified by spec 01's LoadedForm.version(). The export fixture's catalog
has the same version. No expectation is imposed on disagreement between a
separately supplied catalog and the loaded form, or on direct FormWalker
calls outside Fieldwork.walkAll().

**Tests:** All `InjectedAmendment9*` classes, especially
`simulatedVersionsReachTheWideAndLongExportsPerSubmission`.

### I2. What are the inference and equality rules for version strings?

**Question:** Is there an integer-width limit, should leading zeros be
preserved, and does “digits” include non-ASCII digit characters? Is equality
numeric or textual?

**Assumption:** No size limit is stated, so canonical ASCII positive
integers are decremented exactly, including a value above Long.MAX_VALUE.
Version 1 has usable previous version 0. Zero, negative, signed, decimal,
exponent, whitespace-surrounded and alphabetic versions do not qualify for
automatic inference. An explicit previousVersion overrides inference and
is kept exactly, including Unicode, commas, quotes and XML-sensitive text.
Equal-version tests use identical strings; they do not choose between
numeric and textual equality for differently spelled numbers. Leading-zero
formatting, Unicode digits, and a present-but-empty form version are left
open. A missing form version disables the update even when an explicit
previousVersion is supplied, following the parenthetical in “Versions”.

**Tests:** The inference, explicit-version, invalid-version and absent-version
tests in `InjectedAmendment9VersionsTest` and `InjectedAmendment9FaultTest`.

### I3. Which date determines the version?

**Question:** Does the update apply by local interview date, upload date,
nominal visit number, or the date before schedule problems are applied?

**Assumption:** Compare the actual Submission.start().toLocalDate() with
the configured date, inclusively for the new version, in the configured
local clock. Crossing midnight before ending or uploading does not change
the version. Schedule changes are reflected through the resulting start.
Resent copies use their original's version and keep only their resent
record, including when the original carries outdatedVersion. Reinterviews
copy the specified id/cascade answers, not the version: their own start
and enumerator determine their version. The reinterview date-boundary test
chooses the update date from a preliminary public run, avoiding any assumed
PRNG sequence for originals.

**Tests:** Date-boundary, overnight, configured-zone and schedule tests in
`InjectedAmendment9VersionsTest`; `InjectedAmendment9InteractionsTest`.

### I4. Are impossible-update and inactive-fault warnings coalesced?

**Question:** “Versions” asks for one warning when no update can be
simulated, and “The fault” also asks for one when outdatedVersion is
inactive. Should requesting that fault duplicate the same explanation?

**Assumption:** One configuration-wide explanation satisfies both clauses,
rather than two warnings for the same inactive update. When the fault is
requested, its warning contains `outdatedVersion`, following earlier
fault-warning conventions. Impossible updates warn even without problems;
an absent formUpdate without a requested version fault does not warn.
No exact prose, language, or reason substring is prescribed. Separate
travel, outlier, cumulation and small-sample warnings retain their own
places and counts. A valid update after all interviews does not disable
the assigned fault or produce an inactive-update warning.

**Tests:** Disabled/invalid-update and warning-order tests in
`InjectedAmendment9FaultTest`, invalid-version tests in
`InjectedAmendment9VersionsTest`, and
`aFutureValidUpdateProducesNoOutdatedRecordsAndDoesNotDisableTheFaultSlot`.

### I5. What does “nothing else changes” cover, and which protections apply?

**Question:** Does a version fault alter the plan or answers, and do answer
protections prevent a version fault on a reinterview or a refused interview?

**Assumption:** All submission properties except the root's version remain
equal to a run with the same other problems and without outdatedVersion:
answers, metadata, identity, answered count, start/end/upload times and
submission warnings. The visit list also stays equal. XML is compared as
DOM trees after removing only the version attribute; attribute serialization
order is not constrained. The version is not a question answer, so answer
protections do not exempt reinterviews or fake refusals. Resent copies have
their explicitly specified exception. The cleanValue is always the current
version, including under cumulated faults. Adding the fault is compared
against six faults with E=6, where the same m=3 enumerators carry the earlier
faults, so the comparison does not assume unchanged assignment when m changes.

**Tests:** `versionFaultLeavesAnswersClocksMetadataLocationsAndWarningsUnchanged`,
`addingTheSeventhFaultKeepsEarlierFaultResultsWhenTheSameEnumeratorsCarryThem`,
`InjectedAmendment9InteractionsTest`, and the seven-fault assignment tests.

### I6. What assignment and randomness evidence is sufficient?

**Question:** Is there a prescribed seed-specific assignment? How should a
fault with no post-update interview affect assignment and sample warnings?

**Assumption:** Use the inherited canonical order, m=min(7,floor(E/2)),
round-robin cumulation, and fourteen enumerators to avoid cumulation.
Absent or impossible updates remove the fault before assignment; a valid
future update keeps its slot. Small-sample warnings count all interviews,
not just outdated records, with nine/ten as the boundary. A single
enumerator gets the inherited comparison warning, with normal version
updates still applied. No PRNG or particular faulty username is assumed.
The uniformity test uses 320 fixed seeds, eight enumerators, and seven
binomial standard deviations plus two as tolerance; all eight bins must
be observed. Other tests use a fixed seed, require positive observations,
and check repeated/fresh runs and locale/time-zone independence. JVM
defaults are restored in finally blocks.

**Tests:** `InjectedAmendment9AssignmentTest`, the future-update test, and
replay/locale tests in `InjectedAmendment9RecordsTest`.

### I7. How are existing tests and configuration validation treated?

**Question:** Amendment 9 supersedes six-fault defaults, but this task says
to preserve all existing tests and add tests for Amendment 9 only.

**Assumption:** Keep all existing test/support files unchanged and add five
test classes, one support class and two resource fixtures. Do not adjudicate
old defaults or edit the disputes file. Configuration syntax/errors and
default-list accessor checks belong to spec 04 Amendment 14; the new tests
use its documented JSON and the existing public ProblemRates constructors.
No production source or implementation was read or modified. The ordinary
no-update test is the backward-compatibility behavior explicitly required
by this amendment. An empty submission count is not used because the
configuration requires at least one submission.

**Tests:** All `InjectedAmendment9*` classes. Existing six-fault tests remain
available for a separate dispute; this run does not claim the full existing
suite should pass.

### Amendment 9 build verification (2026-11-07)

**Question:** Can `mvnw.cmd -o -q test-compile` verify these additions in
this sandbox?

**Observation:** The baseline, post-edit and final requested invocations
fail in testCompile while opening the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. An offline `-e`
retry reports `java.nio.file.AccessDeniedException`. MAVEN_USER_HOME was
set to `.tools/maven-home`. Maven cannot establish successful compilation
of the tests here; no repository copies, dependency installation, build
configuration edits or drive mappings were used.

**Assumption:** Reuse the supplementary in-memory Java compiler from prior
test-author runs, against compiled public API classes and cached dependency
bytes, without reading production sources. This establishes supplementary
Java compilation only and does not turn the requested Maven command into a
success. Temporary probes and logs are confined to
`.codex-runs/spec14-amendment9/`.

**Result:** Supplementary compilation succeeds with `--release 17` for all
six new Java files and thirteen unchanged support sources. A separate
fixture probe successfully loads the simple version variants, the
seven-fault form and the overnight form. The final diagnostic JUnit run
discovers and starts 74 invocations across five test classes: one passes,
73 fail, none are skipped or aborted, and no container fails. Seventy
failures are the unimplemented `formUpdate` setting; three are the
unrecognized `outdatedVersion` fault. Downstream behavioral assertions
cannot yet be verified at runtime. Logs: `compile-final.log`,
`junit-final.log`, `maven-final.log`, and `maven-detail.log` in that directory.

All previously committed tests are preserved. The questions file is the
only modified tracked file; test classes/support and fixtures are new.
No production code, spec text, pom.xml or dispute file was changed, and no
commit was made.

### Dispute 11 build verification (2026-11-07)

**Question:** Can the required offline Maven test run verify the corrected
Amendment 9 schedule fixtures in this sandbox?

**Observation:** `mvnw.cmd -o -q test` failed at `testCompile`, reporting
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, before JUnit ran.
`MAVEN_USER_HOME` was set to `.tools/maven-home`.

**Assumption:** This build limitation does not authorize dependency or build
configuration changes. Use the existing supplementary in-memory compiler
against compiled API classes and cached dependency bytes without reading
production sources; report its result separately from Maven.

**Dependent tests and result:** All six `InjectedAmendment9*` Java sources
and thirteen supporting test sources compile with `--release 17`. The
separate JUnit run passes all 74 invocations across the five Amendment 9
test classes, including all seven invocations of the four disputed methods,
with no failures, skips or aborts. The full Maven suite remains unverified.
Logs: `.codex-runs/spec14-dispute11-maven.log` and
`.codex-runs/spec14-dispute11/{compile.log,junit.log}`.
