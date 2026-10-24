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
