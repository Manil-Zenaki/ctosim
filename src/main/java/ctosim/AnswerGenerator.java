package ctosim;

import java.time.LocalDate;
import java.util.random.RandomGenerator;

/** Proposes an answer to one question. See specs/05-answer-generator.md. */
public final class AnswerGenerator {

    public AnswerGenerator(SimulationConfig config) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The answer in XForm value format, or {@code null} to leave the question blank. */
    public String generate(Question question, LocalDate today, RandomGenerator random) {
        throw new UnsupportedOperationException("not implemented");
    }
}
