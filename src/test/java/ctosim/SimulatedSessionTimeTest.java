package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("java.util.TimeZone.default")
class SimulatedSessionTimeTest {
    @Test void calculationsReadTheClockBeforeAnyNavigation() throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(START.toLocalDate(), date(session, "today_value"));
        assertTime(session, "now_value", START);
        assertEquals(0, number(session, "elapsed"));
    }

    @Test void firstLoadSetvalueActionsReadTheSimulatedClock() throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(START.toLocalDate(), date(session, "action_today"));
        assertTime(session, "action_now", START);
        assertEquals(0, number(session, "action_elapsed"));
    }

    @Test void startTimestampUsesTheClockAtSessionOpening() throws Exception {
        var clock = new SimulatedClock(START);
        clock.advance(Duration.ofDays(12));
        var session = load().newSimulatedSession(clock, SEED);
        assertTime(session, "started", START.plusDays(12));
    }

    @Test void endIsUnsetBeforePostProcessingEvenAfterClockAdvancesAndAnswers() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        assertNull(node(session, "finished").getValue());
        clock.advance(Duration.ofHours(1));
        tick(session, 1);
        answer(session, "finished answering");
        assertNull(node(session, "finished").getValue());
    }

    @Test void postProcessingWithoutAnAdvanceSetsEndEqualToStart() throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), SEED);
        finish(session);
        assertTime(session, "finished", START);
    }

    @Test void endReadsTheClockAtPostProcessingEvenWithoutRecalculation() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        clock.advance(Duration.ofDays(3).plusSeconds(17));
        finish(session);
        assertTime(session, "finished", START.plusDays(3).plusSeconds(17));
    }

    @Test void answeringAndPostProcessingPreserveStartAndInstanceId() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        Object id = value(session, "meta/instanceID");
        clock.advance(Duration.ofDays(3));
        tick(session, 1);
        answer(session, "caf\u00e9 \u6771\u4eac \ud83c\udf0d");
        finish(session);
        assertTime(session, "started", START);
        assertEquals(id, value(session, "meta/instanceID"));
    }

    @Test void recalculationAfterMidnightReadsTheNewDateAndTime() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        clock.advance(Duration.ofSeconds(3));
        tick(session, 1);
        assertEquals(START.plusSeconds(3).toLocalDate(), date(session, "today_value"));
        assertTime(session, "now_value", START.plusSeconds(3));
    }

    @Test void repeatedRecalculationWithoutAnAdvanceDoesNotUseRealTime() throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), SEED);
        for (int i = 1; i <= 20; i++) {
            tick(session, i);
            assertTime(session, "now_value", START);
            assertEquals(START.toLocalDate(), date(session, "today_value"));
            assertEquals(0, number(session, "elapsed"));
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "499999999,0", "999999999,0", "1000000000,1",
            "1000000001,1", "1999999999,1", "2000000000,2"})
    void durationFloorsElapsedNanosecondsToWholeSeconds(long nanos, long expected) throws Exception {
        var clock = new SimulatedClock(START.plusNanos(750_000_000));
        var session = calculation("decimal", "duration() + 0 * /data/tick")
                .newSimulatedSession(clock, SEED);
        clock.advance(Duration.ofNanos(nanos));
        tick(session, 1);
        assertEquals((double) expected, number(session, "result"));
    }

    @Test void durationAccumulatesSmallAdvancesBeforeRounding() throws Exception {
        var clock = new SimulatedClock(START);
        var session = calculation("decimal", "duration() + 0 * /data/tick")
                .newSimulatedSession(clock, SEED);
        for (int i = 1; i <= 10; i++) {
            clock.advance(Duration.ofMillis(150));
            tick(session, i);
            assertEquals((i * 150) / 1000, number(session, "result"));
        }
    }

    @Test void durationStartsAtSessionOpeningRatherThanClockConstruction() throws Exception {
        var clock = new SimulatedClock(START);
        clock.advance(Duration.ofDays(200));
        var session = load().newSimulatedSession(clock, SEED);
        assertEquals(0, number(session, "elapsed"));
        clock.advance(Duration.ofSeconds(7));
        tick(session, 1);
        assertEquals(7, number(session, "elapsed"));
    }

    @Test void durationSupportsMoreThanAnIntOfElapsedSeconds() throws Exception {
        var clock = new SimulatedClock(START);
        var session = calculation("decimal", "duration() + 0 * /data/tick")
                .newSimulatedSession(clock, SEED);
        long seconds = (long) Integer.MAX_VALUE + 123;
        clock.advance(Duration.ofSeconds(seconds, 999_999_999));
        tick(session, 1);
        assertEquals((double) seconds, number(session, "result"));
    }

    @ParameterizedTest
    @CsvSource({"UTC,2040-02-29T12:34:56", "Asia/Kathmandu,2040-02-29T00:15:00",
            "Pacific/Kiritimati,2040-02-29T00:15:00", "America/Los_Angeles,2040-07-01T23:45:00",
            "America/Los_Angeles,2040-01-01T23:45:00"})
    void functionsActionsAndPreloadsUseTheJvmDefaultZone(String zone, String startText) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var start = LocalDateTime.parse(startText);
            var clock = new SimulatedClock(start);
            var session = load().newSimulatedSession(clock, SEED);
            for (String path : new String[]{"now_value", "action_now", "started"}) assertTime(session, path, start);
            for (String path : new String[]{"today_value", "action_today"}) assertEquals(start.toLocalDate(), date(session, path));
            clock.advance(Duration.ofDays(1));
            tick(session, 1);
            finish(session);
            assertTime(session, "now_value", start.plusDays(1));
            assertTime(session, "finished", start.plusDays(1));
            assertEquals(start.plusDays(1).toLocalDate(), date(session, "today_value"));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"2024-03-10T01:30:00", "2024-11-03T00:30:00"})
    void durationUsesClockArithmeticAcrossDaylightSavingTransitions(String startText) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var start = LocalDateTime.parse(startText);
            var clock = new SimulatedClock(start);
            var session = load().newSimulatedSession(clock, SEED);
            clock.advance(Duration.ofHours(3));
            tick(session, 1);
            assertEquals(10_800, number(session, "elapsed"));
            assertTime(session, "now_value", start.plusHours(3));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
