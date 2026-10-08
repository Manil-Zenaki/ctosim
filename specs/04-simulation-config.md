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
