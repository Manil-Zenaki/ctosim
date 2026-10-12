# Spec 07 — questions, assumptions, and validation

Expectations are derived from spec 07 and the public contracts it references
in specs 01, 04, 05, and 06. No production source was opened or searched.

1. **Question:** Does an override for the primary instance root itself count
   as a known name? What about secondary instances, duplicate leaf names,
   case differences, or Unicode names?
   **Assumption:** “Any element of the form's primary instance” includes its
   root and repeat templates, regardless of whether an element has a body
   control. Names match exactly and case-sensitively, consistent with spec
   04. Secondary-instance elements do not count. A duplicate known leaf name
   does not produce an unknown-name warning. Sorting uses Java natural
   string order, including uppercase and non-ASCII names.
   **Tests:** `allPrimaryInstanceElementKindsAreRecognizedAsOverrideNames`,
   `unknownOverridesAreReportedOnceInNaturalNameOrder`,
   `unicodeElementNameIsRecognizedAndReceivesItsOverride`,
   `siblingRepeatsEachReceiveTheirOwnOverride`.

2. **Question:** Is `walk(number)` limited by `config.submissions()`?
   **Assumption:** No: spec 07 rejects only numbers below one. The configured
   batch size does not impose an additional upper bound on this method.
   **Tests:** `submissionNumbersBelowOneAreRejected`,
   `anyPositiveNumberIsReportedEvenBeyondConfiguredBatchSize` (including
   `Integer.MAX_VALUE`).

3. **Question:** Which RNG, seed derivation, draw ordering, and correlations
   are required? Must different numbers produce different XML even for a
   completely empty form without metadata?
   **Assumption:** No particular RNG or seed-mixing formula is specified.
   Tests compare repeated runs, independent walkers, separate loads, and
   differently ordered calls, without hard-coded random outputs. Different
   identifiers are asserted only on forms containing an instanceID; an
   empty form cannot necessarily produce distinguishable XML. No particular
   independence between start-day and start-minute draws is imposed.
   **Tests:** Reproducibility tests in `FormWalkerContractTest`; the
   distribution tests use fixed seeds and seven binomial standard deviations
   plus two observations as their tolerance. Endpoint checks use samples
   large enough to make accidental omission extremely unlikely.

4. **Question:** Do defaults include zero and values populated immediately
   before the question is reached? Does any override disable preservation,
   even if it contains only a missing rate or text list?
   **Assumption:** Zero is an existing value. Calculate and value-changed
   action results are inspected when reached. Any named override disables
   rule 2. If the generator then returns null or conversion fails, “no
   value” means clearing the old default, not retaining it. Read-only takes
   precedence over an override. Whitespace-only and empty-string default
   normalization are left to the engine and are not specified by these tests.
   **Tests:** Default/action/read-only tests in `FormWalkerQuestionTest`,
   `blankGenerationClearsAnOverriddenExistingValue`,
   `failedOverrideDoesNotKeepTheOriginalDefault`,
   `existingValueTakesPrecedenceOverUnsupportedControl`.

5. **Question:** What exact warning prose and cardinality are required for a
   required question that also fails conversion or exhausts its constraints?
   **Assumption:** Warnings must identify the field and the specified reason;
   conversion warnings also contain the attempted value. Tests check these
   fragments and the exact `submission N:` prefix, not punctuation or a
   complete English sentence. They do not require separate versus combined
   messages for the two reasons on a required field. A single optional
   unsupported/conversion/constraint failure gives one warning, and a
   repeated field gives one such warning per failed instance. Intermediate
   rejected proposals do not produce final failure warnings after success.
   **Tests:** Warning tests in `FormWalkerContractTest`,
   `FormWalkerQuestionTest`, `FormWalkerKindTest`, and
   `eachFailedRepeatedQuestionInstanceGetsItsOwnWarning`.

6. **Question:** Does a required field left blank by an empty choice filter,
   or an unsupported required control, also require a missing-required
   warning? Do configuration warnings get copied into submission warnings?
   **Assumption:** These cases are not explicit. Required warnings are tested
   for the two stated causes (conversion failure and exhausted constraints).
   Empty-choice behavior is tested on optional fields. No tests constrain
   copying configuration warnings into submission warnings.
   **Tests:** `requiredUnsatisfiedConstraintAlsoProducesARequiredWarning`,
   `requiredConversionFailureAlsoProducesARequiredWarning`,
   `rejectedRequiredSelectAlsoWarnsThatItIsRequired`,
   `selectsWithNoCurrentlyOfferedChoicesAreLeftBlankAndClockStillMoves`.

7. **Question:** How can a black-box test observe exactly 50 generator calls,
   or prove that conversion failure made exactly one call, with no injectable
   generator or attempt counter in the public API?
   **Assumption:** It cannot directly count those calls. The retry test uses
   integer proposals in 1..20 and a constraint accepting only 1. Failure
   probability after 50 fresh proposals is `(19/20)^50`; 2,500 submissions
   distinguish major budget errors, frozen draws, and unlimited retries.
   This is deliberately not represented as proof of the exact 49/50/51
   boundary. Fixed invalid answers verify one final warning, a blank result,
   continuation, and a single handled-question clock advance, but cannot
   expose invisible repeated conversions of the same fixed value. An
   explicit generator test seam would enable exact call-count assertions.
   **Tests:** `retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget`,
   `impossibleConstraintLeavesBlankWarnsAndContinuesToNextQuestion`,
   `unconvertibleAnswerIsBlankAndWarningNamesItsValue`.

8. **Question:** How should the walker handle a NumericRange for a repeat
   with no integral values, negative bounds, values outside integer limits,
   or pre-existing instances already exceeding the target?
   **Assumption:** Spec 04 permits numeric bounds that do not make a valid
   repeat count, but spec 07 gives no fallback/error/deletion policy. Those
   cases are not asserted. Tests cover nonnegative valid targets, zero,
   singleton counts, inward fractional rounding, the global maximum of
   1,000, and a large positive target that reaches the navigation guard.
   DateRange/text/fixed-value overrides use global repeat bounds because
   spec 07 explicitly selects only NumericRange overrides.
   **Tests:** `FormWalkerRepeatTest` and repeat distribution tests.

9. **Question:** How is an exact navigation-step boundary counted, and what
   path syntax is required at a repeat-add event?
   **Assumption:** The number of engine events per question/repeat and path
   multiplicity formatting are not exposed by FormWalker. The guard test
   forces substantially more than 100,000 steps and checks the exception
   type, submission number, and current repeat-path prefix. It does not
   impose an undocumented event-count formula or claim to distinguish
   exactly 100,000 from 100,001. Test timeouts are generous harness safeguards,
   not a specified wall-clock performance contract.
   **Tests:** `runawayEnumeratorRepeatStopsWithSubmissionNumberAndCurrentPath`.

10. **Question:** Which XML spelling, namespaces, and date/time formatting
    should the tests require from the submission serializer?
    **Assumption:** Parse XML namespace-aware and compare element values and
    relevance omission; do not require attribute order, indentation,
    self-closing tags, or namespace prefixes. Exact XML bytes are compared
    between reproducible walks, as spec 07 explicitly requires. Engine
    timestamp strings include offsets; compare their local time to the
    simulated clock, consistent with spec 06. Geographic coordinate values
    are checked numerically because the engine may normalize lexical zeros.
    Space-separated select answers are compared as sets when testing fixed
    conversion, and in display order when testing generated choices.
    **Tests:** `timestampPreloadsMatchReportedStartAndPostProcessedEnd`,
    date/time/geography/select tests in `FormWalkerKindTest`, reproducibility
    tests, UTF-8 round trip, and real-form acceptance.

11. **Question:** What does “within the fieldwork period” mean for a start
    configured near midnight, and must the interview end stay inside it?
    **Assumption:** Use the exact stated arithmetic: start + d days + m
    minutes, preserving seconds, even if the resulting civil date rolls
    over. No bound is placed on the end other than clock advances and end
    not preceding start. A generator question receives the date at the time
    it is handled; default date tests use a first question to avoid guessing
    its unexposed handling timestamp. Value-changed actions additionally
    check advancing `now()`, `today()`, and `duration()` between questions.
    **Tests:** `secondPrecisionInConfiguredStartIsPreserved`, start/clock
    distribution tests, `generatedDatesUseSimulatedInterviewDate`,
    `valueChangedActionsSeeTheAdvancedSimulatedClockAtEachQuestion`, and
    `FormWalkerRealFormTest`.

12. **Question:** What happens when a select-multiple answer contains both
    offered and unoffered items, duplicate values, unusual whitespace, or
    an unoffered value that is a valid label?
    **Assumption:** Any unoffered item makes the complete answer
    unconvertible; matching is by value, never display label. Duplicate-item
    handling and nonstandard whitespace are unspecified and not tested.
    **Tests:** `anyUnofferedSelectValueRejectsTheWholeAnswer`,
    `fixedSelectOneIsMatchedByValueRatherThanLabelOrIndex`,
    `selectMultipleConvertsEverySpaceSeparatedValue`.

13. **Question:** What XForm markup exercises the engine's unsupported OSM
    control, and how much generator behavior should walker tests duplicate?
    **Assumption:** The fixture uses `<upload mediatype="osm/*">` rather than
    a literal `<osm>` element, which JavaRosa rejects. Mapping tests check
    observable generator signatures and configured values for every kind,
    plus currently offered choices, appearance, requiredness, and leaf names.
    The detailed generator-only distributions remain the responsibility of
    spec 05's existing tests.
    **Tests:** `FormWalkerKindTest` and question descriptor tests in
    `FormWalkerQuestionTest`.

## Build and validation

- **Question:** Can the prescribed offline wrapper command run under this
  build account?
- **Assumption:** Build-environment errors are not evidence about the tests.
  `mvnw.cmd -o -q test-compile` failed before Maven started: the wrapper tried
  to download Maven and could not connect. The installed distribution is
  cached under `56ba1f9f`, whereas this Windows wrapper computes a full SHA-256
  directory name. The fallback
  `.tools/apache-maven-3.9.16/bin/mvn.cmd -o -q test-compile` reached
  test compilation but failed with `java.nio.file.AccessDeniedException`
  while canonicalizing cached `commons-io-2.5.jar`. Forked compilation hit
  the same cached-JAR failure and unresolved project symbols. No repository
  copies, drive mappings, installations, or build-configuration edits were
  made.
- **Tests:** The standard Maven test-compilation result remains blocked.
  A temporary JavaCompiler probe under `.codex-runs/` successfully compiled
  all seven new Java source files with `--release 17` against the actual
  existing project class files and JUnit 5.11.4 APIs. Its file manager reads
  existing class/JAR bytes directly, avoiding the failing ZIP filesystem
  canonicalization; it does not create substitute production stubs. Outputs,
  probe source, and logs stay under `.codex-runs/`. This independently verifies
  source/API compilation but is not a successful run of the requested Maven
  command.
- The final compiled tests were executed through the cached JUnit Platform:
  **127 cases discovered, none skipped**. There were 125 direct
  `UnsupportedOperationException` failures from the unimplemented walker
  and two assertion failures because its constructor stub throws
  `UnsupportedOperationException` where the null-argument tests require
  `NullPointerException`. No fixture-loading errors remained. A temporary
  public-engine probe also confirmed that the unsupported fixtures navigate
  to questions: boolean data type 9, rank control 16 (`CONTROL_RANK`), and
  OSM control 14 (`CONTROL_OSM_CAPTURE`). This execution does not validate
  the walker behavior that lies beyond its constructor stub.
- Final repository status contains only the new tests, two resource fixtures,
  and this questions file. Production files, the POM, specs other than this
  questions file, and existing tests were not modified; no commit was made.

## Dispute validation (2026-10-09)

- **Question:** Can the prescribed offline test command run for this dispute?
- **Assumption:** Build-environment failures do not adjudicate test correctness.
  `mvnw.cmd -o -q test` failed before Maven started because the wrapper
  attempted a download and could not connect. The cached distribution's
  `.tools/apache-maven-3.9.16/bin/mvn.cmd -o -q test` reached `testCompile`
  but failed on cached `commons-io-2.5.jar`.
- **Tests:** The existing `.codex-runs/WalkerCompileProbe.java` successfully
  compiled all seven FormWalker test source files, including the amended
  distribution test, against existing project classes and cached JUnit APIs.
  No production source was inspected or changed, and no dependencies were
  installed or copied.
- **Execution:** `.codex-runs/WalkerJUnitProbe.java` ran all 128 FormWalker
  tests successfully (none skipped, aborted, or failed), including the
  amended retry test. Results are recorded in
  `.codex-runs/form-walker-dispute-junit.log`. PowerShell reported a native
  stderr error for the SLF4J missing-binding warning and command exit 1;
  the JUnit summary itself reports 128 successes and zero failures.

## Amendment 1 questions and assumptions (2026-10-09)

The new `FormWalkerAmendment*Test` classes cover only Amendment 1. Existing
tests and fixtures are retained unchanged. No production source was opened,
searched, or modified. The public APIs in the specs and the existing test
helpers provide all required signatures.

14. **Question:** Must timestamp strings have the exact fractional-second
    spelling in the example, and how should daylight-saving gaps, overlaps,
    or the artificial date underlying a time-only value be interpreted?
    **Assumption:** Assert parsed local date-times and offsets, not the
    example's millisecond spelling. Test fixed offsets (including negative,
    fractional-hour, and extreme civil offsets), and regional timestamp
    offsets in both winter and summer. Time-only answers are tested with
    fixed-offset zones; no reference date for a region's time-only offset
    is imposed. Gap/overlap resolution is not specified here and is not
    tested. The explicit coverage-form UTC suffix requirement is tested
    literally. A custom `SimpleTimeZone` tests restoration of the prior
    zone's full value, rather than restoration by ID alone. All five new
    test classes use JUnit `@Isolated`, and manual default-zone changes use
    `finally`, so they cannot overlap other JUnit tests even if parallel
    execution is enabled.
    **Tests:** `FormWalkerAmendmentTimeZoneTest`,
    `coverageUsesDefaultEnumeratorIdentityAndUtcTimestamp`.

15. **Question:** Which exception can demonstrate zone restoration after a
    session has actually begun without relying on undocumented failure modes?
    **Assumption:** Use the existing specified 100,000-navigation-step guard
    with a large repeat target and a read-only child. Invalid submission
    numbers separately test restoration at input rejection. The 120-second
    timeout is a harness safeguard, not a performance contract.
    **Tests:** `failureAfterSessionOpeningAlsoRestoresThePreviousZone`,
    `rejectedSubmissionNumberRestoresThePreviousZone`.

16. **Question:** Is any particular enumerator RNG, seed-mixing algorithm,
    device-ID encoding, or independence from other settings required?
    **Assumption:** Do not prescribe numeric assignments or device IDs.
    The enumerator depends on seed and submission number for a given range;
    changing form, start, time zone, missing rate, or batch size must not
    change that identity. A device depends only on seed and enumerator number,
    so enumerator 1 keeps its device when the configured enumerator count
    changes. Leading zeros are valid in a 15-digit device ID. For meaningful
    seed sensitivity, compare whole assignment sequences, not individual
    submissions that can legitimately choose the same enumerator. Uniformity
    uses 2,500 fixed-seed draws over the default five enumerators and the
    existing seven-standard-deviations-plus-two binomial tolerance. Padding
    tests include the one-enumerator boundary, decimal-width transitions,
    the example 120, and the maximum 10,000.
    **Tests:** `FormWalkerAmendmentEnumeratorTest`.

17. **Question:** How should `calculate-here` interact with other question
    rules, namespaces, missing body controls, or a simultaneous ordinary
    `calculate` attribute?
    **Assumption:** The amendment explicitly puts it before all ordinary
    question rules, so read-only, existing defaults, configured overrides,
    and unsupported controls do not bypass it. Attribute recognition uses
    the specified namespace URI, independent of prefix. An attribute in
    another namespace or no namespace does not opt in. A field without a
    body question is never reached; it is not processed as `calculate_here`.
    No test combines `calculate` and `calculate-here` on the same bind,
    because precedence there is unspecified and the converter does not
    produce that combination. Repeat count fixtures use count-node paths,
    as the engine requires, rather than literal `jr:count="2"` expressions.
    **Tests:** Precedence, namespace, no-body-question, and nested-context
    tests in `FormWalkerAmendmentCalculateHereTest`.

18. **Question:** What qualifies as a calculation evaluation failure, and
    what warning text or result-conversion policy should be required?
    **Assumption:** Use syntactically valid `pulldata('wrong-arity')`, whose
    evaluation failure is explicitly specified in spec 03. This avoids
    conflating form-load errors with failures encountered during navigation.
    Test both empty and populated prior values, continuation, no clock
    advance, field-name warning fragments, submission prefix, and warning
    order. Do not prescribe full warning prose or impose an unspecified
    policy for an expression that evaluates successfully but yields a value
    incompatible with its bind type.
    **Tests:** `evaluationFailureKeepsTheValueWarnsAndContinuesWithoutAClockAdvance`,
    `failedCalculationsWarnInTheOrderQuestionsAreReached`,
    `conditionalCalculationIsHandledOnlyWhenRelevantAtNavigation`.

19. **Question:** How can the final `duration()` refresh's ordering and
    dependent updates be observed without an internal evaluation hook?
    **Assumption:** A single last question ensures that the only earlier
    answer occurred at elapsed time zero. Expressions gated on positive
    duration and using `once()` can then record whether an earlier document
    node was refreshed first, and whether the end preload was still blank.
    Reverse bind order deliberately separates instance document order from
    declaration order. A call may be nested or separated from its parentheses
    by whitespace; literal text `duration()` is not a call. Other unrelated
    calculates are not proactively refreshed merely because their text
    contains that literal. Normal calculate chains and dependent relevance
    are updated as required after a value change. Repeated instances include
    counted parents and zero, one, or multiple enumerator-controlled children.
    Numeric values are checked as whole numbers without requiring a particular
    decimal spelling. No policy is imposed for final-refresh evaluation
    failures or calculations that change repeat structure during the pass.
    **Tests:** `FormWalkerAmendmentDurationTest`.

20. **Question:** Must `calculate_here` randomness match an independently
    guessed random sequence or change when a constant field is inserted?
    **Assumption:** No random draw ordering across different forms is
    specified. Check value domains, UUID format, same-seed replay across
    intervening walks and independent walkers, and different seeds/numbers
    over meaningful samples. Clock tests compare observed timestamps and
    elapsed seconds rather than an invented RNG sequence. `once()` retains
    existing values, including the zero captured by an ordinary
    `once(duration())` at session opening.
    **Tests:** Randomness, `once()`, and clock tests in
    `FormWalkerAmendmentCalculateHereTest`; `onceDurationRetainsItsInitialZeroWhilePlainDurationReachesTheEnd`.

21. **Question:** What should acceptance tests do when the published coverage
    XForm has not yet been regenerated with converter Amendment 2?
    **Assumption:** Keep the requested acceptance assertions active and load
    the actual sample with `samples/coverage` as its media directory. At
    authoring time, the checked-in `time_main` bind still has ordinary
    `calculate="once(duration())"`, with no `calculate-here` attribute, so
    its positive elapsed-time assertion cannot validate the intended
    converted form until that artifact is regenerated. Do not alter the
    sample, weaken the assertion, or silently skip the test. This is a
    prerequisite of the amended acceptance clause, not proof of a walker
    defect. The household lookup oracle reads the checked-in three-row CSV,
    which has no quoted commas or multiline fields; it does not implement
    or test a general CSV parser.
    **Tests:** `FormWalkerAmendmentCoverageTest` (submissions 1, 2, and 3,
    with duration, conditional time_main, household lookup, and device/zone
    acceptance in separate parameterized methods).

### Amendment 1 build and validation

- **Question:** Can the requested `mvnw.cmd -o -q test-compile` complete in
  this environment?
- **Assumption:** A cached-dependency filesystem failure is not a Java
  compilation error. The command reaches `testCompile` but fails while
  canonicalizing `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
  An `-e` diagnostic run confirms `java.nio.file.AccessDeniedException`
  in `WindowsLinkSupport.getRealPath`. No repository copies, installations,
  drive mappings, dependency changes, or build-configuration edits were made.
- **Tests:** The existing `.codex-runs/WalkerCompileProbe.java` successfully
  compiles all 13 `FormWalker*.java` test/support files (the seven existing
  files and six additions), with Java release 17 against actual existing
  project classes and cached JUnit 5.11.4 APIs. This verifies source/API
  compilation independently; it is not a successful Maven build. Logs and
  the temporary amendment-only JUnit runner are under `.codex-runs/`.
- **Execution:** The amendment-only JUnit run discovered 81 cases: 13 passed,
  68 failed, none skipped or aborted. No `FormLoadException` remained after
  correcting the count-node fixtures. The current implementation yields
  missing or old-behavior values for amendment calculations and metadata;
  all 12 real-coverage cases stop at the media-loading API's
  `UnsupportedOperationException`. These are retained as contract tests;
  passing the unimplemented amendment is not the purpose of this run.
  Details are in `.codex-runs/walker-amendment-junit.log`. The separate
  compilation probe passed after the final Java edits. Existing tests,
  production source, the POM, and sample forms remain unchanged. No commit
  was made.

### Device-id dispute adjudication validation

- **Question:** Can the prescribed `mvnw.cmd -o -q test` validate this
  adjudication in the current environment?
- **Assumption:** No: it reaches `testCompile` but fails on the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. This build
  limitation does not justify changing expectations derived from the spec.
- **Tests:** The existing compilation probe compiled all 18 FormWalker
  test/support sources successfully. A JUnit harness ran all 36 cases in
  `FormWalkerAmendmentCoverageTest`, `FormWalkerAmendmentEnumeratorTest`,
  and `FormWalkerDistributionTest`; all passed. Logs are
  `.codex-runs/walker-dispute2-compile.log` and
  `.codex-runs/walker-dispute2-junit.log`. The full Maven suite remains
  unverified. Question 28's pending reconciliation is now resolved by the
  adjudication in `07-form-walker.disputes.md`.

### Amendment 2 questions and assumptions

22. **Question:** Which numeric lexical forms, non-finite values, or overflow
    behavior does `minimum-seconds` support?
    **Assumption:** Test ordinary finite decimal numbers, negative numbers,
    zero, positive fractions, and a minimum exceeding one day. Empty text,
    ordinary nonnumeric text, and `1 + 90` cannot be read as numbers; the
    attribute is not an XPath expression. Do not prescribe NaN/infinity,
    exponent syntax, whitespace normalization, or overflow handling. Ignored
    minima do not imply any particular warning policy. The namespace URI,
    rather than the prefix, identifies both new attributes.
    **Tests:** `FormWalkerAmendment2MinimumTimeTest`,
    `absentInvalidAndNonpositiveMinimaNeverQualifyForViolations`, and namespace
    cases in `FormWalkerAmendment2SpeedViolationTest`.

23. **Question:** Does a question with a minimum wait or violate when it is
    skipped, when an answer fails, or once for every failed constraint attempt?
    **Assumption:** The amendment explicitly references handling under rules
    5/6 and advancing under rule 7. Blank generation, conversion failure,
    exhausted constraints, and accepted answers each constitute one handling.
    Read-only, kept defaults, unsupported controls, irrelevant fields,
    `calculate-here`, and fields without body controls do not. An override
    that replaces a default makes the question eligible again. A positive
    minimum below five seconds still qualifies for a violation.
    **Tests:** `everyHandledOutcomeAdvancesOnceAndCanRecordOnlyOneViolation`,
    `questionsNotHandledUnderRuleFiveOrSixNeitherWaitNorViolate`,
    `minimumAppliesAgainWhenAnOverrideReplacesAnExistingAnswer`, and
    `anyPositiveMinimumQualifiesEvenWhenTheClockDrawAlreadyExceedsIt`.

24. **Question:** What does a report field's "no value" mean when it already
    contains a default, and do repeated report fields show local or global totals?
    **Assumption:** No violations clears any old report value rather than
    preserving it or writing zero. Every relevant report field receives the
    submission-wide count/list, including read-only fields and fields without
    controls; this final assignment does not answer a question or advance the
    clock. Relevance is checked at the end. Tests avoid undefined attribute
    values, incompatible report data types, simultaneous `calculate` or
    `calculate-here` attributes on a report bind, and dependencies that change
    other report fields' relevance during the pass, because precedence and
    ordering for those combinations are not specified.
    **Tests:** Empty/stale/report-relevance cases,
    `everyRelevantReportIsSetIncludingReadonlyFieldsAndFieldsWithoutControls`,
    `nestedCountedAndUncountedRepeatsUseActualSiblingPositionsAndGlobalTotals`,
    `clearingAStaleCountAlsoRefreshesItsDependents`, and final propagation/order
    cases in `FormWalkerAmendment2SpeedViolationTest`.

25. **Question:** How are paths formatted at the root, in ordinary groups,
    after skipped repeat instances, and when navigation differs from XML order?
    **Assumption:** Omit the instance root and the leaf index; every intermediate
    element, including an ordinary group, has its one-based position among
    same-name instance siblings. Skipped questions do not renumber the later
    instances. Use actual navigation/violation order, preserving Unicode names,
    with commas and no spaces. Every copy of the list uses these same paths.
    **Tests:** `listUsesNavigationOrderNotInstanceOrBindOrderAndPreservesUnicode`,
    `skippedRepeatInstancesDoNotRenumberThePathsOfLaterViolations`, and nested
    repeat tests. Counted repeats use count-node paths as in the existing tests.

26. **Question:** Are exact random sequences or independence across different
    configuration values required for minimum times and speed violations?
    **Assumption:** Do not guess an RNG, mixing formula, or draw order. Verify
    replay for identical inputs, reset between walks, and meaningful seed/number
    sensitivity over samples. Rate tests use fixed seeds, 2,500 submissions per
    single-question rate, and the existing seven-standard-deviations-plus-two
    tolerance. A multi-question test checks each question's marginal rate,
    count/list consistency, order, and uniqueness without prescribing joint
    independence. The clamp test uses the exact distribution of
    `max(uniform integer in 5..60, ceil(m))`, not a new uniform draw in ceil(m)..60.
    Endpoint samples are sized to make accidental omissions very unlikely.
    The no-answer/no-clock-change comparison across rates uses fixed answers
    and a minimum above 60; it does not assume unmentioned RNG stream coupling
    for arbitrary answers or low minima across different configurations.
    **Tests:** Rate, clamping, replay, and seed tests in the new minimum-time and
    speed-violation classes; `violationDoesNotAlterFixedAnswersOrTheRequiredClockAdvance`.

27. **Question:** Does upload delay depend on the form or other configuration
    settings, and how is precision treated by `Submission.of`?
    **Assumption:** "From config.seed() and the submission number alone" applies
    to the delay, not the absolute upload timestamp. Match delays across an
    empty form and a timed form with different dates, enumerator counts, zones,
    and violation rates. Upload is local `end.plusMinutes(delay)`, preserving
    seconds and not extending interview duration. `Submission.of` preserves
    the exact supplied end, including nanoseconds, with no random delay. No
    extra XML upload field or new factory validation behavior is inferred.
    **Tests:** `FormWalkerAmendment2UploadTimeTest`; the uniformity test uses
    4,800 fixed-seed draws, individual minute bins and four broad bins, and
    checks the inclusive 1/240 endpoints.

28. **Question:** What should happen to the committed Amendment 1 tests that
    still require 15 decimal digits?
    **Assumption:** Amendment 2 explicitly supersedes that format with exactly
    16 lowercase hexadecimal characters. Per this run's instruction, leave all
    existing tests unchanged, including their now-conflicting assertions and
    earlier questions-file entries. New tests check the new format, within-
    configuration uniqueness, per-enumerator stability, replay, seed extremes,
    and independence from the form and other settings (including enumerator
    count). Leading zeros are allowed; no particular encoding/hash or universal
    collision freedom across different seeds is imposed.
    **Tests:** `FormWalkerAmendment2DeviceIdTest`. The old decimal assertions in
    `FormWalkerAmendmentEnumeratorTest` and `FormWalkerAmendmentCoverageTest`
    will need separate reconciliation when running the complete suite against
    an Amendment 2 implementation.

### Amendment 2 build and validation

- **Question:** Can `mvnw.cmd -o -q test-compile` run successfully here?
- **Assumption:** The cached-dependency access failure is environmental, not
  a test source error. The requested command reaches `testCompile` and fails
  on `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. An offline `-e`
  run confirms `java.nio.file.AccessDeniedException` in
  `WindowsLinkSupport.getRealPath`. No repository copy, drive mapping,
  installation, or build configuration modification was attempted.
- **Tests:** The existing `.codex-runs/WalkerCompileProbe.java` compiles all
  18 FormWalker test/support sources, including the five new files, with Java
  release 17 against actual project class files and cached JUnit APIs. This
  independently checks source/API compilation but is not a successful Maven
  build. Logs and the Amendment 2-only JUnit harness are under `.codex-runs/`.
- **Execution:** The final Amendment 2-only run discovers 83 cases: all fail,
  with none skipped or aborted. There are 67 `ConfigException` failures because
  `speedViolationRate` is not yet recognized, eight unsupported-operation
  failures, and eight assertion failures. No `FormLoadException` occurs on
  the exercised fixture-loading paths. Failures are retained as the requested
  contracts; these results cannot validate statistical behavior until the
  prerequisite config setting and walker amendment are implemented. The final
  compilation probe succeeds after all Java edits. Production code was not
  read or modified; existing tests and fixtures remain unchanged. No commit
  was made.

## Amendment 3 questions and assumptions (2026-10-11)

Only new `FormWalkerAmendment3*` classes and Amendment 3 CSV fixtures are
added. Existing tests and fixtures are unchanged. Expectations come from
spec 07, with the referenced CSV, configuration, and generator contracts
in specs 03, 04, and 05. Production source was not opened or searched.

29. **Question:** Which spellings count as numeric static choices? What if
    there is more than one nonnumeric choice, or a loaded value duplicates
    a static choice?
    **Assumption:** Ordinary signed integers and decimals are numbers, so
    `-2`, `0`, and `2.5` are static. Tests do not prescribe handling for NaN,
    infinity, exponent notation, whitespace around numeric values, additional
    nonnumeric choices (which violate the stated static-choice requirement),
    or collisions between static and loaded values. Deduplication is asserted
    within loaded choices, as specified. Labels never identify the value
    column; the placeholder's value does.
    **Tests:** `noPlaceholderKeepsOrdinaryNumericChoicesAndDoesNotTryMissingFile`,
    `negativeAndDecimalStaticValuesAreNotMistakenForPlaceholder`,
    `loadedDuplicatesDoNotBiasSelectOneDraws`, and the loaded-value tests.

30. **Question:** How should equal numeric sort keys, whitespace-separated
    column lists, multiple search calls, malformed XPath arguments, and
    unreadable or malformed CSV files be handled by the walker?
    **Assumption:** These cases lack a complete walker error/ordering contract
    and are not asserted. Sort tests use distinct finite numeric keys and
    verify that nonnumeric keys, including empty keys, come last in file
    order. They distinguish numeric from lexical ordering and deduplication
    before versus after sorting. Column lists use commas without surrounding
    spaces. Valid nested XPath expressions and quoted commas/parentheses are
    tested. Error cases cover the explicitly specified unsupported arities,
    unsupported types, missing named columns, and missing files.
    **Tests:** `sortbyIsNumericWithInvalidRowsLastAndDeduplicationAfterSorting`,
    `parserHandlesNestedXPathCallsCommasAndParenthesesInsideLiterals`, and
    `invalidCallOrMissingColumnWarnsAndKeepsOnlyStaticChoices`.

31. **Question:** Does "read as pulldata() reads attached files" include CSV
    syntax, media-directory resolution, lazy loading, and per-loaded-form
    caching, or only opening a file?
    **Assumption:** It includes the explicit spec 03 Amendment 3 rules:
    UTF-8/BOM, LF/CRLF, quoted commas/newlines/escaped quotes, untrimmed
    values, ragged rows, first-use reading, and caching for the LoadedForm's
    lifetime. Search's explicit `.csv` extension rule supersedes pulldata's
    suffix behavior. A stream-loaded form has no media directory. Separate
    loaded forms with different media directories must not share file data.
    Tests mutate only their own scratch copies under `.codex-runs/`.
    **Tests:** CSV parameter cases, `utf8BomAndCrLfAreAccepted`,
    `fileIsFirstReadWhenTheQuestionNeedsIt`,
    `attachedFileIsCachedForLoadedFormButFreshLoadsSeeChanges`,
    `attachmentCacheIsNotSharedBetweenFormsWithDifferentMediaDirectories`,
    and path/stream-loading tests in `FormWalkerAmendment3SearchFailureTest`.

32. **Question:** How can the tests observe Question.choices order and
    deduplication without inspecting internals or injecting a generator?
    **Assumption:** Spec 05 exposes order through generated select-multiple
    answers. Across 120 fixed-seed submissions, verify every token belongs
    to the expected list, tokens are distinct and in list order, all choices
    are reachable, and the allowed maximum selection size is reached. The
    largest fixture has eight choices; this sample makes accidental lack
    of coverage very unlikely. A separate 2,500-draw select-one test checks
    deduplicated uniform probabilities using seven binomial standard
    deviations plus two observations. No RNG implementation or exact draws
    are prescribed. Seed sensitivity compares entire answer sequences.
    **Tests:** `assertGeneratedChoices` callers,
    `loadedDuplicatesDoNotBiasSelectOneDraws`, replay and seed tests.

33. **Question:** How should warning wording/counts and successful empty
    search results be distinguished?
    **Assumption:** No particular English sentence is required. Warnings
    identify the question, identify a missing file by its filename, and use
    the usual `submission N:` prefix. Each of two questions with one missing
    file yields one search warning in navigation order; tests do not require
    a single warning for combined search, conversion, and required failures.
    A header-only file or a successful search with no matches is not an
    error and produces no search warning. Empty-choice tests use optional
    questions, avoiding unspecified missing-required warnings for empty lists.
    **Tests:** `FormWalkerAmendment3SearchFailureTest`,
    `headerOnlyFileProducesAnEmptyLoadedListWithoutWarning`, and empty-match
    parameter cases in `FormWalkerAmendment3SearchTest`.

34. **Question:** Does a zero-count repeat require deleting arbitrary
    pre-existing rows, or recalculating a count that changes after traversal?
    **Assumption:** The amendment explicitly specifies the final absence of
    repeat elements and never meeting their questions when the count is zero.
    Tests use template-based repeats with counts defaulted, calculated per
    parent, or answered before traversal. They do not prescribe behavior for
    late count changes or manually pre-populated rows. A child that cannot
    satisfy a required constraint proves the zero repeat is not traversed:
    no answers, warnings, or clock advancement are allowed. A `count()`
    dependent must report zero rather than a phantom initial instance.
    **Tests:** `FormWalkerAmendment3EmptyRepeatTest`; per-parent fixture uses
    counts 0, 1, 0, and repeated walks exercise both zero and one independently.

35. **Question:** Which configuration and submission numbers does Amendment
    3's coverage acceptance require? Optional fields may otherwise be blank.
    **Assumption:** Amendment 3 does not specify a configuration or number
    range. Use submissions 1..3, matching earlier acceptance checks, with
    only `missingRate` set to zero to exercise region and village every time.
    Other defaults remain unchanged. Read the allowed village IDs from the
    published CSV, without hard-coding its rows. The zero-person acceptance
    sets only `n_people` to zero. These integration tests may be blocked by
    unimplemented prerequisite functions elsewhere in the real form.
    **Tests:** `FormWalkerAmendment3CoverageTest`.

36. **Question:** Does search supersede ordinary relevance, constraints,
    requiredness, special-choice selection, and choice-filter behavior?
    **Assumption:** Only the offered choices and their conversion change;
    the amendment says everything else is unchanged. Tests retain ordinary
    handling of constraints, required conversion failures, weights, special
    choices, and irrelevant questions. The explicit exemption from choice
    filters applies to the original placeholder and static choices, so an
    itemset predicate that excludes them must not hide them from search.
    All six argument expressions are evaluated when reached, in the current
    question instance; repeated parents may produce different loaded sets.
    **Tests:** Search constraint/required/special/weight cases,
    `searchBypassesChoiceFiltersForBothPlaceholderAndStaticChoices`,
    `fieldArgumentsAreEvaluatedAfterEarlierQuestionsAreAnswered`, and
    `fieldReferencesAreResolvedSeparatelyForEachRepeatParent`.

### Amendment 3 build and validation

- **Question:** Can the prescribed `mvnw.cmd -o -q test-compile` complete?
- **Assumption:** Cached-dependency access errors are environmental and do
  not establish a source error. The exact command reaches `testCompile` but
  fails accessing `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
  An offline `-e` rerun confirms `java.nio.file.AccessDeniedException` in
  `WindowsLinkSupport.getRealPath`. No dependencies were installed or copied,
  drives mapped, or build configurations modified.
- **Tests:** The existing `.codex-runs/WalkerCompileProbe.java` successfully
  compiles all 23 FormWalker test/support sources, including the five new
  files, with Java release 17 against actual project class files and cached
  JUnit APIs. This verifies Java/API compilation independently; it is not a
  successful Maven invocation. All logs and temporary harness files remain
  under `.codex-runs/`.
- **Execution:** The final Amendment 3-only JUnit run discovers 89 cases:
  18 pass and 71 fail, with none skipped or aborted. Failures comprise 67
  assertion failures and four runtime failures in the published coverage
  form's `/data/oracle/agg_count_if` calculation (a repeated-node XPath type
  mismatch). There are no `FormLoadException` or container failures. Most
  search failures expose placeholder values remaining offered, attached
  values being rejected, or missing expected warnings. The failures are
  retained as contracts for the pending implementation; passing prerequisite
  cases do not imply the amendment is implemented. The final Java edits
  were compiled before this run. Existing tests, production source, build
  configuration, and published sample files are unchanged; no commit was made.

### Dispute 3 build and validation

- **Question:** Can the prescribed `mvnw.cmd -o -q test` complete for
  this adjudication?
- **Assumption:** Failure accessing the cached dependency does not
  establish a test source error. The command reaches `testCompile` but
  fails on `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
  No dependencies or build configuration were changed.
- **Tests:** The existing `.codex-runs/WalkerCompileProbe.java` compiled
  all 23 FormWalker test/support sources successfully against project
  class files and cached JUnit APIs. This verifies compilation separately
  from Maven. Logs: `.codex-runs/walker-dispute3-maven-test.log` and
  `.codex-runs/walker-dispute3-compile.log`.
- **Execution:** The five disputed test classes ran 73 cases: 62 passed,
  11 failed, none skipped or aborted. All failures are the `opened`
  date-only assertion in the two time-zone parameterized tests; the XML
  contains a midnight timestamp with an offset. These expectations are
  retained under Amendment 4, including spec 03's clarification that
  date storage applies regardless of declared field type. Log:
  `.codex-runs/walker-dispute3-junit.log`.

## Amendment 5 questions and assumptions (2026-10-12)

Only new `FormWalkerAmendment5*` Java files and the
`form-walker/amendment5/template.xml` fixture are added. Existing test
classes and fixtures are retained. Production source was neither read nor
modified. Expectations come from spec 07 and its referenced public loader,
configuration, generator, and simulated-session contracts.

37. **Question:** Does "form order" mean control/navigation order or primary
    instance element order? Are changes within a pass immediately visible?
    **Assumption:** It means the order of questions in the form body. Each
    repair/clear is visible to subsequent questions and their calculations;
    earlier questions are checked again on subsequent passes. Tests put the
    body in a different order from the instance and use backward dependencies.
    **Tests:** `finalFailureWarningsFollowControlOrderRatherThanInstanceElementOrder`,
    `clearingALaterQuestionInvalidatesAnEarlierOneOnTheNextPass`, and
    `fiveRepairPassesAreFollowedByClearingUntilEvenEarlierDependentsAreValid`.

38. **Question:** Does the initial final check count toward the five passes,
    and how is termination after that limit interpreted?
    **Assumption:** There are at most five repair passes total. After those,
    only invalid populated questions are cleared, repeatedly until no invalid
    populated question remains. A chain of length five must fully repair; a
    longer backward dependency chain retains the five repaired terminal
    values and clears the remaining prefix, including values invalidated by
    earlier clearing sweeps. No clearing-order assertion is made in this
    fallback phase. Blank nodes do not violate the guarantee.
    **Tests:** The chain cases of lengths 1, 2, 5, 6, 8, and 12 in
    `FormWalkerAmendment5ConvergenceTest`.

39. **Question:** How should constraints that depend on post-processing
    (speed reports, final duration, end timestamp), volatile XPath functions,
    changing repeat counts, or newly relevant questions be handled?
    **Assumption:** The guarantee and placement of the final check do not
    fully specify these interactions. Tests do not prescribe a resolution
    for constraints made invalid *after* the check by post-processing, or
    for volatile/self-modifying constraints and new repeat creation. They
    do verify that repair changes are visible to calculations and report
    relevance, and that a now-irrelevant question is excluded. They do not
    require traversal of a question that was never previously relevant.
    **Tests:** `repairsRefreshCalculationsAndReportRelevanceBeforeReportsArePopulated`,
    `clearingRefreshesDependentCalculations`, `aRepairCanMakeAnotherInvalidQuestionIrrelevant`,
    and the exclusion tests in `FormWalkerAmendment5ScopeTest`.

40. **Question:** What happens if the generator returns null or an
    unconvertible value during a final repair? Can tests observe exactly 50
    calls through the public API?
    **Assumption:** Do not assert the unspecified final-repair null/conversion
    warning or retry policy. Initial invalid-default handling follows the
    explicit ordinary rules, including null clearing. There is no public
    generator injection/call counter. The retry test therefore checks the
    probability implied by one original draw and up to 50 fresh repair draws:
    700 submissions with bad:good weights 49:1, expected clearing probability
    `0.98^51`, tolerance seven binomial standard deviations plus two. This
    detects missing/stale retries, very short budgets, and unlimited retries;
    it cannot reliably distinguish 49 from 50 attempts. No exact random
    sequence or implementation of seed mixing is assumed.
    **Tests:** `retriesUseFreshDrawsAndApproximatelyFiftyAttemptsBeforeClearing`,
    select repair cases, and invalid-default blank handling.

41. **Question:** How are final-failure warnings distinguished and counted?
    **Assumption:** A failure warning contains the field name, `constraint`,
    and `end`, without requiring an exact sentence. Each failed populated
    instance emits one warning, then a separate `required` warning when
    required on the final instance. Subsequent passes do not warn again for
    the cleared value. Warning prefixes, ordering, immutability, and replay
    remain as specified. Repeat warnings need name the question, but need not
    include its indexed path.
    **Tests:** `assertFinalFailure` callers, final requiredness/replay tests,
    warning order test, and per-relevant-repeat failure test.

42. **Question:** How can real-form constraints be checked independently
    against the final submission without mutating or recalculating it?
    **Assumption:** Parse the original form to identify supported question
    controls and their binds; exclude `calculate_here`, read-only, absent,
    and blank fields. Build a separate minimal form containing the serialized
    submission and only its original type/constraint bindings. No calculates,
    actions, preloads, relevance rules, or repeat counts are copied. Repeat
    controls and blank templates preserve repeat identity for `index()`;
    no submitted value or actual repeat instance is regenerated. (Corrected
    during Dispute 4: omitting repeat declarations lost the required context.) Evaluate
    each original expression using JavaRosa's public XPath API in the node's
    actual repeated context, with simulated time set to submission end. This
    uses the engine's specified expression semantics, not the walker's check.
    Select values are read as strings in this oracle so no choice-list
    conversion can alter them. The oracle explicitly requires absolute bind
    and control paths (as in the current published forms); it fails rather
    than silently ignoring a different path syntax. Sanity tests prove that
    a second-repeat violation and a cross-field violation are detected and
    that source calculations cannot repair an invalid snapshot.
    **Tests:** `FormWalkerAmendment5AcceptanceTest` and its oracle support.
    Acceptance is separately parameterized for every XML form and every
    submission number 1..20, with exactly the default configuration and the
    explicitly required coverage-3 media directory. The remaining forms use
    the loader's normal media-directory behavior. No claim requires a
    particular question to be nonblank in each stochastic submission.

43. **Question:** What reliability is required for stochastic success and
    end-date checks, and do constraint hints change the expected draws?
    **Assumption:** Fixed seeds and generous probabilities are used, never
    exact sequences. Select-one/two-choice select-multiple tests permit
    exhaustion with the specified warning and require at least 55 successful
    results in 60 submissions. Numeric repeat
    repairs draw from ranges of size at most three. The end-date test permits
    exhaustion but requires at least one successful repair over 160 walks;
    50 uniform draws over 365 dates give a probability below `3e-10` of no
    successes in all walks. Hints may increase this success rate. Statistical
    attempt-budget assertions use explicit choice weights and wrap `.` in
    `concat(., '')`, alongside the gate reference, so spec 09 Amendment 2
    supplies neither hints nor fallback candidates. The numeric navigation
    budget test likewise uses `. * 2 = 2`.
    **Tests:** Select repair, repeat context, seed sensitivity, and
    `finalRepairUsesTheClockDateAfterCrossingMidnight`.

44. **Question:** Which existing calculated/unsupported values are covered?
    **Assumption:** An editable calculated question remains a question;
    `once()` plus explicit `readonly=false()` makes its initial calculated
    value stable and replaceable. A calculation without a control, a read-only
    control, and a `calculate_here` question are explicitly excluded. The
    amendment does not clearly say whether an invalid default on an
    unsupported control should be kept under rule 2 or proceed to its usual
    unsupported warning; no new expectation is imposed for that combination.
    **Tests:** `editableCalculationDefaultIsCheckedWhenReached` and
    `fieldsWithoutControlsReadonlyAndCalculateHereAreExcludedEvenWithFalseConstraints`.

### Amendment 5 build and validation

- **Question:** Can the requested `mvnw.cmd -o -q test-compile` complete?
- **Assumption:** A cached-jar filesystem access failure is environmental,
  not evidence of a Java source error. The initial up-to-date baseline
  invocation completed, but compiling the added sources with the exact
  requested command fails in `testCompile` on
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. A final offline
  `-e` invocation confirms `java.nio.file.AccessDeniedException` at
  `WindowsLinkSupport.getRealPath`. No repositories were copied, packages
  installed, drives mapped, or build settings changed.
- **Tests:** The diagnostic compiler in
  `.codex-runs/WalkerAmendment5CompileProbe.java` successfully compiles all
  30 FormWalker test/support sources with Java release 17 against the actual
  project classes and cached JavaRosa/JUnit APIs. It reads the existing jar
  class entries in memory to bypass the compiler's failing Windows path
  canonicalization; it supplies no substitute production classes. This is
  separate compilation verification, not a successful Maven invocation.
  Logs are `.codex-runs/walker-amendment5-test-compile.log`,
  `.codex-runs/walker-amendment5-test-compile-detail.log`, and
  `.codex-runs/walker-amendment5-probe-compile.log`.
- **Execution:** The diagnostic JUnit launcher runs 174 cases: 130 pass,
  44 fail with assertion failures, and none are skipped or aborted. There
  are no container, form-loading, or runtime exception failures. This
  includes all 120 published-form/submission combinations and two oracle
  sanity tests. One published submission retains `resp_contact=0` in the
  IPA form, violating its phone constraint. The other failures exercise
  the pending Amendment 5 behavior. The full log is
  `.codex-runs/walker-amendment5-junit.log`. After relaxing the select
  success-frequency check to allow the contract's rare exhausted retries,
  all sources were compiled again and the unit classes rerun; the acceptance
  classes were unchanged. The final targeted log is
  `.codex-runs/walker-amendment5-unit-final.log`.
- Existing tests, production source, build configuration, and sample forms
  remain unchanged. No commit was made. Temporary probes and logs stay under
  `.codex-runs/`.

### Dispute 4 build validation

- **Question:** Can the prescribed offline Maven test run compile the
  adjudicated tests in this environment?
- **Assumption:** The recurring cached dependency failure is a build
  limitation, not a reason to change test expectations. `mvnw.cmd -o -q
  test` again failed in `testCompile` on `commons-io-2.5.jar`. No build
  settings, cached dependencies, or production files were changed.
- **Tests:** The existing offline compiler probe compiled all 30
  FormWalker test/support sources, including the corrected retry fixtures,
  repeat-aware oracle, and its two new regression cases. This verifies
  compilation against the real project classes and cached APIs, but is
  not a successful Maven run. Logs: `.codex-runs/walker-dispute4-maven-test.log`
  and `.codex-runs/walker-dispute4-compile.log`. Execution results are
  recorded in the final adjudication in `07-form-walker.disputes.md`.
