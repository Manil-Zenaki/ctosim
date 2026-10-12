package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static ctosim.FormWalkerAmendment6SearchTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment6LifecycleTest {
    @Test void finalRepairSearchesAfterAllGeneratedAnswersHaveBeenRejected() throws Exception {
        LoadedForm form = late("", "int", ". * 2 = 14", "required=\"true()\"");
        Submission result = run(form, gateAnd("\"value\":0"));
        assertEquals("7", value(result, "answer"));
        assertEquals(2, result.answered(), "Repair adds no answer to the two navigation answers");
        assertElapsed(result, 2);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void finalSearchRepairsAKeptValueWithoutCountingItAsANavigationAnswer() throws Exception {
        LoadedForm form = late("0", "int", ". * 2 = 14", "");
        Submission result = run(form, "\"integerRange\":{\"min\":0,\"max\":0}," + gateAnd(""));
        assertEquals("7", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void finalRepairUsesConstraintLiteralsForText() throws Exception {
        Submission result = run(late("", "string", "contains(., '@')", ""), gateAnd("\"value\":\"bad\""));
        assertEquals("@", value(result, "answer"));
        assertEquals(2, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void finalSearchUsesTheDateAfterNavigationHasCrossedMidnight() throws Exception {
        LoadedForm form = amended5("<answer/><gate>0</gate>",
                bind("answer", "date", constraint("/data/gate = 0 or "
                        + "format-date-time(., '%Y-%m-%d') = format-date-time(today() + 3652, '%Y-%m-%d')"))
                        + bind("gate", "int", "ct:minimum-seconds=\"172800\""),
                input("answer") + input("gate"));
        SimulationConfig cfg = settings("\"start\":\"2026-01-05T08:00:00\",\"fieldworkDays\":1,"
                + "\"speedViolationRate\":0," + gateAnd("\"value\":\"2000-01-01\""));
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals(result.end().toLocalDate().plusDays(3652).toString(), value(result, "answer"));
        assertTrue(result.end().toLocalDate().isAfter(result.start().toLocalDate()));
        assertEquals(2, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exhaustingEveryLevelLeavesOneNavigationFailureAndRequiredWarningWhenNeeded(boolean required) throws Exception {
        Submission result = new FormWalker(single("int", constraint(". * 2 = 20002")
                + " required=\"" + required + "()\""), forced("0", "")).walk(12);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        assertEquals(required ? 2 : 1, result.warnings().size());
        assertWarning(result, "answer", "constraint");
        if (required) assertWarning(result, "answer", "required");
        for (String w : result.warnings()) assertTrue(w.startsWith("submission 12:"), w);
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("change"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exhaustingEveryLevelDuringFinalRepairRetainsTheEndOfFormFailure(boolean required) throws Exception {
        Submission result = run(late("", "int", ". * 2 = 20002", "required=\"" + required + "()\""),
                gateAnd("\"value\":0"));
        assertEquals("", value(result, "answer"));
        assertEquals(2, result.answered());
        assertElapsed(result, 2);
        assertFinalFailure(result, "answer", required);
    }

    @Test void aKindWithNoSearchLevelsStillFailsWithItsConstraintWarning() throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", "string", constraint("false()")),
                "<upload ref=\"/data/answer\" mediatype=\"image/*\"><label>Image</label></upload>");
        Submission result = new FormWalker(form, forced("bad.jpg", "")).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
        assertElapsed(result, 1);
    }

    @Test void selectSearchDoesNotInventAnUnavailableValueFromAConstraintLiteral() throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", "select1", constraint("contains(., 'z')")),
                choices(false, "a", "b"));
        Submission result = new FormWalker(form, forced("a", "")).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
    }

    @Test void aFiveChoiceRequirementIsNotInventedBeyondTheFiniteCombinationSearch() throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", "select", constraint("count-selected(.) * 2 = 10")),
                choices(true, "a", "b", "c", "d", "e"));
        Submission result = new FormWalker(form, forced("a", "")).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
    }

    @Test void searchAddsNeitherClockAdvancesNorSpeedViolationsBeyondOneHandling() throws Exception {
        LoadedForm form = amended5("<answer/><violations/><paths/><elapsed/>",
                bind("answer", "int", constraint(". * 2 = 14") + " ct:minimum-seconds=\"61\"")
                        + bind("violations", "int", "ct:speed-violations=\"count\"")
                        + bind("paths", "string", "ct:speed-violations=\"list\"")
                        + bind("elapsed", "int", "calculate=\"duration()\""), input("answer"));
        Submission result = new FormWalker(form, forced("0", "\"speedViolationRate\":1")).walk(1);
        assertEquals("7", value(result, "answer"));
        assertEquals(61, seconds(result));
        assertEquals("61", value(result, "elapsed"));
        assertEquals("1", value(result, "violations"));
        assertEquals("answer", value(result, "paths"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void finalSearchDoesNotMoveTimeOrAddSpeedViolations() throws Exception {
        LoadedForm form = amended5("<answer/><gate>0</gate><violations/><paths/>",
                bind("answer", "int", constraint("/data/gate = 0 or . * 2 = 14") + " ct:minimum-seconds=\"61\"")
                        + bind("gate", "int", "ct:minimum-seconds=\"61\"")
                        + bind("violations", "int", "ct:speed-violations=\"count\"")
                        + bind("paths", "string", "ct:speed-violations=\"list\""), input("answer") + input("gate"));
        Submission result = run(form, "\"speedViolationRate\":1," + gateAnd("\"value\":0"));
        assertEquals("7", value(result, "answer"));
        assertEquals(122, seconds(result));
        assertEquals("2", value(result, "violations"));
        assertEquals("answer,gate", value(result, "paths"));
        assertEquals(2, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void evaluatingSearchCandidatesDoesNotWriteThemOrRunTheirDependentCalculations() throws Exception {
        LoadedForm form = amended5("<answer/><first/><twice/>",
                bind("answer", "int", constraint(". * 2 = 14"))
                        + bind("first", "string", "calculate=\"once(/data/answer)\"")
                        + bind("twice", "int", "calculate=\"/data/answer * 2\""), input("answer"));
        Submission result = new FormWalker(form, forced("0", "")).walk(1);
        assertEquals("7", value(result, "answer"));
        assertEquals("7", value(result, "first"), "Rejected proposals and searched candidates must not set once()");
        assertEquals("14", value(result, "twice"), "The chosen answer must update ordinary calculations");
        successful(result);
    }

    @Test void initialSearchUsesEachRepeatInstancesOwnConstraintContext() throws Exception {
        LoadedForm form = amended5("<size>3</size><row jr:template=\"\"><target/><answer/></row>",
                bind("size", "int", "") + bind("row/target", "int", "calculate=\"position(..) * 7\"")
                        + bind("row/answer", "int", constraint(". * 2 = ../target * 2")),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/answer") + "</repeat>");
        Submission result = new FormWalker(form, forced("0", "")).walk(1);
        assertEquals(3, count(result, "row"));
        for (int i = 1; i <= 3; i++) assertEquals(Integer.toString(i * 7), value(result, "row[" + i + "]/answer"));
        assertEquals(3, result.answered());
        assertElapsed(result, 3);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void finalSearchUsesEachRepeatInstancesOwnConstraintContext() throws Exception {
        LoadedForm form = amended5("<size>3</size><gate>0</gate><row jr:template=\"\"><target/><answer/></row>",
                bind("size", "int", "") + bind("gate", "int", "")
                        + bind("row/target", "int", "calculate=\"position(..) * 7\"")
                        + bind("row/answer", "int", constraint("/data/gate = 0 or . * 2 = ../target * 2")),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/answer") + "</repeat>" + input("gate"));
        Submission result = run(form, gateAnd("\"value\":0"));
        for (int i = 1; i <= 3; i++) assertEquals(Integer.toString(i * 7), value(result, "row[" + i + "]/answer"));
        assertEquals(4, result.answered());
        assertElapsed(result, 4);
        assertTrue(result.warnings().isEmpty());
    }
}
