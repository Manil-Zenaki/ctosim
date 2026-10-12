package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment5ScopeTest {
    @Test void fieldsWithoutControlsReadonlyAndCalculateHereAreExcludedEvenWithFalseConstraints() throws Exception {
        LoadedForm form = amended5("<no_control>8</no_control><calculated/><note>8</note><here/><plain>8</plain>",
                bind("no_control", "int", constraint("false()"))
                + bind("calculated", "int", "calculate=\"8\" " + constraint("false()"))
                + bind("note", "int", "readonly=\"true()\" " + constraint("false()"))
                + bind("here", "int", "ct:calculate-here=\"8\" " + constraint("false()"))
                + bind("plain", "int", ""), input("note") + input("here") + input("plain"));
        Submission result = run(form, "");
        for (String name : new String[]{"no_control", "calculated", "note", "here", "plain"})
            assertEquals("8", value(result, name), name);
        assertEquals(0, result.answered());
        assertEquals(0, seconds(result));
        assertTrue(result.warnings().isEmpty());
    }

    @Test void anIrrelevantQuestionWithAnInvalidDefaultIsNotHandled() throws Exception {
        LoadedForm form = amended5("<answer>9</answer>",
                bind("answer", "int", "relevant=\"false()\" required=\"true()\" " + constraint("false()")), input("answer"));
        Submission result = run(form, "");
        assertEquals(0, count(result, "answer"));
        assertEquals(0, result.answered());
        assertEquals(0, seconds(result));
        assertTrue(result.warnings().isEmpty());
    }

    @Test void aQuestionMadeIrrelevantByALaterAnswerIsNotRepaired() throws Exception {
        Submission result = run(late("9", "int", "false()", "relevant=\"/data/gate = 0\""), gateAnd(""));
        assertEquals(0, count(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void everyRelevantCountedOrUncountedRepeatInstanceUsesItsOwnConstraintContext(boolean counted) throws Exception {
        LoadedForm form = amended5("<size>3</size><row jr:template=\"\"><answer>9</answer><limit/></row><gate>0</gate>",
                bind("size", "int", "") + bind("row/answer", "int", constraint("/data/gate = 0 or . = ../limit"))
                + bind("row/limit", "int", "calculate=\"position(..)\"") + bind("gate", "int", ""),
                "<repeat nodeset=\"/data/row\" " + (counted ? "jr:count=\"/data/size\"" : "") + ">"
                + input("row/answer") + "</repeat>" + input("gate"));
        Submission result = run(form, "\"repeats\":{\"min\":3,\"max\":3},\"integerRange\":{\"min\":1,\"max\":3}," + gateAnd(""));
        assertEquals(3, count(result, "row"));
        for (int i = 1; i <= 3; i++) assertEquals(Integer.toString(i), value(result, "row[" + i + "]/answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void nestedRepeatsDoNotConflateSameNameQuestionsAcrossParents() throws Exception {
        LoadedForm form = amended5("<size>2</size><parent jr:template=\"\"><limit/><child jr:template=\"\"><answer>9</answer></child></parent><gate>0</gate>",
                bind("size", "int", "") + bind("parent/limit", "int", "calculate=\"position(..)\"")
                + bind("parent/child/answer", "int", constraint("/data/gate = 0 or . = ../../limit"))
                + bind("gate", "int", ""),
                "<repeat nodeset=\"/data/parent\" jr:count=\"/data/size\"><repeat nodeset=\"/data/parent/child\" jr:count=\"/data/size\">"
                + input("parent/child/answer") + "</repeat></repeat>" + input("gate"));
        Submission result = run(form, "\"integerRange\":{\"min\":1,\"max\":2}," + gateAnd(""));
        assertEquals(2, count(result, "parent"));
        for (int p = 1; p <= 2; p++) {
            assertEquals(2, count(result, "parent[" + p + "]/child"));
            for (int c = 1; c <= 2; c++) assertEquals(Integer.toString(p), value(result, "parent[" + p + "]/child[" + c + "]/answer"));
        }
        assertTrue(result.warnings().isEmpty());
    }

    @Test void invalidRequiredAnswersInRepeatsAreClearedAndWarnedPerRelevantInstance() throws Exception {
        LoadedForm form = amended5("<size>3</size><row jr:template=\"\"><answer>9</answer></row><gate>0</gate>",
                bind("size", "int", "") + bind("row/answer", "int", constraint("/data/gate = 0")
                        + " required=\"true()\" relevant=\"position(..) != 2\"") + bind("gate", "int", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/answer") + "</repeat>" + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertEquals(3, count(result, "row"));
        assertEquals("", value(result, "row[1]/answer"));
        assertEquals(0, count(result, "row[2]/answer"));
        assertEquals("", value(result, "row[3]/answer"));
        assertEquals(4, result.warnings().size(), "One failure and one required warning per relevant instance");
        for (int i = 0; i < 4; i += 2) {
            assertTrue(result.warnings().get(i).contains("answer"));
            assertTrue(result.warnings().get(i).toLowerCase(java.util.Locale.ROOT).contains("constraint"));
            assertTrue(result.warnings().get(i + 1).toLowerCase(java.util.Locale.ROOT).contains("required"));
        }
        assertEquals(1, result.answered());
    }
}
