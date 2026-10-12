# Project status and hand-over notes

Last updated: 2026-10-11. Read `CLAUDE.md`, `docs/WORKFLOW.md` and
`docs/ARCHITECTURE.md` first; this file records where the work stands and
what was learnt along the way.

## Modules

| # | Module | State |
|---|---|---|
| 01 | FormLoader | done |
| 02 | XLSForm converter (Python, `converter/`) | done; Amendment 2 (`calculate_here` as a question computed when reached) |
| 03 | SurveyCTO functions | done; Amendment 3 (`pulldata()` reads attached CSVs from a media directory) |
| 04 | SimulationConfig | done; Amendment 1 (`timeZone`, `enumerators`) |
| 05 | AnswerGenerator | done |
| 06 | Simulated sessions | done; Amendment 2 (per-session device properties) |
| 07 | FormWalker | done; Amendment 1 (time zone, enumerators, `calculate_here`, final `duration()`) |
| 08 | SurveyCtoExporter | done: wide and long CSV, checked byte for byte against real exports of the coverage form |
| 09 | Constraint hints | done |
| 10 | CLI | done: `ctosim.cmd FORM [options]` / `java -jar target/ctosim.jar` (build with `mvnw package`) |
| 11 | Stata command | done: `ctosim using FORM, ...` (`stata/`), 35 Stata tests; published on the `stata-package` branch |

Suite: 4301 Java tests, 331 Python tests and 35 Stata tests, all green (2026-10-11).

## Next step

All planned modules are done, and the second real export
(`samples/coverage2/`) is reproduced byte for byte: geopoints and
select_multiple dummies in nested repeats, nested count columns,
`instanceName`, audio audit URLs, upload times (`SubmissionDate`),
speed violations (minimum times kept by the converter, `speedViolationRate`
in the config), Android-style 16-hex device ids.

The third real export (`samples/coverage3/`, four submissions: Collect
on Android and the web, two form versions) was compared with the
simulator on 2026-10-11 and led to amendments of specs 03 (Amendment 4),
05 (Amendment 1), 06 (Amendment 3), 07 (Amendments 3 and 4) and 08
(Amendment 2), all implemented: conditional repeat functions (`count-if`
...), MD5 `hash()`, English `format-date-time`, `decimal-time()` of
text, calculations storing dates only (`now()` stores the date, as
SurveyCTO documents), counted repeats with count 0, `search()` choices
from attached CSV files, `numbers_decimal`, `month-year` and table
header (`label`) appearances, zeros for blank relevant select_multiples,
trimmed `instanceName`, comments file URLs. 65 of the 73 `oracle`
functions now give SurveyCTO's exact result; the other 8 depend on the
date, chance, the form version or the phone's language.

The four disputes these amendments raised (specs 03, 06, 07, 08) were
adjudicated on 2026-10-11: all upheld. Codex had stopped on its usage
limit before writing the spec 08 questions file for Amendment 2 (its
tests were committed by hand). Two engine limits are written in spec 03
Amendment 4: a predicate on the repeat's own step inside a condition is
not restricted to the instance, and a `NaN` result stores no value.

Still not observed: speed-violation fields when no violation occurred,
`review_*` columns. Known differences left for later (realism, not
correctness): web submissions (`deviceid` `(web)`, no audio audit,
sensors or media), sensor values, comments, Collect's media file names
(`1791745516219.m4a`), seconds in typed times, phone-language
`format-number` and phone-zone `decimal-date-time`.


## Waiting on the project owner

- **Published** on 2026-10-11: https://github.com/Manil-Zenaki/ctosim
  (public; history cleaned of the IOM addresses before the first push).
  `net install` from GitHub checked in Stata 19. To update the package:
  `scripts/publish-stata-package.ps1`, then
  `git push origin main stata-package` (from a terminal where you are
  signed in to GitHub).

- Later: more XLSForms in `samples/forms/` to cover more cases.

## Developer guide

`docs/DEVELOPER_GUIDE.md` (install, run, configuration, path from XLSForm
to simulated data, workflow, glossary). Keep it in step with the specs.

## How to work here

- Java: `JAVA_HOME="/c/Program Files/StataNow19/utilities/java/windows-x64/zulu-jdk21.0.11" MAVEN_USER_HOME="$PWD/.tools/maven-home" ./mvnw -o -q test`.
  Maven's repository is `.tools/m2` (`.mvn/maven.config`). `.tools/` is not
  versioned; a fresh clone needs one online build to fill it.
- **Check Maven's exit code before committing** (a chained command once
  committed with a failing test).
- Python: `.venv/Scripts/python -m pytest converter/tests -q`; the package is
  installed editable (`pip install -e "converter[test]"`).
- Codex: `scripts/codex-tests.ps1 specs/NN-x.md` (fresh session; commits
  with `Test-Author: codex`), `-Dispute` to adjudicate, `-Focus "<section>"`
  for an amendment. The tree must be clean; do not edit the repository
  while it runs (the script reverts out-of-scope changes).
- Codex runs on the owner's ChatGPT plan and hits usage limits (reset after
  a few hours). The machine may sleep during long waits: a sleeping
  `Start-Sleep` does not fire on time. If a session stops on the limit after writing its work,
  review it and commit it by hand with the `Test-Author: codex` and
  `Codex-Session` trailers, saying so in the message.
- Codex sandbox build: `mvnw.cmd` now finds Maven under
  `.tools/maven-home/wrapper/dists/apache-maven-3.9.16/0daed3be...`. Its
  Maven build may still hit `AccessDeniedException` on JARs (Java zip
  filesystem); Codex works around it with an in-memory `JavaCompiler`
  under `.codex-runs/`. Not blocking.
- Review each Codex question on its own merits, and probe JavaRosa before
  confirming anything doubtful.
- Probes: throwaway programs in `samples/_probe/` (ignored). Classpath:
  `./mvnw -o -q dependency:build-classpath -Dmdep.outputFile=cp.txt`, then
  `java -cp "target/classes;<cp>"`. `samples/_probe/f07/Demo.java` and
  `Prof.java` walk real forms; `migration.json` forces consent.
- Project lives at `C:\Users\manil\code\SurveyCTO_data_simulation`, outside
  OneDrive on purpose (OneDrive placeholders broke the Codex sandbox). The
  old OneDrive copy is stale.

## Engine facts learnt the hard way (JavaRosa 6.0.0)

- Unknown XPath functions fail at evaluation; SurveyCTO functions are
  stubbed or implemented (specs 01, 03).
- Body control `ref` predicates are verified at parse time in a context
  with no custom functions: a stub there makes the form unloadable.
- Binds with **identical expressions are merged** and evaluated once at the
  common ancestor: `index()`, `random()`, `uuid()` are rewritten to
  numbered internal calls (`EngineExpressions`).
- Evaluation order differs between parses of the same form: random draws
  are keyed by seed, call site, node and count (`SessionEnvironment.Draws`).
- `IAnswerData.wrapData` stores whole-number results as `IntegerData` even
  in string fields; the native `selected()` then fails on numbers, hence
  the `string()` wrapping of select functions' first argument.
- Dates compare as **whole days** since 1970 (`now() > date(today)` is
  false on the same day).
- `XFormSerializingVisitor` **omits non-relevant elements**: the exporter
  must take its columns from the field catalog.
- An itemset's `valueRef` keeps the choice filter's predicate: to list a
  filtered select's choices, expand the nodeset without predicates and read
  the value child below each item.
- Counted repeats (`jr:count`) are created by the engine; only
  enumerator-controlled repeats raise "new repeat" prompts.
- Two published sample forms are invalid as published (missing choice
  list `region`; unmatched `end group` at row 186); the converter rejects
  them with the row.
