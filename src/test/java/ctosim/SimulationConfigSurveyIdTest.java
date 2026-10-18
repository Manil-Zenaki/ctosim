package ctosim;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 04, Amendment 4: only configuration parsing, not survey-id generation. */
class SimulationConfigSurveyIdTest {
    // Deliberately excludes "id" so a source name cannot satisfy the key diagnostic.
    static final String SOURCE_NAME = "survey-settings-\u00e9tude.json";

    static Stream<String> acceptedNames() {
        return Stream.of("x", "household_id", "Household_ID", "id", "seed",
                "missing_from_every_override", "household id", "9household", "group/member-id",
                "m\u00e9nage_\u5bb6\u5ead", "e\u0301tude", "\u00e9tude",
                "respondent_\ud83c\udf0d", "question\"name", "question\\name");
    }

    @ParameterizedTest
    @MethodSource("acceptedNames")
    void preservesTheExactNameWithoutRequiringAFormOrAnOverride(String name) throws Exception {
        SimulationConfig config = parse("{\"id\":" + quote(name) + "}");
        assertEquals(Optional.of(name), config.id());
        assertTrue(config.overriddenFields().isEmpty(), "The id setting does not create an override");
    }

    @Test
    void omittedIdStaysAbsentEvenWithAFieldOverrideNamedId() throws Exception {
        SimulationConfig config = parse("{\"fields\":{\"id\":{\"value\":\"household\"}}}");
        assertEquals(Optional.empty(), config.id());
        assertEquals("household", field(config, "id").value().orElseThrow());
    }

    @Test
    void escapedKeyAndEscapedValueAreDecodedBeforeBeingExposed() throws Exception {
        SimulationConfig config = parse("{\"\\u0069\\u0064\":\"m\\u00e9nage_\\u5bb6\\u5ead\"}");
        assertEquals(Optional.of("m\u00e9nage_\u5bb6\u5ead"), config.id());
    }

    @Test
    void idDoesNotConflictWithOrReplaceAnOverrideForTheSameName() throws Exception {
        SimulationConfig config = parse("""
                {"id":"household", "fields":{"household":{"value":"fixed", "missingRate":0.25}}}
                """);
        assertEquals(Optional.of("household"), config.id());
        assertEquals("fixed", field(config, "household").value().orElseThrow());
        assertEquals(0.25, field(config, "household").missingRate().orElseThrow());
    }

    @Test
    void idRemainsALegalArbitraryFieldProfileAndChoiceName() throws Exception {
        SimulationConfig config = parse("""
                {"id":"id", "profiles":{"id":2},
                 "fields":{"id":{"weights":{"id":3,"other":1}}}}
                """);
        assertEquals(Optional.of("id"), config.id());
        assertEquals(2.0, config.profiles().get("id").doubleValue());
        assertEquals(List.of("id", "other"), List.copyOf(field(config, "id").weights().keySet()));
        assertEquals(3.0, field(config, "id").weights().get("id").doubleValue());
    }

    static Stream<String> invalidValues() {
        Stream<String> wrongTypes = Stream.of("null", "true", "false", "0", "-1", "1.5", "1e3",
                "[]", "[\"household\"]", "{}", "{\"name\":\"household\"}");
        Stream<String> badNames = Stream.of("", " ", "\t", "\r", "\n", "\f", " \t\r\n",
                " household", "household ", " household ", "\thousehold", "household\t",
                "\rhousehold", "household\r", "\nhousehold", "household\n",
                "\fhousehold", "household\f").map(name -> quote(name).replace("\f", "\\f"));
        return Stream.concat(wrongTypes, badNames);
    }

    static void assertIdDiagnostic(ConfigException error, String source) {
        assertDiagnostic(error, source, "id");
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\bid\\b.*"),
                () -> "The message must name the id key, not merely say 'invalid': " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsWrongTypesEmptyNamesAndEdgeWhitespaceWithSourceAndKey(String jsonValue) {
        ConfigException error = assertThrows(ConfigException.class,
                () -> SimulationConfig.parse("{\"id\":" + jsonValue + "}", SOURCE_NAME));
        assertIdDiagnostic(error, SOURCE_NAME);
    }

    @Test
    void rejectsAnEscapedEdgeSpaceAfterDecodingTheString() {
        ConfigException error = assertThrows(ConfigException.class,
                () -> SimulationConfig.parse("{\"id\":\"\\u0020household\"}", SOURCE_NAME));
        assertIdDiagnostic(error, SOURCE_NAME);
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"Id\":\"household\"}", "Id"),
                Arguments.of("{\"ID\":\"household\"}", "ID"),
                Arguments.of("{\"id \":\"household\"}", "id "),
                Arguments.of("{\" id\":\"household\"}", " id"),
                Arguments.of("{\"id\":\"household\",\"ids\":\"other\"}", "ids"),
                Arguments.of("{\"repeats\":{\"min\":0,\"max\":1,\"id\":\"household\"}}", "repeats.id"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"id\":\"household\"}}", "integerRange.id"),
                Arguments.of("{\"decimalRange\":{\"min\":0,\"max\":1,\"id\":\"household\"}}", "decimalRange.id"),
                Arguments.of("{\"fields\":{\"answer\":{\"value\":1,\"id\":\"household\"}}}", "fields.answer.id"),
                Arguments.of("{\"fields\":{\"answer\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"id\":\"household\"}]}}}",
                        "fields.answer.when[0].id"),
                Arguments.of("{\"area\":{\"minLatitude\":0,\"maxLatitude\":1,\"minLongitude\":0,\"maxLongitude\":1,\"id\":\"household\"}}", "area.id"),
                Arguments.of("{\"fields\":{\"answer\":{\"distribution\":{\"normal\":{\"mean\":1,\"sd\":0,\"id\":\"household\"}}}}}",
                        "fields.answer.distribution.normal.id"),
                Arguments.of("{\"textPools\":{\"id\":[\"household\"]}}", "textPools.id"));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void recognizesIdOnlyAtTheTopLevelAndWithExactSpelling(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class,
                () -> SimulationConfig.parse(json, SOURCE_NAME)), SOURCE_NAME, location);
    }

    static Stream<String> duplicateKeys() {
        return Stream.of(
                "{\"id\":\"a\",\"id\":\"a\"}",
                "{\"id\":\"a\",\"id\":\"b\"}",
                "{\"id\":\"b\",\"seed\":2,\"id\":\"a\"}",
                "{\"id\":\"a\",\"\\u0069d\":\"b\"}",
                "{\"i\\u0064\":\"a\",\"id\":\"b\"}");
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void rejectsDuplicateIdKeysIncludingIdenticalValuesAndDecodedSpellings(String json) {
        // Duplicate keys may be diagnosed as syntax or schema errors (confirmed question 2).
        assertDiagnostic(assertThrows(ConfigException.class,
                () -> SimulationConfig.parse(json, SOURCE_NAME)), SOURCE_NAME, null);
    }
}
