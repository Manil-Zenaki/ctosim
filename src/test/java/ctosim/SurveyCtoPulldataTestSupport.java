package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class SurveyCtoPulldataTestSupport {
    Path area;
    Path media;
    Path xform;

    @BeforeEach
    void createAttachmentWorkspace() throws Exception {
        Path runs = Files.createDirectories(Path.of(".codex-runs")).toAbsolutePath();
        area = Files.createTempDirectory(runs, "pulldata-");
        media = Files.createDirectory(area.resolve("media"));
        xform = area.resolve("attached.xml");
        writeForm("", "", "", "");
    }

    @AfterEach
    void removeAttachmentWorkspace() throws Exception {
        if (area != null) {
            try (var paths = Files.walk(area)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    static String resource(String name) throws Exception {
        try (var in = SurveyCtoPulldataTestSupport.class.getResourceAsStream("/ctosim/pulldata/" + name)) {
            assertNotNull(in, "Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    void writeForm(String result, String target, String model, String body) throws Exception {
        Files.writeString(xform, resource("form.xml").replace("__RESULT__", result)
                .replace("__TARGET__", target).replace("__MODEL__", model).replace("__BODY__", body));
    }

    void calculate(String expression) throws Exception {
        writeForm("calculate=\"" + attribute("concat('[', " + expression + ", ']')") + "\"", "", "", "");
    }

    Path attach(String source, String fixture) throws Exception {
        return Files.writeString(media.resolve(source + ".csv"), resource(fixture));
    }

    LoadedForm loadMedia() throws Exception { return FormLoader.load(xform, media); }

    static String call(String source, String column, String keyColumn, String key) {
        // Fixture arguments do not contain apostrophes; XPath expressions use single quotes.
        return "pulldata('" + source + "', '" + column + "', '" + keyColumn + "', '" + key + "')";
    }

    static void assertLookup(FormEntryController session, String expression, String expected) throws Exception {
        assertValue(expected, evaluate(session, expression));
    }

    static FormEntryController session(LoadedForm loaded, String kind) {
        var clock = new SimulatedClock(LocalDateTime.of(2040, 1, 2, 3, 4));
        return switch (kind) {
            case "ordinary" -> loaded.newEntryController();
            case "simulated" -> loaded.newSimulatedSession(clock, 321L);
            case "properties" -> loaded.newSimulatedSession(clock, 321L, Map.of("username", "test"));
            default -> throw new AssertionError(kind);
        };
    }

    static void assertNamesFile(Exception failure, String fileName) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains(fileName), () -> "Error must name " + fileName + ": " + messages);
    }
}
