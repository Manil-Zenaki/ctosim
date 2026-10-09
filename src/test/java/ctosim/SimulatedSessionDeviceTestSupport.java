package ctosim;

import java.util.LinkedHashMap;
import java.util.Map;
import org.javarosa.form.api.FormEntryController;

import static ctosim.SimulatedSessionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class SimulatedSessionDeviceTestSupport {
    static final Map<String, String> PROPERTY_FIELDS = Map.of(
            "username", "operator", "deviceid", "device", "subscriberid", "subscriber",
            "simserial", "sim", "phonenumber", "phone", "vendor.custom-id", "custom",
            "USERNAME", "uppercase", "appareil-\u00e9quipe", "unicode");

    private SimulatedSessionDeviceTestSupport() {}

    static LoadedForm deviceForm() throws Exception {
        return loadXml(fixture("device-properties.xml"));
    }

    static LoadedForm knownDeviceForm() throws Exception {
        // Spec 01 leaves ordinary-session behavior for other property names unspecified.
        String xml = fixture("device-properties.xml").lines()
                .filter(line -> !line.contains("jr:preloadParams=\"vendor.custom-id\"")
                        && !line.contains("jr:preloadParams=\"USERNAME\"")
                        && !line.contains("jr:preloadParams=\"appareil-"))
                .collect(java.util.stream.Collectors.joining("\n"));
        return loadXml(xml);
    }

    static Map<String, String> properties(String owner) {
        Map<String, String> result = new LinkedHashMap<>();
        PROPERTY_FIELDS.keySet().stream().sorted().forEach(key -> result.put(key, owner + ":" + key));
        return result;
    }

    static void assertPreloads(FormEntryController session, Map<String, String> expected) {
        PROPERTY_FIELDS.forEach((property, field) -> {
            if (expected.containsKey(property)) {
                assertEquals(expected.get(property), value(session, field), property);
            } else {
                assertNull(node(session, field).getValue(), "Missing property must have no answer: " + property);
            }
        });
    }

    static void assertUsername(FormEntryController session, String expected, boolean changed) {
        // Wrapping exposes the empty XPath string without prescribing how an empty answer is stored.
        assertEquals("[" + expected + "]", value(session, "username_result"));
        assertEquals("[" + expected + "]", value(session, "first_username"));
        if (changed) assertEquals("[" + expected + "]", value(session, "changed_username"));
    }
}
