# Spec 14 — Injected problems and their answer key

## Purpose

Data-quality checks (IPA's `ipacheck` and teams' own do-files) are
tested and taught on data whose problems are known. The fieldwork plan
(spec 13) gives data in which these checks find no problem. This module
puts problems into the plan **on purpose**, and lists each one in an
**answer key**, so that a user can compare what the checks found with
what was put there.

This spec covers the problems of the fieldwork itself: duplicate
submissions, abnormal durations, interviews outside working hours,
overlapping interviews and impossible travel. (Problems in the answers
themselves, such as faulty enumerators and outliers, are a later
amendment.)

Problems are injected **only when the configuration asks for them**
(spec 04, Amendment 6). Without that setting, the plan and its results
are exactly those of spec 13.

Every problem is one that could happen with SurveyCTO Collect: the
submissions are still walked through the form, so relevance,
constraints and calculations hold in them as in any other submission.

Language: Java. Package `ctosim`.

## Public API

```java
public record InjectedProblem(
        int submission,      // number of the submission that carries the problem
        String key,          // that submission's KEY (its instanceID)
        String enumerator,   // that submission's username (spec 07, Amendment 1)
        String family,       // "duplicate", "duration", "schedule" or "travel"
        String problem,      // see "Problems"
        String check,        // the check expected to find it, see "Problems"
        String field,        // the field concerned, "" when none
        String value,        // the value it holds in the submission, "" when none
        String cleanValue,   // the value it would hold without the problem, "" when none
        String relatedKey,   // KEY of the other submission involved, "" when none
        boolean deterministic) { }

public final class AnswerKey {
    public static String csv(java.util.List<InjectedProblem> problems);
}

// added to Fieldwork:
public java.util.List<InjectedProblem> problems();
```

## Problems

When `config.problems()` (spec 04, Amendment 6) is present, `walkAll()`
injects the following problems. `N` is `config.submissions()` and `E`
is `config.enumerators()`. Each problem is carried by one submission,
its **target**; the table gives, for each, the record's `family`,
`problem` and `check`, and whether it is deterministic.

| `problem` | `family` | `check` | deterministic | rate (spec 04, Amendment 6) |
|---|---|---|---|---|
| `resent` | `duplicate` | `ipacheckids` (`ipacheckdups` when the configuration has no `id`) | yes | `resent` |
| `reinterview` | `duplicate` | `ipacheckids` | yes | `reinterview` |
| `short` | `duration` | `ipacheckoutliers` | no | `shortDuration` |
| `long` | `duration` | `ipacheckoutliers` | no | `longDuration` |
| `offhours` | `schedule` | `ipachecktimeuse` | yes | `offHours` |
| `overlap` | `schedule` | `custom` | yes | `overlap` |
| `travel` | `travel` | `custom` | yes | `travel` |

A deterministic problem is one that a check comparing exact values
finds every time; a check of the other kind (an outlier test) may also
flag submissions that carry no problem, and may miss one.

### How many, and which submissions

For each problem of the table, its **count** is its rate times `N`,
rounded half up, and at least 1 when the rate is above 0.

The targets are drawn from `config.seed()` and `N` only (not from the
answers), uniformly and without replacement, problem by problem in the
order of the table. A submission is the target of at most one problem.
The targets of every problem except `short` and `long` are drawn among
the submissions numbered above `E` (each enumerator's first interview
carries no such problem); those of `short` and `long` among all
submissions.

When fewer eligible submissions remain than a problem's count, that
problem gets as many targets as remain, and `warnings()` holds a warning
that names the problem and gives the count asked for and the number
injected.

### The original of a duplicate

A `resent` or `reinterview` target `n` has an **original**: a
submission `m` lower than `n`, drawn from `config.seed()` and `n`,
uniformly among the submissions below `n` that are no problem's target
and no other duplicate's original. When there is none, the target is
walked as an ordinary visit of the plan, carries no problem, and
`warnings()` holds a warning that names the problem and the submission.

### `resent`: the same submission sent twice

Submission `n` is a copy of its original `m`: the same instance except
its `instanceID`, which is the target's own (as `walk(n, …)` would give
it); the same `start()`, `end()` and username; an upload time
(`submitted()`) later than the original's by a whole number of minutes
drawn between 1 and 120; no warnings of its own. It takes no place in
the plan: the enumerator of visit `n` is not delayed by it, and the
enumerator's next interview follows its previous one as if submission
`n` did not exist. `visits()` holds, at position `n`, the original's
visit.

Its record: `field` is the configuration's `id` and `value` its value in
the submission when the configuration names an id; otherwise both are
`""`. `relatedKey` is the original's KEY.

Since the copy has the same times as its original, the two also overlap;
checks of overlap are expected to be run once duplicates are handled.

### `reinterview`: the same household interviewed again

Submission `n` is an ordinary visit of the plan, walked as usual, except
that it gets its original's survey id: the visit's `id()` is the
original's, and when the id is given at the question (spec 13,
Amendment 2) the id question and its upper questions (spec 13,
Amendment 3) are answered as in the original.

Its record: `field` is the configuration's `id`, `value` the id, and
`relatedKey` the original's KEY.

When the configuration names no id, or the original has no value for
it, no `reinterview` is injected: the target is walked as an ordinary
visit, and `warnings()` holds one warning that names the problem. When
the engine does not accept the original's id in the target, the target
keeps the answer it gets as usual, carries no problem, and `warnings()`
holds a warning that names the submission.

### `short` and `long`: abnormal durations

The target is walked as its ordinary visit, with the same answers, but
every advance of the simulated clock after a question (spec 07, rule 7
of "Questions") is multiplied by a factor `f` drawn from
`config.seed()` and the submission number, and rounded to the nearest
whole second (at least 1 second): `f` is uniform between 0.15 and 0.30
for `short`, between 3 and 6 for `long`. A question's minimum time
(spec 07, Amendment 1) still applies to the multiplied advance.

The submission's answers are those it would have without the problem,
except the values that depend on the clock (times, `duration()`).

Its records: one for each field of the form whose calculation is
`duration()` and that holds a value in the submission, in the form's
order, with `value` its value and `cleanValue` the value it holds when
the same visit is walked without the problem. When the form has no such
field, one record with `field` `""`, `value` the whole seconds from
`start()` to `end()`, and `cleanValue` the same without the problem.

### `offhours`: an interview outside working hours

The target is an extra interview of its enumerator at the end of the
day of the enumerator's previous interview: it starts at a time drawn
from `config.seed()` and its number, uniformly between two and four
hours after the end of work of that day (spec 13, "When"), and at least
ten minutes after the end of the previous interview. It takes place in
that day's village (spec 13, "Where"). The enumerator's next interview
is the first of its next day.

When that start would fall within the next day's working hours, the
target is walked as an ordinary visit, carries no problem, and
`warnings()` holds a warning that names the submission.

Its record: see "Start records" below; `relatedKey` `""`.

### `overlap`: two interviews at once

The target is an interview of another household (its own survey id, as
an ordinary visit) that starts before the end of its enumerator's
previous interview: at the previous interview's start plus a number of
whole seconds drawn from `config.seed()` and the target's number,
uniformly between 0 and the previous interview's duration minus five
minutes, so that the two interviews share at least five minutes. It is
on the same day and in the same village as the previous interview. The
enumerator's next interview follows the later of the two ends, as in
spec 13.

When the previous interview lasts less than five minutes, the target is
walked as an ordinary visit, carries no problem, and `warnings()` holds
a warning that names the submission.

Its record: see "Start records" below; `relatedKey` the previous
interview's KEY.

### Start records

The record of an `offhours` or `overlap` problem names the time the
interview started: one record for each field of the form of type
`start`, in the form's order, with `field` its name and `value` its
value as written in the wide export (spec 08). When the form has no such
field, one record with `field` `""` and `value` the target's `start()`
written `yyyy-MM-ddTHH:mm:ss`. `cleanValue` is `""`.

### `travel`: an impossible trip

Travel can only be seen in location answers. When the form has no
geopoint, geotrace or geoshape question, no `travel` problem is
injected, and `warnings()` holds one warning that says so.

The target is the enumerator's next interview on the same day as its
previous one: it starts ten to forty minutes (a whole number, drawn as
in spec 13) after the end of the previous interview, whatever the daily
quota. It takes place at a point drawn from `config.seed()` and its
number, uniformly among the points of the area (spec 13, "Where") whose
distance to the previous interview's point is at least
`max(20 km, 2 km × g)`, where `g` is the number of minutes between the
end of the previous interview and the start of the target. The
enumerator's next interview is the first of its next day, in its new
village.

When that start would be after the end of work, or when no point of the
area is far enough, the target is walked as an ordinary visit, carries
no problem, and `warnings()` holds a warning that names the submission.

In the target, location questions are not left blank by the missing
rate. Its records: one for each location question answered in the
submission, in the form's order, with `value` its answer as written in
the wide export and `relatedKey` the previous interview's KEY.

### Everything else

A submission that is no target is walked as in spec 13, at the place
the plan gives it once the targets before it are placed. The plan's
guarantees of spec 13 (no overlap, starts within working hours, one
village a day, distinct survey ids) hold for every submission that is
neither a target nor, for an overlap or a resent copy, the target's
related submission.

### Warnings

The warnings of this module come in `warnings()` after those of
spec 13, in the order of the table, and for each problem by submission
number.

## `problems()`

`problems()` holds the records of the last call of `walkAll()`: one or
more per injected problem, as described above, ordered by submission
number, then in the order given for that problem. It is empty before
the first call and when `config.problems()` is empty. Every record
describes a problem that is in the returned submissions: `key`,
`enumerator`, `value` and `relatedKey` are those of the submissions.

## Answer key

`AnswerKey.csv(problems)` returns the answer key as CSV text, with the
CSV rules of spec 08 ("CSV text"), a header row and one row per record,
in the list's order. The columns, in this order:

| Column | Content |
|---|---|
| `KEY` | `key` |
| `enumerator` | `enumerator` |
| `family` | `family` |
| `problem` | `problem` |
| `check` | `check` |
| `field` | `field` |
| `value` | `value` |
| `clean_value` | `cleanValue` |
| `related_KEY` | `relatedKey` |
| `kind` | `deterministic` or `statistical` |

An empty list gives the header row alone. A null list or record throws
`NullPointerException`.

## Randomness

All draws come from `config.seed()`, as in spec 13. The same form and
configuration give equal submissions, visits, warnings and problems on
every call of `walkAll()`, whatever the machine's locale or time zone.
Lists returned by this module are unmodifiable.
