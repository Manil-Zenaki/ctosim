package ctosim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment10Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment10ContractTest {
    @ParameterizedTest @ValueSource(strings = {"", "not XML", "<data><answer/></data>"})
    void manuallyBuiltSubmissionsHaveAnUnmodifiableEmptyErrorList(String xml) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 5, 8, 0);
        Submission result = Submission.of(9, xml, time, time);
        assertTrue(result.formErrors().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.formErrors().add(
                new FormError(9, "answer", "/data/answer", "diagnostic")));
    }

    @Test void successfulWalkHasAnUnmodifiableEmptyErrorList() throws Exception {
        Submission result = walk10(single("string", ""));
        assertTrue(result.formErrors().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.formErrors().add(
                new FormError(27, "answer", "/data/answer", "diagnostic")));
    }

    @Test void nonemptyErrorListRejectsMutationThroughEveryListView() throws Exception {
        List<FormError> errors = walk10(failingQuestion("", "")).formErrors();
        assertEquals(1, errors.size());
        FormError error = errors.get(0);
        assertThrows(UnsupportedOperationException.class, () -> errors.add(error));
        assertThrows(UnsupportedOperationException.class, () -> errors.set(0, error));
        assertThrows(UnsupportedOperationException.class, () -> errors.remove(0));
        assertThrows(UnsupportedOperationException.class, errors::clear);
        assertThrows(UnsupportedOperationException.class, () -> errors.subList(0, 1).clear());
        var iterator = errors.listIterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertThrows(UnsupportedOperationException.class, () -> iterator.set(error));
    }

    @Test void errorRecordsReplayAcrossFreshLoadsAndInterleavedWalksWithoutMutatingEarlierResults() throws Exception {
        SimulationConfig cfg = config10("");
        FormWalker walker = new FormWalker(multipleFailures(), cfg);
        Submission first = walker.walk(41);
        List<FormError> snapshot = new ArrayList<>(first.formErrors());
        assertEquals(2, snapshot.size());
        Submission other = walker.walk(Integer.MAX_VALUE);
        assertEquals(List.of("second", "first"), fields(other));
        assertDiagnostics(other);
        assertEquals(snapshot, first.formErrors());
        Submission replay = walker.walk(41);
        assertSameSubmission(first, replay);
        assertEquals(first.submitted(), replay.submitted());
        assertEquals(snapshot, replay.formErrors());
        Submission fresh = new FormWalker(multipleFailures(), cfg).walk(41);
        assertSameSubmission(first, fresh);
        assertEquals(snapshot, fresh.formErrors());
    }

    @ParameterizedTest @ValueSource(strings = {"false()", ". = 'accepted'"})
    void ordinaryRejectedConstraintsAndSuccessfulSearchAreNotEngineErrors(String constraint) throws Exception {
        Submission result = walk10(single("string", attr("constraint", constraint)));
        assertTrue(result.formErrors().isEmpty());
        if (constraint.equals("false()")) assertEquals(1, result.unsatisfiedConstraints().size());
        else assertEquals("accepted", value(result, "answer"));
    }

    @Test void conversionFailureIsNotAnEngineExpressionError() throws Exception {
        Submission result = walk10(single("int", ""));
        assertEquals("", value(result, "answer"));
        assertWarning(result, "answer", "candidate");
        assertTrue(result.formErrors().isEmpty());
    }

    @Test void skippedIrrelevantAndReadonlyConstraintsDoNotProduceErrors() throws Exception {
        Submission result = walk10(errorForm("<hidden/><note>kept</note><after/>",
                bind("hidden", "string", "relevant=\"false()\" " + attr("constraint", BAD_CONSTRAINT))
                        + bind("note", "string", "readonly=\"true()\" " + attr("constraint", BAD_CONSTRAINT))
                        + bind("after", "string", ""), input("hidden") + input("note") + input("after")));
        assertTrue(result.formErrors().isEmpty());
        assertEquals("kept", value(result, "note"));
        assertEquals("continued", value(result, "after"));
    }

    @Test void configurationExpressionErrorsKeepTheirOwnDiagnostics() throws Exception {
        Submission result = new FormWalker(single("string", ""), config10(
                "\"fields\":{\"answer\":{\"expr\":\"${missing_field}\"}}" )).walk(7);
        assertWarning(result, "answer", "missing_field");
        assertTrue(result.formErrors().isEmpty(), "A configuration expression is not a form expression");
    }

    @Test void invalidSubmissionNumberStillThrowsInsteadOfReturningAFormError() throws Exception {
        FormWalker walker = new FormWalker(failingQuestion("", ""), config10(""));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(0));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(-1));
    }

    @Test void navigationLimitStillThrowsInsteadOfBeingReportedAsAFormError() throws Exception {
        FormWalker walker = new FormWalker(repeat(false, false), config10(
                "\"fields\":{\"row\":{\"min\":200001,\"max\":200001}}"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> walker.walk(731));
        assertTrue(error.getMessage().contains("731"));
        assertTrue(error.getMessage().contains("/data/row"));
    }

    @Test void recoveredErrorsRestoreTheCallersDefaultTimezone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone caller = TimeZone.getTimeZone("GMT-07:00");
        try {
            TimeZone.setDefault(caller);
            Submission result = new FormWalker(failingQuestion("", ""), config10("\"timeZone\":\"+03:00\"" )).walk(7);
            onlyError(result, "answer");
            assertEquals(caller, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
