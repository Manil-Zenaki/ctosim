# Spec 03 questions and test assumptions

Tests are derived from spec 03 and its explicit relationship to spec 01. No repository production source was read. JavaRosa public signatures, its controller's repeat-creation delegation, and existing test helpers were inspected solely to drive entry sessions.

1. **What counts as a number when an argument or list item is text?**
   Assumption: ordinary decimal strings are numeric (including negative values and fractions); empty strings, arbitrary words and NaN are not. This follows the requirement to rank string list items numerically and use native argument conversion. Infinity, exponent syntax, localized numbers and boolean-to-number coercion are left untested because their treatment is not specified.
   Tests: `SurveyCtoScalarFunctionsTest.numbersUseGroupingHalfEvenRoundingAndNoTrailingZeros`, `SurveyCtoRankingTest`, invalid-position tests in `SurveyCtoListFunctionsTest`.

2. **How should decimal half-even midpoints be interpreted after conversion to Double?**
   Assumption: the rounding rule applies to the numeric value supplied by the engine. Midpoint tests use exactly representable binary fractions (1.0625 and 1.1875, and their negatives), so no expectation depends on a binary approximation of a decimal tie. Nonfinite formatting and locale-specific numeric input syntax remain untested.
   Tests: `SurveyCtoScalarFunctionsTest.numbersUseGroupingHalfEvenRoundingAndNoTrailingZeros`.

3. **Are separators literal, and are list items trimmed or case-folded?**
   Assumption: the specified string separator is literal, including regex metacharacters and multi-character/Unicode separators; item equality is exact. Whitespace and case are retained. Empty items include leading and trailing items. Self-overlapping separators are not tested because a splitting convention is not given.
   Tests: `SurveyCtoListFunctionsTest`.

4. **How are repeated or leading/trailing spaces in geopoints handled?**
   Assumption: no extra tokenization rule beyond the stated space-separated items is imposed. Distance and shortening tests use single spaces. `geo-scatter` must preserve even unusual whitespace because it explicitly returns its input unchanged. Tabs, newlines and extra malformed altitude/accuracy tokens are not tested for distance/shortening.
   Tests: `SurveyCtoGeographyTest`; whitespace handling is tested only by `geoScatterReturnsItsFirstArgumentExactlyWithoutRandomness`.

5. **Does short-geopoint validate or normalize coordinates?**
   Assumption: it only takes the first two items and joins them with one space. Coordinate range validation is explicitly required for distance-between, not for short-geopoint. Original numeric spelling and nonnumeric tokens are therefore preserved.
   Tests: `SurveyCtoGeographyTest.shortGeopointKeepsTheFirstTwoItemsWithoutNumericValidation`.

6. **Must geo-scatter validate its range or point?**
   Assumption: the unconditional result “p unchanged” means no validation or displacement, including negative/non-numeric range values. Wrong argument counts still fail. No statistical tests or seed assumptions apply because randomness is explicitly disabled.
   Tests: `SurveyCtoGeographyTest.geoScatterReturnsItsFirstArgumentExactlyWithoutRandomness`, `SurveyCtoFunctionContractTest`.

7. **Which select variants and labels must choice-label support?**
   Assumption: “select question” includes both select1 and select; the explicit value argument chooses the item regardless of the current answer. Static and translated inline choices are tested. Dynamic itemsets, choice-filter visibility, missing translations, rich-label markup and duplicate choice values are not specified sufficiently to assert a policy.
   Tests: `SurveyCtoFieldsTest`, `SurveyCtoRepeatContextTest.choiceLabelInsideARepeatResolvesTheCurrentInstanceForBothReferenceStyles`.

8. **What happens if a field argument is a scalar string or refers to several nodes?**
   Assumption: tests supply actual node references, as required for choice-label and rank-index. Choice-label on an existing non-select node or missing node returns the stated empty string; relevant on a missing node returns false. Scalar “path strings”, arbitrary scalar relevant arguments, multi-node relevant arguments and rank-index on nonrepeated fields have no asserted result. The rank-index arity tests use a dedicated repeat fixture so valid argument prefixes cannot fail for unrelated reasons.
   Tests: `SurveyCtoFieldsTest`, `SurveyCtoFunctionContractTest`.

9. **Do language/relevance changes force recalculation even without an explicit dependency?**
   Assumption: only evaluation-time behavior is required; scheduling belongs to the engine. Tests explicitly evaluate after language or relevance changes. Calculate-update tests change fields syntactically referenced in the expression. They do not require the engine to infer implicit dependencies on language or an unrelated ancestor-relevance field.
   Tests: `SurveyCtoFieldsTest`, `SurveyCtoRankingTest.rankCalculationRecomputesWhenRepeatedScoreChanges`, `rankCalculationRecomputesWhenPositionChanges`.

10. **Which exception and message are required for invalid arity?**
    Assumption: any Exception from expression evaluation satisfies the spec; no subtype/message is mandated. XPath parsing and session initialization occur outside the exception assertion, so they cannot supply a false positive. All smaller argument counts, one excess argument and several excess arguments are covered for fixed arities; hash rejects zero and accepts one/many.
    Tests: `SurveyCtoFunctionContractTest.everyFunctionRejectsUnsupportedArgumentCountsAtEvaluation`.

11. **What floating-point tolerance should geography use?**
    Assumption: one millimetre absolute error is permitted for floating-point haversine arithmetic, including antipodes and longitude wraparound. Analytical equatorial/meridional arcs and one non-equatorial arc use the specified radius, not a second implementation of haversine. Invalid coordinates return Double NaN.
    Tests: `SurveyCtoGeographyTest.distanceUsesSpecifiedSphereAndIgnoresAltitudeAndAccuracy`, `invalidPointInEitherArgumentReturnsNumericNaN`.

12. **How is the real-form N=1 case exercised when its parent group is relevant only for counts greater than one?**
    Assumption: the acceptance criterion concerns engine-created instances even in that case. Tests fill the relevant gating answers through FormDef and explicitly call the engine's repeat-creation API if instances are not already created, including for N=1. They assert the calculated string values without adding/removing XML or editing instance nodes directly. Counts 1, 3 and the published form's upper boundary 25 are covered. The acceptance test discovers all .xml files directly under samples/xforms, and fails rather than silently skipping if none exist.
    Tests: `SurveyCtoRealFormsTest`.

13. **Does relevance change the numbering of repeat instances?**
    Assumption: “position of the repeat instance” is its structural 1-based position, not its position among visible repeats. An irrelevant second instance does not renumber the third. Nested repeat positions restart in each parent, and intervening nonrepeat groups do not alter them. Post-deletion recalculation timing is left untested.
    Tests: `SurveyCtoRepeatContextTest`.

14. **How are hash arguments converted to strings?**
    Assumption: the native XPath spellings apply (integer 42 becomes `42`, booleans `true`/`false`, missing field value empty); UTF-8 has no BOM or implicit Unicode normalization. The test oracle uses Java's standard SHA-256 on independently specified joined strings, including empty arguments and U+001F boundaries. Date/time and nonfinite numeric representations are not asserted.
    Tests: `SurveyCtoScalarFunctionsTest.hashIsLowercaseSha256OfUtf8ValuesSeparatedByUnitSeparator`, `hashAcceptsManyArgumentsAndIncludesEveryArgument`.

## Amendment 2: numeric select arguments

15. **Does "any expression of the form" include body predicates and output expressions?**
    Assumption: this amendment's explicit parenthetical defines the required sites: the five bind attributes (`calculate`, `relevant`, `constraint`, `required`, `readonly`) and setvalue values. No new numeric-coercion requirement is asserted for body ref/nodeset predicates, labels, output expressions, or unrelated XML attributes. Numeric literals, field references, and nested expressions at the listed sites are all covered, including relative and absolute references in repeats.
    Tests: `SurveyCtoNumericSelectCalculationTest`, `SurveyCtoNumericSelectBindingTest`, `SurveyCtoNumericSelectContextTest`.

16. **Which native select semantics should be asserted beyond the amendment's examples?**
    Assumption: only the first argument changes, through XPath string conversion. Ordinary finite integers and decimals (including zero and negatives) are single tokens; matching remains exact and token-based, so numeric 1 does not match `01` or `1.0`. Existing text retains leading zeros and Unicode. Zero-based selected-at position 1 is beyond a singleton and yields the native empty string. The tests do not specify new rules for nonfinite numbers, dates, booleans, numeric second arguments, negative/fractional positions, whitespace tokenization, or invalid arity. Those are not new behaviors defined by this amendment.
    Tests: `SurveyCtoNumericSelectCalculationTest.numericExpressions`, `stringExpressions`, and the corresponding parameterized calculate/setvalue tests.

17. **Does amendment 2 change result types or how calculated results are stored?**
    Assumption: it changes the first argument only. selected still produces Boolean, count-selected Double, and selected-at String at XPath evaluation time. Calculate results are subject to the answer-storage conversion described in Amendment 1. Most assertions wrap output in brackets to avoid confusing storage conversion with function semantics; a separate test evaluates expressions retrieved from a stored constraint through JavaRosa's public API to check raw result types. The calculated arm is explicitly checked to hold IntegerData even with a string bind.
    Tests: `SurveyCtoNumericSelectBindingTest.allThreeFunctionsPreserveTheirXpathResultTypesInStoredExpressions`, `SurveyCtoNumericSelectCalculationTest.calculatedStringFieldActuallyHoldsANumber`, and bracketed result assertions in all new classes.

18. **What execution order is required between first-load setvalue actions and calculates?**
    Assumption: no new ordering guarantee is introduced. Setvalue tests use the engine's `odk-instance-first-load` event with typed instance defaults, empty fields, and literal/computed arguments. Cases using the calculated arm replace that reference with numeric literal 1 solely for the action test, avoiding an assumption about whether the arm has been calculated yet. Each form is opened twice to check actions in every session.
    Tests: `SurveyCtoNumericSelectBindingTest.instanceFirstLoadSetvalueUsesNumericStringCoercion`.

19. **What should tests require for calls parsed outside the form?**
    Assumption: "not affected" means the session must preserve JavaRosa's native outcome, including its exception type when native evaluation fails. The tests compare freshly parsed calls against a fresh JavaRosa evaluation context over the same instance, rather than imposing an additional coercion rule or hard-coding exception behavior for all three functions. All positive numeric-coercion tests use stored form expressions, not external XPath calls.
    Tests: `SurveyCtoNumericSelectCalculationTest.expressionsParsedOutsideTheFormRetainNativeEvaluationBehavior`.

20. **How should the real-form acceptance condition be evaluated when ancestors are irrelevant?**
    Assumption: acceptance concerns each question/group's stored treatment relevance expression. Tests first enable the interview through its availability, introduction, one-member household, respondent availability, and consent answers so that references to the treatment fields are not masked as empty. They set both treatment fields through FormDef.setValue with IntegerData, then obtain the actual stored conditions from the engine's public triggerables API and evaluate them in the target node context, independently of whether a downstream list group is currently visible. No replacement XPath is parsed, and neither the sample XML nor instance nodes are directly edited. All four combinations of the two arms are covered; the expected branch values come from the published form's bindings. Synthetic bind tests additionally verify relevance updates on instance nodes.
    Tests: `SurveyCtoNumericSelectRealFormTest.publishedTreatmentRelevancesEvaluateWithoutExceptionsAndFollowStoredNumbers`, `SurveyCtoNumericSelectBindingTest.numericSelectCallsWorkInEveryBooleanBindAndUpdateAfterAnswers`.

21. **Must random arm assignment be reproducible or produce both arms in a small sample?**
    Assumption: Amendment 2 supplies no seed or distribution guarantee. The once/random pattern is checked against each session's actual stored arm, without a probabilistic assertion that both values appear. Deterministic cases and explicit real-form assignments independently exercise both arms. Numeric answer changes, clearing values, fresh sessions, existing repeats, nested repeats, and engine-created repeats are tested without requiring a new scheduling policy.
    Tests: `SurveyCtoNumericSelectContextTest.randomOnceCalculatedArmBranchesAccordingToItsStoredNumberInEverySession`, `SurveyCtoNumericSelectRealFormTest`, and the update/context tests in the new classes.
