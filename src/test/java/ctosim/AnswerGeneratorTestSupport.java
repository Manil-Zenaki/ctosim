package ctosim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

/** Fixtures use only the public contracts in specs 04 and 05. */
final class AnswerGeneratorTestSupport {
    static final LocalDate TODAY = LocalDate.of(2040, 3, 1);
    static final int DRAWS = 30_000;
    static final long SEED = 0x5A17BEEFL;

    private AnswerGeneratorTestSupport() { }

    static SimulationConfig config(String members) throws ConfigException {
        return SimulationConfig.parse("{" + members + "}", "answer-generator-test.json");
    }

    static SimulationConfig mixedOverrideFixture() throws IOException, ConfigException {
        String resource = "/ctosim/answer-generator/mixed-overrides.json";
        try (InputStream input = AnswerGeneratorTestSupport.class.getResourceAsStream(resource)) {
            assertNotNull(input, "Missing configuration fixture: " + resource);
            return SimulationConfig.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), resource);
        }
    }

    static AnswerGenerator generator(String members) throws ConfigException {
        return new AnswerGenerator(config(members));
    }

    static AnswerGenerator override(String members) throws ConfigException {
        return generator("\"fields\":{\"answer\":{" + members + "}}");
    }

    static Question question(QuestionKind kind) {
        List<String> choices = kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE
                ? List.of("z", "a", "m", "b", "99", "-99") : List.of();
        return new Question("answer", kind, choices, "", true);
    }

    static Question select(QuestionKind kind, List<String> choices) {
        return new Question("answer", kind, choices, "", true);
    }

    static List<String> draws(AnswerGenerator generator, Question question, int count) {
        return draws(generator, question, TODAY, new Random(SEED), count);
    }

    static List<String> draws(AnswerGenerator generator, Question question, LocalDate today,
                              RandomGenerator random, int count) {
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(generator.generate(question, today, random));
        }
        return result;
    }

    static Map<String, Integer> counts(List<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) counts.merge(value, 1, Integer::sum);
        return counts;
    }

    // Interior rates allow seven binomial standard deviations plus two counts.
    // Probabilities zero and one are exact contractual boundaries.
    static void assertRate(int observed, int trials, double probability, String label) {
        double expected = trials * probability;
        double tolerance = probability == 0 || probability == 1 ? 0
                : 7 * Math.sqrt(trials * probability * (1 - probability)) + 2;
        assertEquals(expected, observed, tolerance, label + " (trials=" + trials + ")");
    }

    static void assertUniform(List<String> values, List<String> allowed) {
        Map<String, Integer> counts = counts(values);
        assertTrue(allowed.containsAll(counts.keySet()), () -> "Unexpected values: " + counts.keySet());
        for (String value : allowed) {
            assertRate(counts.getOrDefault(value, 0), values.size(), 1.0 / allowed.size(), value);
        }
    }
}
