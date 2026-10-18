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
