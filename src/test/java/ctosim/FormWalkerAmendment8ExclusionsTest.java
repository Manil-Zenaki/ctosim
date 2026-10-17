package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static ctosim.FormWalkerAmendment8Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment8ExclusionsTest {
    @Test void intentionalMissingnessIsNotAConstraintFailureEvenWithAnImpossibleConstraint() throws Exception {
        Submission result = walk(single("int", constraint("false()")), "\"missingRate\":1");
        assertEquals("", value(result, "answer"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void anExpressionProducingAnEmptyAnswerDoesNotCreateAConstraintRecord() throws Exception {
        Submission result = run(single("string", constraint("false()")),
                "\"fields\":{\"answer\":{\"expr\":\"''\"}}");
        assertEquals("", value(result, "answer"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failedNumericConversionIsNotRecordedEvenWithAConstraintAndRequiredWarning(boolean required) throws Exception {
        Submission result = fixed(single("int", constraint("false()") + " required=\"" + required + "()\""), "bad-number");
        assertEquals("", value(result, "answer"));
        assertWarning(result, "answer", "bad-number");
        if (required) assertWarning(result, "answer", "required");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @ParameterizedTest @CsvSource({"select1,select1,999", "select,select,20 999"})
    void unofferedChoicesAreConversionFailuresNotConstraintRecords(String control, String type, String answer) throws Exception {
        Submission result = fixed(select(control, type, constraint("false()")), answer);
        assertEquals("", value(result, "answer"));
        assertWarning(result, "answer", answer);
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"select1", "select"})
    void emptyOfferedChoiceListsDoNotCreateConstraintRecords(String control) throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", control, constraint("false()")),
                "<" + control + " ref=\"/data/answer\"><label>Choice</label>"
                + "<itemset nodeset=\"instance('choices')/root/item[false()]\">"
                + "<label ref=\"label\"/><value ref=\"value\"/></itemset></" + control + ">", "",
                "<instance id=\"choices\"><root><item><label>A</label><value>a</value></item></root></instance>");
        Submission result = walk8(form);
        assertEquals("", value(result, "answer"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"readonly=\"true()\"", "relevant=\"false()\""})
    void skippedQuestionsWithImpossibleConstraintsDoNotCreateRecords(String attributes) throws Exception {
        Submission result = walk8(single("int", constraint("false()") + " " + attributes));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void unsupportedQuestionsDoNotCreateConstraintRecords() throws Exception {
        Submission result = walk8(single("boolean", constraint("false()")));
        assertWarning(result, "answer");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void calculationsAndCalculateHereAreNotRecordedForTheirUnsatisfiedConstraints() throws Exception {
        LoadedForm form = amended5("<calculated/><here/>",
                bind("calculated", "int", "calculate=\"7\" " + constraint("false()"))
                + bind("here", "int", "ct:calculate-here=\"7\" " + constraint("false()")), input("here"));
        Submission result = walk8(form);
        assertEquals("7", value(result, "calculated"));
        assertEquals("7", value(result, "here"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectedProposalsFollowedBySuccessfulSearchDoNotLeaveARecord(boolean atEnd) throws Exception {
        LoadedForm form = atEnd ? late("", "int", ". * 2 = 14", "") : single("int", constraint(". * 2 = 14"));
        Submission result = run(form, atEnd ? gateAnd("\"value\":0") : "\"fields\":{\"answer\":{\"value\":0}}");
        assertEquals("7", value(result, "answer"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void aQuestionSuccessfullyRepairedAtTheEndDoesNotLeaveARecord() throws Exception {
        Submission result = run(late("0", "int", ". = 3", ""),
                "\"integerRange\":{\"min\":3,\"max\":3}," + gateAnd(""));
        assertEquals("3", value(result, "answer"));
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }
}
