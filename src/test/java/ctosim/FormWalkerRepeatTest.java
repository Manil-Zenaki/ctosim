package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormWalkerRepeatTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 4})
    void countedRepeatsFollowEngineCountRatherThanEnumeratorRange(int target) throws Exception {
        Submission result = walk(repeat(true, false), "\"missingRate\":0,\"repeats\":{\"min\":7,\"max\":7},"
                + "\"fields\":{\"size\":{\"value\":" + target + "},\"row\":{\"min\":9,\"max\":9}}");
        assertEquals(target, count(result, "row"));
        assertEquals(2 + target, result.answered());
        for (int i = 1; i <= target; i++) assertFalse(value(result, "row[" + i + "]/answer").isBlank());
        assertFalse(value(result, "after").isBlank(), "Walker continues after counted repeat");
        assertElapsed(result, 2 + target);
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 4, 1000})
    void enumeratorRepeatUsesInclusiveFixedGlobalCount(int target) throws Exception {
        Submission result = walk(repeat(false, false), "\"missingRate\":0,\"repeats\":{\"min\":" + target + ",\"max\":" + target + "}");
        assertEquals(target, count(result, "row"));
        assertEquals(2 + target, result.answered());
        assertElapsed(result, 2 + target);
        assertFalse(value(result, "after").isBlank());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void repeatOverrideRoundsFractionalBoundsInward() throws Exception {
        Submission result = walk(repeat(false, false), """
            "missingRate":0,"repeats":{"min":8,"max":8},
            "fields":{"row":{"min":1.2,"max":2.9}}
            """);
        assertEquals(2, count(result, "row"));
        assertEquals(4, result.answered());
    }

    @Test void repeatOverrideCanExplicitlySelectZero() throws Exception {
        Submission result = walk(repeat(false, false), """
            "missingRate":0,"repeats":{"min":3,"max":3},
            "fields":{"row":{"min":0,"max":0}}
            """);
        assertEquals(0, count(result, "row"));
        assertEquals(2, result.answered());
        assertElapsed(result, 2);
    }

    @ParameterizedTest @ValueSource(strings = {"\"value\":\"7\"", "\"texts\":[\"ignored\"]", "\"min\":\"2040-01-01\",\"max\":\"2040-01-02\""})
    void nonNumericRepeatOverridesFallBackToGlobalRange(String override) throws Exception {
        Submission result = walk(repeat(false, false), "\"missingRate\":0,\"repeats\":{\"min\":2,\"max\":2},\"fields\":{\"row\":{" + override + "}}");
        assertEquals(2, count(result, "row"));
        assertEquals(4, result.answered());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 3})
    void nestedRepeatsHaveTheirOwnTargetWithinEachParent(int children) throws Exception {
        Submission result = walk(repeat(false, true), "\"missingRate\":0,\"repeats\":{\"min\":2,\"max\":2},\"fields\":{\"child\":{\"min\":"
                + children + ",\"max\":" + children + "}}");
        assertEquals(2, count(result, "row"));
        assertEquals(children, count(result, "row[1]/child"));
        assertEquals(children, count(result, "row[2]/child"));
        assertEquals(4 + 2 * children, result.answered());
        assertElapsed(result, 4 + 2 * children);
    }

    @Test void siblingRepeatsEachReceiveTheirOwnOverride() throws Exception {
        LoadedForm loaded = form("<first jr:template=\"\"><answer/></first><second jr:template=\"\"><answer/></second><after/>",
                bind("first/answer", "string", "") + bind("second/answer", "string", "") + bind("after", "string", ""),
                "<repeat nodeset=\"/data/first\">" + input("first/answer") + "</repeat>"
                        + "<repeat nodeset=\"/data/second\">" + input("second/answer") + "</repeat>" + input("after"));
        Submission result = walk(loaded, """
            "missingRate":0,"fields":{"first":{"min":0,"max":0},"second":{"min":3,"max":3},"answer":{"value":"shared leaf override"}}
            """);
        assertEquals(0, count(result, "first"));
        assertEquals(3, count(result, "second"));
        for (int i = 1; i <= 3; i++) assertEquals("shared leaf override", value(result, "second[" + i + "]/answer"));
        assertEquals(4, result.answered());
    }

    @Test void enumeratorRepeatsInsideCountedParentsAreHandledForEveryParent() throws Exception {
        Submission result = walk(repeat(true, true), """
            "missingRate":0,"repeats":{"min":8,"max":8},
            "fields":{"size":{"value":3},"child":{"min":2,"max":2}}
            """);
        assertEquals(3, count(result, "row"));
        for (int parent = 1; parent <= 3; parent++) assertEquals(2, count(result, "row[" + parent + "]/child"));
        assertEquals(11, result.answered());
        assertElapsed(result, 11);
    }

    @Test void repeatRelevanceAndRequirednessAreEvaluatedPerInstance() throws Exception {
        LoadedForm loaded = form("<row jr:template=\"\"><index/><answer/></row>",
                bind("row/index", "int", "calculate=\"position(..)\"")
                        + bind("row/answer", "string", "relevant=\"../index != 2\" required=\"../index = 3\""),
                "<repeat nodeset=\"/data/row\">" + input("row/answer") + "</repeat>");
        Submission result = walk(loaded, "\"missingRate\":1,\"repeats\":{\"min\":3,\"max\":3}");
        assertEquals("", value(result, "row[1]/answer"));
        assertEquals(0, count(result, "row[2]/answer"));
        assertFalse(value(result, "row[3]/answer").isBlank());
        assertEquals(1, result.answered());
        assertElapsed(result, 2);
    }

    @Test void eachFailedRepeatedQuestionInstanceGetsItsOwnWarning() throws Exception {
        LoadedForm loaded = form("<row jr:template=\"\"><answer/></row>", bind("row/answer", "int", "constraint=\"false()\""),
                "<repeat nodeset=\"/data/row\">" + input("row/answer") + "</repeat>");
        Submission result = walk(loaded, "\"missingRate\":0,\"repeats\":{\"min\":3,\"max\":3}");
        assertEquals(3, result.warnings().size());
        assertTrue(result.warnings().stream().allMatch(w -> w.startsWith("submission 1:") && w.contains("answer")));
        assertEquals(0, result.answered());
        assertElapsed(result, 3);
    }

    @Test @Timeout(120)
    void runawayEnumeratorRepeatStopsWithSubmissionNumberAndCurrentPath() throws Exception {
        FormWalker walker = new FormWalker(repeat(false, false), config("""
            "missingRate":0,"fields":{"row":{"min":200001,"max":200001}}
            """));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> walker.walk(731));
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains("731"), failure.getMessage());
        assertTrue(failure.getMessage().contains("/data/row"), failure.getMessage());
    }
}
