package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigIoTest {
    @TempDir Path temp;

    private Path write(String filename, String json) throws IOException {
        return Files.writeString(temp.resolve(filename), json, StandardCharsets.UTF_8);
    }

    @Test
    void loadReadsUtf8AndAllConfigurationValuesFromAPathWithUnicodeAndSpaces() throws Exception {
        Path path = write("configuration étude 東京.json", completeJson());
        SimulationConfig loaded = SimulationConfig.load(path);
        assertSameSettings(complete(), loaded);
        assertEquals("  café 東京 😀  ", field(loaded, "z_fixed").value().orElseThrow());
    }

    @Test
    void loadAcceptsAnEmptyObjectAsDefaults() throws Exception {
        assertSameSettings(SimulationConfig.defaults(), SimulationConfig.load(write("empty-object.json", "{}")));
    }

    @Test
    void loadAcceptsALeadingUtf8ByteOrderMark() throws Exception {
        Path path = write("bom.json", "\uFEFF" + completeJson());
        byte[] bytes = Files.readAllBytes(path);
        assertArrayEquals(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                java.util.Arrays.copyOf(bytes, 3));
        assertSameSettings(complete(), SimulationConfig.load(path));
    }

    @Test
    void parseAcceptsALeadingDecodedByteOrderMark() throws Exception {
        assertSameSettings(complete(), parse("\uFEFF" + completeJson()));
    }

    @Test
    void missingFileReportsPathAndRetainsIoCause() {
        Path path = temp.resolve("does-not-exist.json");
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(path));
        assertDiagnostic(error, path.toString(), null);
        assertInstanceOf(IOException.class, error.getCause());
    }

    @Test
    void directoryInsteadOfFileReportsPathAndRetainsIoCause() {
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(temp));
        assertDiagnostic(error, temp.toString(), null);
        assertInstanceOf(IOException.class, error.getCause());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\r\n"})
    void loadRejectsEmptyAndWhitespaceOnlyFiles(String content) throws Exception {
        Path path = write("empty-file.json", content);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    static Stream<Arguments> syntaxFailures() {
        return Stream.of(
                Arguments.of("{\n  \"seed\": 1,\n  \"submissions\": @\n}", 3),
                Arguments.of("{\r\n  \"seed\": 1,\r\n  \"submissions\": @\r\n}", 3),
                Arguments.of("{\n  \"seed\": 1,\n  \"fields\": {\n    \"age\": @\n  }\n}", 4));
    }

    private static void assertSyntaxDiagnostic(ConfigException error, String source, int line) {
        assertDiagnostic(error, source, null);
        // Do not lock down parser-specific wording, columns, or punctuation.
        String withoutSource = error.getMessage().replace(source, "");
        assertTrue(withoutSource.matches("(?s).*\\b" + line + "\\b.*"),
                () -> "Expected failing line " + line + " in: " + error.getMessage());
        assertNotNull(error.getCause(), "The underlying JSON syntax exception must be retained");
        assertNotSame(error, error.getCause());
    }

    @ParameterizedTest(name = "parse reports syntax failure on line {1}")
    @MethodSource("syntaxFailures")
    void parseReportsTheActualFailingLineAndRetainsSyntaxCause(String json, int line) {
        String source = "syntax-source.json";
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.parse(json, source));
        assertSyntaxDiagnostic(error, source, line);
    }

    @ParameterizedTest(name = "load reports syntax failure on line {1}")
    @MethodSource("syntaxFailures")
    void loadReportsTheActualFailingLinePathAndSyntaxCause(String json, int line) throws Exception {
        Path path = write("syntax-file.json", json);
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(path));
        assertSyntaxDiagnostic(error, path.toString(), line);
    }

    @ParameterizedTest(name = "load validates {1}")
    @MethodSource("ctosim.SimulationConfigValidationTest#invalidTopLevelValues")
    void loadAppliesValueValidationAndUsesThePathAsSource(String json, String location) throws Exception {
        Path path = write("invalid-value.json", json);
        ConfigException error = assertThrows(ConfigException.class, () -> SimulationConfig.load(path));
        assertDiagnostic(error, path.toString(), location);
    }

    @Test
    void loadIncludesTheFullNestedLocationForABadWeight() throws Exception {
        Path path = write("invalid-weight.json", "{\"fields\":{\"region\":{\"weights\":{\"1\":5,\"2\":-1}}}}");
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), "fields.region.weights.2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{} {}", "{} trailing", "{\"seed\":1,\"seed\":2}",
            "{\"seed\":null}", "{\"submisions\":10}"})
    void loadEnforcesRootShapeDuplicateNullUnknownAndTrailingContentRules(String json) throws Exception {
        Path path = write("strict.json", json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @Test
    void parseNullJsonThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> SimulationConfig.parse(null, SOURCE));
    }

    @Test
    void parseNullSourceNameThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> SimulationConfig.parse("{}", null));
    }

    @Test
    void loadNullPathThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> SimulationConfig.load(null));
    }
}
