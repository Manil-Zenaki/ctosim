package ctosim;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.*;

final class SimulationConfigProblemsSupport {
    static final String[] KEYS = {
            "resent", "reinterview", "shortDuration", "longDuration", "offHours", "overlap", "travel"
    };
    static final double[] DEFAULTS = {.01, .01, .02, .02, .02, .02, .02};

    private SimulationConfigProblemsSupport() { }

    static ProblemRates rates(double[] values) {
        return new ProblemRates(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    static double[] values(ProblemRates rates) {
        return new double[]{rates.resent(), rates.reinterview(), rates.shortDuration(),
                rates.longDuration(), rates.offHours(), rates.overlap(), rates.travel()};
    }

    static void assertRates(double[] expected, ProblemRates actual) {
        double[] observed = values(actual);
        for (int i = 0; i < KEYS.length; i++) {
            // Numeric comparison deliberately treats either sign of zero as zero.
            assertEquals(expected[i], observed[i], 0.0, KEYS[i]);
        }
    }

    static String json(String key, String token) {
        return "{\"problems\":{\"" + key + "\":" + token + "}}";
    }

    static String allRatesJson(String token) {
        return "{\"problems\":{" + String.join(",", Arrays.stream(KEYS)
                .map(key -> "\"" + key + "\":" + token).toList()) + "}}";
    }

    static Stream<Arguments> acceptedRates() {
        return IntStream.range(0, KEYS.length).boxed().flatMap(index -> Stream.of(
                "0", "-0.0", "1", "1.0", "1e0", "0.375", "2.5e-1", "1E-12",
                "0.9999999999999999", "2.2250738585072014e-308")
                .map(token -> Arguments.of(index, token, Double.parseDouble(token))));
    }

    static Stream<Arguments> invalidRates() {
        return Arrays.stream(KEYS).flatMap(key -> Stream.of(
                "-0.000000000001", "-1", "1.0000000000000002", "2", "1e20",
                "null", "true", "false", "\"0.2\"", "\"\"", "[]", "[0.2]", "{}", "{\"rate\":0.2}")
                .map(token -> Arguments.of(key, token)));
    }

    static Stream<Arguments> invalidObjects() {
        return Stream.of("null", "true", "false", "0", "0.5", "\"\"", "\"{}\"", "[]", "[{}]")
                .map(token -> Arguments.of("{\"problems\":" + token + "}", "problems"));
    }

    static Stream<Arguments> unknownKeys() {
        Stream<Arguments> nested = Stream.of("unknown", "", "Travel", "shortduration", "offhours",
                        "travel ", " travel", "seed", "missingRate", "problems")
                .map(key -> Arguments.of(json(key, "0.2"), "problems." + key));
        return Stream.concat(nested, Stream.of(
                Arguments.of("{\"Problems\":{}}", "Problems"),
                Arguments.of("{\"travel\":0.2}", "travel"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"problems\":{}}}", "repeats.problems"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"problems\":{}}}", "integerRange.problems"),
                Arguments.of("{\"fields\":{\"age\":{\"problems\":{}}}}", "fields.age.problems"),
                Arguments.of("{\"fields\":{\"age\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"problems\":{}}]}}}",
                        "fields.age.when[0].problems")));
    }

    static Stream<String> duplicateKeys() {
        Stream<String> nested = Arrays.stream(KEYS).flatMap(key -> Stream.of(
                "{\"problems\":{\"" + key + "\":0.2,\"" + key + "\":0.2}}",
                "{\"problems\":{\"" + key + "\":0,\"" + key + "\":1}}"));
        return Stream.concat(nested, Stream.of(
                "{\"problems\":{},\"problems\":{}}",
                "{\"problems\":{},\"problems\":{\"travel\":0}}",
                "{\"problems\":{},\"probl\\u0065ms\":{}}",
                "{\"problems\":{\"travel\":0,\"trav\\u0065l\":1}}"));
    }
}
