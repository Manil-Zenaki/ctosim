package ctosim;

import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static ctosim.FormWalkerAmendment2Support.REPORTS;
import static ctosim.FormWalkerAmendment2Support.REPORT_BINDS;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment5FinalCheckTest {
    @Test void aKeptDefaultInvalidatedByALaterAnswerIsRepaired() throws Exception {
        Submission result = run(late("9", "int", ". <= 5", ""),
                "\"integerRange\":{\"min\":3,\"max\":3}," + gateAnd(""));
        assertEquals("3", value(result, "answer"));
        assertEquals(1, result.answered(), "Only the gate was answered during navigation");
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void aStillValidEarlierAnswerIsLeftUnchanged() throws Exception {
        Submission result = run(late("3", "int", ". <= 5", ""),
                "\"integerRange\":{\"min\":4,\"max\":4}," + gateAnd(""));
        assertEquals("3", value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anUnrepairableGeneratedAnswerIsClearedWithoutChangingAnswered(boolean required) throws Exception {
        Submission result = run(late("", "int", ". <= 5", "required=\"" + required + "()\""),
                gateAnd("\"value\":9"));
        assertEquals("", value(result, "answer"));
        assertEquals(2, result.answered(), "Final clearing does not undo a navigation answer");
        assertElapsed(result, 2);
        assertFinalFailure(result, "answer", required);
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("changed"));
    }

    @Test void aFailedRepairOfAKeptValueDoesNotBecomeAnAnsweredQuestion() throws Exception {
        Submission result = run(late("9", "int", "false()", ""), gateAnd(""));
        assertEquals("", value(result, "answer"));
        assertEquals(1, result.answered());
        assertFinalFailure(result, "answer", false);
    }

    @Test void requirednessIsEvaluatedOnTheFinalInstance() throws Exception {
        Submission result = run(late("9", "int", "false()", "required=\"/data/gate = 1\""), gateAnd(""));
        assertEquals("", value(result, "answer"));
        assertFinalFailure(result, "answer", true);
    }

    @Test void emptyQuestionsAreNotRegeneratedOrWarnedAboutDuringTheFinalCheck() throws Exception {
        LoadedForm form = late("", "string", "false()", "");
        Submission result = run(form, gateAnd("\"missingRate\":1"));
        assertEquals("", value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void finalRepairPassesCurrentRequirednessToTheGenerator() throws Exception {
        LoadedForm form = late("9", "int", ". = 3", "required=\"/data/gate = 1\"");
        SimulationConfig config = config("\"seed\":815031,\"missingRate\":1,"
                + "\"integerRange\":{\"min\":3,\"max\":3},\"fields\":{\"gate\":{\"value\":1,\"missingRate\":0}}");
        Submission result = new FormWalker(form, config).walk(7);
        assertEquals("3", value(result, "answer"), "Now-required repairs must bypass optional missingness");
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"string,good,. = 'good'", "decimal,2.5,. = 2.5", "int,0,. = 0"})
    void finalRepairUsesTheQuestionTypeAndItsConfiguredGenerator(String type, String replacement, String rule) throws Exception {
        LoadedForm form = amended5("<answer/><gate>0</gate>",
                bind("answer", type, constraint(rule)) + bind("gate", "int", ""), input("answer")
                + "<input ref=\"/data/gate\"><label>Gate</label>"
                + "<setvalue event=\"xforms-value-changed\" ref=\"/data/answer\" value=\"'9'\"/></input>");
        Submission result = run(form, gateAnd("\"value\":\"" + replacement + "\""));
        assertEquals(replacement, value(result, "answer"));
        assertEquals(2, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void finalRepairPreservesTheQuestionsAppearance() throws Exception {
        LoadedForm form = amended5("<answer>bad</answer><gate>0</gate>",
                bind("answer", "string", constraint("/data/gate = 0 or regex(., '^[0-9]{8}$')"))
                + bind("gate", "int", ""),
                "<input ref=\"/data/answer\" appearance=\"numbers\"><label>Phone</label></input>" + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertTrue(value(result, "answer").matches("[0-9]{8}"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectRepairsProduceValuesThatSatisfyTheFinalSelectionConstraint(boolean multiple) throws Exception {
        LoadedForm form = amended5("<answer/><gate>0</gate>",
                bind("answer", multiple ? "select" : "select1",
                        constraint("/data/gate = 0 or (. = 'good')")) + bind("gate", "int", ""),
                selectControl("answer", multiple) + input("gate"));
        FormWalker walker = new FormWalker(form, settings(gateAnd("")));
        int repaired = 0;
        for (int n = 1; n <= 60; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            if (answer.isEmpty()) assertFinalFailure(result, "answer", false);
            else {
                assertEquals("good", answer);
                assertTrue(result.warnings().isEmpty());
                repaired++;
            }
            assertEquals(2, result.answered());
        }
        assertTrue(repaired >= 55, "Fresh retries should almost always find the offered good choice");
    }

    @Test @Timeout(90)
    void retriesUseFreshDrawsAndApproximatelyFiftyAttemptsBeforeClearing() throws Exception {
        // Wrapping '.' in concat prevents both hints and post-budget candidates (spec 09 Amendment 2).
        LoadedForm form = amended5("<answer/><gate>0</gate>",
                bind("answer", "select1", constraint("/data/gate = 0 or concat(., '') = 'good'"))
                + bind("gate", "int", ""), selectControl("answer", false) + input("gate"));
        FormWalker walker = new FormWalker(form, settings(gateAnd("\"weights\":{\"bad\":49,\"good\":1}")));
        int blank = 0;
        int draws = 700;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            assertTrue(answer.isEmpty() || answer.equals("good"), "Rejected choice must never survive");
            if (answer.isEmpty()) {
                blank++;
                assertFinalFailure(result, "answer", false);
            } else assertTrue(result.warnings().isEmpty());
            assertEquals(2, result.answered());
        }
        // One original draw, then at most 50 repair draws. Do not prescribe an RNG algorithm.
        assertUniformBin(blank, draws, Math.pow(0.98, 51), "Exhausted final repairs");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalRepairAndClearingAddNeitherTimeNorSpeedViolations(boolean repairable) throws Exception {
        LoadedForm form = amended5("<answer>9</answer><gate>0</gate>" + REPORTS + "<duration/>",
                bind("answer", "int", constraint("/data/gate = 0 or " + (repairable ? ". = 3" : "false()"))
                        + " ct:minimum-seconds=\"500\"")
                + bind("gate", "int", "ct:minimum-seconds=\"70\"") + REPORT_BINDS
                + bind("duration", "int", "calculate=\"duration()\""), input("answer") + input("gate"));
        Submission result = run(form, "\"speedViolationRate\":1,\"integerRange\":{\"min\":3,\"max\":3}," + gateAnd(""));
        assertEquals(repairable ? "3" : "", value(result, "answer"));
        assertEquals(70, seconds(result));
        assertEquals("70", value(result, "duration"));
        assertEquals("1", value(result, "violations"));
        assertEquals("gate", value(result, "violated"));
        assertEquals(1, result.answered());
    }

    @Test void invalidDefaultDuringNavigationDoesRecordNormalTimeAndSpeedViolation() throws Exception {
        LoadedForm form = amended5("<answer>9</answer>" + REPORTS,
                bind("answer", "int", constraint(". = 3") + " ct:minimum-seconds=\"70\"") + REPORT_BINDS,
                input("answer"));
        Submission result = run(form, "\"speedViolationRate\":1,\"integerRange\":{\"min\":3,\"max\":3}");
        assertEquals("3", value(result, "answer"));
        assertEquals(70, seconds(result));
        assertEquals("answer", value(result, "violated"));
        assertEquals("1", value(result, "violations"));
    }

    @Test void finalRepairUsesTheClockDateAfterCrossingMidnight() throws Exception {
        LoadedForm form = amended5("<answer>1900-01-01</answer><gate>0</gate>",
                bind("answer", "date", constraint("/data/gate = 0 or . = today()"))
                + bind("gate", "int", "ct:minimum-seconds=\"86400\""), input("answer") + input("gate"));
        FormWalker walker = new FormWalker(form, settings("\"start\":\"2040-02-28T08:00:00\",\"fieldworkDays\":1," + gateAnd("")));
        int successfulRepairs = 0;
        for (int n = 1; n <= 160; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            if (answer.isEmpty()) continue; // 50 draws need not find this one date out of 365.
            LocalDate date = LocalDate.parse(answer);
            LocalDate today = result.end().toLocalDate();
            assertEquals(today, date);
            successfulRepairs++;
        }
        // A stale 'today' cannot generate tomorrow. Chance of no success with fresh dates is < 3e-10.
        assertTrue(successfulRepairs > 0, "Final repairs must use the advanced clock date");
    }

    @Test void successfulRepairsReplayAcrossLoadsAndWalkOrder() throws Exception {
        LoadedForm form = late("bad", "string", ". = 'good'", "");
        SimulationConfig config = settings(gateAnd("\"texts\":[\"bad\",\"good\"]"));
        FormWalker first = new FormWalker(form, config);
        FormWalker second = new FormWalker(late("bad", "string", ". = 'good'", ""), config);
        var expected = new ArrayList<Submission>();
        for (int n = 1; n <= 30; n++) expected.add(first.walk(n));
        for (int n = 30; n >= 1; n--) {
            assertSameSubmission(expected.get(n - 1), first.walk(n));
            assertSameSubmission(expected.get(n - 1), second.walk(n));
        }
    }

    @Test void finalFailureWarningsReplayAfterOtherSubmissions() throws Exception {
        FormWalker first = new FormWalker(late("9", "int", "false()", "required=\"true()\""), settings(gateAnd("")));
        FormWalker second = new FormWalker(late("9", "int", "false()", "required=\"true()\""), settings(gateAnd("")));
        Submission expected = first.walk(7);
        assertFinalFailure(expected, "answer", true);
        first.walk(2);
        second.walk(50);
        assertSameSubmission(expected, first.walk(7));
        assertSameSubmission(expected, second.walk(7));
    }

    @Test void finalRepairDrawsChangeWhenTheConfiguredSeedChanges() throws Exception {
        LoadedForm form = late("999", "int", ". <= 10", "");
        SimulationConfig config = settings("\"integerRange\":{\"min\":0,\"max\":10}," + gateAnd(""));
        FormWalker first = new FormWalker(form, config.withSeed(1));
        FormWalker second = new FormWalker(form, config.withSeed(2));
        boolean differs = false;
        for (int n = 1; n <= 30; n++) {
            String a = value(first.walk(n), "answer");
            String b = value(second.walk(n), "answer");
            assertTrue(Integer.parseInt(a) <= 10);
            assertTrue(Integer.parseInt(b) <= 10);
            differs |= !a.equals(b);
        }
        assertTrue(differs, "Repair answers, not just metadata, must use the configured seed");
    }
}
