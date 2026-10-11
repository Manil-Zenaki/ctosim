package ctosim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendment2DeviceIdTest {
    private static String device(Submission result) throws Exception {
        String id = value(result, "deviceid");
        assertTrue(id.matches("[0-9a-f]{16}"), "Android id must be exactly 16 lowercase hex characters: " + id);
        return id;
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void eachSeedProducesAStableHexDeviceForTheOnlyEnumerator(long seed) throws Exception {
        var walker = new FormWalker(deviceForm(false), config("\"enumerators\":1,\"seed\":" + seed));
        String expected = device(walker.walk(1));
        for (int n : new int[]{37, 2, Integer.MAX_VALUE, 1}) assertEquals(expected, device(walker.walk(n)));
    }

    @Test void differentEnumeratorsHaveDistinctIdsAndKeepThemAcrossSubmissions() throws Exception {
        var walker = new FormWalker(deviceForm(false), config("\"seed\":131973,\"enumerators\":20"));
        Map<String, String> byUsername = new HashMap<>();
        for (int n = 1; n <= 600; n++) {
            Submission result = walker.walk(n);
            String id = device(result);
            String prior = byUsername.putIfAbsent(value(result, "username"), id);
            if (prior != null) assertEquals(prior, id);
        }
        assertEquals(20, byUsername.size(), "Fixed sample exercises all twenty enumerators");
        assertEquals(20, new HashSet<>(byUsername.values()).size(), "No two enumerators share a device");
    }

    @Test void deviceIdentityDoesNotDependOnFormOrOtherConfigurationSettings() throws Exception {
        var first = new FormWalker(deviceForm(false), config("\"seed\":7,\"enumerators\":1"));
        var second = new FormWalker(deviceForm(true), config("\"seed\":7,\"enumerators\":1,"
                + "\"timeZone\":\"+05:45\",\"start\":\"2040-01-01T01:02:03\",\"fieldworkDays\":1,"
                + "\"missingRate\":0,\"speedViolationRate\":1,\"submissions\":1"));
        String expected = device(first.walk(1));
        for (int n : new int[]{2, 71, 1}) assertEquals(expected, device(second.walk(n)));
    }

    @Test void changingConfiguredEnumeratorCountKeepsTheSamePersonsDevice() throws Exception {
        String expected = device(new FormWalker(deviceForm(false), config("\"seed\":751,\"enumerators\":1")).walk(1));
        var walker = new FormWalker(deviceForm(false), config("\"seed\":751,\"enumerators\":5"));
        int seen = 0;
        for (int n = 1; n <= 100; n++) {
            Submission result = walker.walk(n);
            if (value(result, "username").equals("enumerator01")) {
                assertEquals(expected, device(result));
                seen++;
            }
        }
        assertTrue(seen > 0);
    }

    @Test void equalSeedReplaysDeviceIdsAcrossSeparateLoadsAndWalkOrder() throws Exception {
        var first = new FormWalker(deviceForm(true), settings(0.5));
        var second = new FormWalker(deviceForm(true), settings(0.5));
        Submission expected = first.walk(47);
        first.walk(3);
        second.walk(101);
        assertEquals(device(expected), device(first.walk(47)));
        assertEquals(device(expected), device(second.walk(47)));
        assertEquals(value(expected, "username"), value(second.walk(47), "username"));
    }

    @Test void seedAffectsDeviceIdsOverASampleWithoutRequiringCollisionFreedomBetweenSeeds() throws Exception {
        LoadedForm form = deviceForm(false);
        HashSet<String> devices = new HashSet<>();
        for (int seed = 1; seed <= 30; seed++)
            devices.add(device(new FormWalker(form, config("\"enumerators\":1,\"seed\":" + seed)).walk(1)));
        assertTrue(devices.size() > 1, "Device must not be a seed-independent constant");
    }
}
