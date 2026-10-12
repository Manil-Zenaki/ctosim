# Disputes — spec 06 (simulated sessions)

## Dispute 1: `now()` compared with `date()` in boolean binds

**Tests:** `SimulatedSessionExpressionTest.simulatedFunctionsAreAvailableInEveryBooleanBindAttribute`,
the four cases with condition
`now() > date('2040-02-28') and now() < date('2040-02-29')`
(indices 2, 7, 12, 17: relevant, required, readonly, constraint).

**Expectation in the tests:** the condition is true when the clock reads
2040-02-28T23:59:58.

**Argument:** no implementation can satisfy it with the engine. Comparison
operators convert date values to numbers with
`XPathFuncExpr.toNumeric`, whose bytecode (JavaRosa 6.0.0) handles a
`java.util.Date` with `DateUtils.daysSinceEpoch(Date)` — an `int`, the
whole number of days. Observed in a simulated session with the clock at
2040-02-28T23:59:58: `now()` holds that date-time, `now() > date('2040-02-28')`
is `false`, `now() < date('2040-02-29')` is `true`,
`number(date('2040-02-29'))` is `25626.0`. The same holds for the native
`now()` in an ordinary session. The spec has been amended ("Amendment 1:
comparing `now()` with dates").

**Requested change:** keep the four cases (one per boolean bind
attribute), but with a condition that is true under the engine's
semantics and still exercises the simulated `now()`, for example
`decimal-date-time(now()) > decimal-date-time('2040-02-28T23:00:00') and now() < date('2040-02-29')`
or `format-date-time(now(), '%Y-%m-%d %H:%M') = '2040-02-28 23:59'`.

## Adjudication

### Dispute 1: upheld; correct the four test cases

The original expectation contradicts spec 06, Amendment 1. At the test
clock's `2040-02-28T23:59:58`, the amendment explicitly says that
`now() > date('2040-02-28')` is false: numeric date comparisons drop the
time of day. The conjunction therefore cannot be expected to be true.
This decision follows the amended spec, without inspecting implementation
code or relying on the dispute's bytecode observations.

Changed the shared `now()` condition in `booleanLocations()` to
`format-date-time(now(), '%Y-%m-%d %H:%M') = '2040-02-28 23:59'`.
Amendment 1 explicitly permits `format-date-time` for preserving time of
day, and the Time section requires the engine to show the clock's local
date and time. This checks both against the supplied simulated clock.
All four cases (relevant, required, readonly, constraint) and their
existing assertions are retained; only the invalid condition is replaced.

### Validation of the adjudication

- `mvnw.cmd -o -q test` could not start Maven: the wrapper attempted a
  distribution download and failed to connect.
- Running the existing `.tools/apache-maven-3.9.16/bin/mvn.cmd -o -q test`
  directly failed during compilation on the cached `hamcrest-all-1.3.jar`.
  Diagnostic retries with `-e` reached `testCompile` but failed with
  `java.nio.file.AccessDeniedException` on the cached
  `apiguardian-api-1.1.2.jar`.
- Test compilation and execution therefore remain unverified in this
  environment. No dependencies were installed or copied, no drives were
  mapped, and no production source was read or modified.

## Dispute 2: `now()` calculations store the date only (Amendment 3)

Spec 06 Amendment 3 (from spec 03 Amendment 4) makes a calculation
`now()` store the date only, `YYYY-MM-DD`, in every session. These tests
read a timestamp (epoch milliseconds or a time of day) from the field
`now_value`, calculated as `now()`, and now see midnight of that day:

1. `SimulatedSessionDeviceReplayTest.customPropertiesPreserveClockFunctionsAndMetadataLifecycle`
2. `SimulatedSessionIsolationTest.advancingOneClockDoesNotChangeAnotherSessionsTimeOrDuration`
3. `SimulatedSessionIsolationTest.manyRetainedSessionsKeepTheirOwnInstancesAndClockOrigins`
4. `SimulatedSessionIsolationTest.ordinarySessionsOpenedBeforeAndAfterSimulationKeepNativeTimeAndDuration`
5. `SimulatedSessionTimeTest.calculationsReadTheClockBeforeAnyNavigation`
6. `SimulatedSessionTimeTest.durationUsesClockArithmeticAcrossDaylightSavingTransitions`
7. `SimulatedSessionTimeTest.functionsActionsAndPreloadsUseTheJvmDefaultZone`
8. `SimulatedSessionTimeTest.recalculationAfterMidnightReadsTheNewDateAndTime`
9. `SimulatedSessionTimeTest.repeatedRecalculationWithoutAnAdvanceDoesNotUseRealTime`

Requested: keep what each test checks about the clock, but observe the
time through an expression that keeps it, as Amendment 3 says: calculate
`now_value` as `format-date-time(now(), '%Y-%m-%d %H:%M:%S')` or
`decimal-date-time(now())`, or assert the date-only value where the test
only needs the date. Assertions on `start`/`end` preloads, `setvalue`
actions and `duration()` are not concerned.

## Adjudication (2026-10-11)

### Dispute 1: retain the existing correction

Amendment 1 explicitly makes the original strict date comparison false.
The current `booleanLocations()` already uses
`format-date-time(now(), '%Y-%m-%d %H:%M') = '2040-02-28 23:59'`.
This is permitted by Amendments 1 and 3 and correctly checks the simulated
local time in all four boolean bind attributes. Keep these cases unchanged.

### Dispute 2: upheld for all nine tests; correct the time observation

Amendment 3 explicitly supersedes expectations of a timestamp stored by a
date-valued `calculate`, even with a `dateTime` bind. The function's time
inside an expression remains specified. Change the shared `session.xml`
fixture's `now_value` to a string calculated with
`format-date-time(if(/data/tick >= 0, now(), now()), '%Y-%m-%d %H:%M:%S')`.
The dependency on `tick` remains, so the same answers trigger recalculation.
Use a separate string assertion helper; the existing date/timestamp helper
and all preload, setvalue, and duration assertions retain their meaning.

Decisions for each disputed test:

1. `SimulatedSessionDeviceReplayTest.customPropertiesPreserveClockFunctionsAndMetadataLifecycle`:
   replace both calculated timestamp assertions with exact formatted local
   date-time assertions. Amendment 2 keeps the clock contract in property
   sessions; Amendment 3 changes how this test must observe the time.
2. `SimulatedSessionIsolationTest.advancingOneClockDoesNotChangeAnotherSessionsTimeOrDuration`:
   use formatted times for both sessions. Separate clocks must still expose
   their own current times after interleaved evaluation.
3. `SimulatedSessionIsolationTest.manyRetainedSessionsKeepTheirOwnInstancesAndClockOrigins`:
   use formatted times for each retained session's calculated time. Keep
   assertions on its original start timestamp, elapsed seconds, and answer.
4. `SimulatedSessionIsolationTest.ordinarySessionsOpenedBeforeAndAfterSimulationKeepNativeTimeAndDuration`:
   apply the formatted-time observation to both ordinary and simulated
   sessions, including the opening helper. Amendment 3 covers both kinds of
   session. Ordinary formatted times must match a whole second within the
   real-clock bracket with the existing one-second tolerance. Compare
   formatted instants in the default zone to avoid choosing an arbitrary
   offset during a DST overlap. Preload/action timestamp checks stay intact.
5. `SimulatedSessionTimeTest.calculationsReadTheClockBeforeAnyNavigation`:
   check the formatted opening time. The initialized expression must still
   see the supplied clock before navigation.
6. `SimulatedSessionTimeTest.durationUsesClockArithmeticAcrossDaylightSavingTransitions`:
   check the formatted local time after advancing three hours. Keep 10,800
   elapsed seconds, as the spec's confirmed clarification requires local
   clock arithmetic independently of DST offsets.
7. `SimulatedSessionTimeTest.functionsActionsAndPreloadsUseTheJvmDefaultZone`:
   separate formatted calculation assertions from timestamp assertions for
   actions and preloads. Keep every zone case and the subsequent advance;
   the Time section still requires the clock's local date and time.
8. `SimulatedSessionTimeTest.recalculationAfterMidnightReadsTheNewDateAndTime`:
   check the formatted new date and time after the three-second advance,
   preserving coverage of the midnight boundary and live reevaluation.
9. `SimulatedSessionTimeTest.repeatedRecalculationWithoutAnAdvanceDoesNotUseRealTime`:
   check the same formatted time on every recalculation. The clock only
   moves when advanced; Amendment 3 does not weaken this requirement.

### Validation

- `mvnw.cmd -o -q test` reached `testCompile` but failed on the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
- A diagnostic retry, `mvnw.cmd -o -q -e test`, confirmed
  `java.nio.file.AccessDeniedException` for that JAR. The log is
  `.codex-runs/06-dispute-2-test.log`. Compilation and test execution remain
  unverified because of this environment blocker.
- `git diff --check` passed. No production source was read or modified,
  no dependencies were installed or copied, and no drives were mapped.
