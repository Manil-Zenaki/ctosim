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
