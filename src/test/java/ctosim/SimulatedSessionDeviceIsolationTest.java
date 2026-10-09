package ctosim;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionDeviceTestSupport.*;
import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionDeviceIsolationTest {
    @ParameterizedTest @ValueSource(strings = {"replace", "remove", "clear", "nulls"})
    void callerMapChangesCannotAlterPreloadsOrSubsequentUsernameEvaluations(String change) throws Exception {
        var form = deviceForm();
        var supplied = properties("original");
        var expected = new HashMap<>(supplied);
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        switch (change) {
            case "replace" -> supplied.replaceAll((key, old) -> "replacement");
            case "remove" -> supplied.remove("username");
            case "clear" -> supplied.clear();
            case "nulls" -> {
                supplied.put("username", null);
                supplied.put(null, "added-after-opening");
            }
            default -> fail("Unexpected mutation: " + change);
        }
        tick(session, 1);
        assertUsername(session, expected.get("username"), true);
        assertPreloads(session, expected);
        finish(session);
        assertPreloads(session, expected);
    }

    @Test void addingUsernameToAnOriginallyEmptyMapDoesNotChangeTheOpenedSession() throws Exception {
        var supplied = new HashMap<String, String>();
        var session = deviceForm().newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        supplied.putAll(properties("too-late"));
        tick(session, 1);
        assertUsername(session, "", true);
        assertPreloads(session, Map.of());
        finish(session);
        assertPreloads(session, Map.of());
    }

    @Test void eachSessionCopiesTheSameMutableMapAtItsOwnOpeningTime() throws Exception {
        var form = deviceForm();
        var supplied = properties("first");
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        supplied.clear();
        supplied.putAll(properties("second"));
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        supplied.clear();
        var third = form.newSimulatedSession(new SimulatedClock(START), SEED, supplied);
        supplied.putAll(properties("never-used"));
        tick(second, 1);
        tick(third, 1);
        tick(first, 1);
        assertPreloads(first, properties("first"));
        assertPreloads(second, properties("second"));
        assertPreloads(third, Map.of());
        assertUsername(first, "first:username", true);
        assertUsername(second, "second:username", true);
        assertUsername(third, "", true);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interleavedSessionsRetainTheirOwnPropertiesAcrossSharedAndSeparateLoads(boolean separateLoad) throws Exception {
        var form = deviceForm();
        var otherForm = separateLoad ? deviceForm() : form;
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED, properties("first"));
        var second = otherForm.newSimulatedSession(new SimulatedClock(START), SEED, properties("second"));
        var absent = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of());
        for (int step = 1; step <= 3; step++) {
            tick(second, step);
            tick(absent, step);
            tick(first, step);
            assertUsername(first, "first:username", true);
            assertUsername(second, "second:username", true);
            assertUsername(absent, "", true);
        }
        finish(second);
        finish(first);
        finish(absent);
        assertPreloads(first, properties("first"));
        assertPreloads(second, properties("second"));
        assertPreloads(absent, Map.of());
    }

    @Test void explicitPropertiesDoNotLeakToEarlierOrLaterTwoArgumentSessions() throws Exception {
        var form = deviceForm();
        var before = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var custom = form.newSimulatedSession(new SimulatedClock(START), SEED, properties("custom"));
        var empty = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of());
        var after = form.newSimulatedSession(new SimulatedClock(START), SEED);
        for (var defaultSession : new org.javarosa.form.api.FormEntryController[] {before, after}) {
            tick(defaultSession, 1);
            assertPreloads(defaultSession, Map.of("username", "simulated"));
            assertUsername(defaultSession, "simulated", true);
        }
        tick(custom, 1);
        tick(empty, 1);
        assertUsername(custom, "custom:username", true);
        assertUsername(empty, "", true);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ordinarySessionsBeforeAndAfterCustomSessionsKeepSpec01Properties(boolean separateLoad) throws Exception {
        var form = knownDeviceForm();
        var ordinaryForm = separateLoad ? knownDeviceForm() : form;
        var before = ordinaryForm.newEntryController();
        var custom = form.newSimulatedSession(new SimulatedClock(START), SEED, properties("custom"));
        var empty = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of());
        var after = ordinaryForm.newEntryController();
        for (var ordinary : new org.javarosa.form.api.FormEntryController[] {before, after}) {
            tick(ordinary, 1);
            assertEquals("simulated", value(ordinary, "operator"));
            for (String field : new String[] {"device", "subscriber", "sim", "phone"}) {
                assertNull(node(ordinary, field).getValue(), field);
            }
            assertUsername(ordinary, "simulated", true);
            finish(ordinary);
        }
        tick(custom, 1);
        tick(empty, 1);
        for (String property : new String[] {"username", "deviceid", "subscriberid", "simserial", "phonenumber"}) {
            assertEquals("custom:" + property, value(custom, PROPERTY_FIELDS.get(property)));
        }
        assertUsername(custom, "custom:username", true);
        assertPreloads(empty, Map.of());
        assertUsername(empty, "", true);
    }
}
