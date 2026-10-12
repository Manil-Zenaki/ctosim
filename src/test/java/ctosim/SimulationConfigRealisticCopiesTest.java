package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ctosim.SimulationConfigRealisticSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigRealisticCopiesTest {
    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, 83, Long.MAX_VALUE})
    void seedCopiesPreserveEverySettingIncludingNestedRulesAndOrder(long seed) throws Exception {
        SimulationConfig original = parse(fixture());
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(19, copy.submissions());
        assertRetained(original, copy);
        assertFixture(copy);
        assertEquals(83, original.seed());
        assertRetained(parse(fixture()), original);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1000000})
    void submissionCopiesPreserveEverySettingIncludingNestedRulesAndOrder(int count) throws Exception {
        SimulationConfig original = parse(fixture());
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(83, copy.seed());
        assertRetained(original, copy);
        assertFixture(copy);
        assertEquals(19, original.submissions());
        assertRetained(parse(fixture()), original);
    }

    @Test
    void chainedCopiesAndRepeatedParsingPreserveObservableRealisticSettings() throws Exception {
        SimulationConfig original = parse(fixture());
        SimulationConfig copy = original;
        for (int i = 1; i <= 4; i++) {
            copy = copy.withSeed(-i).withSubmissions(i);
            assertRetained(original, copy);
            assertFixture(copy);
            assertRetained(original, parse(fixture()));
        }
        assertEquals(83, original.seed());
        assertEquals(19, original.submissions());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1000001, Integer.MAX_VALUE})
    void rejectedCopiesLeaveAllRealisticSettingsUnchanged(int count) throws Exception {
        SimulationConfig original = parse(fixture());
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertRetained(parse(fixture()), original);
        assertFixture(original);
        assertEquals(19, original.submissions());
    }

    private static List<SimulationConfig> originalsAndCopies() throws Exception {
        SimulationConfig original = parse(fixture());
        return List.of(original, original.withSeed(17), original.withSubmissions(1));
    }

    @Test
    void profileMapsAndTheirEntriesAndViewsAreUnmodifiable() throws Exception {
        for (SimulationConfig config : originalsAndCopies()) {
            Map<String, Double> profiles = config.profiles();
            assertThrows(UnsupportedOperationException.class, () -> profiles.put("new", 2.0));
            assertThrows(UnsupportedOperationException.class, () -> profiles.remove("urban"));
            assertThrows(UnsupportedOperationException.class, profiles::clear);
            assertThrows(UnsupportedOperationException.class, () -> profiles.entrySet().iterator().next().setValue(9.0));
            assertThrows(UnsupportedOperationException.class, () -> profiles.keySet().remove("rural"));
            assertThrows(UnsupportedOperationException.class, () -> profiles.values().remove(7.5));
            var iterator = profiles.entrySet().iterator();
            iterator.next();
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertFixture(config);
        }
    }

    @Test
    void textPoolMapsAndNestedListsAndViewsAreUnmodifiable() throws Exception {
        for (SimulationConfig config : originalsAndCopies()) {
            Map<String, List<String>> pools = config.textPools();
            assertThrows(UnsupportedOperationException.class, () -> pools.put("place", List.of("changed")));
            assertThrows(UnsupportedOperationException.class, () -> pools.remove("firstName"));
            assertThrows(UnsupportedOperationException.class, pools::clear);
            assertThrows(UnsupportedOperationException.class, () -> pools.entrySet().iterator().next().setValue(List.of("changed")));
            assertThrows(UnsupportedOperationException.class, () -> pools.keySet().remove("place"));
            assertThrows(UnsupportedOperationException.class, () -> pools.values().remove(pools.get("place")));
            for (List<String> texts : pools.values()) {
                assertThrows(UnsupportedOperationException.class, () -> texts.add("changed"));
                assertThrows(UnsupportedOperationException.class, () -> texts.set(0, "changed"));
                assertThrows(UnsupportedOperationException.class, () -> texts.remove(0));
                assertThrows(UnsupportedOperationException.class, () -> texts.subList(0, 1).clear());
                var iterator = texts.listIterator();
                iterator.next();
                assertThrows(UnsupportedOperationException.class, () -> iterator.set("changed"));
                assertThrows(UnsupportedOperationException.class, iterator::remove);
            }
            assertFixture(config);
        }
    }

    @Test
    void rulesAndCollectionsInsideRuleOverridesAreUnmodifiable() throws Exception {
        for (SimulationConfig config : originalsAndCopies()) {
            List<OverrideRule> rules = field(config, "conditional").rules();
            OverrideRule first = rules.get(0);
            assertThrows(UnsupportedOperationException.class, () -> rules.add(first));
            assertThrows(UnsupportedOperationException.class, () -> rules.set(0, rules.get(1)));
            assertThrows(UnsupportedOperationException.class, () -> rules.remove(0));
            assertThrows(UnsupportedOperationException.class, () -> rules.subList(0, 1).clear());
            var iterator = rules.listIterator();
            iterator.next();
            assertThrows(UnsupportedOperationException.class, () -> iterator.set(rules.get(1)));
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            Map<String, Double> weights = rules.get(1).override().weights();
            assertThrows(UnsupportedOperationException.class, () -> weights.put("new", 1.0));
            assertThrows(UnsupportedOperationException.class, () -> weights.entrySet().iterator().next().setValue(5.0));
            List<String> texts = rules.get(2).override().texts();
            assertThrows(UnsupportedOperationException.class, () -> texts.add("new"));
            assertThrows(UnsupportedOperationException.class, () -> texts.set(0, "new"));
            for (OverrideRule rule : rules) {
                assertTrue(rule.override().rules().isEmpty());
                assertThrows(UnsupportedOperationException.class, () -> rule.override().rules().add(first));
            }
            assertRetained(parse(fixture()), config);
        }
    }

    @Test
    void emptyCollectionsRemainEmptyAndUnmodifiableInDefaultsAndCopies() throws Exception {
        SimulationConfig empty = parse("{}");
        for (SimulationConfig config : List.of(SimulationConfig.defaults(), empty, empty.withSeed(2), empty.withSubmissions(1))) {
            assertTrue(config.profiles().isEmpty());
            assertTrue(config.area().isEmpty());
            assertTrue(config.textPools().isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> config.profiles().put("new", 1.0));
            assertThrows(UnsupportedOperationException.class, () -> config.textPools().put("place", List.of("X")));
        }
        FieldOverride answer = override("\"value\":1");
        assertThrows(UnsupportedOperationException.class, () -> answer.rules().add(new OverrideRule("1 = 1", answer)));
    }

    @Test
    void parsingAnotherConfigurationCannotMutatePreviouslyReturnedCollections() throws Exception {
        SimulationConfig original = parse(fixture());
        List<String> places = original.textPools().get("place");
        List<OverrideRule> rules = field(original, "income").rules();
        Map<String, Double> profiles = original.profiles();
        parse("{\"profiles\":{\"other\":1},\"textPools\":{\"place\":[\"other\"]},\"fields\":{\"income\":{\"expr\":\"1\"}}}");
        assertEquals(List.of("Adama", "東京", "Adama"), places);
        assertEquals(List.of("urban", "rural", "unused"), new ArrayList<>(profiles.keySet()));
        assertEquals(2, rules.size());
        assertRetained(parse(fixture()), original);
    }
}
