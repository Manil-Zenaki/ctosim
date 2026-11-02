# Spec 14: disputed tests

## Dispute 1: cascade duplicates on a form without instanceID (2026-10-23)

**Tests:** `InjectedProblemsCascadeTest.reinterviewCopiesTheIdAndEveryUpperQuestionFromItsOriginal`
and `InjectedProblemsCascadeTest.resentCopiesACascadeSubmissionWithoutConsumingAnotherHouseholdId`.

**Spec:** "Public API": `key` is "that submission's KEY (its instanceID)" and
`relatedKey` the "KEY of the other submission involved". "`problems()`":
"`key`, `enumerator`, `value` and `relatedKey` are those of the
submissions". A KEY is the instance's `meta/instanceID` (spec 08); a
form without that element has no KEY at all (spec 08 refuses to export
such a submission).

**What the tests do:** both build their form with spec 13's
`FieldworkAmendment3Support.deep(...)`, whose template
(`src/test/resources/ctosim/fieldwork-plan/form-template.xml`) has no
`meta/instanceID`. Every submission therefore has the same empty KEY.
The tests then find each record's original by
`key(s).equals(p.relatedKey())`, which matches the first submission of
the run whatever the original, and require
`originals.add(p.relatedKey())` to succeed for two records, which cannot
happen when both related KEYs are empty. The failures observed are
exactly these: the "original" found is submission 1 (`expected: <R2>
but was: <R1>` on `region`), and the second `originals.add("")` is
false.

**Why the tests are wrong:** the spec identifies submissions in records
by their KEY only; it cannot identify a submission that has none. The
behaviour under test (the id and every upper answer copied from the
original; a resent copy equal to its original) is observable only on a
form that has an `instanceID`, as every SurveyCTO form does.

**Requested change:** give the cascade fixture a `meta/instanceID` with
a `jr:preload="uid"` bind (as `src/test/resources/ctosim/injected-problems/form-template.xml`
does), or find the original by another means than an empty KEY, and
keep the assertions on the copied answers.

## Adjudication (2026-10-24)

### Dispute 1: upheld; fix the cascade fixture in both tests

Spec 14 defines `key` as the submission's instanceID and `relatedKey` as
the original's KEY. The tests read `meta/instanceID` to resolve that
relationship, but `deep(...)` uses a template with no such element or
UID preload. Looking up an original by an empty KEY cannot distinguish
the returned submissions. The fixture therefore cannot support these
assertions; this is a test defect, not evidence of incorrect copying.

`InjectedProblemsCascadeTest` now overrides its fixture builder to add
`meta/instanceID` and its `jr:preload="uid"` bind. This change is local to
the two disputed tests and leaves the shared spec 13 fixtures unchanged.
Both tests explicitly check that returned submission KEYs are nonempty
and distinct before using KEYs to resolve originals.

- `reinterviewCopiesTheIdAndEveryUpperQuestionFromItsOriginal`: retain
  the copied region/district/village/id assertions and the visit ID
  assertion. The reinterview section and clarification (11) explicitly
  require them. Retain the earlier-original, unique-original and distinct
  target-KEY assertions: originals must be earlier and cannot be reused,
  and the target remains its own submission.
- `resentCopiesACascadeSubmissionWithoutConsumingAnotherHouseholdId`:
  retain the copied cascade answers, original-visit equality and distinct
  ordinary household IDs. The resent section requires a copy of the
  original and its visit; "Everything else" preserves distinct survey
  IDs for the ordinary interviews. Fixing the missing metadata makes the
  original lookup meaningful without weakening these checks.

No production source was inspected or changed.

**Verification:** `mvnw.cmd -o -q test` failed at `testCompile`, reporting
the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` path;
the full Maven suite could not run. The existing diagnostic compiler
successfully compiled all 13 spec 14 test/support sources against the
compiled public API classes. A diagnostic JUnit run of
`InjectedProblemsCascadeTest` then passed both disputed tests (2 started,
2 successful, no failures or skips). Logs are
`.codex-runs/spec14-dispute-maven.log`,
`.codex-runs/spec14-dispute-compile.log`, and
`.codex-runs/spec14-dispute-junit.log`.

## Dispute 2: Amendment 1 tests superseded by Amendment 2 (2026-10-24)

**Tests:** `InjectedAmendment1SkipModulesTest.rostersAreShortenedHalfTheTimeUniformlyBelowTheOrdinaryCount`
(both cases), `InjectedAmendment1SkipModulesTest.countLowerBoundUsesTheSmallestAllowedWholeNumber`,
`InjectedAmendment1OutlierTest.twentyRejectedOutlierDrawsFallBackToTheUsualAnswerAndNameTheQuestion`.

**Spec:** Amendment 2, "Roster shortening is less frequent": the
probability is 0.25 instead of 0.5 for a count question and for a repeat
the enumerator controls. "Outliers within the real constraint": a
question is eligible only when the engine accepts a value of its
interval, and "The warning about 20 refused draws of Amendment 1 is no
longer given". Clarification (Amendment 2), B1: these earlier tests are
superseded and go through a dispute.

**What the tests do:** the first three expect shortening with probability
0.5 (`rate(ps.size(), 400, .5)`); the last expects the twenty-refusal
warning naming the question, for a question whose constraint refuses
every outlier value, which is now not eligible.

**Requested change:** expect the probability 0.25 in the roster tests,
keeping their other assertions (values below the ordinary count, the
lower bound, uniformity); for the outlier test, expect what Amendment 2
gives when no question accepts an outlier value: the question keeps its
usual answer, no outlier record, and the final warning naming the
submission where the outlier was drawn (or the outlier moved to a later
submission that accepts one).

## Dispute 3: an "add another" roster whose count the engine never recomputes (2026-10-24)

**Tests:** in `InjectedAmendment2RosterTest`:
`oneOrSeveralCalculationsConnectAnAddAnotherQuestionToItsCount` (both
cases), `replacementChoicesMinimizeCountAreUniformAndNeverUseSpecialValues`,
`replacementChoosesTheFewestInstancesRatherThanJustAnySmallerCount`,
`sameSeedReplaysRosterDecisionsAndNewSeedsCanChangeThem`,
`successiveAddAnotherDecisionsStopTheRosterAndReportItsFinalCounterfactualCount`,
`twoShortenedAddAnotherRostersGetSeparateRecordsInFormOrder`.

**Spec:** Amendment 2, "Rosters built by 'add another' questions": a
question extends its repeat when "at the moment the walker answers it,
two of the choices it offers give different numbers of instances". The
engine decides those numbers.

**What the tests do:** `InjectedAmendment2Support.extending(...)` builds
the count as a calculation on positional references,
`if(/data/person[1]/more = 'no', 1, if(/data/person[2]/more = 'no', 2, ...))`
or `if(string(/data/person[1]/more) = '' or /data/person[1]/more = 'yes', 2, 1)`.
The engine (JavaRosa) does not recompute such a calculation when an
answer of the repeat changes: walking that form without any fault and
with `more` fixed to `no` by an override still gives `total` 4 and four
`person` instances, each answered `no`; setting `more` to `no` or `yes`
leaves `total` at 4. So no offered choice gives a different number of
instances, the question extends nothing, and no fault can shorten the
roster, ordinary walks included. The failures are the consequence: zero
replacements where the tests expect a quarter of the opportunities.

**Why the tests are wrong:** the fixture's roster cannot stop in the
engine at all, so it does not exercise the rule. A roster that the engine
does stop (the usual SurveyCTO pattern, as in the IPA household form:
the count is the sum over the repeat of a calculation of each "add
another" answer) behaves as the spec says: in a check with
`total = sum(/data/person/flag) + 1` and
`flag = if(../more = 'yes', 1, 0)`, the faulty enumerator's rosters were
markedly shorter (2.3 persons on average against 4.0) and every record
gave the final and counterfactual counts.

**Requested change:** build the count from non-positional references,
for example `sum(/data/person/flag) + 1` with a per-instance calculation
`flag = if(../more = 'yes', 1, 0)` (bounded, if needed, by
`min(..., 4)`), and keep the assertions on the frequency, the choice of
the fewest instances, the records and the counterfactual counts.

## Dispute 4: movement tests whose survey ids repeat (2026-10-24)

**Tests:** in `InjectedAmendment2MovementTest`:
`aFailedTargetMovesToTheNextSubmissionThatCanAcceptAnOutlier` (both
cases), `movementContinuesPastSeveralIneligibleVisitsUntilTheFirstSuccess`
(both cases), `theLastSubmissionIsAnEligibleDestination`,
`exhaustedMovementWarnsOnlyOnceAndNamesTheOriginalTarget`,
`twoMovedOutliersNeverClaimTheSameDestinationOrAnOriginalOutlierTarget`.

**Spec:** spec 13, "Survey ids": when the id question has fewer possible
values `K` than submissions `N`, "some values are used twice, and
`warnings()` holds one warning that names the field and gives `K` and
`N`". The integer id's possible values are the whole numbers of
`config.integerRange()`.

**What the tests do:** `config2` uses `InjectedAmendment1Support.settings`,
whose `integerRange` is 10 to 20 (11 values), with 12 submissions and
`"id":"id"`. Every run therefore holds the spec 13 warning `the survey
id 'id' has only 11 possible values for 12 submissions` and two
submissions share an id. The tests then require `warnings()` to be empty
(or to hold exactly one warning), and they select the eligible visits by
their id (`/data/id = '<id>'`), which also makes the submission that
shares that id eligible. A trace of `theLastSubmissionIsAnEligibleDestination`
shows the outlier correctly moved to submission 12 and the only warning
being the spec 13 one.

**Requested change:** give the id question as many possible values as
submissions (for example an `integerRange` of 10 to 21, or an `id`
override with a range covering 12 values), or compare the warnings
without the spec 13 id warning, keeping the assertions on movement.

## Adjudication (2026-10-25)

### Dispute 1: retain the previously corrected cascade tests

The earlier decision still follows the spec: `relatedKey` identifies the
original by its instanceID, so the fixture must supply distinguishable KEYs.
Both cascade tests already add the UID-preloaded metadata and assert nonempty,
distinct KEYs. Keep that fix and all assertions on copied cascade answers,
original selection, visit IDs and resent visits. No further change is needed.

### Dispute 2: upheld; apply Amendment 2 to the older expectations

- Rename `rostersAreShortenedHalfTheTimeUniformlyBelowTheOrdinaryCount` to
  `rostersAreShortenedOneQuarterOfTheTimeUniformlyBelowTheOrdinaryCount`, and
  expect 0.25 in both its counted and enumerator-controlled cases. The actual
  fixture has 600 faulty-enumerator opportunities, which remains its denominator.
  Amendment 2 explicitly replaces 0.5 for both kinds of roster. Keep the uniform
  choice among smaller counts, actual repeat counts, clean values and unchanged
  ordinary-roster assertions.
- In `countLowerBoundUsesTheSmallestAllowedWholeNumber`, likewise change only
  the shortening probability to 0.25. Keep the permitted counts 3 and 4 and
  their conditional 0.5 selection probability: the amendment changes how often
  shortening happens, not how a shorter count is selected.
- Replace `twentyRejectedOutlierDrawsFallBackToTheUsualAnswerAndNameTheQuestion`
  with `anEngineRejectedOutlierIntervalKeepsUsualAnswersAndWarnsForEachOriginalTarget`.
  Its constraint rejects every value of the upper interval [130,160]. Amendment
  2 makes that question ineligible; clarification B7 does not reopen the lower
  interval merely because the engine rejects the upper one. All six submissions
  are reserved outlier targets, leaving no movement destination. Require no
  outlier records, unchanged ordinary submissions and exactly six warnings
  naming the original targets, with no twenty-refusal warning. Remove the
  obsolete requirement that the warning name the question.

Filter-module replacement tests retain their 0.5 probability, which Amendment
2 expressly leaves unchanged.

### Dispute 3: upheld as a fixture defect; retain the behavioral assertions

Amendment 2 requires offered choices to change the repeat count at answer
time. A positional expression that does not recompute in the engine cannot
establish that precondition. Replace those expressions with per-instance
`flag` calculations and a bounded `sum(.../flag) + 1`, including the optional
alias calculation. Add ordinary yes/no walks that check extension and stopping.

- `oneOrSeveralCalculationsConnectAnAddAnotherQuestionToItsCount` (both cases):
  retain the 0.25 rate, one record per roster and final/clean counts 1/2. The
  count now depends on repeat-local flags, optionally through the alias.
- `replacementChoicesMinimizeCountAreUniformAndNeverUseSpecialValues`: retain
  uniform selection of `no` and `later`, exclusion of `-99`, and count 1. All
  stopping choices contribute zero; `yes` contributes one.
- `replacementChoosesTheFewestInstancesRatherThanJustAnySmallerCount`: use
  contributions 2, 1 and 0 for `yes`, `middle` and `no`, capped at three total
  instances. At the first answer these give counts 3, 2 and 1. Keep the
  requirement to choose `no`, not merely any smaller count, and clean count 3.
- `sameSeedReplaysRosterDecisionsAndNewSeedsCanChangeThem`: retain the replay
  and seed-variation checks on the repaired shared fixture. Randomness remains
  governed by the spec, with no prescribed random sequence.
- `successiveAddAnotherDecisionsStopTheRosterAndReportItsFinalCounterfactualCount`:
  cap the flag-sum count at four. Retain the per-opportunity 0.25 stopping
  probabilities, one record per shortened roster and counterfactual count 4;
  clarification B6 explicitly confirms those expectations.
- `twoShortenedAddAnotherRostersGetSeparateRecordsInFormOrder`: give each
  roster its own flag sum capped at two. Keep separate records in form order,
  their counts 1/2, and the requirement to exercise two shortened rosters in
  one submission.

The shared fixture repair also applies to the existing no-change cases;
their assertions remain intact. No unsupported requirement is imposed on
direct cyclic-count bootstrap (clarification B5).

### Dispute 4: upheld; provide a unique ID for every movement-test visit

Spec 13's "Survey ids", incorporated by spec 14, requires the capacity warning
and reuse when K < N. The old 10..20 range has only 11 integers, so demanding
no such warning for 12 submissions was wrong. Repeated IDs also invalidate
the tests' mapping from eligibility expressions to individual visits; merely
filtering the warning would not repair that mapping.

`config2` now supplies an ID-only min/max override with exactly N integers,
10 through N+9. The amount distribution stays U[10,20], preserving its outlier
interval [25,35]. The eligibility helper asserts that control IDs are distinct.
Keep all movement and warning assertions in:

- `aFailedTargetMovesToTheNextSubmissionThatCanAcceptAnOutlier` (both cases);
- `movementContinuesPastSeveralIneligibleVisitsUntilTheFirstSuccess` (both cases);
- `theLastSubmissionIsAnEligibleDestination`;
- `exhaustedMovementWarnsOnlyOnceAndNamesTheOriginalTarget`;
- `twoMovedOutliersNeverClaimTheSameDestinationOrAnOriginalOutlierTarget`.

The remaining movement tests use the same repaired configuration helper and
retain their assertions. The changes fix prerequisites without weakening
destination ordering, reservation, clean-value or warning requirements.

No production source was read or changed, and no commit was made.

**Verification:** `mvnw.cmd -o -q test` reached `testCompile` but failed on
the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`; the full
Maven suite therefore did not run. The existing diagnostic compiler, adapted
under `.codex-runs/spec14-adjudication/`, successfully compiled all 26 spec 14
test/support sources against compiled public API classes and cached dependencies.
The separate JUnit run of `InjectedProblemsCascadeTest`,
`InjectedAmendment1SkipModulesTest`, `InjectedAmendment1OutlierTest`,
`InjectedAmendment2RosterTest` and `InjectedAmendment2MovementTest` discovered
and passed all 74 tests, with no failures, skips or aborts. Logs:
`.codex-runs/spec14-adjudication-maven.log`,
`.codex-runs/spec14-adjudication/compile.log` and
`.codex-runs/spec14-adjudication/junit.log`.

## Dispute 5: "don't know" tests that predate the team's codes (2026-11-01)

**Tests:** in `InjectedAmendment1DontKnowTest`:
`fractionalSpecialCodesAreAcceptedForDecimalQuestions`,
`multipleReplacementsAreOrderedByFormFieldsAndKeepIndependentOrdinaryDraws`,
`numericCodesAreUniformAmongOnlyNumericEngineAcceptedSpecialValues`
(2 cases), `selectAnswersAreReplacedAtOneQuarterRateWithOneOfferedSpecialCode`
(2 cases), `unicodeOfferedSpecialCodesArePreservedExactly`; in
`InjectedAmendment1CascadeTest`:
`dontKnowUsesOnlyCurrentlyOfferedSearchChoicesNotFilteredRowsOrPlaceholders`;
in `InjectedAmendment1InteractionsTest`:
`dontKnowAndAnOutlierCanAffectDifferentAnswersOnTheSameSubmission`,
`faultsAndOriginalDurationTargetsCoexistAndFaultRecordsStayAfterDurationRecords`,
`surveyIdNumbersAreProtectedFromDontKnowWhileOtherNumbersAreChanged`; in
`InjectedAmendment1AssignmentTest`:
`aSubsetOfFaultsUsesOnlyItsOwnMembersAndItsOwnCumulationThreshold`
(2 cases), `faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder`
(6 cases); in `InjectedAmendment2RefusalTest`:
`fourthFaultFollowsCanonicalAssignmentAndWrapsAfterTheCleanHalf` (4 cases).

**Spec:** Amendment 5: `dontKnow` draws its codes from
`config.dontKnowCodes()` instead of `specialValues`; when that list is
empty, no `dontKnow` is injected and the fault takes no enumerator.
Clarification (Amendment 5), E1: "earlier tests that relied on
`specialValues` for `dontKnow` are superseded and go through a dispute".
Amendment 6 adds the fifth fault `refuse`, which, like `dontKnow`, takes
no enumerator without `refuseCodes`.

**What the tests do:** they give the codes through `specialValues` and no
`dontKnowCodes`. The replacement tests then observe no replacement
(`observed replacements / eligible opportunities ==> expected: <75.0> but
was: <0.0>`). The assignment tests list `dontKnow` among the requested
faults and expect it to take an enumerator (`one assigned enumerator:
dontKnow ==> expected: <1> but was: <0>`), whereas without codes it is
removed before assignment, which shifts the faults that follow.

**Requested change:** supply the same codes as `dontKnowCodes` (keeping
`specialValues` where a test checks it is not used, or dropping it), so
that `dontKnow` is active, and keep every assertion on rates, codes,
eligibility, ordering and assignment. Where an assignment test requests
the default fault list, also supply `refuseCodes` (disjoint from
`dontKnowCodes`) or name the faults explicitly, and expect the five
faults of Amendment 6 where the default list matters.

## Dispute 6: team-code tests whose survey ids repeat (2026-11-01)

**Tests:** `InjectedAmendment5RecordsTest.teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone`,
`InjectedAmendment6RecordsTest.teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone`.

**Spec:** spec 13, "Survey ids": when the id question has fewer possible
values `K` than submissions `N`, some values are used twice, and a warning
gives `K` and `N`. This is the situation of Dispute 4, upheld.

**What the tests do:** `"id":"id"` on an integer question with the
`integerRange` of the support settings, 10 to 20 (11 values), and 240
submissions; they then require every submission's id to be distinct
("survey IDs stay unique"). The same walk without any fault already
repeats 229 of the 240 ids (checked with the public API outside the
test tree), and each submission's id equals its id in the walk without
the fault, so the fault leaves the id alone as required.

**Requested change:** give the id question at least as many possible
values as submissions (a wider `integerRange` or an `id` override with
enough values), keeping every assertion; or drop the distinctness check
and keep the comparison of each id with its clean walk.

## Adjudication (2026-11-01)

### Disputes 1–4: retain the previously corrected tests

The earlier decisions remain consistent with the current spec. Dispute 1's
cascade fixtures now supply distinct instanceIDs for resolving originals.
Dispute 2's roster tests use Amendment 2's 0.25 probability, and its rejected
outlier test checks the final per-target warnings rather than the superseded
twenty-refusal warning. Dispute 3's add-another fixtures use repeat-local
calculations and flag sums, with ordinary walks checking that the roster can
extend and stop. Dispute 4's ID-only override supplies one possible ID per
visit without changing the outlier distribution. Keep these fixes and their
behavioral assertions; no further changes are needed for those disputes.

### Dispute 5: upheld; enable the fault with the team's codes

Amendment 5 expressly replaces `specialValues` with `dontKnowCodes` as the
source of injected don't-know answers. Its clarification E1 supersedes the
earlier tests, and E2 removes a fault with no codes before assignment.
Amendment 6 similarly disables `refuse` without its own codes. Therefore,
expecting replacements or an assignment for `dontKnow` without its codes is
a test defect. The replacement, protection, ordering and assignment rules
themselves remain in force.

- `InjectedAmendment1DontKnowTest`: supply the former code lists through
  `dontKnowCodes` in the select, numeric, fractional, Unicode and multiple-
  replacement tests. Keep the 0.25 rate, uniform usable-code selection,
  engine acceptance, exact Unicode values, clean values, independent ordinary
  draws and form-order assertions. Rename methods referring to special codes
  to refer to team codes. Apply the same fixture repair to the no-change
  tests for blank answers, already-equal codes, unavailable/rejected codes,
  unsupported question types, repeat counts, read-only fields and calculations;
  those tests must exercise an active fault, not pass because it is disabled.
  The empty-list case now omits `dontKnowCodes` and verifies that its accessor
  is empty: spec 04 Amendment 11, referenced by spec 14, rejects an explicitly
  empty JSON array and defines omission as the empty accessor list.
- `dontKnowUsesOnlyCurrentlyOfferedSearchChoicesNotFilteredRowsOrPlaceholders`:
  supply `A`, `C` and `code` as `dontKnowCodes`; retain the rate and the
  requirement that only currently offered `A` replaces ordinary `B`. Also
  migrate the class's cascade-protection fixture so its no-change assertions
  exercise an active fault on the ID and all its upper questions.
- `dontKnowAndAnOutlierCanAffectDifferentAnswersOnTheSameSubmission` and
  `surveyIdNumbersAreProtectedFromDontKnowWhileOtherNumbersAreChanged`:
  supply `-99` through `dontKnowCodes`. Keep every assertion about the changed
  field, protected ID, outlier coexistence and clean values.
- `faultsAndOriginalDurationTargetsCoexistAndFaultRecordsStayAfterDurationRecords`:
  add `dontKnowCodes: ["-99"]` to `allFaultSettings()`. Retain the short-target
  counts, evidence of each requested fault and record ordering. Retain the
  existing `specialValues` setting for its separate uses.
- `aSubsetOfFaultsUsesOnlyItsOwnMembersAndItsOwnCumulationThreshold` (both
  cases) and `faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder` (six
  cases): the same shared-setting fix activates `dontKnow`. Keep the subset
  and canonical assignment assertions, wraparound, clean-half requirement and
  warning thresholds of four and six enumerators respectively.
- `fourthFaultFollowsCanonicalAssignmentAndWrapsAfterTheCleanHalf` (four
  cases): add `dontKnowCodes: ["-99"]` to its shared `FIXED` configuration.
  Keep the four-fault order, wraparound and eight-enumerator warning threshold.

The disputed assignment tests already name their faults explicitly through
`rates(...)`; none requests the default fault list. Their three- and
four-fault expectations therefore remain correct. Adding `refuse` or changing
their thresholds to ten would change the scenarios unnecessarily. Shared
fixture repairs also keep the existing replay, configured-order, resent-copy
and interaction tests meaningful. No probability or behavioral assertion was
weakened.

### Dispute 6: upheld; provide enough possible survey IDs

Spec 13's "Survey ids", incorporated by spec 14, defines integer ID capacity
from the ID override when present, otherwise from `integerRange`, and requires
reuse when capacity is smaller than the submission count. The support range
10..20 contains only 11 values. Neither `dontKnow` nor `refuse` promises to
create 240 unique IDs from that range; their obligation is to leave IDs alone.

In both `InjectedAmendment5RecordsTest` and `InjectedAmendment6RecordsTest`,
`teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone` now gives only
the ID field a min/max override of 10..249, exactly 240 possible integers.
Keep all assertions: IDs are unique and equal to the same visits' clean IDs,
read-only and calculated values stay fixed, the roster retains three members,
and only the eligible amount field receives team codes. The other fields'
ordinary ranges and overrides are unchanged.

No production source was read or changed, and no commit was made.

**Verification:** `mvnw.cmd -o -q test` failed at `testCompile` on the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, so the full Maven
suite could not run. The supplementary compiler successfully compiled all
49 spec 14 test/support sources against existing compiled API classes, both
before and after the final fixture correction. The separate JUnit run of the
seven affected classes started 80 tests: 79 passed; the explicit-empty-array
fixture failed during configuration parsing. After correcting it to omit the
key as spec 04 requires, its targeted rerun passed (1 started, 1 successful).
Thus all 80 affected test cases have passing results across those runs, with
no skips or aborts. `git diff --check` also passed.

Logs: `.codex-runs/spec14-disputes-20261101-maven.log` and
`.codex-runs/spec14-adjudication-20261101/{compile-final.log,junit.log,junit-empty-codes.log}`.
The Maven limitation is also recorded in the questions file.

## Dispute 7: tests that expect text to keep its answer (2026-11-01)

**Tests:** in `InjectedAmendment1AssignmentTest`:
`faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder` (6 cases); in
`InjectedAmendment1DontKnowTest`: `repeatCountQuestionsAreExcludedFromDontKnow`,
`textAndDateQuestionsAreNotReplacedEvenWhenTheirTeamCodesWouldBeValid`; in
`InjectedAmendment1InteractionsTest`:
`faultsAndOriginalDurationTargetsCoexistAndFaultRecordsStayAfterDurationRecords`;
in `InjectedAmendment5CodesTest` and `InjectedAmendment6CodesTest`:
`textAndDatesKeepTheirOrdinaryAnswersDespiteMatchingTeamCodeKinds`; in
`InjectedAmendment5DisabledTest` and `InjectedAmendment6DisabledTest`:
`aNonemptyButUnusableCodeListStillReservesItsFaultyEnumerator`; in
`InjectedAmendment5RecordsTest` and `InjectedAmendment6RecordsTest`:
`teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone`; in
`InjectedAmendment6AssignmentTest`:
`fifthFaultUsesCanonicalRoundRobinAssignmentAndLeavesAtLeastHalfClean`
(6 cases); in `InjectedAmendment6CodesTest`:
`irrelevantNumericQuestionsAreNeitherAnsweredNorRecorded`; in
`InjectedAmendment6PrecedenceTest`:
`answersAlreadyChangedBySkipModulesAreProtectedFromRefuse`; in
`InjectedAmendment6RecordsTest`:
`refusalDoesNotChangeAnAddAnotherAnswerThatControlsAnIndirectRepeatCount`.

**Spec:** Amendment 7: a faulty enumerator of `dontKnow` or `refuse` also
answers a `text` question with one of the team's codes the engine accepts;
"earlier tests that expect a text question to keep its answer under these
faults are superseded and go through a dispute". Clarification G7.

**What the tests do:** each form holds an ordinary text question (a
roster's `person/name`, an `answer` or module question of type string, a
text companion) and the test expects no record on it, an empty or
ordinary value, records on the numeric or select field only, or no
record at all for a code list that no select or number can use (a text
question can). The failures read, for example, `expected: <> but was:
<-99>`, `expected: <amount> but was: <name>`, `expected: <yes> but was:
<q2 8033>` (the `cleanValue` of a record on the text question `q2`, not on
the gate), or `expected: <true> but was: <false>` for "no records". In the
add-another, skipModules and repeat-count tests the protected answers
(the `more` choice, the gate, the count) are left alone; the new records
are on the roster's or module's text questions.

**Requested change:** keep each test's purpose and assertions on the
protected or numeric/select fields, and either let the text questions
receive codes (excluding them from the "no record" or "unchanged"
checks, or asserting their codes as Amendment 7 does), or fix their
answer to a value no team code equals while making them ineligible some
other way the spec allows (for example, a text question whose
`constraint` rejects the codes). For the date part of
`textAndDate...` tests, keep the assertion that dates keep their answers.

## Dispute 8: a text survey id without possible values (2026-11-01)

**Tests:** `InjectedAmendment7ExclusionsTest.textSurveyIdsRemainUniqueAndUnchanged`
(both cases).

**Spec:** spec 13, "Survey ids": a text id question without a list of
texts has no possible values; "its answers are drawn as usual and may
repeat", with a warning. This is the situation of Disputes 4 and 6,
upheld.

**What the test does:** `"id":"survey_id"` on a string question with no
`texts` override, 240 submissions, and it requires every id to be
distinct (line 81). The walk without any fault already repeats ids (4
duplicates in 240, checked with the public API outside the test tree),
with the warning `the survey id 'survey_id' has no possible values; its
answers are drawn as usual and may repeat`; the assertion that each id
equals its clean walk's id passes.

**Requested change:** give the id question at least 240 possible values
(a `texts` override, or an integer id with a wide enough range), keeping
the other assertions.

## Adjudication of Disputes 7 and 8 (2026-11-01)

### Earlier decisions: retain the corrections for Disputes 1–6

The prior decisions remain consistent with the spec: duplicate lookup needs
distinct KEYs; Amendment 2 supersedes the old roster probability and
twenty-refusal warning; add-another fixtures must actually extend and stop;
ID uniqueness needs enough possible values; and an active `dontKnow` needs
`dontKnowCodes`. Keep those corrections. The additional text eligibility
introduced by Amendment 7 requires the changes below to some of the same
tests, without reversing the earlier decisions.

### Dispute 7: upheld; ordinary text is eligible, protected answers remain protected

Amendment 7 and clarification G7 expressly supersede expectations that
ordinary text keeps its answer. Its 0.25 replacement probability and
engine-accepted codes apply to text, including text in groups and repeats.
The same amendment explicitly preserves the exclusions for dates, IDs,
read-only fields, calculations, repeat counts and their controlling answers,
and answers already changed by another fault. Therefore whole-run no-change
assertions must not treat eligible text as protected.

- `InjectedAmendment1AssignmentTest.faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder`
  (six cases) and
  `InjectedAmendment1InteractionsTest.faultsAndOriginalDurationTargetsCoexistAndFaultRecordsStayAfterDurationRecords`:
  retain every assertion. The shared `amendment1/all-faults.xml` fixture now
  rejects `-99` in its three module text questions. Those remain ordinary
  questions for timing and module skipping; the `code` select remains eligible
  for `dontKnow`. This isolates the existing assignment, duration, metadata
  and ordering checks using Amendment 7's engine-acceptance rule.
- `InjectedAmendment1DontKnowTest.repeatCountQuestionsAreExcludedFromDontKnow`:
  keep count answer 3 and three roster instances. Replace the whole-run
  no-record assertion with actual replacements restricted to `name`, using
  one of the configured codes `1` and `2`. The count still cannot have a record.
- `InjectedAmendment1DontKnowTest.textAndDateQuestionsAreNotReplacedEvenWhenTheirTeamCodesWouldBeValid`
  and `textAndDatesKeepTheirOrdinaryAnswersDespiteMatchingTeamCodeKinds` in
  `InjectedAmendment5CodesTest` and `InjectedAmendment6CodesTest`:
  rename to describe text replacements and unchanged dates. Fix the ordinary
  text to `ordinary`, use 600 submissions, and check the 0.25 rate among 300
  faulty-enumerator opportunities, allowed text codes, clean values, recorded
  and unrecorded answers, and metadata. Compare every date with the same
  visit's clean date; only the text may have a replacement record.
- `aNonemptyButUnusableCodeListStillReservesItsFaultyEnumerator` in
  `InjectedAmendment5DisabledTest` and `InjectedAmendment6DisabledTest`:
  replace the text-only fixture with an integer question and the nonnumeric
  code `not-a-number`. The list is now truly unusable. Keep no code records,
  actual `tooFast` records, and the single cumulation warning requiring four
  enumerators. Clarifications E2 and F1 still require an active assignment
  for a nonempty list, even without an eligible question.
- `teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone` in
  `InjectedAmendment5RecordsTest` and `InjectedAmendment6RecordsTest`:
  make the roster's text constraint reject `1` and `2`. Keep all assertions
  on unique IDs and clean IDs, read-only value 17, calculated value 42,
  count 3, three members, and actual records confined to `amount`. Keep the
  sufficient ID range introduced for Dispute 6.
- `InjectedAmendment6AssignmentTest.fifthFaultUsesCanonicalRoundRobinAssignmentAndLeavesAtLeastHalfClean`
  (six cases): retain every assertion. The shared `amendment6/five-faults.xml`
  fixture now rejects both `-99` and `REF` in its interview and module text
  questions. The select question still accepts both codes. Canonical fault
  assignment, wraparound, clean-half, warning and ordering checks are unchanged.
- `InjectedAmendment6CodesTest.irrelevantNumericQuestionsAreNeitherAnsweredNorRecorded`:
  retain the blank numeric answer and absence of records on it. The ordinary
  text companion now positively exercises `refuse`, with fixed ordinary text,
  a 0.25 rate and truthful records restricted to `answer`.
- `InjectedAmendment6PrecedenceTest.answersAlreadyChangedBySkipModulesAreProtectedFromRefuse`:
  retain that skipped submissions receive no refusal record and their gate
  stays `no`. Require an observed gate refusal, whose clean value remains
  `yes`; also allow records on the three eligible module text fields. All
  refusal records must still use `REF`. A module text record cannot be required
  to have the gate's clean value.
- `InjectedAmendment6RecordsTest.refusalDoesNotChangeAnAddAnotherAnswerThatControlsAnIndirectRepeatCount`:
  allow actual `REF` replacements only on `name`. Keep two roster instances
  and require `yes` on both instances' `more` answers. This checks the
  controlling answers' protection while exercising an active text fault.

Shared fixture changes also apply to their existing replay and interaction
tests. No assignment, probability, protected-field or ordering rule is relaxed.

### Dispute 8: upheld; provide a finite pool for the text survey ID

Spec 13's "Survey ids", incorporated by spec 14, defines possible text IDs
through an override's `texts` list. Without that list, an ordinary text draw
need not be unique. Amendment 7 protects the ID from faults; it does not
create a uniqueness guarantee for unconfigured text draws.

`InjectedAmendment7ExclusionsTest.textSurveyIdsRemainUniqueAndUnchanged`
now supplies exactly 240 distinct text IDs (`household-1` through
`household-240`) for 240 submissions. Both `dontKnow` and `refuse` cases keep
the text ID type, uniqueness assertion, comparison with each clean visit's
ID, positive control on the ordinary text companion, and records restricted
to that companion. No assertion was removed.

No production source was read or changed, and no commit was made.

**Verification:** The required `mvnw.cmd -o -q test` failed at `testCompile`
on the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, before
JUnit could run. The supplementary compiler successfully compiled all 56
spec 14 test/support sources with `--release 17` against existing compiled
API classes and cached dependencies. The supplementary JUnit run of all
12 affected classes discovered and passed 213 tests, with no failures,
skips or aborts. `git diff --check` passed. These results verify the changed
tests, but do not establish a successful full Maven suite.

Logs: `.codex-runs/spec14-disputes-7-8-maven.log` and
`.codex-runs/spec14-disputes-7-8/{compile.log,junit.log}`. The Maven limitation
is also recorded in the questions file.

## Dispute 9: a decimal written as the engine writes it (2026-11-01)

**Test:** `InjectedAmendment8ExclusionsTest.nonselectInputsAreNeverTreatedAsOtherQuestions`
(the `decimal` case).

**Spec:** spec 08 and spec 07: answers are written as the engine writes
them; a decimal question answered 12 holds `12.0` (spec 14, Amendment 1
already compares numbers as numbers).

**What the test does:** a `decimal` question fixed to `12` and the
assertion `unchanged(r, "protected", "12")`, which fails with `expected:
<12> but was: <12.0>`. The walk without any fault writes `12.0` too; the
fault leaves the question alone, which is what the test is about.

**Requested change:** compare with the value the walk without faults
writes (or as a number) for the decimal case, keeping the other cases.

## Dispute 10: a select used directly as a repeat count (2026-11-01)

**Test:** `InjectedAmendment8ExclusionsTest.directAndIndirectSelectRepeatCountControllersRemainProtected`
(the direct case, `indirect` false).

**Spec:** the fault's protections (Amendment 8, "Eligible questions")
leave repeat counts alone; nothing in the specs makes the engine create
repeat instances from a `select_one` named directly in `jr:count`.

**What the test does:** `jr:count="/data/size"` where `size` is a
`select1` fixed to `3`, and it asserts three `person` instances (line 41).
The walk without any fault gives no instance either (checked with the
public API outside the test tree: 0 instances faulty and clean), so the
assertion fails whatever the fault does; `size` itself is unchanged, and
the indirect case (through an integer calculation) passes.

**Requested change:** keep the assertion that `size` is unchanged and
that the companion is changed, and compare the instance count with the
walk without faults (or drop it) in the direct case.
