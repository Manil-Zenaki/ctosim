package ctosim;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.AnswerGeneratorRealisticSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorRealisticDistributionTest {
    static Stream<Arguments> zeroSdCases() {
        return Stream.of(QuestionKind.INTEGER, QuestionKind.DECIMAL, QuestionKind.TEXT).flatMap(kind ->
                Stream.of("normal", "lognormal").flatMap(distribution -> Stream.of(
                        Arguments.of(kind, distribution, 2.5, -10, 10, kind == QuestionKind.INTEGER ? "3" : "2.5"),
                        Arguments.of(kind, distribution, 2.345, -10, 10, kind == QuestionKind.INTEGER ? "2" : "2.35"),
                        Arguments.of(kind, distribution, 1.0, 10, 20, "10"),
                        Arguments.of(kind, distribution, 30.0, 10, 20, "20"),
                        Arguments.of(kind, distribution, 10.0, 10, 20, "10"),
                        Arguments.of(kind, distribution, 20.0, 10, 20, "20"))));
    }

    @ParameterizedTest
    @MethodSource("zeroSdCases")
    void zeroSdUsesMeanClampsBeforeFormattingAndRoundsHalfUp(
            QuestionKind kind, String distribution, double mean, double min, double max, String expected)
            throws Exception {
        FieldOverride override = field(distribution(distribution, mean, 0)
                + ",\"min\":" + min + ",\"max\":" + max);
        for (String value : draws(config(""), question(kind), override, 12)) assertEquals(expected, value);
    }

    static Stream<Arguments> negativeCases() {
        return Stream.of(Arguments.of(QuestionKind.INTEGER, -2.5, "-3"),
                Arguments.of(QuestionKind.INTEGER, -0.5, "-1"),
                Arguments.of(QuestionKind.INTEGER, -0.49, "0"),
                Arguments.of(QuestionKind.INTEGER, 2147483648.5, "2147483649"),
                Arguments.of(QuestionKind.DECIMAL, -2.345, "-2.35"),
                Arguments.of(QuestionKind.TEXT, -2.345, "-2.35"),
                Arguments.of(QuestionKind.DECIMAL, -0.004, "0"),
                Arguments.of(QuestionKind.TEXT, -0.004, "0"));
    }

    @ParameterizedTest
    @MethodSource("negativeCases")
    void normalMeansHandleNegativeHalfTiesZeroAndIntegersBeyond32Bits(
            QuestionKind kind, double mean, String expected) throws Exception {
        FieldOverride override = field(distribution("normal", mean, 0)
                + ",\"min\":-3000000000,\"max\":3000000000");
        assertEquals(expected, draws(config(""), question(kind), override, 1).get(0));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT"})
    void distributionWithoutFieldRangeUsesTheAppropriateConfiguredRange(QuestionKind kind) throws Exception {
        SimulationConfig config = config("\"integerRange\":{\"min\":11,\"max\":12},"
                + "\"decimalRange\":{\"min\":21.25,\"max\":22.75}");
        assertEquals(kind == QuestionKind.INTEGER ? "11" : "21.25",
                draws(config, question(kind), field(distribution("normal", -10, 0)), 1).get(0));
        assertEquals(kind == QuestionKind.INTEGER ? "12" : "22.75",
                draws(config, question(kind), field(distribution("normal", 100, 0)), 1).get(0));
    }

    static Stream<Arguments> sampledDistributions() {
        return Stream.of(QuestionKind.INTEGER, QuestionKind.DECIMAL, QuestionKind.TEXT)
                .flatMap(kind -> Stream.of("normal", "lognormal").map(d -> Arguments.of(kind, d)));
    }

    @ParameterizedTest
    @MethodSource("sampledDistributions")
    void specifiedMeanAndSdDescribeTheVariableRatherThanItsLogarithm(QuestionKind kind, String d)
            throws Exception {
        double mean = d.equals("normal") ? 50 : 20;
        List<String> values = draws(config(""), question(kind), field(distribution(d, mean, 10)
                + ",\"min\":-1000,\"max\":100000"), DRAWS);
        double sum = 0, squares = 0;
        int low = 0, middle = 0;
        for (String text : values) {
            double value = number(text, kind);
            sum += value;
            squares += value * value;
            // The half-unit thresholds give INTEGER and decimal answers the same CDF cut points.
            if (value < 19.5) low++;
            if (value < 49.5) middle++;
        }
        double average = sum / DRAWS;
        assertEquals(mean, average, 0.5, "Mean after negligible truncation and rounding");
        assertEquals(10, Math.sqrt(squares / DRAWS - average * average), 0.65, "Standard deviation");
        // Independent CDF constants, allowing <0.005 of decimal-rounding movement.
        double probability = d.equals("normal") ? 0.480061 : 0.572510;
        assertEquals(probability, (double) (d.equals("normal") ? middle : low) / DRAWS, 0.025,
                "CDF distinguishes a Gaussian/log-Gaussian shape from uniform or two-point draws");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DECIMAL", "TEXT"})
    void normalOutOfRangeDrawsAreResampledRatherThanImmediatelyClipped(QuestionKind kind) throws Exception {
        List<String> values = draws(config(""), question(kind), field(distribution("normal", 0, 1)
                + ",\"min\":-1,\"max\":1"), DRAWS);
        int endpoints = 0, center = 0;
        for (String text : values) {
            double x = number(text, kind);
            assertTrue(x >= -1 && x <= 1);
            if (Math.abs(x) == 1) endpoints++;
            if (Math.abs(x) < 0.5) center++;
        }
        assertTrue(endpoints < DRAWS * 0.02, "Clipping would put about 32% at the endpoints");
        assertEquals(0.56, (double) center / DRAWS, 0.025, "Truncated normal central mass");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DECIMAL", "TEXT"})
    void lognormalOutOfRangeDrawsAreResampled(QuestionKind kind) throws Exception {
        List<String> values = draws(config(""), question(kind), field(distribution("lognormal", 20, 10)
                + ",\"min\":10,\"max\":20"), DRAWS);
        int endpoints = 0;
        for (String text : values) {
            double x = number(text, kind);
            assertTrue(x >= 10 && x <= 20);
            if (x == 10 || x == 20) endpoints++;
        }
        assertTrue(endpoints < DRAWS * 0.02, "Resampling must not accumulate rejected tail mass at endpoints");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DECIMAL", "TEXT"})
    void rareAcceptanceHasTheEndpointMassExpectedFromAboutOneHundredAttempts(QuestionKind kind) throws Exception {
        List<String> values = draws(config(""), question(kind), field(distribution("normal", 0, 1)
                + ",\"min\":2,\"max\":2.2"), DRAWS);
        int endpoints = 0;
        for (String text : values) {
            double x = number(text, kind);
            assertTrue(x >= 2 && x <= 2.2);
            if (x == 2 || x == 2.2) endpoints++;
        }
        // Integrate the stated density, independently of production sampling. Accepted values
        // within half a cent of an end also round to that end. Allow both 100 total attempts
        // and an initial draw plus 100 retries; the wording does not resolve that distinction.
        double accepted = normalMass(2, 2.2);
        double roundedEnds = (normalMass(2, 2.005) + normalMass(2.195, 2.2)) / accepted;
        double fail100 = Math.pow(1 - accepted, 100);
        double fail101 = Math.pow(1 - accepted, 101);
        double p100 = fail100 + (1 - fail100) * roundedEnds;
        double p101 = fail101 + (1 - fail101) * roundedEnds;
        double tolerance = 7 * Math.sqrt(DRAWS * p100 * (1 - p100)) + 2;
        assertTrue(endpoints >= DRAWS * p101 - tolerance && endpoints <= DRAWS * p100 + tolerance,
                "Endpoint mass must reflect the retry budget, not immediate clipping or unlimited rejection");
    }

    private static double normalMass(double from, double to) {
        // Midpoint integration error at this resolution is negligible relative to sampling tolerance.
        int steps = 2000;
        double width = (to - from) / steps;
        double sum = 0;
        for (int i = 0; i < steps; i++) {
            double x = from + (i + 0.5) * width;
            sum += Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI);
        }
        return sum * width;
    }

    @ParameterizedTest
    @MethodSource("sampledDistributions")
    @Timeout(10)
    void unreachableDistributionTerminatesByClampingToTheNearestEnd(QuestionKind kind, String d)
            throws Exception {
        for (double mean : new double[]{0.01, 1_000_000}) {
            FieldOverride override = field(distribution(d, mean, 0.000001) + ",\"min\":10,\"max\":20");
            for (String value : draws(config(""), question(kind), override, 10)) {
                assertEquals(mean < 10 ? "10" : "20", value);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sampledDistributions")
    @Timeout(10)
    void singletonRangeTerminatesEvenWithNonzeroSd(QuestionKind kind, String d) throws Exception {
        FieldOverride override = field(distribution(d, 100, 1) + ",\"min\":7,\"max\":7");
        for (String value : draws(config(""), question(kind), override, 10)) assertEquals("7", value);
    }

    static Stream<Arguments> hintCases() {
        return Stream.of(QuestionKind.INTEGER, QuestionKind.DECIMAL, QuestionKind.TEXT).flatMap(kind ->
                Stream.of(
                        Arguments.of(kind, 12.0, true, 18.0, true, 0, "12"),
                        Arguments.of(kind, 12.0, false, 18.0, false, 0,
                                kind == QuestionKind.INTEGER ? "13" : "12.01"),
                        Arguments.of(kind, 12.0, false, 18.0, false, 100,
                                kind == QuestionKind.INTEGER ? "17" : "17.99"),
                        Arguments.of(kind, 30.0, true, 40.0, true, 0, "30"),
                        Arguments.of(kind, 30.0, true, null, true, 100, "40"),
                        Arguments.of(kind, null, true, 0.0, true, -100, "-10"),
                        Arguments.of(kind, 19.0, true, 11.0, true, 0, "10")));
    }

    @ParameterizedTest
    @MethodSource("hintCases")
    void distributionClampsToTheSameHintAdjustedRangeAsUniformDrawing(
            QuestionKind kind, Double lower, boolean lowerInclusive, Double upper, boolean upperInclusive,
            double mean, String expected) throws Exception {
        Question question = hints(question(kind), numericHints(lower, lowerInclusive, upper, upperInclusive));
        FieldOverride override = field(distribution("normal", mean, 0) + ",\"min\":10,\"max\":20");
        assertEquals(expected, draws(config(""), question, override, 1).get(0));
    }

    @ParameterizedTest
    @MethodSource("sampledDistributions")
    void nonzeroSdAlsoRespectsNarrowedHintRanges(QuestionKind kind, String d) throws Exception {
        Question question = hints(question(kind), numericHints(40.0, true, 60.0, true));
        FieldOverride override = field(distribution(d, 50, 20) + ",\"min\":0,\"max\":100");
        for (String value : draws(config(""), question, override, 1500)) {
            double x = number(value, kind);
            assertTrue(x >= 40 && x <= 60);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT"})
    void distributionUsesTheSelectedSatisfiableAlternativeRange(QuestionKind kind) throws Exception {
        ConstraintHints alternatives = new ConstraintHints(null, null, null, null, null, null, null,
                null, Set.of(), Set.of(), null, null,
                List.of(numericHints(2.0, true, 4.0, true), numericHints(80.0, true, 70.0, true),
                        numericHints(60.0, true, 62.0, true)), List.of());
        List<String> values = draws(config(""), hints(question(kind), alternatives),
                field(distribution("normal", 10, 0)), DRAWS);
        assertEquals(Set.of("4", "60"), new java.util.HashSet<>(values));
        rate(values.stream().filter("4"::equals).count(), DRAWS, 0.5);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void distributionDrawsStillHonorExcludedAnswers(QuestionKind kind) throws Exception {
        ConstraintHints excluded = new ConstraintHints(null, null, null, null, null, null, null,
                null, Set.of("50"), Set.of(), null, null, List.of(), List.of());
        for (String value : draws(config(""), hints(question(kind), excluded),
                field(distribution("normal", 50, 10)), 2000)) assertNotEquals("50", value);
    }
}
