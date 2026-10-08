package ctosim;

/** Bounds for a field's answers: numeric or date. See specs/04-simulation-config.md. */
public sealed interface FieldRange permits NumericRange, DateRange {
}
