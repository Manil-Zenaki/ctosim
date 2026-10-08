package ctosim;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.javarosa.form.api.FormEntryController.EVENT_BEGINNING_OF_FORM;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionPreloadTest {
    private static LoadedForm preload(String type, String preload, String parameter) throws Exception {
        return expression(type, "jr:preload=\"" + preload + "\""
                + (parameter == null ? "" : " jr:preloadParams=\"" + parameter + "\""), "");
    }

    @ParameterizedTest @CsvSource({"dateTime,timestamp,start", "dateTime,timestamp,end", "string,uid,"})
    void metadataOnlyFormsOpenInitializedWithoutTimeOrRandomFunctionReferences(
            String type, String preload, String parameter) throws Exception {
        var form = preload(type, preload, parameter);
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals(Set.of(), form.stubbedFunctions());
        if ("start".equals(parameter)) assertTime(session, "result", START);
        else if ("end".equals(parameter)) assertNull(node(session, "result").getValue());
        else {
            String identifier = assertInstanceOf(String.class, value(session, "result"));
            assertTrue(identifier.startsWith("uuid:"));
            assertUuid(identifier.substring(5));
        }
    }

    @Test void isolatedEndPreloadReadsTheClockOnlyWhenPostProcessed() throws Exception {
        var clock = new SimulatedClock(START);
        var session = preload("dateTime", "timestamp", "end").newSimulatedSession(clock, SEED);
        clock.advance(Duration.ofSeconds(29));
        assertNull(value(session, "result"));
        finish(session);
        assertTime(session, "result", START.plusSeconds(29));
    }

    @Test void uidPreloadAloneIsSeededEvenWithoutAUuidFunctionCall() throws Exception {
        var form = preload("string", "uid", null);
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var same = form.newSimulatedSession(new SimulatedClock(START.plusDays(10)), SEED);
        var different = form.newSimulatedSession(new SimulatedClock(START), SEED + 1);
        assertEquals(value(first, "result"), value(same, "result"));
        assertNotEquals(value(first, "result"), value(different, "result"));
    }
}
