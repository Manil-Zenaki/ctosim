package ctosim;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionIsolationTest {
    @Test void answersDoNotLeakToSimulatedSessionsOpenedBeforeOrAfter() throws Exception {
        var form = load();
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var alreadyOpen = form.newSimulatedSession(new SimulatedClock(START), SEED);
        answer(first, "caf\u00e9 \u6771\u4eac");
        assertEquals("initial", value(alreadyOpen, "answer"));
        var later = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals("initial", value(later, "answer"));
        answer(alreadyOpen, "second");
        assertEquals("caf\u00e9 \u6771\u4eac", value(first, "answer"));
        assertEquals("initial", value(later, "answer"));
    }

    @Test void advancingOneClockDoesNotChangeAnotherSessionsTimeOrDuration() throws Exception {
        var form = load();
        var firstClock = new SimulatedClock(START);
        var secondClock = new SimulatedClock(START.plusYears(5));
        var first = form.newSimulatedSession(firstClock, SEED);
        var second = form.newSimulatedSession(secondClock, SEED);
        firstClock.advance(Duration.ofDays(2));
        tick(first, 1);
        tick(second, 1);
        assertFormattedTime(first, "now_value", START.plusDays(2));
        assertEquals(172_800, number(first, "elapsed"));
        assertFormattedTime(second, "now_value", START.plusYears(5));
        assertEquals(0, number(second, "elapsed"));
        secondClock.advance(Duration.ofSeconds(11));
        tick(first, 2);
        tick(second, 2);
        assertEquals(172_800, number(first, "elapsed"));
        assertEquals(11, number(second, "elapsed"));
    }

    @Test void postProcessingOneSessionLeavesOtherSessionsEndUnset() throws Exception {
        var form = load();
        var firstClock = new SimulatedClock(START);
        var secondClock = new SimulatedClock(START.plusDays(10));
        var first = form.newSimulatedSession(firstClock, SEED);
        var second = form.newSimulatedSession(secondClock, SEED);
        firstClock.advance(Duration.ofSeconds(3));
        secondClock.advance(Duration.ofSeconds(9));
        finish(first);
        assertTime(first, "finished", START.plusSeconds(3));
        assertNull(value(second, "finished"));
        finish(second);
        assertTime(second, "finished", START.plusDays(10).plusSeconds(9));
        assertTime(first, "finished", START.plusSeconds(3));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void otherSimulatedSessionsCannotConsumeOrResetARunningSessionsRandomSequence(boolean separateLoad)
            throws Exception {
        var form = load();
        var expected = trace(form.newSimulatedSession(new SimulatedClock(START), SEED));
        var running = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var noisyForm = separateLoad ? load() : form;
        assertEquals(expected.get(0), snapshot(running));
        for (int i = 1; i <= 12; i++) {
            var noisyClock = new SimulatedClock(START.plusYears(i));
            var noisy = noisyForm.newSimulatedSession(noisyClock, SEED + i);
            noisyClock.advance(Duration.ofDays(i));
            tick(noisy, 1);
            tick(noisy, 2);
            finish(noisy);
            tick(running, i);
            assertEquals(expected.get(i), snapshot(running), "Interleaved step " + i);
        }
    }

    @Test void ordinarySessionsCannotConsumeOrResetASimulatedRandomSequence() throws Exception {
        var form = load();
        var expected = trace(form.newSimulatedSession(new SimulatedClock(START), SEED));
        var running = form.newSimulatedSession(new SimulatedClock(START), SEED);
        for (int i = 1; i <= 12; i++) {
            var ordinary = form.newEntryController();
            tick(ordinary, i);
            finish(ordinary);
            tick(running, i);
            assertEquals(expected.get(i), snapshot(running));
        }
    }

    private static List<Map<String, Object>> trace(FormEntryController session) {
        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(snapshot(session));
        for (int i = 1; i <= 12; i++) {
            tick(session, i);
            trace.add(snapshot(session));
        }
        return trace;
    }

    @Test void ordinarySessionsOpenedBeforeAndAfterSimulationKeepNativeTimeAndDuration() throws Exception {
        var form = load();
        var before = openOrdinaryAndCheckTime(form);
        var simulatedClock = new SimulatedClock(START);
        var simulated = form.newSimulatedSession(simulatedClock, SEED);
        simulatedClock.advance(Duration.ofDays(50));
        tick(simulated, 1);
        var after = openOrdinaryAndCheckTime(form);
        for (var ordinary : List.of(before, after)) {
            long lower = System.currentTimeMillis();
            LocalDate firstDate = LocalDate.now();
            tick(ordinary, 1);
            finish(ordinary);
            LocalDate lastDate = LocalDate.now();
            long upper = System.currentTimeMillis();
            assertCurrentFormattedTime(ordinary, "now_value", lower, upper);
            assertCurrentTime(ordinary, "finished", lower, upper);
            assertTrue(date(ordinary, "today_value").equals(firstDate) || date(ordinary, "today_value").equals(lastDate));
            assertEquals(0, number(ordinary, "elapsed"));
        }
        assertFormattedTime(simulated, "now_value", START.plusDays(50));
        assertEquals(4_320_000, number(simulated, "elapsed"));
    }

    private static FormEntryController openOrdinaryAndCheckTime(LoadedForm form) {
        long lower = System.currentTimeMillis();
        var session = form.newEntryController();
        long upper = System.currentTimeMillis();
        assertCurrentTime(session, "started", lower, upper);
        assertCurrentFormattedTime(session, "now_value", lower, upper);
        assertCurrentTime(session, "action_now", lower, upper);
        assertNull(value(session, "finished"));
        assertEquals(0, number(session, "elapsed"));
        return session;
    }

    private static void assertCurrentTime(FormEntryController session, String field, long lower, long upper) {
        long actual = assertInstanceOf(Date.class, value(session, field)).getTime();
        assertTrue(actual >= lower - 1_000 && actual <= upper + 1_000,
                field + " must retain native wall-clock behavior");
    }

    private static void assertCurrentFormattedTime(FormEntryController session, String field, long lower, long upper) {
        String actual = assertInstanceOf(String.class, value(session, field));
        LocalDateTime.parse(actual, NOW_FORMAT); // Reject malformed or date-only values.
        // Compare formatted instants to avoid guessing an offset during a DST overlap.
        for (long second = Math.floorDiv(lower - 1_000, 1_000);
                second <= Math.floorDiv(upper + 1_000, 1_000); second++) {
            String candidate = Instant.ofEpochSecond(second).atZone(ZoneId.systemDefault()).format(NOW_FORMAT);
            if (candidate.equals(actual)) return;
        }
        fail(field + " must retain native wall-clock behavior: " + actual);
    }

    @Test void openingSimulationsDoesNotResetNativeRandomnessToTheirSeed() throws Exception {
        var form = load();
        var draws = new HashSet<>();
        var uuids = new HashSet<>();
        var ids = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            form.newSimulatedSession(new SimulatedClock(START), SEED);
            var ordinary = form.newEntryController();
            assertRandom(ordinary, "draw");
            draws.add(value(ordinary, "draw"));
            uuids.add(value(ordinary, "generated_uuid"));
            String id = assertInstanceOf(String.class, value(ordinary, "meta/instanceID"));
            assertFalse(id.isBlank());
            ids.add(id);
        }
        assertTrue(draws.size() > 1, "Native random() must not be reset to the simulation seed");
        assertTrue(uuids.size() > 1, "Native uuid() must not be reset to the simulation seed");
        assertTrue(ids.size() > 1, "Native uid preloads must not be reset to the simulation seed");
    }

    @Test void ordinaryAndSimulatedAnswersRemainIndependentInBothDirections() throws Exception {
        var form = load();
        var ordinary = form.newEntryController();
        var simulated = form.newSimulatedSession(new SimulatedClock(START), SEED);
        answer(ordinary, "ordinary");
        assertEquals("initial", value(simulated, "answer"));
        answer(simulated, "simulated");
        assertEquals("ordinary", value(ordinary, "answer"));
        assertEquals("initial", value(form.newEntryController(), "answer"));
        assertEquals("initial", value(form.newSimulatedSession(new SimulatedClock(START), SEED), "answer"));
    }

    @Test void manyRetainedSessionsKeepTheirOwnInstancesAndClockOrigins() throws Exception {
        var form = load();
        List<FormEntryController> sessions = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            var clock = new SimulatedClock(START.plusDays(i));
            var session = form.newSimulatedSession(clock, SEED + i);
            clock.advance(Duration.ofSeconds(i));
            tick(session, i + 1);
            answer(session, "session-" + i);
            sessions.add(session);
        }
        for (int i = 0; i < sessions.size(); i++) {
            var session = sessions.get(i);
            assertEquals("session-" + i, value(session, "answer"));
            assertEquals(i, number(session, "elapsed"));
            assertTime(session, "started", START.plusDays(i));
            assertFormattedTime(session, "now_value", START.plusDays(i).plusSeconds(i));
        }
    }
}
