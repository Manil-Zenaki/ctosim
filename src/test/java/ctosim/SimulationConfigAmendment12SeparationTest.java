package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment12Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment12SeparationTest {
    static Stream<Arguments> overlaps() {
        return Stream.of("\"R\"", "\"-99\"", "\" \"", "\"\\t\"", "\"refus\\u00e9\"")
                .flatMap(code -> Stream.of(0, 1, 2).flatMap(index -> Stream.of(false, true)
                        .flatMap(refuseFirst -> Stream.of(false, true).map(load -> {
                            String[] values = {"\"A\"", "\"B\"", "\"C\""};
                            values[index] = code;
                            String refuse = "\"refuseCodes\":[" + String.join(",", values) + "]";
                            String unknown = "\"dontKnowCodes\":[\"unrelated\"," + code + "]";
                            String json = "{" + (refuseFirst ? refuse + "," + unknown : unknown + "," + refuse) + "}";
                            return Arguments.of(json, index, load);
                        }))));
    }

    @ParameterizedTest
    @MethodSource("overlaps")
    void anOverlapAlwaysReportsItsRefusalIndexRegardlessOfRootKeyOrder(String json, int index, boolean load)
            throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(),
                    "refuseCodes[" + index + "]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlapComparesDecodedStringsRatherThanTheirJsonSpelling(boolean load) throws Exception {
        try (Input input = input("{\"refuseCodes\":[\"other\",\"R\\u0046\"],\"dontKnowCodes\":[\"RF\"]}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "refuseCodes[1]");
        }
    }

    static Stream<Arguments> distinctSpellings() {
        return Stream.of(Arguments.of("R", "r"), Arguments.of("R", " R"), Arguments.of("R", "R "),
                Arguments.of("-99", "-099"), Arguments.of("99", "99.0"), Arguments.of("99", "9.9e1"),
                Arguments.of("\u00e9", "e\u0301"), Arguments.of(" ", "\t"))
                .flatMap(pair -> Stream.of(false, true).map(load -> Arguments.of(pair.get()[0], pair.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("distinctSpellings")
    void distinctExactStringsMayBeSplitAcrossTheTwoLists(String unknown, String refused, boolean load)
            throws Exception {
        String json = "{\"dontKnowCodes\":[" + quote(unknown) + "],\"refuseCodes\":[" + quote(refused) + "]}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of(unknown), config.dontKnowCodes());
            assertEquals(List.of(refused), config.refuseCodes());
        }
    }

    static Stream<Arguments> independentLists() {
        return Stream.of("", ",\"problems\":{}", ",\"problems\":{\"faults\":[]}",
                ",\"problems\":{\"faults\":[\"refuse\"]}", ",\"problems\":{\"faults\":[\"dontKnow\"]}",
                ",\"problems\":{\"faults\":[\"refuse\",\"dontKnow\",\"fakeRefusal\"]}")
                .flatMap(problems -> Stream.of(false, true).map(load -> Arguments.of(problems, load)));
    }

    @ParameterizedTest
    @MethodSource("independentLists")
    void bothListsKeepTheirOwnOrderAndCanEachOverlapSpecialValues(String problems, boolean load) throws Exception {
        String json = "{\"refuseCodes\":[\"R2\",\"R1\"],\"dontKnowCodes\":[\"D2\",\"D1\"],"
                + "\"specialValues\":[\"D1\",\"R1\",\"extra\",\"R2\",\"D2\"]" + problems + "}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("R2", "R1"), config.refuseCodes());
            assertEquals(List.of("D2", "D1"), config.dontKnowCodes());
            assertEquals(List.of("D1", "R1", "extra", "R2", "D2"), config.specialValues());
        }
    }

    @ParameterizedTest
    @MethodSource("independentLists")
    void disablingFaultsDoesNotPermitOverlappingCodeLists(String problems, boolean load) throws Exception {
        try (Input input = input("{\"dontKnowCodes\":[\"R\"],\"refuseCodes\":[\"R\"]" + problems + "}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "refuseCodes[0]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dontKnowCodesAloneDoNotInferRefusalCodesAndViceVersa(boolean load) throws Exception {
        try (Input unknown = input("{\"dontKnowCodes\":[\"R\"]}", load);
             Input refused = input("{\"refuseCodes\":[\"R\"]}", load)) {
            assertEquals(List.of(), unknown.read().refuseCodes());
            assertEquals(List.of(), refused.read().dontKnowCodes());
        }
    }
}
