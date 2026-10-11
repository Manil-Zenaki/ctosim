package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigSpeedViolationRateTest {
    @Test
    void defaultsUseOneTenthForSpeedViolations() {
        assertEquals(0.1, SimulationConfig.defaults().speedViolationRate());
    }

    @Test
    void anEmptyObjectUsesTheDefaultSpeedViolationRate() throws Exception {
        assertEquals(0.1, parse("{}").speedViolationRate());
    }

    @Test
    void omittingTheRateFromAnOtherwiseConfiguredObjectUsesTheDefault() throws Exception {
        assertEquals(0.1, complete().speedViolationRate());
    }

    static Stream<Arguments> acceptedRates() {
        return Stream.of(
                Arguments.of("0", 0.0),
                Arguments.of("0.0", 0.0),
                Arguments.of("-0.0", 0.0),
                Arguments.of("0e12", 0.0),
                Arguments.of("1", 1.0),
                Arguments.of("1.000", 1.0),
                Arguments.of("10e-1", 1.0),
                Arguments.of("0.1", 0.1),
                Arguments.of("0.375", 0.375),
                Arguments.of("2.5E-1", 0.25),
                Arguments.of("7e-3", 0.007),
                Arguments.of("1e-12", 1e-12),
                Arguments.of("0.1234567890123456", 0.1234567890123456),
                Arguments.of("0.9999999999999999", Math.nextDown(1.0)));
    }

    @ParameterizedTest(name = "accepts speedViolationRate {0}")
    @MethodSource("acceptedRates")
    void acceptsInclusiveBoundsFractionsAndExponentNotation(String number, double expected) throws Exception {
        double actual = parse("{\"speedViolationRate\":" + number + "}").speedViolationRate();
        // The spec does not prescribe the sign bit of zero.
        if (expected == 0.0) {
            assertTrue(actual == 0.0, "Zero must be accepted rather than replaced by the default");
        } else {
            assertEquals(expected, actual);
        }
    }

    static Stream<String> invalidRates() {
        return Stream.of("-1", "-0.1", "-1e-12", "1.0000000000000002", "1.01", "2", "100", "1e20",
                "null", "true", "false", "\"0.25\"", "\"\"", "\"NaN\"", "\"Infinity\"",
                "[]", "[0.25]", "{}", "{\"value\":0.25}");
    }

    @ParameterizedTest(name = "rejects speedViolationRate {0}")
    @MethodSource("invalidRates")
    void rejectsOutOfRangeValuesWrongTypesAndNullWithSourceAndLocation(String value) {
        ConfigException error = assertThrows(ConfigException.class,
                () -> parse("{\"speedViolationRate\":" + value + "}"));
        assertDiagnostic(error, SOURCE, "speedViolationRate");
    }

    @Test
    void anEscapedJsonKeyIsRecognizedAsTheNewSetting() throws Exception {
        assertEquals(0.375, parse("{\"speedViolation\\u0052ate\":0.375}").speedViolationRate());
    }

    @Test
    void allThreeRatesAreIndependentAndNeedNotSumToOne() throws Exception {
        SimulationConfig config = parse("""
                {"missingRate":0.8,"specialRate":0.9,"speedViolationRate":0.7}
                """);
        assertAll(
                () -> assertEquals(0.8, config.missingRate()),
                () -> assertEquals(0.9, config.specialRate()),
                () -> assertEquals(0.7, config.speedViolationRate()));
    }

    @Test
    void parsingOtherRatesDoesNotChangePriorConfigurationsOrDefaults() throws Exception {
        SimulationConfig defaults = SimulationConfig.defaults();
        SimulationConfig first = parse("{\"speedViolationRate\":0}");
        SimulationConfig second = parse("{\"speedViolationRate\":1}");
        SimulationConfig omitted = parse("{}");
        assertAll(
                () -> assertEquals(0.0, first.speedViolationRate(), 0.0),
                () -> assertEquals(1.0, second.speedViolationRate()),
                () -> assertEquals(0.1, omitted.speedViolationRate()),
                () -> assertEquals(0.1, defaults.speedViolationRate()),
                () -> assertEquals(0.1, SimulationConfig.defaults().speedViolationRate()));
    }

    @Test
    void repeatedParsingAndKeyOrderPreserveTheSameRate() throws Exception {
        String json = "{\"speedViolationRate\":0.375,\"seed\":42,\"missingRate\":0.6}";
        SimulationConfig first = parse(json);
        SimulationConfig repeated = parse(json);
        SimulationConfig reordered = parse("{\"missingRate\":0.6,\"seed\":42,\"speedViolationRate\":0.375}");
        for (SimulationConfig config : new SimulationConfig[]{first, repeated, reordered}) {
            assertEquals(0.375, config.speedViolationRate());
            assertSameSettings(first, config);
        }
    }

    @Test
    void theNewKeyRemainsLegalAsAnArbitraryFieldNameAndChoiceCode() throws Exception {
        SimulationConfig config = parse("""
                {
                  "speedViolationRate":0.375,
                  "fields": {
                    "speedViolationRate":{"value":"field answer"},
                    "choice":{"weights":{"speedViolationRate":2}}
                  }
                }
                """);
        assertEquals(0.375, config.speedViolationRate());
        assertEquals("field answer", field(config, "speedViolationRate").value().orElseThrow());
        assertEquals(2.0, field(config, "choice").weights().get("speedViolationRate").doubleValue());
    }
}
