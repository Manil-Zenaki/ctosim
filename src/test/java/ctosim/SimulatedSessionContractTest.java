package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionContractTest {
    @TempDir Path temp;

    @Test void nullClockIsRejected() throws Exception {
        var form = load();
        assertThrows(NullPointerException.class, () -> form.newSimulatedSession(null, SEED));
    }

    @Test void newSimulatedSessionIsPositionedAtTheBeginning() throws Exception {
        var form = load();
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(EVENT_BEGINNING_OF_FORM, first.getModel().getEvent());
        question(first, "answer");
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(EVENT_BEGINNING_OF_FORM, second.getModel().getEvent());
        assertEquals(EVENT_QUESTION, first.getModel().getEvent());
    }

    @Test void defaultsAndOrdinaryCalculationsAreInitializedAndRecalculateNormally() throws Exception {
        var session = load().newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals("initial", value(session, "answer"));
        assertEquals(0, value(session, "tick"));
        assertEquals(0, value(session, "doubled"));
        tick(session, 7);
        assertEquals(14, value(session, "doubled"));
    }

    @Test void simulatedSessionsHaveTheirOwnControllersInstancesAndNodes() throws Exception {
        var form = load();
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertNotSame(first, second);
        assertNotSame(first.getModel().getForm().getMainInstance(), second.getModel().getForm().getMainInstance());
        assertNotSame(node(first, "answer"), node(second, "answer"));
        assertNotSame(node(first, "meta/instanceID"), node(second, "meta/instanceID"));
    }

    @Test void propertyPreloadsAndSurveyCtoFunctionsRemainAvailable() throws Exception {
        var form = load();
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals("simulated", value(session, "operator"));
        assertEquals("simulated", value(session, "function_operator"));
        for (String field : List.of("device", "subscriber", "sim", "phone")) assertNull(value(session, field), field);
        assertEquals(Set.of("duration", "username"), form.stubbedFunctions());
    }

    @Test void surveyCtoStubsComposeWithSimulatedFunctionsWithoutCallerConfiguration() throws Exception {
        var form = calculation("string", "concat(username(), ':', pulldata('file', 'column'), ':', "
                + "phone-call-duration(), ':', plug-in-metadata(), ':', phone-call-log(), ':', unknown-probe())");
        assertEquals("simulated::0:::", value(form.newSimulatedSession(new SimulatedClock(START), SEED), "result"));
    }

    @Test void aFormWithoutTimeOrRandomFunctionsCanOpenASimulatedSession() throws Exception {
        var form = calculation("decimal", "/data/tick + 7");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals(7, number(session, "result"));
        tick(session, 3);
        assertEquals(10, number(session, "result"));
        assertTrue(form.stubbedFunctions().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void identicallyDrivenSessionsHaveIdenticalValuesAtEveryNode(boolean separateLoads) throws Exception {
        var firstForm = load();
        var secondForm = separateLoads ? load() : firstForm;
        var firstClock = new SimulatedClock(START);
        var secondClock = new SimulatedClock(START);
        var first = firstForm.newSimulatedSession(firstClock, SEED);
        var second = secondForm.newSimulatedSession(secondClock, SEED);
        assertEquals(snapshot(first), snapshot(second), "Initial instances");
        for (int i = 1; i <= 8; i++) {
            Duration advance = Duration.ofSeconds(i * 11L, 123_456_789);
            firstClock.advance(advance);
            secondClock.advance(advance);
            tick(first, i);
            tick(second, i);
            String text = i == 1 ? "" : "caf\u00e9 \u6771\u4eac \ud83c\udf0d " + i;
            answer(first, text);
            answer(second, text);
            assertEquals(snapshot(first), snapshot(second), "Instance after answer " + i);
        }
        finish(first);
        finish(second);
        assertNotNull(value(first, "finished"));
        assertEquals(snapshot(first), snapshot(second), "Post-processed instances, including metadata");
    }

    @Test void pathAndStreamLoadsOfTheSameFormProduceIdenticalSimulatedInstances() throws Exception {
        Path source = Files.writeString(temp.resolve("session.xml"), fixture("session.xml"), StandardCharsets.UTF_8);
        var pathSession = FormLoader.load(source).newSimulatedSession(new SimulatedClock(START), SEED);
        var streamSession = load().newSimulatedSession(new SimulatedClock(START), SEED);
        tick(pathSession, 4);
        tick(streamSession, 4);
        finish(pathSession);
        finish(streamSession);
        assertEquals(snapshot(pathSession), snapshot(streamSession));
    }

    @Test void sessionOperationsNeverAdvanceTheSuppliedClockImplicitly() throws Exception {
        var clock = new SimulatedClock(START);
        var session = load().newSimulatedSession(clock, SEED);
        assertEquals(START, clock.now());
        tick(session, 1);
        answer(session, "answer");
        finish(session);
        assertEquals(START, clock.now());
    }
}
