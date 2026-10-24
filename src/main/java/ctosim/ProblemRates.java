package ctosim;

import java.util.List;

/**
 * Share of submissions carrying each injected problem, and the faults of faulty enumerators. See
 * specs/04-simulation-config.md, Amendments 6 and 7.
 */
public record ProblemRates(double resent, double reinterview, double shortDuration, double longDuration,
                           double offHours, double overlap, double travel, double outliers, List<String> faults) {

    private static final ProblemRates DEFAULTS = new ProblemRates(0.01, 0.01, 0.02, 0.02, 0.02, 0.02, 0.02);

    public ProblemRates {
        check(resent, "resent");
        check(reinterview, "reinterview");
        check(shortDuration, "shortDuration");
        check(longDuration, "longDuration");
        check(offHours, "offHours");
        check(overlap, "overlap");
        check(travel, "travel");
    }

    /** The fieldwork problems only: no outliers and no faults (Amendment 7). */
    public ProblemRates(double resent, double reinterview, double shortDuration, double longDuration,
                        double offHours, double overlap, double travel) {
        this(resent, reinterview, shortDuration, longDuration, offHours, overlap, travel, 0, List.of());
    }

    public static ProblemRates defaults() {
        return DEFAULTS;
    }

    private static void check(double rate, String name) {
        if (!(rate >= 0 && rate <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1: " + rate);
        }
    }
}
