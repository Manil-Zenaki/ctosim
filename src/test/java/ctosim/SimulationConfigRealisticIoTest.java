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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static ctosim.SimulationConfigRealisticSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigRealisticIoTest {
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")), "config-realistic-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("realistic-Élodie-東京.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadPreserveTheUtf8FixtureWithOrWithoutBom(boolean bom) throws Exception {
        String json = (bom ? "\uFEFF" : "") + fixture();
        SimulationConfig parsed = parse(json);
        SimulationConfig loaded = SimulationConfig.load(write(json));
        assertFixture(parsed);
        assertFixture(loaded);
        assertRetained(parsed, loaded);
        assertEquals(parsed.seed(), loaded.seed());
        assertEquals(parsed.submissions(), loaded.submissions());
    }

    @Test
    void loadAndItsCopiesPreserveAbsentSettings() throws Exception {
        SimulationConfig loaded = SimulationConfig.load(write("{}"));
        assertTrue(loaded.profiles().isEmpty());
        assertTrue(loaded.area().isEmpty());
        assertTrue(loaded.textPools().isEmpty());
        assertRetained(loaded, loaded.withSeed(99).withSubmissions(2));
    }

    @Test
    void loadedSettingsSurviveCopiesAndReplacementOfTheSourceFile() throws Exception {
        Path path = write(fixture());
        SimulationConfig loaded = SimulationConfig.load(path);
        Files.writeString(path, "{}", StandardCharsets.UTF_8);
        assertFixture(loaded);
        assertRetained(loaded, loaded.withSeed(Long.MAX_VALUE));
        assertRetained(loaded, loaded.withSubmissions(1));
        assertFixture(loaded.withSeed(0).withSubmissions(1000000));
    }

    @ParameterizedTest(name = "load rejects {1}: {0}")
    @MethodSource("ctosim.SimulationConfigRealisticValidationTest#allInvalidValues")
    void loadAppliesEveryNewValueRuleAndReportsTheFileAndLocation(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigRealisticValidationTest#duplicateKeys")
    void loadRejectsDuplicateKeysAtEveryNewObjectLevel(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigRealisticRulesTest#malformedExpressions")
    void loadReportsThePreciseKeyOfMalformedXpath(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    static Stream<Arguments> malformedJson() {
        return Stream.of(
                Arguments.of("{\n\"profiles\": {\n\"rural\": @\n}\n}", 3),
                Arguments.of("{\n\"area\": {\n\"minLatitude\": NaN\n}\n}", 3),
                Arguments.of("{\n\"textPools\": {\n\"place\": [\"A\", @]\n}\n}", 3),
                Arguments.of("{\n\"fields\": {\n\"answer\": {\n\"distribution\": {\"normal\": {\"mean\": Infinity,\"sd\":1}}\n}\n}\n}", 4),
                Arguments.of("{\n\"fields\": {\n\"answer\": {\n\"when\": [{\"if\":\"1\",\"value\": @}]\n}\n}\n}", 4));
    }

    private static void assertSyntax(ConfigException error, String source, int line) {
        assertDiagnostic(error, source, null);
        assertNotNull(error.getCause(), "JSON syntax errors retain their underlying cause");
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b" + line + "\\b.*"),
                () -> "Missing failing line " + line + " in " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void parseReportsLineAndCauseForMalformedJsonInsideNewSettings(String json, int line) {
        assertSyntax(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, line);
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void loadReportsLineAndCauseForMalformedJsonInsideNewSettings(String json, int line) throws Exception {
        Path path = write(json);
        assertSyntax(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), line);
    }
}
