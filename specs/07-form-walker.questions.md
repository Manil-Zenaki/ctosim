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
