package ctosim;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment10Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment10NavigationTest {
    private static LoadedForm stuckForm() throws Exception {
        return loadXml(resource("amendment10/stuck-navigation.xml"));
    }

    private static Submission cutShort() throws Exception {
        return new FormWalker(stuckForm(), config10("\"fields\":{\"gate\":{\"value\":\"go\"}},"
                + "\"speedViolationRate\":1,\"timeZone\":\"+03:00\"" )).walk(62);
    }

    @Test void aRecurringNavigationErrorCutsShortInsteadOfLoopingOrAnsweringLaterQuestions() throws Exception {
        // This fixture must fail twice at exactly the same engine navigation index.
        var session = stuckForm().newSimulatedSession(new SimulatedClock(LocalDateTime.of(2026, 1, 5, 8, 0)), 17);
        RuntimeException first = assertThrows(RuntimeException.class, () -> {
            for (int i = 0; i < 100; i++)
                if (session.stepToNextEvent() == FormEntryController.EVENT_END_OF_FORM) break;
        });
        String index = session.getModel().getFormIndex().toString();
        RuntimeException again = assertThrows(RuntimeException.class, session::stepToNextEvent);
        assertEquals(index, session.getModel().getFormIndex().toString());
        assertEquals(first.getMessage(), again.getMessage());

        Submission result = cutShort();
        assertTrue(result.formErrors().size() >= 2, "Both the initial error and the recurrence are recorded");
        assertTrue(result.formErrors().stream().allMatch(error -> error.field().equals("blocked")));
        assertDiagnostics(result);
        assertTrue(result.warnings().stream().anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("cut short")),
                result.warnings().toString());
        assertEquals("", value(result, "after"));
        for (int i = 1; i <= count(result, "blocked"); i++)
            assertEquals("", value(result, "blocked[" + i + "]/inside"));
    }

    @Test void aCutShortInterviewStillRepairsConstraintsAndFinalizesReportsAndTimestamps() throws Exception {
        Submission result = cutShort();
        assertFalse(result.formErrors().isEmpty());
        assertEquals("go", value(result, "gate"));
        assertEquals("fixed", value(result, "repair"), "Amendment 5 still runs after early termination");
        assertEquals("1", value(result, "violations"));
        assertEquals("timed", value(result, "violated"));
        assertEquals(seconds(result), Long.parseLong(value(result, "elapsed")));
        assertEquals(result.end(), OffsetDateTime.parse(value(result, "finished")).toLocalDateTime());
        assertEquals(result.start(), OffsetDateTime.parse(value(result, "started")).toLocalDateTime());
        assertFalse(result.end().isBefore(result.start()));
        long minutes = Duration.between(result.end(), result.submitted()).toMinutes();
        assertTrue(minutes >= 1 && minutes <= 240);
        assertFalse(value(result, "meta/instanceID").isEmpty());
    }

    @Test void aCutShortSubmissionCanBeReplayedAndDoesNotPoisonTheNextWalk() throws Exception {
        SimulationConfig cfg = config10("\"fields\":{\"gate\":{\"value\":\"go\"}}" );
        FormWalker walker = new FormWalker(stuckForm(), cfg);
        Submission first = walker.walk(17);
        List<FormError> errors = List.copyOf(first.formErrors());
        assertFalse(errors.isEmpty());
        Submission next = walker.walk(18);
        assertFalse(next.formErrors().isEmpty());
        assertDiagnostics(next);
        Submission replay = walker.walk(17);
        assertSameSubmission(first, replay);
        assertEquals(first.submitted(), replay.submitted());
        assertEquals(errors, replay.formErrors());
        assertEquals(errors, first.formErrors());
    }

    private static LoadedForm repeatCreationFailure(boolean countExpression) throws Exception {
        String inside = countExpression ? "child_count" : "calculated";
        String nested = countExpression ? "<child jr:template=\"\"><leaf/></child>" : "";
        return errorForm("<before/><row jr:template=\"\"><" + inside + "/>" + nested + "<next/></row><after/>",
                bind("before", "string", "") + bind("row/" + inside, countExpression ? "int" : "string",
                        attr("calculate", REPEATED)) + bind("row/next", "string", "")
                        + (countExpression ? bind("row/child/leaf", "string", "") : "") + bind("after", "string", ""),
                input("before") + "<repeat nodeset=\"/data/row\" jr:count=\"/data/two\">"
                        + (countExpression ? "<repeat nodeset=\"/data/row/child\" jr:count=\"/data/row/child_count\">"
                        + input("row/child/leaf") + "</repeat>" : "")
                        + input("row/next") + "</repeat>" + input("after"));
    }

    @Test void anExpressionErrorDuringForwardNavigationIsRecordedAndTheWalkContinues() throws Exception {
        LoadedForm form = repeatCreationFailure(false);
        assertFailsDuringNavigation(form);
        Submission result = walk10(form);
        assertEquals(List.of("calculated", "calculated"), fields(result));
        assertDiagnostics(result);
        assertIndex(result.formErrors().get(0).path(), "row", 1);
        assertIndex(result.formErrors().get(1).path(), "row", 2);
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }

    @Test void anErrorInACalculatedRepeatCountDoesNotStopTheWholeSubmission() throws Exception {
        LoadedForm form = repeatCreationFailure(true);
        assertFailsDuringNavigation(form);
        Submission result = walk10(form);
        assertEquals(List.of("child_count", "child_count"), fields(result));
        assertDiagnostics(result);
        assertEquals("continued", value(result, "after"));
        assertNotCutShort(result);
    }

    private static void assertFailsDuringNavigation(LoadedForm form) {
        // Verify the fixture exercises moving forward, not answering or initialization.
        var session = form.newSimulatedSession(new SimulatedClock(LocalDateTime.of(2026, 1, 5, 8, 0)), 17);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            for (int i = 0; i < 100; i++)
                if (session.stepToNextEvent() == FormEntryController.EVENT_END_OF_FORM) break;
        });
        assertTrue(failure.getMessage().contains("This field is repeated"), failure.getMessage());
    }
}
