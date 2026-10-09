package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendmentCopiesTest {
    static Stream<Arguments> newSettings() {
        return Stream.of(
                Arguments.of("UTC", 5), Arguments.of("Z", 1),
                Arguments.of("Africa/Addis_Ababa", 17), Arguments.of("+05:45", 9999),
                Arguments.of("America/New_York", 10000), Arguments.of("UT+00:00", 3));
    }

    private SimulationConfig configured(String zone, int enumerators) throws Exception {
        // Extend the existing read-only test fixture without changing it or its tests.
        return parse("{\"timeZone\":" + quote(zone) + ",\"enumerators\":" + enumerators
                + "," + completeJson().substring(1));
    }

    private static void assertAmendmentSettings(SimulationConfig config, String zone, int enumerators) {
        assertAll(
                () -> assertEquals(ZoneId.of(zone), config.timeZone()),
                () -> assertEquals(enumerators, config.enumerators()));
    }

    @ParameterizedTest
    @MethodSource("newSettings")
    void bothNewKeysCoexistWithEveryExistingSettingWithoutChangingThem(String zone, int enumerators)
            throws Exception {
        SimulationConfig config = configured(zone, enumerators);
        assertAmendmentSettings(config, zone, enumerators);
        assertSameSettings(complete(), config);
    }

    @ParameterizedTest
    @MethodSource("newSettings")
    void withSeedPreservesBothNewSettingsAndTheOriginal(String zone, int enumerators) throws Exception {
        SimulationConfig original = configured(zone, enumerators);
        SimulationConfig copy = original.withSeed(Long.MAX_VALUE);
        assertNotSame(original, copy);
        assertEquals(Long.MAX_VALUE, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertAmendmentSettings(copy, zone, enumerators);
        assertAmendmentSettings(original, zone, enumerators);
        assertSameSettingsExceptSeedAndSubmissions(original, copy);
        assertSameSettings(complete(), original);
    }

    @ParameterizedTest
    @MethodSource("newSettings")
    void withSubmissionsPreservesBothNewSettingsEvenWhenEnumeratorsOutnumberTheNewCount(
            String zone, int enumerators) throws Exception {
        SimulationConfig original = configured(zone, enumerators);
        SimulationConfig copy = original.withSubmissions(1);
        assertNotSame(original, copy);
        assertEquals(1, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertAmendmentSettings(copy, zone, enumerators);
        assertAmendmentSettings(original, zone, enumerators);
        assertSameSettingsExceptSeedAndSubmissions(original, copy);
        assertSameSettings(complete(), original);
    }

    @ParameterizedTest
    @MethodSource("newSettings")
    void copyingAnUnchangedSeedStillReturnsANewObjectWithBothSettings(String zone, int enumerators)
            throws Exception {
        SimulationConfig original = configured(zone, enumerators);
        SimulationConfig copy = original.withSeed(original.seed());
        assertNotSame(original, copy);
        assertAmendmentSettings(copy, zone, enumerators);
        assertAmendmentSettings(original, zone, enumerators);
        assertSameSettings(original, copy);
    }

    @ParameterizedTest
    @MethodSource("newSettings")
    void copyingAnUnchangedSubmissionCountStillReturnsANewObjectWithBothSettings(String zone, int enumerators)
            throws Exception {
        SimulationConfig original = configured(zone, enumerators);
        SimulationConfig copy = original.withSubmissions(original.submissions());
        assertNotSame(original, copy);
        assertAmendmentSettings(copy, zone, enumerators);
        assertAmendmentSettings(original, zone, enumerators);
        assertSameSettings(original, copy);
    }

    @Test
    void copyChainsPreserveBothSettingsAtEveryStep() throws Exception {
        SimulationConfig original = configured("America/New_York", 10000);
        SimulationConfig seeded = original.withSeed(0);
        SimulationConfig counted = seeded.withSubmissions(1);
        SimulationConfig reseeded = counted.withSeed(Long.MIN_VALUE);
        SimulationConfig recounted = reseeded.withSubmissions(1000000);
        for (SimulationConfig config : new SimulationConfig[]{original, seeded, counted, reseeded, recounted}) {
            assertAmendmentSettings(config, "America/New_York", 10000);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1000001})
    void aRejectedCopyCannotChangeTimeZoneOrEnumerators(int count) throws Exception {
        SimulationConfig original = configured("+05:45", 10000);
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertAmendmentSettings(original, "+05:45", 10000);
    }

    @Test
    void defaultsAndTheirCopiesKeepTheAmendmentDefaults() {
        SimulationConfig original = SimulationConfig.defaults();
        SimulationConfig seeded = original.withSeed(91);
        SimulationConfig counted = original.withSubmissions(1);
        SimulationConfig chained = seeded.withSubmissions(1000000);
        for (SimulationConfig config : new SimulationConfig[]{original, seeded, counted, chained,
                SimulationConfig.defaults()}) {
            assertAmendmentSettings(config, "UTC", 5);
        }
    }

    @Test
    void parsingOtherConfigurationsCannotChangePreviouslyParsedSettingsOrDefaults() throws Exception {
        SimulationConfig first = parse("{\"timeZone\":\"Africa/Addis_Ababa\",\"enumerators\":10000}");
        SimulationConfig second = parse("{\"enumerators\":1,\"timeZone\":\"-04:00\"}");
        SimulationConfig omitted = parse("{}");
        assertAmendmentSettings(first, "Africa/Addis_Ababa", 10000);
        assertAmendmentSettings(second, "-04:00", 1);
        assertAmendmentSettings(omitted, "UTC", 5);
        assertAmendmentSettings(SimulationConfig.defaults(), "UTC", 5);
    }

    @Test
    void repeatedParsingIsDeterministicAndIndependentOfTopLevelKeyOrder() throws Exception {
        SimulationConfig first = parse("{\"enumerators\":9999,\"timeZone\":\"+05:45\"}");
        SimulationConfig same = parse("{\"enumerators\":9999,\"timeZone\":\"+05:45\"}");
        SimulationConfig reordered = parse("{\"timeZone\":\"+05:45\",\"enumerators\":9999}");
        for (SimulationConfig config : new SimulationConfig[]{first, same, reordered}) {
            assertAmendmentSettings(config, "+05:45", 9999);
        }
    }

    @Test
    void newTopLevelKeyNamesRemainValidAsUnrelatedFieldNames() throws Exception {
        SimulationConfig config = parse("""
                {
                  "timeZone": "Africa/Addis_Ababa",
                  "enumerators": 17,
                  "fields": {
                    "timeZone": {"value": "a field answer"},
                    "enumerators": {"value": 123}
                  }
                }
                """);
        assertAmendmentSettings(config, "Africa/Addis_Ababa", 17);
        assertEquals("a field answer", field(config, "timeZone").value().orElseThrow());
        assertEquals("123", field(config, "enumerators").value().orElseThrow());
    }
}
