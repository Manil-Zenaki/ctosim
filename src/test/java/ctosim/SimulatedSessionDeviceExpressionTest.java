package ctosim;

import java.util.Map;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionDeviceExpressionTest {
    @ParameterizedTest @ValueSource(strings = {"worker-27", "", "\u00c9lodie \u6771\u4eac"})
    void usernameFunctionWorksWhenNoPropertyPreloadExists(String username) throws Exception {
        var form = calculation("string", "concat('[', username(), ']')");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", username));
        assertEquals("[" + username + "]", value(session, "result"));
    }

    @Test void missingUsernameFunctionReturnsEmptyStringWithoutAnyPropertyPreload() throws Exception {
        var form = calculation("string", "concat('[', username(), ']')");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("deviceid", "device-only"));
        assertEquals("[]", value(session, "result"));
    }

    @Test void usernameReferencedOnlyInFirstLoadSetvalueUsesSessionProperties() throws Exception {
        var form = expression("string", "", "<setvalue event=\"odk-instance-first-load\" "
                + "ref=\"/data/result\" value=\"username()\"/>");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", "action-only"));
        assertEquals("action-only", value(session, "result"));
    }

    @Test void usernameReferencedOnlyInValueChangedSetvalueUsesSessionProperties() throws Exception {
        var form = valueChangedAction("string", "concat('[', username(), ']')");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", "later-action"));
        tick(session, 1);
        assertEquals("[later-action]", value(session, "result"));
    }

    @ParameterizedTest @ValueSource(strings = {"relevant", "required", "readonly", "constraint"})
    void usernameUsesEachSessionsPropertiesInBooleanBindAttributes(String location) throws Exception {
        var form = expression("string", location + "=\"username() = 'permitted'\"", "");
        var permitted = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", "permitted"));
        var denied = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", "denied"));
        switch (location) {
            case "relevant" -> {
                assertTrue(node(permitted, "result").isRelevant());
                assertFalse(node(denied, "result").isRelevant());
            }
            case "required" -> {
                assertTrue(node(permitted, "result").isRequired());
                assertFalse(node(denied, "result").isRequired());
            }
            case "readonly" -> {
                assertFalse(node(permitted, "result").isEnabled());
                assertTrue(node(denied, "result").isEnabled());
            }
            case "constraint" -> {
                assertEquals(ANSWER_OK, permitted.answerQuestion(question(permitted, "result"), new StringData("x"), true));
                assertEquals(ANSWER_CONSTRAINT_VIOLATED,
                        denied.answerQuestion(question(denied, "result"), new StringData("x"), true));
            }
            default -> fail("Unexpected attribute: " + location);
        }
    }

    @Test void literalUsernameCallTextIsNotReplacedByThePropertyValue() throws Exception {
        var form = calculation("string", "concat('username()', ':', username())");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED, Map.of("username", "literal-check"));
        assertEquals("username():literal-check", value(session, "result"));
    }

    @Test void providingUsernamePropertiesDoesNotChangeTheStubbedFunctionInventory() throws Exception {
        var form = calculation("string", "concat(username(), ':', duration())");
        var before = java.util.Set.copyOf(form.stubbedFunctions());
        var clock = new SimulatedClock(START);
        var session = form.newSimulatedSession(clock, SEED, Map.of("username", "custom-user"));
        assertEquals("custom-user:0", value(session, "result"));
        assertEquals(before, form.stubbedFunctions());
        assertEquals("simulated:0", value(form.newEntryController(), "result"));
        assertEquals(before, form.stubbedFunctions());
    }
}
