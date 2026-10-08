package ctosim;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionRandomTest {
    @Test void everyRandomCalculationIsFiniteAndInTheHalfOpenUnitInterval() throws Exception {
        var form = calculation("decimal", "if(/data/tick >= 0, random(), 0)");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertRandom(session, "result");
        for (int i = 1; i <= 1_024; i++) {
            tick(session, i);
            assertRandom(session, "result");
        }
    }

    @Test void recalculationDrawsFromASequenceRatherThanRepeatingOneRandomNumber() throws Exception {
        var session = calculation("decimal", "if(/data/tick >= 0, random(), 0)")
                .newSimulatedSession(new SimulatedClock(START), SEED);
        Set<Object> draws = new HashSet<>();
        draws.add(value(session, "result"));
        for (int i = 1; i <= 128; i++) {
            tick(session, i);
            draws.add(value(session, "result"));
        }
        assertTrue(draws.size() > 1, "random() must draw again when recalculated");
    }

    @Test void everyUuidCalculationHasLowercaseVersionFourAndRfcVariantBits() throws Exception {
        var session = calculation("string", "if(/data/tick >= 0, uuid(), '')")
                .newSimulatedSession(new SimulatedClock(START), SEED);
        assertUuid(value(session, "result"));
        for (int i = 1; i <= 256; i++) {
            tick(session, i);
            assertUuid(value(session, "result"));
        }
    }

    @Test void uuidDrawsAreNotAConstantIdentifier() throws Exception {
        var session = calculation("string", "if(/data/tick >= 0, uuid(), '')")
                .newSimulatedSession(new SimulatedClock(START), SEED);
        Set<Object> identifiers = new HashSet<>();
        for (int i = 1; i <= 64; i++) {
            tick(session, i);
            identifiers.add(value(session, "result"));
        }
        assertTrue(identifiers.size() > 1);
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 4294967297L})
    void instanceIdIsUuidPrefixedAndFormattedForEverySeed(long seed) throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), seed);
        String id = assertInstanceOf(String.class, value(session, "meta/instanceID"));
        assertTrue(id.startsWith("uuid:"));
        assertEquals(41, id.length());
        assertUuid(id.substring(5));
    }

    @Test void randomAndUuidSetvalueActionsUseValidRandomValues() throws Exception {
        var form = load();
        for (long seed = 0; seed < 32; seed++) {
            var session = form.newSimulatedSession(new SimulatedClock(START), seed);
            assertRandom(session, "action_draw");
            assertUuid(value(session, "action_uuid"));
        }
    }

    @Test void onceRandomKeepsItsInitialValueThroughAnswersAdvancesAndPostProcessing() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        assertRandom(session, "sticky_draw");
        Object initial = value(session, "sticky_draw");
        for (int i = 1; i <= 32; i++) {
            clock.advance(Duration.ofSeconds(i));
            tick(session, i);
            assertEquals(initial, value(session, "sticky_draw"));
        }
        answer(session, "another answer");
        finish(session);
        assertEquals(initial, value(session, "sticky_draw"));
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void sameSeedReproducesAnEntireSequenceOfRandomNumbersAndUuids(long seed) throws Exception {
        var form = load();
        var expected = randomTrace(form.newSimulatedSession(new SimulatedClock(START), seed));
        var actual = randomTrace(form.newSimulatedSession(new SimulatedClock(START), seed));
        assertEquals(expected, actual);
    }

    @Test void seedAloneDeterminesRandomnessRegardlessOfClockDateOrAdvances() throws Exception {
        var form = load();
        var earlyClock = new SimulatedClock(START.minusYears(40));
        var lateClock = new SimulatedClock(START.plusYears(30));
        var early = form.newSimulatedSession(earlyClock, SEED);
        var late = form.newSimulatedSession(lateClock, SEED);
        assertEquals(randomValues(early), randomValues(late));
        for (int i = 1; i <= 32; i++) {
            earlyClock.advance(Duration.ofNanos(i));
            lateClock.advance(Duration.ofDays(i));
            tick(early, i);
            tick(late, i);
            assertEquals(randomValues(early), randomValues(late));
        }
    }

    @ParameterizedTest @ValueSource(longs = {1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 4294967296L})
    void differentSeedsChangeEverySpecifiedRandomSource(long otherSeed) throws Exception {
        var form = load();
        var first = form.newSimulatedSession(new SimulatedClock(START), 0);
        var other = form.newSimulatedSession(new SimulatedClock(START), otherSeed);
        for (String path : new String[]{"draw", "generated_uuid", "sticky_draw", "action_draw", "action_uuid", "meta/instanceID"}) {
            assertNotEquals(value(first, path), value(other, path), "Different seeds must affect " + path);
        }
        assertEquals(value(first, "started"), value(other, "started"));
        assertEquals(value(first, "now_value"), value(other, "now_value"));
    }

    static List<Object> randomValues(org.javarosa.form.api.FormEntryController session) {
        return List.of(value(session, "draw"), value(session, "generated_uuid"), value(session, "sticky_draw"),
                value(session, "action_draw"), value(session, "action_uuid"), value(session, "meta/instanceID"));
    }

    private static List<List<Object>> randomTrace(org.javarosa.form.api.FormEntryController session) {
        List<List<Object>> trace = new ArrayList<>();
        trace.add(randomValues(session));
        for (int i = 1; i <= 32; i++) {
            tick(session, i);
            trace.add(randomValues(session));
        }
        return trace;
    }
}
