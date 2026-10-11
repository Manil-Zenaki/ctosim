# ctosim — synthetic data for SurveyCTO forms

Generates simulated submissions for a SurveyCTO form that respect the
form's logic (skips, constraints, required fields, calculations, repeats)
and writes them in SurveyCTO's export layout (wide and long CSV), so that
cleaning and analysis code can be written before fieldwork.

```
ctosim.cmd form.xlsx -n 300 -s 42 -c config.json
```

Start with `docs/DEVELOPER_GUIDE.md` (installing, running, configuration,
how it works, glossary). Design: `docs/ARCHITECTURE.md`. How code and
tests are written: `docs/WORKFLOW.md`. Current state: `docs/STATUS.md`.

## Build and test

Requires a JDK 17 or newer (the one bundled with Stata works) and, for
XLSForm conversion, Python 3.10+ with the converter installed
(`pip install -e "converter[test]"`).

```
mvnw.cmd package
```

After cloning, enable the repository's git hooks once:

```
git config core.hooksPath .githooks
```

## Licence

MIT (see `LICENSE`). The published forms in `samples/forms/` keep their own
licences (CC BY 4.0 or MIT), listed with their sources in `samples/SOURCES.md`.

## Reporting a problem

E-mail mzenaki@alum.mit.edu with a subject starting with `[ctosim]`.
