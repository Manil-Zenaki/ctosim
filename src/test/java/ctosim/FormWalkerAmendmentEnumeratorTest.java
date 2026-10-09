package ctosim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendmentEnumeratorTest {
    private int enumerator(Submission result, int total) throws Exception {
        String username = value(result, "username");
        assertTrue(username.matches("enumerator[0-9]+"), username);
        int number = Integer.parseInt(username.substring("enumerator".length()));
        assertTrue(number >= 1 && number <= total, username);
        int width = Math.max(2, Integer.toString(total).length());
        assertEquals("enumerator" + String.format(Locale.ROOT, "%0" + width + "d", number), username);
        return number;
    }

    @ParameterizedTest @ValueSource(ints = {1, 9, 10, 99, 100, 120, 999, 1000, 10000})
    void usernamesUseTheConfiguredWidthAndInclusiveRange(int total) throws Exception {
        var walker = new FormWalker(devices(false), config("\"seed\":49581,\"enumerators\":" + total));
        for (int number = 1; number <= 40; number++) enumerator(walker.walk(number), total);
    }

    @Test void aSingleEnumeratorHasTheSameFifteenDigitDeviceForEverySubmission() throws Exception {
        var walker = new FormWalker(devices(false), config("\"enumerators\":1"));
        String device = value(walker.walk(1), "deviceid");
        assertTrue(device.matches("[0-9]{15}"), device);
        for (int number : new int[]{2, 19, 1, Integer.MAX_VALUE}) {
            Submission result = walker.walk(number);
            assertEquals("enumerator01", value(result, "username"));
            assertEquals(device, value(result, "deviceid"));
        }
    }

    @Test void defaultEnumeratorsAreUniformAndHaveStableDistinctDeviceIds() throws Exception {
        var walker = new FormWalker(devices(false), SimulationConfig.defaults().withSeed(751331));
        int draws = 2500;
        int[] counts = new int[5];
        Map<Integer, String> devices = new HashMap<>();
        for (int number = 1; number <= draws; number++) {
            Submission result = walker.walk(number);
            int who = enumerator(result, 5);
            counts[who - 1]++;
            String device = value(result, "deviceid");
            assertTrue(device.matches("[0-9]{15}"), device);
            String previous = devices.putIfAbsent(who, device);
            if (previous != null) assertEquals(previous, device, "Device is a property of the enumerator");
        }
        for (int i = 0; i < counts.length; i++) assertUniformBin(counts[i], draws, 0.2, "enumerator " + (i + 1));
        assertEquals(5, devices.size());
        assertEquals(5, new HashSet<>(devices.values()).size());
    }

    @Test void usernameFunctionAndPreloadAgreeAndNoOtherDevicePropertyIsSet() throws Exception {
        Submission result = new FormWalker(devices(true), completeConfig()).walk(41);
        String username = value(result, "username");
        assertEquals(username, value(result, "who"));
        assertEquals(username, value(result, "at_here"));
        for (String name : new String[]{"subscriberid", "simserial", "phonenumber", "custom"}) {
            assertEquals(1, count(result, name));
            assertEquals("", value(result, name), name);
        }
    }

    @Test void identityIsReproducibleAcrossWalkOrderAndSeparateLoads() throws Exception {
        var settings = config("\"seed\":9223372036854775807,\"enumerators\":120,\"missingRate\":0");
        var first = new FormWalker(devices(true), settings);
        Submission expected = first.walk(37);
        first.walk(2);
        assertSameSubmission(expected, first.walk(37));
        var second = new FormWalker(devices(true), config("\"seed\":9223372036854775807,\"enumerators\":120,\"missingRate\":0"));
        second.walk(99);
        assertSameSubmission(expected, second.walk(37));
    }

    @Test void identityDependsOnSeedAndNumberRatherThanFormOrOtherSettings() throws Exception {
        var first = new FormWalker(devices(false), config("\"seed\":567,\"enumerators\":7"));
        var second = new FormWalker(devices(true), config("\"seed\":567,\"enumerators\":7,\"timeZone\":\"+05:45\","
                + "\"start\":\"2040-07-01T08:00:00\",\"fieldworkDays\":1,\"missingRate\":0,\"submissions\":1"));
        for (int n = 1; n <= 40; n++) {
            Submission a = first.walk(n);
            Submission b = second.walk(n);
            assertEquals(value(a, "username"), value(b, "username"));
            assertEquals(value(a, "deviceid"), value(b, "deviceid"));
        }
    }

    @Test void deviceIdentityDoesNotDependOnTheNumberOfEnumerators() throws Exception {
        String expected = value(new FormWalker(devices(false), config("\"seed\":735,\"enumerators\":1")).walk(1), "deviceid");
        var walker = new FormWalker(devices(false), config("\"seed\":735,\"enumerators\":5"));
        int matches = 0;
        for (int n = 1; n <= 100; n++) {
            Submission result = walker.walk(n);
            if (enumerator(result, 5) == 1) {
                matches++;
                assertEquals(expected, value(result, "deviceid"));
            }
        }
        assertTrue(matches > 0, "Fixed sample must exercise enumerator 1");
    }

    @Test void changingSeedChangesTheEnumeratorSequence() throws Exception {
        var first = new FormWalker(devices(false), config("\"seed\":71,\"enumerators\":5"));
        var second = new FormWalker(devices(false), config("\"seed\":72,\"enumerators\":5"));
        boolean differs = false;
        for (int n = 1; n <= 40; n++) {
            differs |= !value(first.walk(n), "username").equals(value(second.walk(n), "username"));
        }
        assertTrue(differs, "Seed should affect assignment over a sample; no exact RNG is prescribed");
    }
}
