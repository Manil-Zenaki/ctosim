
## Dispute 1: tests superseded by Amendment 2

1. `ConstraintHintsExtractionTest` compares extracted hints with records
   built by the seven-argument constructor, whose `candidates` are empty.
   Amendment 2 gives every constraint candidates (the values of `. = E`
   terms and the closed ends of an interval), so `. = -2.5` now has
   candidates `[-2.5, -2, -3]` and a date equality has its date. The
   bounds, lengths and patterns these tests check are unchanged. The
   same tests also expect `or`, `not(...)` and `selected(...)` shapes to
   give no hint, which Amendment 2 replaces. Concerned:
   `extractsOnlyTheTightestNumericBounds`, `dateEqualitySetsBothInclusiveBounds`,
   `dateTimeBoundsUseTheJvmDefaultZone`, `firstRegexWinsAndAllKindsOfHintsCanCoexist`,
   `relativeAndAbsoluteReferencesUseQuestionContextAndCurrentAnswers`,
   `ignoresDisjunctionInsideConjunctionWithoutDiscardingValidSiblings`,
   `unsupportedShapesContributeNoHints`. Requested: compare the
   pre-amendment components only (bounds, dates, lengths, pattern), or
   expect the candidates and alternatives Amendment 2 defines.
2. `ConstraintHintsWalkerTest.unsatisfiedOrFixedContradictingAnswersStillGiveConstraintAndRequiredWarnings`
   (case `. >= 900000000 and . <= 999999999` with a fixed value 5)
   expects a blank answer. Amendment 2 makes the walker try the
   constraint's candidates after rejected proposals, also when they came
   from a fixed value (Clarification 20), so the answer is `900000000`.
   Requested: keep the `. > 10 and . < 0` case (no candidate is valid),
   and for the other expect a valid answer without warnings.

## Adjudication of Dispute 1

1. **Upheld: the original extraction expectations must reflect Amendment 2.**
   The seven-argument constructor still has empty collections, but extracted
   hints can now carry candidates. Whole-record equality therefore tests more
   than the unchanged bounds, dates, lengths and pattern contract. The numeric
   bounds, date equality, time-zone equality, mixed-hint and current-reference
   tests now compare those seven components individually and require a single
   conjunction to expose direct hints. Their original component expectations
   are retained. Candidate behavior remains covered by the Amendment 2 tests.

   This does not endorse the dispute's claim that every constraint produces
   candidates, or require the exact fractional list `[-2.5, -2, -3]`.
   Candidate generation is limited to the specified equalities and interval
   endpoints; confirmed question 15 deliberately leaves fractional inverted
   endpoints, reversed equalities, date candidates and impossible-alternative
   candidates unasserted. These component tests likewise avoid imposing an
   exact candidate list for those cases.

   The old no-hint expectations for `or`, negated comparisons, `selected`,
   and `!=` are wrong under Amendment 2's explicit reading rules. They are
   replaced with focused checks of ordered alternatives, complementary bounds,
   inclusion and exclusion. The remaining unsupported-shape cases are kept.
   The conjunction/disjunction test now verifies that sibling bounds apply to
   each branch, using satisfiable branches so it does not prescribe pruning of
   contradictory alternatives (confirmed question 13). The range-or-equality
   case now checks ordered alternatives and the unambiguous candidate order.

2. **Upheld for the fixed phone value; retain the impossible-constraint case.**
   Amendment 2's walker rule and clarification 20 require candidate fallback
   even after fixed-value proposals fail. For the inclusive phone interval,
   candidates start with `900000000`, then `999999999`; the first is accepted.
   The replacement test requires answer `900000000`, one answered question and
   no warnings. The separate impossible-constraint test retains a blank answer,
   zero answered questions, and both constraint and required warnings: no
   value or candidate can satisfy `. > 10 and . < 0`.

### Verification

- `mvnw.cmd -o -q test` was attempted. Maven reaches test compilation but
  fails with `java.nio.file.AccessDeniedException` for the existing
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`; the `-e` retry
  confirms the cause. This is not a successful Maven run.
- The existing diagnostic compiler approach successfully compiled all
  constraint-hint tests and required test helpers against compiled binaries
  (`--release 17`, UTF-8, 18 sources including the runner), without reading
  production source or changing dependencies or build configuration.
- JUnit Platform ran `ConstraintHintsExtractionTest`, `ConstraintHintsWalkerTest`,
  `ConstraintHintsAmendment2ExtractionTest`, and `ConstraintHintsAmendment2WalkerTest`:
  **209 tests passed**, none failed, skipped or aborted; all 25 containers passed.
  Logs: `.codex-runs/09-dispute-maven-test.log`,
  `.codex-runs/09-dispute-maven-detail.log`, `.codex-runs/09-dispute-compile.log`,
  and `.codex-runs/09-dispute-junit.log`.
