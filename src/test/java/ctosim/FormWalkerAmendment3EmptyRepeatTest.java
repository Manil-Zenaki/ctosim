package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment3EmptyRepeatTest {
    @Test void zeroCountHasNoXmlInstanceAndNeverHandlesRequiredImpossibleChild() throws Exception {
        LoadedForm loaded = form("<size>0</size><row jr:template=\"\"><answer/></row><total/>",
                bind("size", "int", "") + bind("row/answer", "int", "required=\"true()\" constraint=\"false()\"")
                + bind("total", "int", "calculate=\"count(/data/row)\""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/answer") + "</repeat>");
        Submission result = walk(loaded, "\"missingRate\":0,\"repeats\":{\"min\":9,\"max\":9},\"fields\":{\"row\":{\"min\":5,\"max\":5}}");
        assertEquals(0, count(result, "row"));
        assertEquals("0", value(result, "total"));
        assertEquals(0, result.answered());
        assertEquals(result.start(), result.end());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void answeredCountAtZeroOneBoundaryControlsInstancesAndContinuesAfterRepeat(int target) throws Exception {
        Submission result = walk(repeat(true, false), "\"missingRate\":0,\"fields\":{\"size\":{\"value\":" + target
                + "},\"answer\":{\"value\":\"child\"},\"after\":{\"value\":\"continued\"}}");
        assertEquals(target, count(result, "row"));
        assertEquals(2 + target, result.answered());
        assertEquals("continued", value(result, "after"));
        if (target == 1) assertEquals("child", value(result, "row[1]/answer"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test void zeroCountIsScopedToEachParentAndDoesNotSkipLaterNonemptyParent() throws Exception {
        LoadedForm loaded = form("<size>3</size><parent jr:template=\"\"><children/><child jr:template=\"\"><answer/></child><after/></parent>",
                bind("size", "int", "") + bind("parent/children", "int", "calculate=\"if(position(..) = 2, 1, 0)\"")
                + bind("parent/child/answer", "string", "") + bind("parent/after", "string", ""),
                "<repeat nodeset=\"/data/parent\" jr:count=\"/data/size\">"
                + "<repeat nodeset=\"/data/parent/child\" jr:count=\"/data/parent/children\">" + input("parent/child/answer")
                + "</repeat>" + input("parent/after") + "</repeat>");
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"answer\":{\"value\":\"child\"},\"after\":{\"value\":\"continued\"}}");
        assertEquals(3, count(result, "parent"));
        assertEquals(0, count(result, "parent[1]/child"));
        assertEquals(1, count(result, "parent[2]/child"));
        assertEquals(0, count(result, "parent[3]/child"));
        assertEquals("child", value(result, "parent[2]/child/answer"));
        for (int i = 1; i <= 3; i++) assertEquals("continued", value(result, "parent[" + i + "]/after"));
        assertEquals(4, result.answered());
        assertElapsed(result, 4);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void zeroOuterCountNeverEntersNestedEnumeratorRepeat() throws Exception {
        Submission result = walk(repeat(true, true), "\"missingRate\":0,\"repeats\":{\"min\":4,\"max\":4},\"fields\":{\"size\":{\"value\":0}}");
        assertEquals(0, count(result, "row"));
        assertEquals(0, count(result, "row/child"));
        assertEquals(2, result.answered());
        assertElapsed(result, 2);
        assertFalse(value(result, "after").isBlank());
    }

    @Test void zeroCountedRepeatDoesNotSuppressNonemptySiblingRepeat() throws Exception {
        LoadedForm loaded = form("<zero>0</zero><one>1</one><empty jr:template=\"\"><bad/></empty><full jr:template=\"\"><answer/></full>",
                bind("zero", "int", "") + bind("one", "int", "")
                + bind("empty/bad", "int", "required=\"true()\" constraint=\"false()\"") + bind("full/answer", "string", ""),
                "<repeat nodeset=\"/data/empty\" jr:count=\"/data/zero\">" + input("empty/bad") + "</repeat>"
                + "<repeat nodeset=\"/data/full\" jr:count=\"/data/one\">" + input("full/answer") + "</repeat>");
        Submission result = walk(loaded, "\"missingRate\":0");
        assertEquals(0, count(result, "empty"));
        assertEquals(1, count(result, "full"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void laterZeroWalkDoesNotRetainInstancesFromEarlierNonzeroWalk() throws Exception {
        LoadedForm loaded = repeat(true, false);
        SimulationConfig cfg = config("\"seed\":481912,\"missingRate\":0,\"fields\":{\"size\":{\"min\":0,\"max\":1}}");
        FormWalker walker = new FormWalker(loaded, cfg);
        boolean zero = false, one = false;
        for (int n = 1; n <= 32; n++) {
            Submission result = walker.walk(n);
            int size = Integer.parseInt(value(result, "size"));
            assertEquals(size, count(result, "row"));
            zero |= size == 0;
            one |= size == 1;
            assertSameSubmission(result, new FormWalker(loaded, cfg).walk(n));
        }
        assertTrue(zero && one, "Fixed-seed sample must exercise both counts");
    }
}
