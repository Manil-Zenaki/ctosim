package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FieldworkAmendment7FallbackTest {
    @Test void aConstraintRejectedReplayFallsBackAndWarnsWithTheFieldAndRecordedValue() throws Exception {
        var form = one("int", "constraint=\". = /data/attempt * 17\"", input("identity"));
        var rows = three(form, field("identity", expr("/data/attempt * 17")));
        values(rows, "identity", "17", "34", "51");
        assertTrue(rows.get(0).warnings().isEmpty());
        assertWarning(rows.get(1), "identity", "17");
        assertWarning(rows.get(2), "identity", "17");
    }

    @Test void aFailedReplayDoesNotReplaceTheFirstVisitSnapshotWithItsFallback() throws Exception {
        var form = one("string", "constraint=\"/data/attempt != 2 or . = 'fallback'\"", input("identity"));
        var rows = three(form, field("identity", "\"value\":\"original\",\"when\":["
                + "{\"if\":\"/data/attempt = 2\",\"value\":\"fallback\"},"
                + "{\"if\":\"/data/attempt = 3\",\"value\":\"third\"}]"));
        values(rows, "identity", "original", "fallback", "original");
        assertWarning(rows.get(1), "identity", "original");
        assertTrue(rows.get(2).warnings().isEmpty(), "a newly accepted original needs no replay warning");
    }

    @Test void aChoiceNoLongerOfferedFallsBackToTheCurrentOfferedChoice() throws Exception {
        String secondary = "<instance id=\"ids\"><root>"
                + "<item><code>old</code><stage>1</stage></item>"
                + "<item><code>new</code><stage>2</stage></item>"
                + "<item><code>third</code><stage>3</stage></item></root></instance>";
        String control = "<select1 ref=\"/data/identity\"><label>Identifier</label>"
                + "<itemset nodeset=\"instance('ids')/root/item[stage = /data/attempt]\">"
                + "<value ref=\"code\"/><label ref=\"code\"/></itemset></select1>";
        var form = form7("<identity/>", bind("identity", "string", ""), control, "", secondary);
        var rows = three(form, "");
        values(rows, "identity", "old", "new", "third");
        assertWarning(rows.get(1), "identity", "old");
        assertWarning(rows.get(2), "identity", "old");
    }

    @Test void rejectingOneRecordedAnswerDoesNotPreventOtherIdentificationFromBeingReplayed() throws Exception {
        var form = form7("<bad/><good/>", bind("bad", "int", "constraint=\". = /data/attempt\"")
                + bind("good", "string", ""), input("bad") + input("good"), "");
        var rows = three(form, field("bad", expr("/data/attempt")) + "," + field("good", byAttempt("first", "later")));
        values(rows, "good", "first", "first", "first");
        values(rows, "bad", "1", "2", "3");
        assertWarning(rows.get(1), "bad", "1");
    }

    @Test void replayRejectionWarningsBelongToTheSubmissionRatherThanThePlan() throws Exception {
        var form = one("int", "constraint=\". = /data/attempt\"", input("identity"));
        var plan = new Fieldwork(form, config7(field("identity", expr("/data/attempt"))));
        var rows = plan.walkAll();
        assertWarning(rows.get(1), "identity", "1");
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        assertTrue(plan.configWarnings().isEmpty());
    }
}
