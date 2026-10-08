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
