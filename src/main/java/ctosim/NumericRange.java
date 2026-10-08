package ctosim;

/** Numeric bounds, inclusive. See specs/04-simulation-config.md. */
public record NumericRange(double min, double max) implements FieldRange {
}
