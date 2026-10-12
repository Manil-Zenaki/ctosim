# Spec 07 — FormWalker

## Purpose

Fill one form from beginning to end, as an enumerator would, and return
the resulting submission. The walker drives a simulated session (spec 06)
through the form engine: the engine decides which questions are relevant,
evaluates calculations and constraints and creates counted repeat
instances; the walker asks the generator (spec 05) for each answer,
retries when a constraint rejects it, decides how many instances to add
to repeats the enumerator controls, and moves the simulated clock forward.

Language: Java. Package `ctosim`.

## Public API

```java
public final class FormWalker {
    public FormWalker(LoadedForm form, SimulationConfig config);
    public java.util.List<String> configWarnings();
    public Submission walk(int number);
}

public final class Submission {
    public int number();
    public String instanceXml();
    public java.time.LocalDateTime start();
    public java.time.LocalDateTime end();
    public int answered();
    public java.util.List<String> warnings();
}
```

## Construction

- `new FormWalker(null, config)` and `new FormWalker(form, null)` throw
  `NullPointerException`.
- `configWarnings()` lists, in natural string order of the names, one
  warning for each name in `config.overriddenFields()` that is not the
  name of any element of the form's primary instance (questions, notes,
  calculates, groups, repeats, metadata). Each warning contains that name.
  The list is unmodifiable; it is empty when every override names an
  element.

## Walking one submission

`walk(number)` simulates submission number `number` (1 for the first).
`number` < 1 throws `IllegalArgumentException`.

### Reproducibility

The submission depends only on the form, the configuration and `number`.
Two walkers built from the same form (or two loads of the same form) and
equal configurations return submissions with identical `instanceXml`,
`start`, `end`, `answered` and `warnings` for the same `number`, whatever
other submissions either walker produced before, and in whatever order.
Different numbers give different submissions (in particular different
`instanceID`s, when the form has one).

All randomness used for a submission comes from `config.seed()` and
`number`: the seed of the simulated session, the answers, the clock
advances and the repeat counts.

### Start time

The interview starts at `config.start()` plus `d` days plus `m` minutes,
where `d` is drawn uniformly among the integers 0 to
`config.fieldworkDays() − 1` and `m` uniformly among the integers 0 to
540 (nine hours). The simulated clock starts there; `Submission.start()`
is that time.

### Questions

The walker steps through the form with the engine's navigation, so it only
meets questions that are relevant at that point. For each question:

1. **Read-only** questions (notes, read-only fields) are not answered and
   the clock does not move.
2. **Existing value.** When the question already has a value when it is
   reached (an instance default, or a value set by a calculation or an
   action) and the configuration has no override for its name, the value
   is kept: the question is not answered again and the clock does not
   move.
3. **Unsupported controls.** A question whose control the generator has no
   kind for (`rank`, `osm`, or any data type not listed below) is not
   answered, and a warning naming the question is added; the clock does
   not move.
4. Otherwise the walker builds a `Question` (spec 05):
   - `name`: the question node's name (the last step of its path);
   - `kind`: from the engine's data type and control, as in the table
     below;
   - `choices`: for select questions, the values of the choices the engine
     currently offers (after choice filters), in order; empty otherwise;
   - `appearance`: the question's appearance attribute, `""` when none;
   - `required`: whether the engine currently marks the question as
     required;

   and asks `AnswerGenerator.generate` for an answer, with `today` the
   clock's current date.
5. **Blank.** When the generator returns `null`, the question is left with
   no value.
6. **Answering.** Otherwise the walker gives the answer to the engine
   (conversion below). When the engine rejects it because of a
   constraint, the walker asks the generator again (same question, same
   random sequence, so fresh draws), up to **50 attempts in total**. When
   no attempt is accepted, the question is left with no value and a
   warning is added that names the question and says that its constraint
   could not be satisfied.
7. After a question has been handled under 5 or 6, the clock advances by a
   whole number of seconds drawn uniformly between 5 and 60.

`Submission.answered()` counts the questions for which the engine accepted
an answer from the walker (rule 6), once per question instance; questions
left blank, kept, read-only or unsupported are not counted.

A **required** question that ends without a value (because its constraint
could not be satisfied, or its answer could not be converted) also gets a
warning that names it and says it is required.

| Engine control / data type | `QuestionKind` |
|---|---|
| select one control | `SELECT_ONE` |
| select multiple control | `SELECT_MULTIPLE` |
| trigger control (acknowledge) | `ACKNOWLEDGE` |
| image / audio / video / file capture control | `IMAGE` / `AUDIO` / `VIDEO` / `FILE` |
| data type text | `TEXT` |
| data type integer or long | `INTEGER` |
| data type decimal | `DECIMAL` |
| data type date / time / date-time | `DATE` / `TIME` / `DATETIME` |
| data type geopoint / geotrace / geoshape | `GEOPOINT` / `GEOTRACE` / `GEOSHAPE` |
| data type barcode | `BARCODE` |

Controls are considered before data types (an integer select is a
select). A range control is treated by its data type.

### Converting answers

- Select one: the offered choice whose value equals the answer.
- Select multiple: the offered choices whose values are the
  space-separated items of the answer.
- Other kinds: the engine's own conversion of the answer text for the
  question's data type.

When an answer cannot be converted — a select value that is not among the
offered choices (for example a fixed value from the configuration), or
text the engine cannot read as the question's type — the question is left
with no value and a warning names the question and the value. No retry is
made in that case.

### Repeats

- **Counted repeats** (`jr:count`) are created by the engine; the walker
  answers their questions like any other.
- **Repeats the enumerator controls** (no count): the first time the
  engine offers to add an instance of such a repeat within a given parent
  instance, the walker draws a target count uniformly among the integers
  from `min` to `max`, where `min` and `max` are the repeat's override
  range when the configuration has a `NumericRange` override for the
  repeat's name (bounds rounded inward to integers), otherwise
  `config.repeatMin()` and `config.repeatMax()`. The walker adds an
  instance each time it is offered, until that repeat holds the target
  number of instances in that parent; then it declines and moves on. A
  target of 0 means no instance is added.

### End of the interview

At the end of the form, the walker post-processes the instance (which
sets the `end` timestamp, spec 06) and serialises it with the engine's
submission serialiser (`XFormSerializingVisitor.serializeInstance`).
`Submission.end()` is the clock's time at that moment;
`instanceXml()` is the serialised instance as a UTF-8 string. As the
engine serialises submissions, elements that are not relevant are left
out of the XML.

### Limits

A walk that takes more than 100 000 navigation steps stops with an
`IllegalStateException` whose message names the submission number and the
path of the current question (this catches runaway repeats, for example a
count drawn from a very large integer range).

### Warnings

`Submission.warnings()` lists the submission's warnings in the order they
occurred; it is unmodifiable and may be empty. Every warning starts with
`submission N:` where N is the submission number.

## Acceptance on real forms

With `SimulationConfig.defaults()`, walking submissions 1 to 3 of each
form in `samples/xforms/` completes without exception. In each result,
`instanceXml()` is a well-formed XML document whose root element has the
form's `id`, `start()` is within the fieldwork period, and `end()` is not
before `start()`.

## Clarifications (answers to `07-form-walker.questions.md`)

All thirteen assumptions were reviewed one by one and are confirmed. In
particular: the instance root and repeat templates count as element names,
secondary instances do not (1); any override for a field disables the
"existing value" rule, and a blank or failed answer then clears the old
value (4); read-only comes first, then existing value, then unsupported
control (4); `walk` has no upper bound tied to `config.submissions()` (2).

## Amendment 1: time zone, enumerators, `calculate_here`, final `duration()`

These rules come from comparing simulated submissions with real SurveyCTO
exports. Everything not mentioned here is unchanged.

### Time zone

During `walk`, the JVM's default time zone is `config.timeZone()` (spec
04, Amendment 1), so the date-times stored in the instance (`start`,
`end`, `datetime` and `time` answers, `now()`) carry that zone's offset
(`2026-01-05T08:00:00.000+03:00` with `"+03:00"`). The previous default
time zone is restored when `walk` returns or throws. `Submission.start()`
and `end()` are unchanged: the clock's local date-times. The output of a
walk therefore no longer depends on the machine's time zone. (Walks must
not run concurrently with other code that depends on the default time
zone.)

### Enumerators

Each submission is made by one of `config.enumerators()` simulated
enumerators, numbered from 1:

- The enumerator of submission `number` is drawn uniformly among 1 to
  `config.enumerators()`, from `config.seed()` and `number` alone.
- The session is opened with these device properties (spec 06,
  Amendment 2): `username` is `enumerator` followed by the enumerator's
  number, zero-padded to the number of digits of `config.enumerators()`
  and to at least two digits (`enumerator03`; `enumerator007` when there
  are 120 enumerators); `deviceid` is a string of 15 decimal digits that
  depends only on `config.seed()` and the enumerator's number. Two
  different enumerators of the same configuration have different device
  ids. No other property is set (`subscriberid`, `simserial`,
  `phonenumber` stay empty).

### `calculate_here` fields

A question whose bind has the attribute `calculate-here` in the namespace
`urn:ctosim:xforms` (written by the converter, spec 02 Amendment 2) is
handled before the rules of "Questions" when the walker reaches it (it is
therefore only handled when relevant):

- its expression is evaluated in the context of that question's node,
  as the engine evaluates a `calculate` there (same functions, same
  simulated clock and randomness, `once()` with its usual meaning), and
  the result becomes the question's value; fields that depend on it are
  updated as after any answer;
- it is not counted in `answered()`, and the clock does not move;
- when the expression cannot be evaluated, the question keeps its value
  and a warning names it.

### `duration()` at the end of the interview

At the end of the form, before the instance is post-processed, every field
whose `calculate` expression calls `duration()` is recalculated, at each
of its instances, in document order; fields that depend on it are updated
as after any answer. A field computed by `once(duration())` therefore
keeps its value, while a field computed by `duration()` holds the whole
number of seconds between `Submission.start()` and `Submission.end()`.
`calculate-here` expressions are not re-evaluated at that point.

### Acceptance on the coverage form

With `samples/xforms/ctosim_coverage.xml` (converted with spec 02
Amendment 2) loaded with media directory `samples/coverage/` and
`SimulationConfig.defaults()`, for submissions 1 to 3:

- `duration` equals the whole seconds from `start()` to `end()`;
- in submissions where `time_main` is present, it is a whole number
  between 1 and `duration`;
- `pl_head` and `pl_size` hold the `head_name` and `hh_size` of the row of
  `samples/coverage/hh_preload.csv` whose `hhid` is the submission's
  `hhid`;
- `username` matches `enumerator0[1-5]`, `deviceid` is 15 digits, and
  `starttime` ends with `+00:00` or `Z`.

Clarification (Amendment 1): assumptions 14–20 of the questions file are
reviewed one by one and confirmed. In particular, the `calculate-here`
rule comes before every other question rule, including configured
overrides (17); the attribute is recognised by its namespace URI, not its
prefix (17); `duration()` is recognised as a call in the parsed
expression, not as text (19).

## Amendment 2: minimum times, speed violations, device ids, upload time

From the second real export (`samples/coverage2/`, filled with SurveyCTO
Collect). Everything not mentioned here is unchanged.

### API addition

```java
// added to Submission:
public java.time.LocalDateTime submitted();
```

### Upload time

`submitted()` is the time the server received the submission: `end()`
plus a whole number of minutes drawn uniformly between 1 and 240, from
`config.seed()` and the submission number alone. A submission built with
`Submission.of` (spec 08) has `submitted()` equal to `end()`.

### Device ids

The `deviceid` property of an enumerator (Amendment 1) is now a string
of **16 lowercase hexadecimal characters** (`0-9a-f`), like the Android
id SurveyCTO Collect reports, instead of 15 decimal digits. It still
depends only on `config.seed()` and the enumerator's number, and two
enumerators of the same configuration still have different ids.

### Minimum times

A question whose bind has the attribute `minimum-seconds` in the
namespace `urn:ctosim:xforms` (spec 02, Amendment 3) has a minimum time
`m`, its value read as a number (a value that cannot be read is ignored,
and the question then has no minimum time).

- When the clock advances after such a question (rule 7 of "Questions"),
  it advances by the drawn number of seconds or by `m` rounded up to a
  whole second, whichever is larger.
- **Speed violation.** When `m` > 0, each time the walker handles such a
  question under rule 5 or 6, a speed violation is recorded with
  probability `config.speedViolationRate()` (at most one per handling),
  drawn from `config.seed()` and the submission number. A violation does
  not change the answer or the clock.

### Speed-violation fields

At the end of the form, before the `duration()` recalculation of
Amendment 1, every relevant field whose bind has the attribute
`speed-violations` in the namespace `urn:ctosim:xforms` gets its value:

- `"count"`: the number of violations recorded in the submission, or no
  value when there were none;
- `"list"`: the violated questions, in the order the violations occurred,
  separated by commas (no spaces), or no value when there were none. A
  question is written as its path below the instance root, each step
  except the last followed by its position among same-name siblings in
  brackets: `main[1]/resp_name`, `main[1]/member[2]/member_name`.

Fields that depend on them are updated as after any answer.

Clarification (Amendment 2): assumptions 22–28 of the questions file are
reviewed one by one and confirmed. In particular, every outcome of rules
5 and 6 is one handling, so blank and failed questions can also record a
violation (23); a report field with no violations is cleared (24); paths
give every step but the last its position, including ordinary groups (25).

## Amendment 3: empty counted repeats and `search()` choices

From the export of the third coverage form (`samples/coverage3/`).
Everything not mentioned here is unchanged.

### Counted repeat with a count of 0

When the count of a counted repeat is 0 in a parent instance, that
repeat has **no instance** there: the submission's instance XML has no
element of that repeat in that parent, and none of its questions is met.
(Submission C of the export: `n_people` = 0, `person_count` = 0, no row
in the `person` long file.)

### Choices loaded with `search()`

A select question whose appearance contains a `search(...)` call
(SurveyCTO's "dynamically load choices from pre-loaded data") takes its
choices from a CSV file attached to the form, not from the choice list
alone. In the form's choice list, the **placeholder** is the first
choice whose value is not a number: its value is the name of the CSV
column holding the choice values. The form's other choices are
**static** choices (SurveyCTO requires their values to be numbers). A
question with no placeholder is treated as an ordinary select.

The call has 1, 4 or 6 arguments:
`search(source)`, `search(source, type, columns, text)`,
`search(source, type, columns, text, filterColumn, filterText)`. Each
argument is an XPath expression (a string literal or a field reference),
evaluated in the question's context and converted to a string.

- The file is `<source>.csv` in the form's media directory (spec 03,
  Amendment 3), with `.csv` not added when `source` already ends with
  it; it is read as `pulldata()` reads attached files.
- With one argument every data row is a match. Otherwise `columns` is one
  column name or several separated by commas, and a row matches when the
  value in at least one of those columns, compared with `text` as
  follows, passes: `contains` (holds `text`), `startswith`, `endswith`,
  `matches` (equals `text`). Comparisons are exact and case-sensitive.
- With six arguments, a matching row is kept only when its value in
  `filterColumn` equals `filterText` exactly.
- The **loaded choices** are the values, in the placeholder's column, of
  the kept rows, in file order, without repeats (the first occurrence
  stays) and without empty values. When the file has a column named
  `sortby`, the rows are first ordered by its numeric value (rows whose
  `sortby` is not a number come last, in file order).
- The choices given to the generator (`Question.choices`, spec 05) are
  the form's choices in order, with the placeholder replaced by the
  loaded choices; a choice filter does not apply. Answers are converted
  against these choices; the stored value is the chosen value
  (`V03`), even though the engine's own choice list does not contain
  it.

When the file is missing, has no column named after the placeholder or
after a column the call names, or the call cannot be read (another
number of arguments, or a `type` other than the four above), the loaded
choices are empty and the walk adds a warning naming the question (and
the file, when it is missing); the question then has only its static
choices.

Acceptance: walking `samples/xforms/ctosim_coverage3.xml`, loaded with
media directory `samples/coverage3`, stores in `/data/selects/village`
one of the `village_id` values of `samples/coverage3/villages.csv` whose
`region` equals the stored `/data/selects/region`, never `village_id`.

Clarification (Amendment 3): assumptions 29–36 of the questions file are
reviewed one by one and confirmed. In particular, the CSV file is read
exactly as `pulldata()` reads it, including first-use reading and
caching for the life of the `LoadedForm`, and a source ending with
`.csv` names that file (31); a header-only file, or a search with no
match, is not an error and gives no warning (33); a count of 0 concerns
repeats created from their template (34); only the offered choices and
the conversion of answers change, and a choice filter hides neither the
placeholder nor the static choices (36).

## Amendment 4: dates stored by calculations

Spec 03, Amendment 4 makes every `calculate`, including a
`calculate_here` (Amendment 1), whose result is a date or date-time
store the date only, `YYYY-MM-DD`, in the session's time zone. A field
calculated as `now()` therefore holds the walk's current date, without a
time or an offset; to record the time a point is reached, SurveyCTO's
documentation uses `once(format-date-time(now(), ...))`. Tests that read
a timestamp from such a field are superseded by that amendment; the
other timestamps of Amendment 1 (`start`, `end`, `datetime` and `time`
answers) are unchanged.

## Amendment 5: no answer violates its question's constraint

When a form is filled normally, an answer cannot violate its question's
constraint: SurveyCTO Collect checks the constraint when the enumerator
leaves the question, and checks every question again when the form is
finalised. Until now the walker checked an answer only when it gave it,
so a value could still violate its constraint at the end (a kept default,
or a constraint that refers to a question answered later). Everything
not mentioned here is unchanged.

### The guarantee

In the instance XML of every submission, every **question** that holds a
value satisfies its own constraint, evaluated on the final instance. A
question here is a field the walker meets as a question (rule 4 of
"Questions": not read-only, a control the generator has a kind for),
in every repeat instance where it is relevant. Fields without a control
(`calculate`), read-only fields, `calculate_here` fields and questions
without a constraint are not concerned.

### Kept values

Rule 2 of "Questions" keeps a value the question already has (a default,
or a value set by a calculation or an action) only when that value
satisfies the question's constraint. Otherwise the walker answers the
question as if it had no value (rules 4 to 7).

### Check at the end of the form

After the last question, before the speed-violation fields and the
`duration()` recalculation of Amendments 1 and 2, the walker goes over
the questions in form order and evaluates the constraint of each one
that holds a value:

- a question whose constraint holds is left as it is;
- a question whose constraint does not hold is answered again: the
  generator is asked for answers (fresh draws, `today` the clock's date)
  until the engine accepts one, up to 50 attempts. The clock does not
  move, no speed violation is recorded and `answered()` does not change;
- when no attempt is accepted, the question is cleared and a warning
  names it and says that its constraint does not hold at the end of the
  form (and, when it is required, a second warning says that it is
  required).

Answering or clearing a question can change other values, so the check
is repeated until a pass changes nothing; after 5 passes, every question
whose constraint still does not hold is cleared, with the same warnings,
until none remains.

Acceptance: for each form of `samples/xforms/` (the coverage-3 form with
media directory `samples/coverage3`), in submissions 1 to 20 walked with
the default configuration, every question that holds a value satisfies
its constraint on the final instance.

Clarification (Amendment 5): assumptions 37–44 of the questions file are
reviewed one by one and confirmed. In particular, "form order" is the
order of the form's body, and a repair or a clearing is visible to the
questions checked after it (37); the five passes include the first one
(38); constraints made invalid after the check by post-processing are
not concerned (39); a warning names the field and says that its
constraint does not hold at the end of the form (41).

## Amendment 6: search before leaving a question blank

A synthetic answer that breaks its constraint, or a blank left only
because the walker could not find a valid answer, defeats the purpose of
the simulator: a real enumerator always finds an accepted answer when
one exists. Hints (spec 09) cover the common constraint shapes, but a
constraint can be any expression. So, before leaving a question blank
because no answer was accepted, the walker searches.

When the 50 attempts and the candidates of spec 09 Amendment 2 have all
been rejected (rule 6 of "Questions", and the end-of-form check of
Amendment 5), the walker takes the levels of
`AnswerGenerator.searchValues(question, today)` (spec 05, Amendment 2)
in order. For a `TEXT` or `BARCODE` question, a first level comes before
them: the string literals written in the question's constraint, in order
of appearance, without duplicates (so `contains(., '@')` can be met by
`@`). For each level, it converts each value as answers are
converted and keeps those the question's constraint accepts, evaluated
without changing the instance; values that cannot be converted are
skipped. When a level has accepted values, the walker gives the engine
one of them, drawn uniformly from them with the answers' random
sequence, and stops. Only when no level has an accepted value is the
question left blank with the warning of rule 6 (or of Amendment 5).

The search does not move the clock and does not count as further
attempts. `answered()` counts the question as answered when the search
answers it under rule 6.

Acceptance: for every form of `samples/xforms/` (the coverage-3 form
with media directory `samples/coverage3`), submissions 1 to 50 walked
with the default configuration have no warning that a constraint could
not be satisfied or does not hold at the end of the form; and a form
whose questions have the constraints `. mod 7 = 3` (integer),
`int(. * 4) = . * 4 and . > 1000` (decimal), `contains(., '@')` (text),
`format-date-time(., '%d') = '13'` (date),
`format-date-time(., '%H') = '07'` (time),
`count-selected(.) = 3 and not(selected(., 'a'))` (select_multiple of
six choices) gets a valid answer to each of them in submissions 1 to 20.

## Amendment 7: rules, expressions and profiles

Uses the settings of spec 04, Amendment 3. Everything not mentioned here
is unchanged.

### Profiles

When the configuration has `profiles`, each submission draws one, in
proportion to the weights, from `config.seed()` and the submission
number alone (so it does not change the other draws of the submission).
Within that submission, `profile()` in a rule's condition or an `expr`
is the profile's name; without profiles it is `""`.

### The override of a question

When a question is reached (rule 4 of "Questions"), the walker computes
its **effective override** from `config.field(name)`:

1. the conditions of its `when` rules are evaluated in order, as XPath
   `boolean()`, in the session, with the question node as context and
   each `${x}` replaced by the path of field `x` (as the converter does,
   spec 02); the first rule whose condition is true applies, and its
   settings replace the override's settings of the same keys (`min` and
   `max` together; any other key of the override is kept). When no
   condition is true, the override's own settings apply;
2. when the resulting settings have an `expr`, it is evaluated the same
   way and its string value becomes a fixed `value` (an empty string
   leaves the question blank, like a `null` answer).

The effective override is what `AnswerGenerator.generate(question,
today, random, override)` receives (spec 05, Amendment 3), and what the
other rules of this spec read in place of `config.field(name)` (for
example the fixed value of the blank rule). A field named in the
configuration still counts as overridden for rule 2 (kept values),
whatever its rules give.

A condition or `expr` that cannot be evaluated (an unknown field, an
evaluation error) gives a warning that names the field and the
expression; such a condition counts as false, such an `expr` as absent.
A `${x}` naming no field of the form is reported by `configWarnings()`
as other unknown names are.

### Acceptance

With a configuration whose `profiles` are `{"a": 1, "b": 3}`, a text
question whose override is `{"expr": "profile()"}` holds `a` or `b` in
every submission, both occur among submissions 1 to 100 (about a quarter
`a`), and a second text question with rules `if: "profile() = 'a'"` →
`value: "x"` and otherwise `value: "y"` holds `x` exactly in the
submissions whose first question holds `a`. Walking the same submission
number twice gives the same profile.

Clarification (Amendments 6 and 7): assumptions 45–51 of the questions
file are reviewed one by one and confirmed. In particular, the search
also applies when the configuration fixes a value that breaks the
constraint (45); string literals are those of the parsed constraint
(46); a search outcome is only what the finite levels of spec 05
Amendment 2 contain (49). Codex stopped on its usage limit before
writing questions for Amendment 7; its tests were reviewed and kept.
