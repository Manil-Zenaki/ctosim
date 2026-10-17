package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static ctosim.FormWalkerAmendment8Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment8RepeatsTest {
    private Submission repeated(int size, boolean atEnd, String rule) throws Exception {
        String xml = resource("amendment8/repeats.xml")
                .replace("__SIZE__", Integer.toString(size))
                .replace("__CONSTRAINT__", constraint(rule))
                .replace("__BEFORE__", atEnd ? "" : input("gate"))
                .replace("__AFTER__", atEnd ? input("gate") : "");
        return run(loadXml(xml), gateAnd("\"value\":5"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eachRepeatedFailureHasItsOwnIndexedPathAndSameInstanceAbsoluteReference(boolean atEnd) throws Exception {
        Submission result = repeated(3, atEnd, "/data/gate=0 or ((.>=/data/row/target) and (.<=60))");
        assertRecordsMatchWarnings(result);
        assertEquals(3, result.unsatisfiedConstraints().size());
        HashSet<String> paths = new HashSet<>();
        for (int i = 1; i <= 3; i++) {
            UnsatisfiedConstraint failure = result.unsatisfiedConstraints().get(i - 1);
            assertEquals("answer", failure.field());
            assertEquals(atEnd, failure.atEndOfForm());
            assertTrue(failure.path().contains("/row[" + i + "]/"), failure.path());
            assertTrue(paths.add(failure.path()), "Different repeat instances must have different paths");
            assertEquals(Map.of("gate", "1", "target", Integer.toString(70 + i)), failure.references());
            assertEquals(List.of("gate", "target"), new ArrayList<>(failure.references().keySet()));
            assertEquals("${gate}=0 or ((.>=${target}) and (.<=60))", failure.constraint());
            assertEquals("", value(result, "row[" + i + "]/answer"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void repeatedRelativeReferencesUseTheQuestionInstanceAndRemainRelativeInTheConstraint(boolean atEnd) throws Exception {
        Submission result = repeated(3, atEnd, "/data/gate=0 or ((.>=../target) and (.<=60))");
        assertEquals(3, result.unsatisfiedConstraints().size());
        for (int i = 1; i <= 3; i++) {
            UnsatisfiedConstraint failure = result.unsatisfiedConstraints().get(i - 1);
            assertEquals(Map.of("gate", "1", "target", Integer.toString(70 + i)), failure.references());
            assertEquals("${gate}=0 or ((.>=../target) and (.<=60))", failure.constraint());
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void zeroCountHasNoFailureAndOneCountHasExactlyOne(int size) throws Exception {
        Submission result = repeated(size, false, "(.>=/data/row/target) and (.<=60)");
        assertEquals(size, count(result, "row"));
        assertEquals(size, result.unsatisfiedConstraints().size());
        assertRecordsMatchWarnings(result);
    }

    @Test void irrelevantRepeatedQuestionsAreNotRecorded() throws Exception {
        String xml = resource("amendment8/repeats.xml").replace("__SIZE__", "3")
                .replace("__CONSTRAINT__", constraint("false()") + " relevant=\"../target != 72\"")
                .replace("__BEFORE__", input("gate")).replace("__AFTER__", "");
        Submission result = run(loadXml(xml), gateAnd("\"value\":5"));
        assertRecordsMatchWarnings(result);
        assertEquals(2, result.unsatisfiedConstraints().size());
        assertTrue(result.unsatisfiedConstraints().get(0).path().contains("/row[1]/"));
        assertTrue(result.unsatisfiedConstraints().get(1).path().contains("/row[3]/"));
    }

    @Test void aReferenceFromOutsideARepeatJoinsAllNodeTextsInDocumentOrder() throws Exception {
        LoadedForm form = form("<size>3</size><row jr:template=\"\"><target/></row><answer/>",
                bind("size", "int", "") + bind("row/target", "int", "calculate=\"4 - position(..)\"")
                + bind("answer", "int", constraint("sum(/data/row/target) < 0")),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\"><input ref=\"/data/row/target\"><label>Target</label></input></repeat>"
                + input("answer"));
        Submission result = walk8(form);
        assertEquals(3, count(result, "row"));
        UnsatisfiedConstraint failure = onlyRecord(result);
        assertEquals(Map.of("target", "3 2 1"), failure.references());
        assertEquals("sum(${target}) < 0", failure.constraint());
    }

    @Test void nestedRepeatsDisambiguateBothIndicesAndCaptureTheSameParentAndChild() throws Exception {
        Submission result = walk8(fixture8("nested-repeats.xml"));
        assertRecordsMatchWarnings(result);
        assertEquals(4, result.unsatisfiedConstraints().size());
        HashSet<String> paths = new HashSet<>();
        int index = 0;
        for (int outer = 1; outer <= 2; outer++) {
            for (int inner = 1; inner <= 2; inner++) {
                UnsatisfiedConstraint failure = result.unsatisfiedConstraints().get(index++);
                assertFalse(failure.atEndOfForm());
                assertTrue(failure.path().contains("/family[" + outer + "]/"), failure.path());
                assertTrue(failure.path().contains("/member[" + inner + "]/"), failure.path());
                assertTrue(paths.add(failure.path()));
                assertEquals(List.of("parent", "target"), new ArrayList<>(failure.references().keySet()));
                assertEquals(Map.of("parent", Integer.toString(outer), "target", Integer.toString(70 + inner)), failure.references());
            }
        }
    }
}
