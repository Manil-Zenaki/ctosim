package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoNumericSelectTestSupport.*;
import static ctosim.SurveyCtoTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoNumericSelectBindingTest {
    static Stream<Arguments> conditionSites() {
        return Stream.of("relevant", "required", "readonly").flatMap(site -> Stream.of(
                Arguments.of(site, "selected(/data/number, '1')", false),
                Arguments.of(site, "count-selected(/data/number) = 1", true),
                Arguments.of(site, "selected-at(/data/number, 0) = '1'", false)));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("conditionSites")
    void numericSelectCallsWorkInEveryBooleanBindAndUpdateAfterAnswers(
            String site, String expression, boolean afterTwo) throws Exception {
        var session = numericForm("", site + "=\"" + attribute(expression) + "\"", "").newEntryController();
        var target = child(root(session), "target");
        assertEquals(true, conditionState(site, target));
        answer(session, child(root(session), "number"), new IntegerData(2));
        assertEquals(afterTwo, conditionState(site, target));
        answer(session, child(root(session), "number"), null);
        assertEquals(false, conditionState(site, target));
        answer(session, child(root(session), "number"), new IntegerData(1));
        assertEquals(true, conditionState(site, target));
    }

    private static boolean conditionState(String site, org.javarosa.core.model.instance.TreeElement node) {
        return switch (site) {
            case "relevant" -> node.isRelevant();
            case "required" -> node.isRequired();
            case "readonly" -> !node.isEnabled();
            default -> throw new AssertionError("Unexpected bind " + site);
        };
    }

    @ParameterizedTest @ValueSource(strings = {
            "selected(., '1')", "count-selected(.) = 1 and . = 1", "selected-at(., 0) = '1'"})
    void constraintsCoerceTheNumericCandidateInTheBoundNodesContext(String expression) throws Exception {
        var session = numericForm("", "constraint=\"" + attribute(expression) + "\"", "").newEntryController();
        var target = child(root(session), "target");
        var index = questionIndex(session, target);
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, session.answerQuestion(index, new IntegerData(2), true));
        assertEquals(ANSWER_OK, session.answerQuestion(index, new IntegerData(1), true));
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, session.answerQuestion(index, new IntegerData(2), true));
    }

    @ParameterizedTest @ValueSource(strings = {
            "selected(/data/number, '1')", "count-selected(/data/number) = 1", "selected-at(/data/number, 0) = '1'"})
    void constraintsReadTheCurrentNumericValueOfReferencedFields(String expression) throws Exception {
        var session = numericForm("", "constraint=\"" + attribute(expression) + "\"", "").newEntryController();
        var target = child(root(session), "target");
        var form = session.getModel().getForm();
        assertTrue(form.evaluateConstraint(target.getRef(), new IntegerData(7)));
        answer(session, child(root(session), "number"), null);
        assertFalse(form.evaluateConstraint(target.getRef(), new IntegerData(7)));
    }

    @ParameterizedTest(name = "setvalue: {0} -> {1}")
    @MethodSource({"ctosim.SurveyCtoNumericSelectCalculationTest#numericExpressions",
            "ctosim.SurveyCtoNumericSelectCalculationTest#stringExpressions"})
    void instanceFirstLoadSetvalueUsesNumericStringCoercion(String expression, String expected) throws Exception {
        // These cases refer to /data/arm, which is calculated during initialization.
        // Replace only that reference with a numeric literal to avoid specifying
        // an order between calculates and first-load actions.
        String actionExpression = expression.replace("/data/arm", "1");
        String action = "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" value=\""
                + attribute(bracket(actionExpression)) + "\"/>";
        var loaded = numericForm("", "", action);
        for (int i = 0; i < 2; i++) {
            var session = loaded.newEntryController();
            assertEquals("[" + expected + "]", value(child(root(session), "result")));
        }
    }

    static Stream<Arguments> rawResults() {
        return Stream.of(Arguments.of("selected(1, '1')", Boolean.TRUE),
                Arguments.of("count-selected(2.5)", Double.valueOf(1)),
                Arguments.of("selected-at(2.5, 0)", "2.5"));
    }

    @ParameterizedTest @MethodSource("rawResults")
    void allThreeFunctionsPreserveTheirXpathResultTypesInStoredExpressions(String expression, Object expected) throws Exception {
        // A constraint exposes its stored expression through JavaRosa's public API.
        // evalRaw observes function types before calculate/answer storage conversion.
        var session = numericForm("", "constraint=\"" + attribute(expression) + "\"", "").newEntryController();
        var form = session.getModel().getForm();
        var target = child(root(session), "target");
        var context = new org.javarosa.core.model.condition.EvaluationContext(form.getEvaluationContext(), target.getRef());
        assertValue(expected, target.getConstraint().constraint.evalRaw(form.getMainInstance(), context));
    }
}
