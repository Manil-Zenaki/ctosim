package ctosim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigInterviewsPerDayIoTest {
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")),
                    "interviews-config-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("daily settings \u00e9tude.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadAcceptUtf8WithOrWithoutALeadingBom(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream(
                "/ctosim/simulation-config/amendment-interviews-per-day.json")) {
            assertNotNull(input, "Amendment 5 fixture must exist");
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SimulationConfig parsed = parse(json);
        SimulationConfig loaded = SimulationConfig.load(write(json));
        assertEquals(37, parsed.interviewsPerDay());
        assertEquals(37, loaded.interviewsPerDay());
        assertEquals("m\u00e9nage_\u5bb6\u5ead", loaded.id().orElseThrow());
        assertEquals(parsed.id(), loaded.id());
        SimulationConfigRealisticSupport.assertRetained(parsed, loaded);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigInterviewsPerDayTest#acceptedCounts")
    void loadAcceptsExactIntegerFormsAndBounds(String value, int expected) throws Exception {
        assertEquals(expected, SimulationConfig.load(write("{\"interviewsPerDay\":" + value + "}")).interviewsPerDay());
    }

    @Test
    void loadUsesTheDefaultWhenTheKeyIsAbsent() throws Exception {
        assertEquals(4, SimulationConfig.load(write("{}")).interviewsPerDay());
        assertEquals(4, SimulationConfig.load(write(SimulationConfigRealisticSupport.fixture())).interviewsPerDay());
    }

    @Test
    void loadRecognizesAnEscapedKey() throws Exception {
        assertEquals(37, SimulationConfig.load(write("{\"interviewsPer\\u0044ay\":37}")).interviewsPerDay());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigInterviewsPerDayTest#invalidCounts")
    void loadReportsTheFilePathAndLocationForInvalidValues(String value) throws Exception {
        Path path = write("{\"interviewsPerDay\":" + value + "}");
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), "interviewsPerDay");
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigInterviewsPerDayTest#unknownKeys")
    void loadRejectsUnknownOrMisplacedKeys(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigInterviewsPerDayTest#duplicateKeys")
    void loadRejectsDuplicateDecodedKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    static Stream<String> malformedJson() {
        return Stream.of("{\n  \"seed\":2,\n  \"interviewsPerDay\": @\n}",
                "{\r\n  \"seed\":2,\r\n  \"interviewsPerDay\": @\r\n}",
                "{\n  \"seed\":2,\n  \"interviewsPerDay\": NaN\n}",
                "{\n  \"seed\":2,\n  \"interviewsPerDay\": Infinity\n}");
    }

    private static void assertSyntaxDiagnostic(ConfigException error, String source) {
        assertDiagnostic(error, source, null);
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b3\\b.*"),
                () -> "Expected failing line 3: " + error.getMessage());
        assertNotNull(error.getCause(), "JSON syntax errors retain their underlying cause");
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void parseReportsTheSyntaxLineAndCause(String json) {
        assertSyntaxDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE);
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void loadReportsTheSyntaxLineAndCause(String json) throws Exception {
        Path path = write(json);
        assertSyntaxDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"interviewsPerDay\":4}]", "{\"interviewsPerDay\":4} {}",
            "{\"interviewsPerDay\":4} null", "{\"interviewsPerDay\":4} trailing"})
    void theNewKeyDoesNotRelaxTheSingleObjectRequirement(String json) throws Exception {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @Test
    void loadedConfigurationAndItsCopiesRetainTheirSnapshotAfterFileReplacement() throws Exception {
        Path path = write("{\"interviewsPerDay\":37}");
        SimulationConfig original = SimulationConfig.load(path);
        SimulationConfig seedCopy = original.withSeed(99);
        SimulationConfig countCopy = original.withSubmissions(1);
        Files.writeString(path, "{\"interviewsPerDay\":1000}", StandardCharsets.UTF_8);
        assertEquals(1000, SimulationConfig.load(path).interviewsPerDay());
        assertEquals(37, original.interviewsPerDay());
        assertEquals(37, seedCopy.interviewsPerDay());
        assertEquals(37, countCopy.interviewsPerDay());
    }
}
