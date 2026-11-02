package ctosim;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment13Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment13ParsingTest {
    @Test
    void defaultsHaveNoOtherCodes() {
        assertEquals(List.of(), SimulationConfig.defaults().otherCodes());
    }

    static Stream<Arguments> omittedInputs() {
        return Stream.of("{}", "{\"specialValues\":[\"-99\",\"DK\"]}", "{\"problems\":{}}",
                "{\"problems\":{\"faults\":[\"other\"]}}",
                "{\"fields\":{\"otherCodes\":{\"value\":\"DK\"}}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("omittedInputs")
    void omissionProducesAnEmptyListEvenWhenSpecialValuesOrOtherFaultAreConfigured(
            String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(List.of(), input.read().otherCodes());
        }
    }

    static Stream<Arguments> exactCodeInputs() {
        return Stream.of(List.of("x"), List.of("z", "a", "m"),
                List.of("-99", "-099", "-99.0", "-9.9e1", "+99", "99", "099"),
                List.of("DK", "dk", "Dk", " DK", "DK "),
                List.of(" ", "\t", "\n", "\r", "\f", "\u00a0"),
                List.of("\u00e9", "e\u0301", "\u4e0d\u77e5\u9053", "\ud83e\udd37"),
                List.of("null", "true", "NaN", "Infinity", "${unknown}", "a/b", "a\"b", "a\\b", "a\nb"))
                .flatMap(codes -> Stream.of(false, true).map(load -> Arguments.of(codes, load)));
    }

    @ParameterizedTest
    @MethodSource("exactCodeInputs")
    void codesAreNonemptyExactStringsPreservedInFileOrder(List<String> codes, boolean load) throws Exception {
        try (Input input = input(json(codes), load)) {
            assertEquals(codes, input.read().otherCodes());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixturePreservesNonAsciiCodesAndTheirOrder(boolean load) throws Exception {
        try (Input input = input(fixture(), load)) {
            assertEquals(List.of("refus\u00e9", "\u4e0d\u77e5\u9053", "\ud83e\udd37", "-099", " DK "),
                    input.read().otherCodes());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leadingBomAndEscapedKeyAndValuesAreAccepted(boolean load) throws Exception {
        try (Input input = input("\ufeff{\"otherCo\\u0064es\":[\"D\\u004b\",\"\\u00e9\"]}", load)) {
            assertEquals(List.of("DK", "\u00e9"), input.read().otherCodes());
        }
    }

    static Stream<Arguments> invalidInputs() {
        Stream<Arguments> containers = Stream.of("null", "true", "false", "0", "1.5", "\"DK\"", "{}", "[]")
                .map(value -> Arguments.of("{\"otherCodes\":" + value + "}", "otherCodes"));
        Stream<Arguments> entries = Stream.of("null", "true", "false", "0", "-99", "1.5", "[]", "[\"DK\"]", "{}", "\"\"")
                .flatMap(value -> Stream.of(0, 1, 2).map(index -> {
                    String[] tokens = {"\"A\"", "\"B\"", "\"C\""};
                    tokens[index] = value;
                    return Arguments.of("{\"otherCodes\":[" + String.join(",", tokens) + "]}",
                            "otherCodes[" + index + "]");
                }));
        Stream<Arguments> duplicates = Stream.of(
                Arguments.of("{\"otherCodes\":[\"DK\",\"DK\"]}", "otherCodes[1]"),
                Arguments.of("{\"otherCodes\":[\"DK\",\"R\",\"DK\"]}", "otherCodes[2]"),
                Arguments.of("{\"otherCodes\":[\"DK\",\"D\\u004b\"]}", "otherCodes[1]"),
                Arguments.of(json(List.of("\u00e9", "\u00e9")), "otherCodes[1]"),
                Arguments.of(json(List.of(" ", " ")), "otherCodes[1]"));
        return Stream.of(containers, entries, duplicates).flatMap(stream -> stream)
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidCodesReportConfigExceptionSourceLocationAndReason(String json, String path, boolean load)
            throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> unknownKeys() {
        Stream<Arguments> root = Stream.of("OtherCodes", "othercodes", "otherCode", "otherCodes ")
                .map(key -> Arguments.of("{" + quote(key) + ":[\"DK\"]}", key));
        Stream<Arguments> nested = Stream.of(
                Arguments.of("{\"problems\":{\"otherCodes\":[\"DK\"]}}", "problems.otherCodes"),
                Arguments.of("{\"consent\":{\"fields\":[\"c\"],\"otherCodes\":[\"DK\"]}}", "consent.otherCodes"),
                Arguments.of("{\"villages\":{\"latitude\":\"lat\",\"longitude\":\"lon\",\"otherCodes\":[\"DK\"]}}", "villages.otherCodes"),
                Arguments.of("{\"fields\":{\"x\":{\"value\":1,\"otherCodes\":[\"DK\"]}}}", "fields.x.otherCodes"),
                Arguments.of("{\"fields\":{\"x\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"otherCodes\":[\"DK\"]}]}}}",
                        "fields.x.when[0].otherCodes"));
        return Stream.concat(root, nested).flatMap(args -> Stream.of(false, true)
                .map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void onlyTheExactTopLevelKeyExtendsTheSchema(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theNewKeyRemainsLegalAsAFieldProfileAndChoiceName(boolean load) throws Exception {
        String json = "{\"otherCodes\":[\"otherCodes\"],\"id\":\"otherCodes\","
                + "\"profiles\":{\"otherCodes\":1},\"fields\":{\"otherCodes\":{\"weights\":{\"otherCodes\":1}}}}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("otherCodes"), config.otherCodes());
            assertEquals("otherCodes", config.id().orElseThrow());
            assertEquals(Map.of("otherCodes", 1.0), config.profiles());
            assertEquals(Map.of("otherCodes", 1.0), config.field("otherCodes").orElseThrow().weights());
        }
    }

    static Stream<Arguments> duplicateKeys() {
        return Stream.of("otherCodes", "otherCo\\u0064es").flatMap(key -> Stream.of("DK", "other")
                .flatMap(value -> Stream.of(false, true).map(load -> Arguments.of(
                        "{\"otherCodes\":[\"DK\"],\"" + key + "\":[\"" + value + "\"]}", load))));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateDecodedKeysAreRejectedEvenWithIdenticalArrays(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    static Stream<Arguments> malformedInputs() {
        return Stream.of("{\n\"otherCodes\": [\n\"DK\",\n@\n]}",
                "{\n\"otherCodes\": [\n\"DK\",\n\"bad\\q\"\n]}",
                "{\n\"otherCodes\": [\n\"DK\",\n]\n}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("malformedInputs")
    void malformedCodesJsonReportsFailingLineAndRetainsCause(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertNotNull(error.getCause());
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?is).*line\\s*[:=]?\\s*4\\b.*"),
                    () -> "Expected failing line 4: " + error.getMessage());
        }
    }

    static Stream<Arguments> invalidRootInputs() {
        String valid = json(List.of("DK"));
        return Stream.of("[" + valid + "]", valid + " {}", valid + " null", valid + " garbage")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("invalidRootInputs")
    void codesStillRequireExactlyOneRootObject(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }
}
