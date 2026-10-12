package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.amended5;
import static ctosim.FormWalkerAmendment5Support.constraint;
import static ctosim.FormWalkerAmendment7Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment7ExpressionTest {
    static Stream<Arguments> expressions() {
        return Stream.of(Arguments.of("string", "concat('Élodie', ' 東京 & <ok>')", "Élodie 東京 & <ok>"),
                Arguments.of("int", "6 * 7", "42"), Arguments.of("decimal", "5 div 4", "1.25"),
                Arguments.of("string", "true()", "true"), Arguments.of("string", "false()", "false"),
                Arguments.of("string", "0", "0"), Arguments.of("date", "'2024-02-29'", "2024-02-29"),
                Arguments.of("string", "'  spaced  '", "  spaced  "));
    }

    @ParameterizedTest @MethodSource("expressions")
    void expressionStringValueIsConvertedAsAFixedAnswer(String type, String expression, String expected) throws Exception {
        Submission result = answer7(single(type, ""), "\"expr\":" + quote(expression));
        assertEquals(expected, value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void expressionCanReferToItsOwnExistingValueUsingTheQuestionAsContext() throws Exception {
        Submission result = answer7(default7("int", "8", ""), "\"expr\":\". + 1\"");
        assertEquals("9", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @Test void conditionCanReferToItsOwnExistingValueUsingTheQuestionAsContext() throws Exception {
        Submission result = answer7(default7("int", "8", ""), "\"value\":0,\"when\":["
                + rule7(". = 8", "\"value\":9") + "]");
        assertEquals("9", value(result, "answer"));
    }

    @Test void namedReferencesFindGroupedFieldsAndSeeEarlierAnswersAndDependentCalculations() throws Exception {
        SimulationConfig cfg = settings7("\"fields\":{"
                + "\"age\":{\"value\":20},"
                + "\"income\":{\"expr\":\"${age} * 100\"},"
                + "\"category\":{\"value\":\"wrong\",\"when\":["
                + rule7("${annual} = 24000 and ${age} = 20", "\"expr\":\"concat('adult-', ${income})\"") + "]},"
                + "\"answer\":{\"value\":1},\"branch\":{\"value\":\"unused\"}}");
        Submission result = new FormWalker(fixture7("context.xml"), cfg).walk(1);
        assertEquals("2000", value(result, "household/income"));
        assertEquals("24000", value(result, "household/annual"));
        assertEquals("adult-2000", value(result, "household/category"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void repeatInstancesEvaluateRulesAndExpressionsInTheirOwnQuestionContext(boolean namedReferences) throws Exception {
        String index = namedReferences ? "${index}" : "../index";
        String answer = namedReferences ? "${answer}" : "../answer";
        SimulationConfig cfg = settings7("\"fields\":{\"age\":{\"value\":20},\"income\":{\"value\":2},"
                + "\"category\":{\"value\":\"unused\"},\"answer\":{\"expr\":" + quote(index + " * 10") + "},"
                + "\"branch\":{\"value\":\"later\",\"when\":["
                + rule7(index + " = 1 and " + answer + " = 10", "\"value\":\"first\"") + ","
                + rule7(index + " = 2 and " + answer + " = 20", "\"value\":\"second\"") + "]}}");
        Submission result = new FormWalker(fixture7("context.xml"), cfg).walk(1);
        assertEquals(3, count(result, "row"));
        for (int i = 1; i <= 3; i++) assertEquals(Integer.toString(i * 10), value(result, "row[" + i + "]/answer"));
        assertEquals("first", value(result, "row[1]/branch"));
        assertEquals("second", value(result, "row[2]/branch"));
        assertEquals("later", value(result, "row[3]/branch"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void expressionUsesTheCurrentSimulatedDateAfterTheClockAdvances() throws Exception {
        LoadedForm form = amended5("<first/><answer/>",
                bind("first", "string", "ct:minimum-seconds=\"172800\"") + bind("answer", "date", ""),
                input("first") + input("answer"));
        SimulationConfig cfg = settings7("\"start\":\"2026-01-05T08:00:00\",\"fieldworkDays\":1,"
                + "\"fields\":{\"first\":{\"value\":\"go\"},"
                + "\"answer\":{\"expr\":\"format-date-time(today(), '%Y-%m-%d')\"}}");
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals(result.start().toLocalDate().plusDays(2).toString(), value(result, "answer"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emptyExpressionClearsAnExistingValueAndDoesNotCountAsAnAnswer(boolean required) throws Exception {
        Submission result = answer7(default7("string", "old", "required=\"" + required + "()\""), "\"expr\":\"''\"");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        // The spec does not say whether an intentional expression blank emits a required warning.
    }

    @Test void ruleExpressionOverridesTheRetainedParentFixedValueIncludingWhenItIsEmpty() throws Exception {
        Submission result = answer7(default7("string", "old", ""), "\"value\":\"parent\",\"when\":["
                + rule7("true()", "\"expr\":\"''\"") + "]");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
    }

    @Test void nonemptyRuleExpressionBecomesTheEffectiveFixedValue() throws Exception {
        Submission result = answer7(single("int", ""), "\"min\":1,\"max\":1,\"when\":["
                + rule7("true()", "\"expr\":\"40 + 2\"") + "]");
        assertEquals("42", value(result, "answer"));
    }

    @Test void expressionAnswersStillRespectEffectiveMissingness() throws Exception {
        Submission result = answer7(single("string", ""), "\"expr\":\"'present'\",\"missingRate\":1");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @ParameterizedTest @ValueSource(strings = {"select1", "select"})
    void expressionSelectAnswersAreConvertedAgainstOfferedChoices(String control) throws Exception {
        String expected = control.equals("select") ? "20 10" : "10";
        Submission result = answer7(select(control, "string", ""), "\"expr\":" + quote("'" + expected + "'"));
        assertEquals(expected, value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unconvertibleExpressionClearsTheDefaultAndNamesTheValue(boolean required) throws Exception {
        Submission result = answer7(default7("int", "8", "required=\"" + required + "()\""), "\"expr\":\"'not-an-integer'\"");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "not-an-integer");
        if (required) assertWarning(result, "answer", "required");
        assertElapsed(result, 1);
    }

    @Test void expressionCannotInventAnUnofferedSelectValue() throws Exception {
        Submission result = answer7(select("select1", "string", ""), "\"expr\":\"'absent-choice'\"");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "absent-choice");
    }

    @Test void generatedExpressionValueStillHasToSatisfyTheQuestionConstraint() throws Exception {
        Submission result = answer7(single("int", constraint("false()")), "\"expr\":\"7\"");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
        assertElapsed(result, 1);
    }

    @Test void ruleRangeFeedsConstraintRetriesInsteadOfTheUnmatchedParentRange() throws Exception {
        FormWalker walker = new FormWalker(single("int", constraint(". mod 2 = 0")), settings7(field7(
                "\"min\":1,\"max\":1,\"when\":[" + rule7("true()", "\"min\":2,\"max\":3") + "]")));
        for (int n = 1; n <= 30; n++) {
            Submission result = walker.walk(n);
            assertEquals("2", value(result, "answer"));
            assertEquals(1, result.answered());
            assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        }
    }

    @Test void ruleAnswersUpdateRelevanceBeforeTheFollowingQuestionIsReached() throws Exception {
        LoadedForm form = form("<answer/><followup/>", bind("answer", "int", "")
                + bind("followup", "string", "relevant=\"/data/answer = 1\""), input("answer") + input("followup"));
        SimulationConfig cfg = settings7("\"fields\":{\"answer\":{\"value\":0,\"when\":["
                + rule7("true()", "\"value\":1") + "]},\"followup\":{\"expr\":\"'visible'\"}}");
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals("visible", value(result, "followup"));
        assertEquals(2, result.answered());
    }
}
