package ctosim;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment15CopiesTest {
    private static SimulationConfig parse(String json) throws ConfigException {
        return SimulationConfig.parse(json, "visit-copies.json");
    }

    private static String planJson(String state) {
        return switch (state) {
            case "absent" -> "{}";
            case "defaults" -> visits("");
            case "custom" -> visits("\"available\":\" 01 \u6771\u4eac \",\"number\":\"Visit number\","
                    + "\"revisitRate\":0.625,\"unreachedRate\":0.125,\"maxVisits\":10");
            case "zeroRates" -> visits("\"revisitRate\":0,\"unreachedRate\":0,\"maxVisits\":2");
            default -> throw new AssertionError(state);
        };
    }

    private static Optional<VisitPlan> expectedPlan(String state) {
        return switch (state) {
            case "absent" -> Optional.empty();
            case "defaults" -> Optional.of(new VisitPlan("availability", "1", null, 0.1, 0.025, 3));
            case "custom" -> Optional.of(new VisitPlan("availability", " 01 \u6771\u4eac ", "Visit number", 0.625, 0.125, 10));
            case "zeroRates" -> Optional.of(new VisitPlan("availability", "1", null, 0, 0, 2));
            default -> throw new AssertionError(state);
        };
    }

    private static String richJson(String state) throws Exception {
        String earlier = SimulationConfigRealisticSupport.fixture();
        String plan = planJson(state);
        return "{" + (state.equals("absent") ? "" : plan.substring(1, plan.length() - 1) + ",")
                + "\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"villages\":{\"latitude\":\"Lat\",\"longitude\":\"Lon\"},"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"dontKnowCodes\":[\"D\"],\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"],\"otherRate\":0.625,"
                + "\"formUpdate\":{\"date\":\"2028-02-29\",\"previousVersion\":\"v01\"},"
                + "\"problems\":{\"travel\":0.125,\"leftOpen\":0.625,\"outliers\":0.875,"
                + "\"faults\":[\"outdatedVersion\",\"other\",\"refuse\"]},"
                + earlier.substring(earlier.indexOf('{') + 1);
    }

    private static void assertEarlierSettingsExceptCopyTargets(SimulationConfig expected, SimulationConfig actual) {
        SimulationConfigRealisticSupport.assertRetained(expected, actual);
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.interviewsPerDay(), actual.interviewsPerDay());
        assertEquals(expected.consent(), actual.consent());
        assertEquals(expected.villages(), actual.villages());
        assertEquals(expected.dontKnowCodes(), actual.dontKnowCodes());
        assertEquals(expected.refuseCodes(), actual.refuseCodes());
        assertEquals(expected.otherCodes(), actual.otherCodes());
        assertEquals(expected.otherRate(), actual.otherRate());
        assertEquals(expected.formUpdate(), actual.formUpdate());
    }

    private static void assertAllSettings(SimulationConfig expected, SimulationConfig actual) {
        assertEarlierSettingsExceptCopyTargets(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(expected.visits(), actual.visits());
    }

    @ParameterizedTest
    @ValueSource(strings = {"defaults", "custom", "zeroRates"})
    void aVisitPlanAloneDoesNotChangeAnyEarlierDefaultOrRequireAnId(String state) throws Exception {
        SimulationConfig expected = SimulationConfig.defaults();
        SimulationConfig actual = parse(planJson(state));
        assertEarlierSettingsExceptCopyTargets(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(expectedPlan(state), actual.visits());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingVisitsPreservesAllExistingNondefaultSettings(boolean load) throws Exception {
        SimulationConfig expected = parse(richJson("absent"));
        try (Input input = input(richJson("custom"), load)) {
            SimulationConfig actual = input.read();
            assertEarlierSettingsExceptCopyTargets(expected, actual);
            assertEquals(expected.seed(), actual.seed());
            assertEquals(expected.submissions(), actual.submissions());
            assertEquals(expected.problems(), actual.problems());
            assertEquals(expectedPlan("custom"), actual.visits());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void visitRatesHaveNoSumOrCapacityConstraintWithUnrelatedSettings(boolean load) throws Exception {
        String json = "{\"submissions\":1,\"fieldworkDays\":1,\"interviewsPerDay\":1,\"enumerators\":1,"
                + "\"missingRate\":1,\"specialRate\":1,\"otherRate\":1,\"speedViolationRate\":1,"
                + "\"problems\":{\"reinterview\":1},\"visits\":{\"field\":\"availability\","
                + "\"revisitRate\":0.5,\"unreachedRate\":0.5,\"maxVisits\":10}}";
        try (Input input = input(json, load)) {
            assertEquals(new VisitPlan("availability", "1", null, 0.5, 0.5, 10), input.read().visits().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void availableMayEqualAFieldNameOrACodeInAnotherSetting(boolean load) throws Exception {
        String json = "{\"visits\":{\"field\":\"availability\",\"number\":\"visit_number\",\"available\":\"availability\"},"
                + "\"dontKnowCodes\":[\"availability\"],\"specialValues\":[\"availability\"],"
                + "\"consent\":{\"fields\":[\"availability\"],\"yes\":\"availability\"},"
                + "\"fields\":{\"availability\":{\"value\":\"unavailable\"}}}";
        try (Input input = input(json, load)) {
            assertEquals("availability", input.read().visits().orElseThrow().available());
        }
    }

    static Stream<Arguments> copyCases() {
        return bothReaders(Stream.of("absent", "defaults", "custom", "zeroRates").flatMap(state -> Stream.of(
                "sameSeed", "minSeed", "maxSeed", "sameCount", "minCount", "maxCount",
                "sameProblems", "defaultProblems", "zeroProblems", "clearProblems")
                .map(operation -> Arguments.of(state, operation))));
    }

    @ParameterizedTest
    @MethodSource("copyCases")
    void everyCopyRetainsTheCompleteVisitPlanAndLeavesTheReceiverUnchanged(String state, String operation, boolean load)
            throws Exception {
        String json = richJson(state);
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
                case "zeroProblems" -> {
                    problems = new ProblemRates(0, 0, 0, 0, 0, 0, 0);
                    copy = original.withProblems(problems);
                }
                case "clearProblems" -> { problems = null; copy = original.withProblems(null); }
                default -> throw new AssertionError(operation);
            }
            assertNotSame(original, copy);
            assertEquals(expectedPlan(state), copy.visits());
            assertEarlierSettingsExceptCopyTargets(snapshot, copy);
            assertEquals(seed, copy.seed());
            assertEquals(count, copy.submissions());
            assertEquals(Optional.ofNullable(problems), copy.problems());
            assertAllSettings(snapshot, original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "defaults", "custom", "zeroRates"})
    void chainsKeepVisitsWhenProblemsAreAddedReplacedAndCleared(String state) throws Exception {
        SimulationConfig original = parse(planJson(state));
        SimulationConfig added = original.withProblems(ProblemRates.defaults());
        SimulationConfig changed = added.withSeed(Long.MAX_VALUE).withSubmissions(1_000_000)
                .withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, 0));
        SimulationConfig cleared = changed.withProblems(null).withSeed(Long.MIN_VALUE).withSubmissions(1);
        for (SimulationConfig config : List.of(original, added, changed, cleared)) {
            assertEquals(expectedPlan(state), config.visits());
        }
        assertTrue(original.problems().isEmpty());
        assertTrue(cleared.problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1_000_001})
    void rejectedCopyCannotChangeVisitsOrOtherSettings(int count) throws Exception {
        SimulationConfig original = parse(richJson("custom"));
        SimulationConfig snapshot = parse(richJson("custom"));
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertAllSettings(snapshot, original);
    }

    @Test
    void parsingAnotherPlanDoesNotMutateAnEarlierPlanOrDefaults() throws Exception {
        SimulationConfig first = parse(planJson("custom"));
        VisitPlan retained = first.visits().orElseThrow();
        SimulationConfig second = parse(planJson("defaults"));
        assertEquals(expectedPlan("defaults"), second.visits());
        assertEquals(expectedPlan("custom").orElseThrow(), retained);
        assertEquals(expectedPlan("custom"), first.visits());
        assertTrue(parse("{}").visits().isEmpty());
        assertTrue(SimulationConfig.defaults().visits().isEmpty());
    }
}
