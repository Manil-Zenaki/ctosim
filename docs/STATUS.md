# Project status and hand-over notes

Last updated: 2026-10-25. Read `CLAUDE.md`, `docs/WORKFLOW.md` and
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
| 12 | Fictitious attachments (`fakepreload`) | done 2026-10-17: `--fake-attachments`, Stata `fakepreload`; disputes adjudicated |
| 13 | Fieldwork plan (`Fieldwork`) | done 2026-10-23: Amendments 1-3 (quota, cascade ids, steering); CLI `--id`, Stata `id()` |
| 14 | Injected problems (`AnswerKey`) | phase 2a 2026-10-23 (duplicates, durations, off hours, overlaps, travel; CLI `--problems`, Stata `problems`); phase 2b 2026-10-25 (Amendments 1-2: faulty enumerators, outliers within constraints, consent and fake refusals); Amendment 3 (form left open, `duration()` as screen time) |

Suite (2026-10-18): 1083 Python, 7919 Java and 66 Stata tests, all green.

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

Spec 07 Amendment 5 (2026-10-12): no answer in a submission violates its
question's constraint; the walker re-checks every answered question at
the end of the form, as Collect does when finalising. A question whose
constraint is a disjunction (`(. >= 0 and . <= 10) or . = 99`) gives no
hints, so 50 random draws can still fail and leave it blank (a few per
hundred submissions in the IPA and migration forms); hints for
disjunctions would be a spec 09 amendment.

2026-10-12, constraints and realism: spec 09 Amendment 2 (hints from
`or`, `if()`, `not()`, select rules and prefixes; candidate values),
spec 05 Amendment 2 and spec 07 Amendment 6 (systematic search for an
accepted answer before leaving a question blank), spec 04 Amendment 3,
spec 05 Amendment 3 and spec 07 Amendment 7 (distributions, conditional
rules `when`, expressions `expr`, household profiles, survey area,
plausible texts by field name). All implemented and adjudicated. The
Stata help file now shows basic and advanced use cases (spec 11
Amendment 3).

The four disputes these amendments raised (specs 03, 06, 07, 08) were
adjudicated on 2026-10-11: all upheld. Codex had stopped on its usage
limit before writing the spec 08 questions file for Amendment 2 (its
tests were committed by hand). Two engine limits are written in spec 03
Amendment 4: a predicate on the repeat's own step inside a condition is
not restricted to the instance, and a `NaN` result stores no value.

2026-10-17, validation on a real form (the team's own forms stay in
`samples/private/`, ignored by git): converter Amendments 4 and 5 (long
runs of empty rows, translations with ctosim's bind columns), then
**unsatisfied constraints** (spec 07 Amendment 8, spec 10 Amendment 1,
spec 11 Amendment 4): a question that no answer could satisfy, usually a
mistake in the form, is listed in its own section of the run report and
of the command line, in red in Stata, with its XLSForm row, its
constraint as written, the submissions and the values it referred to;
`r(unsatisfied)` counts them. In Stata, report lines are now shown through
a small Mata printer: `display` expanded `${name}` in them, even through
`macval()` inside the report loop. Then the six CIV Land forms (also private): all simulate and their
SurveyCTO import do-files run, after spec 02 Amendment 6 (`or_other`
gives the choice `other` and a field `<name>_other`, as SurveyCTO does;
library messages no longer leak into the warnings) and spec 07
Amendment 9 (an integer that sets a repeat count is drawn from the
`repeats` range, so nested repeats stay small). With fictitious attachments for
them (kept with the forms), two more fixes: spec 03 Amendment 5 (a number
as the second argument of `selected()`) and spec 09 Amendment 3 (answers
drawn from a `regex()` pattern use letters, digits and spaces; a `${` in
a generated text broke the engine's labels). Performance, on the
3 400-row CIV form: conversion 215 s to 21 s (pyxform compared repeat
sections by serialising them; compared by identity while it runs), load
8.9 s to 6 s (live DOM lists), 0.9 s to 0.35 s per submission (the
parsed form is serialised once and each session reads its own copy, as
ODK Collect does; constraint texts then come from the binds). Next possible step (owner, later):
simulating several form versions in one data set, as SurveyCTO exports
data collected while the form changed.

2026-10-17 night, GIL forms (33 real forms of 8 projects, private): with
`fakepreload`, all simulate after spec 02 Amendment 7 (typographic quotes
kept; labels and hints of hidden calculates ignored), spec 09 Amendment 4
(patterns with lookaround assertions) and spec 02 Amendment 8 (`disabled`
applies row by row, as a test form on SurveyCTO showed), except forms
whose own expressions fail in the engine.

2026-10-18, form errors: spec 02 Dispute 6 adjudicated (Python green).
Spec 07 Amendment 10, spec 10 Amendment 3 and spec 11 Amendment 6 are
implemented: an expression the engine cannot evaluate leaves the question
blank, is recorded and reported first (`FORM ERRORS` section, last line
on the command line, red sections and a final red message in Stata,
`r(formerrors)`); a submission whose walk cannot get past a point is cut
short. Spec 07 Dispute 7 (answer counts around a form error) and spec 11
Dispute 5 (`r()` read after `log close`, which clears it) were both
accepted. GIL bench: the last 4 forms (Centers_takeup x3, Mauritania
epoux) now simulate and import; Centers_takeup shows its two form errors
(rows 42 and 300). Then: package republish and push (owner).

2026-10-18 evening, realism step 3 (data-quality checks), phase 1: a
coherent fieldwork plan, the default from now on (owner's choice), so
that `ipacheck` and custom checks find only problems put there on
purpose. Spec 13 (each enumerator's interviews follow one another within
working hours, in one village a day; distinct survey ids) and spec 04
Amendment 4 (`id`) are implemented and green. Checked with ipacheck
4.3.1 on the IPA household form (scratchpad probe): 0 overlap and 0
impossible trip, against 4-16 per 100 interviews before. That check led
to spec 13 Amendment 1 and spec 04 Amendment 5 (`interviewsPerDay`, 4 by
default, instead of a quota derived from `fieldworkDays`; ids from a
`search()` list), specs and stub committed. **Next** (Codex): tests for
13 A1 and 04 A5, a dispute for the spec 13 tests of the old quota, spec
10 Amendment 4 again (Codex's partial tests, which do not compile, are in
`git stash` "codex partial: spec 10 Amendment 4 tests"; drop the stash
once Codex has rewritten them) and spec 11 Amendment 7; then the code,
and the ipacheck check again. Phase 2 (injected problems with an answer
key) follows.

2026-10-23: the Codex tests of 13 A1, 04 A5, 10 A4 and 11 A7 are in and
implemented (quota `interviewsPerDay`, `--id`, Stata `id()`); disputes 13/1,
10/6 and 11/6 accepted. An end-to-end check on the IPA household form
(district, village, household chosen in cascade from a preloaded file)
led to spec 13 Amendment 2 (an id from a `search()` list filtered by an
answer is given at the question, among the offered choices not yet
received) and spec 12 Amendment 1 (the id's level of a fictitious file
gets a code for every submission), both implemented: ipacheckids went
from 97 to 21 duplicated observations (11 ids reused, in villages drawn
more often than others); 0 overlap, 0 impossible trip. Open: disputes
13/2 and 12/2 (tests superseded by these rules), and the CLI's part of
the sizing (spec 10, "Clarification of Amendment 4"): Codex's tests
stopped on its usage limit (partial work in `git stash`), code to follow.
Java 8700 tests, only the 9 disputed cases fail.

2026-10-23 afternoon, phase 1 done: spec 13 Amendment 3 (the upper
questions of a survey-id cascade, district and village, are steered to
choices that still lead to a household not yet interviewed) and the
CLI's sizing of fictitious files are implemented; disputes 13/2 and 12/2
accepted. End-to-end check with the Stata command on the IPA household
form (100 submissions, `fakepreload id(hhid)`): 100 distinct ids,
`ipacheckids` 0 duplicate, 0 overlap, 0 impossible trip, 0 start outside
working hours, 4 interviews per enumerator a day over 5 days. Suites:
Java 8805 and Stata 82, all green (spec 13 Dispute 3 accepted).
**Next**: phase 2, problems injected on purpose with an answer key
(faulty enumerators, duplicate ids, outliers within constraints,
abnormal durations, impossible travel; named after ipacheck's checks),
to be designed with the owner before any spec.

2026-10-23 evening, phase 2a (problems injected on purpose, with an
answer key), designed with the owner: opt-in (`problems` in the
configuration, `--problems`, Stata `problems`); injected while walking,
never by editing the CSV; the key is a separate `answer-key.csv`. A
duplicate is two submissions with the same survey id, whatever their
times; an overlap is two interviews of one enumerator with different ids
(owner's rule). Spec 14, spec 04 Amendment 6, spec 10 Amendment 5 and
spec 11 Amendment 8 are implemented. Java 9574 tests, only the 2 tests of
dispute 14/1 fail (their form has no instanceID, so no KEY); Stata 98 tests, only the
display test of dispute 11/7 fails (its directory name contains
"warnings"). Codex hit its usage limit; both disputes wait for a run.
End-to-end check on the IPA household form (200 submissions, `--id hhid
--fake-attachments --problems`), deterministic checks in Stata: duplicate
ids 8 flagged, all explained by the key; off hours 4/4, overlaps 2/2,
impossible trips 4/4, no false alarm; the 8 abnormal durations are all IQR
outliers, among natural ones (the form's durations are bimodal). Two
overlaps were not injected: the IPA form's interviews often last under
five minutes. 2026-10-24: disputes 14/1 (the cascade fixture gets an
instanceID) and 11/7 (the warning-count cursor no longer matches the
key's path) accepted; Java 9574 and Stata 98 tests, all green. **Next**:
republish the package, then phase 2b (faulty
enumerators: too fast, "No" to filter questions of modules of 3 or more
questions, don't-know codes declared in the configuration, rounded
numbers; outliers within constraints, detected automatically). Later
problem types agreed with the owner: a form left open (long and
overlapping) and a wrong survey id (typo); located villages from a
preloaded file for travel.

2026-10-24/25, phase 2b (problems in the answers), designed with the
owner: spec 14 Amendments 1 and 2, spec 04 Amendments 7 and 8, spec 07
Amendment 11, spec 11 Amendments 9 and 10. Faults are carried by
enumerators (`tooFast`, `skipModules`, `dontKnow`, `fakeRefusal`), one per
fault while at least half the enumerators stay clean; outliers are
placed where the engine's constraint accepts them; consent is simulated
per interview only for the questions the configuration names (owner: the
simulator must not guess them), 90 % by default; rounding of numbers was
dropped (owner: round amounts are normal). Disputes 04/1, 10/7,
11/8 and 14/2-4 accepted (superseded tests; a roster fixture whose positional
count JavaRosa never recomputes; 11 ids for 12 submissions). Java 10767
tests and Stata 109 tests, all green. End-to-end check on the IPA household form (20
enumerators, 400 submissions, consent questions named): the too-fast
enumerator's median duration 31 min against about 78, the "don't know"
enumerator 10 % codes against 2 %, outliers 8 of 8 placed (one an age of
196, which the form's constraint allows for the first member), the fake
refusals 70 % consent against 75-100 % (weak with 20 interviews each, as
the owner's mild dosage implies), rosters 1.25 persons against 1.4-2.6.
Without consent weights, the IPA form's three consent questions give 11 %
consent, hence its bimodal durations seen in phase 2a. **Next**: owner's
choice; agreed later problem types: a form left open (long and
overlapping), a wrong survey id, located villages for travel.

2026-10-25 afternoon, a form left open (owner: interruption only; never
skipped: the form stays open overnight when the pause comes late, and is
finished the next morning when no interview follows), with `duration()`
as screen time (spec 07 Amendment 12, spec 14 Amendment 3, spec 04
Amendment 9, spec 11 Amendment 11). The wrong-survey-id problem was
dropped (owner). Java 11138 tests; the 8 CLI tests of dispute 10/8 and
the 2 Stata tests of dispute 11/9 (configurations without `leftOpen`)
wait for Codex. IPA check: the 8 forms left open are exactly those whose
`endtime - starttime` exceeds `duration` by more than ten minutes, and
every overlap of different ids is explained by the key. **Next**: the
two disputes, republish; then located villages (owner to say how the
teams' preload files hold coordinates).

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
- Run Stata and the scripts from PowerShell: Git Bash rewrites Stata's
  `/e /q` switches into paths, and Windows PowerShell 5.1 turns native
  stderr redirected with `2>&1` (or `2>$null`) into a terminating error
  under `$ErrorActionPreference = 'Stop'`, so do not redirect it.
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
