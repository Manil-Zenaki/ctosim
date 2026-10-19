package ctosim;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigInterviewsPerDayTest {
    @Test
    void defaultsUseFourInterviewsPerDay() {
        assertEquals(4, SimulationConfig.defaults().interviewsPerDay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"enumerators\":1,\"fieldworkDays\":1,\"submissions\":1000000}",
            "{\"fields\":{\"interviewsPerDay\":{\"value\":17}}}"})
    void omissionUsesFourRegardlessOfOtherSettings(String json) throws Exception {
        assertEquals(4, parse(json).interviewsPerDay());
    }

    static Stream<Arguments> acceptedCounts() {
        return Stream.of(
                Arguments.of("1", 1), Arguments.of("2", 2), Arguments.of("4", 4),
                Arguments.of("37", 37), Arguments.of("999", 999), Arguments.of("1000", 1000),
                Arguments.of("1.0", 1), Arguments.of("37.000", 37), Arguments.of("1000.000", 1000),
                Arguments.of("1e0", 1), Arguments.of("3.7e1", 37), Arguments.of("1E+3", 1000),
                Arguments.of("10000e-1", 1000), Arguments.of("0.1e1", 1),
                Arguments.of("100000000000000000000000e-23", 1));
    }

    @ParameterizedTest(name = "interviewsPerDay {0} means {1}")
    @MethodSource("acceptedCounts")
    void acceptsInclusiveBoundsAndExactIntegralNumberForms(String value, int expected) throws Exception {
        assertEquals(expected, parse("{\"interviewsPerDay\":" + value + "}").interviewsPerDay());
    }

    static Stream<String> invalidCounts() {
        return Stream.of("0", "-0", "0.0", "-1", "1001", "1001.0", "1.001e3",
                "0.999", "1.5", "999.5", "1000.5", "-1.5",
                "1.0000000000000000000001", "999.9999999999999999999999",
                "1000.0000000000000000000001", "1.0000000000000000000001e0",
                "1e-400", "1e400", "-1e400", "2147483648", "4294967297", "-4294967295",
                "9223372036854775808", "18446744073709551617",
                "null", "\"4\"", "\"4.0\"", "\"\"", "true", "false", "[]", "[4]", "{}", "{\"count\":4}");
    }

    @ParameterizedTest(name = "reject interviewsPerDay JSON value {0}")
    @MethodSource("invalidCounts")
    void rejectsWrongTypesFractionsOverflowAndOutOfRangeValuesWithTheirLocation(String value) {
        assertDiagnostic(assertThrows(ConfigException.class,
                () -> parse("{\"interviewsPerDay\":" + value + "}")), SOURCE, "interviewsPerDay");
    }

    @Test
    void decodesEscapesBeforeRecognizingTheSetting() throws Exception {
        assertEquals(37, parse("{\"interviewsPer\\u0044ay\":37}").interviewsPerDay());
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"InterviewsPerDay\":4}", "InterviewsPerDay"),
                Arguments.of("{\"interviewsperday\":4}", "interviewsperday"),
                Arguments.of("{\"interviewsPerDay \":4}", "interviewsPerDay "),
                Arguments.of("{\"interviewsPerDays\":4}", "interviewsPerDays"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"interviewsPerDay\":4}}", "repeats.interviewsPerDay"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"interviewsPerDay\":4}}", "integerRange.interviewsPerDay"),
                Arguments.of("{\"decimalRange\":{\"min\":0,\"max\":1,\"interviewsPerDay\":4}}", "decimalRange.interviewsPerDay"),
                Arguments.of("{\"fields\":{\"answer\":{\"value\":1,\"interviewsPerDay\":4}}}", "fields.answer.interviewsPerDay"),
                Arguments.of(SimulationConfigRealisticSupport.ruleJson("\"value\":1,\"interviewsPerDay\":4"),
                        "fields.answer.when[0].interviewsPerDay"),
                Arguments.of("{\"area\":{\"minLatitude\":0,\"maxLatitude\":1,\"minLongitude\":0,\"maxLongitude\":1,\"interviewsPerDay\":4}}",
                        "area.interviewsPerDay"),
                Arguments.of("{\"textPools\":{\"interviewsPerDay\":[\"answer\"]}}", "textPools.interviewsPerDay"),
                Arguments.of(SimulationConfigRealisticSupport.fieldJson("\"distribution\":{\"normal\":{\"mean\":0,\"sd\":1,\"interviewsPerDay\":4}}"),
                        "fields.answer.distribution.normal.interviewsPerDay"));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void onlyTheExactTopLevelSchemaKeyIsAdded(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<String> duplicateKeys() {
        return Stream.of("{\"interviewsPerDay\":1,\"interviewsPerDay\":1000}",
                "{\"interviewsPerDay\":4,\"interviewsPerDay\":4}",
                "{\"interviewsPerDay\":4,\"interviewsPer\\u0044ay\":5}",
                "{\"interviewsPer\\u0044ay\":5,\"interviewsPerDay\":4}");
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void rejectsDuplicateKeysEvenWhenEqualOrEscaped(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @Test
    void theNewNameRemainsLegalInArbitraryNameMappings() throws Exception {
        SimulationConfig config = parse("""
                {"interviewsPerDay":7,"id":"interviewsPerDay",
                 "profiles":{"interviewsPerDay":2},
                 "fields":{"interviewsPerDay":{"weights":{"interviewsPerDay":3}}}}
                """);
        assertEquals(7, config.interviewsPerDay());
        assertEquals("interviewsPerDay", config.id().orElseThrow());
        assertEquals(Map.of("interviewsPerDay", 2.0), config.profiles());
        assertEquals(Map.of("interviewsPerDay", 3.0), field(config, "interviewsPerDay").weights());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 1000})
    void aWorkloadBeyondExpectedFieldworkDaysIsStillValidConfiguration(int dailyLimit) throws Exception {
        SimulationConfig config = parse("{\"interviewsPerDay\":" + dailyLimit
                + ",\"fieldworkDays\":1,\"enumerators\":1,\"submissions\":1000000}");
        assertEquals(dailyLimit, config.interviewsPerDay());
        assertEquals(1, config.fieldworkDays());
        assertEquals(1, config.enumerators());
        assertEquals(1_000_000, config.submissions());
    }

    @Test
    void dailyCapacityMayExceedSubmissionsWithoutClamping() throws Exception {
        SimulationConfig config = parse("{\"interviewsPerDay\":1000,\"submissions\":1,\"enumerators\":10000}");
        assertEquals(1000, config.interviewsPerDay());
        assertEquals(1, config.submissions());
        assertEquals(10000, config.enumerators());
    }
}
