package ctosim;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment9Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment9ParsingTest {
    static Stream<Arguments> acceptedInputs() {
        return acceptedRates().flatMap(token -> Stream.of(Arguments.of(false, token), Arguments.of(true, token)));
    }

    @ParameterizedTest
    @MethodSource("acceptedInputs")
    void parseAndLoadAcceptNumericFormsAndInclusiveEndpoints(boolean load, String token) throws Exception {
        try (Input input = input(json(token), load)) {
            ProblemRates p = input.read().problems().orElseThrow();
            assertEquals(Double.parseDouble(token), p.leftOpen(), 0.0);
            assertEquals(new ProblemRates(.01, .01, .02, .02, .02, .02, .02,
                    p.leftOpen(), .02, DEFAULT_FAULTS), p);
        }
    }

    static Stream<Arguments> omittedKeyInputs() {
        return Stream.of("{}", "{\"travel\":0}", "{\"outliers\":0.875,\"faults\":[]}",
                "{\"resent\":0,\"reinterview\":0,\"shortDuration\":0,\"longDuration\":0,"
                        + "\"offHours\":0,\"overlap\":0,\"travel\":0,\"outliers\":0,\"faults\":[]}")
                .flatMap(body -> Stream.of(Arguments.of(false, body), Arguments.of(true, body)));
    }

    @ParameterizedTest
    @MethodSource("omittedKeyInputs")
    void everyPresentProblemsObjectDefaultsOmittedLeftOpenEvenWhenOtherProblemsAreDisabled(
            boolean load, String body) throws Exception {
        try (Input input = input("{\"problems\":" + body + "}", load)) {
            assertEquals(.02, input.read().problems().orElseThrow().leftOpen());
        }
    }

    @Test
    void configurationDefaultsStillHaveNoProblems() {
        assertTrue(SimulationConfig.defaults().problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void omissionOfProblemsDoesNotEnableLeftOpen(boolean load) throws Exception {
        try (Input input = input("{}", load)) {
            assertTrue(input.read().problems().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leftOpenMayBeTheOnlyEnabledProblemWithoutOtherConfigurationPrerequisites(boolean load) throws Exception {
        String json = "{\"submissions\":1,\"enumerators\":1,\"interviewsPerDay\":1,\"problems\":{"
                + "\"resent\":0,\"reinterview\":0,\"shortDuration\":0,\"longDuration\":0,"
                + "\"offHours\":0,\"overlap\":0,\"travel\":0,\"leftOpen\":1,\"outliers\":0,\"faults\":[]}}";
        try (Input input = input(json, load)) {
            assertEquals(new ProblemRates(0, 0, 0, 0, 0, 0, 0, 1, 0, List.of()),
                    input.read().problems().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allNineProblemRatesMayBeOne(boolean load) throws Exception {
        String json = "{\"problems\":{\"resent\":1,\"reinterview\":1,\"shortDuration\":1,"
                + "\"longDuration\":1,\"offHours\":1,\"overlap\":1,\"travel\":1,"
                + "\"leftOpen\":1,\"outliers\":1,\"faults\":[]}}";
        try (Input input = input(json, load)) {
            assertEquals(new ProblemRates(1, 1, 1, 1, 1, 1, 1, 1, 1, List.of()),
                    input.read().problems().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void allZeroProblemsRemainPresentAndDoNotRestoreTheLeftOpenDefault(boolean load) throws Exception {
        String json = "{\"problems\":{\"resent\":0,\"reinterview\":0,\"shortDuration\":0,"
                + "\"longDuration\":0,\"offHours\":0,\"overlap\":0,\"travel\":0,"
                + "\"leftOpen\":0,\"outliers\":0,\"faults\":[]}}";
        try (Input input = input(json, load)) {
            assertEquals(new ProblemRates(0, 0, 0, 0, 0, 0, 0), input.read().problems().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bomAndEscapedKeyAreAccepted(boolean load) throws Exception {
        try (Input input = input("\uFEFF{\"problems\":{\"left\\u004fpen\":0.625}}", load)) {
            assertEquals(.625, input.read().problems().orElseThrow().leftOpen());
        }
    }

    @Test
    void fixtureKeepsDistinctRatesRegardlessOfJsonMemberOrder() throws Exception {
        Path path = Path.of(getClass().getResource("/ctosim/simulation-config/amendment-9-left-open.json").toURI());
        ProblemRates p = SimulationConfig.load(path).problems().orElseThrow();
        assertEquals(rates(.99), p);
        assertThrows(UnsupportedOperationException.class, () -> p.faults().clear());
    }

    @Test
    void newKeyRemainsLegalAsAnArbitraryFieldProfileChoiceAndIdName() throws Exception {
        SimulationConfig config = SimulationConfig.parse("""
                {"id":"leftOpen","profiles":{"leftOpen":1},
                 "fields":{"leftOpen":{"weights":{"leftOpen":1}}}}
                """, "names.json");
        assertEquals("leftOpen", config.id().orElseThrow());
        assertEquals(1.0, config.profiles().get("leftOpen"));
        assertEquals(1.0, config.field("leftOpen").orElseThrow().weights().get("leftOpen"));
        assertTrue(config.problems().isEmpty());
    }

    static Stream<Arguments> invalidInputs() {
        Stream<Arguments> cases = Stream.concat(invalidRates().map(token -> Arguments.of(json(token), "problems.leftOpen")),
                unknownKeys());
        return cases.flatMap(args -> Stream.of(Arguments.of(false, args.get()[0], args.get()[1]),
                Arguments.of(true, args.get()[0], args.get()[1])));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void parseAndLoadRejectInvalidValuesAndMisplacedKeysWithSourcePathAndReason(
            boolean load, String json, String path) throws Exception {
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), path);
        }
    }

    static Stream<Arguments> duplicateInputs() {
        return duplicateKeys().flatMap(json -> Stream.of(Arguments.of(false, json), Arguments.of(true, json)));
    }

    @ParameterizedTest
    @MethodSource("duplicateInputs")
    void duplicateLeftOpenKeysAreRejectedIncludingEqualValuesAndDecodedNames(boolean load, String json) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    static Stream<Arguments> malformedInputs() {
        return Stream.of("@", "NaN", "Infinity", "-Infinity")
                .flatMap(token -> Stream.of(Arguments.of(false, token), Arguments.of(true, token)));
    }

    @ParameterizedTest
    @MethodSource("malformedInputs")
    void malformedLeftOpenJsonReportsItsLineAndRetainsTheParserCause(boolean load, String token) throws Exception {
        String json = "{\n  \"problems\": {\n    \"leftOpen\": " + token + "\n  }\n}";
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertNotNull(error.getCause());
            String diagnostic = error.getMessage().replace(input.source(), "");
            assertTrue(diagnostic.matches("(?s).*\\b3\\b.*"), error::getMessage);
        }
    }
}
