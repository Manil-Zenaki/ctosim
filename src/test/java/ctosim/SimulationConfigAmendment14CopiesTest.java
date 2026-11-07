package ctosim;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment14Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment14CopiesTest {
    private static SimulationConfig parse(String json) throws ConfigException {
        return SimulationConfig.parse(json, "update-copies.json");
    }

    private static String richJson(String update) throws Exception {
        String previous = SimulationConfigRealisticSupport.fixture();
        return "{" + (update == null ? "" : "\"formUpdate\":" + update + ",")
                + "\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"villages\":{\"latitude\":\"Lat\",\"longitude\":\"Lon\"},"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"dontKnowCodes\":[\"D\"],\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"],\"otherRate\":0.625,"
                + "\"problems\":{\"travel\":0.125,\"leftOpen\":0.625,\"outliers\":0.875,"
                + "\"faults\":[\"outdatedVersion\",\"other\",\"refuse\"]},"
                + previous.substring(previous.indexOf('{') + 1);
    }

    private static void assertEarlierSettingsExceptCopySettings(SimulationConfig expected, SimulationConfig actual) {
        SimulationConfigRealisticSupport.assertRetained(expected, actual);
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.interviewsPerDay(), actual.interviewsPerDay());
        assertEquals(expected.consent(), actual.consent());
        assertEquals(expected.villages(), actual.villages());
        assertEquals(expected.dontKnowCodes(), actual.dontKnowCodes());
        assertEquals(expected.refuseCodes(), actual.refuseCodes());
        assertEquals(expected.otherCodes(), actual.otherCodes());
        assertEquals(expected.otherRate(), actual.otherRate());
    }

    private static void assertSnapshot(SimulationConfig expected, SimulationConfig actual) {
        assertEarlierSettingsExceptCopySettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(expected.formUpdate(), actual.formUpdate());
    }

    @Test
    void anUpdateAlonePreservesEveryEarlierDefaultAndDoesNotEnableProblems() throws Exception {
        SimulationConfig expected = SimulationConfig.defaults();
        SimulationConfig actual = parse("{\"formUpdate\":" + update("2026-01-05", "v1") + "}");
        assertEarlierSettingsExceptCopySettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(new FormUpdate(LocalDate.of(2026, 1, 5), "v1"), actual.formUpdate().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingAnUpdatePreservesEveryEarlierNondefaultSetting(boolean load) throws Exception {
        SimulationConfig expected = parse(richJson(null));
        try (Input input = input(richJson(update("2028-02-29", " v01 ")), load)) {
            SimulationConfig actual = input.read();
            assertEarlierSettingsExceptCopySettings(expected, actual);
            assertEquals(expected.seed(), actual.seed());
            assertEquals(expected.submissions(), actual.submissions());
            assertEquals(expected.problems(), actual.problems());
            assertEquals(new FormUpdate(LocalDate.of(2028, 2, 29), " v01 "), actual.formUpdate().orElseThrow());
            assertTrue(expected.formUpdate().isEmpty());
        }
    }

    static Stream<Arguments> datesAndFieldwork() {
        return Stream.of("2025-12-31", "2026-01-05", "2026-01-06", "2028-02-29")
                .flatMap(date -> Stream.of("UTC", "Pacific/Kiritimati", "-12:00")
                        .flatMap(zone -> Stream.of(false, true).map(load -> Arguments.of(date, zone, load))));
    }

    @ParameterizedTest
    @MethodSource("datesAndFieldwork")
    void updateDateIsNotRestrictedToFieldworkOrShiftedByTimeZone(String date, String zone, boolean load)
            throws Exception {
        String json = "{\"start\":\"2026-01-05T23:59:59\",\"fieldworkDays\":1,\"timeZone\":" + quote(zone)
                + ",\"formUpdate\":" + update(date, null) + "}";
        try (Input input = input(json, load)) {
            assertEquals(LocalDate.parse(date), input.read().formUpdate().orElseThrow().date());
        }
    }

    static Stream<Arguments> copyCases() {
        return Stream.of("absent", "dateOnly", "withVersion").flatMap(state -> Stream.of(false, true)
                .flatMap(load -> Stream.of("sameSeed", "minSeed", "maxSeed", "sameCount", "minCount", "maxCount",
                        "sameProblems", "defaultProblems", "legacyProblems", "newFaultProblems", "clearProblems")
                        .map(operation -> Arguments.of(state, load, operation))));
    }

    @ParameterizedTest
    @MethodSource("copyCases")
    void everyCopyPreservesTheCompleteUpdateAndLeavesTheReceiverUnchanged(String state, boolean load, String operation)
            throws Exception {
        String update = state.equals("absent") ? null
                : update("2028-02-29", state.equals("dateOnly") ? null : " v01 \u6771\u4eac ");
        String json = richJson(update);
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
                case "newFaultProblems" -> {
                    problems = new ProblemRates(0, 0, 0, 0, 0, 0, 0, 0, List.of("outdatedVersion"));
                    copy = original.withProblems(problems);
                }
                case "clearProblems" -> { problems = null; copy = original.withProblems(null); }
                default -> throw new AssertionError(operation);
            }
            assertNotSame(original, copy);
            assertEquals(snapshot.formUpdate(), copy.formUpdate());
            assertEarlierSettingsExceptCopySettings(snapshot, copy);
            assertEquals(seed, copy.seed());
            assertEquals(count, copy.submissions());
            assertEquals(Optional.ofNullable(problems), copy.problems());
            assertSnapshot(snapshot, original);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "dateOnly", "withVersion"})
    void chainedCopiesPreserveUpdatesWhenProblemsAreAddedAndCleared(String state) throws Exception {
        SimulationConfig original = parse(state.equals("absent") ? "{}" : "{\"formUpdate\":"
                + update("2028-02-29", state.equals("dateOnly") ? null : "001") + "}");
        SimulationConfig enabled = original.withProblems(ProblemRates.defaults());
        SimulationConfig changed = enabled.withSeed(Long.MAX_VALUE).withSubmissions(1_000_000)
                .withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, 0));
        SimulationConfig cleared = changed.withProblems(null).withSeed(Long.MIN_VALUE).withSubmissions(1);
        for (SimulationConfig config : List.of(enabled, changed, cleared)) {
            assertEquals(original.formUpdate(), config.formUpdate());
        }
        assertTrue(original.problems().isEmpty());
        assertTrue(cleared.problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1_000_001})
    void rejectedSubmissionCopyLeavesTheUpdateAndEveryOtherSettingUnchanged(int count) throws Exception {
        String json = richJson(update("2028-02-29", "v1"));
        SimulationConfig original = parse(json);
        SimulationConfig snapshot = parse(json);
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertSnapshot(snapshot, original);
    }

    @Test
    void parsingAnotherConfigurationCannotChangeAnEarlierUpdateOrDefaults() throws Exception {
        SimulationConfig first = parse("{\"formUpdate\":" + update("2026-01-05", "v1") + "}");
        FormUpdate retained = first.formUpdate().orElseThrow();
        SimulationConfig second = parse("{\"formUpdate\":" + update("2028-02-29", null) + "}");
        SimulationConfig absent = parse("{}");
        assertEquals(new FormUpdate(LocalDate.of(2026, 1, 5), "v1"), retained);
        assertEquals(retained, first.formUpdate().orElseThrow());
        assertEquals(new FormUpdate(LocalDate.of(2028, 2, 29), null), second.formUpdate().orElseThrow());
        assertTrue(absent.formUpdate().isEmpty());
        assertTrue(SimulationConfig.defaults().formUpdate().isEmpty());
    }
}
