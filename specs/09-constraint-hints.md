# Spec 09 — Constraint hints

## Purpose

The generator (spec 05) draws answers from configured ranges and the
walker (spec 07) retries when the engine rejects an answer. When the
valid answers lie outside the configured range, retrying cannot succeed:
a phone number stored as an integer with constraint
`.>=900000000 and .<=999999999` is never drawn from the default integer
range 0–100. Real forms state most of their valid answers in their
constraints (bounds on `.`, bounds relative to other fields, text length,
regular expressions).

This module reads a question's constraint and derives **hints** —
bounds, lengths and a pattern — that the generator uses to draw a valid
answer directly. Retrying stays as the fallback for constraints that give
no usable hint.

Language: Java. Package `ctosim`.

## Public API

```java
public record Bound(double value, boolean inclusive) { }
public record DateBound(java.time.LocalDate value, boolean inclusive) { }

public record ConstraintHints(Bound lower, Bound upper,
                              DateBound earliest, DateBound latest,
                              Integer minLength, Integer maxLength,
                              String pattern) {
    public static ConstraintHints none();
    public static ConstraintHints forCurrentQuestion(org.javarosa.form.api.FormEntryController session);
}
```

Every component is `null` when the constraint gives no such hint.
`none()` has all components `null`.

Added to `Question` (spec 05): a sixth component `ConstraintHints hints`
(never `null`), with the canonical constructor taking six arguments. The
five-argument constructor of spec 05 remains and gives
`ConstraintHints.none()`.

## Deriving hints

`forCurrentQuestion(session)` looks at the question the session is
currently on (`session.getModel().getFormIndex()`). When the session is
not on a question, or the question has no constraint, it returns
`none()`.

The constraint is read as written in the form, after the engine's own
parsing. Only the terms of its top-level conjunction are considered: for
`A and B and C`, each of `A`, `B`, `C` is examined on its own; an
expression that is not a conjunction is a single term. Each term
contributes a hint only if it has one of these shapes, where `.` is the
current question node and `E` is an expression that does not reference
`.`:

| Term | Hint |
|---|---|
| `. > E`, `. >= E`, `. < E`, `. <= E` | a lower or upper bound, exclusive for `>` / `<`, inclusive for `>=` / `<=` |
| `E < .`, `E <= .`, `E > .`, `E >= .` | the same bound, read from the other side (`E < .` is `. > E`) |
| `. = E` or `E = .` | both an inclusive lower and an inclusive upper bound |
| `string-length(.) OP E` (OP as above, either side) | a minimum and/or maximum length |
| `regex(., 'P')` with a string literal `P` | the pattern `P` |

`E` is evaluated in the session at the time `forCurrentQuestion` is
called, with the question node as context (so `${other_field}`, `../x`
and functions such as `today()` all work). Then:

- a number (or a value the engine converts to a finite number) gives a
  numeric bound, or a length when the term is a `string-length` term;
- a date or date-time value gives a date bound (its date in the JVM's
  default time zone), when the term is a comparison of `.`;
- anything else — an empty value, `NaN`, a non-numeric string, an
  evaluation error — gives no hint for that term.

Lengths are integers: an exclusive bound or a non-integer value is turned
into the inclusive integer bound it implies (`string-length(.) > 3`
gives a minimum length of 4; `string-length(.) <= 9.5` gives a maximum of
9). A negative minimum length is 0.

When several terms bound the same side, the tightest applies (the
largest lower bound, the smallest upper bound; at equal values, an
exclusive bound is tighter than an inclusive one). When several `regex`
terms appear, the first one gives the pattern.

Terms of any other shape (disjunctions `or`, negations `not(...)`,
`selected(...)`, comparisons where `.` is inside another expression such
as `. * 2 < 10`) give no hint. They are not errors.

`forCurrentQuestion` never throws because of the constraint's content.

## Using hints in the generator (amends spec 05)

Hints apply after the blank rule and the fixed-value rule, which are
unchanged. They are ignored for kinds not listed below.

### Integer and decimal

Let **R** be the range spec 05 uses (override or configuration), and
**H** the interval the numeric hints allow, as closed bounds:

- for `INTEGER`, an exclusive lower bound `v` becomes ⌊v⌋ + 1 and an
  exclusive upper bound `v` becomes ⌈v⌉ − 1; inclusive bounds become
  ⌈v⌉ (lower) and ⌊v⌋ (upper);
- for `DECIMAL`, an exclusive bound `v` becomes `v + 0.01` (lower) or
  `v − 0.01` (upper); inclusive bounds stay as they are.

The answer is drawn as in spec 05 but from:

1. **R ∩ H** when it is not empty;
2. otherwise, when H has both sides and is not empty, **H**;
3. otherwise, when H has only a lower bound L, **[L, L + (R.max − R.min)]**;
   when only an upper bound U, **[U − (R.max − R.min), U]**;
4. when H is empty (its lower bound above its upper bound), the hints are
   ignored and R is used.

### Dates

For `DATE` and `DATETIME`, let **D** be the date range of spec 05 and
**H** the closed date interval of the date hints (an exclusive bound moves
by one day). The date is drawn from D ∩ H when not empty; otherwise from
H when it has both sides; otherwise from the 365-day interval starting at
the lower bound or ending at the upper bound; when H is empty, the hints
are ignored. The time part of `DATETIME` is unchanged.

### Text

When the override has `texts`, they are used as in spec 05 and the hints
are ignored. Otherwise:

1. **Pattern.** When there is a pattern, the answer is a string drawn at
   random among those the regular expression (Java syntax, as used by the
   engine's `regex()`) matches; the answer always matches the pattern in
   full. When no such string can be produced (an unsupported or
   unsatisfiable pattern), the pattern is ignored.
2. **Length.** Otherwise, when there is a minimum or maximum length: a
   string whose length is drawn uniformly between the minimum (1 when
   absent) and the maximum (the larger of the minimum and 10 when absent);
   made of decimal digits when the appearance contains `numbers` or
   `numbers_phone`, otherwise of lowercase ASCII letters `a`–`z`, each
   character drawn uniformly. When the maximum is below the minimum, the
   length hints are ignored.
3. Otherwise, the rules of spec 05.

Drawing with hints is deterministic in the same sense as spec 05: same
configuration, question (including hints), date and random state give the
same answer.

## Using hints in the walker (amends spec 07)

For each question the walker answers, it builds the `Question` with
`ConstraintHints.forCurrentQuestion(session)` computed when the question is
reached (so bounds relative to other fields use their current values).
Everything else in spec 07 is unchanged, including the 50 attempts.

## Acceptance on real forms

- In `ethiopia_migration_household.xml`, with a configuration that sets
  `availability_04`, `consent` and `hh_one_exist` to `"1"`, no submission
  among numbers 1 to 50 has a warning about `contact_cell` or
  `contact_others_phone_out` (integer phone numbers bounded by literal
  constraints), whenever those questions are reached.
- In the XForm converted from `samples/coverage/ctosim_coverage.xlsx`,
  every `phone` answer in submissions 1 to 50 (with `consent` set to
  `"1"`) matches `^09[0-9]{8}$`.

## Clarifications (answers to `09-constraint-hints.questions.md`)

All twelve assumptions were reviewed one by one and are confirmed. In
particular: an equality to a fractional length gives an empty length
interval, which generation ignores, while explicit bounds 0–0 give the
empty string (4); a one-sided date interval holds 365 dates (5); the
translation width uses the configured range's unrounded endpoints (6);
when hints do not apply, generation draws exactly as spec 05 does, from
the same random stream (10).

## Amendment 1: effect on spec 07's retry behaviour

Because the walker now passes hints, a constraint whose terms all give
hints (such as `. = 1`, or `. >= 1 and . <= 20`) is usually satisfied by
the first proposal, so it no longer exercises the walker's retries.
Spec 07's rules are unchanged; observing its 50-attempt retry budget
statistically now requires a constraint that gives no hints, for example
`. = 1 or . = 1` or `not(. != 1)` (disjunctions and negations give no
hint, see "Deriving hints").
