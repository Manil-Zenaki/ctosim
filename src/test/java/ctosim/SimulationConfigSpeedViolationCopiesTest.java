package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigSpeedViolationCopiesTest {
    private SimulationConfig configured(String rate) throws Exception {
        return parse("{\"speedViolationRate\":" + rate
                + ",\"timeZone\":\"+05:45\",\"enumerators\":17," + completeJson().substring(1));
    }

    private static void assertPreservedSettings(SimulationConfig original, SimulationConfig copy) {
        assertSameSettingsExceptSeedAndSubmissions(original, copy);
        assertEquals(original.timeZone(), copy.timeZone());
        assertEquals(original.enumerators(), copy.enumerators());
        assertEquals(original.speedViolationRate(), copy.speedViolationRate());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSpeedViolationRateTest#acceptedRates")
    void theRateCoexistsWithEveryEarlierSetting(String number, double expected) throws Exception {
        SimulationConfig config = configured(number);
        assertEquals(expected, config.speedViolationRate(), 0.0);
        assertSameSettings(complete(), config);
        assertEquals(ZoneId.of("+05:45"), config.timeZone());
        assertEquals(17, config.enumerators());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSpeedViolationRateTest#acceptedRates")
    void withSeedPreservesTheRateAndLeavesTheReceiverUnchanged(String number, double expected) throws Exception {
        SimulationConfig original = configured(number);
        long oldSeed = original.seed();
        for (long seed : new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE, oldSeed}) {
            SimulationConfig copy = original.withSeed(seed);
            assertNotSame(original, copy);
            assertEquals(seed, copy.seed());
            assertEquals(original.submissions(), copy.submissions());
            assertPreservedSettings(original, copy);
            assertEquals(expected, copy.speedViolationRate(), 0.0);
            assertEquals(oldSeed, original.seed());
            assertEquals(expected, original.speedViolationRate(), 0.0);
        }
        assertSameSettings(complete(), original);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigSpeedViolationRateTest#acceptedRates")
    void withSubmissionsPreservesTheRateAndLeavesTheReceiverUnchanged(String number, double expected)
            throws Exception {
        SimulationConfig original = configured(number);
        int oldCount = original.submissions();
        for (int count : new int[]{1, 1000000, oldCount}) {
            SimulationConfig copy = original.withSubmissions(count);
            assertNotSame(original, copy);
            assertEquals(count, copy.submissions());
            assertEquals(original.seed(), copy.seed());
            assertPreservedSettings(original, copy);
            assertEquals(expected, copy.speedViolationRate(), 0.0);
            assertEquals(oldCount, original.submissions());
            assertEquals(expected, original.speedViolationRate(), 0.0);
        }
        assertSameSettings(complete(), original);
    }

    @Test
    void alternatingCopiesPreserveANondefaultRateAtEveryStep() throws Exception {
        SimulationConfig original = configured("0.625");
        SimulationConfig seeded = original.withSeed(19);
        SimulationConfig counted = seeded.withSubmissions(1);
        SimulationConfig reseeded = counted.withSeed(Long.MIN_VALUE);
        SimulationConfig recounted = reseeded.withSubmissions(1000000);
        for (SimulationConfig config : new SimulationConfig[]{original, seeded, counted, reseeded, recounted}) {
            assertEquals(0.625, config.speedViolationRate());
            assertPreservedSettings(original, config);
        }
    }

    @Test
    void copiesOfDefaultsRetainTheDefaultRate() throws Exception {
        for (SimulationConfig original : new SimulationConfig[]{SimulationConfig.defaults(), parse("{}")}) {
            assertEquals(0.1, original.withSeed(123).speedViolationRate());
            assertEquals(0.1, original.withSubmissions(1).speedViolationRate());
            assertEquals(0.1, original.withSeed(0).withSubmissions(1000000).speedViolationRate());
            assertEquals(0.1, original.speedViolationRate());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1000001, Integer.MAX_VALUE})
    void aRejectedSubmissionCopyCannotChangeTheRate(int count) throws Exception {
        SimulationConfig original = configured("0.625");
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertEquals(0.625, original.speedViolationRate());
        assertSameSettings(complete(), original);
    }
}
