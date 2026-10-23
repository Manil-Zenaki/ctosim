package ctosim;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigProblemsSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigProblemsParsingTest {
    @Test
    void defaultsDoNotEnableProblems() {
        assertTrue(SimulationConfig.defaults().problems().isEmpty());
    }

    @Test
    void omittingTheKeyLeavesProblemsAbsent() throws Exception {
        assertTrue(parse("{}").problems().isEmpty());
        assertTrue(parse(SimulationConfigRealisticSupport.fixture()).problems().isEmpty());
    }

    @Test
    void anEmptyObjectEnablesEveryDefaultRate() throws Exception {
        assertRates(DEFAULTS, parse("{\"problems\":{}}").problems().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#acceptedRates")
    void eachExplicitRateIsRetainedAndOnlyOmittedRatesReceiveDefaults(int index, String token, double value)
            throws Exception {
        double[] expected = DEFAULTS.clone();
        expected[index] = value;
        assertRates(expected, parse(json(KEYS[index], token)).problems().orElseThrow());
    }

    @Test
    void distinctRatesAreMappedByNameRegardlessOfJsonOrder() throws Exception {
        SimulationConfig config = parse("""
                {"problems":{"travel":0.77,"overlap":0.66,"offHours":0.55,
                 "longDuration":0.44,"shortDuration":0.33,"reinterview":0.22,"resent":0.11}}
                """);
        assertRates(new double[]{.11, .22, .33, .44, .55, .66, .77}, config.problems().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1"})
    void allRatesCanBeAtTheSameEndpointWithoutDroppingTheOptionalOrNormalizing(String token) throws Exception {
        double[] expected = new double[7];
        Arrays.fill(expected, Double.parseDouble(token));
        assertRates(expected, parse(allRatesJson(token)).problems().orElseThrow());
    }

    @Test
    void problemRatesAreIndependentOfOtherProbabilitiesAndFieldworkCapacity() throws Exception {
        String json = "{\"missingRate\":1,\"specialRate\":1,\"speedViolationRate\":1,"
                + "\"submissions\":1,\"enumerators\":1,\"fieldworkDays\":1,\"interviewsPerDay\":1,"
                + allRatesJson("1").substring(1);
        SimulationConfig config = parse(json);
        double[] expected = new double[7];
        Arrays.fill(expected, 1);
        assertRates(expected, config.problems().orElseThrow());
        assertEquals(1, config.missingRate());
        assertEquals(1, config.specialRate());
        assertEquals(1, config.speedViolationRate());
        assertTrue(config.id().isEmpty(), "No id or form is required to read these settings");
    }

    @Test
    void escapedSchemaKeysAreRecognizedAfterJsonDecoding() throws Exception {
        double[] expected = DEFAULTS.clone();
        expected[6] = .375;
        assertRates(expected, parse("{\"probl\\u0065ms\":{\"trav\\u0065l\":0.375}}")
                .problems().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#invalidRates")
    void invalidRatesReportTheirFullNestedLocation(String key, String token) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json(key, token))), SOURCE, "problems." + key);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#invalidObjects")
    void problemsMustBeANonnullObject(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#unknownKeys")
    void onlyTheSpecifiedKeysAreAllowedAtTheirSpecifiedLocations(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigProblemsSupport#duplicateKeys")
    void duplicateDecodedKeysAreRejectedEvenWhenValuesAgree(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    static Stream<String> arbitraryNames() {
        return Stream.concat(Stream.of("problems"), Arrays.stream(KEYS));
    }

    @ParameterizedTest
    @MethodSource("arbitraryNames")
    void newSchemaNamesRemainLegalInArbitraryNameMappingsWithoutEnablingProblems(String name) throws Exception {
        SimulationConfig config = parse("{\"id\":" + quote(name) + ",\"profiles\":{" + quote(name)
                + ":1},\"fields\":{" + quote(name) + ":{\"weights\":{" + quote(name) + ":1}}}}");
        assertTrue(config.problems().isEmpty());
        assertEquals(name, config.id().orElseThrow());
        assertEquals(1.0, config.profiles().get(name));
        assertEquals(1.0, field(config, name).weights().get(name));
    }

    @Test
    void repeatedAndIndependentParsesDoNotChangeExistingProblemSettings() throws Exception {
        SimulationConfig first = parse(json("travel", "0"));
        ProblemRates snapshot = first.problems().orElseThrow();
        assertEquals(1, parse(json("travel", "1")).problems().orElseThrow().travel());
        assertTrue(parse("{}").problems().isEmpty());
        assertEquals(snapshot, parse(json("travel", "0")).problems().orElseThrow());
        assertEquals(snapshot, first.problems().orElseThrow());
        assertRates(DEFAULTS, ProblemRates.defaults());
        assertTrue(SimulationConfig.defaults().problems().isEmpty());
    }
}
