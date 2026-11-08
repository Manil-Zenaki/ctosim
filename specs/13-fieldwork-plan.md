# Spec 13 — Fieldwork plan

## Purpose

Data-quality checks run during fieldwork (IPA's `ipacheck` and teams' own
do-files) look at who did each interview, when and where: duplicate
survey ids, interviews of one enumerator that overlap, an enumerator in
two distant places within minutes, interviews outside working hours.
Until now each submission drew its enumerator, its start time and each
GPS answer independently, so simulated data showed such problems by
chance, and checks run on them reported problems that nobody put there.

This module plans the fieldwork as it happens: each enumerator works
days of interviews one after the other, in one village a day, and each
household has its own survey id. Without anything injected, the
simulated data hold none of the problems above. (Injecting such problems
on purpose, with a list of what was injected, is a later module.)

The command line (spec 10, Amendment 4) and the Stata command (spec 11,
Amendment 7) simulate through this plan. `FormWalker.walk(int)` keeps its
behaviour (spec 07).

Language: Java. Package `ctosim`.

## Public API

```java
public record Visit(
        int enumerator,                 // 1 to config.enumerators()
        java.time.LocalDateTime start,  // when the interview starts
        double latitude,                // where the household is, in degrees
        double longitude,
        String id) { }                  // the survey id to give, or null

// added to FormWalker:
public Submission walk(int number, Visit visit);

public final class Fieldwork {
    public Fieldwork(LoadedForm form, SimulationConfig config);
    public java.util.List<String> configWarnings();
    public java.util.List<Submission> walkAll();
    public java.util.List<Visit> visits();
    public java.util.List<String> warnings();
}

// added to SimulationConfig (spec 04, Amendment 4):
public java.util.Optional<String> id();
```

## Walking one visit

`walk(number, visit)` fills the form as `walk(number)` does (spec 07 and
its amendments), with the same randomness for the same `number`, except:

- **Enumerator.** The submission is made by enumerator
  `visit.enumerator()`: its `username` and `deviceid` (spec 07,
  Amendments 1 and 2) are those of that enumerator.
- **Start.** The interview starts at `visit.start()`: the simulated clock
  starts there and `Submission.start()` is that time. The upload time
  (spec 07, Amendment 2) is still drawn from `end()`.
- **Location.** Every geopoint answer, and the first vertex of every
  geotrace and geoshape answer, is within 30 metres of
  (`visit.latitude()`, `visit.longitude()`). The other vertices follow
  the first as in spec 05. A field whose override has its own `area`
  (spec 04, Amendment 3) keeps drawing in that area. A geopoint keeps the
  altitude and accuracy of spec 05.
- **Survey id.** When `visit.id()` is not null and `config.id()` names a
  field, each question of that name is answered `visit.id()`, as if its
  override were `value` (spec 04) set to it. When the engine does not
  accept that answer, the question is answered as usual and a warning
  names the field and the value.

Distances are great-circle distances on a sphere of radius 6 371 km.

`walk(number, null)` throws `NullPointerException`; a visit whose
`start()` is null throws `NullPointerException`; a visit whose
`enumerator()` is not between 1 and `config.enumerators()`, whose
`latitude()` is not between −90 and 90 or whose `longitude()` is not
between −180 and 180 throws `IllegalArgumentException`, as does a
`number` below 1.

## The plan

`walkAll()` simulates submissions 1 to `config.submissions()` and returns
them in number order. Below, `N` is `config.submissions()`, `E` is
`config.enumerators()` and `D` is `config.fieldworkDays()`.

### Who

Submission `n` is made by enumerator `((n − 1) mod E) + 1`. An
enumerator's interviews, in increasing submission number, are its first,
second, ... interview.

### When

Day `j` (`j` = 0, 1, 2, ...) of fieldwork begins at `config.start()` plus
`j` days: work starts at the time of day of `config.start()` and ends
nine hours later. An enumerator does at most `q` interviews a day, where
`q` is `N / (E × D)` rounded up.

- An enumerator's first interview starts on day 0, at the start of work
  plus a whole number of minutes drawn uniformly between 0 and 60.
- Each next interview of an enumerator starts at the end of its previous
  interview (`Submission.end()`) plus a whole number of minutes drawn
  uniformly between 10 and 40 (travel and rest), on the same day, unless
  the enumerator has already done `q` interviews that day or that time
  is after the end of work. It is then the first interview of the
  enumerator's next day: at the start of work plus a whole number of
  minutes drawn uniformly between 0 and 60.

So an enumerator's interviews never overlap, each starts within working
hours, and at least ten minutes separate two of them. An interview may
end after the end of work.

When an enumerator's interviews go beyond day `D − 1`, `warnings()` holds
one warning that gives the number of days the fieldwork took and
`config.fieldworkDays()`.

### Where

Each enumerator has, for each day it works, a **village**: a point drawn
uniformly in `config.area()`, or, without an area, in the rectangle where
spec 05 draws locations without an area. Each interview takes place at a
point within 1.5 km of its village and within that area or rectangle.
Two interviews of the same enumerator on the same day are therefore at
most 3 km apart.

### Survey ids

When `config.id()` names a question, each submission gets its own value
for it. The **possible values** of the question are, in this order of
precedence:

- the values its override allows (spec 04): its `value`; else its
  `texts`; else the whole numbers of its `min`–`max` range, for an
  integer question;
- for a `select_one` question without such an override, the values of its
  choice list, without the special values of the configuration;
- for an integer question without such an override, the whole numbers of
  `config.integerRange()`.

The possible values are put in an order drawn from `config.seed()`, and
submission `n` gets the value at position `(n − 1) mod K`, where `K` is
their number. When `K` is smaller than `N`, some values are used twice,
and `warnings()` holds one warning that names the field and gives `K`
and `N`. When the question has no possible values (a text question
without `texts`, for example), every visit's `id()` is null, and
`warnings()` holds one warning that names the field.

When `config.id()` names no element of the form, `configWarnings()` holds
a warning that names it (after the walker's own configuration warnings,
spec 07), and every visit's `id()` is null.

### Randomness

All draws come from `config.seed()`: the minutes of a submission's start
from its number alone, the villages from the enumerator and the day, the
interview points from the submission number. The same form (loaded from
the same bytes) and configuration give equal results on every call of
`walkAll()`, whatever the machine's locale or time zone, and each
returned submission has the same instance, times and warnings as
`walk(n, visit)` for the visit the plan gave it.

`visits()` holds the visits of the last call of `walkAll()`, in
submission number order. `warnings()` holds the plan's warnings (not the submissions' own) of the
last call of `walkAll()`, in the order given above; it is empty before
the first call, and so is `visits()`. Lists returned by this class are
unmodifiable.

## Clarification

Assumptions 1–13 of the questions file are reviewed one by one. They are
confirmed, with these decisions on the points left open:

- **The survey id is never left blank** (1): a household always has one,
  so the visit's id is given whatever the missing rate of the question
  (`missingRate` does not apply to it); a required or optional question
  is treated the same way.
- **K counts distinct values** (2): a value listed twice in `texts` is a
  possible value once; an empty string is not a possible value.
- **No overlap across days** (9): when an interview ends so late that the
  next day's start of work plus the drawn minutes is not at least ten
  minutes after it, the next interview is the first of the first later
  day for which it is. A day is counted from `config.start()` (day `j`
  begins `j` days after it), whatever the calendar date.

Also confirmed: the possible values follow the stated precedence, and a
weight of 0 does not remove a choice (3); a range gives the integers from
`min` rounded up to `max` rounded down, none when that is empty (4);
`texts` apply to any question kind, and special values are removed only
from a choice list (5); behaviour for elements that are not questions,
read-only or irrelevant questions and filtered choices is not prescribed,
and repeat instances of the id question all get the visit's id (6); any
refusal by the engine (conversion, choice, constraint) leads to the usual
answer and a warning (7); a visit is checked by `walk`, NaN is not
prescribed (8); the minutes of a submission depend on the seed and its
number only, locations do not depend on the answers, and the id order
does not depend on `N` (10); villages are observed through their
consequences only (11); lists are unmodifiable, with no promise on the
lifetime of earlier lists (12); the days taken are the last day index
used plus one, and the overflow warning comes before the id warning (13).

## Amendment 1: a daily quota, and ids from a `search()` list

Checking simulated data with `ipacheck` and travel checks showed two
problems. With the defaults (100 submissions, 5 enumerators, 30 days)
the quota `q` of "When" is 1: each enumerator did one interview a day,
which is not how fieldwork goes, and checks between the interviews of
a day had nothing to look at. And a survey id chosen from a list loaded
by `search()` (the household picked from the preloaded list) had no
possible values but the list's placeholder. Everything not mentioned
here is unchanged.

### Quota

The quota `q` of "When" is now `config.interviewsPerDay()` (spec 04,
Amendment 5), 4 by default, whatever `N`, `E` and `D`. An enumerator
still moves to its next day when its next interview would start after
the end of work, so a long form gives fewer interviews a day. The length
of fieldwork follows from the quota and the interviews' durations;
`config.fieldworkDays()` is only used for the warning when the
interviews go beyond day `D − 1`.

### Ids from a `search()` list

For a `select_one` question whose choices are loaded by `search()`
(spec 07, Amendment 3) and that has no `value`, `texts` or range in its
override, the possible values of "Survey ids" are the values of the
choices the question offers when the form is opened, before any answer:
the choices loaded from the attached file and the static choices,
without the list's placeholder and without the special values of the
configuration. When the file cannot be read, they are the static
choices alone.

Clarification (Amendment 1): assumptions 14–19 of the questions file are
reviewed one by one and confirmed, with one decision on the point left
open (15): a range defines possible values only for an integer question,
so on a `select_one` question whose choices are loaded by `search()` a
range in its override does not count, and the loaded and static choices
apply. Also confirmed: the earlier tests of the quota derived from
`fieldworkDays` are superseded and go through a dispute (14); the pool is
taken when the form is opened, with blank or default values, and each
`walkAll()` starts from a fresh form (16); a value found in the file and
among the static choices counts once, and special values are removed
from both, but not from an explicit `value` or `texts` (17); a missing
file or column gives the static choices, an empty file or no matching
row gives none, and search diagnostics belong to the submissions (18);
schedules are checked through their consequences, without a prescribed
random sequence (19).

## Amendment 2: survey ids chosen through a cascade

Checking the IPA household form showed that Amendment 1 does not work
for the usual cascade (district, then village, then the household from
the preloaded list, each list filtered by the previous answer): when the
form is opened no village is chosen yet, so the household list is empty,
and every submission drew its id as usual, with repeats. Everything not
mentioned here is unchanged.

### Ids given at the question

A `select_one` id question whose choices are loaded by `search()` with a
**filter that depends on the form's answers** (a `text` or `filterText`
argument of the call that is not a string literal, spec 07 Amendment 3)
and whose override has no `value` or `texts` gets its id **when the
walker reaches it**, instead of from the visit:

- the visit's `id()` is null, and the plan has no possible values for it
  (no warning of "Survey ids" for it);
- submission `n` is answered with a value drawn uniformly, from
  `config.seed()` and `n`, among the choices the question offers then
  (spec 07, Amendment 3), without the configuration's special values,
  that no earlier submission of the same `walkAll()` received;
- when every offered choice has already been received, the value is
  drawn among all of them, and `warnings()` holds, after the other
  warnings, one warning that names the field and gives the number of
  submissions whose id was used before;
- the missing rate does not apply to the question, as for a visit's id
  (Clarification of the original spec).

A `search()` list whose arguments are all string literals keeps the
rule of Amendment 1. `walk(number, visit)` alone, outside `walkAll()`,
answers such a question as usual.

Clarification (Amendment 2): assumptions 20–26 of the questions file are
reviewed one by one and confirmed, with decisions on the two points left
open. When the question offers no choice at all, or only special values
(21), it is answered as usual and no id counts as received. An id counts
as received only when the engine accepted it as the answer (22); a
repeated id question, read-only or irrelevant questions and conditional
overrides are not prescribed. Also confirmed: the earlier test of an
answer-dependent filter allocated at opening is superseded and goes
through a dispute (20); only the `text` and `filterText` positions decide
whether the filter depends on the answers (23); the history of received
ids covers the whole `walkAll()`, and the warning counts submissions (24);
the draw depends on the seed, `n`, the offered choices and the ids already
received only (25); a standalone `walk` has no shared history (26).

## Amendment 3: upper questions of a cascade steered to free ids

With Amendment 2, the village of a district-village-household cascade is
still drawn freely, so a village drawn more often than others runs out
of households while others keep free ones (11 repeated ids in 100
submissions of the IPA household form, although its file had 108
households). A team does not go back to a village whose households have
all been interviewed. Everything not mentioned here is unchanged.

### The cascade

When the id question is given its id at the question (Amendment 2) and
the `text` (or `filterText`) argument of its `search()` call is a single
reference to another `select_one` question whose choices are loaded by
`search()`, that question is the id's **upper question**; the upper
question of an upper question is found the same way, and so on. (In the
IPA form: `hhid`'s upper question is `a_kg_id`, whose upper question is
`a_district_id`.)

### Viable choices

At a point of a submission, a choice `c` of an upper question is
**viable** when, with `c` as that question's answer and the other
answers as they are:

- for the upper question of the id question: the id question would offer
  (spec 07, Amendment 3) at least one choice, other than the
  configuration's special values, that no earlier submission of the same
  `walkAll()` received;
- for a higher upper question: the question below it would offer at least
  one viable choice.

### Answering an upper question

When the walker answers an upper question during `walkAll()`, and at
least one of the choices it offers is viable:

- the answer is drawn among the viable offered choices: with the weights
  of the question's override restricted to them when it has weights,
  uniformly otherwise, from `config.seed()` and the submission number;
- the missing rate does not apply to the question.

When no offered choice is viable, or outside `walkAll()`, the question is
answered as before. A question whose override has a `value`, `texts` or
an expression, or whose rules apply, is answered as before too.

Clarification (Amendment 3): assumptions 27–33 of the questions file are
reviewed one by one and confirmed, with a decision on the point left
open (31): while an upper question is steered, the draw among its viable
choices replaces the special-value rate (a special value is drawn only
when it is viable, like any choice). Also confirmed: absolute and
relative single references make a cascade, an expression containing a
reference does not, and the upper question must be a `select_one` loaded
by `search()` (27); weights restricted to the viable choices keep their
ratios, and all-zero weights give a uniform draw (28); only a rule whose
condition is true disables steering (29); viability counts distinct ids
not yet received among the choices actually offered, and looking ahead
neither receives nor reserves an id (30); an override disables steering
only on its own question, and standalone walks never steer (32); draws
depend on the seed and the submission number (33).

## Amendment 4: one village a day in the answers, and located villages

The plan gives each enumerator one village a day ("Where"), but until
now that village was only a point used for the GPS answers: the village
question of a cascade (Amendment 3) was drawn at each interview, so one
enumerator declared several villages, possibly far apart, in a single
day, and a check of the villages visited by an enumerator in a day
flagged clean data. With this amendment the declared village follows the
enumerator's day, and, when the configuration names the columns of the
preloaded village list that hold coordinates (spec 04, Amendment 10),
the interviews take place at the villages' coordinates. Everything not
mentioned here is unchanged.

### The village question

When the id question has an upper question (Amendment 3), that upper
question is the **village question**. An interview's **place** is its
answers to the village question and to the village question's own upper
questions (in the IPA form: `a_district_id` and `a_kg_id`). A form whose
id question has no upper question has no village question, and nothing
of this amendment applies to it.

A village question whose override has a `value`, `texts` or an
expression, or whose rules apply, is not steered (Amendment 3): nothing
of this amendment applies to it either.

### The village of the day

During `walkAll()`, each enumerator has, on each day it works, a **place
of the day**, whose village is its **village of the day**:

- The enumerator's first interview of a day answers the village question
  and its upper questions as in Amendment 3 (drawn among the viable
  choices). The place it gets is the enumerator's place of the day.
- Each next interview of the enumerator on that day answers each
  question of the place with the place of the day's answer, when that
  answer is offered and viable (Amendment 3) at that point of the
  interview.
- When the village of the day is no longer viable (all the households it
  offers have been received), the enumerator **moves** to another
  village: the place is answered with a **candidate** (an answer to each
  question of the place, each offered and viable given the answers above
  it). When the village of the day and at least one candidate's village
  are located (below), the candidate is the one whose village is nearest
  to the village of the day among the candidates whose village is
  located, the first one in the order of the offered choices, upper
  questions first, when two are equally near; otherwise the place is
  answered as in Amendment 3. The new place is the enumerator's place of
  the day from then on.
- When no candidate exists, the place is answered as in Amendment 3.

A new day starts a new place of the day, drawn as for the first day.

**The time of a move.** When an enumerator moves within a day and both
villages are located, the interview starts later than "When" gives, by
two minutes per kilometre between the two villages' coordinates (30 km/h),
rounded up to a whole minute. When that start is after the end of work,
the interview is instead the first interview of the enumerator's next
day ("When"), and its place is that day's new place.

### Located villages

When `config.villages()` (spec 04, Amendment 10) is present, a choice of
the village question is **located** when the first row of the CSV file
the question's `search()` reads (spec 07, Amendment 3), among those whose
placeholder column holds the choice's value, has, in the column named by
`latitude()`, a number between −90 and 90 and, in the column named by
`longitude()`, a number between −180 and 180. These numbers are its
**coordinates**. Without `config.villages()`, no village is located.

An interview whose village answer is located takes place at a point
drawn from `config.seed()` and its submission number, within 1.5 km of
its village's coordinates: that point is its visit's `latitude()` and
`longitude()` in `visits()`, and its location answers follow it as in
"Walking one visit", whether the form asks them before or after the
village question. `config.area()` does not apply to such a point. An
interview whose village is not located takes place as in "Where".

### Warnings

When `config.villages()` is present and the form has no village question
(or one that is not steered), `configWarnings()` holds one warning that
says so. When the file read by the village question has no column of
one of the names given, `warnings()` holds one warning that names the
column and the file, once for each such pair, after the other warnings.

### Randomness

The candidates, the nearest one and the time of a move depend on the
answers and on the coordinates; every draw comes from `config.seed()`
and the submission number, and the results are reproducible as in
"Randomness".

Clarification (Amendment 4): assumptions 34–40 of the questions file are
reviewed one by one and confirmed: a place of the day's answer that is no
longer offered is not reused, and what replaces it is not prescribed
beyond the move rule (34); the first row of the whole file holding the
choice's value gives the coordinates, values compared as strings, and an
unusable cell leaves the choice unlocated without a warning (35); only
constant rules are tested for disabling the village, which also disables
its coordinates (36); the time of a move is added to the usual gap, an
interview pushed to the next day draws that day's place afresh, and a
start exactly at the end of work is still that day (37); ties follow the
offered order, upper questions first, and only located candidates
compete when the village of the day is located (38); no radial
distribution is prescribed, and standalone walks keep no place (39); the
warnings' wording and the order of the two column warnings are not
prescribed, and an unreadable file needs no column warning (40).

## Amendment 5: visit outcomes and revisits

Until now every household was interviewed at the first visit, so a
tracking of the fieldwork (IPA's `ipatracksurvey`, a team's dashboard)
showed an unrealistically perfect progress: no absent household, no
second visit, no household left unreached. When the configuration names
the form's availability question (spec 04, Amendment 15), the plan now
simulates failed visits and revisits. Without that setting, everything
is unchanged.

### Public API

```java
public record Visit(
        int enumerator,
        java.time.LocalDateTime start,
        double latitude,
        double longitude,
        String id,
        int attempt,        // the household's visit number: 1 for its first visit
        boolean found) {    // whether the household is found (available) at this visit

    public Visit(int enumerator, java.time.LocalDateTime start,
            double latitude, double longitude, String id);   // attempt 1, found
}
```

`walk(number, visit)` throws `IllegalArgumentException` for an `attempt`
below 1, in addition to the earlier cases.

### Walking one visit

When `config.visits()` is present, `walk(number, visit)` also does the
following; without it, `attempt()` and `found()` have no effect.

- **Availability.** Each question named by `field` that the walker
  answers gets the `available` value when the visit finds the household.
  When it does not, it gets a value drawn uniformly, from
  `config.seed()` and `number`, among the values the question offers
  other than `available` and the configuration's special values (spec
  04: `specialValues`, `dontKnowCodes`, `refuseCodes`, `otherCodes`). The
  rest of the form follows the form's own logic: what is asked after a
  failed visit is what the form asks then.
- **Visit number.** Each question named by `number` that the walker
  answers gets `attempt()`, written as a whole number.
- These answers replace the question's usual draw, its override and its
  missing rate. When the engine does not accept the answer, or when no
  value other than `available` is offered for a failed visit, the
  question is answered as usual, and the submission has a warning that
  names the field and, when there is one, the value.

### Households

During `walkAll()`, each submission is either a **revisit** (below) or
the **first visit** of a new household. A household's **outcome** is
drawn when its first visit is planned, from `config.seed()` and that
visit's submission number, apart from the other draws:

- with probability `unreachedRate`, it is **unreached**: its first
  `maxVisits` visits all fail;
- with probability `revisitRate`, it is **revisited**: its first `k`
  visits fail and the next one finds it, `k` drawn uniformly between 1
  and `maxVisits − 1`;
- otherwise it is found at its first visit.

A household **waits** for a revisit from the day of a failed visit that
is not its last planned visit. Its visits are made by the enumerator of
its first visit.

### Revisits

On each day it works, an enumerator first revisits its waiting
households, one interview per household, before any new household: as
long as households wait from an earlier day, the enumerator's first
interviews of the day are revisits, of the households in the order of
their latest visit's submission number. A household whose visit failed
that same day waits for a later day. Whether an interview is a revisit
follows from the day it is finally scheduled on (below).

A revisit:

- is the household's next visit: its `attempt()` is one more than the
  household's previous visit's, and its `found()` follows the
  household's outcome;
- gets the household's survey id: the visit's `id()` is the household's,
  and when the id is given at the question (Amendment 2), the id question
  and its upper questions (Amendment 3) are answered as at the
  household's first visit;
- takes place at the household's point: its visit's `latitude()` and
  `longitude()` are those of the household's first visit.

When an enumerator's first interview of a day is a revisit, the
enumerator's village of that day (`"Where"`) is the household's point,
and, when the form has a village question (Amendment 4), its place of
the day is the household's place.

**Trips.** A revisit, and an interview that follows a revisit on the
same day, starts later than "When" gives by two minutes per kilometre
between its point and the point of the enumerator's previous interview
(rounded up to a whole minute), when the two are more than 3 km apart.
When that start is after the end of work, the interview is instead the
first interview of the enumerator's next day. (Moves between located
villages, Amendment 4, keep their own rule.)

### Survey ids

Survey ids of "Survey ids" go to households instead of submissions: the
`k`-th household, in the order of first visits, gets the possible value
at position `(k − 1) mod K`, and the warning for `K` smaller than `N`
compares `K` with the number of households. An id given at the question
(Amendment 2) counts as received at the household's first visit.

### Warnings

- When `field` names no element of the form, `configWarnings()` holds a
  warning that names it, after the others, and no visit fails: every
  visit finds its household and has `attempt()` 1.
- When `number` names no element of the form, `configWarnings()` holds
  a warning that names it, after the others.
- When `walkAll()` ends while households still wait for a revisit,
  `warnings()` holds, after the other warnings, one warning that gives
  their number.

### Randomness

The outcomes, the failed visits' values and the revisits' schedule come
from `config.seed()` as above, and the results are reproducible as in
"Randomness". `visits()` holds every submission's visit, with its
`attempt()` and `found()`.

## Amendment 6: final reasons of a failed visit

Amendment 5 drew the value of a failed visit among all the values meaning
"not available", whatever followed, so a household recorded as moved or
deceased could be found at the next visit. With the final values of the
configuration (spec 04, Amendment 16), the reasons follow the household's
outcome. Without final values, everything is as in Amendment 5.

During `walkAll()`, the value of a failed visit is drawn, as in
Amendment 5, among the values the availability question offers other
than `available` and the special values, restricted as follows:

- **Revisited household** (found after failed visits): among the values
  that are not final. When none is offered, among all of them, as in
  Amendment 5.
- **Unreached household**: among all of them. When the value given is
  final, the household is not visited again: it no longer waits for a
  revisit, and it has fewer than `maxVisits` visits.

A household found at its first visit, and `walk(number, visit)` outside
`walkAll()`, are not affected.

Clarification (Amendment 5): assumptions 41–47 of the questions file are
reviewed one by one and confirmed: the availability and visit number
questions are answered only where the walker answers a question, every
answered instance gets the answer, the visit answers replace values,
weights, expressions and rules, and a question without offered choices
uses the fallback (41); `attempt()` has only the lower bound, `maxVisits`
limits planned households, and the fallback covers every refusal by the
engine (42); a household is an entity of the plan, not an id string: a
revisit carries the household's id only when the plan gave it one or the
id was given at the question, and it receives no new id (43); the day
finally scheduled decides whether an interview is a revisit, waits are
counted in workdays of "When", and a household whose visits are over does
not wait (44); a trip into a new day is not charged, the combination of a
trip with the move time of Amendment 4 is not prescribed, and a later
revisit of a day does not change that day's place (45); the outcome draw
depends on the seed and the first visit's number only, and the failed
value on the seed, the number and the offered values (46); warnings need
the named fields and numbers only, the two configuration warnings follow
the earlier ones in either order, and the waiting warning counts
households (47).

Clarification (Amendment 6): assumptions 48–52 of the questions file are
reviewed one by one and confirmed: a revisited household whose only
reasons are final takes one of them and is still found later (48); a
final value can be drawn at any failed visit of an unreached household,
including its last, and only an accepted final answer at the availability
question ends the household; rejected answers, skipped or repeated
availability questions are not prescribed (49); final values are compared
as exact strings with the choices offered at that visit, after removing
`available` and the special values, and a final value the question does
not offer ends nothing (50); an ended household leaves the queue at once,
is not counted by the waiting warning, and the next interviews go to new
households, which take the next ids (51); the draws stay uniform and
seeded as in Amendment 5 (52).

## Amendment 7: a revisit repeats the household's identification

Amendment 5 gave a revisit the household's survey id, its cascade and its
point, but the other questions asked before the availability question
(the district, the enumerator's name, the household head's name...) were
drawn again at each visit, so the same household could change district
from one visit to the next. A revisit now repeats them, as an enumerator
reopening the same household's form would. Without `config.visits()`,
everything is unchanged.

During `walkAll()`, at a revisit, each question listed below that the
walker answers gets the answer recorded for it in the household's first
visit:

- the questions the walker answered in the first visit before it first
  answered the availability question (`field`), in the order it walked
  the form;
- except questions inside a repeat group, the availability question, the
  visit number question (`number`), and questions of type date, time,
  dateTime, geopoint, geotrace, geoshape, image, audio, video and file,
  which are answered as usual.

A question the first visit left blank is left blank. A repeated answer
replaces the question's usual draw, its override and its missing rate;
enumerator faults and outliers (spec 14) do not change it and the answer
key records nothing for it. When the engine does not accept it, the
question is answered as usual, and the submission has a warning that
names the field and the value.

A question the revisit asks but the first visit did not, and every
question after the availability question, is answered as before. The id
question and its upper questions keep the rule of Amendment 5.
`walk(number, visit)` outside `walkAll()` is not affected.

Clarification (Amendment 7): assumptions 53–58 of the questions file are
reviewed one by one. The repeated questions are those answered before the
first answer to the availability question, in walk order, outside any
repeat at any depth; what happens when the first visit never answers the
availability question, and to values kept without being answered (defaults,
calculations, read-only questions), is not prescribed (53, confirmed). A
question left blank by the missing rate is a recorded blank; a question the
first visit skipped has no recorded answer; the recorded answers are always
the first visit's, and the revisit's relevance decides what is asked (54,
confirmed). A recorded answer belongs to a question, not to a name shared by
questions of different groups, and to a household of the plan, not to an id
string (55, confirmed). A rejected answer falls back to the revisit's own
configuration and choices without changing the recorded answer; wording,
counts and random streams are not prescribed (56, confirmed). The recorded
answer is the one actually given at the first visit, with any fault it
received, and its repetition is neither changed nor recorded; questions
after the availability question stay open to faults and outliers (57,
confirmed). The outlier part of 57 is completed by spec 14, Amendment 10:
a problem's target always finds its household at its first visit and is
never a revisit, so with an outlier rate of 1 every submission is a target
and the plan has no revisit; an outlier reaches a revisit only when it is
moved there from a target with no eligible question. No random-stream
equivalence with a standalone walk is required (58, confirmed).
