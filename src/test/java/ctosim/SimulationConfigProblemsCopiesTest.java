package ctosim;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigProblemsSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigProblemsCopiesTest {
    static SimulationConfig baseline() throws Exception {
        String json = SimulationConfigRealisticSupport.fixture();
        return parse("{\"id\":\"household\",\"interviewsPerDay\":37," + json.substring(json.indexOf('{') + 1));
    }

    static void assertOtherSettings(SimulationConfig expected, SimulationConfig actual) {
        SimulationConfigRealisticSupport.assertRetained(expected, actual);
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.interviewsPerDay(), actual.interviewsPerDay());
    }

    static void assertOnlyProblemsMayDiffer(SimulationConfig expected, SimulationConfig actual) {
        assertOtherSettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
    }

    static Stream<Arguments> transitions() {
        ProblemRates defaults = ProblemRates.defaults();
        ProblemRates distinct = rates(new double[]{.11, .22, .33, .44, .55, .66, .77});
        ProblemRates zero = rates(new double[7]);
        return Stream.<ProblemRates>of(null, defaults, distinct, zero).flatMap(before ->
                Stream.<ProblemRates>of(null, defaults, distinct, zero)
                        .map(after -> Arguments.of(before, after)));
    }

    @Test
    void parsingProblemsAlongsideExistingSettingsPreservesEveryOtherSetting() throws Exception {
        String json = SimulationConfigRealisticSupport.fixture();
        SimulationConfig config = parse("{\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"problems\":{\"travel\":0.75}," + json.substring(json.indexOf('{') + 1));
        assertOnlyProblemsMayDiffer(baseline(), config);
        double[] expected = DEFAULTS.clone();
        expected[6] = .75;
        assertRates(expected, config.problems().orElseThrow());
    }

    @Test
    void parsingProblemsAlonePreservesAllOtherDefaults() throws Exception {
        SimulationConfig config = parse("{\"problems\":{}}");
        assertOnlyProblemsMayDiffer(SimulationConfig.defaults(), config);
        assertRates(DEFAULTS, config.problems().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void withProblemsAddsReplacesRemovesOrKeepsRatesWithoutChangingTheReceiverOrAnyOtherSetting(
            ProblemRates before, ProblemRates after) throws Exception {
        SimulationConfig baseline = baseline();
        SimulationConfig original = baseline.withProblems(before);
        assertNotSame(baseline, original);
        assertOnlyProblemsMayDiffer(baseline, original);
        assertEquals(Optional.ofNullable(before), original.problems());
        SimulationConfig copy = original.withProblems(after);
        assertNotSame(original, copy, "Even an unchanged setting must return a new configuration");
        assertEquals(Optional.ofNullable(after), copy.problems());
        assertOnlyProblemsMayDiffer(baseline, copy);
        assertOnlyProblemsMayDiffer(baseline, original);
        assertEquals(Optional.ofNullable(before), original.problems());
        assertTrue(baseline.problems().isEmpty());
    }

    static Stream<String> configurations() {
        return Stream.of("{}", "{\"problems\":{}}", json("travel", "0"),
                allRatesJson("0"), allRatesJson("1"));
    }

    @ParameterizedTest
    @MethodSource("configurations")
    void seedAndSubmissionCopiesKeepAbsentDefaultPartialAndEndpointProblems(String json) throws Exception {
        SimulationConfig original = parse(json);
        SimulationConfig sameSeed = original.withSeed(original.seed());
        SimulationConfig sameCount = original.withSubmissions(original.submissions());
        assertNotSame(original, sameSeed);
        assertNotSame(original, sameCount);
        assertOnlyProblemsMayDiffer(original, sameSeed);
        assertOnlyProblemsMayDiffer(original, sameCount);
        assertEquals(original.problems(), sameSeed.problems());
        assertEquals(original.problems(), sameCount.problems());
        assertEquals(original.problems(), original.withSeed(Long.MIN_VALUE).withSubmissions(1_000_000)
                .withSeed(Long.MAX_VALUE).withSubmissions(1).problems());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, 83, Long.MAX_VALUE})
    void seedCopiesOfFullyConfiguredProblemsPreserveAllOtherSettings(long seed) throws Exception {
        SimulationConfig original = baseline().withProblems(rates(new double[]{.11, .22, .33, .44, .55, .66, .77}));
        SimulationConfig snapshot = baseline();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(snapshot.submissions(), copy.submissions());
        assertOtherSettings(snapshot, copy);
        assertEquals(original.problems(), copy.problems());
        assertOnlyProblemsMayDiffer(snapshot, original);
        assertRates(new double[]{.11, .22, .33, .44, .55, .66, .77}, original.problems().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void submissionCopiesOfFullyConfiguredProblemsPreserveAllOtherSettings(int count) throws Exception {
        SimulationConfig original = baseline().withProblems(rates(new double[]{.11, .22, .33, .44, .55, .66, .77}));
        SimulationConfig snapshot = baseline();
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(snapshot.seed(), copy.seed());
        assertOtherSettings(snapshot, copy);
        assertEquals(original.problems(), copy.problems());
        assertOnlyProblemsMayDiffer(snapshot, original);
        assertRates(new double[]{.11, .22, .33, .44, .55, .66, .77}, original.problems().orElseThrow());
    }

    @Test
    void settingProblemsPreservesAllDefaultsAndRemovingThemRestoresAbsence() {
        SimulationConfig original = SimulationConfig.defaults();
        SimulationConfig enabled = original.withProblems(ProblemRates.defaults());
        SimulationConfig disabled = enabled.withProblems(null);
        assertOnlyProblemsMayDiffer(original, enabled);
        assertOnlyProblemsMayDiffer(original, disabled);
        assertRates(DEFAULTS, enabled.problems().orElseThrow());
        assertTrue(disabled.problems().isEmpty());
        assertTrue(original.problems().isEmpty());
    }

    @Test
    void withProblemsPreservesImmutabilityOfExistingNestedCollections() throws Exception {
        SimulationConfig copy = baseline().withProblems(ProblemRates.defaults());
        assertThrows(UnsupportedOperationException.class, () -> copy.specialValues().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> copy.overriddenFields().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> copy.profiles().put("new", 1.0));
        assertThrows(UnsupportedOperationException.class, () -> copy.textPools().put("new", java.util.List.of("x")));
        assertThrows(UnsupportedOperationException.class, () -> copy.textPools().get("place").add("new"));
        assertThrows(UnsupportedOperationException.class, () -> field(copy, "income").rules().clear());
        FieldOverride weighted = field(copy, "conditional").rules().get(1).override();
        assertThrows(UnsupportedOperationException.class, () -> weighted.weights().put("new", 1.0));
        FieldOverride text = field(copy, "conditional").rules().get(2).override();
        assertThrows(UnsupportedOperationException.class, () -> text.texts().add("new"));
    }

    @Test
    void rejectedSubmissionCopyDoesNotChangeEnabledProblems() throws Exception {
        SimulationConfig original = baseline().withProblems(ProblemRates.defaults());
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(0));
        assertOnlyProblemsMayDiffer(baseline(), original);
        assertRates(DEFAULTS, original.problems().orElseThrow());
    }
}
