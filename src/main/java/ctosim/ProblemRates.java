package ctosim;

import java.util.List;

/**
 * Share of submissions carrying each injected problem, and the faults of faulty enumerators. See
 * specs/04-simulation-config.md, Amendments 6 and 7.
 */
public record ProblemRates(double resent, double reinterview, double shortDuration, double longDuration,
                           double offHours, double overlap, double travel, double outliers, List<String> faults) {

    /** The faults a faulty enumerator can carry, in their order (spec 14, Amendment 1). */
    static final List<String> FAULTS = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal");

    private static final ProblemRates DEFAULTS = new ProblemRates(0.01, 0.01, 0.02, 0.02, 0.02, 0.02, 0.02, 0.02, FAULTS);

    public ProblemRates {
        check(resent, "resent");
        check(reinterview, "reinterview");
        check(shortDuration, "shortDuration");
        check(longDuration, "longDuration");
        check(offHours, "offHours");
        check(overlap, "overlap");
        check(travel, "travel");
        check(outliers, "outliers");
        java.util.Objects.requireNonNull(faults, "faults");
        List<String> copy = List.copyOf(faults); // rejects null elements
        for (String fault : copy) {
            if (!FAULTS.contains(fault)) {
                throw new IllegalArgumentException("unknown fault '" + fault + "'; known: " + FAULTS);
            }
        }
        if (new java.util.HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("a fault is named twice: " + copy);
        }
        faults = copy;
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
