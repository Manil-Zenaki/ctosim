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
