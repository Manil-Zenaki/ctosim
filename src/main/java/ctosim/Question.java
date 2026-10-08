package ctosim;

import java.util.List;
import java.util.Objects;

/** One question as the generator sees it. See specs/05-answer-generator.md. */
public record Question(String name, QuestionKind kind, List<String> choices, String appearance, boolean required) {

    public Question {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(appearance, "appearance");
        choices = List.copyOf(Objects.requireNonNull(choices, "choices")); // also rejects null entries
    }
}
