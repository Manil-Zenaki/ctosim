package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigTimeZoneTest {
    @Test
    void defaultsUseTheUtcZoneIdRatherThanAnEquivalentOffset() {
        assertEquals(ZoneId.of("UTC"), SimulationConfig.defaults().timeZone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"enumerators\":1}", "{\"seed\":82,\"submissions\":3}"})
    void omittingTimeZoneUsesUtc(String json) throws Exception {
        assertEquals(ZoneId.of("UTC"), parse(json).timeZone());
    }

    static Stream<String> acceptedZoneIds() {
        return Stream.of(
                "UTC", "Z", "GMT", "UT", "Etc/UTC", "Etc/GMT+3",
                "Africa/Addis_Ababa", "America/New_York", "Asia/Kathmandu",
                "Pacific/Chatham", "Australia/Lord_Howe", "Europe/Paris",
                "+03:00", "-04:00", "+05:45", "+00:00", "-00:00",
                "+18:00", "-18:00", "+00:00:01", "-00:00:01", "+05:30:45",
                "+3", "+0330", "UTC+03:00", "GMT-04:30", "UT+05:45", "UTC+00:00");
    }

    @ParameterizedTest(name = "timeZone accepts ZoneId.of({0})")
    @MethodSource("acceptedZoneIds")
    void acceptsRegionPrefixAndOffsetFormsWithExactlyZoneIdOfSemantics(String id) throws Exception {
        SimulationConfig config = parse("{\"timeZone\":" + quote(id) + "}");
        assertEquals(ZoneId.of(id), config.timeZone());
    }

    @Test
    void decodesJsonEscapesBeforeLookingUpTheTimeZone() throws Exception {
        SimulationConfig config = parse("{\"time\\u005aone\":\"Africa\\/Addis_Ababa\"}");
        assertEquals(ZoneId.of("Africa/Addis_Ababa"), config.timeZone());
    }

    static Stream<String> invalidZoneValues() {
        Stream<String> rejectedIds = Stream.of(
                "", " ", "\t", "utc", "z", "africa/addis_ababa", "No_Such/Zone",
                " UTC", "UTC ", "UTC\n", "Africa/Addis Ababa", "EST", "PST",
                "+18:01", "-18:00:01", "+19:00", "+03:60", "+03:00:60",
                "UTC+19:00", "GMT+03:99", "03:00", "\u6771\u4eac", "\uD83C\uDF0D")
                .map(SimulationConfigTestSupport::quote);
        return Stream.concat(rejectedIds,
                Stream.of("null", "0", "3", "3.5", "true", "false", "[]", "[\"UTC\"]",
                        "{}", "{\"id\":\"UTC\"}"));
    }

    @ParameterizedTest(name = "invalid timeZone JSON value: {0}")
    @MethodSource("invalidZoneValues")
    void rejectsInvalidIdsWrongTypesAndNullAtTimeZone(String value) {
        ConfigException error = assertThrows(ConfigException.class,
                () -> parse("{\"timeZone\":" + value + "}"));
        assertDiagnostic(error, SOURCE, "timeZone");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03-08T02:30:00", "2026-11-01T01:30:00"})
    void zoneDoesNotConvertOrRejectTheLocalStartDuringDstTransitions(String start) throws Exception {
        SimulationConfig config = parse("{\"timeZone\":\"America/New_York\",\"start\":"
                + quote(start) + "}");
        assertEquals(LocalDateTime.parse(start), config.start());
        assertEquals(ZoneId.of("America/New_York"), config.timeZone());
    }
}
