package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

final class SimulationConfigAmendment10Support {
    private SimulationConfigAmendment10Support() { }

    record Input(String source, String json, Path path) implements AutoCloseable {
        SimulationConfig read() throws ConfigException {
            return path == null ? SimulationConfig.parse(json, source) : SimulationConfig.load(path);
        }

        @Override public void close() throws IOException {
            if (path != null) Files.deleteIfExists(path);
        }
    }

    static Input input(String json, boolean load) throws IOException {
        if (!load) return new Input("villages-\u00e9tude.json", json, null);
        Path directory = Files.createDirectories(Path.of(".codex-runs"));
        Path path = Files.createTempFile(directory, "villages-\u00e9tude-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new Input(path.toString(), json, path);
    }

    static String quote(String value) {
        return SimulationConfigTestSupport.quote(value).replace("\f", "\\f").replace("\b", "\\b");
    }

    static String json(String latitude, String longitude) {
        return "{\"villages\":{\"latitude\":" + quote(latitude)
                + ",\"longitude\":" + quote(longitude) + "}}";
    }

    static Stream<Arguments> acceptedNames() {
        return Stream.of(
                Arguments.of("x", "y"),
                Arguments.of("Latitude", "latitude"),
                Arguments.of("gps.latitude", "gps.longitude"),
                Arguments.of("village latitude", "village longitude"),
                Arguments.of("lat\tcolumn", "lon\ncolumn"),
                Arguments.of("lat\rcolumn", "lon\fcolumn"),
                Arguments.of("緯度", "経度"),
                Arguments.of("latitude_\ud83c\udf0d", "longitude_\ud83c\udf0d"),
                Arguments.of("\u00e9", "e\u0301"),
                Arguments.of("lat\"\\header", "lon/'[]${header}"),
                Arguments.of("longitude", "latitude"),
                Arguments.of("0", "1"));
    }

    static Stream<String> invalidNames() {
        return Stream.concat(Stream.of(""), Stream.of(" ", "\t", "\r", "\n", "\f")
                .flatMap(space -> Stream.of(space, space + "column", "column" + space, space + "column" + space)));
    }

    static String fixture() throws IOException {
        try (var stream = SimulationConfigAmendment10Support.class.getResourceAsStream(
                "/ctosim/simulation-config/amendment-10-villages.json")) {
            if (stream == null) throw new IOException("Missing Amendment 10 fixture");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
