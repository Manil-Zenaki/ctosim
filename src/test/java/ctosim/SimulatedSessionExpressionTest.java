package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedSessionExpressionTest {
    static Stream<Arguments> booleanLocations() {
        return Stream.of("relevant", "required", "readonly", "constraint").flatMap(location -> Stream.of(
                Arguments.of(location, "today() = date('2040-02-28')"),
                Arguments.of(location, "now() > date('2040-02-28') and now() < date('2040-02-29')"),
                Arguments.of(location, "duration() = 0"),
                Arguments.of(location, "random() >= 0 and random() < 1"),
                Arguments.of(location, "string-length(uuid()) = 36")));
    }

    @ParameterizedTest(name = "{0} evaluates {1} inside the form") @MethodSource("booleanLocations")
    void simulatedFunctionsAreAvailableInEveryBooleanBindAttribute(String location, String condition)
            throws Exception {
        var form = expression("string", location + "=\"" + attribute(condition) + "\"", "");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        switch (location) {
            case "relevant" -> assertTrue(node(session, "result").isRelevant());
            case "required" -> {
                question(session, "result");
                assertTrue(session.getModel().getQuestionPrompt().isRequired());
                assertEquals(ANSWER_REQUIRED_BUT_EMPTY, session.answerQuestion(null, true));
            }
            case "readonly" -> {
                question(session, "result");
                assertTrue(session.getModel().getQuestionPrompt().isReadOnly());
            }
            case "constraint" -> {
                question(session, "result");
                assertEquals(ANSWER_OK, session.answerQuestion(new StringData("accepted"), true));
            }
            default -> fail("Unexpected location " + location);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"relevant", "required", "readonly"})
    void clockDependentBindPropertiesRecalculateWhenTheirDependencyChanges(String location) throws Exception {
        String condition = "duration() >= 2 and /data/tick > 0";
        var clock = new SimulatedClock(START);
        var session = expression("string", location + "=\"" + attribute(condition) + "\"", "")
                .newSimulatedSession(clock, SEED);
        assertProperty(session, location, false);
        clock.advance(Duration.ofSeconds(2));
        tick(session, 1);
        assertProperty(session, location, true);
    }

    private static void assertProperty(org.javarosa.form.api.FormEntryController session,
            String location, boolean expected) {
        boolean actual = switch (location) {
            case "relevant" -> node(session, "result").isRelevant();
            case "required" -> node(session, "result").isRequired();
            case "readonly" -> !node(session, "result").isEnabled();
            default -> throw new AssertionError(location);
        };
        assertEquals(expected, actual, location);
    }

    @Test void constraintReadsTheAdvancedClockWhenAnAnswerIsValidated() throws Exception {
        var clock = new SimulatedClock(START);
        var session = expression("string", "constraint=\"duration() &gt;= 2\"", "")
                .newSimulatedSession(clock, SEED);
        question(session, "result");
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, session.answerQuestion(new StringData("early"), true));
        clock.advance(Duration.ofSeconds(2));
        assertEquals(ANSWER_OK, session.answerQuestion(new StringData("on time"), true));
    }

    static Stream<Arguments> actionFunctions() {
        return Stream.of(Arguments.of("date", "today()"), Arguments.of("dateTime", "now()"),
                Arguments.of("decimal", "duration()"), Arguments.of("decimal", "random()"),
                Arguments.of("string", "uuid()"));
    }

    @ParameterizedTest @MethodSource("actionFunctions")
    void functionReferencedOnlyBySetvalueUsesTheSimulatedSession(String type, String call) throws Exception {
        var form = expression(type, "", "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" value=\""
                + call + "\"/>");
        var first = form.newSimulatedSession(new SimulatedClock(START), SEED);
        var second = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(value(first, "result"), value(second, "result"));
        switch (call) {
            case "today()" -> assertEquals(START.toLocalDate(), date(first, "result"));
            case "now()" -> assertTime(first, "result", START);
            case "duration()" -> assertEquals(0, number(first, "result"));
            case "random()" -> assertRandom(first, "result");
            case "uuid()" -> assertUuid(value(first, "result"));
            default -> fail("Unexpected call " + call);
        }
    }

    @ParameterizedTest @MethodSource("actionFunctions")
    void valueChangedSetvalueActionsUseLiveTimeAndReproducibleRandomness(String type, String call) throws Exception {
        var form = valueChangedAction(type, call);
        var firstClock = new SimulatedClock(START);
        var secondClock = new SimulatedClock(START);
        var first = form.newSimulatedSession(firstClock, SEED);
        var second = form.newSimulatedSession(secondClock, SEED);
        for (int i = 1; i <= 3; i++) {
            firstClock.advance(Duration.ofSeconds(3));
            secondClock.advance(Duration.ofSeconds(3));
            tick(first, i);
            tick(second, i);
            assertEquals(value(first, "result"), value(second, "result"));
            switch (call) {
                case "today()" -> assertEquals(START.plusSeconds(3L * i).toLocalDate(), date(first, "result"));
                case "now()" -> assertTime(first, "result", START.plusSeconds(3L * i));
                case "duration()" -> assertEquals(3 * i, number(first, "result"));
                case "random()" -> assertRandom(first, "result");
                case "uuid()" -> assertUuid(value(first, "result"));
                default -> fail("Unexpected call " + call);
            }
        }
    }

    @Test void nativeFunctionNamesInsideStringLiteralsAreNotRewritten() throws Exception {
        String literal = "today() now() duration() random() uuid() caf\u00e9 \u6771\u4eac";
        var form = calculation("string", "concat('" + literal + "', ':', username())");
        var session = form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(literal + ":simulated", value(session, "result"));
    }

    @Test void simulatedFunctionsComposeWithNativeXPathOperations() throws Exception {
        var start = LocalDateTime.of(2040, 2, 28, 12, 0);
        var form = calculation("string", "concat(if(today() = date('2040-02-28'), username(), 'wrong'), ':', "
                + "duration() + 3, ':', string-length(uuid()), ':', if(random() < 1, 'in range', 'wrong'))");
        assertEquals("simulated:3:36:in range", value(form.newSimulatedSession(new SimulatedClock(start), SEED), "result"));
    }

    @Test void nativeTimeAndRandomFunctionsNeverBecomeListedStubs() throws Exception {
        var form = calculation("string", "concat(today(), now(), random(), uuid())");
        assertEquals(Set.of(), form.stubbedFunctions());
        form.newSimulatedSession(new SimulatedClock(START), SEED);
        assertEquals(Set.of(), form.stubbedFunctions());
        form.newEntryController();
        assertEquals(Set.of(), form.stubbedFunctions());
    }

    @Test void durationRemainsListedAfterOpeningAndUsingSimulatedSessions() throws Exception {
        var form = calculation("decimal", "duration() + 0 * /data/tick");
        var inventory = form.stubbedFunctions();
        assertEquals(Set.of("duration"), inventory);
        var clock = new SimulatedClock(START);
        var simulated = form.newSimulatedSession(clock, SEED);
        clock.advance(Duration.ofSeconds(5));
        tick(simulated, 1);
        assertEquals(5, number(simulated, "result"));
        assertEquals(0, number(form.newEntryController(), "result"));
        assertEquals(Set.of("duration"), inventory);
        assertEquals(Set.of("duration"), form.stubbedFunctions());
    }
}
