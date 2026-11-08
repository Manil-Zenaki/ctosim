package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.*;

final class SimulationConfigAmendment15Support {
    private SimulationConfigAmendment15Support() { }

    record Input(String source, String json, Path path) implements AutoCloseable {
        SimulationConfig read() throws ConfigException {
            return path == null ? SimulationConfig.parse(json, source) : SimulationConfig.load(path);
        }

        @Override public void close() throws IOException {
            if (path != null) Files.deleteIfExists(path);
        }
    }

    static Input input(String json, boolean load) throws IOException {
        if (!load) return new Input("visit-plan-\u00e9tude.json", json, null);
        Path directory = Files.createDirectories(Path.of(".codex-runs"));
        Path path = Files.createTempFile(directory, "visit-plan-\u00e9tude-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new Input(path.toString(), json, path);
    }

    static String quote(String value) {
        return SimulationConfigTestSupport.quote(value).replace("\f", "\\f").replace("\b", "\\b");
    }

    static String visits(String members) {
        return "{\"visits\":{\"field\":\"availability\"" + (members.isEmpty() ? "" : "," + members) + "}}";
    }

    static Stream<Arguments> bothReaders(Stream<Arguments> cases) {
        return cases.flatMap(args -> Stream.of(false, true).map(load -> {
            Object[] values = java.util.Arrays.copyOf(args.get(), args.get().length + 1);
            values[values.length - 1] = load;
            return Arguments.of(values);
        }));
    }

    static void assertInvalid(String json, String location, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            SimulationConfigTestSupport.assertDiagnostic(
                    assertThrows(ConfigException.class, input::read), input.source(), location);
        }
    }

    static String fixture() throws IOException {
        try (var stream = SimulationConfigAmendment15Support.class.getResourceAsStream(
                "/ctosim/simulation-config/amendment-15-visits.json")) {
            if (stream == null) throw new IOException("Missing Amendment 15 fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
