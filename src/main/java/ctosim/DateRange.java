package ctosim;

import java.time.LocalDate;

/** Date bounds, inclusive. See specs/04-simulation-config.md. */
public record DateRange(LocalDate min, LocalDate max) implements FieldRange {
}
