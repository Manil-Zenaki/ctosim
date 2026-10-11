package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigSpeedViolationIoTest {
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")),
                    "simulation-config-speed-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("speed \u00e9tude \u6771\u4eac.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadReadTheUtf8FixtureWithOrWithoutALeadingBom(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream(
                "/ctosim/simulation-config/amendment-speed-violations.json")) {
            assertNotNull(input, "The Amendment 2 fixture must exist");
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SimulationConfig parsed = parse(json);
        SimulationConfig loaded = SimulationConfig.load(write(json));
        for (SimulationConfig config : new SimulationConfig[]{parsed, loaded}) {
            assertEquals(0.375, config.speedViolationRate());
            assertEquals("\u00e9tude \u6771\u4eac", field(config, "label").value().orElseThrow());
        }
        assertSameSettings(parsed, loaded);
    }

    @Test
    void loadUsesTheDefaultWhenTheRateIsOmitted() throws Exception {
        assertEquals(0.1, SimulationConfig.load(write("{}")).speedViolationRate());
        assertEquals(0.1, SimulationConfig.load(write(completeJson())).speedViolationRate());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSpeedViolationRateTest#acceptedRates")
    void loadAcceptsTheSameNumericFormsAsParse(String number, double expected) throws Exception {
        SimulationConfig loaded = SimulationConfig.load(write("{\"speedViolationRate\":" + number + "}"));
        assertEquals(expected, loaded.speedViolationRate(), 0.0);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSpeedViolationRateTest#invalidRates")
    void loadRejectsInvalidRatesWithTheFilePathAndKeyLocation(String value) throws Exception {
        Path path = write("{\"speedViolationRate\":" + value + "}");
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(path));
        assertDiagnostic(error, path.toString(), "speedViolationRate");
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"speedViolationrate\":0.3}", "speedViolationrate"),
                Arguments.of("{\"SpeedViolationRate\":0.3}", "SpeedViolationRate"),
                Arguments.of("{\"speedViolationRates\":0.3}", "speedViolationRates"),
                Arguments.of("{\"speedViolationRate \":0.3}", "speedViolationRate "),
                Arguments.of("{\" speedViolationRate\":0.3}", " speedViolationRate"),
                Arguments.of("{\"speedViolationRate\":0.3,\"typo\":0}", "typo"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":3,\"speedViolationRate\":0.3}}",
                        "repeats.speedViolationRate"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"speedViolationRate\":0.3}}",
                        "integerRange.speedViolationRate"),
                Arguments.of("{\"decimalRange\":{\"min\":0,\"max\":1,\"speedViolationRate\":0.3}}",
                        "decimalRange.speedViolationRate"),
                Arguments.of("{\"fields\":{\"answer\":{\"value\":1,\"speedViolationRate\":0.3}}}",
                        "fields.answer.speedViolationRate"));
    }

    @ParameterizedTest(name = "parse rejects unknown key {1}")
    @MethodSource("unknownKeys")
    void parseRequiresTheExactKeyAtTheTopLevelOnly(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest(name = "load rejects unknown key {1}")
    @MethodSource("unknownKeys")
    void loadRequiresTheExactKeyAtTheTopLevelOnly(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), location);
    }

    static Stream<String> duplicateKeys() {
        return Stream.of(
                "{\"speedViolationRate\":0.375,\"speedViolationRate\":0.375}",
                "{\"speedViolationRate\":0,\"speedViolationRate\":1}",
                "{\"speedViolationRate\":1,\"speedViolationRate\":0}",
                "{\"speedViolationRate\":0.1,\"seed\":42,\"speedViolationRate\":0.2}",
                "{\"speedViolationRate\":0.1,\"speedViolation\\u0052ate\":0.2}",
                "{\"speedViolation\\u0052ate\":0.2,\"speedViolationRate\":0.1}");
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void parseRejectsDuplicateRatesIncludingIdenticalValuesAndDecodedKeys(String json) {
        // Duplicate keys may be diagnosed as syntax errors or schema violations (question 2).
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void loadRejectsDuplicateRatesIncludingIdenticalValuesAndDecodedKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), null);
    }

    static Stream<Arguments> syntaxFailures() {
        return Stream.of(
                Arguments.of("{\n  \"seed\":42,\n  \"speedViolationRate\": @\n}", 3),
                Arguments.of("{\r\n  \"seed\":42,\r\n  \"speedViolationRate\": @\r\n}", 3),
                Arguments.of("{\n  \"speedViolationRate\": NaN\n}", 2),
                Arguments.of("{\n  \"speedViolationRate\": Infinity\n}", 2),
                Arguments.of("{\n  \"speedViolationRate\": -Infinity\n}", 2),
                Arguments.of("{\n  \"speedViolationRate\": 0.3,\n  @\n}", 3));
    }

    private static void assertSyntaxDiagnostic(ConfigException error, String source, int line) {
        assertDiagnostic(error, source, null);
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b" + line + "\\b.*"),
                () -> "Missing failing line " + line + " in: " + error.getMessage());
        assertNotNull(error.getCause(), "The underlying syntax error must be retained");
        assertNotSame(error, error.getCause());
    }

    @ParameterizedTest
    @MethodSource("syntaxFailures")
    void parseReportsTheFailingLineAndRetainsTheSyntaxCause(String json, int line) {
        assertSyntaxDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, line);
    }

    @ParameterizedTest
    @MethodSource("syntaxFailures")
    void loadReportsTheFailingLineAndRetainsTheSyntaxCause(String json, int line) throws Exception {
        Path path = write(json);
        assertSyntaxDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), line);
    }

    static Stream<String> invalidRootContent() {
        return Stream.of(
                "[{\"speedViolationRate\":0.375}]",
                "{\"speedViolationRate\":0.375} {}",
                "{\"speedViolationRate\":0.375} null",
                "{\"speedViolationRate\":0.375} trailing");
    }

    @ParameterizedTest
    @MethodSource("invalidRootContent")
    void parseStillRequiresExactlyOneRootObject(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @MethodSource("invalidRootContent")
    void loadStillRequiresExactlyOneRootObject(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), null);
    }

    @Test
    void copiesOfALoadedConfigurationRetainItsRate() throws Exception {
        SimulationConfig loaded = SimulationConfig.load(write("{\"speedViolationRate\":0.625}"));
        assertEquals(0.625, loaded.withSeed(Long.MAX_VALUE).speedViolationRate());
        assertEquals(0.625, loaded.withSubmissions(1).speedViolationRate());
        assertEquals(0.625, loaded.speedViolationRate());
    }
}
