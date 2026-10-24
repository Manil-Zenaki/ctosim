package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment7Support.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment7ParsingTest {
    @Test
    void omittedProblemsRemainAbsentInsteadOfEnablingNewDefaults() throws Exception {
        assertTrue(SimulationConfig.defaults().problems().isEmpty());
        assertTrue(parse("{}").problems().isEmpty());
    }

    @Test
    void emptyProblemsEnableBothNewDefaultsAndKeepTheSevenOriginalDefaults() throws Exception {
        ProblemRates result = parse("{\"problems\":{}}").problems().orElseThrow();
        assertEquals(.02, result.outliers());
        assertEquals(DEFAULT_FAULTS, result.faults());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, result);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#acceptedOutliers")
    void explicitOutlierRatesAreRetainedWithoutChangingOtherDefaults(String token) throws Exception {
        ProblemRates result = parse(json("outliers", token)).problems().orElseThrow();
        assertEquals(Double.parseDouble(token), result.outliers(), 0.0);
        assertEquals(DEFAULT_FAULTS, result.faults());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, result);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#faultLists")
    void everyOrderedFaultSubsetIsRetainedWithoutChangingRateDefaults(List<String> faults) throws Exception {
        ProblemRates result = parse(json("faults", array(faults))).problems().orElseThrow();
        assertEquals(faults, result.faults());
        assertEquals(.02, result.outliers());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, result);
        assertThrows(UnsupportedOperationException.class, () -> result.faults().add("tooFast"));
    }

    @Test
    void originalRateOverridesStillDefaultBothNewComponents() throws Exception {
        ProblemRates result = parse("{\"problems\":{\"travel\":0}}").problems().orElseThrow();
        assertEquals(0, result.travel());
        assertEquals(.02, result.outliers());
        assertEquals(DEFAULT_FAULTS, result.faults());
    }

    @Test
    void explicitlyDisabledNewComponentsRemainPresentAndDoNotDisableOriginalProblems() throws Exception {
        ProblemRates result = parse("{\"problems\":{\"outliers\":0,\"faults\":[]}}").problems().orElseThrow();
        assertEquals(0, result.outliers());
        assertEquals(List.of(), result.faults());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[\"tooFast\",\"skipModules\",\"dontKnow\"]"})
    void newProblemsAreIndependentOfOtherRatesAndFieldworkCapacity(String faults) throws Exception {
        SimulationConfig result = parse("""
                {"submissions":1,"enumerators":1,"fieldworkDays":1,"interviewsPerDay":1,
                 "missingRate":1,"specialRate":1,"speedViolationRate":1,
                 "problems":{"resent":1,"reinterview":1,"shortDuration":1,"longDuration":1,
                             "offHours":1,"overlap":1,"travel":1,"outliers":1,"faults":%s}}
                """.formatted(faults));
        assertEquals(1, result.problems().orElseThrow().outliers());
        SimulationConfigProblemsSupport.assertRates(new double[]{1, 1, 1, 1, 1, 1, 1}, result.problems().orElseThrow());
        assertEquals(1, result.missingRate());
        assertEquals(1, result.specialRate());
        assertEquals(1, result.speedViolationRate());
    }

    @Test
    void escapedKeysAndFaultNamesAreDecodedBeforeRecognition() throws Exception {
        ProblemRates result = parse("{\"problems\":{\"outli\\u0065rs\":0.625,"
                + "\"fa\\u0075lts\":[\"dontK\\u006eow\",\"tooF\\u0061st\"]}}").problems().orElseThrow();
        assertEquals(.625, result.outliers());
        assertEquals(List.of("dontKnow", "tooFast"), result.faults());
    }

    @ParameterizedTest
    @MethodSource({"ctosim.SimulationConfigAmendment7Support#invalidValues",
            "ctosim.SimulationConfigAmendment7Support#unknownKeys"})
    void invalidNewSettingsReportSourceFullLocationAndReason(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#duplicateKeys")
    void duplicateDecodedNewKeysAreRejectedEvenWhenValuesAreIdentical(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @Test
    void newWordsRemainLegalInArbitraryNameMappingsWithoutEnablingProblems() throws Exception {
        SimulationConfig result = parse("""
                {"id":"outliers","profiles":{"faults":1,"outliers":2},
                 "fields":{"faults":{"value":"dontKnow"},
                           "outliers":{"weights":{"faults":1,"outliers":2}}}}
                """);
        assertTrue(result.problems().isEmpty());
        assertEquals("outliers", result.id().orElseThrow());
        assertEquals("dontKnow", field(result, "faults").value().orElseThrow());
        assertEquals(2.0, field(result, "outliers").weights().get("outliers"));
        assertEquals(1.0, result.profiles().get("faults"));
    }

    @Test
    void repeatedParsingAndSeparateConfigurationsDoNotShareMutableFaultState() throws Exception {
        String json = "{\"problems\":{\"outliers\":0.625,\"faults\":[\"dontKnow\",\"tooFast\"]}}";
        ProblemRates first = parse(json).problems().orElseThrow();
        assertEquals(first, parse(json).problems().orElseThrow());
        parse("{\"problems\":{\"outliers\":0,\"faults\":[]}}");
        assertEquals(.625, first.outliers());
        assertEquals(List.of("dontKnow", "tooFast"), first.faults());
    }
}
