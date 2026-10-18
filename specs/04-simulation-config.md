# Spec 04 — SimulationConfig

## Purpose

Read and validate the optional JSON file that tells the simulator how to
fill a form: how many submissions, which seed, when fieldwork happens,
how often optional questions are skipped, how many instances to create in
repeats whose count the enumerator controls, and per-field overrides
(a fixed value, a range, choice weights, a list of texts).

This module only reads and validates the configuration. It knows nothing
about any form: whether the fields it names exist is checked later, by the
walker (spec 06). How the generator uses each setting is described below
for context; testing that use belongs to specs 05 and 06.

Language: Java. Package `ctosim`.

## Public API

```java
public final class SimulationConfig {
    public static SimulationConfig defaults();
    public static SimulationConfig parse(String json, String sourceName) throws ConfigException;
    public static SimulationConfig load(java.nio.file.Path path) throws ConfigException;

    public long seed();
    public int submissions();
    public java.time.LocalDateTime start();
    public int fieldworkDays();
    public double missingRate();
    public double specialRate();
    public java.util.List<String> specialValues();
    public int repeatMin();
    public int repeatMax();
    public NumericRange integerRange();
    public NumericRange decimalRange();
    public java.util.Optional<FieldOverride> field(String name);
    public java.util.SortedSet<String> overriddenFields();

    public SimulationConfig withSeed(long seed);
    public SimulationConfig withSubmissions(int submissions);
}

public sealed interface FieldRange permits NumericRange, DateRange { }
public record NumericRange(double min, double max) implements FieldRange { }
public record DateRange(java.time.LocalDate min, java.time.LocalDate max) implements FieldRange { }

public final class FieldOverride {
    public java.util.Optional<String> value();
    public java.util.Optional<FieldRange> range();
    public java.util.Map<String, Double> weights();
    public java.util.List<String> texts();
    public java.util.OptionalDouble missingRate();
}

public class ConfigException extends Exception {
    public ConfigException(String message);
    public ConfigException(String message, Throwable cause);
}
```

## File format

A UTF-8 JSON object (a leading byte-order mark is accepted). Every key is
optional; an empty object `{}` gives the defaults. Example:

```json
{
  "seed": 42,
  "submissions": 300,
  "start": "2026-03-02T08:00:00",
  "fieldworkDays": 21,
  "missingRate": 0.05,
  "specialRate": 0.02,
  "specialValues": ["-99", "-98", "98", "99"],
  "repeats": {"min": 1, "max": 6},
  "integerRange": {"min": 0, "max": 50},
  "decimalRange": {"min": 0, "max": 10000},
  "fields": {
    "consent": {"value": "1"},
    "age": {"min": 15, "max": 64},
    "dob": {"min": "1950-01-01", "max": "2008-12-31"},
    "region": {"weights": {"1": 5, "2": 3, "3": 2}},
    "village_name": {"texts": ["Adama", "Bishoftu", "Mojo"]},
    "phone": {"missingRate": 0.5}
  }
}
```

### Top-level keys

| Key | Type | Default | Constraint | Meaning (used by specs 05–06) |
|---|---|---|---|---|
| `seed` | integer | `1` | any value representable as a Java `long` | Seed of all randomness. |
| `submissions` | integer | `100` | 1 to 1 000 000 | Number of submissions to simulate. |
| `start` | string, ISO local date-time (`YYYY-MM-DDTHH:MM` or `YYYY-MM-DDTHH:MM:SS`) | `2026-01-05T08:00:00` | | Start of simulated fieldwork; the simulated clock never uses the real date. |
| `fieldworkDays` | integer | `30` | 1 to 3 650 | Length of fieldwork; submissions are spread over these days. |
| `missingRate` | number | `0.05` | 0 to 1 | Probability that a relevant, non-required question is left blank. |
| `specialRate` | number | `0.02` | 0 to 1 | Probability of answering a select question with one of its special-value choices, when it has any. |
| `specialValues` | array of strings | see below | each a non-empty string; no duplicates | Choice values treated as "don't know / refused" codes. |
| `repeats` | object `{"min": integer, "max": integer}` | `{"min": 1, "max": 3}` | 0 ≤ min ≤ max ≤ 1 000; both keys required | Instance count for repeats without a `repeat_count`. |
| `integerRange` | object `{"min": number, "max": number}` | `{"min": 0, "max": 100}` | min ≤ max; both keys required | Default bounds for integer questions. |
| `decimalRange` | object `{"min": number, "max": number}` | `{"min": 0, "max": 1000}` | min ≤ max; both keys required | Default bounds for decimal questions. |
| `fields` | object mapping a field name to an override | `{}` | see below | Per-field overrides. |

Default `specialValues`: `["-99", "-98", "-97", "-96", "-88", "-77",
"-999", "-888", "-777", "97", "98", "99", "999"]`, in this order.

An integer key holds a JSON number with no fractional part (`42` and
`42.0` are accepted; `42.5` is not). A number key accepts any JSON number.

### Field overrides

The key is the field's `name` (as in the XLSForm). It must be a non-empty
string; leading or trailing white space is not allowed. The value is an
object with at least one of these keys:

| Key | Type | Meaning |
|---|---|---|
| `value` | string or number | Always answer this value. A number is kept as written for whole numbers (`1` → `"1"`, `1.0` → `"1"`), otherwise as Java's shortest decimal form of the double (`2.5` → `"2.5"`). |
| `min`, `max` | both numbers, or both ISO dates `YYYY-MM-DD` | Bounds for numeric or date answers. Both keys are required together; `min ≤ max`. |
| `weights` | object mapping a choice value (string) to a number ≥ 0 | Relative weights of the listed choices; at least one entry, and at least one weight > 0. |
| `texts` | array of strings | Answers to draw from for text questions; at least one entry. |
| `missingRate` | number, 0 to 1 | Replaces the global `missingRate` for this field. |

`value` cannot be combined with `min`/`max`, `weights` or `texts`.
`missingRate` can be combined with anything.

In `FieldOverride`: `value()` is empty when not set; `range()` holds a
`NumericRange` or a `DateRange`; `weights()` keeps the order in which the
choices appear in the file (empty map when not set); `texts()` keeps the
file order (empty list when not set); `missingRate()` is empty when not
set.

### Strictness

- Unknown keys, at any level, are errors (this catches typos such as
  `"submisions"`).
- A key given twice in the same object is an error.
- A JSON `null` is an error wherever a value is expected.
- A value of the wrong JSON type is an error (`"seed": "42"` is an error).
- Anything other than one JSON object (an array, a number, trailing
  content after the object) is an error.

## Accessors and copies

- `defaults()` returns the defaults listed above, with no field overrides.
- Every list, map and set returned is unmodifiable.
- `field(name)` returns the override for `name` (exact, case-sensitive
  match), or empty. `overriddenFields()` lists the names with an override,
  in natural string order.
- `withSeed` and `withSubmissions` return a new configuration that differs
  only in that setting; the receiver is unchanged. `withSubmissions`
  applies the same constraint as the file (1 to 1 000 000) and throws
  `IllegalArgumentException` otherwise.
- A `SimulationConfig` is immutable.

## Errors

`parse` and `load` throw `ConfigException` — never another exception type,
except `NullPointerException` for `null` arguments — when the input cannot
be read, is not valid JSON, or breaks any rule above. The message:

- names the source (`sourceName` for `parse`; the path for `load`);
- for a rule broken by a value, names that value's location as a dotted
  path from the root (`submissions`, `repeats.max`, `fields.age.min`,
  `fields.region.weights.2`), and says what is wrong;
- for invalid JSON, includes the line number where parsing failed.

When the failure comes from an underlying exception (I/O, JSON syntax), it
is kept as the cause.

## Clarifications (answers to `04-simulation-config.questions.md`)

All twelve assumptions were reviewed one by one and are confirmed. In
particular: integer keys are checked on the exact JSON number, before any
floating-point rounding, so every `long` seed is exact (6); `parse` accepts
a leading U+FEFF like `load` accepts a UTF-8 byte-order mark (3);
`withSeed` and `withSubmissions` return a distinct object even when the
value does not change (11).

## Amendment 1: time zone and enumerators

Two top-level keys are added; everything else is unchanged.

| Key | Type | Default | Constraint | Meaning (used by spec 07) |
|---|---|---|---|---|
| `timeZone` | string | `"UTC"` | a time-zone ID that `java.time.ZoneId.of` accepts: a region (`"Africa/Addis_Ababa"`), `"UTC"`, `"Z"`, or an offset (`"+03:00"`) | Time zone of the simulated devices: stored date-times carry its offset. |
| `enumerators` | integer | `5` | 1 to 10 000 | Number of simulated enumerators; each has its own username and device id. |

Accessors added to `SimulationConfig`:

```java
public java.time.ZoneId timeZone();   // ZoneId.of(text of the file), ZoneId.of("UTC") by default
public int enumerators();
```

- `defaults()` has `timeZone()` equal to `ZoneId.of("UTC")` and
  `enumerators()` 5.
- A `timeZone` that `ZoneId.of` rejects, or that is not a string, is an
  error located at `timeZone`. An `enumerators` value outside 1 to 10 000,
  or not an integer, is an error located at `enumerators`. The rules of
  "Strictness" and "Errors" apply to both keys.
- `withSeed` and `withSubmissions` keep both settings.

Clarification (Amendment 1): assumptions 13–17 of the questions file are
reviewed one by one and confirmed. In particular, `timeZone` is compared
as `ZoneId.of` gives it (`UTC` and `Z` are different IDs) (13); the
configuration does not resolve `start` in the zone (14); `enumerators`
may exceed `submissions` (15). A rejected zone's exception may keep the
`DateTimeException` as its cause, but need not (16).

## Amendment 2: speed violations

One top-level key is added; everything else is unchanged.

| Key | Type | Default | Constraint | Meaning (used by spec 07) |
|---|---|---|---|---|
| `speedViolationRate` | number | `0.1` | 0 to 1 | Probability that the enumerator tries to leave a field that has a minimum time too early, recording a speed violation. |

Accessor added to `SimulationConfig`:

```java
public double speedViolationRate();   // 0.1 by default
```

The rules of "Strictness" and "Errors" apply (an error is located at
`speedViolationRate`); `withSeed` and `withSubmissions` keep the setting.

Clarification (Amendment 2): assumptions 18–21 of the questions file are
reviewed one by one and confirmed (the rates are independent; only the
top level gains the key; the usual diagnostics apply).

## Amendment 3: realistic data

Answers drawn uniformly and independently of each other are valid but
unlike real data: ages, incomes and school levels are unrelated, every
point lies near latitude 0, and names read `resp_name 417`. This
amendment adds the settings that make simulated data realistic:
distributions, rules that depend on earlier answers, household
profiles, a survey area and pools of plausible texts. How they are used
is in spec 05 (Amendment 3) and spec 07 (Amendment 7). Everything not
mentioned here is unchanged.

### New public types and accessors

```java
public record Distribution(String kind, double mean, double sd) { }   // kind: "normal" or "lognormal"
public record Area(double minLatitude, double maxLatitude,
                   double minLongitude, double maxLongitude) { }
public record OverrideRule(String condition, FieldOverride override) { }

// added to FieldOverride:
public java.util.Optional<Distribution> distribution();
public java.util.Optional<String> expression();          // key "expr"
public java.util.Optional<Area> area();
public java.util.List<OverrideRule> rules();               // key "when"; empty when not set

// added to SimulationConfig:
public java.util.Map<String, Double> profiles();           // file order; empty when not set
public java.util.Optional<Area> area();
public java.util.Map<String, java.util.List<String>> textPools();   // file order; empty when not set
```

### New top-level keys

| Key | Type | Default | Constraint | Meaning |
|---|---|---|---|---|
| `profiles` | object mapping a profile name to a number ≥ 0 | none | at least one entry, at least one weight > 0; names non-empty, without leading or trailing white space | One profile is drawn per submission, in proportion to its weight; rules and expressions can test it with `profile()`. |
| `area` | object, see "Area" | none | | Where geopoints, geotraces and geoshapes are drawn. |
| `textPools` | object mapping a pool name to an array of strings | none | names among `firstName`, `lastName`, `place`, `comment`; each array non-empty, entries non-empty | Replaces the generator's built-in pool of that name. |

### New field-override keys

| Key | Type | Meaning |
|---|---|---|
| `distribution` | object with exactly one key, `normal` or `lognormal`, whose value is `{"mean": number, "sd": number}` | Numbers are drawn from this distribution instead of uniformly. `sd` ≥ 0; for `lognormal`, `mean` > 0 (`mean` and `sd` are those of the variable itself, not of its logarithm). |
| `expr` | string | An XPath expression, written as in the form (`${field}` references allowed), whose value is the answer. |
| `area` | object, see "Area" | Replaces the top-level `area` for this field. |
| `when` | array of rule objects | Conditional settings, see "Rules". |

`expr` cannot be combined with `value`, `min`/`max`, `weights`, `texts`
or `distribution`. `distribution` can be combined with `min`/`max`
(numbers only) and `missingRate`, not with `value`, `weights` or
`texts`. An override may now consist of `when` alone.

### Area

`{"minLatitude": number, "maxLatitude": number, "minLongitude": number,
"maxLongitude": number}`, all four keys required, with −90 ≤
`minLatitude` ≤ `maxLatitude` ≤ 90 and −180 ≤ `minLongitude` ≤
`maxLongitude` ≤ 180.

### Rules

Each element of `when` is an object with a key `if` (a non-empty string,
an XPath expression) and at least one of the field-override keys
`value`, `expr`, `min`/`max`, `weights`, `texts`, `distribution`,
`area`, `missingRate`, combined under the same restrictions as in an
override. `when` is not allowed inside a rule. In `OverrideRule`,
`condition()` is the `if` string and `override()` the rule's settings
(with empty `rules()`).

### Expressions

The strings of `if` and `expr` are XPath expressions in which
`${name}` stands for a field of the form and `profile()` for the
submission's profile. The configuration checks that each one parses
(after replacing every `${name}` by a path); one that does not parse is
an error located at its key (`fields.age.when[1].if`). Whether the
fields exist is not checked here.

The rules of "Strictness" and "Errors" apply to every new key; an error
is located at its path (`profiles.rural`, `area.minLatitude`,
`fields.income.distribution.lognormal.mean`, `textPools.place[2]`).
`withSeed` and `withSubmissions` keep every setting.

Clarification (Amendment 3): assumptions 22–28 of the questions file are
reviewed one by one and confirmed. In particular, an absent setting stays
absent in the accessors (a field's area is not filled from the top-level
area) (23); expressions that parse are kept exactly as written (24); the
new keys are reserved only where the schema allows them, not as field or
profile names (28).

## Amendment 4: survey id

One top-level key is added for the fieldwork plan (spec 13); everything
else is unchanged.

| Key | Type | Default | Constraint | Meaning (used by spec 13) |
|---|---|---|---|---|
| `id` | string | none | non-empty, without leading or trailing white space | Name of the question holding the survey id (household, respondent): each submission gets its own value. |

Accessor added to `SimulationConfig`:

```java
public java.util.Optional<String> id();   // empty when not set
```

- `defaults()` has an empty `id()`.
- An `id` that is not a string, is empty or has leading or trailing white
  space is an error of the configuration (`ConfigException`, as for the
  other keys). A name that the form does not hold is not an error here:
  the plan warns about it (spec 13).

Clarification (Amendment 4): assumptions 29–33 of the questions file are
reviewed one by one and confirmed: the name is kept exactly as decoded,
with no other grammar or length limit (29); space, tab, CR, LF and form
feed count as white space at either edge, and are rejected, not trimmed
(30); only the top-level key is new, and it does not interact with field
overrides in the configuration itself (31); the existing diagnostics and
input rules apply (32); copies keep `id()`, and uniqueness of the
generated ids belongs to spec 13 (33).
