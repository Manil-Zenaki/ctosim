package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment15RatesTest {
    static Stream<Arguments> validRates() {
        return bothReaders(Stream.of("revisitRate", "unreachedRate").flatMap(member -> Stream.of(
                "0", "-0.0", "1", "1.0", "1e0", "0.125", "6.25e-1", "1e-12", "0.999999999999")
                .map(token -> Arguments.of(member, token))));
    }

    @ParameterizedTest
    @MethodSource("validRates")
    void eachRateAcceptsInclusiveEndpointsAndJsonNumberForms(String member, String token, boolean load)
            throws Exception {
        String other = member.equals("revisitRate") ? "unreachedRate" : "revisitRate";
        try (Input input = input(visits(quote(member) + ":" + token + "," + quote(other) + ":0"), load)) {
            VisitPlan plan = input.read().visits().orElseThrow();
            double actual = member.equals("revisitRate") ? plan.revisitRate() : plan.unreachedRate();
            assertEquals(Double.parseDouble(token), actual, 0.0); // no signed-zero policy
        }
    }

    static Stream<Arguments> independentlyOmittedDefaults() {
        return bothReaders(Stream.of(
                Arguments.of("\"revisitRate\":0.375", 0.375, 0.025),
                Arguments.of("\"unreachedRate\":0.625", 0.1, 0.625),
                Arguments.of("\"revisitRate\":0", 0.0, 0.025),
                Arguments.of("\"unreachedRate\":0", 0.1, 0.0),
                Arguments.of("\"revisitRate\":0.975", 0.975, 0.025),
                Arguments.of("\"unreachedRate\":0.9", 0.1, 0.9)));
    }

    @ParameterizedTest
    @MethodSource("independentlyOmittedDefaults")
    void supplyingOneRateRetainsTheOtherDefault(String members, double revisit, double unreached, boolean load)
            throws Exception {
        try (Input input = input(visits(members), load)) {
            VisitPlan plan = input.read().visits().orElseThrow();
            assertEquals(revisit, plan.revisitRate());
            assertEquals(unreached, plan.unreachedRate());
        }
    }

    static Stream<Arguments> invalidRates() {
        return bothReaders(Stream.of("revisitRate", "unreachedRate").flatMap(member -> Stream.of(
                "null", "\"0.5\"", "true", "false", "[]", "{}", "-1", "-0.000000000001",
                "1.000000000001", "2", "1e400", "-1e400")
                .map(token -> Arguments.of(member, token))));
    }

    @ParameterizedTest
    @MethodSource("invalidRates")
    void eachRateRejectsWrongTypesAndValuesOutsideItsOwnInterval(String member, String token, boolean load)
            throws Exception {
        String other = member.equals("revisitRate") ? "unreachedRate" : "revisitRate";
        assertInvalid(visits(quote(member) + ":" + token + "," + quote(other) + ":0"), "visits." + member, load);
    }

    static Stream<Arguments> validSums() {
        return bothReaders(Stream.of(
                Arguments.of(0.0, 0.0), Arguments.of(1.0, 0.0), Arguments.of(0.0, 1.0),
                Arguments.of(0.5, 0.5), Arguments.of(0.125, 0.875),
                Arguments.of(0.375, 0.25), Arguments.of(0.5, Math.nextDown(0.5))));
    }

    @ParameterizedTest
    @MethodSource("validSums")
    void rateSumMayBeZeroBelowOneOrExactlyOne(double revisit, double unreached, boolean load) throws Exception {
        try (Input input = input(visits("\"revisitRate\":" + revisit + ",\"unreachedRate\":" + unreached), load)) {
            VisitPlan plan = input.read().visits().orElseThrow();
            assertEquals(revisit, plan.revisitRate());
            assertEquals(unreached, plan.unreachedRate());
        }
    }

    static Stream<Arguments> excessiveSums() {
        return bothReaders(Stream.of(
                "\"revisitRate\":0.75,\"unreachedRate\":0.5",
                "\"unreachedRate\":0.75,\"revisitRate\":0.5",
                "\"revisitRate\":1,\"unreachedRate\":1",
                "\"revisitRate\":0.5,\"unreachedRate\":0.500000000001",
                "\"revisitRate\":0.976", "\"unreachedRate\":0.901",
                "\"revisitRate\":1", "\"unreachedRate\":1").map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("excessiveSums")
    void sumAboveOneIsRejectedAtVisitsIncludingWhenOneRateIsDefaulted(String members, boolean load) throws Exception {
        try (Input input = input(visits(members), load)) {
            ConfigException error = assertThrows(ConfigException.class, input::read);
            SimulationConfigTestSupport.assertDiagnostic(error, input.source(), "visits");
            String message = error.getMessage().replace(input.source(), "");
            assertTrue(java.util.regex.Pattern.compile("(?<![\\w.])visits(?![\\w.\\[])").matcher(message).find(),
                    "A combined-rate error must identify visits itself, not only one of its members: " + message);
        }
    }

    static Stream<Arguments> validCounts() {
        return bothReaders(Stream.of("2", "3", "4", "5", "6", "7", "8", "9", "10", "2.0", "10.000",
                "2e0", "1e1", "20e-1").map(token -> Arguments.of(token, (int) Double.parseDouble(token))));
    }

    @ParameterizedTest
    @MethodSource("validCounts")
    void maxVisitsAcceptsEveryAllowedIntegerAndIntegralDecimalAndExponentForms(String token, int expected, boolean load)
            throws Exception {
        try (Input input = input(visits("\"maxVisits\":" + token), load)) {
            assertEquals(expected, input.read().visits().orElseThrow().maxVisits());
        }
    }

    static Stream<Arguments> invalidCounts() {
        return bothReaders(Stream.of("null", "\"3\"", "true", "false", "[]", "{}", "-1", "0", "1", "11",
                "1.999999999999999999999", "2.000000000000000000001", "9.999999999999999999999",
                "10.000000000000000000001", "2.5", "9.5", "1e-400", "1e400",
                "2147483648", "4294967298", "9223372036854775808").map(Arguments::of));
    }

    @ParameterizedTest
    @MethodSource("invalidCounts")
    void maxVisitsRejectsWrongTypesOutOfBoundsOverflowAndFractionsBeforeRounding(String token, boolean load)
            throws Exception {
        assertInvalid(visits("\"maxVisits\":" + token), "visits.maxVisits", load);
    }

    static Stream<Arguments> zeroRateValidation() {
        return bothReaders(Stream.of(
                Arguments.of("{\"visits\":{\"revisitRate\":0,\"unreachedRate\":0}}", "visits"),
                Arguments.of(visits("\"revisitRate\":0,\"unreachedRate\":0,\"maxVisits\":1"), "visits.maxVisits"),
                Arguments.of(visits("\"revisitRate\":0,\"unreachedRate\":0,\"number\":\"availability\""), "visits"),
                Arguments.of(visits("\"revisitRate\":0,\"unreachedRate\":0,\"available\":\"\""), "visits.available")));
    }

    @ParameterizedTest
    @MethodSource("zeroRateValidation")
    void zeroRatesDoNotDisableValidationOfTheOtherMembers(String json, String path, boolean load) throws Exception {
        assertInvalid(json, path, load);
    }
}
