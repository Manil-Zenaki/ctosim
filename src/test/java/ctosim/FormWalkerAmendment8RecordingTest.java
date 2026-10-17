package ctosim;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
class FormWalkerAmendment8RecordingTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void navigationExhaustionRecordsOneFailureEvenWhenRequiredAddsAnotherWarning(boolean required) throws Exception {
        Submission result = walk8(single("int", constraint("false()") + " required=\"" + required + "()\""));
        UnsatisfiedConstraint failure = onlyRecord(result);
        assertEquals("answer", failure.field());
        assertEquals("false()", failure.constraint());
        assertFalse(failure.atEndOfForm());
        assertEquals(Map.of(), failure.references());
        assertEquals("", value(result, "answer"));
        assertEquals(required ? 2 : 1, result.warnings().size());
        assertWarning(result, "answer", "constraint");
        if (required) assertWarning(result, "answer", "required");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalExhaustionRecordsTheFinalConstraintAndReferenceValue(boolean required) throws Exception {
        Submission result = run(late("", "int", "false()", "required=\"" + required + "()\""),
                gateAnd("\"value\":5"));
        UnsatisfiedConstraint failure = onlyRecord(result);
        assertEquals("answer", failure.field());
        assertTrue(failure.atEndOfForm());
        assertTrue(failure.constraint().contains("${gate}"));
        assertEquals(Map.of("gate", "1"), failure.references());
        assertEquals("", value(result, "answer"));
        assertFinalFailure(result, "answer", required);
    }

    @Test void invalidKeptDefaultIsRecordedAsANavigationFailure() throws Exception {
        Submission result = walk8(form("<answer>4</answer>", bind("answer", "int", constraint("false()")), input("answer")));
        assertFalse(onlyRecord(result).atEndOfForm());
        assertEquals("", value(result, "answer"));
    }

    @Test void aGroupedUnicodeQuestionRecordsItsLeafNameAndWarningPath() throws Exception {
        LoadedForm form = form("<group><réponse/></group>", bind("group/réponse", "int", constraint("false()")),
                "<group ref=\"/data/group\"><label>Group</label>" + input("group/réponse") + "</group>");
        UnsatisfiedConstraint failure = onlyRecord(walk8(form));
        assertEquals("réponse", failure.field());
        assertTrue(failure.path().contains("group"));
        assertTrue(failure.path().contains("réponse"));
    }

    @Test void keptDefaultInvalidatedLaterIsRecordedAsAFinalFailure() throws Exception {
        Submission result = run(late("4", "int", "false()", ""), gateAnd(""));
        assertTrue(onlyRecord(result).atEndOfForm());
        assertEquals(1, result.answered());
    }

    @Test void recordsFollowWarningOccurrenceAcrossBothPhasesAndUnrelatedWarnings() throws Exception {
        Submission result = new FormWalker(fixture8("mixed-failures.xml"), settings(
                "\"fields\":{\"gate\":{\"value\":1},\"conversion\":{\"value\":\"not-an-int\"}}" )).walk(31);
        assertRecordsMatchWarnings(result);
        assertEquals(List.of("first", "second", "lateB", "lateA"),
                result.unsatisfiedConstraints().stream().map(UnsatisfiedConstraint::field).toList());
        assertEquals(List.of(false, false, true, true),
                result.unsatisfiedConstraints().stream().map(UnsatisfiedConstraint::atEndOfForm).toList());
        assertWarning(result, "conversion", "not-an-int");
        assertWarning(result, "first", "required");
        assertWarning(result, "lateB", "required");
    }

    @Test void aFailureWhoseReferenceWasClearedOnThePreviousPassIsRecordedOnlyOnce() throws Exception {
        Submission result = run(form("<earlier>5</earlier><later>5</later><gate>0</gate>",
                bind("earlier", "int", constraint(". = /data/later"))
                        + bind("later", "int", constraint("/data/gate = 0")) + bind("gate", "int", ""),
                input("earlier") + input("later") + input("gate")), gateAnd(""));
        assertRecordsMatchWarnings(result);
        assertEquals(List.of("later", "earlier"), result.unsatisfiedConstraints().stream().map(UnsatisfiedConstraint::field).toList());
        assertEquals(Map.of("gate", "1"), result.unsatisfiedConstraints().get(0).references());
        assertEquals(Map.of("later", ""), result.unsatisfiedConstraints().get(1).references());
        assertTrue(result.unsatisfiedConstraints().stream().allMatch(UnsatisfiedConstraint::atEndOfForm));
    }

    @Test void clearingAfterTheFivePassLimitAlsoCreatesRecords() throws Exception {
        StringBuilder instance = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            String name = "link" + i;
            String next = i == 7 ? "gate" : "link" + (i + 1);
            instance.append('<').append(name).append(">0</").append(name).append('>');
            binds.append(bind(name, "int", constraint(". = /data/" + next)));
            body.append(input(name));
        }
        instance.append("<gate>0</gate>");
        binds.append(bind("gate", "int", ""));
        body.append(input("gate"));
        Submission result = run(form(instance.toString(), binds.toString(), body.toString()),
                "\"integerRange\":{\"min\":1,\"max\":1}," + gateAnd(""));
        assertRecordsMatchWarnings(result);
        assertEquals(List.of("link2", "link1"), result.unsatisfiedConstraints().stream().map(UnsatisfiedConstraint::field).toList());
        assertTrue(result.unsatisfiedConstraints().stream().allMatch(UnsatisfiedConstraint::atEndOfForm));
        assertEquals(Map.of("link3", "1"), result.unsatisfiedConstraints().get(0).references());
        assertEquals(Map.of("link2", ""), result.unsatisfiedConstraints().get(1).references());
    }

    @ParameterizedTest @ValueSource(strings = {"", "not XML", "<data><answer/></data>"})
    void submissionsBuiltWithOfHaveAnUnmodifiableEmptyFailureList(String xml) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 5, 8, 0);
        Submission result = Submission.of(9, xml, time, time);
        assertTrue(result.unsatisfiedConstraints().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.unsatisfiedConstraints().add(
                new UnsatisfiedConstraint(9, "answer", "/data/answer", "false()", false, Map.of())));
    }

    @Test void successfulWalkHasAnUnmodifiableEmptyFailureList() throws Exception {
        Submission result = walk8(single("int", constraint("true()")));
        assertFalse(value(result, "answer").isEmpty());
        assertTrue(result.unsatisfiedConstraints().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.unsatisfiedConstraints().add(
                new UnsatisfiedConstraint(23, "answer", "/data/answer", "false()", false, Map.of())));
    }

    @Test void nonemptyFailureListRejectsDirectAndIteratorMutation() throws Exception {
        List<UnsatisfiedConstraint> records = walk8(single("int", constraint("false()"))).unsatisfiedConstraints();
        assertEquals(1, records.size());
        UnsatisfiedConstraint record = records.get(0);
        assertThrows(UnsupportedOperationException.class, () -> records.add(record));
        assertThrows(UnsupportedOperationException.class, () -> records.set(0, record));
        assertThrows(UnsupportedOperationException.class, () -> records.remove(0));
        assertThrows(UnsupportedOperationException.class, records::clear);
        var iterator = records.iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    @Test void failureRecordsReplayAndPreviousSubmissionsRemainUnchangedAcrossWalks() throws Exception {
        SimulationConfig cfg = settings("\"fields\":{\"gate\":{\"value\":1},\"conversion\":{\"value\":\"bad\"}}");
        FormWalker walker = new FormWalker(fixture8("mixed-failures.xml"), cfg);
        Submission first = walker.walk(17);
        List<UnsatisfiedConstraint> saved = new ArrayList<>(first.unsatisfiedConstraints());
        assertEquals(4, saved.size());
        Submission other = walker.walk(Integer.MAX_VALUE);
        assertTrue(other.unsatisfiedConstraints().stream().allMatch(r -> r.submission() == Integer.MAX_VALUE));
        assertEquals(saved, first.unsatisfiedConstraints());
        assertEquals(saved, walker.walk(17).unsatisfiedConstraints());
        assertEquals(saved, new FormWalker(fixture8("mixed-failures.xml"), cfg).walk(17).unsatisfiedConstraints());
    }

    @ParameterizedTest @ValueSource(strings = {"false()", ". * 2 = 20002"})
    void exhaustedFiniteSearchIsRecordedEvenIfAnAnswerExistsOutsideItsLevels(String rule) throws Exception {
        Submission result = run(single("int", constraint(rule)), "\"integerRange\":{\"min\":0,\"max\":0},"
                + "\"fields\":{\"answer\":{\"value\":0}}");
        assertEquals(rule, onlyRecord(result).constraint());
        assertFalse(result.unsatisfiedConstraints().get(0).atEndOfForm());
    }
}
