package ctosim;

/** A numeric bound derived from a constraint. See specs/09-constraint-hints.md. */
public record Bound(double value, boolean inclusive) {
}
