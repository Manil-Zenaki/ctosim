package ctosim;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment10Support.*;
import static ctosim.SimulationConfigProblemsCopiesTest.assertOtherSettings;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment10CopiesTest {
    private static String richJson(boolean villages) throws Exception {
        String previous = SimulationConfigRealisticSupport.fixture();
        return "{" + (villages ? "\"villages\":{\"latitude\":\"Latitude 緯度\",\"longitude\":\"longitude 経度\"}," : "")
                + "\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"problems\":{\"travel\":0.125,\"leftOpen\":0.625,\"outliers\":0.875,"
                + "\"faults\":[\"fakeRefusal\",\"dontKnow\"]},"
                + previous.substring(previous.indexOf('{') + 1);
    }

    private static void assertUnchangedExceptCopySettings(SimulationConfig expected, SimulationConfig actual) {
        assertOtherSettings(expected, actual);
        assertEquals(expected.consent(), actual.consent());
        assertEquals(expected.villages(), actual.villages());
    }

    private static void assertSnapshot(SimulationConfig snapshot, SimulationConfig actual) {
        assertUnchangedExceptCopySettings(snapshot, actual);
        assertEquals(snapshot.seed(), actual.seed());
        assertEquals(snapshot.submissions(), actual.submissions());
        assertEquals(snapshot.problems(), actual.problems());
    }

    static Stream<Arguments> copyCases() {
        return Stream.of(false, true).flatMap(present -> Stream.of(false, true)
                .flatMap(load -> Stream.of("sameSeed", "minSeed", "maxSeed", "sameCount", "minCount", "maxCount",
                        "sameProblems", "defaultProblems", "legacyProblems", "clearProblems")
                        .map(operation -> Arguments.of(present, load, operation))));
    }

    @ParameterizedTest
    @MethodSource("copyCases")
    void everyCopyPreservesPresentOrAbsentVillagesAndLeavesItsReceiverUnchanged(
            boolean present, boolean load, String operation) throws Exception {
        String json = richJson(present);
        SimulationConfig snapshot = parse(json);
        try (Input input = input(json, load)) {
            SimulationConfig original = input.read();
            long seed = original.seed();
            int count = original.submissions();
            ProblemRates problems = original.problems().orElseThrow();
            SimulationConfig copy;
            switch (operation) {
                case "sameSeed" -> copy = original.withSeed(seed);
                case "minSeed" -> { seed = Long.MIN_VALUE; copy = original.withSeed(seed); }
                case "maxSeed" -> { seed = Long.MAX_VALUE; copy = original.withSeed(seed); }
                case "sameCount" -> copy = original.withSubmissions(count);
                case "minCount" -> { count = 1; copy = original.withSubmissions(count); }
                case "maxCount" -> { count = 1_000_000; copy = original.withSubmissions(count); }
                case "sameProblems" -> copy = original.withProblems(problems);
                case "defaultProblems" -> { problems = ProblemRates.defaults(); copy = original.withProblems(problems); }
                case "legacyProblems" -> {
                    problems = new ProblemRates(0, 0, 0, 0, 0, 0, 0);
                    copy = original.withProblems(problems);
                }
                case "clearProblems" -> { problems = null; copy = original.withProblems(null); }
                default -> throw new AssertionError(operation);
            }
            assertNotSame(original, copy);
            assertEquals(present ? Optional.of(new Villages("Latitude 緯度", "longitude 経度")) : Optional.empty(),
                    copy.villages());
            assertUnchangedExceptCopySettings(snapshot, copy);
            assertEquals(seed, copy.seed());
            assertEquals(count, copy.submissions());
            assertEquals(Optional.ofNullable(problems), copy.problems());
            assertSnapshot(snapshot, original);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingVillagesPreservesAllEarlierNondefaultSettings(boolean load) throws Exception {
        SimulationConfig without = parse(richJson(false));
        try (Input input = input(richJson(true), load)) {
            SimulationConfig with = input.read();
            assertOtherSettings(without, with);
            assertEquals(without.seed(), with.seed());
            assertEquals(without.submissions(), with.submissions());
            assertEquals(without.problems(), with.problems());
            assertEquals(without.consent(), with.consent());
            assertEquals(new Villages("Latitude 緯度", "longitude 経度"), with.villages().orElseThrow());
            assertTrue(without.villages().isEmpty());
        }
    }

    @Test
    void villagesAloneDoNotEnableProblemsConsentOrChangeOtherDefaults() throws Exception {
        SimulationConfig defaults = SimulationConfig.defaults();
        SimulationConfig config = parse(json("lat", "lon"));
        assertOtherSettings(defaults, config);
        assertEquals(defaults.seed(), config.seed());
        assertEquals(defaults.submissions(), config.submissions());
        assertTrue(config.problems().isEmpty());
        assertTrue(config.consent().isEmpty());
        assertEquals(new Villages("lat", "lon"), config.villages().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void chainedCopiesKeepVillagesWhenProblemsAreAddedReplacedAndCleared(boolean present) throws Exception {
        SimulationConfig original = parse(present ? json("Lat", "lat") : "{}");
        SimulationConfig enabled = original.withProblems(ProblemRates.defaults());
        SimulationConfig changed = enabled.withSeed(Long.MAX_VALUE).withSubmissions(1_000_000)
                .withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, 0));
        SimulationConfig cleared = changed.withProblems(null).withSeed(Long.MIN_VALUE).withSubmissions(1);
        Optional<Villages> expected = present ? Optional.of(new Villages("Lat", "lat")) : Optional.empty();
        for (SimulationConfig config : List.of(original, enabled, changed, cleared)) {
            assertEquals(expected, config.villages());
        }
        assertTrue(original.problems().isEmpty());
        assertTrue(cleared.problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1_000_001, Integer.MAX_VALUE})
    void rejectedSubmissionCopyLeavesVillagesAndTheRestOfTheConfigurationUnchanged(int count) throws Exception {
        SimulationConfig original = parse(richJson(true));
        SimulationConfig snapshot = parse(richJson(true));
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertSnapshot(snapshot, original);
    }

    @Test
    void parsingAnotherConfigurationCannotReplacePreviouslyParsedVillages() throws Exception {
        SimulationConfig first = parse(json("Latitude", "Longitude"));
        SimulationConfig second = parse(json("lat", "lon"));
        SimulationConfig absent = parse("{}");
        assertEquals(new Villages("Latitude", "Longitude"), first.villages().orElseThrow());
        assertEquals(new Villages("lat", "lon"), second.villages().orElseThrow());
        assertTrue(absent.villages().isEmpty());
        assertTrue(SimulationConfig.defaults().villages().isEmpty());
    }
}
