package ctosim;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment12Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment12ParsingTest {
    @Test
    void defaultsHaveNoRefuseCodes() {
        assertEquals(List.of(), SimulationConfig.defaults().refuseCodes());
    }

    static Stream<Arguments> omittedInputs() {
        return Stream.of("{}", "{\"specialValues\":[\"-99\",\"DK\"]}", "{\"problems\":{}}",
                "{\"problems\":{\"faults\":[\"refuse\"]}}",
                "{\"fields\":{\"refuseCodes\":{\"value\":\"DK\"}}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("omittedInputs")
    void omissionProducesAnEmptyListEvenWhenSpecialValuesOrRefuseFaultAreConfigured(
            String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(List.of(), input.read().refuseCodes());
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
            assertEquals(codes, input.read().refuseCodes());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixturePreservesNonAsciiCodesAndTheirOrder(boolean load) throws Exception {
        try (Input input = input(fixture(), load)) {
            assertEquals(List.of("refus\u00e9", "\u4e0d\u77e5\u9053", "\ud83e\udd37", "-099", " DK "),
                    input.read().refuseCodes());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leadingBomAndEscapedKeyAndValuesAreAccepted(boolean load) throws Exception {
        try (Input input = input("\ufeff{\"refuseCo\\u0064es\":[\"D\\u004b\",\"\\u00e9\"]}", load)) {
            assertEquals(List.of("DK", "\u00e9"), input.read().refuseCodes());
        }
    }

    static Stream<Arguments> invalidInputs() {
        Stream<Arguments> containers = Stream.of("null", "true", "false", "0", "1.5", "\"DK\"", "{}", "[]")
                .map(value -> Arguments.of("{\"refuseCodes\":" + value + "}", "refuseCodes"));
        Stream<Arguments> entries = Stream.of("null", "true", "false", "0", "-99", "1.5", "[]", "[\"DK\"]", "{}", "\"\"")
                .flatMap(value -> Stream.of(0, 1, 2).map(index -> {
                    String[] tokens = {"\"A\"", "\"B\"", "\"C\""};
                    tokens[index] = value;
                    return Arguments.of("{\"refuseCodes\":[" + String.join(",", tokens) + "]}",
                            "refuseCodes[" + index + "]");
                }));
        Stream<Arguments> duplicates = Stream.of(
                Arguments.of("{\"refuseCodes\":[\"DK\",\"DK\"]}", "refuseCodes[1]"),
                Arguments.of("{\"refuseCodes\":[\"DK\",\"R\",\"DK\"]}", "refuseCodes[2]"),
                Arguments.of("{\"refuseCodes\":[\"DK\",\"D\\u004b\"]}", "refuseCodes[1]"),
                Arguments.of(json(List.of("\u00e9", "\u00e9")), "refuseCodes[1]"),
                Arguments.of(json(List.of(" ", " ")), "refuseCodes[1]"));
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
        Stream<Arguments> root = Stream.of("RefuseCodes", "refusecodes", "refuseCode", "refuseCodes ")
                .map(key -> Arguments.of("{" + quote(key) + ":[\"DK\"]}", key));
        Stream<Arguments> nested = Stream.of(
                Arguments.of("{\"problems\":{\"refuseCodes\":[\"DK\"]}}", "problems.refuseCodes"),
                Arguments.of("{\"consent\":{\"fields\":[\"c\"],\"refuseCodes\":[\"DK\"]}}", "consent.refuseCodes"),
                Arguments.of("{\"villages\":{\"latitude\":\"lat\",\"longitude\":\"lon\",\"refuseCodes\":[\"DK\"]}}", "villages.refuseCodes"),
                Arguments.of("{\"fields\":{\"x\":{\"value\":1,\"refuseCodes\":[\"DK\"]}}}", "fields.x.refuseCodes"),
                Arguments.of("{\"fields\":{\"x\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"refuseCodes\":[\"DK\"]}]}}}",
                        "fields.x.when[0].refuseCodes"));
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
        String json = "{\"refuseCodes\":[\"refuseCodes\"],\"id\":\"refuseCodes\","
                + "\"profiles\":{\"refuseCodes\":1},\"fields\":{\"refuseCodes\":{\"weights\":{\"refuseCodes\":1}}}}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("refuseCodes"), config.refuseCodes());
            assertEquals("refuseCodes", config.id().orElseThrow());
            assertEquals(Map.of("refuseCodes", 1.0), config.profiles());
            assertEquals(Map.of("refuseCodes", 1.0), config.field("refuseCodes").orElseThrow().weights());
        }
    }

    static Stream<Arguments> duplicateKeys() {
        return Stream.of("refuseCodes", "refuseCo\\u0064es").flatMap(key -> Stream.of("DK", "other")
                .flatMap(value -> Stream.of(false, true).map(load -> Arguments.of(
                        "{\"refuseCodes\":[\"DK\"],\"" + key + "\":[\"" + value + "\"]}", load))));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateDecodedKeysAreRejectedEvenWithIdenticalArrays(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    static Stream<Arguments> malformedInputs() {
        return Stream.of("{\n\"refuseCodes\": [\n\"DK\",\n@\n]}",
                "{\n\"refuseCodes\": [\n\"DK\",\n\"bad\\q\"\n]}",
                "{\n\"refuseCodes\": [\n\"DK\",\n]\n}")
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
