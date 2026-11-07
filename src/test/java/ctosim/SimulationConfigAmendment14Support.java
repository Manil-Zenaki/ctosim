package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class SimulationConfigAmendment14Support {
    static final List<String> DEFAULT_FAULTS = List.of("tooFast", "skipModules", "dontKnow",
            "fakeRefusal", "refuse", "other", "outdatedVersion");

    private SimulationConfigAmendment14Support() { }

    record Input(String source, String json, Path path) implements AutoCloseable {
        SimulationConfig read() throws ConfigException {
            return path == null ? SimulationConfig.parse(json, source) : SimulationConfig.load(path);
        }

        @Override public void close() throws IOException {
            if (path != null) Files.deleteIfExists(path);
        }
    }

    static Input input(String json, boolean load) throws IOException {
        if (!load) return new Input("form-update-\u00e9tude.json", json, null);
        Path directory = Files.createDirectories(Path.of(".codex-runs"));
        Path path = Files.createTempFile(directory, "form-update-\u00e9tude-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new Input(path.toString(), json, path);
    }

    static String quote(String value) {
        return SimulationConfigTestSupport.quote(value).replace("\f", "\\f").replace("\b", "\\b");
    }

    static String array(List<String> values) {
        return values.stream().map(SimulationConfigAmendment14Support::quote)
                .collect(Collectors.joining(",", "[", "]"));
    }

    static String update(String date, String previousVersion) {
        return "{\"date\":" + quote(date)
                + (previousVersion == null ? "" : ",\"previousVersion\":" + quote(previousVersion)) + "}";
    }

    static String fixture() throws IOException {
        try (var stream = SimulationConfigAmendment14Support.class.getResourceAsStream(
                "/ctosim/simulation-config/amendment-14-form-update.json")) {
            if (stream == null) throw new IOException("Missing Amendment 14 fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
