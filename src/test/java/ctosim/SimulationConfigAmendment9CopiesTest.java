package ctosim;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment9Support.*;
import static ctosim.SimulationConfigProblemsCopiesTest.*;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment9CopiesTest {
    private static SimulationConfig configured() throws Exception {
        String existing = SimulationConfigRealisticSupport.fixture();
        return parse("{\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"problems\":{\"travel\":0.125,\"leftOpen\":0.625,\"outliers\":0.875,"
                + "\"faults\":[\"fakeRefusal\",\"dontKnow\"]},"
                + existing.substring(existing.indexOf('{') + 1));
    }

    private static void assertRetained(SimulationConfig expected, SimulationConfig actual) {
        assertOtherSettings(expected, actual);
        assertEquals(expected.consent(), actual.consent());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(.625, actual.problems().orElseThrow().leftOpen());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, 83, Long.MAX_VALUE})
    void seedCopyPreservesLeftOpenAndEveryOtherSettingIncludingAnUnchangedSeed(long seed) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig snapshot = configured();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertRetained(snapshot, copy);
        assertRetained(snapshot, original);
        assertEquals(snapshot.seed(), original.seed());
        assertEquals(snapshot.submissions(), original.submissions());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void submissionCopyPreservesLeftOpenAndEveryOtherSettingIncludingAnUnchangedCount(int count) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig snapshot = configured();
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertRetained(snapshot, copy);
        assertRetained(snapshot, original);
        assertEquals(snapshot.seed(), original.seed());
        assertEquals(snapshot.submissions(), original.submissions());
    }

    static Stream<String> configurations() {
        return Stream.of("{}", "{\"problems\":{}}", json("0"), json("0.375"), json("1"));
    }

    @ParameterizedTest
    @MethodSource("configurations")
    void copyChainsPreserveAbsentDefaultZeroCustomAndUnitLeftOpen(String json) throws Exception {
        SimulationConfig original = parse(json);
        Optional<ProblemRates> before = original.problems();
        List<SimulationConfig> copies = List.of(original.withSeed(original.seed()),
                original.withSubmissions(original.submissions()),
                original.withSeed(Long.MIN_VALUE).withSubmissions(1_000_000)
                        .withSeed(Long.MAX_VALUE).withSubmissions(1));
        for (SimulationConfig copy : copies) {
            assertNotSame(original, copy);
            assertEquals(before, copy.problems());
        }
        assertEquals(before, original.problems());
    }

    static Stream<Arguments> replacements() {
        List<ProblemRates> options = List.of(ProblemRates.defaults(), rates(0), rates(.375), rates(1),
                new ProblemRates(0, 0, 0, 0, 0, 0, 0),
                new ProblemRates(.1, .2, .3, .4, .5, .6, .7, .8, List.of("dontKnow")));
        return Stream.concat(Stream.<ProblemRates>of((ProblemRates) null), options.stream()).flatMap(before ->
                Stream.concat(Stream.<ProblemRates>of((ProblemRates) null), options.stream())
                        .map(after -> Arguments.of(before, after)));
    }

    @ParameterizedTest
    @MethodSource("replacements")
    void problemsCopyAddsReplacesClearsAndKeepsLeftOpenWithoutChangingOtherSettings(
            ProblemRates before, ProblemRates after) throws Exception {
        SimulationConfig baseline = configured();
        SimulationConfig original = baseline.withProblems(before);
        SimulationConfig copy = original.withProblems(after);
        assertNotSame(original, copy);
        assertEquals(Optional.ofNullable(after), copy.problems());
        assertEquals(Optional.ofNullable(before), original.problems());
        assertOnlyProblemsMayDiffer(baseline, copy);
        assertOnlyProblemsMayDiffer(baseline, original);
        assertEquals(baseline.consent(), copy.consent());
        assertEquals(baseline.consent(), original.consent());
        assertEquals(.625, baseline.problems().orElseThrow().leftOpen());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1_000_001})
    void rejectedSubmissionCopiesDoNotChangeLeftOpen(int count) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig snapshot = configured();
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertRetained(snapshot, original);
    }

    @Test
    void parsingLeftOpenPreservesEarlierNondefaultConfigurationSettings() throws Exception {
        SimulationConfig config = configured();
        assertOnlyProblemsMayDiffer(baseline(), config);
        assertEquals(new Consent(List.of("z", "a"), .625, "Y", "N"), config.consent().orElseThrow());
        assertEquals(new ProblemRates(.01, .01, .02, .02, .02, .02, .125, .625, .875,
                List.of("fakeRefusal", "dontKnow")), config.problems().orElseThrow());
    }
}
