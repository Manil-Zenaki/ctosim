# Spec 13 fieldwork-plan test questions and assumptions

The tests use spec 13 and the contracts it explicitly incorporates from specs
01, 04, 05 and 07. No production source was read or changed. Expectations are
black-box properties; no RNG implementation, exact seed output or warning
wording is prescribed by these tests.

## 1. Optional IDs and missing answers

**Question:** Does a visit's ID bypass the missing-answer rule, or does "as if
its override were value" preserve the blank rule in spec 05?

**Assumption:** Do not resolve this conflict in tests. ID-answer tests use
missingRate 0, or a required question when the field's missingRate is 1.

**Tests:** `FieldworkVisitTest.visitIdReplacesAConfiguredValueAndAnExistingDefault`
uses a required question. Other visit and plan ID tests use missingRate 0.
No test asserts what happens to an optional ID with missingRate 1.

## 2. Duplicate entries in texts

**Question:** If an override's texts list repeats a value, is K the number of
entries or the number of distinct values? Should the pool be deduplicated?

**Assumption:** Unspecified; test pools contain distinct entries. No behavior
for duplicate or empty-string entries is asserted.

**Tests:** All pool/cycling tests in `FieldworkIdTest` avoid duplicates and
empty strings.

## 3. Overrides that do not define an ID pool

**Question:** Does "without such an override" mean no field override at all,
or no override defining value, texts, or an integer min/max range?

**Assumption:** The latter, following the explicit precedence list. A select's
weights alone do not remove values from the choice list, even at weight zero.
Special-value exclusion still applies to that list.

**Tests:** `FieldworkIdTest.choiceWeightsDoNotRemoveAllowedChoicesFromTheIdPool`.

## 4. Whole numbers and empty numeric ranges

**Question:** How are noninteger bounds converted to an ID pool, and what
happens when they enclose no whole number?

**Assumption:** "Whole numbers of the range" includes exactly the integers
between ceil(min) and floor(max), inclusive (including negative integers).
An empty set follows the no-possible-values rule, without division by zero.
No assumption is made about the ordinary generated answer in that case.

**Tests:** `FieldworkIdTest.integerOverrideRangeIncludesExactlyTheWholeNumbersBetweenItsBounds`,
`integerWithoutAnOverrideUsesTheConfiguredGlobalRange`, and the
`emptyIntegerRange` case of `noPossibleValuesLeavesVisitIdsNullAndProducesOneFieldWarning`.

## 5. Texts on integer/select questions and explicit special values

**Question:** Do the ID-pool precedence rules apply even when the ordinary
generator would ignore texts for that question kind? Are explicit fixed
special values removed?

**Assumption:** Spec 13's explicit pool precedence applies. Texts precede an
integer range and a select choice list; special-value exclusion applies to
the choice-list fallback, not an explicitly configured value. Tests use
texts that the engine accepts as integers or offered choices.

**Tests:** `FieldworkIdTest.textsTakePrecedenceOverAnIntegerOverridesRange`,
`textsTakePrecedenceOverASelectChoiceList`, and `aFixedSpecialChoiceRemainsAnExplicitlyAllowedId`.

## 6. Groups, metadata, relevance and changing choice lists

**Question:** What should runtime warnings/IDs be when id names an existing
nonquestion element, an irrelevant/read-only question, multiple distinct
questions with the same name, or a question with dynamically filtered choices?

**Assumption:** Leave those allocation behaviors open. The absent-element
configuration warning must not fire for an existing group. Repeat instances
of one question receive the same visit ID, as explicitly required by "each
question of that name". Static unfiltered lists are used for allocation tests.

**Tests:** `FieldworkIdTest.anExistingNonQuestionElementIsNotReportedAsAnAbsentElement`
checks only configWarnings, without asserting runtime behavior.
`FieldworkVisitTest.visitIdAppliesToEveryQuestionInstanceOfTheNameInRepeats`
checks repeat instances, and
`visitIdTargetsEveryQuestionWithTheNameAcrossSeparateGroups` checks direct
visit injection across groups. No dynamic-choice or duplicate-name pool
allocation oracle is imposed.

## 7. Rejected IDs and fallback

**Question:** Does "not accept" include conversion failure, an unavailable
select choice, and a constraint failure? What is the precise fallback RNG
position and how many warnings are emitted?

**Assumption:** All three rejection mechanisms trigger normal answering and
a warning naming field and attempted ID. Exact retry counts, RNG consumption,
warning count and wording are unspecified. Fallback fixtures use an accepted
ordinary fixed value so the test does not prescribe a new random sequence.

**Tests:** `FieldworkVisitTest.rejectedVisitIdsFallBackToAnOrdinaryAnswerAndWarnWithFieldAndValue`.

## 8. Validation location and nonfinite coordinates

**Question:** Must invalid Visit values be rejected in the record constructor
or specifically by walk? Is NaN an invalid coordinate?

**Assumption:** Constructor and walk validation are both accepted by placing
construction inside assertThrows. Infinity is outside the explicit numeric
intervals; NaN handling is not specified and is not tested. Null Fieldwork
constructor arguments are also unspecified and not tested.

**Tests:** Validation tests in `FieldworkVisitTest`.

## 9. Very long interviews and workdays crossing midnight

**Question:** If an interview extends beyond the following day's opening,
does the scheduler skip days, delay that day's opening, or follow "next day"
even though that would overlap? What is the definition of day for an evening
start whose nine-hour work window crosses midnight?

**Assumption:** No policy is inferred. Timing fixtures use morning starts
and durations no greater than nine hours, so finishing after closing is
tested without the cross-day overlap conflict. Day indices are measured
relative to config.start, not the wall-clock date. A nonzero seconds
component is preserved by whole-minute scheduling.

**Tests:** `FieldworkScheduleTest`, especially
`interviewsMayFinishAfterWorkButTheFollowingInterviewStartsNextDay`.
Multi-day-duration and midnight-window cases await clarification.

## 10. What the random draws depend on

**Question:** Does "from its number alone" mean minute draws are independent
of form-answer draws, the duration of an interview, and how many values are
consumed before that submission? Does the ID permutation depend on N?

**Assumption:** Given a fixed seed, scheduling minutes are derived from the
submission number independently of form answering. Village/interview locations
are independent of form contents when enumerator/day assignments agree.
The ID order depends on the seed and pool, so extending N preserves its
prefix. No particular PRNG, shuffle or seed-mixing formula is assumed.

**Tests:** `FieldworkScheduleTest.minuteDrawsAreIndependentOfHowManyQuestionsTheWalkerAnswers`,
`aCandidateAtExactlyClosingTimeStaysTodayButOneSecondLaterMovesTomorrow`,
`FieldworkLocationTest.answerRandomnessAndInterviewDurationDoNotChangePlannedLocationsWhenTheDayIsUnchanged`,
and `FieldworkIdTest.longerPlansKeepThePrefixOfTheSameSeededIdPermutation`.

## 11. Village observability and geographic tolerances

**Question:** Village centers are not exposed. How can the uniform village
draw and the exact 1.5-km household-to-village bound be checked through the API?

**Assumption:** Do not invent an API or inspect private state. Test the stated
observable consequences: visits stay in the rectangle, all pairs in an
enumerator/day cluster are at most 3 km apart, and independent villages spread
across the rectangle. Distribution checks sample one visit per village and
allow the full boundary-band mass attributable to a 1.5-km displacement, plus
seven binomial standard deviations. These do not completely identify a
hidden village or prove its individual 1.5-km bound. Household displacement
itself is not required to be uniform, so no radial-distribution test is added.

**Tests:** Clustering, area-boundary and distribution tests in
`FieldworkLocationTest`. The independent haversine oracle uses radius
6,371,000 m. Serialized GPS checks allow 0.16 m for spec 05's six-decimal
coordinate rounding; unrounded Visit clustering allows only 0.000001 m of
floating-point tolerance. Degenerate and polar areas are valid under spec 04.

## 12. Empty state, immutability and saved references

**Question:** Are lists from earlier walkAll calls snapshots, or may they be
unmodifiable views that update on later calls?

**Assumption:** Only the specified unmodifiable and last-call properties are
required. Tests copy observations before replay and do not assert the identity
or lifetime semantics of earlier list objects.

**Tests:** `FieldworkContractTest.allReturnedListsRejectModificationBeforeAndAfterWalking`
and `repeatedCallsReproduceResultsAndReplaceRatherThanAccumulateLastRunState`.

## 13. Warning order and meaning of days taken

**Question:** Does days taken mean calendar span through the latest workday,
or a sum of enumerator-days? Are warning strings standardized?

**Assumption:** Days taken is max workday index + 1 across all enumerators,
not a sum. There is one overflow warning, before the single ID-pool warning.
Configuration warnings retain the walker's list as a prefix, followed by the
unknown-ID warning. Tests check required field names and whole-number tokens,
not exact prose. Submission warnings stay outside plan warnings.

**Tests:** Overflow tests in `FieldworkScheduleTest`,
`FieldworkContractTest.overflowWarningComesBeforeTheIdPoolWarning`,
`planWarningsExcludeWarningsBelongingToIndividualSubmissions`, and
`FieldworkIdTest.absentFieldWarnsAtConfigurationTimeAfterTheWalkersWarnings`.

## Build verification

The requested `.\mvnw.cmd -o -q test-compile` was attempted. Maven reached
testCompile but its in-process compiler failed with
`java.nio.file.AccessDeniedException` for
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while closing the JDK
ZIP filesystem (`WindowsPath.toRealPath`). The diagnostic log is under
`.codex-runs/fieldwork-plan/test-compile.log`. No Maven repository was copied,
no drive mapped, and no production/build configuration file changed.

The diagnostic retry with `-Dmaven.compiler.fork=true` also failed, reporting
unresolved existing production symbols in older tests and the same cached-JAR
`AccessDeniedException`. This is not a successful Maven build.

For supplementary compilation evidence, the existing diagnostic compiler
driver was adapted under `.codex-runs/fieldwork-plan/`. It loads the existing
compiled binary APIs and cached dependencies into the compiler's file manager,
without opening production sources or copying the Maven repository. All eight
relevant sources (the seven new Fieldwork Java files plus the existing
FormWalkerTestSupport) compile successfully with `--release 17` and JUnit 5.
Log: `.codex-runs/fieldwork-plan/memory-compile.log`.

The diagnostic JUnit launch discovered and ran **135 cases** from the six new
test classes: **10 passed, 125 failed**, with no skipped or aborted cases,
container failures or form-loading errors. Failures are expected missing
implementation behavior: 48 reject the not-yet-supported `id` configuration
key, 54 throw `UnsupportedOperationException`, and 23 validation/boundary
assertions encounter that stub exception instead of their expected result.
The passing cases are the invalid-ID configuration cases; they alone do not
establish implemented ID support. Downstream assertions remain unexercised
until the module is implemented. Log: `.codex-runs/fieldwork-plan/junit.log`.

The test sources have **71 test methods**, expanding to the 135 parameterized
cases above. No commit was made. The requested Maven command remains blocked
by the environment; the supplementary compiler/JUnit results do not claim a
successful Maven build.

## Amendment 1 additions

The new tests are in `FieldworkAmendment1QuotaTest` and
`FieldworkAmendment1SearchIdTest`, with `FieldworkAmendment1SearchSupport`
and four CSV fixtures under `src/test/resources/ctosim/fieldwork-plan/amendment1/`.
Existing test files and fixtures are unchanged. No production source was read
or changed; no public API stub inspection was necessary.

### 14. Previously committed quota expectations

**Question:** Should existing tests for the old `ceil(N / (E * D))` quota be
updated now that Amendment 1 explicitly replaces it?

**Assumption:** No, following this run's instruction to keep committed tests
and add new files/classes only. The amendment is the oracle for the new tests;
some old schedule assertions will conflict with a correct new implementation.

**Tests:** All of `FieldworkAmendment1QuotaTest`. This is a known conflict in
the retained tests, not a reason to preserve the old quota in production.

### 15. Numeric ranges on search select questions

**Question:** Amendment 1 excludes an override with a range from its search
fallback, but the original survey-ID rules define a range pool only for an
integer question. What pool should a range-only override on a `select_one`
question produce, especially when its bind is numeric?

**Assumption:** Leave this unspecified. Tests cover absent overrides,
weights/missingRate-only overrides, fixed values and texts. They do not assert
an ID pool for a range-only select override, nor infer integer-question
semantics from a numeric select bind. Conditional rules and expressions that
change the pool are also not given an additional allocation oracle here.

**Tests:** `aFixedLoadedValueTakesPrecedenceOverSearchAndStaticChoices`,
`explicitTextsTakePrecedenceOverTheSearchPoolAndRetainExplicitSpecialCodes`,
and `weightsAndMissingRateDoNotSuppressPossibleSearchIds` cover the
unambiguous precedence cases; no test depends on a range-only interpretation.

### 16. Form-opening choices versus the choices at answer time

**Question:** What should be checked when an earlier answer changes a search
argument, so an allocated opening-time ID is no longer offered at the question?

**Assumption:** Amendment 1 explicitly fixes the allocation pool at opening,
before answers. Empty instance values and literal defaults are therefore used
for that pool, even when an earlier question has a configured fixed answer.
The unchanged visit-ID rejection/fallback contract may change the stored
answer; these tests assert the visits' IDs, not equality with that later
answer. Repeat-specific contexts and opening actions/calculations are not
assigned new semantics. Repeating walkAll must use a fresh opening state.

**Tests:** `idPoolUsesBlankOrDefaultValuesBeforeAnEarlierQuestionIsAnswered`
(blank and nonblank defaults) and
`sourceFileExpressionIsAlsoEvaluatedBeforeEarlierAnswers`.

### 17. Deduplication and special values across sources

**Question:** Does an ID present in both the attached file and the static
choices count once, and do configured special values apply to both sources?

**Assumption:** Yes: the existing clarification says K counts distinct
nonempty values, and Amendment 1 excludes configured special values from the
combined offered list. Explicit value/texts overrides retain their established
precedence, including explicitly allowed special codes. Placeholder labels
are never IDs, and numeric-looking strings such as `0007` retain their text.

**Tests:** `loadedAndStaticDuplicatesCountAsOnePossibleId`,
`attachedValuesAndStaticChoicesReplaceThePlaceholderAndCycleWithoutDuplicates`,
`configuredSpecialValuesAreRemovedFromBothLoadedAndStaticChoices`,
`clearingSpecialValuesRestoresCodesFromTheFileAndStaticList`, and
`explicitTextsTakePrecedenceOverTheSearchPoolAndRetainExplicitSpecialCodes`.

### 18. Unreadable attachments and warning ownership

**Question:** Which diagnostics belong to the plan when loading a search list
fails, and must tests force operating-system permission errors?

**Assumption:** Use portable missing-file and missing-value-column cases from
spec 07 Amendment 3. Both give an empty loaded list and retain static choices.
A header-only file and a no-match search are also empty lists, but are not
read errors. Only the specified exhaustion/no-pool warnings are asserted on
the plan; search diagnostics belong to the submission under the incorporated
walker contract. No exact diagnostic wording or OS permission setup is required.

**Tests:** `anUnreadableSearchFallsBackToStaticChoicesOnBothSidesOfThePlaceholder`,
`noUsableLoadedOrStaticChoicesLeavesNullVisitIdsAndOnePlanWarning`,
`readableHeaderOnlyFileLeavesStaticChoicesAsTheIdPool`,
`noMatchingRowsWithoutStaticChoicesGivesAnEmptyPool`, and
`fixedStaticValueStillWorksWhenTheAttachmentIsMissing`.

### 19. Observable scheduling and search-list order

**Question:** How can exact scheduling boundaries and shuffled pools be checked
without prescribing the random implementation?

**Assumption:** One-question forms with a 60-second minimum have exactly
60-second interviews under spec 07. Quotas up to six always fit within the
nine-hour window even with maximum opening/travel draws. A five-hour interview
always permits exactly two starts per day, including with quota 1000. The
closing-boundary test obtains the travel draw through the public plan, then
changes only the form duration, as the confirmed number-only minute rule
permits. ID tests compare pool membership, distinctness, periodicity, replay
and prefix stability; they impose no exact shuffle or file-order permutation.

**Tests:** `FieldworkAmendment1QuotaTest`, `searchPoolExhaustionWarnsExactlyWhenDistinctKIsLessThanN`,
`differentSeedsCanChangeTheSearchIdPermutation`, and
`extendingNPreservesTheSeededSearchIdPrefix`. Search filtering and itemset
choices use the explicitly incorporated spec 07 Amendment 3 contract,
including bypassing choice filters.

### Amendment 1 build verification

The requested `.\mvnw.cmd -o -q test-compile` was run again after the final
test edits and exited 1. Maven reaches `testCompile` but the compiler fails
with `java.nio.file.AccessDeniedException` for the cached
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. An `-e` diagnostic run
confirmed the ZIP-filesystem / `WindowsPath.toRealPath` failure. Logs are
`.codex-runs/fieldwork-amendment1/test-compile.log` and
`test-compile-detail.log`. No dependencies were installed or copied, drives
mapped, or production/build settings changed.

As a supplementary source check, the existing diagnostic compiler driver
was adapted under `.codex-runs/fieldwork-amendment1/`. It compiled all three
new Java files and their three existing test-support dependencies against
the existing compiled public APIs and cached JUnit 5 dependencies, with
`--release 17`. Result: `compiled=true`; log: `memory-compile.log`. This
checks the test sources without opening production sources, and does not
claim the requested Maven build succeeded.

The supplementary JUnit run discovered and executed **70 test cases** from
the two new test classes: **9 passed, 61 failed**, with no skipped/aborted
tests or failed containers. Of the failures, 23 reject the unimplemented
`interviewsPerDay` key; 38 are assertion failures consistent with the old
quota or placeholder-based search ID pool. There were no form-loading
exceptions. Assertions after an earlier failure remain unexercised until
the amendment is implemented. Log: `.codex-runs/fieldwork-amendment1/junit.log`.

Only this questions file, the three new Java files and the four new CSV
fixtures are task changes. Previously committed tests are preserved, and no
commit was made.

### Dispute adjudication build verification (2026-10-23)

The requested `.\mvnw.cmd -o -q test` exited 1 at `testCompile` with a
fatal compiler error naming
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The Maven test run
is blocked; its log is `.codex-runs/fieldwork-dispute/maven-test.log`.
No dependencies were installed or copied and no build configuration was
changed.

The existing diagnostic compiler and JUnit launcher were adapted under
`.codex-runs/fieldwork-dispute/`. All seven selected test/support source
files compiled successfully with `--release 17` against existing compiled
APIs. All 85 cases in `FieldworkScheduleTest`, `FieldworkAmendment1QuotaTest`
and `FieldworkAmendment1SearchIdTest` passed, with no skipped/aborted cases
or failed containers. Logs: `memory-compile.log` and `junit.log` in that
directory. These supplementary results do not claim the Maven build passed.

The decisions correcting assumptions affected by the disputed tests are
recorded at the end of `13-fieldwork-plan.disputes.md`. No new behavioral
assumption was needed for either disputed point.
