package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment5ConvergenceTest {
    @ParameterizedTest @ValueSource(ints = {1, 2, 5, 6, 8, 12}) @Timeout(15)
    void fiveRepairPassesAreFollowedByClearingUntilEvenEarlierDependentsAreValid(int length) throws Exception {
        StringBuilder instance = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= length; i++) {
            String name = "link_" + i + "_value";
            String next = i == length ? "gate" : "link_" + (i + 1) + "_value";
            instance.append('<').append(name).append(">0</").append(name).append('>');
            binds.append(bind(name, "int", constraint(". = /data/" + next)));
            body.append(input(name));
        }
        instance.append("<gate>0</gate>");
        binds.append(bind("gate", "int", ""));
        body.append(input("gate"));
        Submission result = run(amended5(instance.toString(), binds.toString(), body.toString()),
                "\"integerRange\":{\"min\":1,\"max\":1}," + gateAnd(""));
        for (int i = 1; i <= length; i++) {
            String name = "link_" + i + "_value";
            assertEquals(i <= length - 5 ? "" : "1", value(result, name), name);
            if (i <= length - 5) assertFinalFailure(result, name, false);
        }
        assertEquals(Math.max(0, length - 5), result.warnings().size());
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void clearingALaterQuestionInvalidatesAnEarlierOneOnTheNextPass() throws Exception {
        LoadedForm form = amended5("<earlier>5</earlier><later>5</later><gate>0</gate>",
                bind("earlier", "int", constraint(". = /data/later"))
                + bind("later", "int", constraint("/data/gate = 0")) + bind("gate", "int", ""),
                input("earlier") + input("later") + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertEquals("", value(result, "earlier"));
        assertEquals("", value(result, "later"));
        assertFinalFailure(result, "earlier", false);
        assertFinalFailure(result, "later", false);
        assertTrue(result.warnings().get(0).contains("later"));
        assertTrue(result.warnings().get(1).contains("earlier"));
    }

    @Test void finalFailureWarningsFollowControlOrderRatherThanInstanceElementOrder() throws Exception {
        LoadedForm form = amended5("<alpha>1</alpha><beta>1</beta><gate>0</gate>",
                bind("alpha", "int", constraint("/data/gate = 0") + " required=\"true()\"")
                + bind("beta", "int", constraint("/data/gate = 0") + " required=\"true()\"")
                + bind("gate", "int", ""), input("beta") + input("alpha") + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertFinalFailure(result, "beta", true);
        assertFinalFailure(result, "alpha", true);
        assertEquals(4, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("beta"));
        assertTrue(result.warnings().get(1).contains("beta"));
        assertTrue(result.warnings().get(2).contains("alpha"));
        assertTrue(result.warnings().get(3).contains("alpha"));
    }

    @Test void repairsRefreshCalculationsAndReportRelevanceBeforeReportsArePopulated() throws Exception {
        LoadedForm form = amended5("<answer>9</answer><gate>0</gate><double/><violations/><violated/>",
                bind("answer", "int", constraint("/data/gate = 0 or . = 3"))
                + bind("gate", "int", "ct:minimum-seconds=\"70\"")
                + bind("double", "int", "calculate=\"/data/answer * 2\"")
                + bind("violations", "int", "ct:speed-violations=\"count\" relevant=\"/data/double = 6\"")
                + bind("violated", "string", "ct:speed-violations=\"list\" relevant=\"/data/double = 6\""),
                input("answer") + input("gate"));
        Submission result = run(form, "\"integerRange\":{\"min\":3,\"max\":3},\"speedViolationRate\":1," + gateAnd(""));
        assertEquals("3", value(result, "answer"));
        assertEquals("6", value(result, "double"));
        assertEquals("1", value(result, "violations"));
        assertEquals("gate", value(result, "violated"));
    }

    @Test void clearingRefreshesDependentCalculations() throws Exception {
        LoadedForm form = amended5("<answer>9</answer><gate>0</gate><display/>",
                bind("answer", "int", constraint("/data/gate = 0")) + bind("gate", "int", "")
                + bind("display", "string", "calculate=\"concat('value=', /data/answer)\""),
                input("answer") + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertEquals("value=", value(result, "display"));
    }

    @Test void finalDurationCalculationsObserveTheRepairedAnswer() throws Exception {
        LoadedForm form = amended5("<answer>9</answer><gate>0</gate><snapshot/>",
                bind("answer", "int", constraint("/data/gate = 0 or . = 3"))
                + bind("gate", "int", "ct:minimum-seconds=\"70\"")
                + bind("snapshot", "string", "calculate=\"if(duration() > 0, once(string(/data/answer)), '')\""),
                input("answer") + input("gate"));
        Submission result = run(form, "\"integerRange\":{\"min\":3,\"max\":3}," + gateAnd(""));
        assertEquals("3", value(result, "snapshot"), "The final duration phase follows constraint repair");
        assertEquals(70, seconds(result));
    }

    @Test void aRepairCanMakeAnotherInvalidQuestionIrrelevant() throws Exception {
        LoadedForm form = amended5("<answer>9</answer><hidden>9</hidden><gate>0</gate>",
                bind("answer", "int", constraint("/data/gate = 0 or . = 3"))
                + bind("hidden", "int", constraint("/data/gate = 0") + " relevant=\"/data/answer = 9\"")
                + bind("gate", "int", ""), input("answer") + input("hidden") + input("gate"));
        Submission result = run(form, "\"integerRange\":{\"min\":3,\"max\":3}," + gateAnd(""));
        assertEquals("3", value(result, "answer"));
        assertEquals(0, count(result, "hidden"));
        assertTrue(result.warnings().isEmpty(), "No final failure for a question now irrelevant");
    }
}
