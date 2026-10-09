package ctosim;

import org.javarosa.form.api.FormEntryController;

/**
 * What a question's constraint says about its valid answers. Every component is null when
 * the constraint gives no such hint. See specs/09-constraint-hints.md.
 */
public record ConstraintHints(Bound lower, Bound upper, DateBound earliest, DateBound latest,
                              Integer minLength, Integer maxLength, String pattern) {

    private static final ConstraintHints NONE = new ConstraintHints(null, null, null, null, null, null, null);

    public static ConstraintHints none() {
        return NONE;
    }

    public static ConstraintHints forCurrentQuestion(FormEntryController session) {
        throw new UnsupportedOperationException("not implemented");
    }
}
