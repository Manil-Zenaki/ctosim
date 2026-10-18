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
