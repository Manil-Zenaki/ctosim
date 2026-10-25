# Spec 04: disputed tests

## Dispute 1: the three-fault default of Amendment 7, superseded by Amendment 8 (2026-10-24)

**Tests:** in `ProblemRatesAmendment7Test`:
`defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances`,
`defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`; in
`SimulationConfigAmendment7IoTest`:
`loadAcceptsOutlierBoundsAndNumericRepresentations` (10 cases),
`loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents`;
in `SimulationConfigAmendment7ParsingTest`:
`emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults`,
`explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults` (10 cases),
`originalRateOverridesStillDefaultBothNewComponents`.

**Spec:** Amendment 8: "`faults` (Amendment 7) accepts `fakeRefusal`
too, and its default is the four faults, in the order `tooFast`,
`skipModules`, `dontKnow`, `fakeRefusal`; so is
`ProblemRates.defaults().faults()`." Clarification (Amendment 8),
assumption 50: the earlier tests of the three-fault default are
superseded and go through a dispute.

**What the tests do:** they expect the default fault list of Amendment 7,
`[tooFast, skipModules, dontKnow]`; all 25 failures read
`expected: <[tooFast, skipModules, dontKnow]> but was: <[tooFast,
skipModules, dontKnow, fakeRefusal]>`.

**Requested change:** expect the four faults of Amendment 8 wherever the
default list is checked, keeping every other assertion (outlier rates,
absence of `problems`, copies, immutability).

## Adjudication (2026-10-24)

**Dispute 1 upheld: correct the superseded default expectation.** Amendment 8
explicitly requires `[tooFast, skipModules, dontKnow, fakeRefusal]`, in that
order, both for `ProblemRates.defaults()` and for a present `problems` object
with omitted `faults`. Its clarification 50 explicitly supersedes the earlier
three-fault default tests.

- `ProblemRatesAmendment7Test.defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`
  and `defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances`: expect the
  four-fault default. Keep the original-rate, outlier and immutability assertions.
- `SimulationConfigAmendment7IoTest.loadAcceptsOutlierBoundsAndNumericRepresentations`
  (all 10 cases) and
  `loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents`:
  expect four faults when `faults` is omitted from a present `problems` object.
  Keep numeric expectations, absent `problems`, and explicitly disabled settings.
- `SimulationConfigAmendment7ParsingTest.emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults`,
  `explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults` (all 10 cases),
  and `originalRateOverridesStillDefaultBothNewComponents`: expect four default
  faults; changing an outlier or original rate does not change that default.
  Keep every rate assertion.

The shared `SimulationConfigAmendment7Support.DEFAULT_FAULTS` now contains the
four names. Explicit three-fault inputs use `LEGACY_FAULTS`, preserving the
existing ordered-subset, duplicate, constructor and null-element cases. Those
inputs are still valid: clarification 50 changes only the default. No other
assertions, copy tests, production code or specification requirements changed.

Validation: `.\mvnw.cmd -o -q test` failed during `testCompile`; an `-e`
retry confirmed `AccessDeniedException` for the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The existing
`.codex-runs/Amendment7CompileProbe.java` successfully compiled all 11 selected
Amendment 7 test/support and dependency sources, including the three disputed
test classes, against the existing production binaries and cached JUnit API
with Java release 17 (`compiled=true`). Logs are
`.codex-runs/04-dispute-test.log`, `.codex-runs/04-dispute-test-detail.log`, and
`.codex-runs/04-dispute-probe-compile.log`. Compilation is verified by the
probe; runtime test results remain unverified because Maven could not reach
test execution. No implementation source was read or modified.
