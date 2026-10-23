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

import static ctosim.SimulationConfigProblemsSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigProblemsIoTest {
    static class WorkspaceTempDirectory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
            return Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")), "problems-config-");
        }
    }

    @TempDir(factory = WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws IOException {
        return Files.writeString(temp.resolve("problems \u00e9tude.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadAcceptUtf8AndOptionalLeadingBom(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream("/ctosim/simulation-config/amendment-problems.json")) {
            assertNotNull(input);
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SimulationConfig parsed = parse(json);
        SimulationConfig loaded = SimulationConfig.load(write(json));
        double[] expected = {.11, .22, .33, .44, .55, .66, .77};
        assertRates(expected, parsed.problems().orElseThrow());
        assertRates(expected, loaded.problems().orElseThrow());
        assertEquals("m\u00e9nage_\u5bb6\u5ead", loaded.id().orElseThrow());
        SimulationConfigProblemsCopiesTest.assertOnlyProblemsMayDiffer(parsed, loaded);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#acceptedRates")
    void loadRetainsEachExplicitRateAndDefaultsOnlyTheOmittedRates(int index, String token, double value)
            throws Exception {
        double[] expected = DEFAULTS.clone();
        expected[index] = value;
        assertRates(expected, SimulationConfig.load(write(json(KEYS[index], token))).problems().orElseThrow());
    }

    @Test
    void loadDistinguishesAbsentEmptyAndExplicitlyZeroProblems() throws Exception {
        assertTrue(SimulationConfig.load(write("{}")).problems().isEmpty());
        assertRates(DEFAULTS, SimulationConfig.load(write("{\"problems\":{}}")).problems().orElseThrow());
        assertRates(new double[7], SimulationConfig.load(write(allRatesJson("0"))).problems().orElseThrow());
    }

    @Test
    void loadRecognizesEscapedKeys() throws Exception {
        assertEquals(.375, SimulationConfig.load(write("{\"probl\\u0065ms\":{\"trav\\u0065l\":0.375}}"))
                .problems().orElseThrow().travel());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#invalidRates")
    void loadReportsTheFileAndFullRateLocation(String key, String token) throws Exception {
        Path path = write(json(key, token));
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)),
                path.toString(), "problems." + key);
    }

    @ParameterizedTest
    @MethodSource({"ctosim.SimulationConfigProblemsSupport#invalidObjects", "ctosim.SimulationConfigProblemsSupport#unknownKeys"})
    void loadRejectsInvalidObjectsAndUnknownKeysWithTheirLocation(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#duplicateKeys")
    void loadRejectsDuplicateDecodedKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    static Stream<String> malformedJson() {
        return Stream.of("@", "NaN", "Infinity", "-Infinity").flatMap(token -> Stream.of("\n", "\r\n")
                .map(newline -> "{" + newline + "  \"problems\": {" + newline
                        + "    \"travel\": " + token + newline + "  }" + newline + "}"));
    }

    private static void assertSyntax(ConfigException error, String source) {
        assertDiagnostic(error, source, null);
        assertNotNull(error.getCause(), "Underlying JSON syntax failure must be retained");
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b3\\b.*"),
                () -> "Expected failing line 3: " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void parseReportsTheFailingSyntaxLineAndCauseInsideProblems(String json) {
        assertSyntax(assertThrows(ConfigException.class, () -> parse(json)), SOURCE);
    }

    @ParameterizedTest
    @MethodSource("malformedJson")
    void loadReportsTheFailingSyntaxLineAndCauseInsideProblems(String json) throws Exception {
        Path path = write(json);
        assertSyntax(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"problems\":{}}]", "{\"problems\":{}} {}",
            "{\"problems\":{}} null", "{\"problems\":{}} trailing"})
    void problemsDoNotRelaxTheSingleObjectInputRequirement(String json) throws Exception {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @Test
    void loadedProblemsAndCopiesRemainSnapshotsWhenTheFileChanges() throws Exception {
        Path path = write(json("travel", "0"));
        SimulationConfig original = SimulationConfig.load(path);
        SimulationConfig seedCopy = original.withSeed(99);
        SimulationConfig countCopy = original.withSubmissions(1);
        SimulationConfig replaced = original.withProblems(ProblemRates.defaults());
        SimulationConfig removed = original.withProblems(null);
        Files.writeString(path, "{}", StandardCharsets.UTF_8);
        assertTrue(SimulationConfig.load(path).problems().isEmpty());
        double[] expected = DEFAULTS.clone();
        expected[6] = 0;
        assertRates(expected, original.problems().orElseThrow());
        assertRates(expected, seedCopy.problems().orElseThrow());
        assertRates(expected, countCopy.problems().orElseThrow());
        assertRates(DEFAULTS, replaced.problems().orElseThrow());
        assertTrue(removed.problems().isEmpty());
    }
}
