# Disputes — spec 11 (Stata command)

## Dispute 1: default output directories that differ only by case

`test_options_paths.do` runs the command on `café XML.XML`, then on
`café xMl.xMl`, both without `saving()` or `replace`, and expects each run
to succeed in its own default directory (`ctosim-out/café XML`, then
`ctosim-out/café xMl`). On Windows, file names are not case-sensitive:
both are the same directory, which already holds the first run's
`run-report.txt` when the second run starts, so the spec requires error
602 ("the output directory already contains a run-report.txt and replace
is not given"). The test fails on Windows for that reason only; the
questions file (point 6) already set filesystem case sensitivity aside.

Requested change: give each iteration names that differ by more than
case (for example `café XML 1.XML`, `café xMl 2.xMl`), or pass `replace`.

## Dispute 2: Stata's own Java is no longer always the one used (Amendment 1)

`test_runtime_selection.do` asserts that the simulator always runs with
the Java that Stata uses (`c(java_home)`), the original rule of "Finding
the programs". Amendment 1 replaced that rule: the first Java 17 or newer
among `CTOSIM_JAVA`, Stata's Java, `JAVA_HOME` and the system path. With
Stata 16 (Java 11) the command must not run Stata's Java. The test author
noted this (Amendment 1 questions, A2) and kept the test unchanged.

Requested change: remove the test, or expect Stata's Java only when it is
Java 17 or newer (the Amendment 1 tests already cover the full order).

## Dispute 3: compiled jars committed as test fixtures

The project's rule is to never commit build output. The tests commit two
compiled jars, `src/test/stata/fixtures/stata-test-support.jar` and
`src/test/stata/fixtures/amendment-test-support.jar`, built from sources
that are committed beside them (`StataTestSupport.java`,
`AmendmentTestSupport.java`, `build-test-fixtures.ps1`,
`build-amendment-fixtures.ps1`). The simulator double
(`SimulatorDouble.java`) is already compiled at test time with
`JAVA_HOME`'s compiler.

Requested change: build the two support jars at test time as well (for
example in `launch.ps1` / `amendment-launch.ps1`, or from `launch.do`
before the first `javacall`, into a directory under `.codex-runs/` that is
then put on the ado-path), and remove the committed `.jar` files.
