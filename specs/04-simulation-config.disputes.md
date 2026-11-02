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

## Dispute 2: the four-fault default, superseded by Amendment 12 (2026-11-01)

**Tests:** in `ProblemRatesAmendment7Test`:
`defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances`,
`defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`; in
`ProblemRatesAmendment8Test`:
`defaultsAppendFakeRefusalAfterTheThreeExistingFaults`,
`omittedFaultsInAnyPresentProblemsObjectUseAllFourDefaults` (4 cases); in
`ProblemRatesAmendment9Test`:
`defaultsEnableLeftOpenWithoutChangingAnyEarlierDefault`; in
`SimulationConfigAmendment7IoTest`:
`loadAcceptsOutlierBoundsAndNumericRepresentations` (10 cases),
`loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents`;
in `SimulationConfigAmendment7ParsingTest`:
`emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults`,
`explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults` (10 cases),
`originalRateOverridesStillDefaultBothNewComponents`; in
`SimulationConfigAmendment8IoTest`:
`loadDistinguishesAbsentConsentFromRequiredFieldsWithDefaults`; in
`SimulationConfigAmendment9ParsingTest`:
`parseAndLoadAcceptNumericFormsAndInclusiveEndpoints` (20 cases).

**Spec:** Amendment 12: `faults` accepts `refuse`, and its default is the
five faults `tooFast`, `skipModules`, `dontKnow`, `fakeRefusal`,
`refuse`; so is `ProblemRates.defaults().faults()`. "The earlier tests of
the four-fault default are superseded and go through a dispute."

**What the tests do:** they expect the default fault list of Amendment 8,
`[tooFast, skipModules, dontKnow, fakeRefusal]`; every failure reads
`expected: <[tooFast, skipModules, dontKnow, fakeRefusal]> but was:
<[tooFast, skipModules, dontKnow, fakeRefusal, refuse]>` (directly, or
inside a `ProblemRates[...]` comparison).

**Requested change:** expect the five faults of Amendment 12 wherever the
default list is checked, keeping every other assertion (rates, absent
`problems`, consent, copies, immutability, numeric forms). A test whose
name says "four" may keep its name or be renamed.

## Adjudication (2026-11-01)

**Dispute 2 upheld: correct every disputed four-fault default expectation.**
Amendment 12 explicitly requires `[tooFast, skipModules, dontKnow,
fakeRefusal, refuse]`, in that order, for `ProblemRates.defaults()` and
for a present `problems` object whose `faults` member is omitted. Its
clarification 66 changes only the default list; explicit lists retain
their supplied contents and order. Dispute 1's historical ruling remains
correct under Amendment 8, but its four-fault expectation is now superseded.

- `ProblemRatesAmendment7Test.defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`
  and `defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances`: use the
  five-fault default. Keep all rate and immutability assertions; the new
  fault does not change those requirements.
- `ProblemRatesAmendment8Test.defaultsAppendFakeRefusalAfterTheThreeExistingFaults`
  and `omittedFaultsInAnyPresentProblemsObjectUseAllFourDefaults` (four cases):
  use the five-fault default because neither default construction nor
  overriding an unrelated rate supplies an explicit fault list. Rename
  them to `defaultsIncludeAllFiveFaultsInTheSpecifiedOrder` and
  `omittedFaultsInAnyPresentProblemsObjectUseAllFiveDefaults`; keep the
  original rate assertions and inputs.
- `ProblemRatesAmendment9Test.defaultsEnableLeftOpenWithoutChangingAnyEarlierDefault`:
  change only the expected record's fault list. Amendment 12 leaves all
  nine default rates, including `leftOpen`, unchanged.
- `SimulationConfigAmendment7IoTest.loadAcceptsOutlierBoundsAndNumericRepresentations`
  (ten cases) and
  `loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents`:
  use five faults when a present `problems` object omits `faults`. Keep
  numeric expectations, absent `problems`, and explicitly empty lists.
- `SimulationConfigAmendment7ParsingTest.emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults`,
  `explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults` (ten cases),
  and `originalRateOverridesStillDefaultBothNewComponents`: use five default
  faults. Empty objects and unrelated rate overrides use the current
  default; keep every rate assertion.
- `SimulationConfigAmendment8IoTest.loadDistinguishesAbsentConsentFromRequiredFieldsWithDefaults`:
  change the omitted-faults expectation to five faults. Keep all consent
  and absent-problems assertions because Amendment 12 does not alter them.
- `SimulationConfigAmendment9ParsingTest.parseAndLoadAcceptNumericFormsAndInclusiveEndpoints`
  (twenty cases): change only the expected record's default fault list.
  Keep the numeric forms, inclusive endpoints, other rates, and both input
  paths; setting `leftOpen` does not supply an explicit fault list.

These corrections are implemented in the three Amendment 7, 8 and 9
support classes' `DEFAULT_FAULTS` constants. The Amendment 8 permutation
provider now uses a separate `AMENDMENT_8_FAULTS` constant containing its
original four names, so explicit-list cases remain identical. Amendment 7's
`LEGACY_FAULTS`, constructor inputs, fixtures, copy tests and all other
assertions are unchanged. No implementation source was read or modified.

Validation: `.\mvnw.cmd -o -q test` failed at `testCompile`; an `-e` retry
confirmed `AccessDeniedException` for
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Logs are
`.codex-runs/04-dispute2-test.log` and
`.codex-runs/04-dispute2-test-detail.log`. Using the existing compiler-probe
approach, 21 test/support sources compiled successfully with Java release
17 against `target/classes` and cached JUnit APIs
(`.codex-runs/04-dispute2-probe-compile.log`). The direct JUnit fallback
then ran all eleven selected Amendment 7-9 ProblemRates and SimulationConfig
test classes, including every disputed test and the copy tests: **1,201 tests
passed, zero failed; all 81 containers passed**
(`.codex-runs/04-dispute2-junit.log`). Maven itself remains blocked by the
cached-JAR access failure. No dependencies, build configuration or production
files were changed, and no commit was made.

## Dispute 3: the five-fault default, superseded by Amendment 13 (2026-11-01)

**Tests:** in `ProblemRatesAmendment7Test`:
`defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances`,
`defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`; in
`ProblemRatesAmendment8Test`: `defaultsIncludeAllFiveFaultsInTheSpecifiedOrder`,
`omittedFaultsInAnyPresentProblemsObjectUseAllFiveDefaults` (4 cases); in
`ProblemRatesAmendment9Test`:
`defaultsEnableLeftOpenWithoutChangingAnyEarlierDefault`; in
`ProblemRatesAmendment12Test`:
`omittedFaultsDefaultToAllFiveEvenWithoutRefusalCodes` (8 cases),
`problemRateDefaultsHaveExactlyTheFiveFaultsInTheSpecifiedOrder`,
`theFiveDefaultFaultsAreUnmodifiable`; in `SimulationConfigAmendment7IoTest`:
`loadAcceptsOutlierBoundsAndNumericRepresentations` (10 cases),
`loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents`;
in `SimulationConfigAmendment7ParsingTest`:
`emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults`,
`explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults` (10 cases),
`originalRateOverridesStillDefaultBothNewComponents`; in
`SimulationConfigAmendment8IoTest`:
`loadDistinguishesAbsentConsentFromRequiredFieldsWithDefaults`; in
`SimulationConfigAmendment9ParsingTest`:
`parseAndLoadAcceptNumericFormsAndInclusiveEndpoints` (20 cases).

**Spec:** Amendment 13: "`faults` accepts `other`, and its default is the
six faults, in the order `tooFast`, `skipModules`, `dontKnow`,
`fakeRefusal`, `refuse`, `other`; so is `ProblemRates.defaults().faults()`.
The earlier tests of the five-fault default are superseded and go through
a dispute." Clarification (Amendment 13), assumption 72.

**What the tests do:** they expect the default fault list of Amendment 12;
the failures read `expected: <[tooFast, skipModules, dontKnow, fakeRefusal,
refuse]> but was: <[tooFast, skipModules, dontKnow, fakeRefusal, refuse,
other]>`, or the same inside a whole `ProblemRates` record.

**Requested change:** expect the six faults of Amendment 13 wherever the
default list is checked, keeping every other assertion (rates, absence of
`problems`, copies, immutability, explicit lists).
