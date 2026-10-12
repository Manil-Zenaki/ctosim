# Developer guide

This guide is for a developer who joins the project. It explains how to
install, run, test and change ctosim, and what happens between a
SurveyCTO form and its simulated data. Terms in *italics* are defined in
the [glossary](#glossary) at the end.

Other documents:

- `docs/ARCHITECTURE.md`: design and module plan.
- `docs/WORKFLOW.md`: how specifications, tests and code are written.
- `docs/STATUS.md`: where the work stands, open points, engine facts
  learnt the hard way.
- `specs/NN-*.md`: the contract of each module (the source of truth for
  behaviour).

## 1. What ctosim does

ctosim generates synthetic *submissions* for a SurveyCTO form. The
submissions respect the form's own logic (relevance, constraints,
required fields, calculations, repeats, choice filters) and are written
in SurveyCTO's export layout (wide and long CSV). Teams can then write and
test their cleaning and analysis code before fieldwork starts, and run it
unchanged on the real data.

The key design choice is that ctosim does **not** reimplement the form
logic. It drives the official ODK form engine, JavaRosa, through the
form, as a data-collection app would. The engine decides what is
relevant, what is valid and what is calculated; ctosim only proposes
answers and plays the enumerator.

```
 SurveyCTO XLSForm (.xlsx) ──► converter (Python, pyxform) ──► XForm (.xml) + field catalog (.fields.json)
                                                                     │
 attached CSVs (pulldata) ─────────────────────────────────► FormLoader (JavaRosa + SurveyCTO functions)
                                                                     │
 config (.json) ──► SimulationConfig ──► FormWalker ◄──► AnswerGenerator (+ constraint hints)
                                              │   one submission per walk, in a simulated session
                                              ▼
                                      SurveyCtoExporter ──► wide CSV, long CSVs
                                              │
                                             CLI ──► run-report.txt
```

## 2. Installing

Requirements:

- A JDK 17 or newer. The JDK bundled with Stata works:
  `C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11`.
- Python 3.10 or newer, for the converter.
- Git.

Steps, from the repository root:

1. Enable the repository's git hooks (once per clone):
   `git config core.hooksPath .githooks`.
2. Create the Python environment and install the converter:
   ```
   python -m venv .venv
   .venv\Scripts\python -m pip install -e "converter[test]"
   ```
3. Build once **online**, so that Maven fills its local repository
   (`.tools/m2`, set in `.mvn/maven.config`; `.tools/` is not versioned):
   ```
   set JAVA_HOME=C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11
   mvnw.cmd package
   ```
   Later builds can run offline (`mvnw.cmd -o ...`).

`mvnw package` produces `target/ctosim.jar`, with its dependencies in
`target/lib/`.

## 3. Running

```
ctosim.cmd FORM [options]
```

`ctosim.cmd` (repository root) runs `target\ctosim.jar` with the Java of
`JAVA_HOME`, switching the console to UTF-8 for the run. Without the
launcher: `java -jar target/ctosim.jar FORM [options]`.

| Option | Meaning | Default |
|---|---|---|
| `FORM` | the SurveyCTO XLSForm (`.xlsx`), or an XForm already converted (`.xml` with its `.fields.json` beside it) | required |
| `-c`, `--config FILE` | simulation configuration (JSON, see below) | built-in defaults |
| `-n`, `--submissions N` | number of submissions (1 to 1 000 000) | the configuration's |
| `-s`, `--seed N` | seed | the configuration's |
| `-o`, `--output DIR` | output directory | `ctosim-out/<form file name>` |
| `-m`, `--media DIR` | attached files (CSV files read by `pulldata()`) | the form's directory |
| `--server NAME` | server name in the fictitious attachment URLs | `example` |
| `--python EXE` | Python used to convert an XLSForm | `CTOSIM_PYTHON`, else `python` |
| `-h`, `--help` | usage | |

Example, with the project's environment:

```
ctosim.cmd samples\coverage\ctosim_coverage.xlsx -n 300 -s 42 --python .venv\Scripts\python.exe
```

The output directory then holds:

- `<form title>_WIDE.csv`: one row per submission;
- `<form title>.csv` and `<form title>-<repeat path>.csv`: the long
  export, one file per repeat group, linked by `PARENT_KEY` / `KEY`;
- `form.xml` and `form.fields.json`: the converted form (XLSForm input
  only), to rerun without converting;
- `run-report.txt`: form, seed, configuration, output files and every
  warning (conversion, missing attached files, unimplemented functions,
  configuration fields that the form does not have, problems met while
  filling each submission).

Exit status: 0 on success (even with warnings), 1 for a problem with an
input or output file, 2 for a wrong command line. The same inputs always
produce byte-identical files, on any machine.

### From Stata

Stata users install the command once (Stata 16 or newer):

```
net install ctosim, from("https://raw.githubusercontent.com/Manil-Zenaki/ctosim/stata-package/") replace
```

then, in any do-file or interactively (batch mode included):

```
ctosim using "household.xlsx", n(300) seed(42) clear
ctosim using "household.xlsx", n(300) seed(42) dofile("import_household.do") replace clear
```

The first form imports the wide CSV as it is; the second runs the import
do-file that SurveyCTO generates for the form (its Stata template), so the
simulated data arrive exactly as the real data will (dates converted,
labels applied). The options mirror the command line (`n`, `seed`,
`config`, `saving`, `media`, `server`); results are in `r()`; `help ctosim`
documents everything. Requirements: a Java 17 or newer (Stata 18+ ships
one; otherwise install one or set `CTOSIM_JAVA`) and, for an XLSForm, a
Python set in Stata (`python set exec`). Spec 11.

### Configuration

A JSON file; every key is optional. Full rules: `specs/04-simulation-config.md`.

```json
{
  "seed": 42,
  "submissions": 300,
  "start": "2026-03-02T08:00:00",
  "fieldworkDays": 21,
  "timeZone": "Africa/Addis_Ababa",
  "enumerators": 8,
  "missingRate": 0.05,
  "specialRate": 0.02,
  "specialValues": ["-99", "-98", "98", "99"],
  "speedViolationRate": 0.1,
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

| Key | Default | Effect |
|---|---|---|
| `seed` | 1 | all randomness |
| `submissions` | 100 | number of submissions |
| `start`, `fieldworkDays` | `2026-01-05T08:00`, 30 | each interview starts on a random day of the period, between 0 and 9 hours after `start`'s time |
| `timeZone` | `UTC` | offset carried by stored date-times |
| `enumerators` | 5 | pool of simulated enumerators (`username` `enumerator01`..., one 16-hex `deviceid` each) |
| `missingRate` | 0.05 | probability that a relevant, non-required question is left blank |
| `specialRate`, `specialValues` | 0.02, `-99 -98 ...` | probability of answering a select with a "don't know / refused" code, and which values are such codes |
| `speedViolationRate` | 0.1 | probability of a speed violation on a field with a minimum time |
| `repeats` | 1 to 3 | number of instances of repeats without a `repeat_count` |
| `integerRange`, `decimalRange` | 0–100, 0–1000 | default bounds when a constraint gives none |
| `fields` | none | per-field *overrides*: fixed `value`, `min`/`max`, choice `weights`, `texts`, own `missingRate`, and the realism keys below |
| `profiles` | none | household profiles, one drawn per submission in proportion to its weight; `profile()` in rules and expressions |
| `area` | none | rectangle (`minLatitude`, `maxLatitude`, `minLongitude`, `maxLongitude`) where GPS points are drawn |
| `textPools` | built-in | replaces the `firstName`, `lastName`, `comment` pools, or adds a `place` pool |

#### Realistic data (spec 04 Amendment 3, spec 05 Amendment 3, spec 07 Amendment 7)

```json
{
  "profiles": {"rural": 3, "urban": 1},
  "area": {"minLatitude": 8.9, "maxLatitude": 9.1, "minLongitude": 38.6, "maxLongitude": 38.9},
  "textPools": {"place": ["Adama", "Bishoftu", "Mojo"]},
  "fields": {
    "age": {"distribution": {"normal": {"mean": 32, "sd": 12}}, "min": 0, "max": 99},
    "income": {"distribution": {"lognormal": {"mean": 3000, "sd": 2000}}, "min": 0, "max": 100000,
               "when": [{"if": "profile() = 'urban'",
                         "distribution": {"lognormal": {"mean": 9000, "sd": 5000}}}]},
    "school": {"weights": {"0": 1, "1": 2, "2": 3, "3": 3},
               "when": [{"if": "${age} < 6", "value": "0"},
                        {"if": "profile() = 'rural'", "weights": {"0": 4, "1": 4, "2": 2}}]},
    "hh_members": {"expr": "${hh_size} - 1"}
  }
}
```

- `distribution`: `normal` or `lognormal` (mean and standard deviation of
  the variable itself), kept within `min`/`max` or the default range.
- `when`: rules tried in order; the first whose `if` (an expression as in
  the form, with `${field}` and `profile()`) is true replaces the field's
  settings of the same keys. Rules are evaluated when the question is
  reached, so they can use any earlier answer.
- `expr`: the answer is the value of an expression (`${a} + ${b}`,
  `pulldata(...)`, `profile()`); an empty value leaves the question blank.
- Without any setting, text questions whose name says what they hold
  (`resp_name`, `phone_number`, `email`, `village`, `other_specify`, ...)
  get plausible names, phone numbers, e-mails, places (with a `place`
  pool) and comments.

#### Constraints

Generated answers always satisfy their question's constraint when the
constraint can be satisfied: hints are derived from the constraint
(ranges with "don't know" codes, `if()`, `not()`, `selected()` and
`count-selected()` rules, lengths, `regex()`, prefixes; spec 09), the
walker tries the constraint's own values, then searches systematically
for a value the constraint accepts (spec 07 Amendment 6), and checks
every answer again at the end of the form (Amendment 5).

## 4. From XLSForm to simulated data, step by step

### 4.1 Conversion (spec 02, Python)

`python -m ctosim_xlsform form.xlsx -o form.xml` (the CLI calls it). A
SurveyCTO XLSForm is not a plain ODK XLSForm, so the converter
normalises it and then calls `pyxform`:

- renames SurveyCTO columns (`relevance` → `relevant`, `read only` →
  `read_only`, ...) and quotes bare numbers in `selected(${x}, 1)`;
- turns SurveyCTO-only types into something the engine accepts:
  `caseid`, `comments`, audits and sensors become empty hidden
  calculates; `enumerator` becomes a text question; `calculate_here`
  becomes a read-only question carrying its expression in
  `ctosim:calculate-here`;
- keeps `minimum_seconds` as `ctosim:minimum-seconds` and marks the
  speed-violation fields with `ctosim:speed-violations` (namespace
  `urn:ctosim:xforms`);
- drops disabled rows and blocks, with a warning;
- writes the *field catalog*: every survey row with its original SurveyCTO
  type, instance path and row number. The XForm alone loses the
  SurveyCTO types; the exporter needs them.

### 4.2 Loading (specs 01, 03)

`FormLoader.load(xform, mediaDirectory)` parses the XForm with JavaRosa
and makes it runnable:

- **SurveyCTO functions.** Functions that change form logic
  (`index()`, `choice-label()`, `rank-value()`, list functions, ...) are
  implemented; `pulldata()` reads the attached CSV files; the rest are
  *stubbed functions* that return a fixed value and are reported.
- **Engine expression rewrites** (`EngineExpressions`): the engine merges
  identical expressions and evaluates them once, which breaks `index()`
  and `random()` in repeats, so such calls are numbered; `today()`,
  `now()`, `random()` and `uuid()` are routed to the *simulated session*;
  select functions get `string()` around their first argument.
- `missingAttachments()` lists `pulldata()` sources with no CSV file.

### 4.3 Walking one submission (specs 05, 06, 07, 09)

`FormWalker.walk(n)` fills submission `n`:

1. Opens a *simulated session*: the clock starts on a random day and time
   of fieldwork; randomness comes from the seed and `n`; the device
   properties are those of an enumerator drawn for this submission; the
   JVM time zone is the configured one during the walk.
2. Steps through the form with the engine. For each relevant question:
   `calculate_here` fields are computed; read-only fields and existing
   values are kept; otherwise the *AnswerGenerator* proposes an answer,
   guided by *constraint hints*, and the walker retries (up to 50 times)
   when the engine rejects it.
3. Advances the simulated clock 5 to 60 seconds after each answer (at
   least the field's minimum time), possibly recording a speed violation.
4. Decides how many instances to add to repeats without a count.
5. At the end, fills the speed-violation fields, recomputes `duration()`
   fields, post-processes (sets `end`) and serialises the instance.

The *submission* holds the instance XML, start, end, upload time
(`submitted()`, 1 to 240 minutes after the end) and warnings. Elements
that are not relevant are absent from the XML.

### 4.4 Exporting (spec 08)

`SurveyCtoExporter` turns submissions into SurveyCTO's layout, as
checked byte for byte against two real exports (`samples/coverage/`,
`samples/coverage2/`):

- columns come from the field catalog, never from the XML;
- dates as `Oct 7, 2026`, times as `8:30:20 AM`;
- select_multiple: the value, then one 0/1 *dummy column* per choice
  (`assets__99` for `-99`);
- geopoints in four columns (`gps-Latitude`, ...);
- repeats: wide columns suffixed by instance numbers (`job_days_6_1_2`),
  count columns for counted repeats (`member_count`, `child_count_1`);
  long files with `PARENT_KEY`, `KEY` and `SET-OF-<repeat>`;
- media, text audit and audio audit fields: fictitious attachment URLs;
- metadata: `SubmissionDate` first; `instanceID`, `instanceName` (when
  the form defines it), `formdef_version`, `KEY` last.

## 5. Repository layout

| Path | Content |
|---|---|
| `src/main/java/ctosim/` | the Java engine (loader, functions, config, generator, sessions, walker, exporter, CLI) |
| `src/test/java/ctosim/`, `src/test/resources/` | JUnit tests, written by Codex only |
| `converter/src/ctosim_xlsform/`, `converter/tests/` | the Python converter and its pytest tests |
| `specs/` | one specification per module, with the test author's questions and the disputes |
| `samples/forms/` | published SurveyCTO forms used as test material (`samples/SOURCES.md`) |
| `samples/xforms/` | the same forms converted, plus the coverage forms |
| `samples/coverage/`, `samples/coverage2/` | fictitious forms deployed on SurveyCTO and their real exports (test fixtures) |
| `scripts/codex-tests.ps1` | runs Codex as the independent test author |
| `ctosim.cmd` | the command-line launcher |
| `stata/` | the Stata command (`ctosim.ado`, help, `ctosim.pkg`, `stata.toc`) |
| `src/stata-bridge/` | the helper that the Stata command loads with `javacall` (compiled for Java 8) |
| `src/test/stata/` | the Stata tests (do-files, run in batch mode), written by Codex only |

## 6. Building and testing

```
set JAVA_HOME=C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11
set MAVEN_USER_HOME=%CD%\.tools\maven-home
mvnw.cmd -o test                                  &rem Java tests
mvnw.cmd -o package                               &rem tests + target/ctosim.jar
.venv\Scripts\python -m pytest converter/tests -q &rem Python tests
```

Always check Maven's exit status before committing.

The Stata command (spec 11) has its own build and tests:

```
powershell -File scriptsuild-stata-package.ps1    &rem target/stata-package (+ stata/ctosim_bridge.jar)
powershell -File scripts\stata-tests.ps1            &rem each src/test/stata/test_*.do, in batch mode
powershell -File scripts\publish-stata-package.ps1  &rem commit the package to the stata-package branch
git push origin stata-package                        &rem make it installable with net install
```

The Stata tests use every installed Stata (`C:\Program Files\Stata*`),
or the one named by `STATA_EXE`. Stata ignores `shell` in batch mode on
Windows, so the command and the tests call Java through `javacall`.

## 7. Changing the code

Read `docs/WORKFLOW.md` first. In short:

1. **Spec first.** A change of behaviour starts as a new or amended
   specification (`specs/NN-*.md`): observable behaviour only, no
   implementation hints, no suggested tests. API changes come with stubs
   that throw `UnsupportedOperationException`. Commit: `docs(spec): ...`.
2. **Independent tests.** `scripts/codex-tests.ps1 specs/NN-x.md`
   (`-Focus "<amendment title>"` for an amendment) runs Codex in a fresh
   session; it writes the tests and its questions, and the script commits
   them with a `Test-Author: codex` trailer. Claude (or any implementer)
   never edits `src/test/`; the commit hook enforces it.
3. **Answer the questions** by amending the spec, one by one; probe
   JavaRosa when unsure.
4. **Implement** until the suite is green. Commit: `feat(module): ...`.
5. **Disputes.** When a test contradicts the spec, write the argument in
   `specs/NN-x.disputes.md` and run the script with `-Dispute`; Codex
   decides.

Commits are atomic and follow Conventional Commits. Practical notes on
Codex (usage limits, sandbox builds) and on the engine are in
`docs/STATUS.md`.

To learn the engine, write throwaway programs in `samples/_probe/`
(ignored), with the class path from
`mvnw -o -q dependency:build-classpath -Dmdep.outputFile=cp.txt`.

## 8. Known limits

- Answers are plausible in type and range, not in meaning: text answers
  look like `resp_name 6220` unless `texts` overrides them.
- Only `pulldata()` reads attached CSV files. Choice lists read from
  files (`select_one_from_file`, `search()`) have not been tested;
  unimplemented SurveyCTO functions return a fixed value and are listed
  in the run report.
- No real attachment files are produced (photos, audits, comments): their
  columns hold fictitious URLs.
- The export conventions not yet seen in a real export are listed in
  `docs/STATUS.md` and in spec 08.

## Glossary

**Attached file / media directory.** A CSV file deployed with a form and
read by `pulldata()`; ctosim reads them from the media directory (the
form's directory by default).

**Constraint hints.** Bounds, lengths and patterns read from a question's
`constraint` expression (for example `. >= 15 and . <= 99`), so that the
generator draws valid answers directly instead of retrying. Not related to
the XLSForm `hint` column, which is never read. Spec 09.

**Converter.** The Python package `ctosim_xlsform` that turns a SurveyCTO
XLSForm into an XForm and a field catalog. Spec 02.

**Counted repeat.** A repeat group with a `repeat_count`: the engine
creates its instances. SurveyCTO exports its count in a `<repeat>_count`
column.

**Dummy columns.** The 0/1 columns that follow a select_multiple in an
export, one per choice, named `<field>_<value>` with `-` replaced by `_`.

**Dispute.** A documented disagreement with a test, adjudicated by a fresh
Codex session on the spec alone (`specs/NN-*.disputes.md`).

**Engine expression rewrites.** The loader's changes to a copy of the
form's expressions, where JavaRosa's behaviour differs from SurveyCTO's
or where calls must use the simulated session (`EngineExpressions`). The
user's form is never changed.

**Enumerator (simulated).** One of `enumerators` fictitious data
collectors; each submission is assigned one, which gives its `username`
and `deviceid`.

**Enumerator-controlled repeat.** A repeat without a count: the
enumerator decides how many instances to add; ctosim draws that number
from `repeats` or the field's override.

**Field catalog.** `form.fields.json`, written by the converter: every
survey row's name, original SurveyCTO type, instance path and row. It
gives the exporter its columns and types.

**Long / wide export.** SurveyCTO's two CSV layouts: wide has one row per
submission with repeated fields spread over numbered columns; long has a
main file plus one file per repeat group, linked by keys.

**Minimum time / speed violation.** A field's `minimum_seconds`; leaving
it earlier records a speed violation, counted and listed in the
`speed violations count` / `list` fields.

**Override.** A per-field setting in the configuration's `fields`
(fixed value, range, weights, texts, missing rate), applied by the
answer generator.

**Simulated session.** An engine session whose clock (`today()`, `now()`,
`duration()`, `start`, `end`), randomness (`random()`, `uuid()`,
`instanceID`) and device properties come from the simulation instead of
the machine, so that runs are reproducible. Spec 06.

**Special values.** Choice values that code "don't know", "refused" and
the like (`-99`, `98`, ...); the generator chooses them with probability
`specialRate`.

**Stubbed function.** A SurveyCTO function that ctosim does not implement;
it is evaluable but returns a fixed value, and the run report lists it.

**Submission.** One simulated interview: its instance XML (as the engine
serialises it), start, end, upload time, number of answers and warnings.

**Walker.** `FormWalker`, which fills one submission by navigating the
form with the engine and playing the enumerator. Spec 07.

**XForm.** The XML form definition that the ODK engine runs; the
canonical input of the Java side.
