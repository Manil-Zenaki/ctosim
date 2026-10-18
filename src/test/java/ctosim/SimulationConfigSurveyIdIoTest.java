package ctosim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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

class SimulationConfigSurveyIdIoTest {
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")),
                    "survey-config-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("settings \u00e9tude.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadPreserveTheUtf8NameWithOrWithoutALeadingBom(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream(
                "/ctosim/simulation-config/amendment-survey-id.json")) {
            assertNotNull(input, "Amendment 4 fixture must exist");
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(Optional.of("m\u00e9nage_\u5bb6\u5ead"), parse(json).id());
        assertEquals(Optional.of("m\u00e9nage_\u5bb6\u5ead"), SimulationConfig.load(write(json)).id());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSurveyIdTest#acceptedNames")
    void loadAcceptsTheSameExactNamesAsParse(String name) throws Exception {
        assertEquals(Optional.of(name), SimulationConfig.load(write("{\"id\":" + quote(name) + "}")).id());
    }

    @Test
    void loadDecodesEscapesInTheKeyAndValue() throws Exception {
        assertEquals(Optional.of("household"),
                SimulationConfig.load(write("{\"\\u0069d\":\"house\\u0068old\"}")).id());
    }

    @Test
    void loadLeavesIdAbsentWhenItIsOmitted() throws Exception {
        assertEquals(Optional.empty(), SimulationConfig.load(write("{}")).id());
        assertEquals(Optional.empty(), SimulationConfig.load(write(SimulationConfigRealisticSupport.fixture())).id());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSurveyIdTest#invalidValues")
    void loadReportsInvalidIdsWithTheFilePathAndKey(String jsonValue) throws Exception {
        Path path = write("{\"id\":" + jsonValue + "}");
        SimulationConfigSurveyIdTest.assertIdDiagnostic(
                assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSurveyIdTest#unknownKeys")
    void loadRejectsMisspelledAndMisplacedKeys(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSurveyIdTest#duplicateKeys")
    void loadRejectsDuplicateIdKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), null);
    }

    static Stream<String> malformedJson() {
        return Stream.of("{\n  \"seed\":2,\n  \"id\": @\n}",
                "{\n  \"seed\":2,\n  \"id\": \"bad\\q\"\n}",
                "{\r\n  \"seed\":2,\r\n  \"id\": @\r\n}");
    }

    private static void assertSyntaxDiagnostic(ConfigException error, String source) {
        assertDiagnostic(error, source, null);
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b3\\b.*"),
                () -> "Expected failing line 3: " + error.getMessage());
        assertNotNull(error.getCause(), "Syntax failures retain the underlying cause");
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void parseReportsTheLineAndCauseForMalformedIdJson(String json) {
        assertSyntaxDiagnostic(assertThrows(ConfigException.class,
                () -> SimulationConfig.parse(json, SimulationConfigSurveyIdTest.SOURCE_NAME)),
                SimulationConfigSurveyIdTest.SOURCE_NAME);
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void loadReportsTheLineAndCauseForMalformedIdJson(String json) throws Exception {
        Path path = write(json);
        assertSyntaxDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"id\":\"household\"}]", "{\"id\":\"household\"} {}",
            "{\"id\":\"household\"} null", "{\"id\":\"household\"} trailing"})
    void anIdSettingDoesNotRelaxTheSingleRootObjectRequirement(String json) throws Exception {
        assertDiagnostic(assertThrows(ConfigException.class,
                () -> SimulationConfig.parse(json, SimulationConfigSurveyIdTest.SOURCE_NAME)),
                SimulationConfigSurveyIdTest.SOURCE_NAME, null);
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @Test
    void loadedIdAndItsCopiesRemainUnchangedAfterTheFileIsReplaced() throws Exception {
        Path path = write("{\"id\":\"first\"}");
        SimulationConfig original = SimulationConfig.load(path);
        SimulationConfig seedCopy = original.withSeed(99);
        SimulationConfig countCopy = original.withSubmissions(1);
        Files.writeString(path, "{\"id\":\"second\"}", StandardCharsets.UTF_8);
        assertEquals(Optional.of("second"), SimulationConfig.load(path).id());
        assertEquals(Optional.of("first"), original.id());
        assertEquals(Optional.of("first"), seedCopy.id());
        assertEquals(Optional.of("first"), countCopy.id());
    }
}
