# Disputes — spec 07 (FormWalker)

## Dispute 1: retry-budget test now satisfied by constraint hints

**Test:** `FormWalkerDistributionTest.retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget`.

**Failure since spec 09 was implemented:**
`Exhausted retry budget ==> expected: <192.36...> but was: <0.0>`.

**Argument:** the test measures how often 50 proposals drawn from 1–20 all
fail the constraint `. = 1`. Spec 09 (which amends spec 07: "Using hints
in the walker") makes the walker derive hints from the constraint, and an
equality term gives both bounds, so the generator proposes 1 directly and
the budget is never exhausted. The test's premise ("each proposal
succeeds with p=1/20") no longer holds for this constraint. Spec 09's
"Amendment 1: effect on spec 07's retry behaviour" records this and names
constraint shapes that give no hints.

**Requested change:** keep the test and its statistics, but use a
constraint that is equivalent for validation and gives no hint under
spec 09, for example `. = 1 or . = 1` or `not(. != 1)`, so that each
proposal still succeeds with probability 1/20.

## Adjudication

### Dispute 1: accepted; update the constraint, retain the statistical test

Spec 07, Questions rule 6, requires fresh proposals up to 50 attempts in
total. Spec 09 explicitly amends the walker to pass constraint hints to
the generator while keeping that retry rule unchanged. Under spec 09's
"Deriving hints", `. = 1` supplies both inclusive bounds; its generator
rules therefore narrow the configured integer range 1..20 to the singleton
1. The old fixture's assumed per-proposal success probability of 1/20 is
no longer valid. This is a test-fixture error under the amended contract,
not evidence that the retry budget is wrong.

Changed the fixture to `. = 1 or . = 1`. It still accepts exactly 1, but
disjunctions explicitly provide no hints under spec 09, as also confirmed
by its Amendment 1. This restores proposals from 1..20 and the exhaustion
probability `(19/20)^50`. Retained the fixed seed, 2,500 submissions,
seven-sigma tolerance, and all answer, warning, and clock assertions.
The test remains an approximate statistical check, not proof of the exact
49/50/51 boundary. No implementation source was read or changed.

Validation: the prescribed `mvnw.cmd -o -q test` could not start because
the wrapper attempted an unavailable download. Cached Maven reached
`testCompile` but failed on `commons-io-2.5.jar`. The existing compilation
probe compiled all seven FormWalker test source files successfully, and
the existing JUnit probe ran all 128 FormWalker tests: 128 successful,
none skipped, none failed, including the amended retry test. The run log
is `.codex-runs/form-walker-dispute-junit.log`. These probe results do not
constitute a successful Maven build; the build limitation is also recorded
in `07-form-walker.questions.md`.

## Dispute 2: device ids are now 16 hexadecimal characters (Amendment 2)

Amendment 2 replaces the device id format of Amendment 1: a `deviceid` is
now "a string of 16 lowercase hexadecimal characters (`0-9a-f`) ...
instead of 15 decimal digits". Three tests written for Amendment 1 still
require `[0-9]{15}`:

- `FormWalkerAmendmentCoverageTest.coverageUsesDefaultEnumeratorIdentityAndUtcTimestamp`
  (line 52);
- `FormWalkerAmendmentEnumeratorTest.aSingleEnumeratorHasTheSameFifteenDigitDeviceForEverySubmission`
  (line 37);
- `FormWalkerAmendmentEnumeratorTest.defaultEnumeratorsAreUniformAndHaveStableDistinctDeviceIds`
  (line 55).

Requested change: expect `[0-9a-f]{16}` in these assertions (and rename
the "FifteenDigit" test); keep everything else they check.

## Adjudication of the current disputes

### Dispute 1: retain the previously corrected retry-budget test

The current test already uses `. = 1 or . = 1`. Spec 09's "Using hints
in the walker" amends spec 07, and its Amendment 1 explicitly identifies
this disjunction as a constraint that gives no hints. It accepts only 1
while preserving proposals from the configured integer range 1..20.
Spec 07's 50-attempt rule therefore still supports the test's exhaustion
probability `(19/20)^50`. Keep the current test unchanged; the earlier
adjudication remains valid.

### Dispute 2: accepted; update all three device-id assertions

Spec 07, Amendment 2, "Device ids", explicitly requires 16 lowercase
hexadecimal characters instead of 15 decimal digits. This supersedes
the earlier format in Amendment 1, including its coverage-form acceptance
check. The three disputed `[0-9]{15}` assertions are therefore wrong.

Changed them to `[0-9a-f]{16}` in
`coverageUsesDefaultEnumeratorIdentityAndUtcTimestamp`,
`defaultEnumeratorsAreUniformAndHaveStableDistinctDeviceIds`, and the
single-enumerator test. Renamed the latter to
`aSingleEnumeratorHasTheSameSixteenCharacterLowercaseHexDeviceForEverySubmission`.
Java's `String.matches` checks the entire value, so these assertions
enforce the required length, character set, and lowercase spelling.
All remaining checks are retained: stable per-enumerator identity,
distinct ids within a configuration, uniform enumerator assignment,
username formatting, and the UTC timestamp are unchanged by Amendment 2.
No implementation source was read or modified.

Validation for this adjudication: `mvnw.cmd -o -q test` reached
`testCompile` but failed on the cached `commons-io-2.5.jar`. The existing
compilation probe successfully compiled all 18 FormWalker test/support
sources against the project class files. A JUnit harness then ran
`FormWalkerAmendmentCoverageTest`, `FormWalkerAmendmentEnumeratorTest`,
and `FormWalkerDistributionTest`: all 36 cases passed, with none skipped
or aborted. Logs are `.codex-runs/walker-dispute2-compile.log` and
`.codex-runs/walker-dispute2-junit.log`. This verifies compilation and the
disputed test classes, but does not constitute a successful Maven suite run.
