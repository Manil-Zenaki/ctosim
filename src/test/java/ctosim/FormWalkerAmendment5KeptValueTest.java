package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment5KeptValueTest {
    @ParameterizedTest @ValueSource(ints = {0, 10})
    void validDefaultsAtInclusiveBoundsAreKeptWithoutAnsweringOrTime(int initial) throws Exception {
        LoadedForm form = amended5("<answer>" + initial + "</answer>",
                bind("answer", "int", constraint(". >= 0 and . <= 10")), input("answer"));
        Submission result = run(form, "");
        assertEquals(Integer.toString(initial), value(result, "answer"));
        assertEquals(0, result.answered());
        assertEquals(0, seconds(result));
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 11})
    void invalidDefaultsAreAnsweredNormallyWithoutAFieldOverride(int initial) throws Exception {
        LoadedForm form = amended5("<answer>" + initial + "</answer>",
                bind("answer", "int", constraint(". >= 0 and . <= 10")), input("answer"));
        Submission result = run(form, "\"integerRange\":{\"min\":5,\"max\":5}");
        assertEquals("5", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void invalidOptionalDefaultIsClearedWhenTheGeneratorReturnsBlank() throws Exception {
        LoadedForm form = amended5("<answer>bad</answer>",
                bind("answer", "string", constraint(". = 'good'")), input("answer"));
        Submission result = new FormWalker(form, config("\"missingRate\":1")).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void impossibleDefaultIsClearedWithOrdinaryConstraintAndRequiredWarnings(boolean required) throws Exception {
        LoadedForm form = amended5("<answer>bad</answer>", bind("answer", "string",
                constraint("false()") + " required=\"" + required + "()\""), input("answer"));
        Submission result = run(form, "");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        assertWarning(result, "answer", "constraint");
        assertEquals(required ? 2 : 1, result.warnings().size());
        if (required) assertWarning(result, "answer", "required");
    }

    @ParameterizedTest @ValueSource(ints = {0, 99})
    void anActionPopulatedQuestionIsKeptOnlyWhenItsConstraintHolds(int actionValue) throws Exception {
        LoadedForm form = amended5("<answer/>", bind("answer", "int", constraint(". <= 10")), input("answer"),
                "<setvalue event=\"odk-instance-first-load\" ref=\"/data/answer\" value=\"" + actionValue + "\"/>");
        Submission result = run(form, "\"integerRange\":{\"min\":5,\"max\":5}");
        assertEquals(actionValue == 0 ? "0" : "5", value(result, "answer"));
        assertEquals(actionValue == 0 ? 0 : 1, result.answered());
        assertElapsed(result, actionValue == 0 ? 0 : 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void editableCalculationDefaultIsCheckedWhenReached() throws Exception {
        LoadedForm form = amended5("<source>99</source><answer/>", bind("source", "int", "")
                + bind("answer", "int", "calculate=\"once(/data/source)\" readonly=\"false()\" "
                + constraint(". <= 10")), input("answer"));
        Submission result = run(form, "\"integerRange\":{\"min\":5,\"max\":5}");
        assertEquals("5", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void validDefaultDoesNotSatisfyAConstraintUsingTheWrongQuestionContext() throws Exception {
        LoadedForm form = amended5("<group><limit>2</limit><answer>3</answer></group><limit>100</limit>",
                bind("group/limit", "int", "") + bind("limit", "int", "")
                + bind("group/answer", "int", constraint(". <= ../limit")),
                "<group ref=\"/data/group\">" + input("group/answer") + "</group>");
        Submission result = run(form, "\"integerRange\":{\"min\":1,\"max\":1}");
        assertEquals("1", value(result, "group/answer"));
        assertEquals(1, result.answered());
    }
}
