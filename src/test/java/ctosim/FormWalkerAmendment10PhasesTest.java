package ctosim;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment10Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment10PhasesTest {
    @ParameterizedTest @ValueSource(strings = {"", "kept"})
    void aConstraintThatStartsThrowingOnlyAtFinalCheckIsClearedAndRecorded(String initial) throws Exception {
        LoadedForm form = errorForm("<answer>" + initial + "</answer><gate>safe</gate><after/>",
                bind("answer", "string", attr("constraint", "if(/data/gate = 'safe', true(), " + BAD_CONSTRAINT + ")"))
                        + bind("gate", "string", "") + bind("after", "string", ""),
                input("answer") + input("gate") + input("after"));
        Submission result = new FormWalker(form, config10("\"fields\":{\"gate\":{\"value\":\"go\"},"
                + "\"after\":{\"value\":\"continued\"}}" )).walk(39);
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertEquals("go", value(result, "gate"));
        assertEquals("continued", value(result, "after"));
        assertEquals(initial.isEmpty() ? 3 : 2, result.answered(), "Final checks do not change answer accounting");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
        assertNotCutShort(result);
    }

    @Test void finalCheckContinuesToRepairOtherQuestionsAfterAnExpressionError() throws Exception {
        LoadedForm form = errorForm("<broken>kept</broken><repair>old</repair><gate>safe</gate>",
                bind("broken", "string", attr("constraint", "if(/data/gate = 'safe', true(), " + BAD_CONSTRAINT + ")"))
                        + bind("repair", "string", attr("constraint", "/data/gate = 'safe' or . = 'repaired'"))
                        + bind("gate", "string", ""), input("broken") + input("repair") + input("gate"));
        Submission result = new FormWalker(form, config10("\"fields\":{\"gate\":{\"value\":\"go\"}}" )).walk(3);
        onlyError(result, "broken");
        assertEquals("", value(result, "broken"));
        assertEquals("repaired", value(result, "repair"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void navigationErrorsPrecedeFinalCheckErrorsInTheRecordList() throws Exception {
        LoadedForm form = errorForm("<late>kept</late><early/><gate>safe</gate>",
                bind("late", "string", attr("constraint", "if(/data/gate = 'safe', true(), " + BAD_CONSTRAINT + ")"))
                        + bind("early", "string", attr("constraint", BAD_CONSTRAINT)) + bind("gate", "string", ""),
                input("late") + input("early") + input("gate"));
        Submission result = new FormWalker(form, config10("\"fields\":{\"gate\":{\"value\":\"go\"}}" )).walk(12);
        assertEquals(List.of("early", "late"), fields(result));
        assertDiagnostics(result);
    }

    @ParameterizedTest @ValueSource(strings = {"calculate", "relevant", "required", "readonly"})
    void dependentBindExpressionErrorsNameTheFailingFieldAndClearTheAnswerBeingHandled(String attribute) throws Exception {
        String expression = "if(/data/trigger = 'go', " + REPEATED + ", 'safe')";
        LoadedForm form = errorForm("<trigger/><dependent/><after/>",
                bind("trigger", "string", "") + bind("dependent", "string", attr(attribute, expression))
                        + bind("after", "string", ""), input("trigger") + input("dependent") + input("after"));
        RuntimeException engineError = engineAnswerFailure(form, "trigger", "go");
        assertTrue(engineError.getMessage().contains("dependent"), "Fixture must name its failing bind");
        Submission result = new FormWalker(form, config10("\"fields\":{\"trigger\":{\"value\":\"go\"},"
                + "\"after\":{\"value\":\"continued\"}}" )).walk(6);
        FormError error = onlyError(result, "dependent");
        assertEquals(singleLine(engineError.getMessage()), error.message());
        assertTrue(error.path().contains("/dependent"));
        assertEquals("", value(result, "trigger"), "The handled question is cleared even when another expression failed");
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }

    @Test void failingValueChangedSetvalueIsRecordedAndTheNextQuestionIsAnswered() throws Exception {
        LoadedForm form = errorForm("<trigger/><target/><after/>",
                bind("trigger", "string", "") + bind("target", "string", "") + bind("after", "string", ""),
                "<input ref=\"/data/trigger\"><label>Trigger</label>"
                        + "<setvalue event=\"xforms-value-changed\" ref=\"/data/target\" "
                        + attr("value", "if(/data/trigger = 'go', " + REPEATED + ", '')") + "/></input>" + input("after"));
        RuntimeException engineError = engineAnswerFailure(form, "trigger", "go");
        Submission result = new FormWalker(form, config10("\"fields\":{\"trigger\":{\"value\":\"go\"},"
                + "\"after\":{\"value\":\"continued\"}}" )).walk(6);
        // This engine diagnostic identifies no failed target expression: use the handled question.
        assertEquals(singleLine(engineError.getMessage()), onlyError(result, "trigger").message());
        assertEquals("", value(result, "trigger"));
        assertEquals("continued", value(result, "after"));
    }

    @Test void choiceFilterEvaluationErrorsAreRecordedAndNavigationContinues() throws Exception {
        // Choices have a different context from source, so its phone reference remains multi-node.
        LoadedForm form = errorForm("<answer/><after/><options><item><code>11</code></item></options>",
                bind("answer", "string", "") + bind("after", "string", ""),
                "<select1 ref=\"/data/answer\"><label>Answer</label><itemset "
                        + attr("nodeset", "/data/options/item[code = " + REPEATED + "]")
                        + "><label ref=\"code\"/><value ref=\"code\"/></itemset></select1>" + input("after"));
        Submission result = walk10(form);
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
    }

    @Test void recoveryStillPostprocessesTimestampsDurationAndSpeedViolationFields() throws Exception {
        LoadedForm form = errorForm("<before/><answer/><after/><elapsed/><violations/><violated/>" + METADATA,
                bind("before", "string", "ct:minimum-seconds=\"61\"")
                        + bind("answer", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("after", "string", "") + bind("elapsed", "int", "calculate=\"duration()\"")
                        + bind("violations", "int", "ct:speed-violations=\"count\"")
                        + bind("violated", "string", "ct:speed-violations=\"list\"") + METADATA_BINDS,
                input("before") + input("answer") + input("after"));
        Submission result = new FormWalker(form, config10("\"speedViolationRate\":1,\"timeZone\":\"+03:00\"" )).walk(15);
        onlyError(result, "answer");
        assertEquals("1", value(result, "violations"));
        assertEquals("before", value(result, "violated"));
        assertEquals(seconds(result), Long.parseLong(value(result, "elapsed")));
        assertEquals(result.end(), OffsetDateTime.parse(value(result, "finished")).toLocalDateTime());
        assertEquals(result.start(), OffsetDateTime.parse(value(result, "started")).toLocalDateTime());
        long minutes = Duration.between(result.end(), result.submitted()).toMinutes();
        assertTrue(minutes >= 1 && minutes <= 240);
        assertFalse(value(result, "meta/instanceID").isEmpty());
    }
}
