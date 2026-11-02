package ctosim;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment13Support.*;
import static ctosim.SimulationConfigProblemsCopiesTest.assertOtherSettings;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment13CopiesTest {
    private static final List<String> CODES = List.of("refus\u00e9", "-099", " DK ");

    private static String richJson(boolean codes) throws Exception {
        String previous = SimulationConfigRealisticSupport.fixture();
        return "{" + (codes ? "\"otherCodes\":[\"refus\u00e9\",\"-099\",\" DK \"]," : "")
                + "\"dontKnowCodes\":[\"unknown\",\"-97\"],\"refuseCodes\":[\"R\"],\"otherRate\":0.625,"
                + "\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"villages\":{\"latitude\":\"Lat\",\"longitude\":\"Lon\"},"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"problems\":{\"travel\":0.125,\"leftOpen\":0.625,\"outliers\":0.875,"
                + "\"faults\":[\"other\",\"fakeRefusal\",\"refuse\"]},"
                + previous.substring(previous.indexOf('{') + 1);
    }

    private static void assertEarlierSettingsExceptCopySettings(SimulationConfig expected, SimulationConfig actual) {
        assertOtherSettings(expected, actual);
        assertEquals(expected.consent(), actual.consent());
        assertEquals(expected.villages(), actual.villages());
        assertEquals(expected.dontKnowCodes(), actual.dontKnowCodes());
        assertEquals(expected.refuseCodes(), actual.refuseCodes());
    }

    private static void assertSnapshot(SimulationConfig expected, SimulationConfig actual) {
        assertEarlierSettingsExceptCopySettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(expected.otherCodes(), actual.otherCodes());
        assertEquals(expected.otherRate(), actual.otherRate());
    }

    @Test
    void codesAlonePreserveEveryEarlierDefaultAndDoNotEnableProblems() throws Exception {
        SimulationConfig expected = SimulationConfig.defaults();
        SimulationConfig actual = parse(json(CODES));
        assertEarlierSettingsExceptCopySettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(CODES, actual.otherCodes());
        assertEquals(.03, actual.otherRate());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, .625, 1})
    void rateAlonePreservesEveryEarlierDefaultAndDoesNotEnableProblems(double rate) throws Exception {
        SimulationConfig expected = SimulationConfig.defaults();
        SimulationConfig actual = parse("{\"otherRate\":" + rate + "}");
        assertEarlierSettingsExceptCopySettings(expected, actual);
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertEquals(expected.problems(), actual.problems());
        assertEquals(List.of(), actual.otherCodes());
        assertEquals(rate, actual.otherRate());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingCodesPreservesAllEarlierNondefaultSettings(boolean load) throws Exception {
        SimulationConfig without = parse(richJson(false));
        try (Input input = input(richJson(true), load)) {
            SimulationConfig with = input.read();
            assertEarlierSettingsExceptCopySettings(without, with);
            assertEquals(without.seed(), with.seed());
            assertEquals(without.submissions(), with.submissions());
            assertEquals(without.problems(), with.problems());
            assertEquals(CODES, with.otherCodes());
            assertEquals(List.of(), without.otherCodes());
        }
    }

    static Stream<Arguments> independentSettings() {
        return Stream.of(List.<String>of(), List.of("other"), List.of("DK"), List.of("R", "DK", "extra"))
                .flatMap(special -> Stream.of("", ",\"problems\":{}", ",\"problems\":{\"faults\":[]}",
                        ",\"problems\":{\"faults\":[\"refuse\"]}")
                        .flatMap(problems -> Stream.of(false, true).map(load -> Arguments.of(special, problems, load))));
    }

    @ParameterizedTest
    @MethodSource("independentSettings")
    void specialValuesAndFaultSelectionNeitherFilterNorReplaceCodes(
            List<String> special, String problems, boolean load) throws Exception {
        String specialJson = json(special).replace("\"otherCodes\"", "\"specialValues\"");
        String json = "{\"otherCodes\":[\"DK\",\"R\"]," + specialJson.substring(1, specialJson.length() - 1)
                + problems + "}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("DK", "R"), config.otherCodes());
            assertEquals(special, config.specialValues());
            SimulationConfig baseline = parse("{" + specialJson.substring(1, specialJson.length() - 1) + problems + "}");
            assertEquals(baseline.problems(), config.problems(), "Codes must not change the configured faults");
        }
    }

    static Stream<Arguments> copyCases() {
        return Stream.of(false, true).flatMap(present -> Stream.of(false, true)
                .flatMap(load -> Stream.of("sameSeed", "minSeed", "maxSeed", "sameCount", "minCount", "maxCount",
                        "sameProblems", "defaultProblems", "legacyProblems", "clearProblems")
                        .map(operation -> Arguments.of(present, load, operation))));
    }

    @ParameterizedTest
    @MethodSource("copyCases")
    void everyCopyPreservesPresentOrAbsentCodesAndLeavesItsReceiverUnchanged(
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
            assertEquals(present ? CODES : List.of(), copy.otherCodes());
            assertEarlierSettingsExceptCopySettings(snapshot, copy);
            assertEquals(.625, copy.otherRate());
            assertEquals(seed, copy.seed());
            assertEquals(count, copy.submissions());
            assertEquals(Optional.ofNullable(problems), copy.problems());
            assertThrows(UnsupportedOperationException.class, () -> copy.otherCodes().add("new"));
            assertSnapshot(snapshot, original);
        }
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                Arguments.of("add", (Consumer<List<String>>) list -> list.add("new")),
                Arguments.of("addAll", (Consumer<List<String>>) list -> list.addAll(List.of("new"))),
                Arguments.of("set", (Consumer<List<String>>) list -> list.set(0, "new")),
                Arguments.of("remove", (Consumer<List<String>>) list -> list.remove(0)),
                Arguments.of("removeIf", (Consumer<List<String>>) list -> list.removeIf(code -> true)),
                Arguments.of("clear", (Consumer<List<String>>) List::clear),
                Arguments.of("replaceAll", (Consumer<List<String>>) list -> list.replaceAll(code -> "new-" + code)),
                Arguments.of("sort", (Consumer<List<String>>) list -> list.sort(String::compareTo)),
                Arguments.of("iterator.remove", (Consumer<List<String>>) list -> {
                    var iterator = list.iterator(); iterator.next(); iterator.remove();
                }),
                Arguments.of("listIterator.set", (Consumer<List<String>>) list -> {
                    var iterator = list.listIterator(); iterator.next(); iterator.set("new");
                }),
                Arguments.of("listIterator.add", (Consumer<List<String>>) list -> list.listIterator().add("new")),
                Arguments.of("subList.clear", (Consumer<List<String>>) list -> list.subList(0, 1).clear()))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest(name = "{0}, load={2}")
    @MethodSource("mutations")
    void codesAndTheirViewsAreUnmodifiable(String operation, Consumer<List<String>> mutation, boolean load)
            throws Exception {
        try (Input input = input(json(CODES), load)) {
            SimulationConfig original = input.read();
            SimulationConfig copy = original.withSeed(91);
            assertThrows(UnsupportedOperationException.class, () -> mutation.accept(original.otherCodes()), operation);
            assertEquals(CODES, original.otherCodes());
            assertEquals(CODES, copy.otherCodes());
            assertThrows(UnsupportedOperationException.class, () -> mutation.accept(copy.otherCodes()), operation);
            assertEquals(CODES, original.otherCodes());
            assertEquals(CODES, copy.otherCodes());
        }
    }

    @Test
    void absentCodeListsCannotBePopulatedThroughTheAccessor() throws Exception {
        for (SimulationConfig config : List.of(SimulationConfig.defaults(), parse("{}"))) {
            assertThrows(UnsupportedOperationException.class, () -> config.otherCodes().add("DK"));
            assertEquals(List.of(), config.otherCodes());
        }
    }

    @Test
    void changingAnArraySnapshotCannotChangeTheCodes() throws Exception {
        SimulationConfig config = parse(json(CODES));
        String[] snapshot = config.otherCodes().toArray(String[]::new);
        snapshot[0] = "changed";
        assertEquals(CODES, config.otherCodes());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void chainedCopiesKeepCodesWhenProblemsAreAddedReplacedAndCleared(boolean present) throws Exception {
        SimulationConfig original = parse(present ? json(CODES) : "{}");
        SimulationConfig enabled = original.withProblems(ProblemRates.defaults());
        SimulationConfig changed = enabled.withSeed(Long.MAX_VALUE).withSubmissions(1_000_000)
                .withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, 0));
        SimulationConfig cleared = changed.withProblems(null).withSeed(Long.MIN_VALUE).withSubmissions(1);
        for (SimulationConfig config : List.of(original, enabled, changed, cleared)) {
            assertEquals(present ? CODES : List.of(), config.otherCodes());
            assertThrows(UnsupportedOperationException.class, () -> config.otherCodes().add("new"));
        }
        assertTrue(original.problems().isEmpty());
        assertTrue(cleared.problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1_000_001, Integer.MAX_VALUE})
    void aRejectedSubmissionCopyLeavesCodesAndOtherSettingsUnchanged(int count) throws Exception {
        SimulationConfig original = parse(richJson(true));
        SimulationConfig snapshot = parse(richJson(true));
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertSnapshot(snapshot, original);
    }

    @Test
    void parsingOtherConfigsCannotReplacePreviouslyReturnedCodes() throws Exception {
        SimulationConfig first = parse(json(CODES));
        List<String> retained = first.otherCodes();
        SimulationConfig second = parse(json(List.of("different")));
        SimulationConfig absent = parse("{}");
        assertEquals(CODES, retained);
        assertEquals(CODES, first.otherCodes());
        assertEquals(List.of("different"), second.otherCodes());
        assertEquals(List.of(), absent.otherCodes());
        assertEquals(List.of(), SimulationConfig.defaults().otherCodes());
    }

    static Stream<Arguments> rateCopyCases() {
        return Stream.of("", ",\"otherRate\":0", ",\"otherRate\":1", ",\"otherRate\":0.1234567890123456")
                .flatMap(rate -> Stream.of(false, true).flatMap(codes -> Stream.of(false, true)
                        .map(load -> Arguments.of(rate, codes, load))));
    }

    @ParameterizedTest
    @MethodSource("rateCopyCases")
    void allCopyOperationsAndChainsPreserveDefaultZeroOneAndPreciseRates(String rate, boolean codes, boolean load)
            throws Exception {
        String json = "{\"seed\":31" + rate + (codes ? ",\"otherCodes\":[\"96\",\"9996\"]" : "") + "}";
        double expectedRate = rate.isEmpty() ? .03 : Double.parseDouble(rate.substring(rate.indexOf(':') + 1));
        List<String> expectedCodes = codes ? List.of("96", "9996") : List.of();
        try (Input input = input(json, load)) {
            SimulationConfig original = input.read();
            for (SimulationConfig copy : List.of(original.withSeed(original.seed()), original.withSeed(Long.MIN_VALUE),
                    original.withSubmissions(original.submissions()), original.withSubmissions(1_000_000),
                    original.withProblems(null), original.withProblems(ProblemRates.defaults()),
                    original.withProblems(ProblemRates.defaults()).withSeed(Long.MAX_VALUE).withSubmissions(1)
                            .withProblems(null))) {
                assertNotSame(original, copy);
                assertEquals(expectedRate, copy.otherRate());
                assertEquals(expectedCodes, copy.otherCodes());
                assertThrows(UnsupportedOperationException.class, () -> copy.otherCodes().add("new"));
            }
            assertEquals(expectedRate, original.otherRate());
            assertEquals(expectedCodes, original.otherCodes());
            assertEquals(31, original.seed());
            assertEquals(100, original.submissions());
            assertTrue(original.problems().isEmpty());
        }
    }

    @Test
    void parsingOtherRatesDoesNotChangeEarlierConfigsOrDefaults() throws Exception {
        SimulationConfig first = parse("{\"otherCodes\":[\"96\"],\"otherRate\":0.625}");
        SimulationConfig second = parse("{\"otherRate\":0}");
        SimulationConfig third = parse("{\"otherRate\":1}");
        assertEquals(.625, first.otherRate());
        assertEquals(List.of("96"), first.otherCodes());
        assertEquals(0, second.otherRate());
        assertEquals(1, third.otherRate());
        assertEquals(.03, parse("{}").otherRate());
        assertEquals(.03, SimulationConfig.defaults().otherRate());
    }
}
