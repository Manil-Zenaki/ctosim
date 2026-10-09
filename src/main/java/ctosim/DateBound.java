package ctosim;

import java.time.LocalDate;

/** A date bound derived from a constraint. See specs/09-constraint-hints.md. */
public record DateBound(LocalDate value, boolean inclusive) {
}
