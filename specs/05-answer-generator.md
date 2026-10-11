# Spec 05 — AnswerGenerator

## Purpose

Propose an answer to one question. The walker (spec 07) describes the
question, asks for an answer, gives it to the form engine, and asks again
when the engine rejects it (constraint violation). The generator knows
nothing about the form engine: questions are described with plain Java
values, and answers are returned as text in the XForm value format.

Language: Java. Package `ctosim`. No dependency on JavaRosa types.

## Public API

```java
public enum QuestionKind {
    TEXT, INTEGER, DECIMAL, DATE, TIME, DATETIME,
    SELECT_ONE, SELECT_MULTIPLE,
    GEOPOINT, GEOTRACE, GEOSHAPE,
    BARCODE, IMAGE, AUDIO, VIDEO, FILE,
    ACKNOWLEDGE
}

public record Question(String name, QuestionKind kind, java.util.List<String> choices,
                       String appearance, boolean required) {
    // canonical constructor: name, kind, choices, appearance not null; choices copied, unmodifiable
}

public final class AnswerGenerator {
    public AnswerGenerator(SimulationConfig config);
    public String generate(Question question, java.time.LocalDate today,
                           java.util.random.RandomGenerator random);
}
```

`generate` returns the answer text, or `null` for "leave the question
blank". `choices` lists the values of the choices currently offered by a
select question, in display order (after any choice filter); it is empty
for other kinds. `appearance` is the question's appearance attribute (`""`
when none). `today` is the simulated date of the interview.

## Behaviour

### General rules

- **Determinism.** The result depends only on the configuration, the
  arguments and the state of `random`: two generators built from the same
  configuration, given equal arguments and random generators in the same
  state, return the same answer. The generator never uses another source
  of randomness, the clock or the default locale.
- **Fresh draws.** Calling `generate` again with the same random
  generator draws new values (this is how the walker retries after a
  constraint violation); except when a fixed value applies (below), a
  second call can return a different answer.
- **Field override.** `config.field(question.name())` gives the override
  for this question, if any (spec 04). Parts of an override that do not
  apply to the question's kind are ignored (for example `texts` on an
  integer question, or a `DateRange` on a decimal question).

### Blank answers

The **missing rate** is the override's `missingRate` when set, otherwise
the configuration's `missingRate`.

- A **required** question is never left blank by this rule.
- A question that is not required is left blank (`null`) with probability
  equal to the missing rate. With rate 0 it is never blank; with rate 1 it
  is always blank.
- A select question with no choices is always blank, whatever the rate
  (there is nothing to choose).

The blank rule applies first; the rules below apply to answers that are
not blank.

### Fixed value

When the override has a `value`, the answer is that value, for every kind
and on every call (blank rule still applies first).

### Select one

Let **special choices** be the choices whose value is in the
configuration's `specialValues`, and **ordinary choices** the others.

- If there is at least one special choice: with probability `specialRate`
  the answer is a special choice, drawn uniformly among the special
  choices.
- Otherwise, the answer is an ordinary choice. Without `weights` in the
  override, it is drawn uniformly among the ordinary choices. With
  `weights`, each ordinary choice is drawn with probability proportional
  to its weight, a choice missing from `weights` having weight 0; weights
  of values that are not among the choices are ignored. If the weights of
  all ordinary choices are 0, the draw is uniform among ordinary choices.
- If there are no ordinary choices, the answer is drawn uniformly among the
  special choices.

### Select multiple

- With probability `specialRate`, when there is at least one special
  choice: the answer is a single special choice, drawn uniformly.
- Otherwise the answer is a non-empty set of distinct ordinary choices.
  Its size k is drawn uniformly between 1 and min(3, number of ordinary
  choices). The k choices are drawn without replacement, uniformly or,
  with `weights`, with probability proportional to the weight among the
  choices not yet drawn (a choice with weight 0 is drawn only when every
  remaining choice has weight 0).
- If there are no ordinary choices, the answer is one special choice.
- The answer lists the selected values separated by one space, in the
  order of `choices`.

### Numbers

The **range** is the override's `NumericRange` when set, otherwise
`integerRange` (for `INTEGER`) or `decimalRange` (for `DECIMAL`) from the
configuration.

- `INTEGER`: an integer drawn uniformly among the integers in the range
  (from ⌈min⌉ to ⌊max⌋, inclusive), written without a decimal point
  (`-3`, `0`, `42`). When the range contains no integer (for example
  [0.2, 0.8]), the answer is `Math.round(min)`.
- `DECIMAL`: a number drawn uniformly in [min, max], rounded to two
  decimal places (half-up), written with a `.` and no exponent, without
  trailing zeros and without a trailing `.` (`12.5`, `3`, `-0.25`); a
  value that rounds to zero is written `0`. When min = max the answer is
  that value, formatted the same way.

### Text

- With `texts` in the override: one of the texts, drawn uniformly.
- Otherwise, when `appearance` contains the word `numbers` or
  `numbers_phone` (appearance words are separated by spaces): a string of
  exactly 8 decimal digits, each drawn uniformly (leading zeros allowed).
- Otherwise: the question name, one space, and an integer drawn uniformly
  between 1 and 9999 (`village_name 417`).

### Dates and times

The **date range** is the override's `DateRange` when set, otherwise the
365 days ending on `today`: [`today` − 364 days, `today`].

- `DATE`: a date drawn uniformly in the date range, written `YYYY-MM-DD`.
- `TIME`: a time drawn uniformly at whole minutes between 06:00 and 20:00
  inclusive, written `HH:MM:00`.
- `DATETIME`: a date as for `DATE` and a time as for `TIME`, written
  `YYYY-MM-DDTHH:MM:00` (no time zone).

### Locations

A point is written `latitude longitude altitude accuracy`, separated by
single spaces, with latitude and longitude having exactly six decimal
places, altitude `0` and accuracy `5`.

- `GEOPOINT`: one point with latitude and longitude each drawn uniformly
  in [−1, 1]. (A configurable area is a later addition.)
- `GEOTRACE`: four points; the first is drawn as for `GEOPOINT`, each
  following point is the previous one shifted by an amount drawn uniformly
  in [−0.001, 0.001] in latitude and in longitude. Points are separated by
  `; ` (semicolon and space).
- `GEOSHAPE`: like `GEOTRACE` with four drawn points, followed by a copy
  of the first point (five points, closed shape).

### Other kinds

- `BARCODE`: a string of exactly 10 decimal digits.
- `IMAGE`, `AUDIO`, `VIDEO`, `FILE`: a file name made of the question
  name, `_`, an integer drawn uniformly between 1 and 999999, and the
  extension `.jpg`, `.m4a`, `.mp4` or `.bin` respectively
  (`photo_hh_52113.jpg`).
- `ACKNOWLEDGE`: `OK`.

## Errors

- `new AnswerGenerator(null)` throws `NullPointerException`.
- `generate` throws `NullPointerException` when `question`, `today` or
  `random` is `null`.
- `Question`'s constructor throws `NullPointerException` when `name`,
  `kind`, `choices` or `appearance` is `null`, or when `choices` contains
  `null`. Its `choices()` is an unmodifiable copy (later changes to the
  list passed in do not affect it).

## Clarifications (answers to `05-answer-generator.questions.md`)

All fifteen assumptions were reviewed one by one and are confirmed. In
particular: decimal half-up rounding applies to the number's ordinary
decimal value (`2.345` → `2.35`) (6); integers are not limited to the
32-bit range (5); a select question with no choices is blank even when
required or fixed, since the blank rule comes first (12).

## Amendment 1: appearances seen in the third real export

From the export of the third coverage form (`samples/coverage3/`). Each
rule applies when `appearance` contains the word (appearance words are
separated by spaces). Everything not mentioned here is unchanged; the
blank rule still applies first, and a fixed `value` still takes
precedence over the first two rules.

- **`numbers_decimal` (`TEXT`).** Without `texts` in the override, the
  answer is a decimal number drawn and written as for `DECIMAL` (range:
  the override's `NumericRange` when set, otherwise `decimalRange`).
- **`month-year` (`DATE`).** The answer is the first day of the month of
  the date drawn as for `DATE` (`2025-03-01`). SurveyCTO Collect stores
  the first of the month for such a question.
- **`label` (`SELECT_ONE`, `SELECT_MULTIPLE`).** The question is the
  header row of a table: it only shows the choice labels and cannot be
  answered. The answer is always blank (`null`), even when the question
  is required or the override has a `value`.

Clarification (Amendment 1): assumptions 16–21 of the questions file are
reviewed one by one and confirmed. In particular, appearance words are
whole space-separated words, so `list-nolabel` is not `label` (17); a
`month-year` answer is the first day of the drawn date's month even when
that day is before the range's minimum, and months are drawn in
proportion to their days in the range (18); no particular sequence of
random draws is required (19); a `label` select is blank even when it is
required or has a fixed value (20).
