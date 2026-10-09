package ctosim;

import java.util.List;
import java.util.Objects;

/** One question as the generator sees it. See specs/05-answer-generator.md and 09-constraint-hints.md. */
public record Question(String name, QuestionKind kind, List<String> choices, String appearance, boolean required,
                       ConstraintHints hints) {

    public Question {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(hints, "hints");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices")); // also rejects null entries
    }

    /** A question whose constraint gives no hints. */
    public Question(String name, QuestionKind kind, List<String> choices, String appearance, boolean required) {
        this(name, kind, choices, appearance, required, ConstraintHints.none());
    }
}
