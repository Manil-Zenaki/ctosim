package ctosim;

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
class FormWalkerAmendment10RecoveryTest {
    @Test void repeatedReferenceConstraintLeavesBlankRecordsEngineMessageAndContinues() throws Exception {
        Submission result = walk10(failingQuestion("", ""));
        FormError error = onlyError(result, "answer");
        assertTrue(error.message().contains("This field is repeated"), error.message());
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
        assertEquals(1, result.answered());
        assertNotCutShort(result);
    }

    @Test void unnamedConstraintDiagnosticFallsBackToTheHandledQuestionAndPreservesMessageText() throws Exception {
        LoadedForm form = failingQuestion("", "");
        RuntimeException engineError = engineAnswerFailure(form, "answer", "candidate");
        assertTrue(engineError.getMessage().contains("This field is repeated"));
        Submission result = walk10(form);
        assertEquals(singleLine(engineError.getMessage()), onlyError(result, "answer").message());
    }

    @Test void theAmendmentsUnwrappedRepeatedReferenceExampleIsAlsoRecovered() throws Exception {
        Submission result = walk10(errorForm("<answer/><after/>",
                bind("answer", "string", attr("constraint", ". != /data/source/phone"))
                        + bind("after", "string", ""), input("answer") + input("after")));
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
    }

    @Test void anInvalidRegexRaisedByAFormExpressionIsRecoveredToo() throws Exception {
        LoadedForm form = form("<answer/><after/>",
                bind("answer", "string", attr("constraint", "regex(., '[')")) + bind("after", "string", ""),
                input("answer") + input("after"));
        RuntimeException engineError = engineAnswerFailure(form, "answer", "candidate");
        Submission result = walk10(form);
        assertEquals(singleLine(engineError.getMessage()), onlyError(result, "answer").message());
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "old value"})
    void failedAnswerClearsAnyOverriddenDefault(String initial) throws Exception {
        Submission result = walk10(failingQuestion(initial, ""));
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
    }

    @Test void checkingAnExistingDefaultCanFailWithoutStoppingTheWalk() throws Exception {
        Submission result = new FormWalker(failingQuestion("existing", ""), config10("")).walk(8);
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertFalse(value(result, "after").isEmpty());
    }

    @Test void optionalAndRequiredExpressionErrorsAreNotUnsatisfiedConstraints() throws Exception {
        for (boolean required : List.of(false, true)) {
            Submission result = walk10(failingQuestion("", "required=\"" + required + "()\""));
            onlyError(result, "answer");
            assertTrue(result.unsatisfiedConstraints().isEmpty(), "An exception is not an evaluated false constraint");
        }
    }

    @Test void severalIndependentErrorsAreAllReportedInBodyOrder() throws Exception {
        Submission result = walk10(multipleFailures());
        assertEquals(List.of("second", "first"), fields(result));
        assertDiagnostics(result);
        assertEquals("", value(result, "first"));
        assertEquals("", value(result, "second"));
        assertFalse(value(result, "between").isEmpty());
        assertEquals("continued", value(result, "after"));
        assertEquals(2, result.answered());
        assertNotCutShort(result);
    }

    @Test void anErrorAtTheLastQuestionStillReturnsAFinalizedSubmission() throws Exception {
        LoadedForm form = errorForm("<answer/>" + METADATA, bind("answer", "string",
                attr("constraint", BAD_CONSTRAINT)) + METADATA_BINDS, input("answer"));
        Submission result = walk10(form);
        onlyError(result, "answer");
        assertEquals("", value(result, "answer"));
        assertFalse(value(result, "finished").isEmpty());
        assertFalse(result.end().isBefore(result.start()));
        assertTrue(result.submitted().isAfter(result.end()));
        assertNotCutShort(result);
    }

    @Test void errorsAtDifferentRepeatInstancesDoNotLookLikeAStuckQuestion() throws Exception {
        LoadedForm form = errorForm("<row jr:template=\"\"><answer/><next/></row><after/>",
                bind("row/answer", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("row/next", "string", "") + bind("after", "string", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/two\">" + input("row/answer")
                        + input("row/next") + "</repeat>" + input("after"));
        Submission result = walk10(form);
        assertEquals(List.of("answer", "answer"), fields(result));
        assertDiagnostics(result);
        for (int i = 1; i <= 2; i++) {
            assertIndex(result.formErrors().get(i - 1).path(), "row", i);
            assertEquals("", value(result, "row[" + i + "]/answer"));
            assertFalse(value(result, "row[" + i + "]/next").isEmpty());
        }
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }

    @Test void nestedRepeatPathsIdentifyEveryInstanceWithAnError() throws Exception {
        LoadedForm form = errorForm("<outer jr:template=\"\"><inner jr:template=\"\"><answer/></inner></outer><after/>",
                bind("outer/inner/answer", "string", attr("constraint", BAD_CONSTRAINT)) + bind("after", "string", ""),
                "<repeat nodeset=\"/data/outer\" jr:count=\"/data/two\">"
                        + "<repeat nodeset=\"/data/outer/inner\" jr:count=\"/data/two\">"
                        + input("outer/inner/answer") + "</repeat></repeat>" + input("after"));
        Submission result = walk10(form);
        assertEquals(4, result.formErrors().size());
        assertDiagnostics(result);
        for (int parent = 1; parent <= 2; parent++) for (int child = 1; child <= 2; child++) {
            FormError error = result.formErrors().get((parent - 1) * 2 + child - 1);
            assertIndex(error.path(), "outer", parent);
            assertIndex(error.path(), "inner", child);
        }
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }

    @Test void sameLeafNameInTwoGroupsDoesNotTriggerEarlyTermination() throws Exception {
        Submission result = walk10(errorForm("<left><réponse/></left><right><réponse/></right><after/>",
                bind("left/réponse", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("right/réponse", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("after", "string", ""),
                "<group ref=\"/data/left\"><label>Left</label>" + input("left/réponse") + "</group>"
                        + "<group ref=\"/data/right\"><label>Right</label>" + input("right/réponse") + "</group>"
                        + input("after")));
        assertEquals(List.of("réponse", "réponse"), fields(result));
        assertTrue(result.formErrors().get(0).path().contains("/left"));
        assertTrue(result.formErrors().get(1).path().contains("/right"));
        assertDiagnostics(result);
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }
}
