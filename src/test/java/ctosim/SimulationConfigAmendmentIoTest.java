package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendmentIoTest {
    // Keep generated load fixtures in the repository's designated temporary area.
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")),
                    "simulation-config-amendment-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("config \u00e9tude \u6771\u4eac.json"), json, StandardCharsets.UTF_8);
    }

    private String fixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/ctosim/simulation-config/amendment-time-zone-enumerators.json")) {
            assertNotNull(input, "The Amendment 1 configuration fixture must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void loadReadsBothNewKeysFromAUtf8File() throws Exception {
        SimulationConfig config = SimulationConfig.load(write(fixture()));
        assertEquals(ZoneId.of("Africa/Addis_Ababa"), config.timeZone());
        assertEquals(17, config.enumerators());
    }

    @Test
    void loadOfAnEmptyObjectUsesBothAmendmentDefaults() throws Exception {
        SimulationConfig config = SimulationConfig.load(write("{}"));
        assertEquals(ZoneId.of("UTC"), config.timeZone());
        assertEquals(5, config.enumerators());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigTimeZoneTest#acceptedZoneIds")
    void loadAcceptsTheSameZoneIdFormsAsParse(String id) throws Exception {
        SimulationConfig config = SimulationConfig.load(write("{\"timeZone\":" + quote(id) + "}"));
        assertEquals(ZoneId.of(id), config.timeZone());
        assertEquals(5, config.enumerators());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigEnumeratorsTest#acceptedCounts")
    void loadAcceptsIntegralCountsAndKeepsTheDefaultZone(String number, int expected) throws Exception {
        SimulationConfig config = SimulationConfig.load(write("{\"enumerators\":" + number + "}"));
        assertEquals(expected, config.enumerators());
        assertEquals(ZoneId.of("UTC"), config.timeZone());
    }

    @Test
    void parseAndLoadAcceptALeadingBomWithBothNewKeys() throws Exception {
        String json = "\uFEFF" + fixture();
        SimulationConfig parsed = parse(json);
        SimulationConfig loaded = SimulationConfig.load(write(json));
        assertEquals(ZoneId.of("Africa/Addis_Ababa"), parsed.timeZone());
        assertEquals(17, parsed.enumerators());
        assertEquals(parsed.timeZone(), loaded.timeZone());
        assertEquals(parsed.enumerators(), loaded.enumerators());
    }

    static Stream<Arguments> invalidNewValues() {
        return Stream.concat(
                SimulationConfigTimeZoneTest.invalidZoneValues()
                        .map(value -> Arguments.of("{\"timeZone\":" + value + "}", "timeZone")),
                SimulationConfigEnumeratorsTest.invalidEnumeratorValues()
                        .map(value -> Arguments.of("{\"enumerators\":" + value + "}", "enumerators")));
    }

    @ParameterizedTest(name = "load rejects value at {1}: {0}")
    @MethodSource("invalidNewValues")
    void loadValidatesBothKeysAndReportsTheFilePathAndValueLocation(String json, String location)
            throws Exception {
        Path path = write(json);
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(path));
        assertDiagnostic(error, path.toString(), location);
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"timezone\":\"UTC\"}", "timezone"),
                Arguments.of("{\"TimeZone\":\"UTC\"}", "TimeZone"),
                Arguments.of("{\"timeZone \":\"UTC\"}", "timeZone "),
                Arguments.of("{\"enumerator\":5}", "enumerator"),
                Arguments.of("{\"Enumerators\":5}", "Enumerators"),
                Arguments.of("{\" enumerators\":5}", " enumerators"),
                Arguments.of("{\"timeZone\":\"UTC\",\"enumerators\":5,\"typo\":0}", "typo"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":3,\"enumerators\":5}}", "repeats.enumerators"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"timeZone\":\"UTC\"}}",
                        "integerRange.timeZone"),
                Arguments.of("{\"fields\":{\"answer\":{\"value\":1,\"timeZone\":\"UTC\"}}}",
                        "fields.answer.timeZone"),
                Arguments.of("{\"fields\":{\"answer\":{\"value\":1,\"enumerators\":5}}}",
                        "fields.answer.enumerators"));
    }

    @ParameterizedTest(name = "parse rejects unknown key {1}")
    @MethodSource("unknownKeys")
    void newKeysAreCaseSensitiveAndOnlyExtendTheTopLevelSchema(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest(name = "load rejects unknown key {1}")
    @MethodSource("unknownKeys")
    void loadRejectsMisspelledAndMisplacedNewKeys(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), location);
    }

    static Stream<String> duplicateKeys() {
        return Stream.of(
                "{\"timeZone\":\"UTC\",\"timeZone\":\"UTC\"}",
                "{\"timeZone\":\"UTC\",\"timeZone\":\"+03:00\"}",
                "{\"timeZone\":\"UTC\",\"time\\u005aone\":\"Z\"}",
                "{\"enumerators\":5,\"enumerators\":5}",
                "{\"enumerators\":1,\"enumerators\":10000}",
                "{\"enumerators\":1,\"enumera\\u0074ors\":2}",
                "{\"timeZone\":\"UTC\",\"enumerators\":5,\"timeZone\":\"UTC\"}",
                "{\"enumerators\":5,\"timeZone\":\"UTC\",\"enumerators\":5}");
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void parseRejectsRepeatedNewKeysIncludingIdenticalAndEscapedKeys(String json) {
        // As in the original suite, either a syntax or a schema diagnostic is valid.
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void loadRejectsRepeatedNewKeysIncludingIdenticalAndEscapedKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), null);
    }

    static Stream<Arguments> syntaxFailures() {
        return Stream.of(
                Arguments.of("{\n  \"enumerators\": 5,\n  \"timeZone\": @\n}", 3),
                Arguments.of("{\r\n  \"timeZone\": \"UTC\",\r\n  \"enumerators\": @\r\n}", 3),
                Arguments.of("{\n  \"timeZone\": \"bad\\q\"\n}", 2),
                Arguments.of("{\n  \"enumerators\": NaN\n}", 2),
                Arguments.of("{\n  \"enumerators\": Infinity\n}", 2));
    }

    private static void assertSyntaxFailure(ConfigException error, String source, int line) {
        assertDiagnostic(error, source, null);
        String withoutSource = error.getMessage().replace(source, "");
        assertTrue(withoutSource.matches("(?s).*\\b" + line + "\\b.*"),
                () -> "Missing failing line " + line + " in: " + error.getMessage());
        assertNotNull(error.getCause(), "Underlying JSON syntax errors must be retained");
        assertNotSame(error, error.getCause());
    }

    @ParameterizedTest
    @MethodSource("syntaxFailures")
    void parseReportsSyntaxFailureLineAndRetainsItsCause(String json, int line) {
        assertSyntaxFailure(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, line);
    }

    @ParameterizedTest
    @MethodSource("syntaxFailures")
    void loadReportsSyntaxFailureLineAndRetainsItsCause(String json, int line) throws Exception {
        Path path = write(json);
        assertSyntaxFailure(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), line);
    }

    static Stream<String> invalidRootContent() {
        return Stream.of(
                "[{\"timeZone\":\"UTC\",\"enumerators\":5}]",
                "{\"timeZone\":\"UTC\",\"enumerators\":5} {}",
                "{\"timeZone\":\"UTC\",\"enumerators\":5} null",
                "{\"timeZone\":\"UTC\",\"enumerators\":5} trailing");
    }

    @ParameterizedTest
    @MethodSource("invalidRootContent")
    void newSettingsStillRequireExactlyOneRootObject(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @MethodSource("invalidRootContent")
    void loadStillRequiresExactlyOneRootObject(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), null);
    }
}
