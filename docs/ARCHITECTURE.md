# Architecture

## Goal

Generate synthetic submissions for a SurveyCTO form that **respect the form's
own logic**: relevance (skips), constraints, required fields, calculations,
repeat groups and choice filters. The output looks like a SurveyCTO data
export, so the cleaning and analysis code can be developed and tested
before fieldwork starts.

## Key design decision: let the form engine enforce the logic

We do not reimplement XPath, relevance or constraint evaluation. Each
submission is produced by **driving the official ODK form engine
(JavaRosa)** through the form, exactly as a data-collection app would:

```
           form.xml (XForm)
                │
        ┌───────▼────────┐
        │  FormLoader    │  parse XForm, register SurveyCTO function stubs
        └───────┬────────┘
                │ FormDef
        ┌───────▼────────┐      ┌──────────────────┐
        │  FormWalker    │◄────►│ AnswerGenerator  │ propose a value per question
        │ (one record)   │      └────────▲─────────┘
        └───────┬────────┘               │
                │ filled instance  ┌─────┴────────────┐
                │                  │ SimulationConfig │ per-field overrides,
                │                  └──────────────────┘ repeat counts, rates
        ┌───────▼────────┐
        │ SurveyCtoExport│  wide and long CSVs, SurveyCTO layout
        └───────┬────────┘
                │
        ┌───────▼────────┐
        │  Simulator/CLI │  n records, seed, output folder
        └────────────────┘
```

The engine only shows a question when it is relevant, rejects answers that
violate a constraint, refuses to move past an empty required question and
recomputes `calculate` fields after every answer. The walker proposes a
value, and on rejection proposes another one, up to a retry limit.

## Modules

Each module has a specification in `specs/` that is the contract for its
tests. Modules are built in this order:

| # | Module | Language | Responsibility |
|---|--------|----------|----------------|
| 01 | `FormLoader` | Java | Read an XForm file, return a parsed form; make SurveyCTO-specific XPath functions evaluable (stubs); serve metadata preloads. Clear errors for unreadable or invalid forms. |
| 02 | XLSForm converter | Python | Turn a SurveyCTO XLSForm (`.xlsx`) into an XForm the engine accepts, plus a field catalog recording SurveyCTO-only field types. |
| 03 | `SurveyCtoFunctions` | Java | Real implementations of the SurveyCTO functions that change form logic (`index()`, `choice-label()`, `rank-value()`, list functions, ...), replacing their stubs. |
| 04 | `SimulationConfig` | Java | Read and validate an optional JSON config: seed, missing/refusal rates, repeat-count ranges, per-field overrides (fixed value, range, choice weights). |
| 05 | `AnswerGenerator` | Java | Given one question (type, choices, bounds) and a random source, propose a candidate answer. Pure and deterministic for a given seed. |
| 06 | Simulated sessions | Java | Sessions whose `today()`, `now()`, `duration()`, timestamps, `random()`, `uuid()` and `instanceID` come from a simulated clock and a seed, so runs are reproducible. |
| 07 | `FormWalker` | Java | Walk one submission through a simulated session: answer relevant questions, retry on constraint violations, size enumerator-controlled repeats, advance the clock, serialise the instance. |
| 08 | `SurveyCtoExporter` | Java | Write submissions in SurveyCTO's export layouts (wide CSV and long CSVs), as checked against real exports of the coverage form in `samples/coverage/`. |
| 09 | Constraint hints | Java | Read bounds, lengths and regex patterns from a question's constraint so the generator draws valid answers directly. |
| 10 | CLI | Java | Convert, configure, simulate N submissions and write outputs plus a run report. |

## Input formats

Researchers hold their forms as **SurveyCTO XLSForms** (`.xlsx`). These are
not plain ODK XLSForms, so `pyxform` alone rejects most of them. Observed on
the published forms in `samples/forms/` (see `samples/SOURCES.md`):

- SurveyCTO column names: `relevance`, `read only`, `constraint message`,
  `required message` (ODK: `relevant`, `read_only`, ...).
- SurveyCTO-only field types: `caseid`, `comments`, `text audit`,
  `audio audit`, `speed violations ...`, `calculate_here`, ...
- `selected(${x}, 1)` with an unquoted number, which SurveyCTO accepts and
  JavaRosa rejects.
- Duplicate choice values within a list, and forms without a `settings`
  sheet.
- SurveyCTO-only XPath functions that drive skip logic (`index()`,
  `choice-label()`, `rank-value()`): stubbing them is not enough, they need
  real implementations (module 03).

Module 02 normalises these and calls `pyxform`; the resulting **XForm XML**
is the canonical input of the Java engine.

## Determinism

The same form, config and seed must always produce byte-identical outputs.
All randomness flows from one seeded `java.util.Random` (or a splittable
derivative), never from wall-clock time or hash ordering.

## Tooling

- Java 17 bytecode, built with the Maven wrapper (`mvnw` / `mvnw.cmd`).
- JDK: any JDK 17+. The one bundled with Stata works
  (`StataNow19/utilities/java/windows-x64/zulu-jdk21.0.11`).
- Tests: JUnit 5.
