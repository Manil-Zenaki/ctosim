package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigInterviewsPerDayCopiesTest {
    private static String withDailyLimit(String json, int dailyLimit) {
        return "{\"interviewsPerDay\":" + dailyLimit + "," + json.substring(json.indexOf('{') + 1);
    }

    private static String baselineJson() throws Exception {
        String json = SimulationConfigRealisticSupport.fixture();
        return "{\"id\":\"household\"," + json.substring(json.indexOf('{') + 1);
    }

    private static SimulationConfig configured() throws Exception {
        return parse(withDailyLimit(baselineJson(), 37));
    }

    private static void assertRetained(SimulationConfig expected, SimulationConfig actual) {
        SimulationConfigRealisticSupport.assertRetained(expected, actual);
        assertEquals(expected.id(), actual.id());
        assertEquals(expected.interviewsPerDay(), actual.interviewsPerDay());
    }

    @Test
    void addingADailyLimitPreservesEveryPreviouslyConfiguredSetting() throws Exception {
        String json = baselineJson();
        SimulationConfig baseline = parse(json);
        SimulationConfig config = parse(withDailyLimit(json, 37));
        assertEquals(4, baseline.interviewsPerDay());
        assertEquals(37, config.interviewsPerDay());
        assertEquals(baseline.seed(), config.seed());
        assertEquals(baseline.submissions(), config.submissions());
        assertEquals(baseline.id(), config.id());
        SimulationConfigRealisticSupport.assertRetained(baseline, config);
    }

    @Test
    void aDailyLimitOnlyConfigurationPreservesEveryOtherDefault() throws Exception {
        SimulationConfig baseline = SimulationConfig.defaults();
        SimulationConfig config = parse("{\"interviewsPerDay\":37}");
        assertEquals(37, config.interviewsPerDay());
        assertEquals(baseline.seed(), config.seed());
        assertEquals(baseline.submissions(), config.submissions());
        assertEquals(baseline.id(), config.id());
        SimulationConfigRealisticSupport.assertRetained(baseline, config);
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 83, Long.MAX_VALUE})
    void seedCopiesPreserveTheDailyLimitAndAllOtherSettings(long seed) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertRetained(original, copy);
        assertEquals(83, original.seed());
        assertEquals(37, original.interviewsPerDay());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void submissionCopiesPreserveTheDailyLimitAndAllOtherSettings(int count) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertRetained(original, copy);
        assertEquals(19, original.submissions());
        assertEquals(37, original.interviewsPerDay());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 1000})
    void chainedAndUnchangedCopiesKeepBoundaryAndDefaultDailyLimits(int dailyLimit) throws Exception {
        SimulationConfig original = parse("{\"interviewsPerDay\":" + dailyLimit + "}");
        SimulationConfig sameSeed = original.withSeed(original.seed());
        SimulationConfig sameCount = original.withSubmissions(original.submissions());
        assertNotSame(original, sameSeed);
        assertNotSame(original, sameCount);
        assertRetained(original, sameSeed);
        assertRetained(original, sameCount);
        assertRetained(original, sameSeed.withSubmissions(1).withSeed(-9).withSubmissions(1_000_000));
        assertEquals(dailyLimit, original.interviewsPerDay());
    }

    @Test
    void copiesKeepFourWhenTheDailyLimitWasOmitted() throws Exception {
        for (SimulationConfig original : new SimulationConfig[]{SimulationConfig.defaults(), parse("{}"),
                parse(baselineJson())}) {
            assertEquals(4, original.withSeed(99).interviewsPerDay());
            assertEquals(4, original.withSubmissions(1).interviewsPerDay());
            assertEquals(4, original.interviewsPerDay());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1_000_001, Integer.MAX_VALUE})
    void aRejectedSubmissionCopyLeavesTheDailyLimitUnchanged(int count) throws Exception {
        SimulationConfig original = configured();
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertEquals(37, original.interviewsPerDay());
        assertEquals(19, original.submissions());
    }

    @Test
    void separateParsesAndDefaultsDoNotChangePreviouslyReturnedLimits() throws Exception {
        SimulationConfig first = parse("{\"interviewsPerDay\":1}");
        SimulationConfig second = parse("{\"interviewsPerDay\":1000}");
        SimulationConfig omitted = parse("{}");
        assertEquals(1, first.interviewsPerDay());
        assertEquals(1000, second.interviewsPerDay());
        assertEquals(4, omitted.interviewsPerDay());
        assertEquals(4, SimulationConfig.defaults().interviewsPerDay());
        assertEquals(first.interviewsPerDay(), parse("{\"interviewsPerDay\":1}").interviewsPerDay());
    }
}
