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
