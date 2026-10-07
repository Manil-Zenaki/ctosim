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
        │ SurveyCtoExport│  wide CSV in SurveyCTO export layout
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

| # | Module | Responsibility |
|---|--------|----------------|
| 01 | `FormLoader` | Read an XForm file, return a parsed form; make SurveyCTO-specific XPath functions evaluable (stubs). Clear errors for unreadable or invalid forms. |
| 02 | `SimulationConfig` | Read and validate an optional JSON config: seed, missing/refusal rates, repeat-count ranges, per-field overrides (fixed value, range, choice weights). |
| 03 | `AnswerGenerator` | Given one question (type, choices, bounds) and a random source, propose a candidate answer. Pure and deterministic for a given seed. |
| 04 | `FormWalker` | Walk one submission through the engine: answer relevant questions, retry on constraint violations, decide repeat counts, return the filled instance. |
| 05 | `SurveyCtoExporter` | Flatten submissions into SurveyCTO's wide CSV layout (repeat suffixes, split select_multiple columns, metadata columns). |
| 06 | `Simulator` + CLI | Orchestrate n submissions with a seed and write outputs plus a run report. |

## Input formats

- **XForm XML** (the form definition SurveyCTO serves for a deployed form) is
  the canonical input.
- An **XLSForm** (`.xlsx`) can be converted to XForm with `pyxform`; this
  conversion is a later, optional front end.

## Determinism

The same form, config and seed must always produce byte-identical outputs.
All randomness flows from one seeded `java.util.Random` (or a splittable
derivative), never from wall-clock time or hash ordering.

## Tooling

- Java 17 bytecode, built with the Maven wrapper (`mvnw` / `mvnw.cmd`).
- JDK: any JDK 17+. The one bundled with Stata works
  (`StataNow19/utilities/java/windows-x64/zulu-jdk21.0.11`).
- Tests: JUnit 5.
