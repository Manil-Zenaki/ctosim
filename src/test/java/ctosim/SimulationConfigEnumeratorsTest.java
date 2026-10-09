package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigEnumeratorsTest {
    @Test
    void defaultsUseFiveEnumerators() {
        assertEquals(5, SimulationConfig.defaults().enumerators());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"timeZone\":\"+03:00\"}", "{\"submissions\":1}"})
    void omittingEnumeratorsUsesFive(String json) throws Exception {
        assertEquals(5, parse(json).enumerators());
    }

    static Stream<Arguments> acceptedCounts() {
        return Stream.of(
                Arguments.of("1", 1), Arguments.of("2", 2), Arguments.of("5", 5),
                Arguments.of("9999", 9999), Arguments.of("10000", 10000),
                Arguments.of("1.0", 1), Arguments.of("42.000", 42),
                Arguments.of("10000.000", 10000), Arguments.of("1e0", 1),
                Arguments.of("4.2e1", 42), Arguments.of("1E+4", 10000),
                Arguments.of("100000e-1", 10000), Arguments.of("0.1e1", 1),
                Arguments.of("100000000000000000000000e-23", 1));
    }

    @ParameterizedTest(name = "enumerators {0} is the integer {1}")
    @MethodSource("acceptedCounts")
    void acceptsInclusiveLimitsAndExactIntegralDecimalAndExponentForms(String number, int expected)
            throws Exception {
        assertEquals(expected, parse("{\"enumerators\":" + number + "}").enumerators());
    }

    @Test
    void decodesAnEscapedEnumeratorsKey() throws Exception {
        assertEquals(27, parse("{\"enumera\\u0074ors\":27}").enumerators());
    }

    static Stream<String> invalidEnumeratorValues() {
        return Stream.of(
                "0", "-0", "0.0", "-1", "10001", "10001.0", "1.0001e4",
                "0.999", "1.5", "9999.5", "10000.5", "-1.5",
                "1.0000000000000000000001", "9999.9999999999999999999999",
                "10000.000000000000000000001", "1.000000000000000000001e0",
                "1e-400", "1e400", "-1e400", "2147483648", "4294967297",
                "-4294967295", "9223372036854775808", "18446744073709551617",
                "null", "\"5\"", "\"5.0\"", "true", "false", "[]", "[5]", "{}",
                "{\"count\":5}");
    }

    @ParameterizedTest(name = "invalid enumerators JSON value: {0}")
    @MethodSource("invalidEnumeratorValues")
    void rejectsOutOfRangeFractionalUnrepresentableWrongTypeAndNullCounts(String value) {
        ConfigException error = assertThrows(ConfigException.class,
                () -> parse("{\"enumerators\":" + value + "}"));
        assertDiagnostic(error, SOURCE, "enumerators");
    }

    @Test
    void enumeratorsMayOutnumberSubmissions() throws Exception {
        SimulationConfig config = parse("{\"enumerators\":10000,\"submissions\":1}");
        assertEquals(10000, config.enumerators());
        assertEquals(1, config.submissions());
    }
}
