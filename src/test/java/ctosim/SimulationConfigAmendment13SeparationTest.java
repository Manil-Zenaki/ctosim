package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment13Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment13SeparationTest {
    static Stream<Arguments> overlaps() {
        return Stream.of("dontKnowCodes", "refuseCodes").flatMap(peer ->
                Stream.of("\"96\"", "\"-666\"", "\" \"", "\"\\t\"", "\"\\u00e9\"")
                .flatMap(code -> Stream.of(0, 1, 2).flatMap(index -> Stream.of(false, true)
                        .flatMap(otherFirst -> Stream.of(false, true).map(load -> {
                            String[] values = {"\"A\"", "\"B\"", "\"C\""};
                            values[index] = code;
                            String other = "\"otherCodes\":[" + String.join(",", values) + "]";
                            String conflicting = quote(peer) + ":[\"unrelated\"," + code + "]";
                            return Arguments.of("{" + (otherFirst ? other + "," + conflicting
                                    : conflicting + "," + other) + "}", index, load);
                        })))));
    }

    @ParameterizedTest
    @MethodSource("overlaps")
    void overlapsWithEitherListAlwaysReportTheOtherCodeIndexRegardlessOfKeyOrder(String json, int index, boolean load)
            throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "otherCodes[" + index + "]");
        }
    }

    static Stream<Arguments> escapedOverlaps() {
        return Stream.of("dontKnowCodes", "refuseCodes").flatMap(peer -> Stream.of(false, true)
                .flatMap(otherFirst -> Stream.of(false, true).map(load -> {
                    String other = "\"otherCodes\":[\"unrelated\",\"9\\u0036\"]";
                    String conflict = quote(peer) + ":[\"96\"]";
                    return Arguments.of("{" + (otherFirst ? other + "," + conflict : conflict + "," + other) + "}", load);
                })));
    }

    @ParameterizedTest
    @MethodSource("escapedOverlaps")
    void overlapComparesDecodedStrings(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "otherCodes[1]");
        }
    }

    static Stream<Arguments> distinctStrings() {
        return Stream.of(Arguments.of("O", "o"), Arguments.of("O", " O"), Arguments.of("O", "O "),
                Arguments.of("96", "096"), Arguments.of("96", "96.0"), Arguments.of("96", "9.6e1"),
                Arguments.of("\u00e9", "e\u0301"), Arguments.of(" ", "\t"))
                .flatMap(pair -> Stream.of("dontKnowCodes", "refuseCodes").flatMap(peer -> Stream.of(false, true)
                        .map(load -> Arguments.of(pair.get()[0], pair.get()[1], peer, load))));
    }

    @ParameterizedTest
    @MethodSource("distinctStrings")
    void distinctExactStringsMayAppearAcrossLists(String other, String code, String peer, boolean load) throws Exception {
        try (Input input = input("{\"otherCodes\":[" + quote(other) + "]," + quote(peer) + ":[" + quote(code) + "]}", load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of(other), config.otherCodes());
            assertEquals(List.of(code), peer.equals("dontKnowCodes") ? config.dontKnowCodes() : config.refuseCodes());
        }
    }

    static Stream<Arguments> disabledFeatures() {
        return Stream.of("dontKnowCodes", "refuseCodes").flatMap(peer -> Stream.of("", ",\"problems\":{}",
                ",\"problems\":{\"faults\":[]}", ",\"problems\":{\"faults\":[\"other\"]}")
                .flatMap(problems -> Stream.of("0", "1").flatMap(rate -> Stream.of(false, true)
                        .map(load -> Arguments.of(peer, problems, rate, load)))));
    }

    @ParameterizedTest
    @MethodSource("disabledFeatures")
    void ratesAndFaultSelectionDoNotRelaxOverlapValidation(String peer, String problems, String rate, boolean load)
            throws Exception {
        try (Input input = input("{\"otherCodes\":[\"96\"]," + quote(peer) + ":[\"96\"],\"otherRate\":"
                + rate + problems + "}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "otherCodes[0]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void threeListsKeepTheirOwnOrderAndMayAllOverlapSpecialValues(boolean load) throws Exception {
        try (Input input = input("{\"otherCodes\":[\"O2\",\"O1\"],\"dontKnowCodes\":[\"D2\",\"D1\"],"
                + "\"refuseCodes\":[\"R2\",\"R1\"],\"specialValues\":[\"D1\",\"O2\",\"R2\",\"O1\"],\"otherRate\":0}", load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("O2", "O1"), config.otherCodes());
            assertEquals(List.of("D2", "D1"), config.dontKnowCodes());
            assertEquals(List.of("R2", "R1"), config.refuseCodes());
            assertEquals(List.of("D1", "O2", "R2", "O1"), config.specialValues());
            assertTrue(config.problems().isEmpty());
        }
    }
}
