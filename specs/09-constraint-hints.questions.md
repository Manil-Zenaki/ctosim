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
