# Spec 10 — Command line

## Purpose

One command turns a SurveyCTO form into simulated data: it converts the
XLSForm (spec 02), loads the form with its attached files (specs 01, 03),
reads the configuration (spec 04), walks the requested number of
submissions (spec 07), writes the SurveyCTO wide and long exports (spec
08) and a run report.

Language: Java. Package `ctosim`.

## Public API

```java
public final class Cli {
    public static int run(String[] args, java.io.PrintStream out, java.io.PrintStream err);
    public static void main(String[] args);   // System.exit(run(args, System.out, System.err))
}
```

`run` never calls `System.exit` and never throws for any input: every
failure is reported on `err` and in the returned exit status. A null
argument throws `NullPointerException`.

## Usage

```
ctosim FORM [options]
```

| Option | Value | Default | Meaning |
|---|---|---|---|
| `-c`, `--config` | file | none: `SimulationConfig.defaults()` | Simulation configuration (spec 04). |
| `-n`, `--submissions` | integer, 1 to 1 000 000 | the configuration's `submissions` | Number of submissions; replaces the configuration's value. |
| `-s`, `--seed` | integer (Java `long`) | the configuration's `seed` | Seed; replaces the configuration's value. |
| `-o`, `--output` | directory | `ctosim-out/<FORM's file name without extension>`, relative to the working directory | Where every output file is written. |
| `-m`, `--media` | directory | the directory containing FORM | Attached files (CSV files read by `pulldata()`). |
| `--server` | name | `example` | Server name in attachment URLs (spec 08). |
| `--python` | executable | environment variable `CTOSIM_PYTHON` when set and not empty, otherwise `python` | Python used to convert an XLSForm. |
| `-h`, `--help` | | | Print the usage on `out` and exit with status 0, ignoring every other argument. |

- Options may come before or after FORM; each option takes the next
  argument as its value. An option given twice keeps its last value.
- FORM is required, and exactly one non-option argument is allowed.

### FORM

- A file ending in `.xlsx` (any case) is a **SurveyCTO XLSForm**. It is
  converted by running `<python> -m ctosim_xlsform FORM -o <output>/form.xml`
  (spec 02, "Command line"), which writes `form.xml` and
  `form.fields.json` in the output directory. The converter's standard
  error lines are its warnings when it exits with status 0.
- A file ending in `.xml` (any case) is an **XForm already converted**;
  its field catalog is the file of the same path with the extension
  replaced by `.fields.json`. Neither file is copied to the output
  directory.

## What a run does

1. Read the configuration, then apply `-n` and `-s`.
2. Create the output directory (and its missing parents).
3. For an XLSForm, convert it as above.
4. Load the XForm with the media directory (`FormLoader.load(xform,
   media)`) and the field catalog (`FieldCatalog.load`).
5. Walk submissions 1 to N with one `FormWalker` (spec 07).
6. Write the exports with `SurveyCtoExporter(catalog, form, server)`
   `.writeAll(submissions, output)` (spec 08).
7. Write the run report `run-report.txt` in the output directory.
8. Print on `out` one line saying how many submissions of which form id
   were written to which directory, and one line giving the number of
   warnings and the report's file name.

Files in the output directory that the run does not write are left
alone; files it writes replace existing ones.

## Run report

`run-report.txt`, UTF-8 without byte-order mark, lines separated by LF,
ending with one LF:

```
ctosim run report
Form: <title> (<form id>, version <version>)
Source: <FORM's file name>
Configuration: <configuration file's name, or "defaults">
Seed: <seed>
Submissions: <N>
Fieldwork: <start, ISO yyyy-MM-ddTHH:mm:ss> for <fieldworkDays> days, time zone <time-zone ID>
Enumerators: <enumerators>
Files:
  <file name>
  ...
Warnings: <count>
  <warning>
  ...
```

- `<version>` is `none` when the form has no version.
- `Files:` lists the file names written, one per line indented by two
  spaces, in this order: the wide file, the long files in the exporter's
  order, then `form.xml` and `form.fields.json` when FORM is an XLSForm,
  then `run-report.txt`.
- `Warnings:` gives their number, then one line per warning, indented by
  two spaces, in this order:
  1. the converter's warnings, in its order;
  2. one per name in `LoadedForm.missingAttachments()`, in its order:
     `attached file <name>.csv not found`;
  3. one per name in `LoadedForm.stubbedFunctions()`, in its order,
     except `duration` and `username`, which simulated sessions implement
     (spec 06):
     `function <name>() is not implemented by the simulator; it returns a fixed value`;
  4. `FormWalker.configWarnings()`, in its order;
  5. each submission's warnings, by submission number then in order.
  A warning that contains line breaks has each replaced by a space.
- The report holds no absolute path and no wall-clock time.

## Determinism

Two runs with the same FORM (and catalog), configuration, attached
files and options write byte-identical files, whatever the machine's
time zone or the working directory.

## Exit status and errors

| Status | When |
|---|---|
| 0 | the run completed (warnings do not change the status), or `--help` |
| 1 | an input or output problem: the configuration cannot be read or is invalid; FORM does not exist; the converter cannot be started or exits with a non-zero status; the catalog of an `.xml` FORM is missing or invalid; the form cannot be loaded; the media directory is invalid; a walk fails; a file cannot be written |
| 2 | a usage problem: no FORM or more than one; an unknown option; an option without its value; a `-n` or `-s` value that is not an integer in range; a FORM whose name ends neither in `.xlsx` nor in `.xml` |

- For status 2, `err` receives a message naming the problem followed by
  the usage. For status 1, `err` receives a message naming the file or
  step that failed (with the converter's own error output when the
  converter failed, and the executable's name when it could not be
  started). Nothing is printed on `out` for statuses 1 and 2.
- Usage problems are detected before any file is read or written.
- When status 1 occurs, files already written may remain; the run report
  is written only by a run that completes.

## Packaging (not covered by unit tests)

- `mvnw package` builds `target/ctosim.jar`, runnable with
  `java -jar target/ctosim.jar FORM [options]`, with `ctosim.Cli` as its
  main class; its dependencies are copied to `target/lib/`, which the
  jar's manifest puts on its class path.
- `ctosim.cmd` at the repository root runs that jar with the Java of
  `JAVA_HOME` (or `java` on the `PATH`), passing its arguments through
  and returning the jar's exit status. It switches the console to the
  UTF-8 code page for the run and restores the previous one afterwards.

## Clarifications (answers to `10-cli.questions.md`)

All twelve assumptions were reviewed one by one and are confirmed. In
particular, the usage text and messages are free apart from what the spec
states (1); the converter may be replaced by a test double reached
through `--python` and `PYTHONPATH` (10); the CLI is checked against the
public pipeline it composes (11). In addition, `main` writes `out` and
`err` in UTF-8, so that file names with accents reach a calling program
intact.

## Amendment 1: unsatisfied constraints in the report and on `out`

A question that no answer could satisfy is almost always a mistake in
the form, and the user must see it without reading every warning. The
run now reports these questions in a section of their own, built from
`Submission.unsatisfiedConstraints()` (spec 07, Amendment 8). Everything
not mentioned here is unchanged; the warnings still list every case.

### In the run report

When at least one submission has an unsatisfied constraint, the report
holds a section placed after the `Enumerators:` line and before
`Files:`:

```
Unsatisfied constraints: <F>
  <field>, row <row>: <constraint>
    submissions (<k> of <N>): <s1>, <s2>, ...
    in submission <s1>: <name> = <value>; <name> = <value>
  ...
```

- The records of all submissions are grouped by question: two records
  concern the same question when their paths are equal once repeat
  indices are removed. `<F>` is the number of questions. The questions
  are listed in the order of their first record (by submission number,
  then in the order of that submission's records).
- `<field>` is the question's name. `, row <row>` gives the question's
  row in the XLSForm, from the field catalog entry whose `xpath` is the
  question's path without repeat indices; it is left out when the
  catalog has no such entry. `<constraint>` is the record's
  `constraint`, from the question's first record.
- `<k>` is the number of distinct submissions with a record for the
  question and `<N>` the number of submissions of the run. The
  submission numbers follow in increasing order, separated by `, `;
  after the twentieth, the list ends with `, ...`.
- The `in submission` line gives the `references` of the question's
  first record, in their order, as `<name> = <value>` separated by `; `,
  a value being written `(blank)` when it is the empty string. The line
  is left out when that record has no references.
- In every line of the section, each line break is replaced by a space.

When no submission has an unsatisfied constraint, the report has no
such section and is as before.

### On `out`

When the section is present, the run prints a third line on `out`,
after the two of "What a run does": it gives `<F>`, says that their
constraints could not be satisfied, names the questions in the order of
the section, separated by `, `, and names `run-report.txt`.

Clarification (Amendment 1): assumptions 13–17 of the questions file are
reviewed one by one and confirmed. In particular, the third line on `out`
has no exact sentence, only the stated content (13); questions are
grouped by their path without repeat indices, so equal names at
different paths stay distinct, and the catalog row is looked up by that
full path (14); isolated CR and LF both become spaces (15); a run
without records is unchanged in every respect (17).

## Amendment 2: fictitious attached files (`--fake-attachments`)

A run can replace the attached files the form reads but the media
directory lacks by fictitious ones (spec 12). Everything not mentioned
here is unchanged; without the option, a run is as before.

| Option | Value | Meaning |
|---|---|---|
| `--fake-attachments` | none | Build fictitious files for the attached files that are missing. |

With the option, after loading the form (step 4 of "What a run does"):

1. `FakeAttachments.of(form, seed)` is built with the run's seed.
2. When its `files()` is not empty, each file is written as
   `<output>/attachments/<name>.csv`, the CSV files of the media
   directory are copied there too (a file of the same name is not
   overwritten by a fictitious one, since only missing files are
   built), and the form is loaded again with that directory as its
   media directory.
3. Each question of `keyAnswers()` that the configuration does not name
   in `fields` is answered as if the configuration gave it
   `{"texts": <its keys>}`; when its keys are row numbers (`1` to `N`,
   a key looked up with an `int` field, spec 12), as if it gave it
   `{"min": 1, "max": N}` instead, since `texts` does not apply to an
   integer question (spec 05).

The run report then has, after the `Enumerators:` line (and before the
section of spec 10 Amendment 1, when there is one), the line
`Fictitious attachments: <name>.csv, <name>.csv` listing the files of
`files()` in their order, and the `Files:` list includes
`attachments/<name>.csv` for each, after the long files. The warning
`attached file <name>.csv not found` is not given for those files, and
`out` receives one more line, after the others, naming them and saying
that their content is invented.

Clarification (Amendment 2): assumptions 18–24 of the questions file are
reviewed one by one. 18, 19, 20, 22, 23 and 24 are confirmed; in
particular any entry naming a question in `fields` prevents its key
answers (22), and a write or copy failure is an output problem, status 1
(24). Assumption 21 raised a real gap: `texts` has no effect on an
integer question, so integer keys would never match their rows. Step 3
now gives such a question the range `1` to `N` of its keys; the test that
checked the `texts` semantics for integer keys is disputed accordingly.

## Amendment 3: form errors, made impossible to miss

Form errors (spec 07, Amendment 10) are mistakes in the form that would
block an enumerator. The run must make them obvious, so that the user
opens the report. Everything not mentioned here is unchanged.

### In the run report

When at least one submission has a form error, the report holds, right
after the `Enumerators:` line and before every other optional section
(Amendments 1 and 2), a section:

```
FORM ERRORS (an enumerator would be blocked): <F>
  <field>, row <row>: <message>
    submissions (<k> of <N>): <s1>, <s2>, ...
  ...
```

built as the section of Amendment 1: records grouped by the field's path
without repeat indices, `<F>` the number of fields, in the order of their
first record; `, row <row>` from the field catalog, left out when the
catalog has no entry; `<message>` the first record's message; the
submission list as in Amendment 1 (twenty numbers, then `, ...`).

### On `out`

When the section is present, `out` receives, after every other line, a
line starting with `FORM ERRORS:` that gives `<F>`, says that the form has
errors that would block an enumerator, names the fields in the order of
the section, separated by `, `, and names `run-report.txt`.

The exit status is still 0: the data are written.

Clarification (Amendment 3): assumptions 25–29 of the questions file are
reviewed one by one and confirmed. In particular, the `FORM ERRORS:` line
is checked for its prefix, the standalone count, the field names in
order, `run-report.txt` and the words "block" and "enumerator", and is
the last line of `out` (25); the report reproduces the first record's
message verbatim, records grouped by unindexed path in order of first
occurrence (26, 27); the submission lists hold only submissions with a
record, sorted numerically (28); the other sections, warnings, exports
and the exit status are unchanged, and a rerun without errors has no
section (29).

## Amendment 4: the fieldwork plan, and `--id`

A run now simulates its submissions through the fieldwork plan
(spec 13): step "Walk" of "What a run does" builds
`new Fieldwork(form, config)` and calls `walkAll()`, instead of calling
`FormWalker.walk(k)` for each `k`. The configuration warnings of the run
are those of `Fieldwork.configWarnings()`, and the plan's `warnings()`
are added after the submissions' own warnings. Everything else is
unchanged: the exports, the report and `out` are built from the
returned submissions as before.

| Option | Value | Meaning |
|---|---|---|
| `--id` | a question name | The question holding the survey id; replaces the configuration's `id` (spec 04, Amendment 4). |

- `--id` takes a non-empty name without leading or trailing white space;
  otherwise, or without a value, it is a usage error (status 2), as for
  the other options. Given twice, it is a usage error too.
- With `--fake-attachments`, the key answers (Amendment 2) are set before
  the plan is built, so that a key question named by the id takes its
  possible values from them (spec 13, "Survey ids").

Clarification (Amendment 4): assumptions 30–34 of the questions file are
reviewed one by one and confirmed: `--id` follows the name rule of spec
04 Amendment 4 and is checked before any input is read (30); the run is
compared with the public `Fieldwork` API on the same inputs (31); `--id`
changes nothing else, and the plan's configuration warnings take the
place of the walker's while its other warnings come last (32); key
answers of Amendment 2 are set before the plan is built, and an assigned
id is never blanked by the missing rate (33); runs are deterministic
across directories and time zones (34).

### Clarification of Amendment 4: fictitious files sized for the survey ids

With `--fake-attachments`, step 1 of Amendment 2 now builds
`FakeAttachments.of(form, seed, id, N)`, with the run's survey id
question (`--id` or the configuration's `id`, null when none) and number
of submissions (spec 12, Amendment 1).

Clarification of the sizing (Clarification of Amendment 4): assumptions
35–38 of the questions file are reviewed one by one and confirmed: the
effective seed, number of submissions and id (after `-n`, `-s` and `--id`)
are passed (35); sizing follows spec 12 Amendment 1 and its Clarification
(36); the CLI infers no id and resizes no existing media (37); the larger
files are those the run uses (38).

## Amendment 5: injected problems, `--problems`

A run can inject problems into the fieldwork and write their answer key
(spec 14). Everything not mentioned here is unchanged.

| Option | Value | Meaning |
|---|---|---|
| `--problems` | none | Inject problems: at the configuration's `problems` rates when it has them, at `ProblemRates.defaults()` otherwise (spec 04, Amendment 6). |

- Problems are injected when `--problems` is given or the configuration
  has `problems`. Given twice, `--problems` is a usage error (status 2).
- When problems are injected, the run writes `answer-key.csv` in the
  output directory: `AnswerKey.csv(fieldwork.problems())` (spec 14), as
  UTF-8 without byte-order mark. The `Files:` list of the run report
  names it right after the long files (and before the fictitious
  attachments, if any). Without problems, the file is not written.
- The run report holds, after the `Enumerators:` line and after the
  sections of Amendments 1 to 3, a line
  `Problems: <P> injected, listed in answer-key.csv`, where `<P>` is the
  number of distinct submissions in `problems()`; without problems, no
  such line.
- When problems are injected, `out` receives, after the line giving the
  number of warnings and before the lines of Amendments 1 to 3, one line
  that gives `<P>` and names `answer-key.csv`.
