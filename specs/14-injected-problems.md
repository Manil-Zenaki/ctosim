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

Since the copy has the same times as its original, the two also overlap
in time. This is not an `overlap` problem: a duplicate is two
submissions with the same survey id, whatever their times, while an
overlap is two interviews of one enumerator with different survey ids
whose times overlap.

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

## Clarification

Assumptions 1–14 of the questions file are reviewed one by one. Decisions
on the points left open:

- **Geopoint value** (1): the `value` of a geopoint's record is its
  answer as the instance holds it: latitude, longitude, altitude and
  accuracy separated by single spaces (spec 05), the same numbers as its
  four wide-export columns.
- **Other clean values** (2): `cleanValue` is `""` in every record but
  those of `short` and `long`.
- **Duration fields** (4): a field counts when its calculation is
  exactly `duration()` (white space aside); `once(duration())` and other
  expressions do not. When the form has such fields but none holds a
  value in the target, the record is the one for a form without such a
  field.
- **Minimum time** (5): the drawn advance is multiplied by `f` and
  rounded (half up, at least 1 second), then a question's minimum time
  applies to the result.
- **Count warnings** (7): for a problem, the warning about a short count
  comes before its per-submission warnings.
- **A short overlapping interview** (10): the target is not lengthened;
  when it lasts less than five minutes, the two interviews share its
  whole duration.

Also confirmed: instance document order is the form's order (3); every
target of every problem is reserved before originals are drawn, and a
target that falls back is not reused (6); one configuration-wide warning
when no id is configured or no location question exists (7); a fallback
is an ordinary visit, and a duration's clean value is that of the same
visit walked without the problem (8); the unit of the off-hours draw is
not prescribed, and the stated boundaries are inclusive (9); a
reinterview's visit carries the original's id, and its cascade answers
are copied (11); the next interview after travel is only required to be
the first of the next day, and area overrides, location questions in
repeats or all irrelevant are not prescribed (12); no random sequence is
prescribed (13); no constructor validation and no lifetime for earlier
lists (14).

## Amendment 1: faulty enumerators and outliers

Phase 2b puts problems into the **answers**. Some enumerators are
**faulty**: one goes too fast, one says "No" to filter questions to skip
modules, one abuses "don't know" codes, on all their interviews, as data
quality checks that compare enumerators (`ipacheckenumdb`) expect. And
some submissions get an **outlier**: a number far from the others but
accepted by the form's constraint. Everything not mentioned here is
unchanged.

The configuration (spec 04, Amendment 7) adds an `outliers` rate and the
list of `faults` to inject; both are given by `config.problems()`.

### New problems

| `problem` | `family` | `check` | deterministic |
|---|---|---|---|
| `outlier` | `outlier` | `ipacheckoutliers` | no |
| `tooFast` | `enumerator` | `ipacheckenumdb` | no |
| `skipModules` | `enumerator` | `ipacheckenumdb` | no |
| `dontKnow` | `enumerator` | `ipacheckenumdb` | no |

`outlier` comes after `travel` in the table of "Problems": its targets
are drawn after those of `travel`, by the rules of "How many, and which
submissions", among all submissions (as for `short` and `long`), with
the rate `outliers`. The faults are not drawn per submission: they are
carried by enumerators.

A submission may carry problems of both kinds: an outlier or a fault on
a submission that is the target of a problem of the original table, a
fault and an outlier on one submission. A `resent` copy stays an exact
copy of its original and carries no record but its `resent` record.

In every record, `cleanValue` is the value the field would hold without
that problem, the other problems of the submission kept.

### Faulty enumerators

Let `F` be the faults of `config.problems()`, in the order `tooFast`,
`skipModules`, `dontKnow`, and `m` = min(|F|, floor(E / 2)), so that at
least half of the enumerators stay clean. `m` enumerators are drawn from
`config.seed()` and `E` only, uniformly without replacement; fault number
`k` of `F` (counting from 0) is carried by enumerator number `k mod m`
of those drawn (counting from 0, in the order drawn). A faulty enumerator
applies its faults to every one of its submissions (except `resent`
copies).

`warnings()` holds, after the warnings of the original table and before
those of outliers:

- when `m` is smaller than the size of `F`: one warning saying that
  faults are cumulated on the same enumerators, and giving the number of
  enumerators needed for one enumerator per fault (twice the size of `F`);
- when `m` is 0 and `F` is not empty: one warning saying that no fault
  was injected, since one enumerator cannot be compared with others;
- for each faulty enumerator with fewer than 10 submissions: one warning
  naming it and giving its number of submissions, too few for checks to
  tell it from the others.

#### `tooFast`

Every advance of the simulated clock after a question is multiplied by
0.4 (and by the factor of `short` or `long` too, when the submission is
also such a target), rounded as in "`short` and `long`", before the
question's minimum time applies; a speed violation is recorded with
probability min(1, 3 × `config.speedViolationRate()`) instead of
`config.speedViolationRate()`. Records: as for `short` and `long`.

#### `skipModules`

A **module** is a group or repeat of the form holding at least three
questions, at any depth. A `select_one` question **controls** a module
when, at the moment the walker answers it, two of the choices it offers
are such that with one the module is relevant and with the other it is
not, the other answers being as they are.

When a faulty enumerator answers a question that controls modules, the
walker draws its answer `a` as usual. Let `O` be the modules the
question controls that are relevant with `a`. When `O` is not empty,
with probability 0.5 (drawn from `config.seed()` and the submission
number, apart from the answers' own draws) the answer is replaced by a
choice drawn uniformly among the offered choices, other than the
configuration's special values, with which no module of `O` is
relevant; when there is none, `a` is kept. Record (one per replaced
answer): `field` the question's name, `value` the answer given,
`cleanValue` `a`.

The same fault shortens rosters: a faulty enumerator declares fewer
instances of a repeat, with the same probability 0.5 (drawn the same
way):

- when it answers a question whose answer sets the number of instances
  of a repeat (`jr:count`), with `a` the count drawn as usual, the answer
  is replaced by a whole number drawn uniformly between `low` and
  `a − 1`, where `low` is the smallest whole number of at least 1 that
  the question's constraint hints (spec 09) allow; when `a` is not
  above `low`, `a` is kept. The engine may refuse the drawn number: `a`
  is then kept. Record: `field` the question's name, `value` the number
  given, `cleanValue` `a`;
- for a repeat whose number of instances the enumerator controls (spec
  07), with `a` the number drawn as usual, the enumerator stops after a
  number drawn the same way. Record: `field` the repeat's name, `value`
  the number of instances created, `cleanValue` `a`.

#### `dontKnow`

When a faulty enumerator answers a question whose answer `a` drawn as
usual is not blank, with probability 0.25 (drawn as for `skipModules`)
the answer is replaced by a "don't know / refused" code, among the
configuration's `specialValues`:

- for a `select_one` or `select_multiple` question: one of the special
  values among the offered choices, drawn uniformly (alone, for a
  `select_multiple`);
- for an integer or decimal question: one of the special values that are
  numbers and that the engine accepts, drawn uniformly;
- otherwise, or when there is none, `a` is kept.

Record (one per replaced answer that differs from `a`): `field` the
question's name, `value` the code, `cleanValue` `a`.

#### Questions left alone

The faults and outliers never change: the survey id question and the
upper questions of its cascade (spec 13), answers given by a
`reinterview`, and read-only questions and calculations. Questions whose
answer sets the number of instances of a repeat are changed only by
`skipModules`, as above, never by `dontKnow` or an outlier.

### Outliers

In an `outlier` target, one integer or decimal question gets a value far
from those the simulator draws for it, but accepted by its constraint.

For a question, let `D` be the distribution the walker draws its answers
from: uniform on its drawing range (spec 05 and spec 09, "Integer and decimal"), or
the `normal` or `lognormal` distribution of its override (spec 04,
Amendment 3), ignoring any bounds. Its **upper fence** is the larger of
`Q3 + 1.5 × (Q3 − Q1)` and `mean + 3 × sd`, its **lower fence** the
smaller of `Q1 − 1.5 × (Q3 − Q1)` and `mean − 3 × sd`, where `Q1`, `Q3`,
`mean` and `sd` are those of `D`.

A question of the target is **eligible** when the walker answers it as
usual (it is not left alone, above), it is relevant, and its
constraint's hints (spec 09) allow a value at or above its upper fence
or at or below its lower fence. The target is first walked as usual; one
eligible question (its first instance) is drawn uniformly, from
`config.seed()` and the submission number; the target is then walked
again with that question answered by an **outlier value**, drawn
uniformly (whole numbers for an integer question):

- in [upper fence, upper fence + (upper fence − median)], within the
  hints; when that is empty,
- in [lower fence − (median − lower fence), lower fence], within the
  hints.

The engine may still refuse it: after 20 refused draws the question is
answered as usual, and `warnings()` holds a warning naming the
submission and the question. When the target has no eligible question,
it carries no outlier, and `warnings()` holds a warning naming the
submission.

Record: `field` the question's name, `value` the outlier value,
`cleanValue` the answer drawn as usual.

### Order

The records of a submission come in the order of the problems' table
(the original one, then `outlier`, `tooFast`, `skipModules`,
`dontKnow`), and within a problem in the form's order of their fields.
Clarification (Amendment 1): assumptions A1–A9 of the questions file are
reviewed one by one. Decisions on the points left open:

- **Outlier targets** (A1): `outlier` is a problem of the table like the
  others: its targets are drawn without replacement after those of
  `travel`, so a submission is the target of at most one problem of the
  table, outlier included, and an outlier target is never a duplicate's
  original. The sentence on submissions carrying problems of both kinds
  concerns faults, which apply to any submission of a faulty enumerator.
- **One enumerator** (A2): when `m` is 0, only the warning that no fault
  was injected is given, not the cumulation warning. A faulty enumerator
  with no submission gets the small-sample warning (0 submissions); the
  count of `resent` copies is not prescribed.
- **Several problems on one answer** (A6): a question that gets an
  outlier value is not changed by `dontKnow`; an answer replaced by
  `skipModules` is not changed again by `dontKnow`. The rule on
  `cleanValue` applies to the records of this amendment; the records of
  the original table keep theirs.
- **Outlier distributions** (A7): the quartiles of a uniform
  distribution are those of the continuous uniform distribution on the
  drawing range, for integer questions too. A question is not eligible
  when its `D` has a standard deviation of 0, when its answer drawn as
  usual is blank, or when its override has a `value`, an `expr` or
  rules (`when`).

Also confirmed: fault assignment follows the order `tooFast`,
`skipModules`, `dontKnow`, whatever the configuration's order (A3); an
answer fixed by an override can be replaced by a fault (A4); without a
count question, `low` is 1 and the configured repeat range does not
apply to shortening (A5); the twenty refusals are observed through the
fallback (A8); no random sequence is prescribed (A9).

## Amendment 2: consent refusals, rosters, and outliers that respect the real constraint

Checking Amendment 1 on the IPA household form showed three gaps: the
faulty enumerator of `skipModules` mostly answered "No" to consent (a
fake refusal, a fault of its own); rosters built by an "add another
member?" question were never shortened; and outliers were rarely placed,
because many interviews had no eligible question and because constraints
that depend on another answer (`. <= ${first_income}`) refused the values
drawn. Everything not mentioned here is unchanged.

### A fourth fault: `fakeRefusal`

| `problem` | `family` | `check` | deterministic |
|---|---|---|---|
| `fakeRefusal` | `enumerator` | `ipacheckenumdb` | no |

It comes after `dontKnow` in the order of faults (and of the problems'
table). It needs consent questions (spec 07, Amendment 11): when the
configuration names none, no `fakeRefusal` is injected, the fault takes
no enumerator, and `warnings()` holds one warning that says so, among
the faults' warnings.

A faulty enumerator turns a submission whose respondent would consent
(spec 07, Amendment 11) into a refusal with probability
min(1, (1 − r) / r), where `r` is the consent rate: on average its
refusals are twice those of the others (with `r` 0.9, it gets about 80 %
consents instead of 90 %). The draw comes from `config.seed()` and the
submission number, apart from the answers' own draws. The refusal is
given as spec 07 Amendment 11 gives a refusal. Record: `field` the last
consent field, `value` the configuration's refusing value, `cleanValue`
its consenting value.

`skipModules` never changes a consent question.

### Rosters built by "add another" questions

A `select_one` question inside a repeat **extends** that repeat when the
repeat's number of instances (its `jr:count`) depends on the question's
answers, directly or through calculations, and, at the moment the walker
answers it, two of the choices it offers give different numbers of
instances.

When a faulty enumerator of `skipModules` answers such a question with
an answer `a` drawn as usual that gives more instances than another
offered choice, with probability 0.25 (drawn as for `skipModules`) the
answer is replaced by a choice, drawn uniformly among the offered ones
other than the configuration's special values, that gives the fewest
instances. Record (one per roster the fault shortened, not one per answer):
`field` the repeat's name, `value` the number of instances of the
repeat at the end of the walk, `cleanValue` that number when the same
visit is walked without this fault's roster shortening.

### Roster shortening is less frequent

The probability with which `skipModules` declares fewer instances, for
a count question and for a repeat whose number of instances the
enumerator controls (Amendment 1), is 0.25 instead of 0.5. The
probability of answering a filter question so that its modules stay
closed is unchanged (0.5).

### Outliers within the real constraint

The **outlier interval** of a question is the interval of Amendment 1
("in [upper fence, ...], within the hints; when that is empty, in
[lower fence − ..., lower fence], within the hints"). A question is now
eligible only when, at the moment the walker answers it, the engine
accepts at least one value of its outlier interval, with the other
answers as they are; its constraint is so taken into account even when
it depends on other answers. The outlier value is a value of the
interval that the engine accepts then, drawn from `config.seed()` and
the submission number.

The eligible questions of the target (its walk as usual) are tried in an
order drawn uniformly: the target is walked again with the first one
answered by an outlier value; when the engine accepts no value of its
interval in that walk, with the next one; and so on. The first walk
that gives an outlier value is the submission.

When no question of the target gets an outlier value, the outlier
**moves** to the next submission, in number order, that is the target
of no problem of the table and to which no outlier has moved, and is
tried there the same way. When no later submission can take it,
`warnings()` holds one warning naming the submission where it was
drawn. The warning about 20 refused draws of Amendment 1 is no longer
given.

Clarification (Amendment 2): assumptions B1–B10 of the questions file
are reviewed one by one and confirmed, with decisions on the points left
open: the earlier tests of the half-probability roster shortening and of
the twenty-refusal warning are superseded and go through a dispute (B1);
a fake refusal that the last consent field cannot take gives no record,
and conflicts on a consent answer are not prescribed (B3); the 0.25 draw
is made at each answer that could extend the roster, with one record per
roster (B6); an outlier interval is the upper one unless the hints leave
it empty, as in Amendment 1, whatever the engine accepts (B7); outliers
that could not be placed move in the order of their targets' numbers
(B9). Also confirmed: rates at or below one half turn every interview of
the faulty enumerator that would consent into a refusal, and rate 0
gives none (B2); an inactive `fakeRefusal` takes no enumerator and gives
one warning among the faults' (B4); the bootstrap of a roster whose count
refers directly to a question of the same repeat is not prescribed (B5);
retries are observed through their outcomes (B8); no random sequence is
prescribed (B10).

## Amendment 3: a form left open

An enumerator opens a form, is interrupted, does another interview, and
comes back to finish the first one. In SurveyCTO data, `starttime` is the
first opening and `endtime` the finalisation, while `duration` counts
only the time the form was on the screen (spec 07, Amendment 12): the
interval from start to end is far longer than the duration, and it
contains the other interview. Everything not mentioned here is
unchanged.

### The problem

| `problem` | `family` | `check` | deterministic | rate (spec 04, Amendment 9) |
|---|---|---|---|---|
| `leftOpen` | `schedule` | `custom` | yes | `leftOpen` |

`leftOpen` comes after `outlier` in the table of "Problems": its targets
are drawn after those of `outlier`, by the rules of "How many, and which
submissions", among all submissions. It is a problem of the table: a
submission is the target of at most one problem of the table.

### The interruption

Let `A` be the target and `D` the screen duration (spec 07, Amendment
12) that `A` has when its visit is walked without the problem. A
fraction `f` is drawn uniformly between 0.2 and 0.8 from
`config.seed()` and the target's number. `A` is walked as its visit,
with one pause (spec 07, Amendment 12) after the first question whose
handling brings the screen time to at least `f × D`. Let `t` be the time
the pause begins.

**The englobed interview.** Let `B` be the enumerator's next submission
in number order that is not a `resent` copy. When `B` exists and is the
target of none of `offhours`, `overlap`, `travel` and `leftOpen`:

- `B` starts at `t` plus a whole number of minutes drawn uniformly
  between 10 and 40, when that is not after the end of work of the day
  of `A`; otherwise (the form stays open overnight) `B` is the first
  interview of the enumerator's next working day, as in spec 13 "When"
  (start of work plus 0 to 60 minutes), in that day's village;
- `B` is walked as an ordinary visit with that start (and, on the same
  day, in the village of `A`), carrying its own problems if any;
- the pause of `A` ends at the end of `B` plus a whole number of
  minutes drawn uniformly between 10 and 40, from `config.seed()` and
  the target's number.

**Without an englobed interview** (no such `B`): the pause of `A` ends at
the start of work of the next day after the day of `A`, plus a whole
number of minutes drawn uniformly between 0 and 60.

`A` then finishes its questions after the pause. The enumerator's next
interview after `A` (after `B`, when there is one) follows the end of
`A` as in spec 13, on the day `A` ended.

So `A` starts within working hours, its interval from start to end
contains `B` entirely, and `end() − start()` exceeds its duration by the
length of the pause. No `leftOpen` target is skipped: the problem is
always injected.

### Records

As "Start records" (one per field of type `start`, or one record with
`field` `""`), with `relatedKey` the KEY of `B`, or `""` without an
englobed interview.

Clarification (Amendment 3): assumptions C1–C6 of the questions file are
reviewed one by one and confirmed: the pause comes after the handling of
its question and before the next control, a one-question form pausing
after its only question (C1); a target with no screen time, or whose
pause would begin after its next-day resumption, is not prescribed (C2);
a submission drawn for a schedule or travel problem is excluded as `B`
even when that problem could not be injected, the defined next interview
is the only candidate, and an unsuccessful `resent` target's role is not
prescribed (C3); `B` is checked only against the end of work, not the
daily quota, and `A` keeps its visit's point (C4); no random sequence is
prescribed (C5); the clean duration removes `leftOpen` only (C6).

## Amendment 4: impossible travel through located villages

Until now an impossible trip could only be seen in GPS answers. With
the villages located in the preloaded list (spec 13, Amendment 4), it can
be seen without GPS: an interview declared in a village too far from the
village of the enumerator's previous interview to be reached in the time
between them, as when an interview is made up. Everything not mentioned
here is unchanged.

### `travel` through the villages

When the form has a village question and `config.villages()` is present
(spec 13, Amendment 4), the `travel` target is planned as in "`travel`:
an impossible trip" (same day as the previous interview, ten to forty
minutes after its end, the next interview being the first of the next
day), except where it takes place:

- its place (spec 13, Amendment 4) is answered with a candidate drawn
  from `config.seed()` and its number, uniformly among the candidates
  whose village is located and at least `max(20 km, 2 km × g)` from the
  coordinates of the previous interview's village, `g` being the number
  of minutes between the end of the previous interview and the start of
  the target;
- it takes place at a point within 1.5 km of that village's coordinates
  (spec 13, Amendment 4), so its location answers, if the form has any,
  are far from the previous interview's too;
- it is not a move: the enumerator's place of the day is unchanged, and
  the time of a move does not apply.

This holds whether or not the form has a geopoint, geotrace or geoshape
question. When the previous interview's village is not located, or no
candidate is far enough, the target is walked as an ordinary visit,
carries no problem, and `warnings()` holds a warning that names the
submission. A form with location questions but no village question, or
without `config.villages()`, keeps the rule of "`travel`: an impossible
trip". When the form has neither location questions nor a village
question with `config.villages()`, no `travel` problem is injected, and
`warnings()` holds one warning that says so.

Its records: one for the village question, with `value` the target's
village answer, as written in the wide export, and `cleanValue` the
previous interview's; then one for each location question answered in
the submission, as in "`travel`: an impossible trip"; all in the form's
order, each with `relatedKey` the previous interview's KEY.

### `reinterview` and the village of the day

A `reinterview` target answers the place of its original (the id
question's upper questions, as in "`reinterview`"). So that this does not
look like a trip:

- when the target is not its enumerator's first interview of its day
  and the form has a village question, its original is drawn as in "The
  original of a duplicate", but among the eligible submissions whose
  village answer is the enumerator's village of the day, when there is
  at least one; otherwise among all eligible submissions, as before;
- when the target is its enumerator's first interview of its day, the
  original's place is the enumerator's place of the day.

The target is not a move (spec 13, Amendment 4) even when its village
differs from the village of the day; the village of the day is then
unchanged.

Clarification (Amendment 4): assumptions D1–D6 of the questions file are
reviewed one by one and confirmed: the records follow the form's order,
only the village record has a `cleanValue`, and the previous interview is
the enumerator's actual predecessor, even a travel target (D1); the
candidates are those of spec 13 Amendment 4, drawn uniformly whatever
the choices' weights (D2); with a village question and `villages`, an
unlocated previous village or no far candidate gives an ordinary visit,
never the earlier GPS rule (D3); a first interview of the day that is a
reinterview sets the place of the day, and the village preference of a
reinterview needs a village question only, not coordinates (D4); that a
trip is not a move is observed through its consequences (D5); no random
sequence is prescribed (D6).

## Amendment 5: "don't know" codes named by the user

Trying the simulator on teams' forms showed that a code drawn from the
configuration's `specialValues` can mean something else in a form ("97"
is often "Other"), or be a code the form never uses: a numeric "don't
know" then makes a later question impossible to answer (`. <= ${B04}`
after `B04` = `-888`). The simulator cannot tell what a code means, so
the user names the team's codes. Everything not mentioned here is
unchanged.

The fault `dontKnow` (Amendment 1) draws its codes from
`config.dontKnowCodes()` (spec 04, Amendment 11) instead of
`specialValues`, with the same rules otherwise: for a `select_one` or
`select_multiple` question, one of those codes among the offered choices;
for an integer or decimal question, one of those codes that are numbers
and that the engine accepts; otherwise, or when there is none, the
answer drawn as usual is kept.

When `config.dontKnowCodes()` is empty, no `dontKnow` is injected, the
fault takes no enumerator, and `warnings()` holds one warning that says
so, among the faults' warnings, as for `fakeRefusal` without consent
questions (Amendment 2).

`specialValues` keeps its other uses, unchanged (among them the ordinary
special answers of spec 05, and the choices a shortened roster avoids,
Amendment 2).

Clarification (Amendment 5): assumptions E1–E6 of the questions file are
reviewed one by one and confirmed: an omitted key gives an empty list, and
earlier tests that relied on `specialValues` for `dontKnow` are superseded
and go through a dispute (E1); a fault without codes is removed before
enumerators are assigned, as `fakeRefusal` without consent questions, while
a non-empty list stays active even when no question can use it (E2);
numeric codes are compared as numbers and never rounded, the engine
accepting the code for the question answered, with no look-ahead at later
questions (E3); the warning is identified by `dontKnow`, in the faults'
warnings, without prescribed wording (E4); `dontKnowCodes` and
`specialValues` are independent lists that may overlap (E5); no random
sequence is prescribed, and `cleanValue` comes from the same visit walked
without the fault (E6).

## Amendment 6: refused answers, a fault of their own

Teams code "don't know" and "refused to answer" with distinct values, and
`ipacheckenumdb` reports a "don't know" rate and a refusal rate per
enumerator. `dontKnow` (Amendment 5) uses the "don't know" codes only
(spec 04, Amendment 12); a fifth fault abuses refusals. Everything else is
unchanged.

| `problem` | `family` | `check` | deterministic |
|---|---|---|---|
| `refuse` | `enumerator` | `ipacheckenumdb` | no |

It comes after `fakeRefusal` in the order of faults (and of the problems'
table), so that the enumerators of the four earlier faults are unchanged
when it is not injected.

A faulty enumerator of `refuse` answers as one of `dontKnow` (Amendments
1 and 5), with `config.refuseCodes()` in place of `config.dontKnowCodes()`:
probability 0.25 per answer, drawn apart from `dontKnow`'s draws, the same
eligible questions and the same rules for select and numeric questions.
Its records have `problem` `refuse`. When one enumerator carries both
faults, `dontKnow` applies first; `refuse` applies only to an answer that
`dontKnow` left unchanged.

When `config.refuseCodes()` is empty, no `refuse` is injected, the fault
takes no enumerator, and `warnings()` holds one warning that says so,
among the faults' warnings, as for `dontKnow` without codes.

Clarification (Amendment 6): assumptions F1–F7 of the questions file are
reviewed one by one and confirmed: only an empty `refuseCodes()` disables
the fault, removed before enumerators are assigned, while a non-empty list
stays active even when no question can use it (F1); "left unchanged" means
the answer itself, per answer, so a "don't know" draw that finds no usable
code or gives the answer already there does not block `refuse` (F2);
`refuse`'s draws are independent of `dontKnow`'s, adding `refuse` does not
change `dontKnow`'s draws, and no random sequence is prescribed (F3); the
eligible questions and the protections of `dontKnow` all carry over,
repeat counts and the answers that control them, reinterview copies,
resent submissions and answers already changed by `skipModules` or an
outlier included (F4); numeric codes are compared as numbers and select
codes as exact strings, drawn uniformly among the usable codes, with no
fallback to another list (F5); a `refuse` record's `cleanValue` is the
answer it replaced, and the `cleanValue` of a `dontKnow` record in a run
with both faults is not prescribed; records are grouped by fault, then
follow the form's order (F6); the warning is identified by `refuse`, in the
faults' warnings, without prescribed wording, and five cumulated faults
need ten enumerators (F7).

## Amendment 7: team codes in text questions, and the questions left alone

Teams write their "don't know" and "refused" codes in text questions too
(an enumerator types "-999" where the respondent does not know a name or
an amount written as text), which is why `ipacheckenumdb` takes each code
both as a number and as a string. Everything not mentioned here is
unchanged.

A faulty enumerator of `dontKnow` or `refuse` (Amendments 1, 5 and 6) also
answers a `text` question with one of the team's codes, with the same
probability, draws, precedence and exclusions as for the other questions:
one of the fault's codes, drawn uniformly among those the engine accepts
for that question at that moment (its constraint, a `regex()` included);
when the engine accepts none, the answer drawn as usual is kept. Records
are as for the other questions (`value` the code, `cleanValue` the answer
it replaced). An "other, specify" text question is answered only when it
is relevant, so it receives a code only when the enumerator chose "other".

The questions these faults never change are, whatever the configuration:
`date`, `time` and `datetime`; `geopoint`, `geotrace` and `geoshape`;
`barcode`; `image`, `audio`, `video` and `file` questions (signatures and
drawings included); `range` questions, even though their value is a
number; `rank`; `acknowledge` and notes; calculations and read-only
questions; and, as before, the survey id and the upper questions of its
cascade, repeat counts and the answers that control them, consent
questions, answers copied by a reinterview, and answers already changed
by another fault or an outlier. The questions they may change are thus
`select_one`, `select_multiple`, `integer`, `decimal` (other than `range`)
and `text`.

Earlier tests that expect a text question to keep its answer under these
faults are superseded and go through a dispute.

Clarification (Amendment 7): assumptions G1–G7 of the questions file are
reviewed one by one and confirmed: a text code is the exact string of the
team's list, never compared as a number, with no fallback to another list
(G1); with probability 0.25 a code is drawn uniformly among those the
engine accepts at that moment, given the interview's answers so far,
injected ones included (G2); blank and irrelevant text stays blank, an
accepted code equal to the answer already there keeps its share of the
draws and gives no record, and leaves the answer to `refuse` (G3); the
`cleanValue` of a `dontKnow` record in a run with both faults stays
unprescribed (G4); a protected question has no record and no direct
change, a rank or note may stay blank, and the answers that control a
repeat count are protected whatever their type, a text question included
(G5); no random sequence is prescribed (G6); earlier tests that expect
text to keep its answer are superseded and go through a dispute (G7).
