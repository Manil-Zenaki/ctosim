# AnswerGenerator test questions and assumptions

The expectations in these tests come from spec 05. The explicitly referenced
spec 04 supplies the public configuration API, accepted JSON, and defaults.
No production source or implementation bodies were read. Focused per-case
configuration inputs and questions are constructed in the tests. The shared
mixed-override configuration is stored under
`src/test/resources/ctosim/answer-generator/mixed-overrides.json`. No external
form fixtures are needed.

## 1. Which random API calls or seeded sequences are contractual?

**Question:** Must an implementation use a particular random algorithm, consume
a particular number of draws, or return a particular answer for a given seed?

**Assumption:** No. Compare complete sequences from equivalent inputs and equal
random states, without hard-coded seeded answers. Freshness means a sequence
can vary, not that every pair of consecutive answers must differ. Different
random seeds are checked over sequences, not single answers. Fixed answers,
singletons, and ACKNOWLEDGE need not vary. Tests do not require fixed answers or
blank branches to leave the random state untouched. They do not prescribe what
changing the configuration's seed does when the supplied random state is fixed.

**Tests:** `AnswerGeneratorContractTest` determinism, freshness, different-seed,
hidden-state, and `LongOnlyRandom` tests; all statistical tests.

## 2. What statistical tolerances are appropriate?

**Question:** How can uniformity and rates be checked without locking tests to
one correct sampling algorithm?

**Assumption:** Fixed seeds, typically 30,000 draws (60,000 or 90,000 for selected
distributions), and a tolerance of seven binomial standard deviations plus two
counts are appropriate for interior probabilities. Probabilities zero and one
are checked exactly. Full-support comparisons detect unexpected answers. Statistical
checks can detect material bias but are not mathematical proofs of uniformity.
The default text suffix test uses 150,000 draws, making omission of either
endpoint exceptionally unlikely for a correct uniform implementation. Media
suffixes are checked for bounds and bucket uniformity; their million-value
support makes demanding both endpoints in ordinary samples unreliable.

**Tests:** `AnswerGeneratorTestSupport.assertRate`, all distribution tests, and
`defaultTextSuffixIsUniformFromOneThrough9999IncludingEndpoints`.

## 3. What happens when remaining multiple-select weights are all zero?

**Question:** Does the fallback explicitly require uniform draws among the
remaining zero-weight choices, as select-one explicitly does?

**Assumption:** The spec requires filling the requested size with distinct
ordinary choices and forbids a zero-weight draw while a positive-weight choice
remains. It does not explicitly state the fallback distribution for
select-multiple, so no fallback uniformity expectation is imposed. Uniformity
without weights and proportional draws while positive weights remain are tested.

**Tests:** `multipleSelectDrawsZeroWeightsOnlyAfterPositiveWeightsAreExhausted`,
`allZeroOfferedWeightsStillYieldEveryAllowedMultipleSelectSize`,
`multipleSelectWithEnoughPositiveChoicesNeverDrawsZeroWeight`, and
`weightedMultipleSelectUsesWeightsWithoutReplacement`.

## 4. Are duplicate or whitespace-containing choice values valid?

**Question:** The Question constructor forbids nulls but says nothing about
duplicate values, empty values, or values containing spaces. How should these
affect sampling and the space-separated answer format?

**Assumption:** Leave those cases untested. Select fixtures contain unique,
nonempty, whitespace-free values, including Unicode. Non-select questions use
empty choices as described by the spec. There is no assumed validation error
for duplicate or blank names, choices, or unusual appearance text.

**Tests:** All selection tests; `questionCopiesChoicesInTheirOriginalOrder`.

## 5. What is the integer domain for NumericRange's double bounds?

**Question:** Are integer answers limited to Java int or long, and what should
happen for finite doubles beyond the exact-integer or long range?

**Assumption:** The spec does not impose a 32-bit limit. A narrow range just above
Integer.MAX_VALUE is therefore tested; its integers are exactly representable
by double and long. Bounds near Long.MIN_VALUE/Long.MAX_VALUE, values beyond
2^53, enormous spans, and non-finite values are not tested because overflow and
representability policies are unstated. Ordinary inward rounding and the
explicit Math.round(min) fallback are tested, including negative half ties.

**Tests:** `integerSupportsBoundsBeyondThe32BitIntegerRange`,
`integerRangeRoundsBoundsInwardAndIncludesBothEndpointsUniformly`, and
`singletonAndIntegerFreeRangesUseSpecifiedIntegerOrMathRoundMinimum`.

## 6. What does decimal half-up mean for a double's binary representation?

**Question:** Should a singleton such as JSON 2.345 round according to its
ordinary decimal value or its exact binary expansion?

**Assumption:** Half-up means the ordinary decimal value: 2.345 becomes 2.35 and
-2.345 becomes -2.35. Exact binary half ties (1.125 and -1.125) are also tested.
No expectation is made for the exact unrounded floating-point interpolation
used during a random draw. Rounding happens after sampling, so a rounded result
may be outside the original bounds (for example [.001,.004] yields `0`).

**Tests:** `equalDecimalBoundsUseHalfUpPlainFormatting`,
`decimalRoundsAfterDrawingSoRoundedAnswerCanLieOutsideRawBounds`, and
`decimalIsUniformBeforeRoundingIncludingHalfWidthEndpointBins`.

## 7. How are appearance words recognized?

**Question:** Are tabs/newlines word separators? Are words case-insensitive?

**Assumption:** Test the stated, space-separated, lowercase words only. Leading,
trailing, and repeated spaces still separate words. Substrings such as
`my_numbers` and `numbers_phone_extra` are not the specified words. Case folding
and other whitespace delimiters remain untested. Numeric appearance affects
TEXT only.

**Tests:** `numericAppearanceWordsProduceEightAsciiDigits`,
`onlyCompleteAppearanceWordsActivateNumericText`, and
`numericTextAppearanceOnlyAppliesToTextQuestions`.

## 8. Are repeated text entries sampled as entries or distinct values?

**Question:** Spec 04 allows duplicate strings in texts; does uniformity refer
to list entries or unique strings?

**Assumption:** No duplicate-text weighting expectation is imposed. Distinct
Unicode, whitespace, newline, and empty strings are returned verbatim. An empty
string is not the null blank sentinel. The fixed-value rule likewise returns
an empty string when explicitly configured, subject to the blank rule first.

**Tests:** `textOverrideIsUniformAndPreservesUnicodeWhitespaceAndEmptyStrings`,
`textOverrideTakesPrecedenceOverNumericAppearance`, and
`fixedEmptyStringIsAnAnswerAndNotNull`.

## 9. Does “each drawn uniformly” mean separate conditional draws?

**Question:** Are date/time components, coordinate axes, text digits, and
multiple-select choices intended to use fresh draws rather than correlated
reuse of one sample?

**Assumption:** Yes. Numeric text says each digit is drawn uniformly; location
coordinates and shifts are separately drawn; DATETIME combines DATE and TIME
rules; weighted sampling explicitly uses the remaining weights after each
draw. Tests include joint coordinate/date-time bins, adjacent digit pairs, and
analytically computed probabilities for unordered weighted subsets. No exact
PRNG-call ordering is assumed.

**Tests:** `numericTextDigitsAreUniformAtEveryPosition`,
`datetimeCombinesUniformDatesAndTimesWithoutTimeZone`,
`initialLatitudeAndLongitudeAreUniformInMinusOneThroughOne`, and
`weightedMultipleSelectUsesWeightsWithoutReplacement`.

## 10. How are printed geographic coordinates rounded?

**Question:** The spec requires six decimal places but does not name a rounding
mode, state whether intermediate points use rounded coordinates, or specify
whether negative zero is normalized.

**Assumption:** Do not assert a rounding mode or signed-zero normalization.
Allow up to 0.000001 (plus floating-point slack) in a printed coordinate
difference to accommodate rounding of two endpoints. Distribution tests omit
tiny neighborhoods around bin boundaries. Both accumulating unrounded points
and accumulating previously formatted points are accepted. Only the first
point is bounded by [-1,1]; later points can move beyond it. Shape closure is
an exact textual copy of the first point.

**Tests:** All `AnswerGeneratorLocationTest` tests, especially
`eachNewPointIsShiftedFromItsPredecessorByAtMostOneThousandth` and
`eachStepAndEachAxisUsesUniformShifts`.

## 11. Are barcode digits required to be uniform?

**Question:** Unlike numeric text, BARCODE specifies ten decimal digits but
does not explicitly specify their distribution.

**Assumption:** Require ten ASCII decimal digits and check freshness/determinism.
Do not impose digit uniformity, require a leading-zero observation, or enforce
a particular barcode standard/checksum. Leading zeros are accepted if returned.

**Tests:** `barcodeHasExactlyTenAsciiDigits` and the
parameterized freshness/determinism tests.

## 12. How should out-of-domain fixed values and empty selects interact?

**Question:** Must a fixed select answer be an offered choice, or a fixed number
parse as numeric? Does a required empty select override a fixed value?

**Assumption:** The explicit fixed-value rule applies verbatim to every kind
without engine validation. The blank rule precedes it, and an empty select is
always null even if required or fixed. This follows the stated precedence,
despite ordinary required questions never being blanked by missingRate.

**Tests:** `fixedValueIsReturnedVerbatimForEveryKindAndRetry`,
`blankRulePrecedesFixedValueForEveryKind`, and
`emptySelectIsBlankEvenWhenRequiredAndFixed`.

## 13. What behavior is required for irrelevant override parts?

**Question:** Does “ignored” require an identical random consumption pattern to
an otherwise equal configuration without those parts?

**Assumption:** Only the resulting semantic rules and distributions are
asserted. Irrelevant NumericRange, DateRange, texts, and weights must not
replace the applicable kind's rules. The tests do not require identical
seeded sequences between different configurations. Compatible applicable and
irrelevant override parts are tested together using combinations accepted by
spec 04.

**Tests:** `AnswerGeneratorOverrideTest` and the irrelevant/combined override
tests in the number, text, and date/time classes.

## 14. What extreme dates and filename names are supported?

**Question:** Does YYYY imply handling negative or more-than-four-digit years?
What happens if today minus 364 underflows LocalDate? Are names containing path
separators sanitized for media filenames?

**Assumption:** Those policies are unspecified and not tested. Tests cover
ordinary four-digit years, leap days, year boundaries, dates outside the
default window, and Unicode names with underscores. File names are returned
as strings; tests do not create media files.

**Tests:** `AnswerGeneratorDateTimeTest` and
`mediaFileNamesPreserveQuestionNameAndUseUniformSuffixAndCorrectExtension`.

## 15. How is the no-clock/no-default-locale requirement checked?

**Question:** Must tests manipulate the system clock or inspect internals to
prove no hidden time dependency?

**Assumption:** No production introspection or system-clock changes. Use
supplied dates decades away from the clock and compare equivalent random
states, including after unrelated prior calls. Temporarily change the JVM
default locale to French and Arabic, restoring all default locale categories
in finally. A JUnit resource lock serializes this test with cooperating locale
tests. No separate time-zone default assumption is needed for the required
zone-free date/time text.

**Tests:** `defaultDateRangeIsExactly365DaysEndingOnProvidedToday`,
`outputsDoNotDependOnDefaultLocale`, and
`unrelatedPriorCallsDoNotCreateHiddenGeneratorState`.

## Amendment 1: additional questions and assumptions

Only new test classes were added for this amendment; the committed tests and
their helpers were kept unchanged. Small questions and configuration inputs
are constructed inline, so no additional resource fixtures are necessary.
No production sources or implementation bodies were read.

### 16. What if multiple numeric TEXT appearance words occur together?

**Question:** Which rule wins when `numbers_decimal` occurs alongside `numbers`
or `numbers_phone` on the same TEXT question?

**Assumption:** The amendment does not specify precedence between those words.
No tests require a particular result for that combination. Combinations whose
words apply to different kinds are tested: `label` must not blank TEXT or DATE,
and `numbers_decimal` must not displace DATE's `month-year` behavior.

**Tests:** `AnswerGeneratorDecimalAppearanceTest.labelWordDoesNotSuppressDecimalText`,
`AnswerGeneratorMonthYearAppearanceTest.appearancesForOtherKindsDoNotDisplaceMonthNormalization`;
all other appearance fixtures avoid conflicting numeric TEXT words.

### 17. Do the new words use the existing appearance token rules?

**Question:** Are leading, trailing, and repeated spaces accepted, and are
substrings such as `list-nolabel` sufficient to trigger `label`?

**Assumption:** The confirmed assumption 7 applies to all three new words:
space-separated complete words match, including with repeated spaces;
substrings do not. Case folding, tabs, newlines, and nonbreaking spaces are
still unspecified and untested.

**Tests:** Complete-word and substring tests in `AnswerGeneratorDecimalAppearanceTest`,
`AnswerGeneratorMonthYearAppearanceTest`, and `AnswerGeneratorLabelAppearanceTest`.

### 18. Is month-year normalization applied before or after enforcing the date range?

**Question:** Can the stored first-of-month date precede the configured minimum,
and should months have equal probability?

**Assumption:** The wording says to draw a DATE and then take its month's first
day. Thus [2025-03-17, 2025-03-17] stores 2025-03-01, and each month's probability
is its count of eligible days divided by the inclusive range length. The default
window remains exactly 365 days ending on the supplied today. This is a direct
reading of the amendment, not an added requirement to sample months uniformly
or clamp the resulting date. The oracle enumerates eligible dates independently;
it does not compare results against production DATE generation.

**Tests:** All normalization and distribution tests in
`AnswerGeneratorMonthYearAppearanceTest`, including singleton dates, partial
months, leap days, and year boundaries.

### 19. Must appearances share identical random draw sequences with base kinds?

**Question:** Does "as for DECIMAL/DATE" mandate identical random API calls or
exactly matching seeded outputs between different kinds or appearances?

**Assumption:** No additional random call sequence is specified. Test the stated
distributions and formats directly, and compare seeded sequences only for equal
configuration and arguments. Existing assumptions 1, 2, 6, and 15 still apply.
Decimal endpoint bins have probability 1/8 versus 1/4 for interior bins in
[-0.02, 0.02]. Month probabilities are proportional to eligible days. Statistical
checks use fixed seeds and the existing seven-standard-deviation-plus-two-count
tolerance; retry variation is checked across sequences rather than requiring
every consecutive answer to differ.

**Tests:** `AnswerGeneratorAppearanceContractTest`, decimal distribution tests,
and month distribution tests. Default-locale changes use the existing resource
lock name and restore all locale categories in finally.

### 20. Does the label exception override fixed values of every form?

**Question:** Does a label select remain blank when required, fixed to an offered
choice, fixed to an unoffered value, or fixed to an empty string?

**Assumption:** Yes: "always blank" explicitly overrides both required and fixed
values for SELECT_ONE and SELECT_MULTIPLE only. This does not impose random-state
consumption requirements. For the first two new appearances, the existing blank
and fixed-value precedence remains unchanged; TEXT's texts override also remains
verbatim, including nonnumeric and empty strings.

**Tests:** `AnswerGeneratorLabelAppearanceTest`, precedence tests in
`AnswerGeneratorAppearanceContractTest`, and texts-override tests in
`AnswerGeneratorDecimalAppearanceTest`.

### 21. Build verification in this sandbox (2026-10-11)

**Question:** Can the required offline compile be completed with the available
filesystem access?

**Assumption / result:** No successful compile can be claimed in this run.
`mvnw.cmd -o -q test-compile` exits 1 with a fatal compiler error naming
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Repeating with `-e`
identifies `java.nio.file.AccessDeniedException` in Windows real-path resolution
while closing the JAR filesystem. A diagnostic run with
`-Dmaven.compiler.fork=true` also fails with the same access error and unresolved
project types (including Question, QuestionKind, SimulationConfig, and
AnswerGenerator) in both existing and new tests. A direct javac check of only
the four new classes and existing AnswerGeneratorTestSupport likewise cannot
resolve the project types from target/classes, and reports AccessDeniedException
while closing the cached JUnit params JAR. Its output directory is under
`.codex-runs/answer-appearances-compile`.

No repositories were copied, drives mapped, dependencies installed, production
code read or changed, or build configuration changed to work around these errors.
Compilation must be rechecked in an environment with working access to the
existing build outputs and Maven cache; runtime tests were not run.

**Tests:** All four new Amendment 1 test classes. Their public API usage is
derived solely from specs 04 and 05 and the unchanged existing test helper.
