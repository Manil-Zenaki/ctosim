package ctosim;

import java.util.Map;

/** A question left blank because no answer met its constraint. See specs/07-form-walker.md, Amendment 8. */
public record UnsatisfiedConstraint(int submission, String field, String path, String constraint,
                                    boolean atEndOfForm, Map<String, String> references) {
}
