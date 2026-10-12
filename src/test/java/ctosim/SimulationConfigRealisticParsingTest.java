package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ctosim.SimulationConfigRealisticSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigRealisticParsingTest {
    @Test
    void defaultsAndEmptyJsonHaveNoRealisticSettings() throws Exception {
        for (SimulationConfig config : List.of(SimulationConfig.defaults(), parse("{}"))) {
            assertTrue(config.profiles().isEmpty());
            assertTrue(config.area().isEmpty());
            assertTrue(config.textPools().isEmpty());
        }
    }

    @Test
    void legacyOverrideHasEmptyNewAccessors() throws Exception {
        FieldOverride answer = override("\"value\":\"unchanged\"");
        assertTrue(answer.distribution().isEmpty());
        assertTrue(answer.expression().isEmpty());
        assertTrue(answer.area().isEmpty());
        assertTrue(answer.rules().isEmpty());
    }

    @Test
    void fixturePreservesAllNewSettingsAndFileOrder() throws Exception {
        assertFixture(parse(fixture()));
    }

    @Test
    void profilesKeepUnnormalizedWeightsUnicodeNamesCaseAndFileOrder() throws Exception {
        SimulationConfig config = parse("{\"profiles\":{\"東京\":0,\"Rural\":2.5,\"rural\":7.5,\"two words\":1e2}}");
        assertEquals(List.of("東京", "Rural", "rural", "two words"), new ArrayList<>(config.profiles().keySet()));
        assertEquals(List.of(0.0, 2.5, 7.5, 100.0), new ArrayList<>(config.profiles().values()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0.000001", "1e20"})
    void aSinglePositiveProfileIsSufficient(String weight) throws Exception {
        assertEquals(Map.of("only", Double.valueOf(weight)), parse("{\"profiles\":{\"only\":" + weight + "}}").profiles());
    }

    @Test
    void customTextPoolsContainOnlySpecifiedPoolsAndPreserveExactEntries() throws Exception {
        SimulationConfig config = parse("{\"textPools\":{\"comment\":[\" repeat \",\"repeat\",\"repeat\",\"東京\\nÉlodie\"]}}");
        assertEquals(Map.of("comment", List.of(" repeat ", "repeat", "repeat", "東京\nÉlodie")), config.textPools());
    }

    @Test
    void emptyTextPoolMappingDoesNotInventOverrides() throws Exception {
        assertTrue(parse("{\"textPools\":{}}").textPools().isEmpty());
    }

    static Stream<Arguments> validDistributions() {
        return Stream.of(
                Arguments.of("normal", "-10", "0", -10.0, 0.0),
                Arguments.of("normal", "0", "2.5", 0.0, 2.5),
                Arguments.of("normal", "1.25e2", "3.5e1", 125.0, 35.0),
                Arguments.of("lognormal", "0.000001", "0", 0.000001, 0.0),
                Arguments.of("lognormal", "1200.5", "450.25", 1200.5, 450.25),
                Arguments.of("lognormal", "1", "1e6", 1.0, 1e6));
    }

    @ParameterizedTest
    @MethodSource("validDistributions")
    void distributionsPreserveVariableParametersWithoutLogTransform(String kind, String mean, String sd,
                                                                  double expectedMean, double expectedSd) throws Exception {
        String members = "\"distribution\":{\"" + kind + "\":{\"mean\":" + mean + ",\"sd\":" + sd + "}}";
        for (boolean inRule : new boolean[]{false, true}) {
            FieldOverride answer = settings(members, inRule);
            assertEquals(new Distribution(kind, expectedMean, expectedSd), answer.distribution().orElseThrow());
            assertTrue(answer.range().isEmpty());
            assertTrue(answer.value().isEmpty());
            assertTrue(answer.expression().isEmpty());
            assertTrue(answer.weights().isEmpty());
            assertTrue(answer.texts().isEmpty());
            assertTrue(answer.missingRate().isEmpty());
            assertTrue(answer.rules().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void distributionAllowsEqualFractionalNumericBoundsAndMissingRate(boolean inRule) throws Exception {
        FieldOverride answer = settings("\"distribution\":" + NORMAL + ",\"min\":0.25,\"max\":0.25,\"missingRate\":1", inRule);
        assertEquals(new Distribution("normal", -2.5, 3.25), answer.distribution().orElseThrow());
        assertEquals(new NumericRange(0.25, 0.25), answer.range().orElseThrow());
        assertEquals(1, answer.missingRate().orElseThrow());
    }

    static Stream<Arguments> validAreas() {
        return Stream.of(
                Arguments.of(-90.0, 90.0, -180.0, 180.0),
                Arguments.of(90.0, 90.0, 180.0, 180.0),
                Arguments.of(-90.0, -90.0, -180.0, -180.0),
                Arguments.of(0.0, 0.0, 0.0, 0.0),
                Arguments.of(-4.5, -3.25, 120.125, 121.75));
    }

    @ParameterizedTest
    @MethodSource("validAreas")
    void areaAcceptsInclusiveEndpointsDegenerateBoxesAndFractionalCoordinates(double minLat, double maxLat,
                                                                             double minLon, double maxLon) throws Exception {
        String area = "{\"minLatitude\":" + minLat + ",\"maxLatitude\":" + maxLat
                + ",\"minLongitude\":" + minLon + ",\"maxLongitude\":" + maxLon + "}";
        Area expected = new Area(minLat, maxLat, minLon, maxLon);
        assertEquals(expected, parse("{\"area\":" + area + "}").area().orElseThrow());
        for (boolean inRule : new boolean[]{false, true}) {
            assertEquals(expected, settings("\"area\":" + area, inRule).area().orElseThrow());
        }
    }

    @Test
    void fieldAreaDoesNotRequireOrOverwriteGlobalArea() throws Exception {
        SimulationConfig config = parse(fieldJson("\"area\":" + AREA));
        assertTrue(config.area().isEmpty());
        assertEquals(new Area(-4, -3, -75, -74), field(config, "answer").area().orElseThrow());
        config = parse("{\"area\":" + AREA + ",\"fields\":{\"answer\":{\"value\":1}}}");
        assertTrue(field(config, "answer").area().isEmpty(), "An absent field setting is not an inherited accessor value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"expr\":\"${unknown} + 1\"", "\"distribution\":{\"normal\":{\"mean\":0,\"sd\":0}}", "\"value\":1"})
    void areaCanAccompanySettingsWithNoExplicitAreaConflict(String members) throws Exception {
        for (boolean inRule : new boolean[]{false, true}) {
            FieldOverride actual = settings(members + ",\"area\":" + AREA + ",\"missingRate\":0.5", inRule);
            assertEquals(new Area(-4, -3, -75, -74), actual.area().orElseThrow());
            assertEquals(0.5, actual.missingRate().orElseThrow());
        }
    }

    @Test
    void newWordsRemainLegalArbitraryFieldAndChoiceNames() throws Exception {
        for (String name : List.of("profiles", "area", "textPools", "expr", "distribution", "when", "if")) {
            SimulationConfig config = parse("{\"fields\":{" + quote(name) + ":{\"weights\":{" + quote(name) + ":2}}}}");
            assertEquals(Map.of(name, 2.0), field(config, name).weights());
            assertTrue(field(config, name).rules().isEmpty());
        }
    }

    @Test
    void escapedNewKeysAndNamesAreDecodedBeforeRecognition() throws Exception {
        SimulationConfig config = parse("{\"profi\\u006ces\":{\"r\\u0075ral\":2},\"textP\\u006fols\":{\"pla\\u0063e\":[\"X\"]},"
                + "\"fields\":{\"answer\":{\"e\\u0078pr\":\"1 + 2\"}}}");
        assertEquals(Map.of("rural", 2.0), config.profiles());
        assertEquals(Map.of("place", List.of("X")), config.textPools());
        assertEquals("1 + 2", field(config, "answer").expression().orElseThrow());
    }
}
