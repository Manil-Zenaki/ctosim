package ctosim;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment7Support.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment7IoTest {
    @TempDir(factory = SimulationConfigProblemsIoTest.WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws Exception {
        return Files.writeString(temp.resolve("faults étude.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadAcceptUtf8AndOptionalBomWithBothNewComponents(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream("/ctosim/simulation-config/amendment-7-faults-outliers.json")) {
            assertNotNull(input);
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (SimulationConfig config : List.of(parse(json), SimulationConfig.load(write(json)))) {
            ProblemRates result = config.problems().orElseThrow();
            assertEquals(.625, result.outliers());
            assertEquals(List.of("dontKnow", "tooFast"), result.faults());
            assertEquals("ménage_家庭", config.id().orElseThrow());
            double[] expected = SimulationConfigProblemsSupport.DEFAULTS.clone();
            expected[6] = .75;
            SimulationConfigProblemsSupport.assertRates(expected, result);
            assertThrows(UnsupportedOperationException.class, () -> result.faults().set(0, "skipModules"));
        }
    }

    @Test
    void loadDistinguishesAbsentProblemsFromNewDefaultsAndExplicitlyDisabledNewComponents() throws Exception {
        assertTrue(SimulationConfig.load(write("{}")).problems().isEmpty());
        ProblemRates defaults = SimulationConfig.load(write("{\"problems\":{}}")).problems().orElseThrow();
        assertEquals(.02, defaults.outliers());
        assertEquals(DEFAULT_FAULTS, defaults.faults());
        ProblemRates disabled = SimulationConfig.load(write("{\"problems\":{\"outliers\":0,\"faults\":[]}}"))
                .problems().orElseThrow();
        assertEquals(0, disabled.outliers());
        assertEquals(List.of(), disabled.faults());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#acceptedOutliers")
    void loadAcceptsOutlierBoundsAndNumericRepresentations(String token) throws Exception {
        ProblemRates result = SimulationConfig.load(write(json("outliers", token))).problems().orElseThrow();
        assertEquals(Double.parseDouble(token), result.outliers(), 0.0);
        assertEquals(DEFAULT_FAULTS, result.faults());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#faultLists")
    void loadKeepsEveryFaultSubsetAndItsOrder(List<String> faults) throws Exception {
        ProblemRates result = SimulationConfig.load(write(json("faults", array(faults)))).problems().orElseThrow();
        assertEquals(faults, result.faults());
        assertEquals(.02, result.outliers());
    }

    @ParameterizedTest
    @MethodSource({"ctosim.SimulationConfigAmendment7Support#invalidValues",
            "ctosim.SimulationConfigAmendment7Support#unknownKeys"})
    void loadReportsFileFullLocationAndReasonForInvalidNewSettings(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#duplicateKeys")
    void loadRejectsDuplicateDecodedNewKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    static Stream<String> syntaxErrors() {
        return Stream.of("\n", "\r\n").flatMap(newline -> Stream.of(
                "\"outliers\":NaN", "\"outliers\":Infinity", "\"outliers\":-Infinity",
                "\"outliers\":@", "\"faults\":[\"tooFast\",@]")
                .map(member -> "{" + newline + "\"problems\":{" + newline + member + newline + "}}"));
    }

    private static void assertSyntax(ConfigException error, String source) {
        assertDiagnostic(error, source, null);
        assertNotNull(error.getCause(), "JSON syntax exception must remain the cause");
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b3\\b.*"),
                () -> "Expected syntax failure on line 3: " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("syntaxErrors")
    void parseRetainsSyntaxCauseAndFailingLineInsideNewSettings(String json) {
        assertSyntax(assertThrows(ConfigException.class, () -> parse(json)), SOURCE);
    }

    @ParameterizedTest
    @MethodSource("syntaxErrors")
    void loadRetainsSyntaxCauseAndFailingLineInsideNewSettings(String json) throws Exception {
        Path path = write(json);
        assertSyntax(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    @Test
    void loadedNewComponentsAndTheirCopiesRemainSnapshotsAfterFileReplacement() throws Exception {
        Path path = write("{\"problems\":{\"outliers\":0.875,\"faults\":[\"skipModules\",\"tooFast\"]}}");
        SimulationConfig original = SimulationConfig.load(path);
        List<SimulationConfig> copies = List.of(original, original.withSeed(99), original.withSubmissions(2),
                original.withProblems(original.problems().orElseThrow()));
        Files.writeString(path, "{\"problems\":{\"outliers\":0,\"faults\":[]}}", StandardCharsets.UTF_8);
        assertEquals(List.of(), SimulationConfig.load(path).problems().orElseThrow().faults());
        for (SimulationConfig config : copies) {
            assertEquals(.875, config.problems().orElseThrow().outliers());
            assertEquals(List.of("skipModules", "tooFast"), config.problems().orElseThrow().faults());
        }
    }
}
