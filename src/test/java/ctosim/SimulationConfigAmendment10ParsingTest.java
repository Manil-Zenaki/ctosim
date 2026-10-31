package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment10Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment10ParsingTest {
    @Test
    void defaultsHaveNoLocatedVillages() {
        assertTrue(SimulationConfig.defaults().villages().isEmpty());
    }

    static Stream<Arguments> absentInputs() {
        return Stream.of("{}", "{\"problems\":{}}", "{\"area\":{\"minLatitude\":0,\"maxLatitude\":1,"
                + "\"minLongitude\":0,\"maxLongitude\":1}}",
                "{\"fields\":{\"villages\":{\"value\":\"x\"},\"latitude\":{\"value\":1},"
                + "\"longitude\":{\"value\":2}},\"textPools\":{\"place\":[\"A\"]}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("absentInputs")
    void omittedVillagesStayAbsentEvenWithOtherLocationSettings(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertTrue(input.read().villages().isEmpty());
        }
    }

    static Stream<Arguments> acceptedInputs() {
        return acceptedNames().flatMap(names -> Stream.of(false, true)
                .map(load -> Arguments.of(names.get()[0], names.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("acceptedInputs")
    void columnNamesArePreservedExactlyWithoutConsultingAFormOrCsv(
            String latitude, String longitude, boolean load) throws Exception {
        try (Input input = input(json(latitude, longitude), load)) {
            Villages actual = input.read().villages().orElseThrow();
            assertEquals(latitude, actual.latitude());
            assertEquals(longitude, actual.longitude());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixturePreservesUnicodeAndMapsMembersByNameRegardlessOfOrder(boolean load) throws Exception {
        try (Input input = input(fixture(), load)) {
            assertEquals(new Villages("緯度 Latitude", "経度 longitude"), input.read().villages().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leadingBomAndEscapedKeysAndValuesAreDecodedBeforeUse(boolean load) throws Exception {
        String json = "\ufeff{\"villa\\u0067es\":{\"latit\\u0075de\":\"Lat\\u0069tude\","
                + "\"longit\\u0075de\":\"Long\\u0069tude\"}}";
        try (Input input = input(json, load)) {
            assertEquals(new Villages("Latitude", "Longitude"), input.read().villages().orElseThrow());
        }
    }

    static Stream<Arguments> invalidValues() {
        Stream<Arguments> objects = Stream.of("null", "true", "false", "0", "1.5", "\"lat,lon\"", "[]",
                "[{\"latitude\":\"lat\",\"longitude\":\"lon\"}]")
                .map(token -> Arguments.of("{\"villages\":" + token + "}", "villages"));
        Stream<Arguments> members = Stream.of("latitude", "longitude").flatMap(member ->
                Stream.concat(Stream.of("null", "true", "false", "0", "1.5", "[]", "[\"column\"]", "{}"),
                        invalidNames().map(SimulationConfigAmendment10Support::quote))
                        .map(token -> Arguments.of("{\"villages\":{\"" + member + "\":" + token + ",\""
                                + (member.equals("latitude") ? "longitude" : "latitude") + "\":\"other\"}}",
                                "villages." + member)));
        Stream<Arguments> missingOrEqual = Stream.of(
                Arguments.of("{\"villages\":{}}", "villages"),
                Arguments.of("{\"villages\":{\"latitude\":\"lat\"}}", "villages"),
                Arguments.of("{\"villages\":{\"longitude\":\"lon\"}}", "villages"),
                Arguments.of(json("x", "x"), "villages"),
                Arguments.of(json("緯度", "緯度"), "villages"),
                Arguments.of("{\"villages\":{\"latitude\":\"lat\",\"longitude\":\"l\\u0061t\"}}", "villages"));
        return Stream.of(objects, members, missingOrEqual).flatMap(stream -> stream);
    }

    static Stream<Arguments> invalidInputs() {
        return invalidValues().flatMap(args -> Stream.of(false, true)
                .map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidVillagesReportConfigExceptionSourceLocationAndReason(
            String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), path);
        }
    }

    static Stream<Arguments> unknownKeys() {
        Stream<Arguments> root = Stream.of("Villages", "village", "villages ", "latitude", "longitude")
                .map(key -> Arguments.of("{" + quote(key) + ":{\"latitude\":\"lat\",\"longitude\":\"lon\"}}", key));
        Stream<Arguments> child = Stream.of("Latitude", "Longitude", "lat", "lon", "latitude ", "longitude ",
                "villages", "fields", "file", "area")
                .map(key -> Arguments.of("{\"villages\":{\"latitude\":\"lat\",\"longitude\":\"lon\","
                        + quote(key) + ":\"extra\"}}", "villages." + key));
        Stream<Arguments> misplaced = Stream.of(
                Arguments.of("{\"problems\":{\"villages\":{\"latitude\":\"a\",\"longitude\":\"b\"}}}", "problems.villages"),
                Arguments.of("{\"consent\":{\"fields\":[\"c\"],\"villages\":{}}}", "consent.villages"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"villages\":{}}}", "repeats.villages"),
                Arguments.of("{\"fields\":{\"x\":{\"value\":1,\"villages\":{}}}}", "fields.x.villages"),
                Arguments.of("{\"fields\":{\"x\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"villages\":{}}]}}}",
                        "fields.x.when[0].villages"));
        return Stream.of(root, child, misplaced).flatMap(stream -> stream).flatMap(args ->
                Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void theAmendmentOnlyExtendsTheSpecifiedSchemaAndUsesExactKeyNames(
            String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> duplicateInputs() {
        Stream<String> members = Stream.of("latitude", "longitude").flatMap(key -> Stream.of("same", "different")
                .flatMap(value -> Stream.of(key, key.replace("u", "\\u0075"))
                        .map(duplicate -> "{\"villages\":{\"" + key + "\":\"same\",\""
                                + (key.equals("latitude") ? "longitude" : "latitude") + "\":\"other\",\""
                                + duplicate + "\":\"" + value + "\"}}")));
        Stream<String> rootAndEscaped = Stream.of(
                "{\"villages\":{\"latitude\":\"a\",\"longitude\":\"b\"},\"villages\":{\"latitude\":\"a\",\"longitude\":\"b\"}}",
                "{\"villages\":{\"latitude\":\"a\",\"longitude\":\"b\"},\"villa\\u0067es\":{\"latitude\":\"c\",\"longitude\":\"d\"}}",
                "{\"villages\":{\"latitude\":\"a\",\"longitude\":\"b\",\"latit\\u0075de\":\"c\"}}");
        return Stream.concat(members, rootAndEscaped).flatMap(json ->
                Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("duplicateInputs")
    void duplicateDecodedObjectKeysAreRejectedEvenWhenValuesAreEqual(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    static Stream<Arguments> malformedInputs() {
        return Stream.of(
                "{\n  \"villages\": {\n    \"latitude\": \"lat\",\n    \"longitude\": @\n  }\n}",
                "{\n  \"villages\": {\n    \"latitude\": \"lat\",\n    \"longitude\": \"lon\\q\"\n  }\n}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("malformedInputs")
    void malformedVillagesJsonReportsFailingLineAndRetainsCause(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertNotNull(error.getCause(), "JSON parser failure must be retained as the cause");
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?is).*line\\s*[:=]?\\s*4\\b.*"),
                    () -> "Expected failing line 4: " + error.getMessage());
        }
    }

    static Stream<Arguments> nonObjectOrTrailingInputs() {
        String valid = json("lat", "lon");
        return Stream.of("[" + valid + "]", valid + " {}", valid + " null", valid + " garbage")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("nonObjectOrTrailingInputs")
    void locatedVillagesStillRequireExactlyOneRootObject(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void newSchemaNamesRemainLegalAsArbitraryNames(boolean load) throws Exception {
        String json = "{\"villages\":{\"latitude\":\"villages\",\"longitude\":\"latitude\"},"
                + "\"id\":\"villages\",\"profiles\":{\"villages\":1},"
                + "\"fields\":{\"villages\":{\"weights\":{\"latitude\":1,\"longitude\":2}},"
                + "\"latitude\":{\"value\":1},\"longitude\":{\"value\":2}}}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(new Villages("villages", "latitude"), config.villages().orElseThrow());
            assertEquals("villages", config.id().orElseThrow());
            assertEquals(java.util.Map.of("villages", 1.0), config.profiles());
            assertEquals(java.util.Map.of("latitude", 1.0, "longitude", 2.0),
                    config.field("villages").orElseThrow().weights());
            assertEquals("1", config.field("latitude").orElseThrow().value().orElseThrow());
            assertEquals("2", config.field("longitude").orElseThrow().value().orElseThrow());
        }
    }
}
