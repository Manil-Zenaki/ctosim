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
