package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment9Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment9DependencyTest {
    @ParameterizedTest @ValueSource(strings = {"int", "long"})
    void directIntegerCountUsesRepeatRangeInsteadOfGlobalIntegerRange(String type) throws Exception {
        Submission result = run9(direct9(type, "", "required=\"true()\"", input("answer")), 2, 2, "");
        assertCount9(result, 2);
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 1000})
    void repeatRangeIncludesZeroSingletonAndMaximumAllowedCount(int target) throws Exception {
        assertCount9(run9(direct9(), target, target, ""), target);
    }

    @Test void defaultConfigurationGivesSmallCountsWithoutAFieldOverride() throws Exception {
        FormWalker walker = new FormWalker(direct9(), SimulationConfig.defaults());
        for (int n = 1; n <= 30; n++) {
            Submission result = walker.walk(n);
            int answer = Integer.parseInt(value(result, "answer"));
            assertTrue(answer >= 1 && answer <= 3, "Default count must be in 1..3: " + answer);
            assertCount9(result, answer);
        }
    }

    @Test void integerRangeControlIsAlsoACountQuestion() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\"",
                "<range ref=\"/data/answer\" start=\"0\" end=\"20\" step=\"1\"><label>Count</label></range>");
        assertCount9(run9(form, 2, 2, ""), 2);
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 8})
    void countDependencyTraversesEveryControlFreeCalculationEvenInReverseBindOrder(int depth) throws Exception {
        StringBuilder instance = new StringBuilder("<answer/>");
        StringBuilder binds = new StringBuilder(bind("answer", "int", "required=\"true()\""));
        for (int i = 1; i <= depth; i++) instance.append("<c").append(i).append("/>");
        for (int i = depth; i >= 1; i--) {
            String source = i == 1 ? "/data/answer" : "/data/c" + (i - 1);
            binds.append(bind("c" + i, "int", "calculate=\"" + source + " + 1\""));
        }
        LoadedForm form = form(instance + row9("row"), binds + rowBind9("row"),
                input("answer") + repeat9("row", "/data/c" + depth));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "answer"));
        assertEquals(2 + depth, count(result, "row"), "Count expression itself is not clamped");
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void allIntegerDependenciesOfABranchedCalculationReceiveSmallRanges() throws Exception {
        LoadedForm form = form("<left/><right/><a/><b/><total/>" + row9("row"),
                bind("left", "int", "required=\"true()\"") + bind("right", "int", "required=\"true()\"")
                        + bind("a", "int", "calculate=\"/data/left + 1\"")
                        + bind("b", "int", "calculate=\"/data/left + /data/right\"")
                        + bind("total", "int", "calculate=\"/data/a + /data/b\"") + rowBind9("row"),
                input("left") + input("right") + repeat9("row", "/data/total"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "left"));
        assertEquals("2", value(result, "right"));
        assertEquals(7, count(result, "row"));
        assertEquals(2, result.answered());
    }

    @Test void groupedUnicodeFieldIsFoundThroughARelativeCalculationReference() throws Exception {
        LoadedForm form = form("<group><ménages/><total/>" + row9("row") + "</group>",
                bind("group/ménages", "int", "required=\"true()\"")
                        + bind("group/total", "int", "calculate=\"../ménages + 1\"") + rowBind9("group/row"),
                "<group ref=\"/data/group\"><label>Group</label>" + input("group/ménages")
                        + repeat9("group/row", "/data/group/total") + "</group>");
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "group/ménages"));
        assertEquals(3, count(result, "group/row"));
    }

    @Test void oneQuestionMayControlMoreThanOneRepeat() throws Exception {
        LoadedForm form = form("<answer/>" + row9("first") + row9("second"),
                bind("answer", "int", "required=\"true()\"") + rowBind9("first") + rowBind9("second"),
                input("answer") + repeat9("first", "/data/answer") + repeat9("second", "/data/answer"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "answer"));
        assertEquals(2, count(result, "first"));
        assertEquals(2, count(result, "second"));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @ValueSource(strings = {"string", "decimal"})
    void intermediateCalculationsNeedNotHaveIntegerType(String type) throws Exception {
        LoadedForm form = form("<answer/><middle/><total/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"")
                        + bind("middle", type, "calculate=\"/data/answer\"")
                        + bind("total", "int", "calculate=\"number(/data/middle) + 1\"") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/total"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "answer"));
        assertEquals(3, count(result, "row"));
    }

    @Test void relevantCountQuestionRemainsACountDependencyWhenItsRepeatIsIrrelevant() throws Exception {
        LoadedForm form = form("<answer/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"")
                        + bind("row", "string", "relevant=\"false()\"") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/answer"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "answer"));
        assertEquals(0, count(result, "row"));
        assertEquals(1, result.answered());
    }

    @Test void referenceInRepeatRelevanceDoesNotMakeAnIntegerACountQuestion() throws Exception {
        LoadedForm form = form("<answer/><size>1</size>" + row9("row"),
                bind("answer", "int", "required=\"true()\"") + bind("size", "int", "")
                        + bind("row", "string", "relevant=\"/data/answer &gt; 0\"") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/size"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("11", value(result, "answer"));
        assertEquals(1, count(result, "row"));
    }

    @Test void ordinaryIntegerAndDownstreamCalculationDoNotBecomeCountDependencies() throws Exception {
        LoadedForm form = form("<answer/><ordinary/><unrelated/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"") + bind("ordinary", "int", "required=\"true()\"")
                        + bind("unrelated", "int", "calculate=\"/data/ordinary + /data/answer\"") + rowBind9("row"),
                input("answer") + input("ordinary") + repeat9("row", "/data/answer"));
        Submission result = run9(form, 2, 2, "");
        assertCount9(result, 2);
        assertEquals("11", value(result, "ordinary"));
        assertEquals("13", value(result, "unrelated"));
    }

    @Test void quotedPathsAndSimilarFieldNamesAreNotDependencies() throws Exception {
        LoadedForm form = form("<answer/><answer_extra/><decoy/><total/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"")
                        + bind("answer_extra", "int", "required=\"true()\"") + bind("decoy", "int", "required=\"true()\"")
                        + bind("total", "int", "calculate=\"/data/answer + string-length('/data/decoy') * 0\"")
                        + rowBind9("row"),
                input("answer") + input("answer_extra") + input("decoy") + repeat9("row", "/data/total"));
        Submission result = run9(form, 2, 2, "");
        assertCount9(result, 2);
        assertEquals("11", value(result, "answer_extra"));
        assertEquals("11", value(result, "decoy"));
    }

    @Test void sameLeafNameInAnotherGroupDoesNotBecomeACountDependency() throws Exception {
        LoadedForm form = form("<first><answer/></first><second><answer/></second>" + row9("row"),
                bind("first/answer", "int", "required=\"true()\"")
                        + bind("second/answer", "int", "required=\"true()\"") + rowBind9("row"),
                "<group ref=\"/data/first\"><label>First</label>" + input("first/answer") + "</group>"
                        + "<group ref=\"/data/second\"><label>Second</label>" + input("second/answer") + "</group>"
                        + repeat9("row", "/data/first/answer"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "first/answer"));
        assertEquals("11", value(result, "second/answer"));
        assertEquals(2, count(result, "row"));
    }

    @Test void secondaryInstanceReferenceDoesNotMarkASameNamedPrimaryQuestion() throws Exception {
        LoadedForm form = form("<answer/><total/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"")
                        + bind("total", "int", "calculate=\"instance('other')/lookup/answer\"") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/total"), "",
                "<instance id=\"other\"><lookup><answer>1</answer></lookup></instance>");
        Submission result = run9(form, 2, 2, "");
        assertEquals("11", value(result, "answer"));
        assertEquals(1, count(result, "row"));
    }

    @Test void dependencyTraversalStopsAtACalculatedFieldWithAControl() throws Exception {
        LoadedForm form = form("<answer/><controlled/>" + row9("row"),
                bind("answer", "int", "required=\"true()\"")
                        + bind("controlled", "int", "calculate=\"/data/answer * 0 + 2\" readonly=\"true()\"")
                        + rowBind9("row"),
                input("answer") + input("controlled") + repeat9("row", "/data/controlled"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("11", value(result, "answer"));
        assertEquals(2, count(result, "row"));
    }

    @Test void decimalCountDependencyKeepsItsDecimalRange() throws Exception {
        Submission result = run9(direct9("decimal", "", "required=\"true()\"", input("answer")), 2, 2,
                "\"decimalRange\":{\"min\":7,\"max\":7}");
        assertCount9(result, 7);
    }

    @Test void textNumbersDecimalCountDependencyKeepsItsAppearanceRange() throws Exception {
        Submission result = run9(direct9("string", "", "required=\"true()\"",
                "<input ref=\"/data/answer\" appearance=\"numbers_decimal\"><label>Count</label></input>"),
                2, 2, "\"decimalRange\":{\"min\":7,\"max\":7}");
        assertCount9(result, 7);
    }

    @Test void integerSelectCountStillUsesOfferedChoicesAndWeights() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\"",
                "<select1 ref=\"/data/answer\"><label>Count</label>"
                        + "<item><label>Seven</label><value>7</value></item>"
                        + "<item><label>Eight</label><value>8</value></item></select1>");
        assertCount9(run9(form, 2, 2, field9("\"weights\":{\"7\":1,\"8\":0}")), 7);
    }

    @Test void formWithoutCountedRepeatsLeavesOrdinaryIntegersAlone() throws Exception {
        Submission result = run9(single("int", "required=\"true()\""), 2, 2, "");
        assertEquals("11", value(result, "answer"));
    }
}
