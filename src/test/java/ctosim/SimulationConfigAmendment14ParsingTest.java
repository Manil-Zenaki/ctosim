package ctosim;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment14Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment14ParsingTest {
    @Test
    void defaultsDoNotScheduleAFormUpdate() {
        assertTrue(SimulationConfig.defaults().formUpdate().isEmpty());
    }

    static Stream<Arguments> omittedUpdates() {
        return Stream.of("{}", "{\"problems\":{}}",
                "{\"problems\":{\"faults\":[\"outdatedVersion\"]}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("omittedUpdates")
    void omittedUpdateStaysAbsentEvenWithTheFaultEnabled(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertTrue(input.read().formUpdate().isEmpty());
        }
    }

    static Stream<Arguments> validDates() {
        return Stream.of("0001-01-01", "1900-02-28", "2000-02-29", "2026-01-05",
                "2026-12-31", "2028-02-29", "9999-12-31")
                .flatMap(date -> Stream.of(false, true).map(load -> Arguments.of(date, load)));
    }

    @ParameterizedTest
    @MethodSource("validDates")
    void aDateAloneIsSufficientAndAbsentPreviousVersionIsNull(String date, boolean load) throws Exception {
        try (Input input = input("{\"formUpdate\":" + update(date, null) + "}", load)) {
            SimulationConfig config = input.read();
            FormUpdate actual = config.formUpdate().orElseThrow();
            assertEquals(LocalDate.parse(date), actual.date());
            assertNull(actual.previousVersion());
            assertTrue(config.problems().isEmpty());
        }
    }

    static Stream<Arguments> previousVersions() {
        return Stream.of("v1", "0", "0001.0", "1e3", "-99", "V1", " v1 ", " ", "\t\r\n",
                "r\u00e9vision \u6771\u4eac \ud83d\udcdd", "a\"b\\c", "e\u0301")
                .flatMap(version -> Stream.of(false, true).map(load -> Arguments.of(version, load)));
    }

    @ParameterizedTest
    @MethodSource("previousVersions")
    void previousVersionIsANonemptyExactString(String version, boolean load) throws Exception {
        try (Input input = input("{\"formUpdate\":" + update("2026-03-02", version) + "}", load)) {
            assertEquals(new FormUpdate(LocalDate.of(2026, 3, 2), version), input.read().formUpdate().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void utf8FixturePreservesVersionAndReversedMemberOrder(boolean load) throws Exception {
        try (Input input = input(fixture(), load)) {
            SimulationConfig config = input.read();
            assertEquals(new FormUpdate(LocalDate.of(2028, 2, 29), " 001.0 / r\u00e9vision \u6771\u4eac \ud83d\udcdd "),
                    config.formUpdate().orElseThrow());
            assertEquals(List.of("outdatedVersion", "other", "tooFast"), config.problems().orElseThrow().faults());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leadingBomAndEscapedKeysAndValuesAreAccepted(boolean load) throws Exception {
        String json = "\ufeff{\"form\\u0055pdate\":{\"d\\u0061te\":\"2028-02-2\\u0039\","
                + "\"previous\\u0056ersion\":\"v\\u0031\"}}";
        try (Input input = input(json, load)) {
            assertEquals(new FormUpdate(LocalDate.of(2028, 2, 29), "v1"), input.read().formUpdate().orElseThrow());
        }
    }

    static Stream<Arguments> invalidUpdates() {
        Stream<Arguments> objects = Stream.of("null", "[]", "[{}]", "true", "false", "0", "\"2026-01-05\"")
                .map(token -> Arguments.of("{\"formUpdate\":" + token + "}", "formUpdate"));
        Stream<Arguments> dates = Stream.of("null", "0", "20260105", "true", "false", "[]", "{}",
                "\"\"", "\"2026-02-29\"", "\"1900-02-29\"", "\"2026-04-31\"",
                "\"2026-00-01\"", "\"2026-13-01\"", "\"2026-01-00\"", "\"2026-01-32\"",
                "\"2026-1-05\"", "\"2026-01-5\"", "\"26-01-05\"", "\"2026/01/05\"",
                "\" 2026-01-05\"", "\"2026-01-05 \"", "\"2026-01-05\\n\"",
                "\"2026-01-05T08:00:00\"", "\"2026-01-05Z\"", "\"2026-005\"", "\"not-a-date\"")
                .map(token -> Arguments.of("{\"formUpdate\":{\"date\":" + token + "}}", "formUpdate.date"));
        Stream<Arguments> versions = Stream.of("null", "0", "1.0", "true", "false", "[]", "{}", "\"\"")
                .map(token -> Arguments.of("{\"formUpdate\":{\"date\":\"2026-01-05\",\"previousVersion\":"
                        + token + "}}", "formUpdate.previousVersion"));
        Stream<Arguments> members = Stream.of(
                Arguments.of("{\"formUpdate\":{}}", "formUpdate"),
                Arguments.of("{\"formUpdate\":{\"previousVersion\":\"v1\"}}", "formUpdate"),
                Arguments.of("{\"formUpdate\":{\"date\":\"2026-01-05\",\"version\":\"v1\"}}", "formUpdate.version"),
                Arguments.of("{\"formUpdate\":{\"date\":\"2026-01-05\",\"Date\":\"2026-01-06\"}}", "formUpdate.Date"));
        return Stream.of(objects, dates, versions, members).flatMap(stream -> stream)
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void invalidUpdatesReportConfigExceptionSourceLocationAndReason(String json, String path, boolean load)
            throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    static Stream<Arguments> duplicateKeys() {
        return Stream.of(
                "{\"formUpdate\":{\"date\":\"2026-01-05\"},\"formUpdate\":{\"date\":\"2026-01-05\"}}",
                "{\"formUpdate\":{\"date\":\"2026-01-05\"},\"form\\u0055pdate\":{\"date\":\"2027-01-05\"}}",
                "{\"formUpdate\":{\"date\":\"2026-01-05\",\"date\":\"2026-01-05\"}}",
                "{\"formUpdate\":{\"date\":\"2026-01-05\",\"d\\u0061te\":\"2027-01-05\"}}",
                "{\"formUpdate\":{\"date\":\"2026-01-05\",\"previousVersion\":\"v1\",\"previousVersion\":\"v1\"}}",
                "{\"formUpdate\":{\"date\":\"2026-01-05\",\"previousVersion\":\"v1\",\"previous\\u0056ersion\":\"v2\"}}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateDecodedObjectKeysAreRejectedEvenWithIdenticalValues(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void malformedUpdateReportsTheFailingLineAndKeepsTheSyntaxCause(boolean load) throws Exception {
        String json = "{\n  \"formUpdate\": {\n    \"date\": \"2026-01-05\",\n    \"previousVersion\": @\n  }\n}";
        try (Input input = input(json, load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?s).*\\b4\\b.*"),
                    "The syntax error is on line 4");
            assertNotNull(error.getCause());
        }
    }

    static Stream<Arguments> misplacedKeys() {
        return Stream.of(
                Arguments.of("{\"FormUpdate\":{\"date\":\"2026-01-05\"}}", "FormUpdate"),
                Arguments.of("{\"date\":\"2026-01-05\"}", "date"),
                Arguments.of("{\"previousVersion\":\"v1\"}", "previousVersion"),
                Arguments.of("{\"problems\":{\"formUpdate\":{\"date\":\"2026-01-05\"}}}", "problems.formUpdate"),
                Arguments.of("{\"fields\":{\"answer\":{\"formUpdate\":{\"date\":\"2026-01-05\"}}}}", "fields.answer.formUpdate"),
                Arguments.of("{\"fields\":{\"answer\":{\"when\":[{\"if\":\"1 = 1\",\"formUpdate\":{\"date\":\"2026-01-05\"}}]}}}",
                        "fields.answer.when[0].formUpdate"))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("misplacedKeys")
    void onlyTheSpecifiedSchemaLocationsGainNewKeys(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"formUpdate", "date", "previousVersion", "outdatedVersion"})
    void newSchemaNamesRemainLegalAsFieldProfileAndChoiceNames(String name) throws Exception {
        String json = "{\"fields\":{" + quote(name) + ":{\"weights\":{" + quote(name)
                + ":1}}},\"profiles\":{" + quote(name) + ":1}}";
        SimulationConfig config = SimulationConfig.parse(json, "names.json");
        assertEquals(1.0, config.field(name).orElseThrow().weights().get(name));
        assertEquals(1.0, config.profiles().get(name));
        assertTrue(config.formUpdate().isEmpty());
    }

    @Test
    void publicRecordPreservesItsDateAndNullablePreviousVersion() {
        LocalDate date = LocalDate.of(2026, 1, 5);
        assertEquals(date, new FormUpdate(date, null).date());
        assertNull(new FormUpdate(date, null).previousVersion());
        assertEquals(" v01 ", new FormUpdate(date, " v01 ").previousVersion());
    }
}
