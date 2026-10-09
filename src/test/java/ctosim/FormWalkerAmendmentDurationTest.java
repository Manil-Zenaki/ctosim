package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendmentDurationTest {
    @ParameterizedTest @ValueSource(strings = {"duration()", "duration ( )", "0 + duration()", "if(true(), duration(), 0)"})
    void finalRefreshFindsDurationCallsAndIncludesTheLastQuestionAdvance(String expression) throws Exception {
        LoadedForm form = amendedForm("<elapsed/><last/>", bind("elapsed", "int", "calculate=\"" + expression + "\"")
                + bind("last", "string", ""), input("last"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertElapsed(result, 1);
        assertEquals(seconds(result), integer(result, "elapsed"));
        assertEquals(1, result.answered());
    }

    @Test void anInterviewWithoutHandledQuestionsHasZeroFinalDuration() throws Exception {
        LoadedForm form = amendedForm("<elapsed/><note>kept</note>", bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("note", "string", "readonly=\"true()\""), input("note"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(0, integer(result, "elapsed"));
        assertEquals(result.start(), result.end());
    }

    @Test void finalDurationIncludesBlankAndRejectedQuestionsWithoutCountingThemAsAnswers() throws Exception {
        LoadedForm form = amendedForm("<elapsed/><blank/><reject/>", bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("blank", "string", "") + bind("reject", "int", "constraint=\"false()\""), input("blank") + input("reject"));
        Submission result = walk(form, "\"missingRate\":1,\"fields\":{\"reject\":{\"value\":1,\"missingRate\":0}}");
        assertEquals(seconds(result), integer(result, "elapsed"));
        assertElapsed(result, 2);
        assertEquals(0, result.answered());
    }

    @Test void dependentFieldsAreUpdatedThroughMultipleLevelsBeforeSerialization() throws Exception {
        LoadedForm form = amendedForm("<elapsed/><double/><message/><answer/>",
                bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("double", "int", "calculate=\"/data/elapsed * 2\"")
                + bind("message", "string", "calculate=\"concat('seconds=', /data/double)\"") + bind("answer", "string", ""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(seconds(result) * 2, integer(result, "double"));
        assertEquals("seconds=" + seconds(result) * 2, value(result, "message"));
    }

    @Test void onceDurationRetainsItsInitialZeroWhilePlainDurationReachesTheEnd() throws Exception {
        LoadedForm form = amendedForm("<frozen/><elapsed/><answer/>",
                bind("frozen", "int", "calculate=\"once(duration())\"")
                + bind("elapsed", "int", "calculate=\"duration()\"") + bind("answer", "string", ""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(0, integer(result, "frozen"));
        assertEquals(seconds(result), integer(result, "elapsed"));
        assertTrue(seconds(result) > 0);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 3})
    void finalRefreshVisitsEveryNestedRepeatInstance(int children) throws Exception {
        LoadedForm form = amendedForm("<parents>2</parents><parent jr:template=\"\"><elapsed/><child jr:template=\"\"><elapsed/><copy/><answer/></child></parent><last/>",
                bind("parents", "int", "") + bind("parent/elapsed", "int", "calculate=\"duration()\"")
                + bind("parent/child/elapsed", "int", "calculate=\"duration()\"")
                + bind("parent/child/copy", "int", "calculate=\"../elapsed + 1\"")
                + bind("parent/child/answer", "string", "") + bind("last", "string", ""),
                "<repeat nodeset=\"/data/parent\" jr:count=\"/data/parents\"><repeat nodeset=\"/data/parent/child\">"
                + input("parent/child/answer") + "</repeat></repeat>" + input("last"));
        Submission result = walk(form, "\"missingRate\":0,\"repeats\":{\"min\":" + children + ",\"max\":" + children + "}");
        assertEquals(2, count(result, "parent"));
        for (int p = 1; p <= 2; p++) {
            String parent = "parent[" + p + "]";
            assertEquals(seconds(result), integer(result, parent + "/elapsed"));
            assertEquals(children, count(result, parent + "/child"));
            for (int c = 1; c <= children; c++) {
                String child = parent + "/child[" + c + "]";
                assertEquals(seconds(result), integer(result, child + "/elapsed"));
                assertEquals(seconds(result) + 1, integer(result, child + "/copy"));
            }
        }
        assertEquals(2 * children + 1, result.answered());
        assertElapsed(result, 2 * children + 1);
    }

    @Test void finalRefreshUsesInstanceDocumentOrderRatherThanBindOrder() throws Exception {
        // With no intermediate answer there is no earlier positive-duration recalculation.
        // Reversing final refresh order would freeze `second` at zero before `first` becomes 11.
        LoadedForm form = amendedForm("<first/><second/><last/>",
                bind("second", "int", "calculate=\"if(duration() &gt; 0, once(/data/first), '')\"")
                + bind("first", "int", "calculate=\"if(duration() &gt; 0, 11, 0)\"")
                + bind("last", "string", ""), input("last"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(11, integer(result, "first"));
        assertEquals(11, integer(result, "second"));
    }

    @Test void finalRefreshHappensBeforeEndTimestampPostProcessing() throws Exception {
        LoadedForm form = amendedForm(METADATA + "<before_end/><answer/>", METADATA_BINDS
                + bind("before_end", "string", "calculate=\"if(duration() &gt; 0, once(if(string-length(/data/finished) = 0, 'before', 'after')), '')\"")
                + bind("answer", "string", ""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals("before", value(result, "before_end"));
        assertFalse(value(result, "finished").isBlank());
    }

    @Test void calculateHereExpressionsAreNotEvaluatedAgainDuringFinalRefresh() throws Exception {
        LoadedForm form = amendedForm("<snapshot/><snapshot_copy/><elapsed/><last/>",
                here("snapshot", "int", "duration()", "readonly=\"true()\"")
                + bind("snapshot_copy", "int", "calculate=\"/data/snapshot + 1\"")
                + bind("elapsed", "int", "calculate=\"duration()\"") + bind("last", "string", ""),
                input("snapshot") + input("last"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(0, integer(result, "snapshot"));
        assertEquals(1, integer(result, "snapshot_copy"));
        assertEquals(seconds(result), integer(result, "elapsed"));
        assertElapsed(result, 1);
    }

    @Test void finalDurationRefreshAlsoUpdatesDependentRelevanceBeforeSerialization() throws Exception {
        LoadedForm form = amendedForm("<elapsed/><visible>kept</visible><answer/>",
                bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("visible", "string", "relevant=\"/data/elapsed &gt; 0\" readonly=\"true()\"")
                + bind("answer", "string", ""), input("visible") + input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(1, count(result, "visible"));
        assertEquals("kept", value(result, "visible"));
        assertEquals(1, result.answered());
    }

    @Test void durationTextInsideAStringLiteralDoesNotCauseAnUnrelatedCalculationToRefresh() throws Exception {
        LoadedForm form = amendedForm("<decoy/><answer/>",
                bind("decoy", "string", "calculate=\"concat('duration()', format-date-time(now(), '%H:%M:%S'))\"")
                + bind("answer", "string", ""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        String expected = "duration()" + result.start().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        assertEquals(expected, value(result, "decoy"));
    }
}
