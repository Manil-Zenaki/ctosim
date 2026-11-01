package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class SimulationConfigAmendment11Support {
    private SimulationConfigAmendment11Support() { }

    record Input(String source, String json, Path path) implements AutoCloseable {
        SimulationConfig read() throws ConfigException {
            return path == null ? SimulationConfig.parse(json, source) : SimulationConfig.load(path);
        }

        @Override public void close() throws IOException {
            if (path != null) Files.deleteIfExists(path);
        }
    }

    static Input input(String json, boolean load) throws IOException {
        if (!load) return new Input("team-codes-\u00e9tude.json", json, null);
        Path directory = Files.createDirectories(Path.of(".codex-runs"));
        Path path = Files.createTempFile(directory, "team-codes-\u00e9tude-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new Input(path.toString(), json, path);
    }

    static String quote(String value) {
        return SimulationConfigTestSupport.quote(value).replace("\f", "\\f").replace("\b", "\\b");
    }

    static String json(List<String> codes) {
        return "{\"dontKnowCodes\":" + codes.stream().map(SimulationConfigAmendment11Support::quote)
                .collect(Collectors.joining(",", "[", "]")) + "}";
    }

    static String fixture() throws IOException {
        try (var stream = SimulationConfigAmendment11Support.class.getResourceAsStream(
                "/ctosim/simulation-config/amendment-11-dont-know-codes.json")) {
            if (stream == null) throw new IOException("Missing Amendment 11 fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
