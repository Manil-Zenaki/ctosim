package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment16ParsingTest {
    static Stream<Arguments> lists() {
        return bothReaders(Stream.of(
                Arguments.of("", List.of()),
                Arguments.of("\"final\":[]", List.of()),
                Arguments.of("\"final\":[\"moved\"]", List.of("moved")),
                Arguments.of("\"final\":[\"z\",\"a\",\"10\",\"2\"]", List.of("z", "a", "10", "2")),
                Arguments.of("\"final\":[\"01\",\"1.0\",\"1e0\",\" 1\",\"1 \"]",
                        List.of("01", "1.0", "1e0", " 1", "1 ")),
                Arguments.of("\"final\":[\"Moved\",\"moved\",\"\\u00e9\",\"e\\u0301\"]",
                        List.of("Moved", "moved", "\u00e9", "e\u0301")),
                Arguments.of("\"final\":[\" \",\"\\t\",\"\\n\",\"\\r\",\"\\f\",\"\\b\"]",
                        List.of(" ", "\t", "\n", "\r", "\f", "\b")),
                Arguments.of("\"final\":[\"a\\\"b\",\"a\\\\b\",\"\\u6771\\u4eac\\ud83d\\udcdd\"]",
                        List.of("a\"b", "a\\b", "\u6771\u4eac\ud83d\udcdd"))));
    }

    @ParameterizedTest
    @MethodSource("lists")
    void finalValuesPreserveExactDecodedStringsAndFileOrder(String members, List<String> expected, boolean load)
            throws Exception {
        try (Input input = input(visits(members), load)) {
            VisitPlan plan = input.read().visits().orElseThrow();
            assertEquals(expected, plan.finalValues());
            assertEquals(new VisitPlan("availability", "1", null, 0.1, 0.025, 3, expected), plan);
        }
    }

    static Stream<Arguments> distinctAvailableCodes() {
        return bothReaders(Stream.of(
                Arguments.of("Y", "y"), Arguments.of("Y", " Y "),
                Arguments.of("01", "1"), Arguments.of("1.0", "1"),
                Arguments.of("\u00e9", "e\u0301"), Arguments.of(" ", "\t")));
    }

    @ParameterizedTest
    @MethodSource("distinctAvailableCodes")
    void availableAndFinalUseExactStringComparison(String available, String code, boolean load) throws Exception {
        try (Input input = input(visits("\"available\":" + quote(available)
                + ",\"final\":[" + quote(code) + "]"), load)) {
            assertEquals(List.of(code), input.read().visits().orElseThrow().finalValues());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bomAndEscapedFinalKeyAreAccepted(boolean load) throws Exception {
        try (Input input = input("\ufeff" + visits("\"f\\u0069nal\":[\"mo\\u0076ed\"]"), load)) {
            assertEquals(List.of("moved"), input.read().visits().orElseThrow().finalValues());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixtureKeepsFinalValuesAndEveryExistingVisitComponent(boolean load) throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/ctosim/simulation-config/amendment-16-final.json")) {
            assertNotNull(stream);
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Input input = input(json, load)) {
            assertEquals(new VisitPlan("availability", "Y", "visit_number", 0.625, 0.125, 10,
                    List.of("moved", "d\u00e9c\u00e9d\u00e9", "\u6771\u4eac", "1")),
                    input.read().visits().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalValuesMayOverlapOtherCodeListsAndFieldNames(boolean load) throws Exception {
        String json = "{\"specialValues\":[\"D\",\"R\",\"O\"],\"dontKnowCodes\":[\"D\"],"
                + "\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"],"
                + "\"visits\":{\"field\":\"availability\",\"number\":\"visit_number\","
                + "\"revisitRate\":0,\"unreachedRate\":0,"
                + "\"final\":[\"D\",\"R\",\"O\",\"availability\",\"visit_number\"]}}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("D", "R", "O", "availability", "visit_number"),
                    config.visits().orElseThrow().finalValues());
            assertTrue(config.id().isEmpty());
            assertTrue(config.problems().isEmpty());
            assertEquals(List.of("D"), config.dontKnowCodes());
            assertEquals(List.of("R"), config.refuseCodes());
            assertEquals(List.of("O"), config.otherCodes());
        }
    }
}
