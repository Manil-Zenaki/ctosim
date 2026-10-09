# Project status and hand-over notes

Last updated: 2026-10-09. Read `CLAUDE.md`, `docs/WORKFLOW.md` and
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

Suite at commit `ca5395b`: 3335 Java tests and 269 Python tests; all green
except the disputed tests below.

## Next step: three disputes to adjudicate

Codex hit its usage limit (reset 2026-10-09 21:11) before adjudicating.
Run, with a clean tree, one after the other:

```
scripts/codex-tests.ps1 specs/03-surveycto-functions.md -Dispute
scripts/codex-tests.ps1 specs/02-xlsform-converter.md -Dispute
scripts/codex-tests.ps1 specs/10-cli.md -Dispute
```

- spec 03, Dispute 2: seven older tests still treat `pulldata` as a stub
  (`FormLoaderFunctionTest` x5, `SimulatedSessionContractTest`,
  `SurveyCtoFunctionContractTest`);
- spec 02, Disputes 3 and 4: an element-identity comparison across two
  parses; a count node expected for a plain `${size}` repeat count;
- spec 10, Dispute 1: the runaway fixture takes ~50 s to hit the
  100 000-step limit (JavaRosa navigation slows down in huge repeats),
  over the test's 45 s timeout.

Then check the whole suite is green and close the work.

End-to-end check done: `ctosim.cmd samples/coverage/ctosim_coverage.xlsx
--python .venv/Scripts/python.exe` gives `duration` = end − start,
`time_main` between 1 and `duration`, `pl_head`/`pl_size` from
`hh_preload.csv`, enumerator usernames and 15-digit device ids, UTC
offsets.

Unconfirmed export conventions (spec 08, "Not observed"): geopoint
columns in a repeat, dummies in a nested repeat, `instanceName`, and what
`comments` / `audio audit` hold. A re-export with a geopoint inside the
`member` repeat would settle the first.

## Waiting on the project owner

- Later: more XLSForms in `samples/forms/` to cover more cases.

## Final deliverable still to do

A developer guide for a coder new to the project (install, build, test,
run, path from XLSForm to simulated data) with a **glossary**. Terms that
need it: constraint hints (bounds derived from the `constraint` column —
not the XLSForm `hint` column, which is never read), field catalog,
simulated session, stubbed function, special values, engine expression
rewrites (`EngineExpressions`), submission, walker.

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
