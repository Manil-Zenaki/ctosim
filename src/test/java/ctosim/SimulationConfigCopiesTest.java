package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigCopiesTest {
    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -9007199254740993L, -1, 0, 1, 9007199254740993L, Long.MAX_VALUE})
    void withSeedReturnsANewConfigurationAndChangesOnlyTheSeed(long seed) throws Exception {
        SimulationConfig original = complete();
        SimulationConfig expectedOriginal = complete();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(321, copy.submissions());
        assertSameSettingsExceptSeedAndSubmissions(expectedOriginal, copy);
        assertSameSettings(expectedOriginal, original);
        assertEquals(-9007199254740993L, original.seed());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 321, 1000000})
    void withSubmissionsReturnsANewConfigurationAndChangesOnlyTheCount(int submissions) throws Exception {
        SimulationConfig original = complete();
        SimulationConfig expectedOriginal = complete();
        SimulationConfig copy = original.withSubmissions(submissions);
        assertNotSame(original, copy);
        assertEquals(submissions, copy.submissions());
        assertEquals(-9007199254740993L, copy.seed());
        assertSameSettingsExceptSeedAndSubmissions(expectedOriginal, copy);
        assertSameSettings(expectedOriginal, original);
        assertEquals(321, original.submissions());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1000001, Integer.MAX_VALUE})
    void withSubmissionsRejectsOutOfRangeCountsWithoutChangingTheReceiver(int submissions) throws Exception {
        SimulationConfig original = complete();
        SimulationConfig expected = complete();
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(submissions));
        assertSameSettings(expected, original);
    }

    @Test
    void chainedCopiesKeepIntermediateConfigurationsUnchanged() throws Exception {
        SimulationConfig original = complete();
        SimulationConfig first = original.withSeed(27);
        SimulationConfig second = first.withSubmissions(987);
        SimulationConfig third = second.withSeed(-5);
        assertEquals(-9007199254740993L, original.seed());
        assertEquals(321, original.submissions());
        assertEquals(27, first.seed());
        assertEquals(321, first.submissions());
        assertEquals(27, second.seed());
        assertEquals(987, second.submissions());
        assertEquals(-5, third.seed());
        assertEquals(987, third.submissions());
        assertSameSettingsExceptSeedAndSubmissions(original, third);
    }

    @Test
    void copyingDefaultsCannotChangeCurrentOrFutureDefaults() {
        SimulationConfig defaults = SimulationConfig.defaults();
        SimulationConfig copy = defaults.withSeed(8).withSubmissions(9);
        assertEquals(8, copy.seed());
        assertEquals(9, copy.submissions());
        assertEquals(1, defaults.seed());
        assertEquals(100, defaults.submissions());
        assertSameSettings(defaults, SimulationConfig.defaults());
        assertSameSettingsExceptSeedAndSubmissions(defaults, copy);
    }

    private static Arguments mutation(String name, Consumer<SimulationConfig> action) {
        return Arguments.of(name, action);
    }

    static Stream<Arguments> collectionMutations() {
        return Stream.of(
                mutation("specialValues.add", c -> c.specialValues().add("new")),
                mutation("specialValues.set", c -> c.specialValues().set(0, "new")),
                mutation("specialValues.remove", c -> c.specialValues().remove(0)),
                mutation("specialValues.clear", c -> c.specialValues().clear()),
                mutation("specialValues.iterator.remove", c -> {
                    Iterator<String> it = c.specialValues().iterator(); it.next(); it.remove();
                }),
                mutation("specialValues.subList.clear", c -> c.specialValues().subList(0, 1).clear()),
                mutation("texts.add", c -> field(c, "village_name").texts().add("new")),
                mutation("texts.set", c -> field(c, "village_name").texts().set(0, "new")),
                mutation("texts.remove", c -> field(c, "village_name").texts().remove(0)),
                mutation("texts.clear", c -> field(c, "village_name").texts().clear()),
                mutation("texts.listIterator.set", c -> {
                    ListIterator<String> it = field(c, "village_name").texts().listIterator();
                    it.next(); it.set("new");
                }),
                mutation("texts.subList.clear", c -> field(c, "village_name").texts().subList(0, 1).clear()),
                mutation("weights.put", c -> field(c, "region").weights().put("new", 2.0)),
                mutation("weights.replace", c -> field(c, "region").weights().replace("z", 2.0)),
                mutation("weights.remove", c -> field(c, "region").weights().remove("z")),
                mutation("weights.clear", c -> field(c, "region").weights().clear()),
                mutation("weights.entry.setValue", c ->
                        field(c, "region").weights().entrySet().iterator().next().setValue(2.0)),
                mutation("weights.entrySet.clear", c -> field(c, "region").weights().entrySet().clear()),
                mutation("weights.keySet.remove", c -> field(c, "region").weights().keySet().remove("z")),
                mutation("weights.values.remove", c -> field(c, "region").weights().values().remove(0.0)),
                mutation("weights.iterator.remove", c -> {
                    Iterator<Map.Entry<String, Double>> it = field(c, "region").weights().entrySet().iterator();
                    it.next(); it.remove();
                }),
                mutation("weights.compute", c -> field(c, "region").weights().compute("z", (k, v) -> 9.0)),
                mutation("weights.replaceAll", c -> field(c, "region").weights().replaceAll((k, v) -> 9.0)),
                mutation("overriddenFields.add", c -> c.overriddenFields().add("new")),
                mutation("overriddenFields.remove", c -> c.overriddenFields().remove("age")),
                mutation("overriddenFields.clear", c -> c.overriddenFields().clear()),
                mutation("overriddenFields.iterator.remove", c -> {
                    Iterator<String> it = c.overriddenFields().iterator(); it.next(); it.remove();
                }),
                mutation("overriddenFields.headSet.clear", c -> c.overriddenFields().headSet("dob").clear()),
                mutation("overriddenFields.tailSet.clear", c -> c.overriddenFields().tailSet("dob").clear()),
                mutation("overriddenFields.subSet.clear", c -> c.overriddenFields().subSet("age", "phone").clear()));
    }

    @ParameterizedTest(name = "unmodifiable {0}")
    @MethodSource("collectionMutations")
    void everyCollectionAndItsViewsRejectActualModifications(String description,
                                                              Consumer<SimulationConfig> mutation) throws Exception {
        SimulationConfig config = complete();
        SimulationConfig expected = complete();
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(config), description);
        assertSameSettings(expected, config);
    }

    @ParameterizedTest(name = "copied configuration has unmodifiable {0}")
    @MethodSource("collectionMutations")
    void copiesKeepCollectionsAndViewsUnmodifiable(String description,
                                                   Consumer<SimulationConfig> mutation) throws Exception {
        SimulationConfig original = complete();
        SimulationConfig copy = original.withSeed(9).withSubmissions(10);
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(copy), description);
        assertSameSettingsExceptSeedAndSubmissions(original, copy);
    }

    @Test
    void emptyCollectionsAndDefaultCollectionsAlsoRejectAdditions() throws Exception {
        SimulationConfig defaults = SimulationConfig.defaults();
        assertThrows(UnsupportedOperationException.class, () -> defaults.specialValues().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> defaults.overriddenFields().add("new"));
        SimulationConfig empty = parse("{\"specialValues\":[]}");
        assertThrows(UnsupportedOperationException.class, () -> empty.specialValues().add("new"));
        FieldOverride fixed = override("\"value\":\"x\"");
        assertThrows(UnsupportedOperationException.class, () -> fixed.texts().add("new"));
        assertThrows(UnsupportedOperationException.class, () -> fixed.weights().put("new", 1.0));
    }
}
