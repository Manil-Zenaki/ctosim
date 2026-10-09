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
  3. one per name in `LoadedForm.stubbedFunctions()`, in its order:
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
  `java -jar target/ctosim.jar FORM [options]`, containing its
  dependencies, with `ctosim.Cli` as its main class.
- `ctosim.cmd` at the repository root runs that jar with the Java of
  `JAVA_HOME` (or `java` on the `PATH`), passing its arguments through
  and returning the jar's exit status.

## Clarifications (answers to `10-cli.questions.md`)

All twelve assumptions were reviewed one by one and are confirmed. In
particular, the usage text and messages are free apart from what the spec
states (1); the converter may be replaced by a test double reached
through `--python` and `PYTHONPATH` (10); the CLI is checked against the
public pipeline it composes (11). In addition, `main` writes `out` and
`err` in UTF-8, so that file names with accents reach a calling program
intact.
