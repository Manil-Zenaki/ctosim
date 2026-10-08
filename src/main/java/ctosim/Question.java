package ctosim;

import java.util.List;

/** One question as the generator sees it. See specs/05-answer-generator.md. */
public record Question(String name, QuestionKind kind, List<String> choices, String appearance, boolean required) {
}
