package ctosim;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class SimulationConfigRealisticSupport {
    static final String AREA = "{\"minLatitude\":-4,\"maxLatitude\":-3,\"minLongitude\":-75,\"maxLongitude\":-74}";
    static final String NORMAL = "{\"normal\":{\"mean\":-2.5,\"sd\":3.25}}";
    private SimulationConfigRealisticSupport() { }

    static String fixture() throws Exception {
        try (InputStream input = SimulationConfigRealisticSupport.class.getResourceAsStream(
                "/ctosim/simulation-config/amendment-realistic.json")) {
            assertNotNull(input, "Amendment 3 fixture must exist");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String fieldJson(String members) {
        return "{\"fields\":{\"answer\":{" + members + "}}}";
    }

    static String ruleJson(String members) {
        return fieldJson("\"when\":[{\"if\":\"1 = 1\"," + members + "}]");
    }

    static FieldOverride settings(String members, boolean inRule) throws ConfigException {
        FieldOverride result = field(parse(inRule ? ruleJson(members) : fieldJson(members)), "answer");
        return inRule ? result.rules().get(0).override() : result;
    }

    static void assertOverrideEquals(FieldOverride expected, FieldOverride actual) {
        assertAll(
                () -> assertEquals(expected.value(), actual.value()),
                () -> assertEquals(expected.range(), actual.range()),
                () -> assertEquals(expected.weights(), actual.weights()),
                () -> assertEquals(new ArrayList<>(expected.weights().keySet()), new ArrayList<>(actual.weights().keySet())),
                () -> assertEquals(expected.texts(), actual.texts()),
                () -> assertEquals(expected.missingRate(), actual.missingRate()),
                () -> assertEquals(expected.distribution(), actual.distribution()),
                () -> assertEquals(expected.expression(), actual.expression()),
                () -> assertEquals(expected.area(), actual.area()),
                () -> assertEquals(expected.rules().size(), actual.rules().size()));
        for (int i = 0; i < expected.rules().size(); i++) {
            assertEquals(expected.rules().get(i).condition(), actual.rules().get(i).condition());
            assertOverrideEquals(expected.rules().get(i).override(), actual.rules().get(i).override());
        }
    }

    static void assertRetained(SimulationConfig expected, SimulationConfig actual) {
        assertSameSettingsExceptSeedAndSubmissions(expected, actual);
        assertEquals(expected.timeZone(), actual.timeZone());
        assertEquals(expected.enumerators(), actual.enumerators());
        assertEquals(expected.speedViolationRate(), actual.speedViolationRate());
        assertEquals(expected.profiles(), actual.profiles());
        assertEquals(new ArrayList<>(expected.profiles().keySet()), new ArrayList<>(actual.profiles().keySet()));
        assertEquals(expected.area(), actual.area());
        assertEquals(expected.textPools(), actual.textPools());
        assertEquals(new ArrayList<>(expected.textPools().keySet()), new ArrayList<>(actual.textPools().keySet()));
        for (String name : expected.overriddenFields()) {
            assertOverrideEquals(field(expected, name), field(actual, name));
        }
    }

    static void assertFixture(SimulationConfig config) {
        assertEquals(List.of("urban", "rural", "unused"), new ArrayList<>(config.profiles().keySet()));
        assertEquals(List.of(2.5, 7.5, 0.0), new ArrayList<>(config.profiles().values()));
        assertEquals(new Area(8.5, 9.5, 38, 40), config.area().orElseThrow());
        assertEquals(List.of("place", "firstName", "comment", "lastName"), new ArrayList<>(config.textPools().keySet()));
        assertEquals(List.of("Adama", "東京", "Adama"), config.textPools().get("place"));
        assertEquals(List.of("Élodie"), config.textPools().get("firstName"));
        assertEquals(List.of("line one\nline two"), config.textPools().get("comment"));
        assertEquals(List.of("O'Neil", "李"), config.textPools().get("lastName"));
        FieldOverride income = field(config, "income");
        assertEquals(new Distribution("lognormal", 1200.5, 450.25), income.distribution().orElseThrow());
        assertEquals(new NumericRange(0, 10000), income.range().orElseThrow());
        assertEquals(0.15, income.missingRate().orElseThrow());
        assertEquals(List.of("profile() = 'rural'", "${age} < 18"), income.rules().stream().map(OverrideRule::condition).toList());
        FieldOverride rural = income.rules().get(0).override();
        assertEquals(new Distribution("normal", 800, 100), rural.distribution().orElseThrow());
        assertEquals(new NumericRange(0, 3000), rural.range().orElseThrow());
        assertEquals(0, rural.missingRate().orElseThrow());
        assertEquals("0", income.rules().get(1).override().value().orElseThrow());
        assertEquals(1, income.rules().get(1).override().missingRate().orElseThrow());
        assertEquals("${income} * 12", field(config, "derived").expression().orElseThrow());
        assertEquals(new Area(-4, -3, -75, -74), field(config, "gps").area().orElseThrow());
        List<OverrideRule> rules = field(config, "conditional").rules();
        assertEquals(6, rules.size());
        assertEquals("${income} + 1", rules.get(0).override().expression().orElseThrow());
        assertEquals(Map.of("z", 3.0, "a", 1.0), rules.get(1).override().weights());
        assertEquals(List.of("z", "a"), new ArrayList<>(rules.get(1).override().weights().keySet()));
        assertEquals(List.of("Élodie", "東京"), rules.get(2).override().texts());
        assertEquals(new Area(1, 2, 3, 4), rules.get(3).override().area().orElseThrow());
        assertEquals(new DateRange(LocalDate.of(2020, 1, 1), LocalDate.of(2021, 12, 31)), rules.get(4).override().range().orElseThrow());
        assertEquals(0.75, rules.get(5).override().missingRate().orElseThrow());
        rules.forEach(rule -> assertTrue(rule.override().rules().isEmpty()));
    }
}
