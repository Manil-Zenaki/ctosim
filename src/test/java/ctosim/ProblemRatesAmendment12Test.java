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

import static ctosim.SimulationConfigAmendment12Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment12Test {
    // Amendment 13 changes defaults only; explicit Amendment 12 selections stay unchanged.
    private static final List<String> DEFAULT_FAULTS =
            List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse", "other");
    private static final List<String> AMENDMENT_12_FAULTS =
            List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse");

    private static ProblemRates rates(List<String> faults, boolean legacy) {
        return legacy ? new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .88, faults)
                : new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .99, .88, faults);
    }

    @Test
    void problemRateDefaultsHaveExactlyTheSixFaultsInTheSpecifiedOrder() {
        assertEquals(DEFAULT_FAULTS, ProblemRates.defaults().faults());
    }

    @Test
    void addingTheFifthDefaultFaultKeepsAllDefaultRates() {
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
    void anEscapedRefuseFaultNameIsDecodedBeforeValidation(boolean load) throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":[\"ref\\u0075se\"]}}", load)) {
            assertEquals(List.of("refuse"), input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<Arguments> misplacedFaultName() {
        return Stream.of(Arguments.of("{\"refuse\":0.5}", "refuse"),
                Arguments.of("{\"problems\":{\"refuse\":0.5}}", "problems.refuse"))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("misplacedFaultName")
    void refuseIsAFaultNameAndDoesNotIntroduceANewRateKey(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> defaultInputs() {
        return Stream.of("{\"problems\":{}}", "{\"problems\":{\"travel\":0}}",
                "{\"problems\":{},\"refuseCodes\":[\"R\"]}",
                "{\"dontKnowCodes\":[\"D\"],\"problems\":{}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("defaultInputs")
    void omittedFaultsDefaultToAllSixEvenWithoutRefusalCodes(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(DEFAULT_FAULTS, input.read().problems().orElseThrow().faults());
        }
    }

    static Stream<List<String>> selections() {
        return Stream.of(List.<String>of(), List.of("refuse"), List.of("fakeRefusal"),
                List.of("refuse", "fakeRefusal"), List.of("fakeRefusal", "refuse"),
                List.of("dontKnow", "refuse"), AMENDMENT_12_FAULTS,
                List.of("refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast"));
    }

    static Stream<Arguments> fileSelections() {
        return selections().flatMap(faults -> Stream.of(false, true)
                .map(load -> Arguments.of(faults, load)));
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void explicitFaultSelectionKeepsOrderAndNeverAddsOrConflatesRefuseAndFakeRefusal(
            List<String> faults, boolean load) throws Exception {
        String array = json(faults).replace("\"refuseCodes\"", "\"faults\"");
        try (Input input = input("{\"problems\":" + array + "}", load)) {
            SimulationConfig config = input.read();
            assertEquals(faults, config.problems().orElseThrow().faults());
            assertTrue(config.refuseCodes().isEmpty());
            assertTrue(config.dontKnowCodes().isEmpty());
            assertTrue(config.consent().isEmpty());
        }
    }

    static Stream<Arguments> constructorSelections() {
        return selections().flatMap(faults -> Stream.of(false, true)
                .map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("constructorSelections")
    void bothListTakingConstructorsAcceptRefuseAndKeepExactOrder(List<String> faults, boolean legacy) {
        assertEquals(faults, rates(faults, legacy).faults());
    }

    @Test
    void theSevenRateConstructorStillEnablesNoFaults() {
        assertEquals(List.of(), new ProblemRates(0, 0, 0, 0, 0, 0, 0).faults());
    }

    static Stream<Arguments> invalidFileFaults() {
        Stream<Arguments> entries = Stream.of("\"Refuse\"", "\"REFUSE\"", "\"refused\"", "\"refusal\"",
                "\"refuse \"", "\" refuse\"", "\"refuse\\t\"", "\"\"", "null", "1", "true", "{}", "[]")
                .map(token -> Arguments.of("[\"refuse\"," + token + "]", "problems.faults[1]"));
        Stream<Arguments> duplicates = Stream.of(
                Arguments.of("[\"refuse\",\"refuse\"]", "problems.faults[1]"),
                Arguments.of("[\"refuse\",\"fakeRefusal\",\"refuse\"]", "problems.faults[2]"),
                Arguments.of("[\"refuse\",\"ref\\u0075se\"]", "problems.faults[1]"));
        return Stream.concat(entries, duplicates).flatMap(args -> Stream.of(false, true)
                .map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidFileFaults")
    void invalidFaultNamesAndRepeatedRefuseReportTheOffendingIndex(String faults, String path, boolean load)
            throws Exception {
        try (Input input = input("{\"problems\":{\"faults\":" + faults + "}}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> invalidConstructorFaults() {
        return Stream.of(List.of("Refuse"), List.of("REFUSE"), List.of("refused"), List.of("refusal"),
                List.of("refuse "), List.of(" refuse"), List.of("refuse\t"), List.of(""),
                List.of("refuse", "refuse"), List.of("refuse", "fakeRefusal", "refuse"),
                List.of("refuse", new String("refuse")))
                .flatMap(faults -> Stream.of(false, true).map(legacy -> Arguments.of(faults, legacy)));
    }

    @ParameterizedTest
    @MethodSource("invalidConstructorFaults")
    void bothConstructorsRejectMisspelledOrRepeatedRefuse(List<String> faults, boolean legacy) {
        assertThrows(IllegalArgumentException.class, () -> rates(faults, legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothConstructorsRejectNullFaultListsOrElementsAlongsideRefuse(boolean legacy) {
        assertThrows(NullPointerException.class, () -> rates(null, legacy));
        assertThrows(NullPointerException.class, () -> rates(Arrays.asList("refuse", null), legacy));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void constructorsDefensivelyCopyFaultListsContainingRefuse(boolean legacy) {
        List<String> input = new ArrayList<>(List.of("refuse", "fakeRefusal"));
        ProblemRates result = rates(input, legacy);
        input.set(0, "dontKnow");
        input.clear();
        assertEquals(List.of("refuse", "fakeRefusal"), result.faults());
        assertThrows(UnsupportedOperationException.class, () -> result.faults().set(0, "dontKnow"));
        assertThrows(UnsupportedOperationException.class, () -> result.faults().subList(0, 1).clear());
    }

    @Test
    void theSixDefaultFaultsAreUnmodifiable() {
        ProblemRates defaults = ProblemRates.defaults();
        assertThrows(UnsupportedOperationException.class, () -> defaults.faults().remove("refuse"));
        assertEquals(DEFAULT_FAULTS, defaults.faults());
    }

    @ParameterizedTest
    @MethodSource("fileSelections")
    void parsedFaultListsRemainUnmodifiableAfterEveryCopy(List<String> faults, boolean load) throws Exception {
        String array = json(faults).replace("\"refuseCodes\"", "\"faults\"");
        try (Input input = input("{\"refuseCodes\":[\"R\"],\"problems\":" + array + "}", load)) {
            SimulationConfig original = input.read();
            for (SimulationConfig config : List.of(original, original.withSeed(42), original.withSubmissions(1),
                    original.withProblems(original.problems().orElseThrow()))) {
                List<String> actual = config.problems().orElseThrow().faults();
                assertEquals(faults, actual);
                assertThrows(UnsupportedOperationException.class, () -> actual.add("new"));
                if (actual.contains("refuse")) {
                    assertThrows(UnsupportedOperationException.class, () -> actual.remove("refuse"));
                }
                assertEquals(List.of("R"), config.refuseCodes());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void installingConstructorSuppliedRefusePreservesRatesAndCodesWithoutChangingTheReceiver(boolean legacy)
            throws Exception {
        SimulationConfig original = SimulationConfig.parse(
                "{\"dontKnowCodes\":[\"D\"],\"refuseCodes\":[\"R\"]}", "fault-copy.json");
        ProblemRates requested = rates(List.of("refuse", "fakeRefusal"), legacy);
        SimulationConfig copy = original.withProblems(requested);
        assertNotSame(original, copy);
        assertEquals(requested, copy.problems().orElseThrow());
        assertEquals(List.of("R"), copy.refuseCodes());
        assertEquals(List.of("D"), copy.dontKnowCodes());
        assertTrue(original.problems().isEmpty());
        assertEquals(List.of("refuse", "fakeRefusal"), copy.withSeed(42).withSubmissions(5)
                .problems().orElseThrow().faults());
    }
}
