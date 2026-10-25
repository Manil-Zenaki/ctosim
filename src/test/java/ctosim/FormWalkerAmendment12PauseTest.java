package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static ctosim.FormWalkerAmendment12Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change the JVM default time zone")
class FormWalkerAmendment12PauseTest {
    @Test void finalDurationExcludesThePauseWhileFinalisationIncludesIt() throws Exception {
        var run = pause(timeline(5, "90.1"), pausedSettings(""));
        Submission s = run.submission();
        totals(s, 455);
        assertTrue(seconds(s) > 455, "The interruption must appear in elapsed wall time");
        assertEquals(s.end(), wall(s, 5));
        assertEquals(5, s.answered());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void calculateHereSeesCumulativeScreenTimeOnBothSidesOfThePause() throws Exception {
        Submission s = pause(timeline(10, "90"), pausedSettings("")).submission();
        long previousGap = 0;
        int jumps = 0;
        boolean sawPositiveScreenTimeBeforePause = false;
        for (int i = 0; i <= 10; i++) {
            assertEquals(i * 90L, integer(s, "s" + i), "Snapshots must not be refreshed at finalisation");
            long gap = offScreen(s, i);
            assertTrue(gap >= previousGap, "Clock and screen time cannot run backwards");
            if (gap > previousGap) jumps++;
            if (i > 0 && gap == 0) sawPositiveScreenTimeBeforePause = true;
            previousGap = gap;
        }
        assertTrue(sawPositiveScreenTimeBeforePause, "The 0.2..0.8 interruption fraction leaves pre-pause work");
        assertEquals(1, jumps, "Only the requested pause separates clock time from screen time");
        assertTrue(previousGap > 0);
        assertEquals(seconds(s) - 900, previousGap);
    }

    @Test void endMetadataAndUploadFollowTheResumedClock() throws Exception {
        var run = pause(timeline(5, "90"), pausedSettings(""));
        Submission s = run.submission();
        Submission clean = run.withoutPause();
        assertTrue(s.end().toLocalDate().isAfter(s.start().toLocalDate()),
                "A sole target resumes on its next working day");
        metadata(s);
        assertEquals(Duration.between(clean.end(), clean.submitted()), Duration.between(s.end(), s.submitted()),
                "The seed and submission number still determine the upload delay");
        assertEquals(clean.start(), s.start());
        assertEquals(seconds(s) - seconds(clean), Duration.between(clean.end(), s.end()).getSeconds());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void datesTimesAndNowFollowClockAdvancesAndPauses(boolean paused) throws Exception {
        // Twelve initial 90-second questions force a pause before the last three
        // questions: even the latest threshold is .8 * (15 * 90) = 1080 seconds.
        StringBuilder nodes = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            nodes.append("<q").append(i).append("/>");
            binds.append(bind("q" + i, "int", "ct:minimum-seconds=\"90\""));
            body.append(input("q" + i));
        }
        nodes.append("<w12/><s12/><date_answer/><time_answer/><datetime_answer/><w15/><s15/>");
        binds.append(wallBind("w12")).append(screenBind("s12"))
                .append(bind("date_answer", "date", "ct:minimum-seconds=\"90\""))
                .append(bind("time_answer", "time", "ct:minimum-seconds=\"90\""))
                .append(bind("datetime_answer", "dateTime", "ct:minimum-seconds=\"90\""))
                .append(wallBind("w15")).append(screenBind("s15"));
        body.append(input("w12")).append(input("s12")).append(input("date_answer"))
                .append(input("time_answer")).append(input("datetime_answer"))
                .append(input("w15")).append(input("s15"));
        String fields = "\"fields\":{"
                + "\"date_answer\":{\"expr\":\"format-date-time(now(), '%Y-%m-%d')\"},"
                + "\"time_answer\":{\"expr\":\"format-date-time(now(), '%H:%M:%S')\"},"
                + "\"datetime_answer\":{\"expr\":\"format-date-time(now(), '%Y-%m-%dT%H:%M:%S')\"}}";
        var form = fixture(nodes.toString(), binds.toString(), body.toString());
        Submission s = paused ? pause(form, pausedSettings(fields)).submission()
                : new FormWalker(form, settings(fields)).walk(1);
        LocalDateTime resumed = wall(s, 12);
        if (paused) assertTrue(resumed.toLocalDate().isAfter(s.start().toLocalDate()));
        assertEquals(resumed.toLocalDate().toString(), value(s, "date_answer"));
        // The engine may add milliseconds and the configured offset to a time.
        String time = value(s, "time_answer");
        assertTrue(time.startsWith(resumed.plusSeconds(90).format(DateTimeFormatter.ofPattern("HH:mm:ss"))), time);
        assertEquals(resumed.plusSeconds(180), OffsetDateTime.parse(value(s, "datetime_answer")).toLocalDateTime());
        assertEquals(resumed.plusSeconds(270), wall(s, 15));
        assertEquals(1080, integer(s, "s12"));
        totals(s, 1350);
    }

    @ParameterizedTest @ValueSource(strings = {"blank", "constraint", "conversion"})
    void pauseDoesNotTurnFailedOrBlankHandlingIntoExtraScreenTime(String outcome) throws Exception {
        StringBuilder nodes = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        StringBuilder fields = new StringBuilder("\"fields\":{");
        for (int i = 1; i <= 5; i++) {
            String q = "q" + i;
            nodes.append('<').append(q).append("/>");
            binds.append(bind(q, "int", "ct:minimum-seconds=\"90\""
                    + (outcome.equals("constraint") ? " constraint=\"false()\"" : "")));
            body.append(input(q));
            if (i > 1) fields.append(',');
            fields.append('"').append(q).append("\":{")
                    .append(outcome.equals("blank") ? "\"missingRate\":1"
                            : "\"value\":" + (outcome.equals("conversion") ? "\"bad\"" : "7"))
                    .append('}');
        }
        fields.append('}');
        Submission s = pause(fixture(nodes.toString(), binds.toString(), body.toString()),
                pausedSettings(fields.toString())).submission();
        totals(s, 450);
        assertTrue(seconds(s) > 450);
        assertEquals(0, s.answered());
        for (int i = 1; i <= 5; i++) assertEquals("", value(s, "q" + i));
    }

    @Test void directWalksDoNotPauseJustBecauseTheConfigurationRequestsLeftOpenProblems() throws Exception {
        var form = timeline(5, "90");
        var config = pausedSettings("");
        var walker = new FormWalker(form, config);
        Submission ordinary = walker.walk(1);
        Submission visit = walker.walk(1, new Visit(1, LocalDateTime.of(2026, 3, 2, 23, 59, 30), 0, 0, null));
        for (Submission s : new Submission[]{ordinary, visit}) {
            totals(s, 450);
            assertEquals(450, seconds(s), "Only the fieldwork plan may request a pause");
            for (int i = 0; i <= 5; i++) assertEquals(0, offScreen(s, i));
        }
    }

    @Test void durationInAnOverrideConditionAndExpressionAlsoExcludesThePause() throws Exception {
        var config = pausedSettings("\"fields\":{\"q5\":{\"value\":-1,\"when\":["
                + "{\"if\":\"duration() = 360\",\"expr\":\"duration()\"}]}}");
        Submission s = pause(timeline(5, "90"), config).submission();
        assertEquals(360, integer(s, "q5"), "Rules and expressions read the same screen clock as calculate_here");
        totals(s, 450);
        assertTrue(seconds(s) > 450);
    }

    @Test void pauseInsideARepeatDoesNotResetOrInflateItsScreenCounter() throws Exception {
        var form = fixture("<count>5</count><row jr:template=\"\"><q/><snapshot/><final/></row>",
                bind("count", "int", "") + bind("row/q", "int", "ct:minimum-seconds=\"90\"")
                + screenBind("row/snapshot") + bind("row/final", "int", "calculate=\"duration()\""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/count\">"
                + input("row/q") + input("row/snapshot") + "</repeat>");
        Submission s = pause(form, pausedSettings("")).submission();
        assertEquals(5, count(s, "row"));
        for (int i = 1; i <= 5; i++) {
            assertEquals(i * 90L, integer(s, "row[" + i + "]/snapshot"));
            assertEquals(450, integer(s, "row[" + i + "]/final"));
        }
        totals(s, 450);
        assertTrue(seconds(s) > 450);
    }

    @Test void repeatedPlansAndIndependentLoadsReplayScreenAndWallTimeWithoutLeakingAPause() throws Exception {
        var config = pausedSettings("");
        var run = pause(timeline(5, "90"), config);
        Submission first = run.submission();
        Submission replay = run.plan().walkAll().get(0);
        Submission fresh = pause(timeline(5, "90"), config).submission();
        assertSameSubmission(first, replay);
        assertSameSubmission(first, fresh);
        assertEquals(first.submitted(), replay.submitted());
        assertEquals(first.submitted(), fresh.submitted());
        assertEquals(0, integer(replay, "s0"));
        Submission ordinary = new FormWalker(run.form(), config.withProblems(null)).walk(1);
        totals(ordinary, 450);
        assertEquals(450, seconds(ordinary));
    }
}
