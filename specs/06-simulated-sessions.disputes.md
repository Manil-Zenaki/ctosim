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
