# Spec 11 — Stata command `ctosim`

## Purpose

Let Stata users simulate a SurveyCTO form without leaving Stata: one
command runs the simulator (spec 10) with the Java that Stata ships and
the Python that Stata is configured with, then loads the simulated data,
either with a plain import or with the form's SurveyCTO import do-file,
so that the data arrives exactly as the real data will.

Language: Stata (ado-file), Stata 16 or newer. Files under `stata/`:
`ctosim.ado` (the command), `ctosim.sthlp` (its help), `ctosim.pkg` and
`stata.toc` (for `net install`).

## Syntax

```
ctosim using FORM [, n(#) seed(#) config(filename) saving(dirname)
                     media(dirname) server(name) dofile(filename)
                     replace clear]
```

`FORM` is a SurveyCTO XLSForm (`.xlsx`) or a converted XForm (`.xml`,
with its `.fields.json` beside it), as for the command line (spec 10).
Paths may be relative to the current working directory and may contain
spaces (quoted).

| Option | Meaning | Default |
|---|---|---|
| `n(#)` | number of submissions, an integer from 1 to 1 000 000 | the configuration's |
| `seed(#)` | seed, an integer | the configuration's |
| `config(filename)` | simulation configuration (spec 04) | none (defaults) |
| `saving(dirname)` | output directory | `ctosim-out/<FORM's file name without extension>`, relative to the current working directory |
| `media(dirname)` | attached files (CSV files read by `pulldata()`) | FORM's directory |
| `server(name)` | server name in attachment URLs | `example` |
| `dofile(filename)` | a SurveyCTO import do-file to load the data with (below) | none: plain import |
| `replace` | allow writing into an output directory that already holds a run | |
| `clear` | allow replacing the data in memory | |

The options are passed to the simulator as the command line's `-n`,
`-s`, `-c`, `-o`, `-m` and `--server` (spec 10).

## What the command does

1. **Checks, before running anything**, and stops with the error code
   given:
   - the data in memory have changed since last saved (`c(changed)` is 1)
     and `clear` is not given: error 4 (Stata's usual "no; dataset in
     memory has changed since last saved");
   - FORM does not exist: error 601; it ends neither in `.xlsx` nor in
     `.xml` (any case): error 198;
   - `config()` or `dofile()` names a file that does not exist: error 601;
   - the output directory already contains a `run-report.txt` and
     `replace` is not given: error 602;
   - `n()` or `seed()` is not an integer, or `n()` is outside 1 to
     1 000 000: error 198 (or the error `syntax` gives for a malformed
     option);
   - the simulator, Java or (for an `.xlsx` FORM) the converter or Python
     cannot be found (below): error 601, with a message saying what is
     missing and how to provide it.
2. **Runs the simulator** with the resolved Java, FORM and options, and
   shows its messages in the Results window. When the simulator exits with
   status 2 the command stops with error 198; with any other non-zero
   status, with error 498. In both cases the simulator's own error
   messages are shown first, and the data in memory are unchanged.
3. **Shows the run's warnings**: the number of warnings, then each
   warning line of `run-report.txt` (spec 10), one per line.
4. **Loads the data**, replacing the data in memory:
   - **without `dofile()`**: the wide CSV file (spec 08) is imported as
     `import delimited` does with `varnames(1)`, `encoding("utf-8")` and
     `bindquotes(strict)`: one observation per submission, one variable
     per column, Stata's default variable naming and type guessing;
   - **with `dofile()`**: the do-file is run (as `run` does: its output is
     not shown) with the output directory as the current working
     directory, so that a SurveyCTO import template finds the CSV file it
     names. The current working directory is restored afterwards, also
     when the do-file fails; an error in the do-file stops the command
     with that error's code. The data in memory are then what the
     do-file left.
5. **Returns** in `r()`:
   - `r(N)`: the number of submissions simulated;
   - `r(warnings)`: the number of warnings;
   - `r(seed)`: the seed used (a scalar);
   - `r(form_id)`: the form id;
   - `r(outdir)`: the output directory, as an absolute path;
   - `r(wide)`: the wide CSV file's absolute path;
   - `r(report)`: `run-report.txt`'s absolute path.

The values of `r(N)`, `r(warnings)`, `r(seed)` and `r(form_id)` are those
of the run report.

## Finding the programs

- **Simulator.** The file named by the environment variable
  `CTOSIM_JAR` when it is set and not empty; otherwise `ctosim.jar`
  found along the ado-path (where `net install` puts it).
- **Java.** The Java that Stata uses (`java query` gives its home): its
  `bin/java` (`bin\java.exe` on Windows).
- **Converter** (`.xlsx` FORM only). The file named by the environment
  variable `CTOSIM_CONVERTER` when it is set and not empty; otherwise
  `ctosim_converter.zip` found along the ado-path. It is an archive of the
  converter and its libraries, made importable by the simulator's Python.
- **Python** (`.xlsx` FORM only). The Python that Stata is set to use
  (`python query`), or, when Stata has none set, the one named by
  `CTOSIM_PYTHON`. When none is found the command stops with error 601
  and says to run `python set exec`.

## Determinism

Two runs with the same FORM, options, configuration and attached files
write the same files and load the same data (as the command line
guarantees, spec 10).

## Help file

`ctosim.sthlp` documents the syntax, every option, the two ways of
loading the data, the stored results, the programs needed (Stata's Java;
a Python for XLSForms) and one example of each loading mode.

## Packaging (not covered by unit tests)

- `scripts/build-stata-package.ps1` builds `target/stata-package/` with
  `ctosim.ado`, `ctosim.sthlp`, `ctosim.pkg`, `stata.toc`, `ctosim.jar`
  (the simulator in one file, with its dependencies) and
  `ctosim_converter.zip` (the converter and its libraries, all pure
  Python).
- That directory is published on the branch `stata-package` of the
  project's public GitHub repository; users install with
  `net install ctosim, from("https://raw.githubusercontent.com/<owner>/<repo>/stata-package/") replace`
  and update with `ado update ctosim, update`.

## Tests

Tests are do-files under `src/test/stata/`, run in batch mode by
`scripts/stata-tests.ps1` with `stata/` on the ado-path and
`CTOSIM_JAR` / `CTOSIM_CONVERTER` pointing to the files built by
`scripts/build-stata-package.ps1`. A test passes when its do-file runs to
the end without error.

## Clarifications (answers to `11-stata-command.questions.md`)

The eleven points were reviewed one by one. Assumptions 1–8, 10 and 11 are
confirmed; in particular a simulator test double reached through
`CTOSIM_JAR` is a valid way to test the command (4), and relative
`dofile()` paths are resolved from the caller's directory (6). Point 9 is
superseded by Amendment 1 for Java.

## Amendment 1: which Java, and batch mode

The simulator needs **Java 17 or newer**. Stata 18 and later ship one;
Stata 16 and the first releases of Stata 17 ship Java 11. The "Java" rule
of "Finding the programs" is replaced by:

- **Java.** The first of these that exists and is a Java 17 or newer:
  1. the executable named by the environment variable `CTOSIM_JAVA`, when
     it is set and not empty (it is then used only if it is Java 17 or
     newer);
  2. the Java that Stata uses (`java query`);
  3. `bin/java` (`bin\java.exe` on Windows) under the environment
     variable `JAVA_HOME`, when it is set and not empty;
  4. `java` found on the system path.

  When none is found, the command stops with error 601 before running
  anything, with a message saying that ctosim needs Java 17 or newer, the
  version of Stata's own Java, and how to provide one (install a free Java
  17 such as Eclipse Temurin, or set `CTOSIM_JAVA` to its `java`
  executable).

In addition:

- **Helper.** The command also uses its helper `ctosim_bridge.jar`,
  installed with it and found along the ado-path. It is not the
  simulator: `CTOSIM_JAR` does not replace it. When it is missing, the
  command stops with error 601 saying to reinstall the package.
- **Batch mode.** The command works the same in an interactive session
  and in a do-file run in batch mode (`stata /e do file.do`), on Windows
  included (where Stata ignores `shell` in batch mode).
