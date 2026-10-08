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
