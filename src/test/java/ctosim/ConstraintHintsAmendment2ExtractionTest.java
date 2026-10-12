package ctosim;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.javarosa.form.api.FormEntryController.ANSWER_OK;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment2ExtractionTest {
    private static void bounds(ConstraintHints h, Double lo, boolean li, Double hi, boolean ui) {
        assertEquals(lo == null ? null : new Bound(lo, li), h.lower());
        assertEquals(hi == null ? null : new Bound(hi, ui), h.upper());
    }

    @Test void rangeOrDontKnowOrRefusedBecomesThreeOrderedLeafAlternatives() throws Exception {
        var h = extract("(. >= 0 and . <= 10) or . = 99 or . = 88");
        assertEquals(3, h.alternatives().size());
        bounds(h.alternatives().get(0), 0d, true, 10d, true);
        bounds(h.alternatives().get(1), 99d, true, 99d, true);
        bounds(h.alternatives().get(2), 88d, true, 88d, true);
        assertEmptyOwnComponents(h);
        for (var leaf : h.alternatives()) {
            assertTrue(leaf.alternatives().isEmpty());
            assertTrue(leaf.candidates().isEmpty());
        }
        assertEquals(List.of("0", "10", "99", "88"), h.candidates());
    }

    private static void assertEmptyOwnComponents(ConstraintHints h) {
        assertAll(() -> assertNull(h.lower()), () -> assertNull(h.upper()),
                () -> assertNull(h.earliest()), () -> assertNull(h.latest()),
                () -> assertNull(h.minLength()), () -> assertNull(h.maxLength()), () -> assertNull(h.pattern()),
                () -> assertNull(h.prefix()), () -> assertNull(h.minSelected()), () -> assertNull(h.maxSelected()),
                () -> assertTrue(h.included().isEmpty()), () -> assertTrue(h.excluded().isEmpty()));
    }

    @Test void conjunctionDistributesLeftToRightAcrossEveryPairOfAlternatives() throws Exception {
        var h = extract("(. > 1 or . >= 3) and (. < 9 or . <= 7)");
        assertEquals(4, h.alternatives().size());
        bounds(h.alternatives().get(0), 1d, false, 9d, false);
        bounds(h.alternatives().get(1), 1d, false, 7d, true);
        bounds(h.alternatives().get(2), 3d, true, 9d, false);
        bounds(h.alternatives().get(3), 3d, true, 7d, true);
        assertEquals(List.of("2", "8", "7", "3"), h.candidates());
    }

    @Test void surroundingConjunctsAreAppliedToEveryBranchWithTightestBounds() throws Exception {
        var h = extract(". >= 5 and ((. > 2 and . < 9) or (. > 6 and . <= 8)) and . <= 9");
        assertEquals(2, h.alternatives().size());
        bounds(h.alternatives().get(0), 5d, true, 9d, false);
        bounds(h.alternatives().get(1), 6d, false, 8d, true);
    }

    @Test void eachBranchKeepsItsOwnPatternPrefixLengthsAndChoiceHints() throws Exception {
        var h = extract("(starts-with(., '09') and string-length(.) = 10 and regex(., '09[0-9]+'))"
                + " or (selected(., 'b') and not(selected(., 'a')) and count-selected(.) = 1)");
        assertEmptyOwnComponents(h);
        assertEquals(2, h.alternatives().size());
        var text = h.alternatives().get(0);
        assertEquals("09", text.prefix()); assertEquals(10, text.minLength()); assertEquals(10, text.maxLength());
        assertEquals("09[0-9]+", text.pattern()); assertTrue(text.included().isEmpty());
        var choice = h.alternatives().get(1);
        assertNull(choice.pattern()); assertNull(choice.prefix()); assertNull(choice.minLength());
        assertEquals(List.of("b"), List.copyOf(choice.included()));
        assertEquals(List.of("a"), List.copyOf(choice.excluded()));
        assertEquals(1, choice.minSelected()); assertEquals(1, choice.maxSelected());
    }

    @ParameterizedTest @ValueSource(strings = {"true() and . = 7", ". = 7 and true()", "false() or . = 7", ". = 7 or false()"})
    void booleanIdentitiesCollapseToASingleDirectHint(String expression) throws Exception {
        var h = extract(expression);
        assertTrue(h.alternatives().isEmpty());
        bounds(h, 7d, true, 7d, true);
        assertEquals(List.of("7"), h.candidates());
    }

    @Test void trueDisjunctIsAnEmptyAlternativeAndDoesNotEraseTheOtherBranch() throws Exception {
        var h = extract("true() or . = 7");
        assertEquals(2, h.alternatives().size());
        assertEquals(ConstraintHints.none(), h.alternatives().get(0));
        bounds(h.alternatives().get(1), 7d, true, 7d, true);
    }

    @Test void falseBranchDoesNotLeakHintsOrCandidates() throws Exception {
        var h = extract("(false() and . = 99) or . = 7");
        assertTrue(h.alternatives().isEmpty());
        bounds(h, 7d, true, 7d, true);
        assertEquals(List.of("7"), h.candidates());
    }

    @ParameterizedTest @ValueSource(ints = {63, 64, 65, 100})
    void unionKeepsOnlyTheFirstSixtyFourAlternatives(int count) throws Exception {
        String expression = IntStream.range(0, count).mapToObj(i -> ". = " + (1000 + i))
                .collect(java.util.stream.Collectors.joining(" or "));
        var h = extract(expression);
        int kept = Math.min(64, count);
        assertEquals(kept, h.alternatives().size());
        for (int i = 0; i < kept; i++) bounds(h.alternatives().get(i), 1000d + i, true, 1000d + i, true);
        assertEquals(IntStream.range(0, kept).mapToObj(i -> Integer.toString(1000 + i)).toList(), h.candidates());
    }

    @Test void cartesianExpansionIsAlsoCappedInLeftThenRightOrder() throws Exception {
        String left = IntStream.range(0, 9).mapToObj(i -> ". >= " + i).collect(java.util.stream.Collectors.joining(" or "));
        String right = IntStream.range(0, 9).mapToObj(i -> ". <= " + (100 + i)).collect(java.util.stream.Collectors.joining(" or "));
        var h = extract("(" + left + ") and (" + right + ")");
        assertEquals(64, h.alternatives().size());
        for (int i = 0; i < 64; i++) bounds(h.alternatives().get(i), (double) (i / 9), true, 100d + i % 9, true);
    }

    @Test void independentIfUsesCurrentRelativeContextAndIsReevaluatedOnEveryCall() throws Exception {
        var s = session("int", "if(../../source = 17 and ../x = 31, . = ../../source + 1, . = ../x + 1)");
        bounds(ConstraintHints.forCurrentQuestion(s), 18d, true, 18d, true);
        var index = SimulatedSessionTestSupport.question(s, "source");
        assertEquals(ANSWER_OK, s.answerQuestion(index, new IntegerData(20), true));
        SimulatedSessionTestSupport.question(s, "group/answer");
        var changed = ConstraintHints.forCurrentQuestion(s);
        bounds(changed, 32d, true, 32d, true);
        assertEquals(List.of("32"), changed.candidates());
        assertTrue(changed.alternatives().isEmpty());
    }

    @ParameterizedTest @CsvSource({"true(),7", "false(),8"})
    void constantIfReadsOnlyItsChosenBranch(String condition, double expected) throws Exception {
        var h = extract("if(" + condition + ", . = 7, . = 8)");
        bounds(h, expected, true, expected, true);
        assertTrue(h.alternatives().isEmpty());
    }

    @Test void selfDependentIfExpandsConditionAndNegatedCondition() throws Exception {
        var h = extract("if(. > 10, . <= 20, . >= 0)");
        assertEquals(2, h.alternatives().size());
        bounds(h.alternatives().get(0), 10d, false, 20d, true);
        bounds(h.alternatives().get(1), 0d, true, 10d, true);
    }

    @Test void commonSelectMultipleIfIncludesSpecialAloneOrExcludesIt() throws Exception {
        var h = extract("if(selected(., '-99'), count-selected(.) = 1, count-selected(.) >= 1)");
        assertEquals(2, h.alternatives().size());
        var yes = h.alternatives().get(0); var no = h.alternatives().get(1);
        assertEquals(List.of("-99"), List.copyOf(yes.included()));
        assertEquals(1, yes.minSelected()); assertEquals(1, yes.maxSelected());
        assertEquals(List.of("-99"), List.copyOf(no.excluded()));
        assertEquals(1, no.minSelected()); assertNull(no.maxSelected());
    }

    static Stream<Arguments> negatedComparisons() {
        return Stream.of(Arguments.of("not(. > 3)", null, false, 3d, true),
                Arguments.of("not(. >= 3)", null, false, 3d, false),
                Arguments.of("not(. < 3)", 3d, true, null, false),
                Arguments.of("not(. <= 3)", 3d, false, null, false),
                Arguments.of("not(3 < .)", null, false, 3d, true),
                Arguments.of("not(3 <= .)", null, false, 3d, false),
                Arguments.of("not(3 > .)", 3d, true, null, false),
                Arguments.of("not(3 >= .)", 3d, false, null, false),
                Arguments.of("not(. != 3)", 3d, true, 3d, true),
                Arguments.of("not(3 != .)", 3d, true, 3d, true),
                Arguments.of("not(not(. >= 3))", 3d, true, null, false));
    }

    @ParameterizedTest @MethodSource("negatedComparisons")
    void negationComplementsEveryComparison(String expression, Double lo, boolean li, Double hi, boolean ui) throws Exception {
        bounds(extract(expression), lo, li, hi, ui);
    }

    @Test void deMorganOfConjunctionGivesAnOrderedUnion() throws Exception {
        var h = extract("not(. < 2 and . > 8)");
        assertEquals(2, h.alternatives().size());
        bounds(h.alternatives().get(0), 2d, true, null, false);
        bounds(h.alternatives().get(1), null, false, 8d, true);
    }

    @Test void deMorganOfDisjunctionGivesOneIntersection() throws Exception {
        var h = extract("not(. < 2 or . > 8)");
        assertTrue(h.alternatives().isEmpty()); bounds(h, 2d, true, 8d, true);
    }

    @Test void commonNegatedSelectRuleCreatesExcludedAndMaximumCountAlternatives() throws Exception {
        var h = extract("not(selected(., '-99') and count-selected(.) > 1)");
        assertEquals(2, h.alternatives().size());
        assertEquals(List.of("-99"), List.copyOf(h.alternatives().get(0).excluded()));
        assertEquals(1, h.alternatives().get(1).maxSelected());
    }

    @ParameterizedTest @ValueSource(strings = {". != -999", "-999 != .", "not(. = -999)", "not(-999 = .)"})
    void notEqualAndNegatedEqualExcludeTheXPathFormattedValue(String expression) throws Exception {
        assertEquals(List.of("-999"), List.copyOf(extract(expression).excluded()));
    }

    @ParameterizedTest @ValueSource(strings = {"café", "0007", "refused"})
    void stringExclusionsPreserveTheLiteralWithoutNumericNormalization(String value) throws Exception {
        assertEquals(List.of(value), List.copyOf(extract(". != '" + value + "'").excluded()));
        assertEquals(List.of(value), List.copyOf(extract("'" + value + "' != .").excluded()));
    }

    @Test void exclusionsAndInclusionsDeduplicateInAppearanceOrderAndPreserveUnicode() throws Exception {
        var h = extract(". != 2.5 and not(selected(., 'é')) and . != 2.5 and . != ../../source"
                + " and selected(., 'z') and selected(., 'a') and selected(., 'z')");
        assertEquals(List.of("2.5", "é", "17"), List.copyOf(h.excluded()));
        assertEquals(List.of("z", "a"), List.copyOf(h.included()));
    }

    static Stream<Arguments> counts() {
        return Stream.of(Arguments.of("count-selected(.) > 3", 4, null),
                Arguments.of("count-selected(.) >= 3", 3, null),
                Arguments.of("count-selected(.) < 3", null, 2),
                Arguments.of("count-selected(.) <= 3", null, 3),
                Arguments.of("count-selected(.) = 3", 3, 3),
                Arguments.of("3 < count-selected(.)", 4, null),
                Arguments.of("3 <= count-selected(.)", 3, null),
                Arguments.of("3 > count-selected(.)", null, 2),
                Arguments.of("3 >= count-selected(.)", null, 3),
                Arguments.of("3 = count-selected(.)", 3, 3),
                Arguments.of("count-selected(.) > 2.5", 3, null),
                Arguments.of("count-selected(.) >= 2.5", 3, null),
                Arguments.of("count-selected(.) < 2.5", null, 2),
                Arguments.of("count-selected(.) <= 2.5", null, 2),
                Arguments.of("count-selected(.) = 2.5", 3, 2),
                Arguments.of("count-selected(.) >= -4", 0, null),
                Arguments.of("count-selected(.) <= -1", null, -1),
                Arguments.of("count-selected(.) = 0", 0, 0),
                Arguments.of("not(count-selected(.) > 2)", null, 2),
                Arguments.of("count-selected(.) >= ../../source - 15", 2, null),
                Arguments.of("count-selected(.) >= 1 and count-selected(.) > 2 and count-selected(.) < 6 and count-selected(.) <= 4", 3, 4));
    }

    @ParameterizedTest @MethodSource("counts")
    void countsUseIntegerRoundingContextAndTightestBounds(String expression, Integer lo, Integer hi) throws Exception {
        var h = extract(expression);
        assertEquals(lo, h.minSelected()); assertEquals(hi, h.maxSelected());
        assertNull(h.lower()); assertNull(h.upper()); assertNull(h.minLength()); assertNull(h.maxLength());
    }

    @ParameterizedTest @CsvSource({"'substr(., 0, 1) = ''0''',0", "'substr(., 0, 2) = ''09''',09",
            "'starts-with(., ''é-'')',é-"})
    void fixedPrefixShapesPreserveTheirLiteral(String expression, String prefix) throws Exception {
        assertEquals(prefix, extract(expression).prefix());
    }

    @Test void emptyLiteralPrefixIsPreserved() throws Exception {
        assertEquals("", extract("starts-with(., '')").prefix());
    }

    @ParameterizedTest @ValueSource(strings = {"substr(., 1, 2) = '09'", "substr(., 0, 1) = '09'",
            "starts-with(../x, '09')", "selected(../x, 'a')", "selected(., ../x)",
            "count-selected(.) >= string-length(.)", "not(regex(., 'x'))", "not(starts-with(., 'x'))"})
    void unsupportedNewShapesDoNotThrowOrSuppressUsableSiblingHints(String expression) throws Exception {
        var h = extract("(" + expression + ") and . >= 5");
        bounds(h, 5d, true, null, false);
        assertNull(h.prefix()); assertNull(h.minSelected()); assertNull(h.maxSelected());
        assertTrue(h.included().isEmpty()); assertTrue(h.excluded().isEmpty());
    }

    @Test void unsupportedAlternativeRemainsAnEmptyHintBranch() throws Exception {
        var h = extract(". * 2 = 4 or . = 7");
        assertEquals(2, h.alternatives().size());
        assertEquals(ConstraintHints.none(), h.alternatives().get(0));
        bounds(h.alternatives().get(1), 7d, true, 7d, true);
    }

    @Test void equalityCandidatesComeBeforeClosedIntervalEndpointsAndDeduplicateGlobally() throws Exception {
        var h = extract("(. > -3.5 and . < 2.5) or . = -999 or (. >= 2 and . <= 4) or . = -999");
        assertEquals(List.of("-3", "2", "-999", "4"), h.candidates());
    }

    @Test void equalityCandidatesIncludeNonNumericUnicodeLiteralsInSourceOrder() throws Exception {
        assertEquals(List.of("拒否", "café", "ok"), extract(". = '拒否' or . = 'café' or . = 'ok' or . = '拒否'").candidates());
    }

    @Test void fractionalEqualityCandidateUsesXPathNumberFormatting() throws Exception {
        var candidates = extract(". = 2.5").candidates();
        assertFalse(candidates.isEmpty());
        assertEquals("2.5", candidates.get(0));
    }

    @Test void oneSidedAndCountAndLengthBoundsDoNotInventNumericCandidates() throws Exception {
        assertTrue(extract(". > 5 and string-length(.) = 3 and count-selected(.) = 2").candidates().isEmpty());
    }

    @Test void nestedConditionalsAndNestedNegationsAreExpandedRecursively() throws Exception {
        var h = extract("if(. > 0, if(. > 10, . <= 20, . <= 5), . >= -5)");
        assertEquals(3, h.alternatives().size());
        bounds(h.alternatives().get(0), 10d, false, 20d, true);
        bounds(h.alternatives().get(1), 0d, false, 5d, true);
        bounds(h.alternatives().get(2), -5d, true, 0d, true);
        var negated = extract("not(not(selected(., 'a')) or not(count-selected(.) >= 2))");
        assertEquals(List.of("a"), List.copyOf(negated.included()));
        assertEquals(2, negated.minSelected());
        assertTrue(negated.alternatives().isEmpty());
    }

    @Test void dateHintsSurviveNegationAndDisjunctionWithoutBecomingNumericBounds() throws Exception {
        var s = session("date", "not(. < /data/date) or . <= date('2041-01-01')");
        var h = ConstraintHints.forCurrentQuestion(s);
        assertEquals(2, h.alternatives().size());
        var first = h.alternatives().get(0); var second = h.alternatives().get(1);
        assertEquals(new DateBound(java.time.LocalDate.of(2040, 2, 29), true), first.earliest());
        assertNull(first.latest()); assertNull(first.lower());
        assertEquals(new DateBound(java.time.LocalDate.of(2041, 1, 1), true), second.latest());
        assertNull(second.earliest()); assertNull(second.upper());
    }

    @Test void independentIfDoesNotEvaluateAnUnchosenFailingExpression() throws Exception {
        var h = extract("if(true(), . = 7, . > regex('a', '['))");
        bounds(h, 7d, true, 7d, true);
        assertEquals(List.of("7"), h.candidates());
    }

    @ParameterizedTest @ValueSource(strings = {"number('bad')", "1 div 0", "../../empty", "'bad'", "regex('a', '[')"})
    void unusableCountExpressionsDoNotThrowOrEraseOtherHints(String rhs) throws Exception {
        var h = extract("count-selected(.) >= " + rhs + " and selected(., 'a')");
        assertNull(h.minSelected()); assertNull(h.maxSelected());
        assertEquals(List.of("a"), List.copyOf(h.included()));
    }

    @Test void exclusionThatReferencesTheCurrentAnswerGivesNoHint() throws Exception {
        var h = extract(". != . + 1 and not(selected(., 'a'))");
        assertEquals(List.of("a"), List.copyOf(h.excluded()));
    }

    @Test void equalityCandidatesUseTheCurrentSessionValueInsteadOfTheExpressionText() throws Exception {
        assertEquals(List.of("17", "32"), extract(". = ../../source or . = ../x + 1").candidates());
    }

    @Test void firstRegexWithinEachDistributedBranchWinsInOriginalTermOrder() throws Exception {
        var h = extract("(regex(., 'a') or regex(., 'b')) and regex(., 'c')");
        assertEquals(2, h.alternatives().size());
        assertEquals("a", h.alternatives().get(0).pattern());
        assertEquals("b", h.alternatives().get(1).pattern());
    }
}
