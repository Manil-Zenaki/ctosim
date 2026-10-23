# Spec 12 — disputes

## Dispute 1: forms the engine cannot load

`FakeAttachmentsDiscoveryTest.unreadablePulldataCallsDoNotThrowOrLoseOtherReadableCalls`
(all four cases) fails before `FakeAttachments.of` is called: the test
support loads a form whose calculate is `pulldata()`, `pulldata('good')`,
`pulldata('good','bad_value','bad_key')` or a five-argument call, and
`FormLoader.load` stops with "the form engine could not initialise the
form (... XPath evaluation: type mismatch for function 'pulldata')".
`pulldata()` takes exactly four arguments in the engine (spec 03), so
such a form cannot be loaded at all and no `LoadedForm` can reach this
module. The case the spec covers ("a call it cannot read adds nothing")
is a call the engine accepts but this module cannot read, for example a
non-literal file or column name (`pulldata(/data/f, 'age', 'id', ...)`),
or a call in an expression that is not evaluated when the form
initialises (a `relevant` or `constraint` of a question). The test
should use such a call, or expect the load to fail.

## Adjudication

### Dispute 1: upheld; fix the fixture, retain the expectations

Spec 12 accepts an already loaded `LoadedForm`. Its Errors section requires
unreadable calls to add nothing without throwing; it does not require
`FormLoader` to accept forms whose calculations cannot initialise. The four
disputed cases therefore used the wrong expression site: a failure while
loading their malformed calculations cannot establish a violation by
`FakeAttachments.of`.

Updated all four cases of
`FakeAttachmentsDiscoveryTest.unreadablePulldataCallsDoNotThrowOrLoseOtherReadableCalls`
to place the malformed call in the `constraint` of an input question instead
of its `calculate`. Constraints are among the bind expressions spec 12 scans,
and this fixture does not answer or validate the question. The valid
four-argument calculation remains in the same form. Kept the assertions that
`FakeAttachments.of` does not throw and that the generated `good` file has
exactly `age,id` as its columns: these enforce the Errors section and column
discovery rules without requiring the loader to evaluate an invalid call.
Expecting a load failure instead would test the loader rather than spec 12.

Validation: ran `.\mvnw.cmd -o -q test`, then repeated with `-e` to diagnose
the compiler failure. Both stopped at `testCompile`; the diagnostic reports
`java.nio.file.AccessDeniedException` on
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. No tests ran, so
compilation and execution of the revised fixtures remain unverified in this
sandbox. Logs are `.codex-runs/fake-attachments-adjudication-test.log` and
`.codex-runs/fake-attachments-adjudication-diagnostic.log`. No production
source was read or modified.

## Dispute 2: an id level compared with the file's rows (Amendment 1)

`FakeAttachmentsAmendment1ContractTest.countsAtOrBelowOrdinaryLeafCountPreserveBytesEvenWhenIdCountIsSmaller`,
seven cases (line 64), expects a file whose id level has fewer codes than
the submissions to stay unchanged as long as the submissions do not
exceed the file's rows (for an id at the first level of a three-level
file, up to 27). The Clarification of Amendment 1 corrects assumption 17:
the comparison is with the number of codes of the id level, so such a
level is expanded (3 codes, 20 submissions: 20 codes).

Requested: expect expansion when the submissions exceed the id level's
own codes, and keep the byte-for-byte check for counts at or below them.

## Adjudication of the current disputed tests

### Dispute 1: retain the corrected constraint fixtures

The existing adjudication remains valid. Spec 12 takes a `LoadedForm` and
scans bind expressions, including constraints. Its Errors section requires
unreadable calls to add nothing without throwing; it does not require the
loader to initialise malformed calculations. The current four disputed
cases already put the invalid calls in a question's constraint and retain
a valid calculation. Keep those tests unchanged: their no-throw and exact
`age,id` header assertions test spec 12 without evaluating the invalid
calls at form initialisation.

### Dispute 2: upheld; use the ordinary id-code count as the threshold

The Clarification of Amendment 1 explicitly corrects assumption 17. The
comparison is with the ordinary number of codes at the id level, not the
file's terminal row count. The seven disputed unchanged-file expectations
are therefore incorrect, even though they followed the earlier wording.

Renamed the preservation test to
`countsAtOrBelowOrdinaryIdCodeCountPreserveBytes`. Retained its valid cases
and added early-level boundary cases: first-level requests through 3 and
second-level requests through 9 must preserve both exact CSV bytes and key
answers. The existing terminal-level thresholds of 27 and 486 remain.

Added `submissionsAboveIdCodeCountExpandEvenWhenOrdinaryFileHasEnoughRows`
with all seven disputed inputs and the clarification's explicit 20-submission
example. Expected branch counts follow Amendment 1:

- First-level ids in a three-level file with 4, 20, 26, or 27 submissions
  have that many first-level codes and one child at each following level.
- Second-level ids with 10 submissions have 3 parents with 4 children each
  (12 rows); with 26 or 27 submissions they have 9 children per parent
  (27 rows). The last level has one child per id.
- A first-level id in an eight-level file with 486 submissions has 486
  first-level codes and one child at every subsequent level.

The existing independent path oracle checks complete code paths, padding,
exact row counts, one distinct id per row, and enough rows for the requested
submissions. It imposes no additional CSV row ordering. Updated question 17
to record the clarification instead of preserving the superseded assumption.

### Validation

Ran `.\mvnw.cmd -o -q test`; it stopped at `testCompile` with a fatal
compiler error naming `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
Log: `.codex-runs/fake-attachments-dispute2-test.log`. This is not a passing
Maven build.

Reused the existing in-memory classpath compiler diagnostic against compiled
public APIs and cached dependencies. All 10 `FakeAttachments*.java` test and
support sources, plus the temporary JUnit runner, compiled with Java release
17. Log: `.codex-runs/fake-attachments-dispute2-probe-compile.log`.

Ran all three Amendment 1 test classes and `FakeAttachmentsDiscoveryTest`
through JUnit Platform: 174 tests started, 174 passed, no skipped tests or
failed containers. This includes all four malformed-call fixtures from
Dispute 1 and the corrected threshold and expansion cases from Dispute 2.
Log: `.codex-runs/fake-attachments-disputes-focused-tests.log`.

No production source was read or modified, no dependencies or build
configuration were changed, and no commit was made.
