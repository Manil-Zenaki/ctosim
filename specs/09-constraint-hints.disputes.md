
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

## Dispute 2: a satisfiable constraint expected to stay blank (spec 07 Amendment 6)

`ConstraintHintsAmendment2WalkerTest.rejectedCandidatesStillLeaveBlankAndProduceConstraintAndRequiredWarnings`
uses `. >= 100 and . <= 104 and . * 2 = 206`, which 103 satisfies. Its
candidates (100, 104) are rejected, but spec 07 Amendment 6 then
searches the integers of the drawing range [100, 104] and answers 103.
Requested: keep the test's intent (candidates rejected, then blank with
constraint and required warnings) with a constraint no integer
satisfies, for example `. >= 100 and . <= 104 and . * 2 = 207`.

## Adjudication of Dispute 2

1. **Upheld: the blank-answer expectation needs an impossible integer constraint.**
   The original constraint admits exactly the integer 103. Spec 07 Amendment 6
   requires search after the 50 proposals and spec 09's candidates fail, including
   when a fixed override supplied those proposals (spec 07's confirmed assumption
   45). Spec 05 Amendment 2's INTEGER search includes 103, so the original
   blank-answer and warning expectations are wrong.

   The dispute's stated drawing range needs a qualification: this fixture uses
   the default integer range [0, 100], whose intersection with the hints
   [100, 104] is [100, 100] under spec 09. Thus 103 is guaranteed by search
   level 2 (every integer from -10000 to 10000), rather than by a level 1
   drawing range of [100, 104]. This does not change the decision.

   Changed the unsupported conjunct from `. * 2 = 206` to `. * 2 = 207`.
   The closed-end candidates remain 100 and 104 and both are rejected;
   no integer satisfies the full constraint because 207 / 2 is 103.5.
   All search levels therefore fail as well. The retained assertions require
   a blank answer, zero answered questions, and both constraint and required
   warnings, as spec 07 requires after all fallbacks fail. Renamed the test to
   `impossibleIntegerConstraintLeavesBlankAndProducesConstraintAndRequiredWarningsAfterFallbacks`
   to reflect that contract. The prior Dispute 1 adjudication is unchanged.

### Dispute 2 verification

- `mvnw.cmd -o -q test` reaches test compilation but fails with
  `java.nio.file.AccessDeniedException` for the existing
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, confirmed by
  an offline `-e` retry. Maven did not pass.
- The existing diagnostic compiler successfully compiled all constraint-hint
  tests and required helpers against compiled binaries (18 sources including
  the runner, Java 17 target). No production source was read or changed.
- JUnit Platform ran `ConstraintHintsExtractionTest`, `ConstraintHintsWalkerTest`,
  `ConstraintHintsAmendment2ExtractionTest`, and `ConstraintHintsAmendment2WalkerTest`:
  **209 tests passed**, zero failed, skipped or aborted; all 25 containers passed.
- Logs: `.codex-runs/09-dispute2-maven-test.log`,
  `.codex-runs/09-dispute2-maven-detail.log`, `.codex-runs/09-dispute2-compile.log`,
  and `.codex-runs/09-dispute2-junit.log`.
