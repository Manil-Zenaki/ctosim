
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
