package ctosim;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.instance.TreeElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoAmendment5Support.*;
import static ctosim.SurveyCtoTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment5BindingTest {
    @ParameterizedTest @ValueSource(strings = {"relevant", "required", "readonly"})
    void booleanBindsFollowTheNumericSecondArgumentAndItsClearedValue(String site) throws Exception {
        var entry = form("", site + "=\"selected(/data/many, /data/number)\"", "", "")
                .newEntryController();
        var target = child(root(entry), "target");
        assertTrue(state(site, target));
        answer(entry, child(root(entry), "number"), new IntegerData(2));
        assertFalse(state(site, target));
        answer(entry, child(root(entry), "number"), new IntegerData(4));
        assertTrue(state(site, target));
        answer(entry, child(root(entry), "number"), null);
        assertFalse(state(site, target));
    }

    private static boolean state(String site, TreeElement node) {
        return switch (site) {
            case "relevant" -> node.isRelevant();
            case "required" -> node.isRequired();
            case "readonly" -> !node.isEnabled();
            default -> throw new AssertionError(site);
        };
    }

    @ParameterizedTest @ValueSource(strings = {"selected('1 3 4', .)", "selected('1 3 4', . + 0)"})
    void constraintConvertsTheProposedNumericAnswerInItsOwnNodeContext(String expression) throws Exception {
        var entry = form("", "constraint=\"" + attribute(expression) + "\"", "", "")
                .newEntryController();
        var target = child(root(entry), "target");
        var index = questionIndex(entry, target);
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, entry.answerQuestion(index, new IntegerData(2), true));
        assertEquals(ANSWER_OK, entry.answerQuestion(index, new IntegerData(3), true));
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, entry.answerQuestion(index, new IntegerData(2), true));
        assertEquals(ANSWER_OK, entry.answerQuestion(index, new IntegerData(4), true));
    }

    @Test void constraintReadsTheCurrentReferencedSecondArgument() throws Exception {
        var entry = form("", "constraint=\"selected(/data/many, /data/number)\"", "", "")
                .newEntryController();
        var form = entry.getModel().getForm();
        var target = child(root(entry), "target");
        assertTrue(form.evaluateConstraint(target.getRef(), new IntegerData(7)));
        answer(entry, child(root(entry), "number"), new IntegerData(2));
        assertFalse(form.evaluateConstraint(target.getRef(), new IntegerData(7)));
        answer(entry, child(root(entry), "number"), null);
        assertFalse(form.evaluateConstraint(target.getRef(), new IntegerData(7)));
    }

    @ParameterizedTest @CsvSource({"3,true", "2,false"})
    void storedSelectedExpressionStillReturnsAnXpathBoolean(int number, boolean expected) throws Exception {
        var entry = form("", "constraint=\"selected('1 3 4', " + number + ")\"", "", "")
                .newEntryController();
        var form = entry.getModel().getForm();
        var target = child(root(entry), "target");
        var context = new EvaluationContext(form.getEvaluationContext(), target.getRef());
        assertValue(Boolean.valueOf(expected), target.getConstraint().constraint.evalRaw(form.getMainInstance(), context));
    }

    @ParameterizedTest(name = "setvalue {0} = {1}")
    @MethodSource("ctosim.SurveyCtoAmendment5CalculationTest#selections")
    void firstLoadActionsApplyTheSameSecondArgumentConversion(String expression, boolean expected) throws Exception {
        var loaded = form("", "", setvalue("odk-instance-first-load", expression), "");
        for (int i = 0; i < 2; i++) {
            var entry = loaded.newEntryController();
            assertEquals("[" + expected + "]", value(child(root(entry), "result")));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "simulated", "properties"})
    void bodyValueChangedActionUsesTheUpdatedNumericSecondArgument(String kind) throws Exception {
        var loaded = form("", "", "",
                setvalue("xforms-value-changed", "selected(/data/many, /data/number)"));
        var entry = session(loaded, kind);
        answer(entry, child(root(entry), "number"), new IntegerData(2));
        assertEquals("[false]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "number"), new IntegerData(4));
        assertEquals("[true]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "number"), null);
        assertEquals("[false]", value(child(root(entry), "result")));
    }
}
