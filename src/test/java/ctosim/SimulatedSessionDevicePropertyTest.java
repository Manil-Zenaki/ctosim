package ctosim;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormLoaderPreloadTestSupport.singlePreload;
import static ctosim.SimulatedSessionDeviceTestSupport.*;
import static ctosim.SimulatedSessionTestSupport.*;
import static org.javarosa.form.api.FormEntryController.EVENT_BEGINNING_OF_FORM;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionDevicePropertyTest {
    static Stream<String> propertyNames() { return PROPERTY_FIELDS.keySet().stream().sorted(); }

    @ParameterizedTest @MethodSource("propertyNames")
    void eachPropertyWorksWithoutAnyUsernameFunctionReference(String property) throws Exception {
        var form = singlePreload("property", property, "string");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of(property, "001-" + property));
        assertEquals("001-" + property, value(session, "metadata"));
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
    }

    @ParameterizedTest @MethodSource("propertyNames")
    void eachMissingPropertyHasNoAnswerEvenWhenOtherPropertiesArePresent(String property) throws Exception {
        var supplied = properties("enumerator");
        supplied.remove(property);
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        assertNull(node(session, PROPERTY_FIELDS.get(property)).getValue());
    }

    @Test void allPropertiesAreReadFromTheirOwnKeysAtInitialization() throws Exception {
        var supplied = properties("field-team");
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        assertPreloads(session, supplied);
        assertUsername(session, supplied.get("username"), false);
    }

    @Test void emptyMapDoesNotAcquireTheTwoArgumentOverloadsDefaultUsername() throws Exception {
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, Map.of());
        assertPreloads(session, Map.of());
        assertUsername(session, "", false);
        tick(session, 1);
        assertUsername(session, "", true);
        finish(session);
        assertPreloads(session, Map.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {"001234567890", "+001 (234) 050-060", "  field worker  ",
            "\u00c9lodie \u6771\u4eac \ud83d\udcf1", "O'Brien \"A&B\" <device>", "username() ${deviceid}"})
    void propertyStringsArePreservedWithoutTrimmingParsingOrExpressionEvaluation(String supplied) throws Exception {
        var properties = new HashMap<String, String>();
        PROPERTY_FIELDS.keySet().forEach(key -> properties.put(key, supplied));
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, properties);
        assertPreloads(session, properties);
        assertUsername(session, supplied, false);
        tick(session, 1);
        assertUsername(session, supplied, true);
    }

    @Test void explicitlyEmptyUsernameReturnsTheEmptyStringInCalculationsAndActions() throws Exception {
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", ""));
        assertUsername(session, "", false);
        assertEquals("[]", value(session, "preloaded_username"));
        tick(session, 1);
        assertUsername(session, "", true);
    }

    @Test void uppercaseUsernameIsADistinctPropertyAndDoesNotSupplyUsernameFunction() throws Exception {
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, Map.of("USERNAME", "case-sensitive"));
        assertPreloads(session, Map.of("USERNAME", "case-sensitive"));
        assertUsername(session, "", false);
    }

    @Test void unusedPropertiesAreAcceptedAndCannotSupplyMissingPreloads() throws Exception {
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED,
                Map.of("unused", "value", " username ", "not-the-username", "", "empty-key"));
        assertPreloads(session, Map.of());
        assertUsername(session, "", false);
    }

    @ParameterizedTest @MethodSource("propertyNames")
    void explicitEmptyPropertyValuesAreAcceptedWithoutInventingAFallback(String property) throws Exception {
        var form = singlePreload("property", property, "string");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of(property, ""));
        Object actual = value(session, "metadata");
        // JavaRosa may represent an empty text answer as null or as an empty string.
        assertTrue(actual == null || "".equals(actual), "Empty property must not become a fallback: " + actual);
    }

    @Test void openingASessionDoesNotModifyTheCallersMap() throws Exception {
        var supplied = properties("unchanged");
        var expected = new HashMap<>(supplied);
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        tick(session, 1);
        finish(session);
        assertEquals(expected, supplied);
    }

    @Test void nullPropertiesAreRejected() throws Exception {
        var form = deviceForm();
        assertThrows(NullPointerException.class,
                () -> form.newSimulatedSession(new SimulatedClock(START), SEED, null));
    }

    @Test void nullKeyIsRejectedEvenAlongsideAValidUsername() throws Exception {
        var form = deviceForm();
        var supplied = new HashMap<>(Map.of("username", "valid"));
        supplied.put(null, "invalid");
        assertThrows(NullPointerException.class,
                () -> form.newSimulatedSession(new SimulatedClock(START), SEED, supplied));
    }

    @ParameterizedTest @ValueSource(strings = {"username", "deviceid", "unused-property"})
    void nullValueIsRejectedEvenForAnUnusedProperty(String key) throws Exception {
        var form = deviceForm();
        var supplied = new HashMap<>(Map.of("username", "valid"));
        supplied.put(key, null);
        assertThrows(NullPointerException.class,
                () -> form.newSimulatedSession(new SimulatedClock(START), SEED, supplied));
    }

    @Test void threeArgumentOverloadStillRejectsANullClock() throws Exception {
        var form = deviceForm();
        assertThrows(NullPointerException.class, () -> form.newSimulatedSession(null, SEED, Map.of()));
    }
}
