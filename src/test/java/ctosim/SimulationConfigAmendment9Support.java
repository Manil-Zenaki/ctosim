package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

final class SimulationConfigAmendment9Support {
    static final List<String> DEFAULT_FAULTS = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal");

    private SimulationConfigAmendment9Support() { }

    record Input(String source, String json, Path path) implements AutoCloseable {
        SimulationConfig read() throws ConfigException {
            return path == null ? SimulationConfig.parse(json, source) : SimulationConfig.load(path);
        }

        @Override public void close() throws IOException {
            if (path != null) Files.deleteIfExists(path);
        }
    }

    static Input input(String json, boolean load) throws IOException {
        if (!load) return new Input("left-open-\u00e9tude.json", json, null);
        Path directory = Files.createDirectories(Path.of(".codex-runs"));
        Path path = Files.createTempFile(directory, "left-open-\u00e9tude-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new Input(path.toString(), json, path);
    }

    static String json(String token) {
        return "{\"problems\":{\"leftOpen\":" + token + "}}";
    }

    static ProblemRates rates(double leftOpen) {
        return new ProblemRates(.11, .22, .33, .44, .55, .66, .77, leftOpen,
                .88, List.of("fakeRefusal", "tooFast"));
    }

    static Stream<String> acceptedRates() {
        return Stream.of("0", "-0.0", "1", "1.0", "1e0", "0.375", "2.5E-1",
                "1e-12", "0.9999999999999999", "2.2250738585072014e-308");
    }

    static Stream<Double> invalidDoubles() {
        return Stream.of(-Double.MIN_VALUE, -1.0, Math.nextUp(1.0), 2.0,
                -Double.MAX_VALUE, Double.MAX_VALUE, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    static Stream<String> invalidRates() {
        return Stream.of("-1", "-1e-12", "1.0000000000000002", "2", "1e20",
                "null", "true", "false", "\"0.02\"", "\"\"", "[]", "[0.02]", "{}", "{\"rate\":0.02}");
    }

    static Stream<String> duplicateKeys() {
        return Stream.of(
                "{\"problems\":{\"leftOpen\":0,\"leftOpen\":0}}",
                "{\"problems\":{\"leftOpen\":0,\"leftOpen\":1}}",
                "{\"problems\":{\"leftOpen\":1,\"travel\":0,\"leftOpen\":0}}",
                "{\"problems\":{\"leftOpen\":0,\"left\\u004fpen\":1}}");
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.concat(Stream.of("LeftOpen", "leftopen", "left_open", "leftOpens", "leftOpen ", " leftOpen")
                .map(key -> Arguments.of("{\"problems\":{\"" + key + "\":0.2}}", "problems." + key)),
                Stream.of(
                        Arguments.of("{\"leftOpen\":0.2}", "leftOpen"),
                        Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"leftOpen\":0.2}}", "repeats.leftOpen"),
                        Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"leftOpen\":0.2}}", "integerRange.leftOpen"),
                        Arguments.of("{\"decimalRange\":{\"min\":0,\"max\":1,\"leftOpen\":0.2}}", "decimalRange.leftOpen"),
                        Arguments.of("{\"consent\":{\"fields\":[\"c\"],\"leftOpen\":0.2}}", "consent.leftOpen"),
                        Arguments.of("{\"fields\":{\"x\":{\"value\":1,\"leftOpen\":0.2}}}", "fields.x.leftOpen"),
                        Arguments.of("{\"fields\":{\"x\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"leftOpen\":0.2}]}}}",
                                "fields.x.when[0].leftOpen"),
                        Arguments.of("{\"problems\":{\"faults\":[\"leftOpen\"]}}", "problems.faults[0]")));
    }
}
