package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment8Support.*;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment8Test {
    @Test
    void defaultsAppendFakeRefusalAfterTheThreeExistingFaults() {
        assertEquals(DEFAULT_FAULTS, ProblemRates.defaults().faults());
        assertEquals(.02, ProblemRates.defaults().outliers());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, ProblemRates.defaults());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"travel\":0}", "{\"outliers\":0}", "{\"resent\":1}"})
    void omittedFaultsInAnyPresentProblemsObjectUseAllFourDefaults(String problems) throws Exception {
        assertEquals(DEFAULT_FAULTS, parse("{\"problems\":" + problems + "}").problems().orElseThrow().faults());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#ordersIncludingFakeRefusal")
    void constructorAcceptsEveryOrderedSubsetContainingFakeRefusal(List<String> faults) {
        ProblemRates result = rates(faults);
        assertEquals(faults, result.faults());
        assertEquals(.88, result.outliers());
        SimulationConfigProblemsSupport.assertRates(new double[]{.11, .22, .33, .44, .55, .66, .77}, result);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#ordersIncludingFakeRefusal")
    void parseKeepsEveryOrderedSubsetContainingFakeRefusal(List<String> faults) throws Exception {
        ProblemRates result = parse("{\"problems\":{\"faults\":" + array(faults) + "}}").problems().orElseThrow();
        assertEquals(faults, result.faults());
        assertEquals(.02, result.outliers());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[\"tooFast\"]", "[\"dontKnow\",\"skipModules\",\"tooFast\"]"})
    void explicitlySelectedLegacyFaultsDoNotAutomaticallyAcquireFakeRefusal(String token) throws Exception {
        ProblemRates result = parse("{\"problems\":{\"faults\":" + token + "}}").problems().orElseThrow();
        assertFalse(result.faults().contains("fakeRefusal"));
    }

    @Test
    void sevenRateConstructorStillDisablesEveryFaultIncludingFakeRefusal() {
        ProblemRates result = new ProblemRates(.11, .22, .33, .44, .55, .66, .77);
        assertEquals(List.of(), result.faults());
        assertEquals(0, result.outliers());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FakeRefusal", "fakerefusal", "fake_refusal", "fakeRefusal ", " fakeRefusal"})
    void constructorRejectsNearMissFakeRefusalNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> rates(List.of("tooFast", name)));
    }

    @Test
    void constructorRejectsAdjacentDuplicateFakeRefusal() {
        assertThrows(IllegalArgumentException.class, () -> rates(List.of("fakeRefusal", "fakeRefusal")));
    }

    @Test
    void constructorRejectsNonadjacentDuplicateFakeRefusal() {
        assertThrows(IllegalArgumentException.class, () -> rates(List.of("fakeRefusal", "tooFast", "fakeRefusal")));
    }

    @Test
    void constructorRejectsNullAlongsideFakeRefusal() {
        assertThrows(NullPointerException.class, () -> rates(Arrays.asList("fakeRefusal", null)));
    }

    @Test
    void listsContainingFakeRefusalAreDefensivelyCopiedAndUnmodifiable() {
        List<String> caller = new ArrayList<>(List.of("fakeRefusal", "tooFast"));
        ProblemRates result = rates(caller);
        caller.clear();
        assertEquals(List.of("fakeRefusal", "tooFast"), result.faults());
        assertThrows(UnsupportedOperationException.class, () -> result.faults().remove("fakeRefusal"));
        assertThrows(UnsupportedOperationException.class, () -> ProblemRates.defaults().faults().remove("fakeRefusal"));
    }

    @Test
    void fakeRefusalDoesNotRequireConsentOrAnyPositiveProblemRate() throws Exception {
        ProblemRates rates = new ProblemRates(0, 0, 0, 0, 0, 0, 0, 0, List.of("fakeRefusal"));
        SimulationConfig config = SimulationConfig.defaults().withProblems(rates);
        assertEquals(List.of("fakeRefusal"), config.problems().orElseThrow().faults());
        assertTrue(config.consent().isEmpty());
        assertEquals(List.of("fakeRefusal"), parse("""
                {"problems":{"resent":0,"reinterview":0,"shortDuration":0,"longDuration":0,
                 "offHours":0,"overlap":0,"travel":0,"outliers":0,"faults":["fakeRefusal"]}}
                """).problems().orElseThrow().faults());
    }
}
