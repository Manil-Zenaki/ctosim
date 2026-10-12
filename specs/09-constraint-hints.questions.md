# Spec 09 questions and test assumptions

Expectations come from spec 09 and the contracts it amends (04, 05, 06 and 07), with spec 01 used to open engine sessions. No production source or implementation bodies were read. The tests use public engine APIs and existing test helpers. Generated form constraints are substituted into a resource template, with XML attribute escaping.

1. **What must the six-argument Question constructor do when hints is null?**
   Assumption: the stated "never null" invariant permits rejecting null or normalizing it to non-null hints. No particular exception type is prescribed by spec 09; the invariant test permits runtime rejection or a successful constructor with non-null hints. Original five-argument constructor null checks and defensive copying remain required by spec 05.
   Tests: `ConstraintHintsExtractionTest.aSuccessfullyConstructedQuestionNeverExposesNullHints`, constructor tests.

2. **What is the required supported subset of Java regular expressions?**
   Assumption: `^09[0-9]{8}$` must be supported because the acceptance criterion explicitly requires it. For other patterns, the spec permits the documented fallback when unsupported. Tests for literals, empty strings, Unicode, alternation, classes, quantifiers and lookahead therefore accept either a full match or the original text fallback. They do not require a specific generation algorithm or uniformity over regex matches (especially for infinite languages). Invalid `[` and unsatisfiable `(?!)` must fall back. A usable phone pattern takes precedence over conflicting length hints.
   Tests: `ConstraintHintsTextAndPrecedenceTest` pattern tests and both walker acceptance tests.

3. **Does "first regex" mean first matching supported term, even if its pattern is invalid?**
   Assumption: extraction considers only `regex(., literal)` shapes, preserves the literal exactly, and does not validate Java regex syntax. A preceding nonliteral regex contributes no hint; a later eligible literal contributes the pattern. Generation, rather than extraction, decides whether that selected pattern is usable. Regex conjunctions are not combined into an intersection.
   Tests: `literalPatternIsPreservedWithoutValidationOrNormalization`, `firstRegexWinsAndAllKindsOfHintsCanCoexist`, `nonLiteralRegexBeforeLiteralDoesNotClaimThePatternSlot`.

4. **How are negative maxima, fractional equality and contradictory length bounds represented?**
   Assumption: the conversion rules apply to each side independently. Equality to 3.5 gives min 4/max 3. Only negative minima are clamped to zero; a negative maximum is retained. Generation then ignores an inverted length interval, including an absent minimum (default 1) with maximum 0. Explicit min 0/max 0 produces the empty string, distinct from a null blank answer.
   Tests: `lengthComparisonsBecomeInclusiveIntegerBounds`, `lengthIsUniformWithSpecifiedDefaultsAndAllowsEmptyString`, `invertedLengthBoundsFallBackToOriginalTextRules`.

5. **Does a 365-day one-sided date interval include both endpoints?**
   Assumption: it contains exactly 365 dates, L through L+364 or U-364 through U, consistent with the explicit default range in spec 05. Exclusive endpoints move by one calendar day before intersection/fallback, including across leap day. Tests use both DATE and DATETIME and a five-day override so accidentally translating by the configured date width is detectable.
   Tests: `ConstraintHintsDateTest.dateHintsSelectTheSpecifiedUniformClosedInterval`.

6. **What width is used to translate a disjoint one-sided integer range with fractional configured endpoints?**
   Assumption: the explicit `R.max - R.min` is computed from the configured endpoints before the final integer draw. R=[200.2,204.8], L=300 therefore gives [300,304.6] and integer draws 300..304. Numeric emptiness is checked after the specified hint-bound rounding; contradictory hints are retained by extraction and ignored by generation.
   Tests: `translatedIntegerRangeUsesTheUnroundedConfiguredWidth`, number interval cases, contradictory extraction cases.

7. **Does decimal formatting still round singleton hints or bounds with more than two decimal places?**
   Assumption: spec 05 formatting remains in force, even when the formatted answer no longer equals a sub-cent hint. Singleton 2000.345 formats to 2000.35 (and -2000.345 to -2000.35). Interval tests otherwise use cent-aligned closed endpoints to avoid imposing additional clamping behavior. Precision overflow, nonfinite manually constructed bounds, LocalDate extremes and lengths beyond Integer capacity have no stated policy and are not tested.
   Tests: `decimalSingletonUsesOriginalHalfUpFormatting`, decimal interval tests.

8. **Which field-reference spellings and self-reference cases are required at the engine boundary?**
   Assumption: XLSForm `${field}` references are already expanded into XPath in converted XForms. Synthetic XForms use absolute or relative XPath references; the real converted forms cover the conversion boundary. Expressions that explicitly use `.` inside E are rejected. Alias references resolving back to the current node (such as an absolute path to itself), dynamic indirection and multi-node reference policy are not asserted because the spec does not clarify whether detection is syntactic or semantic. Parenthesized nested conjunctions are flattened; an `or` subtree is opaque.
   Tests: `unsupportedShapesContributeNoHints`, numeric conjunction cases, `relativeAndAbsoluteReferencesUseQuestionContextAndCurrentAnswers`, walker relative/repeat cases.

9. **How should date values and evaluation errors be exercised?**
   Assumption: typed date and date-time nodes and native date-valued functions retain their date type before numeric coercion. Offset date-time values are converted to the JVM default zone; timezone tests restore the previous setting. The evaluation-error case uses an invalid regex inside E to fail during evaluation of an otherwise parseable expression. Malformed XPath rejected before a session exists is a loader concern. Null sessions, date-valued string-length bounds and boolean-number conversion are not specified further and are not asserted.
   Tests: extraction date, simulated-clock and timezone tests; `unusableOrFailingExpressionsDoNotThrowOrSuppressOtherTerms`.

10. **Does "ignored" imply identical draws for identical random state?**
    Assumption: when hints do not apply to a kind, or invalid length bounds are ignored without evaluating a regex, generation follows the same original rule with the same random stream. These tests compare equal-seed output sequences. Regex failure tests only check fallback shape because an attempted pattern may consume randomness before failing. Pattern probabilities are not specified; uniformity is asserted only for numeric/date draws, lengths, each character and texts overrides.
    Tests: ignored-kind and ignored-length tests in `ConstraintHintsTextAndPrecedenceTest`. Distribution checks use fixed seeds, 12,000–50,000 draws and seven binomial standard deviations plus two counts. Decimal quartiles allow extra tolerance for half-cent rounding.

11. **How should real-form acceptance avoid adding requirements about relevance or optional blanks?**
    Assumption: use the existing read-only converted files in `samples/xforms/`. The migration test sets exactly the three required fixed overrides and checks warnings for all submissions 1..50, without requiring a question that may be irrelevant to be reached. The coverage test additionally sets missingRate=0 to exercise nonblank phone answers, requires a phone node, and checks every phone in all 50 submissions. Missing acceptance files fail explicitly rather than silently skipping.
    Tests: the two real-form acceptance methods in `ConstraintHintsWalkerTest`.

12. **How can the unchanged 50-attempt limit be observed through this API?**
    Assumption: no new instrumentation or production hook is introduced just to count retries. Tests check successful engine retry for an unsupported hint shape and bounded termination with constraint/required warnings for impossible or fixed-conflicting answers. They do not claim to measure exactly 50 internal generator calls; the public API does not expose that count. These tests preserve the spec 07 behavior rather than infer a different retry limit.
    Tests: `ignoredConstraintShapesStillUseEngineRetryFallback`, `unsatisfiedOrFixedContradictingAnswersStillGiveConstraintAndRequiredWarnings`.

## Build verification

- The requested `mvnw.cmd -o -q test-compile` was attempted. The Windows wrapper cannot start Maven: it expects a SHA-256-named distribution directory, while the prepopulated wrapper cache uses `56ba1f9f`, so it attempts a blocked download before Maven handles `-o`. No repositories were copied, drives mapped, or build files changed.
- Calling the already bundled `.tools/apache-maven-3.9.16/bin/mvn.cmd -o -q test-compile` reaches javac but fails with `java.nio.file.AccessDeniedException` while Java's ZIP filesystem resolves the existing `commons-io-2.5.jar` path. A forked javac attempt also reports missing existing project classes and the same file-access failure.
- A diagnostic compiler driver under `.codex-runs/` reads the same existing binary dependencies through an in-memory classpath, avoiding that Windows path-resolution failure. JavaCompiler with `--release 17`, UTF-8 and annotation processing disabled successfully compiled all new test sources and their three existing helper sources against the actual compiled public APIs. This is supplementary compilation evidence, not a claim that the requested Maven command passed. Logs and diagnostic probes remain under `.codex-runs/` only.
- The compiled suite was also executed through JUnit Platform: 271 cases discovered and started, 102 passed, 169 failed, none skipped or aborted. The remaining failures expose unimplemented extraction or missing hint application, as expected for this stage; no form-loading or parameter-resolution failures remained. The repeat fixture uses a bound count node because JavaRosa requires a node reference for `jr:count`.

## Amendment 2 questions and test assumptions

This run adds only `ConstraintHintsAmendment2*` Java classes and the
`constraint-hints/amendment2/late-candidate.xml` resource. All pre-existing tests
are retained verbatim, including older expectations that Amendment 2 supersedes
(for example, treating `or` as opaque). No production source was read or changed;
the amended public constructor and accessors were verified with `javap -public`
against compiled classes. The spec and its referenced contracts supply the
expectations; existing test helpers supply form construction and engine navigation.

13. **How is a constraint with zero alternatives represented, and are impossible
    alternatives removed during extraction?**
    Assumption: the spec does not define a distinct public representation for zero
    alternatives, or explicitly require extraction to prune contradictory terms.
    Tests therefore assert boolean identities with at least one surviving branch
    and test each stated unsatisfiability rule through generator choice using
    manually constructed alternatives. They do not require pruning or a particular
    record for bare `false()`.
    Tests: `booleanIdentitiesCollapseToASingleDirectHint`,
    `falseBranchDoesNotLeakHintsOrCandidates`, `impossibleAlternatives` cases in
    `ConstraintHintsAmendment2AlternativesTest`.

14. **What does "first" mean for a Cartesian product, and do truncated branches
    still contribute candidates?**
    Assumption: enumerate each left alternative in order, and for it each right
    alternative in order. The retained first 64 alternatives are also the source
    of candidates; discarded alternatives do not contribute candidates. Duplicate
    alternatives are not required to be merged. A `true()` branch remains an
    unconstrained alternative, rather than absorbing other disjuncts.
    Tests: `conjunctionDistributesLeftToRightAcrossEveryPairOfAlternatives`,
    `cartesianExpansionIsAlsoCappedInLeftThenRightOrder`,
    `unionKeepsOnlyTheFirstSixtyFourAlternatives`,
    `trueDisjunctIsAnEmptyAlternativeAndDoesNotEraseTheOtherBranch`.

15. **Which candidates arise from string equalities, reversed equalities,
    fractional singletons, and impossible intervals?**
    Assumption: the explicit `. = E` rule includes nonnumeric strings (including
    Unicode), even though those strings give no numeric bound. Candidate strings
    are evaluated in the current session and deduplicated by string equality.
    Candidates are collected per alternative: equality values first, then closed
    numeric ends. For `. = 2.5`, only the first candidate `2.5` is asserted; the
    spec leaves unclear whether its inverted INTEGER-rounded endpoints are added.
    Reversed equality candidates, date candidates, and candidates from impossible
    alternatives are not asserted. No expectation relies on filtering candidates
    against unsupported conjuncts before the engine tries them.
    Tests: candidate extraction methods in `ConstraintHintsAmendment2ExtractionTest`
    and candidate rescue/order/endpoint methods in `ConstraintHintsAmendment2WalkerTest`.

16. **What happens after all exclusion retries fail, and is "up to 20 times"
    inclusive of the first draw?**
    Assumption: bounded termination is required, but the returned value and the
    initial-draw-versus-redraw count are unspecified. Exhaustion tests assert
    termination without asserting a returned value or a random-call protocol.
    Successful-redraw tests use domains where a run of 20 excluded draws has
    negligible probability (at most 0.2 per draw, fixed seeds). They do not
    prescribe exclusion membership normalization beyond exact answer strings.
    Tests: exclusion methods in `ConstraintHintsAmendment2TextAndExclusionTest`.

17. **How do prefixes behave when too long for the maximum, when multiple
    incompatible prefixes occur, or when a pattern is unsupported?**
    Assumption: those combinations have no specified resolution and are not
    tested. Satisfiable prefix tests count the prefix in the total length and
    inherit the old length defaults when a length bound is present; with neither
    bound there are eight suffix characters. A usable pattern takes precedence.
    BMP Unicode tests avoid imposing an unstated supplementary-code-point length
    policy. Empty-prefix extraction is tested, but its suffix alphabet is not.
    Tests: `prefixes`, `prefixTotalLengthsAreUniformAndIncludeBothEndpoints`,
    `eachSuffixPositionUsesUniformCharacters`,
    `usablePatternTakesPrecedenceOverAConflictingPrefix`, `emptyLiteralPrefixIsPreserved`.

18. **How are special codes and weights handled inside constrained select_multiple
    answers?**
    Assumption: explicit inclusion can require a special code alongside another
    choice; count hints suppress the old automatic special singleton draw. The
    unchanged rules retain choice order, uniqueness and weighted sampling of
    remaining choices; included choices are mandatory even with zero weight.
    Tests do not impose an ordinary-versus-special distribution for unconstrained
    remaining slots. "Hints do not constrain" is tested with absent selection
    hints; redundant bounds and exclusions naming unavailable choices are left open.
    Tests: selection count, inclusion, ordering, weighting, and special-singleton
    methods in `ConstraintHintsAmendment2SelectTest`.

19. **Are missing included choices and contradictory single-set select hints
    errors or fallback cases?**
    Assumption: for select_multiple, these cannot be met with offered choices and
    hence use the specified fallback. An empty choice list still follows the
    original blank rule. A select_one with every choice excluded falls back.
    Unavailable or multiple inclusions on a direct select_one hint set are not
    tested; multiple inclusions are tested as an explicitly unsatisfiable
    alternative. As confirmed by the original clarification 10, ignored hints
    preserve the baseline random stream.
    Tests: `impossibleSelectionHintsAreIgnoredWithTheSameRandomStream`,
    `noOfferedChoicesStillReturnsBlankDespiteInclusions`, alternatives fallback tests.

20. **How do new hints interact with fixed values, texts overrides and blanks?**
    Assumption: the unchanged blank and fixed-value rules run before all new
    generator behavior. A texts override ignores prefix and length hints. The
    interaction between texts overrides and excluded values is not asserted,
    because "hints are ignored" and the new general TEXT exclusion rule could
    be read differently. Candidates are a later walker fallback even when the
    rejected generator proposals came from a fixed override: Amendment 2 states
    that candidates are tried after 50 rejected proposals without an exception.
    Tests: `blankAndFixedRulesStillPrecedeAlternativeChoice`,
    `fixedValueStillWinsOverExclusion`, `textsOverrideIgnoresPrefixAndLengthHints`,
    walker candidate tests.

21. **Can these tests observe exactly 50 walker attempts or prove exactly 20
    exclusion redraws without production instrumentation?**
    Assumption: the public API does not expose attempt counts or a specified
    number of random primitive calls. Fixed rejected proposals reliably exercise
    candidate fallback, but do not measure the internal 50 calls. A minimum time
    of 61 seconds makes any extra question-time advance observable. For final
    repair, a valid kept default is invalidated by a later gate answer, and the
    default text generator cannot produce the equality candidate; the test
    requires refreshed candidates, unchanged `answered()` and no repair time.
    Tests: candidate clock and final-check methods in
    `ConstraintHintsAmendment2WalkerTest`.

22. **What is the null-input policy for the four new collection arguments?**
    Assumption: their public values must never be null, but the spec does not
    prescribe rejection versus normalization of null constructor inputs or null
    collection elements. Tests use valid collections and check defensive copies,
    immutability and iteration order; they do not mandate an exception policy.
    Tests: `ConstraintHintsAmendment2ContractTest`.

23. **How should real-form acceptance handle optional blanks and unrelated
    warnings?**
    Assumption: use exactly `SimulationConfig.defaults()` for every XML form and
    submission 1 through 50, and explicitly load coverage-3 with its media
    directory. Optional blanks and other warning categories are outside this
    amendment's stated criterion. The assertion matches the meaning of exhausted
    constraint/final constraint warnings without requiring exact punctuation.
    Tests: `ConstraintHintsAmendment2AcceptanceTest` (300 cases with the six
    currently published forms). Generator distribution checks elsewhere use fixed
    seeds, 12,000 to 30,000 draws and the existing seven-standard-deviation-plus-two
    binomial tolerance, avoiding dependence on a particular random API call order.

### Amendment 2 build verification

- The initial `mvnw.cmd -o -q test-compile` succeeded before adding sources (the
  existing output was up to date). After adding sources, the requested command
  reaches recompilation but fails in javac's ZIP filesystem with
  `java.nio.file.AccessDeniedException` for the existing
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The `-e` retry confirms
  that cause. No build configuration or dependencies were changed, copied or
  downloaded. Logs are under `.codex-runs/09-amendment2-test-compile*.log`.
- A diagnostic compiler under `.codex-runs/` reads the existing binary classpath
  in memory to bypass that Windows path-resolution error, as used in earlier
  test-author runs. It compiles the new classes, existing required test helpers
  and a JUnit runner with `--release 17`, UTF-8 and annotation processing disabled.
  This is supplementary evidence that the test sources compile, not a claim
  that the requested Maven recompilation passed.
- Final diagnostic compilation succeeds for all eight new Java files, the four
  existing helper sources they need, and the runner (13 sources total). JUnit
  execution of the six focused test classes discovers and runs 195 cases:
  55 pass and 140 fail. All 300 real-form acceptance cases also run: 297 pass
  and three fail with the prohibited constraint warning. There are no skipped
  or aborted tests, failed test containers, form-loading errors or parameter
  resolution errors. All 143 failures are assertion failures against the current
  unimplemented Amendment 2 behavior. Final logs:
  `.codex-runs/09-amendment2-memory-compile.log`,
  `.codex-runs/09-amendment2-junit.log`, and
  `.codex-runs/09-amendment2-acceptance.log`.
