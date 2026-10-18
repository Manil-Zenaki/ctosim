package ctosim;

/** An expression of the form that the engine could not evaluate. See specs/07-form-walker.md, Amendment 10. */
public record FormError(int submission, String field, String path, String message) {
}
