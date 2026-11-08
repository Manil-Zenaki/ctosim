package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment15ParsingTest {
    @Test
    void defaultsHaveNoVisitPlan() {
        assertTrue(SimulationConfig.defaults().visits().isEmpty());
    }

    static Stream<Arguments> absentPlans() {
        return bothReaders(Stream.of("{}", "{\"problems\":{}}", "{\"id\":\"household\"}")
                .map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("absentPlans")
    void omittedVisitsRemainAbsent(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertTrue(input.read().visits().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fieldAloneSuppliesEveryDefaultAndANullNumber(boolean load) throws Exception {
        try (Input input = input(visits(""), load)) {
            assertEquals(new VisitPlan("availability", "1", null, 0.1, 0.025, 3),
                    input.read().visits().orElseThrow());
        }
    }

    static Stream<Arguments> individualOptions() {
        return bothReaders(Stream.of(
                Arguments.of("\"available\":\"Y\"", new VisitPlan("availability", "Y", null, 0.1, 0.025, 3)),
                Arguments.of("\"number\":\"visit_number\"", new VisitPlan("availability", "1", "visit_number", 0.1, 0.025, 3)),
                Arguments.of("\"revisitRate\":0.625", new VisitPlan("availability", "1", null, 0.625, 0.025, 3)),
                Arguments.of("\"unreachedRate\":0.375", new VisitPlan("availability", "1", null, 0.1, 0.375, 3)),
                Arguments.of("\"maxVisits\":2", new VisitPlan("availability", "1", null, 0.1, 0.025, 2))));
    }

    @ParameterizedTest
    @MethodSource("individualOptions")
    void eachOptionalMemberLeavesAllOtherDefaultsIntact(String members, VisitPlan expected, boolean load)
            throws Exception {
        try (Input input = input(visits(members), load)) {
            assertEquals(expected, input.read().visits().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixturePreservesAllSixMembersInReverseOrder(boolean load) throws Exception {
        try (Input input = input(fixture(), load)) {
            assertEquals(new VisitPlan("disponibilit\u00e9 \u6771\u4eac", " 01 \u2713 ",
                    "visite num\u00e9ro", 0.625, 0.125, 10), input.read().visits().orElseThrow());
        }
    }

    static Stream<Arguments> exactNames() {
        return bothReaders(Stream.of("field", "number").flatMap(member -> Stream.of(
                "x", "Availability", "0", "household id", "x/y.z-1", "a\tb", "a\nb",
                "\u00e9tude_\u6771\u4eac_\ud83d\udcdd", "e\u0301", "a\"b\\c")
                .map(name -> Arguments.of(member, name))));
    }

    @ParameterizedTest
    @MethodSource("exactNames")
    void fieldAndNumberFollowOnlyTheExactIdNameRule(String member, String name, boolean load) throws Exception {
        String json = member.equals("field") ? "{\"visits\":{\"field\":" + quote(name) + "}}"
                : visits("\"number\":" + quote(name));
        try (Input input = input(json, load)) {
            VisitPlan plan = input.read().visits().orElseThrow();
            assertEquals(name, member.equals("field") ? plan.field() : plan.number());
        }
    }

    static Stream<Arguments> exactCodes() {
        return bothReaders(Stream.of("0", "001.0", "-99", "1e2", "yes", "YES", " ", "\t\n\r\f",
                " available ", "a\"b\\c", "\u00e9\u6771\u4eac\ud83d\udcdd", "e\u0301")
                .map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("exactCodes")
    void availableIsAnExactNonemptyStringWithoutANameWhitespaceRule(String code, boolean load) throws Exception {
        try (Input input = input(visits("\"available\":" + quote(code)), load)) {
            assertEquals(code, input.read().visits().orElseThrow().available());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bomAndEscapedMemberNamesAndValuesAreDecoded(boolean load) throws Exception {
        String json = "\ufeff{\"v\\u0069sits\":{\"f\\u0069eld\":\"a\\u0062\","
                + "\"av\\u0061ilable\":\"\\u0030\\u0031\",\"n\\u0075mber\":\"n\\u0031\","
                + "\"revisit\\u0052ate\":0.5,\"unreached\\u0052ate\":0.25,\"max\\u0056isits\":4}}";
        try (Input input = input(json, load)) {
            assertEquals(new VisitPlan("ab", "01", "n1", 0.5, 0.25, 4), input.read().visits().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fieldAndNumberAreComparedCaseSensitivelyWithoutUnicodeNormalization(boolean load) throws Exception {
        for (String[] names : List.of(new String[]{"Visit", "visit"}, new String[]{"\u00e9", "e\u0301"})) {
            try (Input input = input("{\"visits\":{\"field\":" + quote(names[0])
                    + ",\"number\":" + quote(names[1]) + "}}", load)) {
                VisitPlan plan = input.read().visits().orElseThrow();
                assertEquals(names[0], plan.field());
                assertEquals(names[1], plan.number());
            }
        }
    }

    static Stream<Arguments> invalidStringsAndObjects() {
        Stream<Arguments> objects = Stream.of("null", "[]", "[{}]", "true", "false", "0", "\"availability\"")
                .map(token -> Arguments.of("{\"visits\":" + token + "}", "visits"));
        Stream<Arguments> strings = Stream.of("field", "number", "available").flatMap(member ->
                Stream.of("null", "0", "1.0", "true", "false", "[]", "{}", "\"\"")
                        .map(token -> Arguments.of(member.equals("field")
                                ? "{\"visits\":{\"field\":" + token + "}}"
                                : visits(quote(member) + ":" + token), "visits." + member)));
        Stream<Arguments> whitespace = Stream.of("field", "number").flatMap(member ->
                Stream.of(" ", "\t", "\r", "\n", "\f").flatMap(ws -> Stream.of(ws, ws + "name", "name" + ws))
                        .map(name -> Arguments.of(member.equals("field")
                                ? "{\"visits\":{\"field\":" + quote(name) + "}}"
                                : visits("\"number\":" + quote(name)), "visits." + member)));
        Stream<Arguments> missingAndEqual = Stream.of(
                Arguments.of("{\"visits\":{}}", "visits"),
                Arguments.of("{\"visits\":{\"number\":\"visit\",\"available\":\"Y\","
                        + "\"revisitRate\":0,\"unreachedRate\":0,\"maxVisits\":2}}", "visits"),
                Arguments.of(visits("\"number\":\"availability\""), "visits"),
                Arguments.of("{\"visits\":{\"number\":\"availability\",\"field\":\"availability\"}}", "visits"),
                Arguments.of(visits("\"number\":\"availabilit\\u0079\""), "visits"));
        return bothReaders(Stream.of(objects, strings, whitespace, missingAndEqual).flatMap(s -> s));
    }

    @ParameterizedTest
    @MethodSource("invalidStringsAndObjects")
    void invalidVisitObjectsAndStringsReportSourcePathAndReason(String json, String path, boolean load)
            throws Exception {
        assertInvalid(json, path, load);
    }

    static Stream<Arguments> unknownOrMisplacedKeys() {
        Stream<Arguments> unknown = Stream.of("Field", "availableCode", "maxVisit", "extra", "visits", "if")
                .map(key -> Arguments.of(visits(quote(key) + ":0"), "visits." + key));
        Stream<Arguments> top = Stream.of("field", "available", "number", "revisitRate", "unreachedRate", "maxVisits", "Visits")
                .map(key -> Arguments.of("{" + quote(key) + ":0}", key));
        Stream<Arguments> nested = Stream.of(
                Arguments.of("{\"problems\":{\"visits\":{\"field\":\"a\"}}}", "problems.visits"),
                Arguments.of("{\"fields\":{\"a\":{\"visits\":{\"field\":\"a\"}}}}", "fields.a.visits"),
                Arguments.of("{\"fields\":{\"a\":{\"when\":[{\"if\":\"1 = 1\",\"visits\":{\"field\":\"a\"}}]}}}",
                        "fields.a.when[0].visits"));
        return bothReaders(Stream.of(unknown, top, nested).flatMap(s -> s));
    }

    @ParameterizedTest
    @MethodSource("unknownOrMisplacedKeys")
    void newKeysAreAcceptedOnlyAtTheirSpecifiedSchemaLocations(String json, String path, boolean load)
            throws Exception {
        assertInvalid(json, path, load);
    }

    static Stream<Arguments> duplicateKeys() {
        Stream<Arguments> members = Stream.of("field", "available", "number", "revisitRate", "unreachedRate", "maxVisits")
                .flatMap(key -> Stream.of(false, true).map(escaped -> {
                    String token = switch (key) {
                        case "field" -> "\"availability\"";
                        case "available" -> "\"Y\"";
                        case "number" -> "\"visit_number\"";
                        case "maxVisits" -> "3";
                        default -> "0.1";
                    };
                    String second = escaped ? "\\u" + String.format("%04x", (int) key.charAt(0)) + key.substring(1) : key;
                    String prefix = key.equals("field") ? "" : "\"field\":\"availability\",";
                    return Arguments.of("{\"visits\":{" + prefix + quote(key) + ":" + token
                            + ",\"" + second + "\":" + token + "}}");
                }));
        Stream<Arguments> top = Stream.of(
                "{\"visits\":{\"field\":\"a\"},\"visits\":{\"field\":\"a\"}}",
                "{\"visits\":{\"field\":\"a\"},\"v\\u0069sits\":{\"field\":\"b\"}}")
                .map(Arguments::of);
        return bothReaders(Stream.concat(members, top));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateDecodedKeysAreRejectedIncludingIdenticalValues(String json, boolean load) throws Exception {
        assertInvalid(json, null, load);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedVisitsReportTheActualLineAndRetainTheSyntaxCause(boolean load) throws Exception {
        String json = "{\n  \"visits\": {\n    \"field\": \"availability\",\n    \"maxVisits\": @\n  }\n}";
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?s).*\\b4\\b.*"));
            assertNotNull(error.getCause());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"visits", "field", "available", "number", "revisitRate", "unreachedRate", "maxVisits"})
    void schemaKeysRemainLegalFieldProfileAndChoiceNames(String name) throws Exception {
        String json = "{\"fields\":{" + quote(name) + ":{\"weights\":{" + quote(name)
                + ":1}}},\"profiles\":{" + quote(name) + ":1},\"visits\":{\"field\":" + quote(name) + "}}";
        SimulationConfig config = SimulationConfig.parse(json, "visit-names.json");
        assertEquals(name, config.visits().orElseThrow().field());
        assertEquals(1.0, config.field(name).orElseThrow().weights().get(name));
        assertEquals(1.0, config.profiles().get(name));
    }
}
