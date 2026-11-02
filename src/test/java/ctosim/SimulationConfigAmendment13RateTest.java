package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment13Support.*;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment13RateTest {
    @Test
    void defaultsHaveAnOtherRateOfThreePercentAndNoOtherCodesOrProblems() {
        SimulationConfig config = SimulationConfig.defaults();
        assertEquals(.03, config.otherRate());
        assertEquals(List.of(), config.otherCodes());
        assertTrue(config.problems().isEmpty());
    }

    static Stream<Arguments> omittedRates() {
        return Stream.of("{}", "{\"otherCodes\":[\"96\"]}", "{\"problems\":{}}",
                "{\"problems\":{\"faults\":[\"other\"]}}", "{\"specialRate\":1,\"missingRate\":0}")
                .flatMap(json -> Stream.of(false, true).map(load -> Arguments.of(json, load)));
    }

    @ParameterizedTest
    @MethodSource("omittedRates")
    void omittingOtherRateUsesItsOwnDefault(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertEquals(.03, input.read().otherRate());
        }
    }

    static Stream<Arguments> acceptedRates() {
        return Stream.of("0", "-0", "0.0", "1", "1.0", "1e0", "3e-2", "0.125",
                "0.000000000001", "0.9999999999999999", "0.1234567890123456")
                .flatMap(token -> Stream.of(false, true).flatMap(codes -> Stream.of(false, true)
                        .map(load -> Arguments.of(token, codes, load))));
    }

    @ParameterizedTest
    @MethodSource("acceptedRates")
    void ratesAcceptEndpointsFractionsAndExponentsWithOrWithoutCodes(String token, boolean codes, boolean load)
            throws Exception {
        String json = "{\"otherRate\":" + token + (codes ? ",\"otherCodes\":[\"96\"]" : "") + "}";
        try (Input input = input(json, load)) {
            SimulationConfig config = input.read();
            assertEquals(Double.parseDouble(token), config.otherRate(), 0.0);
            assertEquals(codes ? List.of("96") : List.of(), config.otherCodes());
            assertTrue(config.problems().isEmpty());
        }
    }

    static Stream<Arguments> invalidRates() {
        return Stream.of("-0.000000000001", "-1", "1.0000000000000002", "1.01", "2", "100",
                "null", "true", "false", "\"0.03\"", "\"\"", "[]", "{}")
                .flatMap(token -> Stream.of(false, true).flatMap(codes -> Stream.of(false, true)
                        .map(load -> Arguments.of(token, codes, load))));
    }

    @ParameterizedTest
    @MethodSource("invalidRates")
    void invalidRatesReportTheirPathEvenWhenNoCodesAreConfigured(String token, boolean codes, boolean load)
            throws Exception {
        try (Input input = input("{\"otherRate\":" + token
                + (codes ? ",\"otherCodes\":[\"96\"]" : "") + "}", load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), "otherRate");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ratesAreIndependentAndNeedNotSumToOne(boolean load) throws Exception {
        try (Input input = input("{\"otherRate\":1,\"missingRate\":1,\"specialRate\":1,"
                + "\"speedViolationRate\":1,\"otherCodes\":[\"96\"],"
                + "\"fields\":{\"q\":{\"missingRate\":1}},\"consent\":{\"fields\":[\"c\"],\"rate\":1}}", load)) {
            SimulationConfig config = input.read();
            assertEquals(1, config.otherRate());
            assertEquals(1, config.missingRate());
            assertEquals(1, config.specialRate());
            assertEquals(1, config.speedViolationRate());
            assertEquals(1, config.field("q").orElseThrow().missingRate().orElseThrow());
            assertEquals(1, config.consent().orElseThrow().rate());
        }
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"OtherRate\":0.5}", "OtherRate"),
                Arguments.of("{\"otherrate\":0.5}", "otherrate"),
                Arguments.of("{\"otherRates\":0.5}", "otherRates"),
                Arguments.of("{\"otherRate \":0.5}", "otherRate "),
                Arguments.of("{\"problems\":{\"otherRate\":0.5}}", "problems.otherRate"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"otherRate\":0.5}}", "repeats.otherRate"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"otherRate\":0.5}}", "integerRange.otherRate"),
                Arguments.of("{\"fields\":{\"q\":{\"value\":1,\"otherRate\":0.5}}}", "fields.q.otherRate"),
                Arguments.of("{\"fields\":{\"q\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"otherRate\":0.5}]}}}",
                        "fields.q.when[0].otherRate"))
                .flatMap(args -> Stream.of(false, true).map(load -> Arguments.of(args.get()[0], args.get()[1], load)));
    }

    @ParameterizedTest
    @MethodSource("unknownKeys")
    void onlyTheExactTopLevelOtherRateKeyExtendsTheSchema(String json, String path, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), path);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void escapedOtherRateKeyIsAcceptedAlongsideAnUnrelatedFieldWithTheSameName(boolean load) throws Exception {
        try (Input input = input("\ufeff{\"otherR\\u0061te\":0.625,"
                + "\"fields\":{\"otherRate\":{\"weights\":{\"otherRate\":1}}}}", load)) {
            SimulationConfig config = input.read();
            assertEquals(.625, config.otherRate());
            assertEquals(java.util.Map.of("otherRate", 1.0), config.field("otherRate").orElseThrow().weights());
        }
    }

    static Stream<Arguments> duplicateKeys() {
        return Stream.of("otherRate", "otherR\\u0061te").flatMap(key -> Stream.of("0.03", "0.75")
                .flatMap(value -> Stream.of(false, true).map(load -> Arguments.of(
                        "{\"otherRate\":0.03,\"" + key + "\":" + value + "}", load))));
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void duplicateDecodedRateKeysAreRejectedEvenWithIdenticalValues(String json, boolean load) throws Exception {
        try (Input input = input(json, load)) {
            assertDiagnostic(assertThrows(ConfigException.class, input::read), input.source(), null);
        }
    }

    static Stream<Arguments> syntaxErrors() {
        return Stream.of("NaN", "Infinity", "-Infinity", "@", "0.3.4")
                .flatMap(token -> Stream.of(false, true).map(load -> Arguments.of(token, load)));
    }

    @ParameterizedTest
    @MethodSource("syntaxErrors")
    void malformedRateJsonReportsItsLineAndRetainsItsCause(String token, boolean load) throws Exception {
        try (Input input = input("{\n\"otherRate\":\n" + token + "\n}", load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            assertDiagnostic(error, input.source(), null);
            assertNotNull(error.getCause());
            assertTrue(error.getMessage().replace(input.source(), "").matches("(?is).*line\\s*[:=]?\\s*3\\b.*"),
                    error::getMessage);
        }
    }
}
