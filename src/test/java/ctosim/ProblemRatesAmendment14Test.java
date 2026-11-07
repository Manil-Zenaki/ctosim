package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment14Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment14Test {
    private static ProblemRates rates(List<String> faults, boolean legacy) {
        return legacy ? new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .88, faults)
                : new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .99, .88, faults);
    }

    @Test
    void problemRateDefaultsHaveExactlyTheSevenFaultsInTheSpecifiedOrder() {
        assertEquals(DEFAULT_FAULTS, ProblemRates.defaults().faults());
    }

    @Test
    void theNewDefaultFaultLeavesAllDefaultRatesUnchanged() {
        ProblemRates defaults = ProblemRates.defaults();
        assertAll(
                () -> assertEquals(.01, defaults.resent()),
                () -> assertEquals(.01, defaults.reinterview()),
                () -> assertEquals(.02, defaults.shortDuration()),
                () -> assertEquals(.02, defaults.longDuration()),
                () -> assertEquals(.02, defaults.offHours()),
                () -> assertEquals(.02, defaults.overlap()),
                () -> assertEquals(.02, defaults.travel()),
                () -> assertEquals(.02, defaults.leftOpen()),
                () -> assertEquals(.02, defaults.outliers()));
    }

    static Stream<Arguments> defaultInputs() {
        return Stream.of("{\"problems\":{}}", "{\"problems\":{\"travel\":0}}",
                "{\"problems\":{},\"formUpdate\":{\"date\":\"2026-01-05\"}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("defaultInputs")
    void omittedFaultsDefaultToSevenRegardlessOfUpdatePresence(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(DEFAULT_FAULTS, input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<List<String>> selections() {
        return Stream.of(List.<String>of(), List.of("outdatedVersion"), List.of("other"),
                List.of("outdatedVersion", "other"), List.of("other", "outdatedVersion"),
                DEFAULT_FAULTS, List.of("outdatedVersion", "other", "refuse", "fakeRefusal",
                        "dontKnow", "skipModules", "tooFast"));
    }

    static Stream<Arguments> fileSelections() {
        return selections().flatMap(faults -> Stream.of(false, true)
                .flatMap(update -> Stream.of(false, true).map(load -> Arguments.of(faults, update, load))));
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void explicitFaultSelectionsKeepOrderWithoutAddingOrRemovingFaults(
            List<String> faults, boolean hasUpdate, boolean load) throws Exception {
        String json = "{\"problems\":{\"faults\":" + array(faults) + "}"
                + (hasUpdate ? ",\"formUpdate\":" + update("2026-01-05", null) : "") + "}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(faults, config.problems().orElseThrow().faults());
            assertEquals(hasUpdate, config.formUpdate().isPresent());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void escapedOutdatedVersionFaultIsDecoded(boolean load) throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":[\"outdated\\u0056ersion\"]}}", load)) {
            assertEquals(List.of("outdatedVersion"), input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<Arguments> constructorSelections() {
        return selections().flatMap(faults -> Stream.of(false, true).map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("constructorSelections")
    void bothListTakingConstructorsAcceptTheNewFaultAndKeepTheSuppliedOrder(List<String> faults, boolean legacy) {
        assertEquals(faults, rates(faults, legacy).faults());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptingTheNewFaultPreservesEveryConstructorRate(boolean legacy) {
        ProblemRates actual = rates(List.of("outdatedVersion"), legacy);
        assertAll(
                () -> assertEquals(.11, actual.resent()),
                () -> assertEquals(.22, actual.reinterview()),
                () -> assertEquals(.33, actual.shortDuration()),
                () -> assertEquals(.44, actual.longDuration()),
                () -> assertEquals(.55, actual.offHours()),
                () -> assertEquals(.66, actual.overlap()),
                () -> assertEquals(.77, actual.travel()),
                () -> assertEquals(legacy ? 0 : .99, actual.leftOpen()),
                () -> assertEquals(.88, actual.outliers()));
    }

    @Test
    void theSevenRateConstructorStillEnablesNoFaults() {
        assertEquals(List.of(), new ProblemRates(0, 0, 0, 0, 0, 0, 0).faults());
    }

    static Stream<Arguments> invalidFileFaults() {
        Stream<Arguments> entries = Stream.of("\"OutdatedVersion\"", "\"outdatedversion\"", "\"outdatedVersions\"",
                "\"outdatedVersion \"", "\" outdatedVersion\"", "\"outdatedVersion\\t\"", "\"\"",
                "null", "1", "true", "{}", "[]")
                .map(token -> Arguments.of("[\"outdatedVersion\"," + token + "]", "problems.faults[1]"));
        Stream<Arguments> duplicates = Stream.of(
                Arguments.of("[\"outdatedVersion\",\"outdatedVersion\"]", "problems.faults[1]"),
                Arguments.of("[\"outdatedVersion\",\"other\",\"outdatedVersion\"]", "problems.faults[2]"),
                Arguments.of("[\"outdatedVersion\",\"outdated\\u0056ersion\"]", "problems.faults[1]"));
        Stream<Arguments> nonArrays = Stream.of("\"outdatedVersion\"", "{\"outdatedVersion\":1}")
                .map(token -> Arguments.of(token, "problems.faults"));
        return Stream.of(entries, duplicates, nonArrays).flatMap(stream -> stream)
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidFileFaults")
    void invalidOrRepeatedNewFaultReportsItsLocation(String faults, String path, boolean load) throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":" + faults + "}}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> invalidConstructorFaults() {
        return Stream.of(List.of("OutdatedVersion"), List.of("outdatedversion"), List.of("outdatedVersions"),
                List.of("outdatedVersion "), List.of(" outdatedVersion"), List.of("outdatedVersion\t"),
                List.of("outdatedVersion", "outdatedVersion"),
                List.of("outdatedVersion", "other", new String("outdatedVersion")))
                .flatMap(faults -> Stream.of(false, true).map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorFaults")
    void bothConstructorsRejectMisspelledOrRepeatedNewFault(List<String> faults, boolean legacy) {
        assertThrows(IllegalArgumentException.class, () -> rates(faults, legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nullElementsAlongsideTheNewFaultStillFail(boolean legacy) {
        assertThrows(NullPointerException.class, () -> rates(Arrays.asList("outdatedVersion", null), legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void constructorsDefensivelyCopyListsContainingTheNewFault(boolean legacy) {
        List<String> source = new ArrayList<>(List.of("outdatedVersion", "other"));
        ProblemRates result = rates(source, legacy);
        source.set(0, "tooFast");
        source.clear();
        assertEquals(List.of("outdatedVersion", "other"), result.faults());
        assertThrows(UnsupportedOperationException.class, () -> result.faults().set(0, "tooFast"));
        assertThrows(UnsupportedOperationException.class, () -> result.faults().subList(0, 1).clear());
    }

    @Test
    void theSevenDefaultFaultsAreUnmodifiable() {
        ProblemRates defaults = ProblemRates.defaults();
        assertThrows(UnsupportedOperationException.class, () -> defaults.faults().remove("outdatedVersion"));
        assertEquals(DEFAULT_FAULTS, defaults.faults());
    }

    static Stream<Arguments> misplacedFault() {
        return Stream.of(Arguments.of("{\"outdatedVersion\":0.5}", "outdatedVersion"),
                Arguments.of("{\"problems\":{\"outdatedVersion\":0.5}}", "problems.outdatedVersion"))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("misplacedFault")
    void outdatedVersionIsAFaultNameAndDoesNotAddARateKey(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void parsedFaultListsRemainUnmodifiableAfterCopies(List<String> faults, boolean hasUpdate, boolean load)
            throws Exception {
        String json = "{\"problems\":{\"faults\":" + array(faults) + "}"
                + (hasUpdate ? ",\"formUpdate\":" + update("2026-01-05", "v1") : "") + "}";
        try (Input input = input(json, load)) {
            SimulationConfig original = input.read();
            for (SimulationConfig config : List.of(original, original.withSeed(42), original.withSubmissions(1),
                    original.withProblems(original.problems().orElseThrow()))) {
                List<String> actual = config.problems().orElseThrow().faults();
                assertEquals(faults, actual);
                assertThrows(UnsupportedOperationException.class, () -> actual.add("new"));
                if (actual.contains("outdatedVersion")) {
                    assertThrows(UnsupportedOperationException.class, () -> actual.remove("outdatedVersion"));
                }
                assertEquals(original.formUpdate(), config.formUpdate());
            }
        }
    }
}
