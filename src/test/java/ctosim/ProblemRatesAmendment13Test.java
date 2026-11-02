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

import static ctosim.SimulationConfigAmendment13Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment13Test {
    private static final List<String> DEFAULT_FAULTS =
            List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse", "other");

    private static ProblemRates rates(List<String> faults, boolean legacy) {
        return legacy ? new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .88, faults)
                : new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .99, .88, faults);
    }

    @Test
    void problemRateDefaultsHaveExactlyTheSixFaultsInTheSpecifiedOrder() {
        assertEquals(DEFAULT_FAULTS, ProblemRates.defaults().faults());
    }

    @Test
    void addingTheSixthDefaultFaultKeepsAllDefaultRates() {
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anEscapedOtherFaultNameIsDecodedBeforeValidation(boolean load) throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":[\"oth\\u0065r\"]}}", load)) {
            assertEquals(List.of("other"), input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<Arguments> misplacedFaultName() {
        return Stream.of(Arguments.of("{\"other\":0.5}", "other"),
                Arguments.of("{\"problems\":{\"other\":0.5}}", "problems.other"))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("misplacedFaultName")
    void otherIsAFaultNameAndDoesNotIntroduceANewRateKey(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> defaultInputs() {
        return Stream.of("{\"problems\":{}}", "{\"problems\":{\"travel\":0}}",
                "{\"problems\":{},\"otherCodes\":[\"R\"]}",
                "{\"dontKnowCodes\":[\"D\"],\"problems\":{}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("defaultInputs")
    void omittedFaultsDefaultToAllSixEvenWithoutOtherCodes(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(DEFAULT_FAULTS, input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<List<String>> selections() {
        return Stream.of(List.<String>of(), List.of("other"), List.of("fakeRefusal"),
                List.of("other", "fakeRefusal"), List.of("fakeRefusal", "other"),
                List.of("dontKnow", "other"), List.of("refuse", "other"), List.of("other", "refuse"), DEFAULT_FAULTS,
                List.of("other", "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast"));
    }

    static Stream<Arguments> fileSelections() {
        return selections().flatMap(faults -> Stream.of(false, true)
                .map(load -> Arguments.of(faults, load)));
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void explicitFaultSelectionKeepsOrderAndNeverAddsOrConflatesOtherAndFakeRefusal(
            List<String> faults, boolean load) throws Exception {
        String array = json(faults).replace("\"otherCodes\"", "\"faults\"");
        try (Input input = input("{\"problems\":" + array + "}", load)) {
            SimulationConfig config = input.read();
            assertEquals(faults, config.problems().orElseThrow().faults());
            assertTrue(config.otherCodes().isEmpty());
            assertTrue(config.dontKnowCodes().isEmpty());
            assertTrue(config.refuseCodes().isEmpty());
            assertTrue(config.consent().isEmpty());
            assertEquals(.03, config.otherRate());
        }
    }

    static Stream<Arguments> constructorSelections() {
        return selections().flatMap(faults -> Stream.of(false, true)
                .map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("constructorSelections")
    void bothListTakingConstructorsAcceptOtherAndKeepExactOrder(List<String> faults, boolean legacy) {
        assertEquals(faults, rates(faults, legacy).faults());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptingOtherDoesNotChangeAnyConstructorRate(boolean legacy) {
        ProblemRates result = rates(List.of("other"), legacy);
        assertAll(
                () -> assertEquals(.11, result.resent()),
                () -> assertEquals(.22, result.reinterview()),
                () -> assertEquals(.33, result.shortDuration()),
                () -> assertEquals(.44, result.longDuration()),
                () -> assertEquals(.55, result.offHours()),
                () -> assertEquals(.66, result.overlap()),
                () -> assertEquals(.77, result.travel()),
                () -> assertEquals(legacy ? 0 : .99, result.leftOpen()),
                () -> assertEquals(.88, result.outliers()));
    }

    @Test
    void theSevenRateConstructorStillEnablesNoFaults() {
        assertEquals(List.of(), new ProblemRates(0, 0, 0, 0, 0, 0, 0).faults());
    }

    static Stream<Arguments> invalidFileFaults() {
        Stream<Arguments> entries = Stream.of("\"Other\"", "\"OTHER\"", "\"otherd\"", "\"refusal\"",
                "\"other \"", "\" other\"", "\"other\\t\"", "\"\"", "null", "1", "true", "{}", "[]")
                .map(token -> Arguments.of("[\"other\"," + token + "]", "problems.faults[1]"));
        Stream<Arguments> duplicates = Stream.of(
                Arguments.of("[\"other\",\"other\"]", "problems.faults[1]"),
                Arguments.of("[\"other\",\"fakeRefusal\",\"other\"]", "problems.faults[2]"),
                Arguments.of("[\"other\",\"oth\\u0065r\"]", "problems.faults[1]"));
        return Stream.concat(entries, duplicates).flatMap(args -> Stream.of(false, true)
                .map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidFileFaults")
    void invalidFaultNamesAndRepeatedOtherReportTheOffendingIndex(String faults, String path, boolean load)
            throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":" + faults + "}}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> invalidConstructorFaults() {
        return Stream.of(List.of("Other"), List.of("OTHER"), List.of("otherd"), List.of("refusal"),
                List.of("other "), List.of(" other"), List.of("other\t"), List.of(""),
                List.of("other", "other"), List.of("other", "fakeRefusal", "other"),
                List.of("other", new String("other")))
                .flatMap(faults -> Stream.of(false, true).map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorFaults")
    void bothConstructorsRejectMisspelledOrRepeatedOther(List<String> faults, boolean legacy) {
        assertThrows(IllegalArgumentException.class, () -> rates(faults, legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothConstructorsRejectNullFaultListsOrElementsAlongsideOther(boolean legacy) {
        assertThrows(NullPointerException.class, () -> rates(null, legacy));
        assertThrows(NullPointerException.class, () -> rates(Arrays.asList("other", null), legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void constructorsDefensivelyCopyFaultListsContainingOther(boolean legacy) {
        List<String> input = new ArrayList<>(List.of("other", "fakeRefusal"));
        ProblemRates result = rates(input, legacy);
        input.set(0, "dontKnow");
        input.clear();
        assertEquals(List.of("other", "fakeRefusal"), result.faults());
        assertThrows(UnsupportedOperationException.class, () -> result.faults().set(0, "dontKnow"));
        assertThrows(UnsupportedOperationException.class, () -> result.faults().subList(0, 1).clear());
    }

    @Test
    void theSixDefaultFaultsAreUnmodifiable() {
        ProblemRates defaults = ProblemRates.defaults();
        assertThrows(UnsupportedOperationException.class, () -> defaults.faults().remove("other"));
        assertEquals(DEFAULT_FAULTS, defaults.faults());
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void parsedFaultListsRemainUnmodifiableAfterEveryCopy(List<String> faults, boolean load) throws Exception {
        String array = json(faults).replace("\"otherCodes\"", "\"faults\"");
        try (Input input = input("{\"otherCodes\":[\"R\"],\"problems\":" + array + "}", load)) {
            SimulationConfig original = input.read();
            for (SimulationConfig config : List.of(original, original.withSeed(42), original.withSubmissions(1),
                    original.withProblems(original.problems().orElseThrow()))) {
                List<String> actual = config.problems().orElseThrow().faults();
                assertEquals(faults, actual);
                assertThrows(UnsupportedOperationException.class, () -> actual.add("new"));
                if (actual.contains("other")) {
                    assertThrows(UnsupportedOperationException.class, () -> actual.remove("other"));
                }
                assertEquals(List.of("R"), config.otherCodes());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installingConstructorSuppliedOtherPreservesRatesAndCodesWithoutChangingTheReceiver(boolean legacy)
            throws Exception {
        SimulationConfig original = SimulationConfig.parse(
                "{\"dontKnowCodes\":[\"D\"],\"otherCodes\":[\"R\"]}", "fault-copy.json");
        ProblemRates requested = rates(List.of("other", "fakeRefusal"), legacy);
        SimulationConfig copy = original.withProblems(requested);
        assertNotSame(original, copy);
        assertEquals(requested, copy.problems().orElseThrow());
        assertEquals(List.of("R"), copy.otherCodes());
        assertEquals(List.of("D"), copy.dontKnowCodes());
        assertTrue(original.problems().isEmpty());
        assertEquals(List.of("other", "fakeRefusal"), copy.withSeed(42).withSubmissions(5)
                .problems().orElseThrow().faults());
    }
}
