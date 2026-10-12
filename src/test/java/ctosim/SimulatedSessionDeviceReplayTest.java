package ctosim;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionDeviceTestSupport.*;
import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionDeviceReplayTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void defaultOverloadEqualsExplicitDefaultPropertiesForTheWholeSession(boolean deviceOnly) throws Exception {
        var form = deviceOnly ? deviceForm() : load();
        var implicitClock = new SimulatedClock(START);
        var explicitClock = new SimulatedClock(START);
        var implicit = form.newSimulatedSession(implicitClock, SEED);
        var explicit = form.newSimulatedSession(explicitClock, SEED, Map.of("username", "simulated"));
        assertEquals(snapshot(implicit), snapshot(explicit));
        for (int step = 1; step <= 3; step++) {
            implicitClock.advance(Duration.ofSeconds(step));
            explicitClock.advance(Duration.ofSeconds(step));
            answer(implicit, "answer-" + step);
            answer(explicit, "answer-" + step);
            tick(implicit, step);
            tick(explicit, step);
            assertEquals(snapshot(implicit), snapshot(explicit), "Step " + step);
        }
        finish(implicit);
        finish(explicit);
        assertEquals(snapshot(implicit), snapshot(explicit));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void equalPropertyMapsProduceIdenticalInstancesAcrossLoadsAndMapIterationOrders(boolean separateLoad) throws Exception {
        var form = load();
        var otherForm = separateLoad ? load() : form;
        var supplied = properties("\u00c9quipe");
        var reverseOrder = new LinkedHashMap<String, String>();
        supplied.keySet().stream().sorted(java.util.Comparator.reverseOrder())
                .forEach(key -> reverseOrder.put(key, supplied.get(key)));
        var firstClock = new SimulatedClock(START);
        var secondClock = new SimulatedClock(START);
        var first = form.newSimulatedSession(firstClock, SEED, supplied);
        var second = otherForm.newSimulatedSession(secondClock, SEED, reverseOrder);
        assertNotSame(first.getModel().getForm().getMainInstance(), second.getModel().getForm().getMainInstance());
        assertEquals(snapshot(first), snapshot(second));
        for (int step = 1; step <= 5; step++) {
            firstClock.advance(Duration.ofSeconds(7));
            secondClock.advance(Duration.ofSeconds(7));
            tick(first, step);
            tick(second, step);
            answer(first, "\u6771\u4eac-" + step);
            answer(second, "\u6771\u4eac-" + step);
            assertEquals(snapshot(first), snapshot(second), "Step " + step);
        }
        finish(first);
        finish(second);
        assertEquals(snapshot(first), snapshot(second));
    }

    @Test void customPropertiesPreserveClockFunctionsAndMetadataLifecycle() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED, properties("clock-user"));
        assertTime(session, "started", START);
        assertFormattedTime(session, "now_value", START);
        assertTime(session, "action_now", START);
        assertEquals(START.toLocalDate(), date(session, "today_value"));
        assertEquals(START.toLocalDate(), date(session, "action_today"));
        assertEquals(0, number(session, "elapsed"));
        assertEquals(0, number(session, "action_elapsed"));
        assertNull(node(session, "finished").getValue());
        clock.advance(Duration.ofSeconds(3));
        tick(session, 1);
        assertFormattedTime(session, "now_value", START.plusSeconds(3));
        assertEquals(START.plusSeconds(3).toLocalDate(), date(session, "today_value"));
        assertEquals(3, number(session, "elapsed"));
        clock.advance(Duration.ofSeconds(7));
        finish(session);
        assertTime(session, "finished", START.plusSeconds(10));
        assertTime(session, "started", START);
        assertEquals("clock-user:username", value(session, "operator"));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void explicitPropertiesPreserveRandomRangesUuidFormatsAndOnceRetention(long seed) throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, seed, properties("random-user"));
        var sticky = value(session, "sticky_draw");
        var instanceId = assertInstanceOf(String.class, value(session, "meta/instanceID"));
        assertTrue(instanceId.matches("uuid:" + UUID_PATTERN));
        assertRandom(session, "sticky_draw");
        assertRandom(session, "action_draw");
        assertUuid(value(session, "action_uuid"));
        for (int step = 1; step <= 16; step++) {
            clock.advance(Duration.ofSeconds(1));
            tick(session, step);
            assertRandom(session, "draw");
            assertUuid(value(session, "generated_uuid"));
            assertEquals(sticky, value(session, "sticky_draw"));
            assertEquals(instanceId, value(session, "meta/instanceID"));
        }
        finish(session);
        assertEquals(sticky, value(session, "sticky_draw"));
        assertEquals(instanceId, value(session, "meta/instanceID"));
    }

    @Test void differentSeedsStillChangeRandomSourcesWhenPropertiesAreEqual() throws Exception {
        var form = load();
        var supplied = properties("same-user");
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED + 1, supplied);
        for (String field : new String[] {"draw", "generated_uuid", "sticky_draw", "action_draw", "action_uuid", "meta/instanceID"}) {
            assertNotEquals(value(first, field), value(second, field), field);
        }
        assertEquals(value(first, "operator"), value(second, "operator"));
    }

    @Test void changingOnlyPropertiesCannotChangeTheSeededRandomSequence() throws Exception {
        var form = load();
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED, properties("first"));
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of());
        for (int step = 0; step <= 12; step++) {
            if (step > 0) {
                tick(first, step);
                tick(second, step);
            }
            // This fixture's random expressions have no property-dependent branches.
            for (String field : new String[] {"draw", "generated_uuid", "sticky_draw", "action_draw", "action_uuid", "meta/instanceID"}) {
                assertEquals(value(first, field), value(second, field), field + " at step " + step);
            }
        }
    }

    @Test void interleavedPropertySessionsCannotChangeAnotherSessionsAnswersClockOrRandomness() throws Exception {
        var form = load();
        var supplied = properties("running");
        var referenceClock = new SimulatedClock(START);
        var reference = form.newSimulatedSession(referenceClock, SEED, supplied);
        List<Map<String, Object>> expected = new ArrayList<>();
        for (int step = 1; step <= 6; step++) {
            referenceClock.advance(Duration.ofSeconds(2));
            answer(reference, "running-" + step);
            tick(reference, step);
            expected.add(snapshot(reference));
        }
        var runningClock = new SimulatedClock(START);
        var running = form.newSimulatedSession(runningClock, SEED, supplied);
        for (int step = 1; step <= 6; step++) {
            var noiseClock = new SimulatedClock(START.plusYears(step));
            var noise = form.newSimulatedSession(noiseClock, SEED + step, properties("noise-" + step));
            noiseClock.advance(Duration.ofDays(step));
            answer(noise, "noise");
            tick(noise, 1);
            finish(noise);
            runningClock.advance(Duration.ofSeconds(2));
            answer(running, "running-" + step);
            tick(running, step);
            assertEquals(expected.get(step - 1), snapshot(running));
        }
    }
}
