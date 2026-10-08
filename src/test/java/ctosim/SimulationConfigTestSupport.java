package ctosim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

final class SimulationConfigTestSupport {
    static final String SOURCE = "configuration-étude.json";

    private SimulationConfigTestSupport() { }

    static SimulationConfig parse(String json) throws ConfigException {
        return SimulationConfig.parse(json, SOURCE);
    }

    static String completeJson() throws IOException {
        try (InputStream input = SimulationConfigTestSupport.class.getResourceAsStream(
                "/ctosim/simulation-config/complete.json")) {
            assertNotNull(input, "Complete configuration fixture must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static SimulationConfig complete() throws IOException, ConfigException {
        return parse(completeJson());
    }

    static FieldOverride field(SimulationConfig config, String name) {
        return config.field(name).orElseThrow(() -> new AssertionError("Missing override: " + name));
    }

    static FieldOverride override(String body) throws ConfigException {
        return field(parse("{\"fields\":{\"answer\":{" + body + "}}}"), "answer");
    }

    static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    static void assertDiagnostic(ConfigException error, String source, String location) {
        String message = error.getMessage();
        assertNotNull(message, "Errors must explain the failure");
        assertTrue(message.contains(source), () -> "Missing source in: " + message);
        if (location != null) {
            assertTrue(message.contains(location), () -> "Missing location " + location + " in: " + message);
        }
        // Require explanatory text without prescribing vocabulary or punctuation.
        String explanation = message.replace(source, "");
        if (location != null) {
            explanation = explanation.replace(location, "");
        }
        assertTrue(explanation.codePoints().anyMatch(Character::isLetter),
                () -> "Expected a reason beyond source and location in: " + message);
    }

    static void assertSameSettingsExceptSeedAndSubmissions(SimulationConfig expected,
                                                           SimulationConfig actual) {
        assertAll(
                () -> assertEquals(expected.start(), actual.start()),
                () -> assertEquals(expected.fieldworkDays(), actual.fieldworkDays()),
                () -> assertEquals(expected.missingRate(), actual.missingRate()),
                () -> assertEquals(expected.specialRate(), actual.specialRate()),
                () -> assertEquals(expected.specialValues(), actual.specialValues()),
                () -> assertEquals(expected.repeatMin(), actual.repeatMin()),
                () -> assertEquals(expected.repeatMax(), actual.repeatMax()),
                () -> assertEquals(expected.integerRange(), actual.integerRange()),
                () -> assertEquals(expected.decimalRange(), actual.decimalRange()),
                () -> assertEquals(new ArrayList<>(expected.overriddenFields()),
                        new ArrayList<>(actual.overriddenFields())));
        for (String name : expected.overriddenFields()) {
            FieldOverride left = field(expected, name);
            FieldOverride right = field(actual, name);
            assertAll("override " + name,
                    () -> assertEquals(left.value(), right.value()),
                    () -> assertEquals(left.range(), right.range()),
                    () -> assertEquals(left.weights(), right.weights()),
                    () -> assertEquals(new ArrayList<>(left.weights().keySet()),
                            new ArrayList<>(right.weights().keySet())),
                    () -> assertEquals(left.texts(), right.texts()),
                    () -> assertEquals(left.missingRate(), right.missingRate()));
        }
    }

    static void assertSameSettings(SimulationConfig expected, SimulationConfig actual) {
        assertEquals(expected.seed(), actual.seed());
        assertEquals(expected.submissions(), actual.submissions());
        assertSameSettingsExceptSeedAndSubmissions(expected, actual);
    }
}
