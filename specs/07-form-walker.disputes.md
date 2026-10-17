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

## Dispute 3: a `calculate_here` of `now()` stores the date only (Amendment 4)

Amendment 4 (from spec 03 Amendment 4) makes a `calculate_here` whose
result is a date-time store the date only. Two tests read a timestamp
from such a field:

1. `FormWalkerAmendmentCalculateHereTest.calculationRunsAtItsBodyPositionUsingTheCurrentClock`
   parses `at_here` (`now()`) as an offset date-time equal to the start
   plus the elapsed seconds.
2. `FormWalkerAmendmentTimeZoneTest.storedTimestampsAndGeneratedAnswersUseConfiguredOffset`
   parses `last_now` (`now()`) as an offset date-time equal to `end()`.

Requested: calculate those fields as
`format-date-time(now(), '%Y-%m-%dT%H:%M:%S')` (the time SurveyCTO's
documentation records this way) and compare the local date-time, or
assert the date-only value; keep the other fields of these tests
(`start`, `end`, `datetime` and `time` answers, `duration()`), which are
unchanged.

## Adjudication including Amendment 4

### Dispute 1: keep the corrected retry-budget test

The current fixture uses `. = 1 or . = 1`, which accepts exactly 1 and
provides no hints under spec 09's "Deriving hints" and Amendment 1.
Spec 07 still requires up to 50 fresh attempts. The configured range
1..20 therefore supports the existing exhaustion probability `(19/20)^50`.
Keep the fixture, statistics, and assertions unchanged.

### Dispute 2: keep the corrected device-id assertions

All three current assertions require `[0-9a-f]{16}`, and the
single-enumerator test has already been renamed accordingly. This is
exactly the format required by spec 07, Amendment 2, which supersedes
Amendment 1's 15-digit format. Keep those tests unchanged.

### Dispute 3, point 1: accepted; preserve the body-position timing check

Amendment 4 explicitly supersedes timestamp expectations for a
`calculate_here` whose result is a date-time. The old `at_here` fixture
used `now()` directly, so parsing it as an offset date-time was wrong.
Change its expression to `format-date-time(now(), '%Y-%m-%dT%H:%M:%S')`
and its bind type to `string`, and parse the resulting text as a
`LocalDateTime`. This follows Amendment 4's documented way to preserve
time as text and retains the assertion that the calculation runs at
`start()` plus the preceding question's elapsed seconds. Keep the
`duration()` checks, subsequent clock advance, and answered count.

### Dispute 3, point 2: accepted; assert dates for raw calculations

Keep `last_now` as a direct `now()` calculation and assert its exact
`YYYY-MM-DD` value equals `end()`'s local date. The same shared fixture
contains `opened`, calculated as `once(now())`; Amendment 4 applies to
every date/date-time calculation, so assert that field equals `start()`'s
local date as well. Neither calculated field should have an offset.

Apply this correction to both tests using that fixture's timestamp
assertions: `storedTimestampsAndGeneratedAnswersUseConfiguredOffset`
and `regionOffsetsFollowTheSimulatedDate`. Retain their configured-zone
and seasonal-offset checks for `started` and `finished`, as well as the
generated `datetime_answer` and `time_answer` offset checks and exact
start/end timestamp comparisons. These timestamps are explicitly
unchanged by Amendment 4. No production source was read or modified.

Validation: `mvnw.cmd -o -q test` reached `testCompile` but failed on
the cached `commons-io-2.5.jar`; this limitation is recorded in the
questions file. The existing offline compilation probe successfully
compiled all 23 FormWalker test/support sources. A JUnit harness ran
the five disputed test classes: 73 cases started, 62 passed, 11 failed,
none skipped or aborted, and no container failures. The body-position
test with formatted text passed, as did the retained retry and device-id
tests. All 11 failures are in the two time-zone parameterized tests at
the `opened` date-only assertion: the current XML contains a midnight
timestamp with an offset instead of `YYYY-MM-DD`. Those cases stop
before the `last_now` assertion. Retain the date-only expectations:
spec 03, Amendment 4's clarification explicitly says date storage
applies whatever the field's declared type, including this fixture's
`dateTime` binds. Compilation succeeded, but the targeted tests and the
Maven suite are not green. Logs are
`.codex-runs/walker-dispute3-maven-test.log`,
`.codex-runs/walker-dispute3-compile.log`, and
`.codex-runs/walker-dispute3-junit.log`.

## Dispute 4: retry-budget tests and the Amendment 5 oracle after spec 09 Amendment 2

1. `FormWalkerDistributionTest.retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget`
   (constraint `. = 1 or . = 1`) and
   `FormWalkerAmendment5FinalCheckTest.retriesUseFreshDrawsAndApproximatelyFiftyAttemptsBeforeClearing`
   (constraint `/data/gate = 0 or . = 'good'`) measure how often 50
   rejected draws exhaust. Spec 09 Amendment 2 derives hints from
   disjunctions and gives both constraints a candidate (`1`, `good`) that
   the walker tries after the 50 attempts, so they never exhaust.
   Requested: use constraints that give neither hints nor candidates,
   where `.` sits inside another expression: `. * 2 = 2` for the integer
   case, `concat(., '') = 'good'` (with the gate) for the select case.
2. `FormWalkerAmendment5AcceptanceTest.everyPopulatedQuestionInEveryPublishedSubmissionSatisfiesItsFinalConstraint`
   reports `/data/consented_grp[1]/f_hr_rpt[2]/f_hr_age_r[1] = 6 violates
   if(index() = 1, . >= 18, if(index() = 2, . >= 3 and . <=6, ...))`.
   For the second instance `index()` is 2 (spec 03), so the constraint is
   `. >= 3 and . <= 6`, which 6 satisfies; the engine accepted the value.
   The oracle's frozen form copies no repeat declaration, so the SurveyCTO
   `index()` (the position of the enclosing repeat instance) finds no
   repeat and returns 1 for every node. Requested: declare the form's
   repeats in the frozen form (templates and repeat controls) so that
   `index()` gives each instance's position, or evaluate constraints in
   the walked session itself.

## Adjudication after spec 09 Amendment 2

### Dispute 1: retain the budget check, supersede its disjunction fixture

The earlier adjudication was correct under spec 09 Amendment 1. Its
premise that disjunctions give no hints is explicitly superseded by
Amendment 2's whole-constraint alternatives and candidate rules. The
50-proposal budget remains, followed by candidate attempts when any
exist. Apply the fixture correction in Dispute 4 below; retain the
statistical check and its answer, warning, and clock assertions.

### Dispute 2: keep the corrected device-id tests

The current three assertions use `[0-9a-f]{16}`, and the renamed
single-enumerator test describes that format. These agree with spec 07
Amendment 2. Keep the format, stable identity, distinctness, enumerator
distribution, and timestamp assertions unchanged.

### Dispute 3: keep both previously corrected calculation tests

The body-position test now stores `format-date-time(now(), ...)` as text
and compares a local date-time. The time-zone tests assert date-only
values for raw `now()` and `once(now())` calculations, while retaining
offset assertions for preloaded timestamps and generated answers. These
are the distinctions required by spec 07 Amendment 4; no further change
is warranted.

### Dispute 4, point 1: accepted; use constraints without hints or candidates

Spec 09 Amendment 2 explicitly amends both navigation retries and the
Amendment 5 final check: after 50 rejected proposals, the walker tries
the constraint's candidates before clearing. It also derives alternatives
from disjunctions. Thus `. = 1 or . = 1` supplies singleton numeric hints
and candidate `1`; `/data/gate = 0 or . = 'good'` supplies candidate
`good`. Expecting the original exhaustion frequencies is now incorrect.

Change the navigation constraint to `. * 2 = 2` and the final-check
constraint to `/data/gate = 0 or concat(., '') = 'good'`. These validate
the same generated values but match none of the specified hint or
candidate shapes: `.` is inside arithmetic or a function, not a direct
equality operand. Keep the fixed seeds, sample sizes, generous binomial
tolerances, and all other assertions. The exhaustion probabilities remain
`(19/20)^50` and `0.98^51` respectively; the latter includes the original
navigation draw followed by 50 final-repair draws. These statistical tests
do not distinguish the exact 49/50/51 boundary.

### Dispute 4, point 2: accepted; preserve repeat identity in the oracle

Spec 07 Amendment 5 requires evaluating constraints in each relevant
repeat instance on the final instance. Spec 03's `index()` is the
1-based position of the innermost enclosing repeat, including through
ordinary groups. A frozen form with no repeat declarations instead gives
the expression an outside-repeat context. For the reported second row,
`6` satisfies the specified 3..6 branch; the oracle's first-row branch
is an invalid expectation. Keep the published-form acceptance assertion
and correct its evaluation context.

The oracle now copies the repeat hierarchy as structural controls and
adds blank templates from the source form. It does not copy `jr:count`,
calculations, actions, relevance, or preloads, and does not navigate or
answer the snapshot. Submitted values and actual repeat instances remain
the basis for evaluation; templates are excluded from inspection. A repeat
absent from the snapshot and its available templates is not recreated.

Added focused oracle checks that accept a valid second-row age and reject
a value allowed only in the first row; verify innermost indices across
nested repeats and separate parents; and ensure source repeat counts do
not change snapshot counts. Existing checks for invalid cross-field
values and suppressed recalculation remain. Updated questions 42 and 43
to record these corrections. No production source was read or modified.

## Dispute 5: tests that expect a blank answer for a satisfiable constraint (Amendment 6)

Amendment 6 makes the walker search before leaving a question blank:
a question stays blank only when no value of the search levels (spec 05,
Amendment 2) satisfies its constraint. Three tests expect a blank answer
for a constraint that such a value satisfies:

1. `FormWalkerDistributionTest.retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget`:
   `. * 2 = 2` is satisfied by 1, an integer of the drawing range, so no
   submission is blank. The 50-attempt budget is no longer observable
   through blank answers (attempts do not move the clock either).
   Requested: assert that every submission answers 1 without warnings,
   or remove the statistical exhaustion check.
2. `FormWalkerAmendment5FinalCheckTest.retriesUseFreshDrawsAndApproximatelyFiftyAttemptsBeforeClearing`:
   `concat(., '') = 'good'` is satisfied by the offered choice `good`,
   which level 1 of a select_one lists. Requested: as in point 1.
3. `FormWalkerAmendment5FinalCheckTest.anUnrepairableGeneratedAnswerIsClearedWithoutChangingAnswered`:
   the late constraint `. <= 5` is satisfied by 0 to 5; the final check
   repairs the fixed answer 9 (Clarification 45: the search applies also
   to a fixed value). Requested: make the late constraint unsatisfiable
   (for example `. <= 5 and . > 5`) so that the answer is cleared.

## Adjudication after Amendment 6

### Dispute 1: superseded by Amendment 6's search

The 50-attempt rule still applies, but exhausted random proposals no longer
imply a blank answer. The current arithmetic constraint has a searchable
answer, so retain the fixture and replace its exhaustion expectation as
described in Dispute 5, point 1. Neither the original equality nor the
later disjunction restores a valid statistical test of the retry budget.

### Dispute 2: keep the corrected device-id tests

All three disputed assertions already require `[0-9a-f]{16}`, and the
single-enumerator test has the corresponding name. These match Amendment
2. Keep their identity, distribution, username and timestamp checks.

### Dispute 3, points 1 and 2: keep the corrected calculation tests

The body-position test uses `format-date-time(now(), ...)` in a string
field and compares its local date-time. The time-zone tests expect date
strings for raw `now()` and `once(now())`, while checking offsets on
preloaded timestamps and generated date-time/time answers. These are the
distinctions required by Amendment 4; both corrections remain valid.

### Dispute 4, point 1: keep the constraints, replace exhaustion expectations

Arithmetic and `concat` still avoid the specified hint/candidate shapes,
but Amendment 6 adds search after those mechanisms fail. The former
exhaustion probabilities describe rejected draws, not final blanks.
Apply the two expectation changes in Dispute 5; do not claim these tests
measure exactly 50 attempts.

### Dispute 4, point 2: keep the corrected final-instance oracle

The oracle already preserves repeat declarations and templates, excludes
templates from inspection, and avoids reapplying counts or calculations.
Its focused tests check the second-row age branch and innermost indices
across nested repeats and parents. This supplies the repeat context
required by spec 03's `index()` and spec 07 Amendment 5's final-instance
guarantee. No further change is needed for this point.

### Dispute 5, point 1: accepted; require the searchable integer answer

Spec 05 Amendment 2 includes every integer of the drawing range 1..20
in search level 1. Exactly 1 satisfies `. * 2 = 2`. Spec 07 Amendment 6
therefore requires that answer even after all random proposals fail.
Renamed the test to
`aSearchableIntegerConstraintAlwaysGetsAnAnswerWithoutWarnings` and
require value `1`, `answered() == 1`, and no warnings in every submission.
Retained the fixed seed, 2,500 submissions, constraint and one-handling
clock assertion. Removed the blank-frequency assertion.

### Dispute 5, point 2: accepted; require the searchable final selection

Spec 05 Amendment 2 lists every offered select-one choice in level 1,
including `good`. Once the gate becomes 1, `good` satisfies the final
constraint. Amendment 6 applies expressly to Amendment 5's final check,
so no submission may remain blank because the repair draws failed.
Renamed the test to
`aSearchableFinalSelectionConstraintAlwaysGetsAnAnswerWithoutWarnings`.
Retained the fixture, weights, fixed seed and 700 submissions; require
`good` and no warnings each time. Keep `answered() == 2` and check the
elapsed time for two navigation handlings; final search adds no handling.
Removed the exhaustion-frequency assertion.

### Dispute 5, point 3: accepted; make final clearing unavoidable

The original `. <= 5` permits searchable integers even when the
configuration fixes the answer at 9. Amendment 6's confirmed assumption
45 explicitly applies search to fixed overrides; expecting clearing for
that fixture is wrong. Changed only its late constraint to
`. <= 5 and . > 5`. The gate initially allows the navigation answer 9,
then makes the constraint unsatisfiable at the final check. Keep both
requiredness cases and all clearing, answered-count, time, warning-order,
warning-prefix and unmodifiable-list assertions.

No production source was read or modified, and no commit was made.

Validation: `mvnw.cmd -o -q test` reached `testCompile` but failed on
the cached `commons-io-2.5.jar`; the questions file records this build
limitation. The existing offline compilation probe compiled all 38
FormWalker test/support sources successfully. The existing JUnit dispute
harness ran 214 cases, including both changed classes, the published-form
final-constraint acceptance tests and oracle checks, the earlier device-id
and calculation tests, and the two disputed time-zone parameterized tests.
All 214 passed, with no skipped or aborted tests or container failures.
This verifies compilation and the selected tests, not a successful Maven
suite run. Logs: `.codex-runs/walker-dispute5-maven-test.log`,
`.codex-runs/walker-dispute5-compile.log`, and
`.codex-runs/walker-dispute5-junit.log`.

## Dispute 6: two Amendment 9 tests of behaviour the amendment does not change

Amendment 9 changes only how the walker draws an `INTEGER` count
question (a question the generator answers as `INTEGER`) whose override
has no range. Two tests in `FormWalkerAmendment9DependencyTest` fail
identically with and without the amendment's implementation (checked by
running them on the code before it):

1. `decimalCountDependencyKeepsItsDecimalRange`: the decimal question is
   not a count question, and the amendment does not touch it. The
   generator writes `7` (spec 05: decimals without trailing zeros), but
   the engine stores a decimal answer as `7.0` in the instance, so
   `assertCount9` fails on `assertEquals("7", value(...))` with
   `expected: <7> but was: <7.0>`. How a decimal is serialised in the
   instance is not part of Amendment 9; the test should compare the
   number (or accept the engine's decimal text), as its name says it
   checks the decimal range.
2. `integerSelectCountStillUsesOfferedChoicesAndWeights`: an integer
   `select1` is answered as `SELECT_ONE` and is not a count question
   (assumption 59 says so). Its answer is `7`, as the test wants, but the
   engine creates no repeat instance from a `jr:count` that points to a
   select answer (`expected: <7> but was: <0>` on the instance count).
   That is the engine's handling of counted repeats (Amendment 3), not
   something Amendment 9 promises; the test should check the answer and
   its choice weights, which is what it is about, and not the number of
   repeat instances.

## Adjudication of Dispute 6

### Point 1: accepted; compare the decimal answer numerically

Amendment 9 applies only to `INTEGER` questions. This decimal question
therefore keeps the singleton `decimalRange` of 7 under spec 05's number
rules. Spec 05's formatting requirement describes the generator's answer
text; spec 07's "Converting answers" then uses the engine's own conversion
for decimals, and "End of the interview" uses the engine's submission
serialiser. Neither requires the stored decimal to preserve the generator's
exact spelling. Requiring `"7"` rather than the numerically equal `"7.0"`
is an unsupported serialization expectation.

Changed `decimalCountDependencyKeepsItsDecimalRange` to compare the stored
value with 7 using `BigDecimal.compareTo`, so differing scales are accepted
without a floating-point tolerance. Retained the seven-repeat assertion
and the no-warning assertion. The shared integer helper `assertCount9`
and the separate text/appearance test remain unchanged.

### Point 2: accepted; check select choices and weights without cardinality

Spec 07's controls-first mapping explicitly makes an integer select a
`SELECT_ONE`; it is not an `INTEGER` count question under Amendment 9.
The offered choices and spec 05's choice weights therefore still govern
its answer. The original repeat rule delegates counted-repeat creation to
the engine; Amendment 3 specifies the zero-count case but does not define
how the engine interprets a select answer as a numeric count. Neither rule
requires seven repeat instances from this fixture's selected value.
The reported before/after implementation results are not the basis for
this decision; the missing specification for that conversion is.

Replaced `assertCount9` in
`integerSelectCountStillUsesOfferedChoicesAndWeights` with assertions for
the selected value and no warnings, without prescribing any repeat count
(including zero). Parameterized the test over 7 and 8, assigning the
expected choice weight 1 and the other weight 0 in each case. This retains
the offered-choice check and also detects always choosing the first item
or ignoring the weights. The repeat declaration and repeat range 2..2
remain in the fixture. Questions-file point 65 records the unspecified
select-to-count conversion and decimal spelling.

Validation: `mvnw.cmd -o -q test` failed in the production `compile` phase
on the cached `commons-io-2.5.jar`, before test compilation or execution.
The existing offline compiler probe successfully compiled all 47
FormWalker test/support sources against the available project class files.
The existing Amendment 9 JUnit probe then ran all three Amendment 9 test
classes: 78 tests passed, with none failed, skipped or aborted and no
container failures. This verifies independent compilation and those tests
against available compiled classes, not a successful Maven build. Logs:
`.codex-runs/walker-dispute6-maven-test.log`,
`.codex-runs/walker-dispute6-compile.log`, and
`.codex-runs/walker-dispute6-junit.log`.

No production source was read or modified, and no commit was made.
