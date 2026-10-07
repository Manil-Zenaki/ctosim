# ctosim — synthetic data for SurveyCTO forms

Generates simulated submissions for a SurveyCTO form that respect the
form's logic (skips, constraints, required fields, calculations, repeats)
and writes them in SurveyCTO's export layout.

Status: in development. See `docs/ARCHITECTURE.md` for the design and
`docs/WORKFLOW.md` for how the code and tests are written.

## Build and test

Requires a JDK 17 or newer (the one bundled with Stata works).

```
mvnw.cmd test
```

After cloning, enable the repository's git hooks once:

```
git config core.hooksPath .githooks
```
