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
