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

## Amendment 2: search values questions and assumptions

This run adds only `AnswerGeneratorSearchNumberTest`,
`AnswerGeneratorSearchTemporalTest`, `AnswerGeneratorSearchChoiceTextTest`,
`AnswerGeneratorSearchContractTest`, and `AnswerGeneratorSearchSupport`.
Existing tests and helpers remain unchanged. Inputs are small inline questions,
configuration strings, and hints constructed with the existing spec 09 test
builder; no resource files or form-engine sessions are needed. Expectations
come from spec 05 Amendment 2 and its explicit references to specs 04 and 09.
No production source or implementation bodies were read.

### 22. Are empty levels retained, and what happens with integer-free ranges?

**Question:** Does an empty Level 1 remain in the outer list? Does an INTEGER
range containing no integer search an empty list or the `Math.round(min)`
fallback used by `generate`? What happens when every alternative is
unsatisfiable?

**Assumption:** Empty-level representation is left open. Empty selects must
have no values, with or without an empty level. TEXT/BARCODE without hints
must still provide their complete broad level, optionally preceded by empty
levels. An integer-free configured range and all-unsatisfiable alternatives
are not tested: the search table's wording and the drawing fallbacks do not
unambiguously settle their search results. A single contradictory hint interval
uses the configured range, as explicitly stated in spec 09. Mixed alternatives
skip contradictory intervals and keep satisfiable ones.

**Tests:** `emptySelectHasNoSearchValues`, `noHintsStillProvideTheBroadTextSearch`,
the numeric/date hint matrices, and numeric/date alternative-order tests.

### 23. Where do fractional decimal endpoints appear?

**Question:** Does "its integers and its two ends" put fractional endpoints
after all integers, or place the lower end first in numerical order? How is
that sequence sampled when it is larger than 10,001 values?

**Assumption:** Do not impose the endpoints' placement relative to interior
integers. Small fractional ranges must contain exactly the formatted ends
and interior integers, with integers in ascending order and no duplicates.
With no interior integers the two ends are in lower-then-upper order.
Oversized decimal tests use integral endpoints so there is no endpoint-order
ambiguity. Fractional-endpoint sampling above the cap remains untested.

**Tests:** `fractionalDecimalEndsAndEveryInteriorIntegerArePresentExactlyOnce`,
`decimalEndsAreHalfUpFormattedAndDeduplicated`,
`exclusiveDecimalHintsMoveEndsByOneCent`,
`integralDecimalRangesRespectTheCapAndEvenSpacing`.

### 24. Which rounding applies to evenly spread integer and date samples?

**Question:** What tie-breaking rule applies when an evenly spread point is
halfway between two integers or two days?

**Assumption:** Rounding to the kind's precision means a nearest integer or
day; either result is allowed at an exact half tie. Each output must lie
within half a unit of its ideal evenly spaced position, remain strictly
increasing, include both ends, and meet the cap. Exact two-unit spacing is
also tested. Decimal output uses the already-confirmed half-up formatting.
Caps apply separately to each drawing range before levels combine ranges
and remove repeated values. Narrow integer ranges above Integer.MAX_VALUE
are tested; representability extremes outside assumption 5 remain untested.

**Tests:** `oversizedIntegerRangesAreSpreadAcrossBothEnds`,
`dateCapEnumeratesSmallRangesAndSpreadsLargeRangesIncludingBothEnds`,
`valueCapIsPerDrawingRangeRatherThanPerLevel`,
`largeDateAlternativesEachReceiveTheirOwn10001ValueBudget`.

### 25. How far does deduplication extend, and what is the combination order?

**Question:** Are repeated answers removed within each level or across levels?
For equal-sized select combinations, what does "in the order of the choices"
mean?

**Assumption:** Deduplicate within each level, preserving first occurrence.
An answer in Level 1 can appear again in Level 2, whose complete list is
specified independently. Equal-sized combinations are ordered lexicographically
by their ascending offered-choice indices, and each answer uses display order.
The 20,000 cap keeps the first combinations in that order. Special choices
participate in combinations like any offered choice; drawing weights and
specialRate do not change exhaustive search. Select fixtures keep assumption
4's unique, nonempty, whitespace-free choice values. Duplicate candidates,
overlapping ranges, decimal endpoints, and broad-level section overlaps provide
the specified duplicate coverage without assuming duplicate-choice semantics.

**Tests:** Exact broad-level tests, alternative-order tests,
`fourChoiceSearchIncludesEveryNonemptySubsetInSizeThenDisplayOrder`,
`combinationLimitKeepsTheFirst20000AndNeverSelectsMoreThanFour`, and candidate/
prefix deduplication tests. The 26- and 27-choice fixtures straddle the cap
with 17,901 and 20,853 possible combinations respectively.

### 26. Do non-range hints or appearances further restrict search lists?

**Question:** Must the table's TEXT/BARCODE candidate and prefix values obey
length/pattern hints? Do `numbers_decimal`, `month-year`, or `label` change
the listed levels? Are select include/exclude hints applied to offered choices?

**Assumption:** The explicit text search recipe is literal: preserve candidates
verbatim, then append the prefix plus 1 through 12 zeros, even when a pattern
or length hint would reject those answers. Broad levels likewise enumerate the
table's complete values independently of drawing hints. For appearances and
select include/exclude hints, the interaction is insufficiently specified;
these combinations are not tested. TEXT/BARCODE fixtures use top-level candidates
and prefix, not prefixes inside alternatives, whose aggregation is unspecified.

**Tests:** `textSearchDoesNotTruncatePrefixesToLengthHintsOrReplaceThemWithPatternDraws`,
all complete broad-level tests, candidate and prefix tests. All search questions
have empty appearance strings and select questions have no choice hints.

### 27. What additional API guarantees apply to searchValues?

**Question:** Must the outer list be unmodifiable, must null arguments throw
NullPointerException, or must equal calls return distinct collection objects?

**Assumption:** Only each individual level is explicitly required to be
unmodifiable. Outer-list mutability, identity/caching, and null-argument errors
are not asserted because Amendment 2 does not state them. Determinism is tested
by equality across instances and repeated calls, including intervening drawing
and search calls. Locale checks use the existing `java.util.Locale.default`
resource lock and restore all locale categories. Fixed values, both missing
rates, optional/required status, and text overrides cannot change the search
recipe. No particular random draw sequence is asserted.

**Tests:** `AnswerGeneratorSearchContractTest`; no additional error tests.

### 28. Amendment 2 compilation verification (2026-10-12)

**Question:** Can the new tests be compiled by the requested offline command
in this sandbox?

**Assumption / result:** Compilation is not verified. Before adding files,
`mvnw.cmd -o -q test-compile` returned success with the existing build state.
After adding the tests, the same command exits 1 with a fatal compiler error
for `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The `-e` diagnostic
identifies `java.nio.file.AccessDeniedException` in Windows real-path resolution
while closing the JAR filesystem. A separate attempt with the quoted argument
`-Dmaven.compiler.fork=true` also exits 1 and reports unresolved existing project
types such as Question, QuestionKind, SimulationConfig, and AnswerGenerator.
This is not a successful compile or an expected runtime assertion failure.

Logs are under `.codex-runs/answer-search/`. No Maven repositories were copied,
drives mapped, dependencies installed, or production/build configuration changed.
Runtime tests were not run; compilation needs rechecking where the existing
dependency cache and project outputs are accessible.

**Tests:** All four new Amendment 2 test classes and their new helper.
