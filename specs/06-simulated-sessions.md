# Spec 06 — Simulated sessions

## Purpose

Make a filled form reproducible. In an ordinary session (spec 01), the
engine reads the real clock (`today()`, `now()`, the `start` and `end`
timestamps) and real randomness (`random()`, `uuid()`, the `instanceID`).
Two simulations with the same seed would then differ, and every
submission would be dated on the day the simulation ran. A **simulated
session** takes its time from a simulated clock and its randomness from a
seed.

Language: Java. Package `ctosim`.

## Public API

```java
public final class SimulatedClock {
    public SimulatedClock(java.time.LocalDateTime start);
    public java.time.LocalDateTime now();
    public void advance(java.time.Duration amount);
}

// added to LoadedForm (spec 01):
public org.javarosa.form.api.FormEntryController newSimulatedSession(SimulatedClock clock, long seed);
```

## SimulatedClock

- `now()` is `start` until the clock is advanced; `advance(d)` moves it
  forward by `d`. Durations are kept to the nanosecond.
- `advance` with a negative duration throws `IllegalArgumentException` and
  leaves the clock unchanged; a zero duration is allowed.
- `new SimulatedClock(null)` and `advance(null)` throw
  `NullPointerException`.
- The clock only moves when `advance` is called; it never reads the real
  time.

## Simulated sessions

`newSimulatedSession(clock, seed)` returns a session like
`newEntryController()` (spec 01: positioned at the beginning, own
initialised instance, SurveyCTO functions available), with these
differences. They apply to the form's own expressions (`bind` attributes
and `setvalue` values) and to its metadata preloads.

### Time

Conversions between the clock's local date-time and the engine's date
values use the JVM's default time zone, so the engine shows the clock's
local date and time.

| In the form | In a simulated session |
|---|---|
| `today()` | the clock's current date (at the time of evaluation) |
| `now()` | the clock's current date-time |
| `duration()` | number: whole seconds elapsed on the clock since the session was opened (rounded down) |
| `start` timestamp preload (`jr:preload="timestamp" jr:preloadParams="start"`) | the clock's date-time when the session is opened |
| `end` timestamp preload | the clock's date-time when the instance is post-processed (`FormDef.postProcessInstance()`); unset before |

Values are read from the clock when the engine evaluates them: if the
clock is advanced and an expression using `now()` is then recalculated,
it sees the new time.

### Randomness

| In the form | In a simulated session |
|---|---|
| `random()` | a number in [0, 1) drawn from the session's random sequence |
| `uuid()` | a random identifier drawn from the session's random sequence, formatted as a version-4 UUID: 36 lowercase characters `xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx`, with `y` one of `8`, `9`, `a`, `b` |
| `uid` preload (`instanceID`) | `uuid:` followed by an identifier formatted as for `uuid()`, drawn from the session's random sequence |

The session's random sequence is determined by `seed` alone.

### Reproducibility

Two simulated sessions of the same loaded form (or of two loads of the
same form) opened with clocks at the same start and the same seed, and
then driven identically (same answers, same clock advances, same
navigation, in the same order), have identical instances: every node has
the same value, including `start`, `end`, `instanceID`, and every field
computed with `today()`, `now()`, `duration()`, `random()` and `uuid()`.

With different seeds, the values computed with `random()` and `uuid()`
and the `instanceID` differ (with overwhelming probability).

`once(random())` keeps its native meaning: the value is drawn the first
time and kept afterwards.

### What does not change

- Ordinary sessions (`newEntryController()`) keep the native behaviour
  described in spec 01 for these functions and preloads.
- Sessions do not share state: answers, clocks and random sequences of one
  session never affect another session, simulated or not.
- `stubbedFunctions()` is unchanged (`today`, `now`, `random`, `uuid` are
  native functions; `duration` remains listed when the form uses it,
  because ordinary sessions still stub it).

## Errors

`newSimulatedSession(null, seed)` throws `NullPointerException`.
