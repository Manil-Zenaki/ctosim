package ctosim;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/** Test inputs and observable-output checks derived from specs 04, 05 and 09 only. */
final class AnswerGeneratorRealisticSupport {
    static final LocalDate TODAY = LocalDate.of(2040, 3, 1);
    static final long SEED = 0x3505ABCDL;
    static final int DRAWS = 24_000;
    static final String POOLS = "\"textPools\":{\"firstName\":[\"Ada\"],"
            + "\"lastName\":[\"Mensah\"],\"place\":[\"Kumasi\"],\"comment\":[\"All recorded.\"]}";

    private AnswerGeneratorRealisticSupport() { }

    static SimulationConfig config(String members) throws ConfigException {
        return SimulationConfig.parse("{" + members + "}", "answer-realistic-test.json");
    }

    static FieldOverride field(String members) throws ConfigException {
        return config("\"fields\":{\"answer\":{" + members + "}}")
                .field("answer").orElseThrow();
    }

    static String distribution(String kind, double mean, double sd) {
        return "\"distribution\":{\"" + kind + "\":{\"mean\":" + mean + ",\"sd\":" + sd + "}}";
    }

    static String area(double south, double north, double west, double east) {
        return "\"area\":{\"minLatitude\":" + south + ",\"maxLatitude\":" + north
                + ",\"minLongitude\":" + west + ",\"maxLongitude\":" + east + "}";
    }

    static Question question(QuestionKind kind) {
        return new Question("answer", kind,
                kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE
                        ? List.of("a", "b", "c") : List.of(),
                kind == QuestionKind.TEXT ? "numbers_decimal" : "", true);
    }

    static Question text(String name) {
        return new Question(name, QuestionKind.TEXT, List.of(), "", true);
    }

    static Question hints(Question question, ConstraintHints hints) {
        return new Question(question.name(), question.kind(), question.choices(), question.appearance(),
                question.required(), hints);
    }

    static ConstraintHints numericHints(Double lower, boolean lowerInclusive,
                                        Double upper, boolean upperInclusive) {
        return new ConstraintHints(lower == null ? null : new Bound(lower, lowerInclusive),
                upper == null ? null : new Bound(upper, upperInclusive), null, null, null, null, null);
    }

    static List<String> draws(AnswerGenerator generator, Question question, FieldOverride override,
                              RandomGenerator random, int count) {
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(generator.generate(question, TODAY, random, override));
        return values;
    }

    static List<String> draws(SimulationConfig config, Question question, FieldOverride override, int count) {
        return draws(new AnswerGenerator(config), question, override, new Random(SEED), count);
    }

    static double number(String value, QuestionKind kind) {
        assertNotNull(value);
        assertTrue(value.matches(kind == QuestionKind.INTEGER ? "-?(?:0|[1-9][0-9]*)"
                        : "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]{0,1}[1-9])?"),
                "Canonical numeric answer: " + value);
        assertNotEquals("-0", value);
        double result = Double.parseDouble(value);
        assertTrue(Double.isFinite(result));
        return result;
    }

    static void rate(long observed, int total, double probability) {
        double tolerance = 7 * Math.sqrt(total * probability * (1 - probability)) + 2;
        assertEquals(total * probability, (double) observed, tolerance,
                "Frequency for probability " + probability + " out of " + total);
    }
}
