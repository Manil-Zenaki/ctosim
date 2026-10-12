package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.amended5;
import static ctosim.FormWalkerAmendment7Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment7DiagnosticsTest {
    @ParameterizedTest @ValueSource(strings = {"${unknown_field} = 1", "unknown_amendment_seven()"})
    void unevaluableConditionWarnsAndActsAsFalseThenContinuesToTheNextRule(String expression) throws Exception {
        Submission result = answer7(single("string", ""), "\"value\":\"base\",\"when\":["
                + rule7(expression, "\"value\":\"bad\"") + "," + rule7("true()", "\"value\":\"next\"") + "]");
        assertEquals("next", value(result, "answer"));
        assertExpressionWarning(result, "answer", expression);
    }

    @Test void unevaluableConditionWithNoMatchUsesTheBaseOverride() throws Exception {
        String expression = "${missing_gate} = 1";
        Submission result = answer7(single("string", ""), "\"value\":\"base\",\"when\":["
                + rule7(expression, "\"value\":\"bad\"") + "]");
        assertEquals("base", value(result, "answer"));
        assertExpressionWarning(result, "answer", expression);
    }

    @ParameterizedTest @ValueSource(strings = {"${unknown_field} + 1", "unknown_amendment_seven()"})
    void failedExpressionIsAbsentAndStillReplacesTheInstanceDefaultThroughOrdinaryGeneration(String expression) throws Exception {
        SimulationConfig cfg = settings7("\"integerRange\":{\"min\":17,\"max\":17},"
                + field7("\"expr\":" + quote(expression)));
        Submission result = new FormWalker(default7("int", "999", ""), cfg).walk(7);
        assertEquals("17", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertExpressionWarning(result, "answer", expression);
    }

    @Test void failedRuleExpressionKeepsTheParentValueAndDoesNotTryAnotherRule() throws Exception {
        String expression = "unknown_amendment_seven()";
        Submission result = answer7(single("string", ""), "\"value\":\"parent\",\"when\":["
                + rule7("true()", "\"expr\":" + quote(expression)) + ","
                + rule7("true()", "\"value\":\"later\"") + "]");
        assertEquals("parent", value(result, "answer"));
        assertExpressionWarning(result, "answer", expression);
    }

    @Test void failedExpressionRetainsItsMissingRate() throws Exception {
        String expression = "unknown_amendment_seven()";
        Submission result = answer7(default7("string", "old", ""), "\"expr\":" + quote(expression) + ",\"missingRate\":1");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertExpressionWarning(result, "answer", expression);
    }

    @Test void expressionsInFalseRulesAndOverriddenParentExpressionsAreNotEvaluated() throws Exception {
        Submission result = answer7(single("string", ""), "\"expr\":\"unknown_parent()\",\"when\":["
                + rule7("false()", "\"expr\":\"unknown_false_branch()\"") + ","
                + rule7("true()", "\"expr\":\"'selected'\"") + "]");
        assertEquals("selected", value(result, "answer"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void configurationReportsUnknownNamesFromAllRuleBranchesAndBaseExpressionsOnceInNaturalOrder() throws Exception {
        SimulationConfig cfg = settings7("\"fields\":{\"answer\":{\"expr\":\"${z_missing} + ${a_missing}\",\"when\":["
                + rule7("${B_missing} = 1", "\"expr\":\"${a_missing}\"") + ","
                + rule7("false()", "\"expr\":\"${inactive_missing}\"") + "]},"
                + "\"a_missing\":{\"value\":1}}");
        FormWalker walker = new FormWalker(single("string", ""), cfg);
        List<String> warnings = walker.configWarnings();
        List<String> names = List.of("B_missing", "a_missing", "inactive_missing", "z_missing");
        assertEquals(names.size(), warnings.size(), warnings.toString());
        for (int i = 0; i < names.size(); i++) assertTrue(warnings.get(i).contains(names.get(i)), warnings.toString());
        assertThrows(UnsupportedOperationException.class, () -> warnings.add("changed"));
        assertEquals(warnings, walker.configWarnings());
    }

    @Test void knownReferencesIncludeNestedFieldsCalculationsAndRepeatTemplateNodes() throws Exception {
        SimulationConfig cfg = settings7(field7("\"expr\":\"${age} + ${annual} + ${index}\",\"when\":["
                + rule7("boolean(${row}) or boolean(${household})", "\"expr\":\"${income}\"") + "]"));
        assertTrue(new FormWalker(fixture7("context.xml"), cfg).configWarnings().isEmpty());
    }

    @Test void profileNamesAreNotTreatedAsFieldNamesByConfigurationWarnings() throws Exception {
        SimulationConfig cfg = settings7("\"profiles\":{\"not_a_field\":1},"
                + field7("\"expr\":\"profile()\",\"when\":[" + rule7("profile() = 'not_a_field'", "\"missingRate\":0") + "]"));
        assertTrue(new FormWalker(single("string", ""), cfg).configWarnings().isEmpty());
    }

    @Test void irrelevantReadonlyAndCalculateHereQuestionsDoNotEvaluateTheirConfiguredExpressions() throws Exception {
        LoadedForm form = amended5("<hidden/><readonly>kept</readonly><here/>",
                bind("hidden", "string", "relevant=\"false()\"")
                + bind("readonly", "string", "readonly=\"true()\"")
                + bind("here", "string", "ct:calculate-here=\"'calculated'\""),
                input("hidden") + input("readonly") + input("here"));
        SimulationConfig cfg = settings7("\"fields\":{\"hidden\":{\"expr\":\"unknown_hidden()\"},"
                + "\"readonly\":{\"expr\":\"unknown_readonly()\"},\"here\":{\"expr\":\"unknown_here()\"}}");
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals(0, count(result, "hidden"));
        assertEquals("kept", value(result, "readonly"));
        assertEquals("calculated", value(result, "here"));
        assertEquals(0, result.answered());
        assertEquals(0, seconds(result));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void expressionWarningsFollowBodyOrderAndArePrefixedWithTheSubmissionNumber() throws Exception {
        LoadedForm form = form("<first/><second/>", bind("first", "string", "") + bind("second", "string", ""),
                input("second") + input("first"));
        SimulationConfig cfg = settings7("\"fields\":{\"first\":{\"expr\":\"unknown_first()\"},"
                + "\"second\":{\"expr\":\"unknown_second()\"}}");
        Submission result = new FormWalker(form, cfg).walk(13);
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("second"));
        assertTrue(result.warnings().get(1).contains("first"));
        result.warnings().forEach(w -> assertTrue(w.startsWith("submission 13:"), w));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("changed"));
    }

    private static void assertExpressionWarning(Submission result, String field, String expression) {
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains(field) && w.contains(expression)),
                () -> "Warning must identify field and original expression: " + result.warnings());
        result.warnings().forEach(w -> assertTrue(w.startsWith("submission " + result.number() + ":"), w));
    }
}
