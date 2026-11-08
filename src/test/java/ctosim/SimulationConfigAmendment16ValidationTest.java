package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment16ValidationTest {
    static Stream<Arguments> wrongLists() {
        return bothReaders(Stream.of("null", "true", "false", "0", "2.5", "\"moved\"", "{}",
                "{\"0\":\"moved\"}").map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("wrongLists")
    void finalMustBeAnArrayAndCannotBeNull(String token, boolean load) throws Exception {
        assertInvalid(visits("\"final\":" + token), "visits.final", load);
    }

    static Stream<Arguments> missingFields() {
        return bothReaders(Stream.of("[]", "[\"moved\"]").map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("missingFields")
    void finalValuesDoNotMakeTheRequiredVisitFieldOptional(String array, boolean load) throws Exception {
        assertInvalid("{\"visits\":{\"final\":" + array + "}}", "visits", load);
    }

    static Stream<Arguments> wrongElements() {
        return bothReaders(Stream.of("null", "true", "false", "0", "1.0", "[]", "{}", "\"\"")
                .flatMap(token -> Stream.of(0, 1, 2).map(index -> {
                    String[] entries = {"\"a\"", "\"b\"", "\"c\""};
                    entries[index] = token;
                    return Arguments.of("[" + String.join(",", entries) + "]", index);
                })));
    }

    @ParameterizedTest
    @MethodSource("wrongElements")
    void eachFinalElementMustBeANonemptyString(String array, int index, boolean load) throws Exception {
        assertInvalid(visits("\"final\":" + array), "visits.final[" + index + "]", load);
    }

    static Stream<Arguments> duplicates() {
        return bothReaders(Stream.of(
                Arguments.of("[\"moved\",\"moved\"]", 1),
                Arguments.of("[\"moved\",\"died\",\"moved\"]", 2),
                Arguments.of("[\"moved\",\"mo\\u0076ed\"]", 1),
                Arguments.of("[\"\\u0061\",\"a\"]", 1),
                Arguments.of("[\" \",\" \"]", 1),
                Arguments.of("[\"\\t\",\"\\u0009\"]", 1),
                Arguments.of("[\"\u00e9\",\"\\u00e9\"]", 1)));
    }

    @ParameterizedTest
    @MethodSource("duplicates")
    void duplicateDecodedFinalCodesAreRejectedAtTheirSecondOccurrence(String array, int index, boolean load)
            throws Exception {
        assertInvalid(visits("\"final\":" + array), "visits.final[" + index + "]", load);
    }

    static Stream<Arguments> collisions() {
        return bothReaders(Stream.of("\"1\"", "\"Y\"", "\" \"", "\"\\u00e9\"")
                .flatMap(code -> Stream.of(0, 1, 2).flatMap(index -> Stream.of(false, true).map(finalFirst -> {
                    String[] entries = {"\"a\"", "\"b\"", "\"c\""};
                    entries[index] = code;
                    String finals = "\"final\":[" + String.join(",", entries) + "]";
                    String available = "\"available\":" + code;
                    return Arguments.of(visits(finalFirst ? finals + "," + available : available + "," + finals), index);
                }))));
    }

    @ParameterizedTest
    @MethodSource("collisions")
    void availableCannotBeFinalRegardlessOfMemberOrderOrPosition(String json, int index, boolean load)
            throws Exception {
        assertInvalid(json, "visits.final[" + index + "]", load);
    }

    static Stream<Arguments> defaultAndEscapedCollisions() {
        return bothReaders(Stream.of(
                "\"final\":[\"moved\",\"1\"]",
                "\"final\":[\"moved\",\"\\u0031\"]",
                "\"available\":\"Y\",\"final\":[\"moved\",\"\\u0059\"]",
                "\"final\":[\"moved\",\"Y\"],\"available\":\"\\u0059\"",
                "\"revisitRate\":0,\"unreachedRate\":0,\"final\":[\"moved\",\"1\"]")
                .map(members -> Arguments.of(visits(members))));
    }

    @ParameterizedTest
    @MethodSource("defaultAndEscapedCollisions")
    void defaultAvailableAndDecodedEscapesStillParticipateInCollisionChecks(String json, boolean load)
            throws Exception {
        assertInvalid(json, "visits.final[1]", load);
    }

    static Stream<Arguments> unknownKeys() {
        return bothReaders(Stream.of(
                Arguments.of(visits("\"Final\":[]"), "visits.Final"),
                Arguments.of(visits("\"finalValues\":[]"), "visits.finalValues"),
                Arguments.of("{\"final\":[]}", "final"),
                Arguments.of("{\"problems\":{\"final\":[]}}", "problems.final"),
                Arguments.of("{\"fields\":{\"a\":{\"final\":[]}}}", "fields.a.final"),
                Arguments.of("{\"fields\":{\"a\":{\"when\":[{\"if\":\"1 = 1\",\"final\":[]}]}}}",
                        "fields.a.when[0].final")));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void finalExtendsOnlyTheVisitsObjectWithItsExactKey(String json, String path, boolean load) throws Exception {
        assertInvalid(json, path, load);
    }

    static Stream<Arguments> duplicateKeys() {
        return bothReaders(Stream.of(
                "\"final\":[],\"final\":[]",
                "\"final\":[\"moved\"],\"final\":[\"died\"]",
                "\"final\":[],\"f\\u0069nal\":[\"moved\"]")
                .map(members -> Arguments.of(visits(members))));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateFinalKeysAreRejectedEvenWhenDecodedOrIdentical(String json, boolean load) throws Exception {
        assertInvalid(json, null, load);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedFinalArrayReportsItsActualLineAndRetainsTheSyntaxCause(boolean load) throws Exception {
        String json = "{\n  \"visits\": {\n    \"field\":\"availability\",\n    \"final\":[\"moved\", @]\n  }\n}";
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?s).*\\b4\\b.*"));
            assertNotNull(error.getCause());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalRemainsAnAllowedArbitraryFieldProfileAndChoiceName(boolean load) throws Exception {
        String json = "{\"fields\":{\"final\":{\"weights\":{\"final\":1}}},\"profiles\":{\"final\":1},"
                + "\"visits\":{\"field\":\"final\",\"final\":[\"final\"]}}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(List.of("final"), config.visits().orElseThrow().finalValues());
            assertEquals(1.0, config.field("final").orElseThrow().weights().get("final"));
            assertEquals(1.0, config.profiles().get("final"));
        }
    }
}
