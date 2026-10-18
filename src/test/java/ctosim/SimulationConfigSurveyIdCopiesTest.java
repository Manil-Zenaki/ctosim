package ctosim;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigSurveyIdCopiesTest {
    private static final String NAME = "Household_\u00e9_\u5bb6\u5ead";

    private static String withId(String json) {
        // The fixture is a JSON object; add a new root member without altering its settings.
        return "{\"id\":" + quote(NAME) + "," + json.substring(json.indexOf('{') + 1);
    }

    private static SimulationConfig configured() throws Exception {
        return parse(withId(SimulationConfigRealisticSupport.fixture()));
    }

    @Test
    void addingIdPreservesAllPreviouslyConfiguredSettings() throws Exception {
        String json = SimulationConfigRealisticSupport.fixture();
        SimulationConfig baseline = parse(json);
        SimulationConfig config = parse(withId(json));
        assertEquals(Optional.empty(), baseline.id());
        assertEquals(Optional.of(NAME), config.id());
        assertEquals(baseline.seed(), config.seed());
        assertEquals(baseline.submissions(), config.submissions());
        SimulationConfigRealisticSupport.assertRetained(baseline, config);
    }

    @Test
    void idOnlyConfigurationPreservesEveryOtherDefault() throws Exception {
        SimulationConfig baseline = SimulationConfig.defaults();
        SimulationConfig config = parse("{\"id\":" + quote(NAME) + "}");
        assertEquals(Optional.empty(), baseline.id());
        assertEquals(Optional.of(NAME), config.id());
        assertEquals(baseline.seed(), config.seed());
        assertEquals(baseline.submissions(), config.submissions());
        SimulationConfigRealisticSupport.assertRetained(baseline, config);
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 83, Long.MAX_VALUE})
    void withSeedKeepsIdAndAllOtherSettingsIncludingForAnUnchangedSeed(long seed) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertEquals(Optional.of(NAME), copy.id());
        SimulationConfigRealisticSupport.assertRetained(original, copy);
        assertEquals(83, original.seed());
        assertEquals(Optional.of(NAME), original.id());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void withSubmissionsKeepsIdAndAllOtherSettingsIncludingForAnUnchangedCount(int count) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertEquals(Optional.of(NAME), copy.id());
        SimulationConfigRealisticSupport.assertRetained(original, copy);
        assertEquals(19, original.submissions());
        assertEquals(Optional.of(NAME), original.id());
    }

    @Test
    void alternatingCopiesKeepTheIdAtEveryStep() throws Exception {
        SimulationConfig original = configured();
        SimulationConfig current = original;
        for (int i = 1; i <= 4; i++) {
            SimulationConfig next = current.withSeed(i).withSubmissions(i);
            assertNotSame(current, next);
            assertEquals(Optional.of(NAME), next.id());
            assertEquals(i, next.seed());
            assertEquals(i, next.submissions());
            SimulationConfigRealisticSupport.assertRetained(original, next);
            current = next;
        }
        assertEquals(83, original.seed());
        assertEquals(19, original.submissions());
        assertEquals(Optional.of(NAME), original.id());
    }

    @Test
    void copiesOfConfigurationsWithoutIdKeepItAbsent() throws Exception {
        for (SimulationConfig original : new SimulationConfig[]{SimulationConfig.defaults(), parse("{}"),
                parse(SimulationConfigRealisticSupport.fixture())}) {
            assertEquals(Optional.empty(), original.withSeed(original.seed()).id());
            assertEquals(Optional.empty(), original.withSubmissions(original.submissions()).id());
            assertEquals(Optional.empty(), original.withSeed(42).withSubmissions(7).id());
            assertEquals(Optional.empty(), original.id());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1_000_001, Integer.MAX_VALUE})
    void rejectedSubmissionCopyLeavesTheConfiguredIdIntact(int count) throws Exception {
        SimulationConfig original = configured();
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertEquals(Optional.of(NAME), original.id());
        assertEquals(19, original.submissions());
    }

    @Test
    void independentParsesCannotChangePreviouslyReturnedIdsOrDefaults() throws Exception {
        SimulationConfig first = parse("{\"id\":\"Household\"}");
        SimulationConfig second = parse("{\"id\":\"household\"}");
        SimulationConfig omitted = parse("{}");
        assertEquals(Optional.of("Household"), first.id());
        assertEquals(Optional.of("household"), second.id());
        assertEquals(Optional.empty(), omitted.id());
        assertEquals(Optional.empty(), SimulationConfig.defaults().id());
        assertEquals(first.id(), parse("{\"id\":\"Household\"}").id());
    }
}
