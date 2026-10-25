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
