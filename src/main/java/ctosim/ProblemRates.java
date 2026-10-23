package ctosim;

/** Share of submissions carrying each injected problem. See specs/04-simulation-config.md, Amendment 6. */
public record ProblemRates(double resent, double reinterview, double shortDuration, double longDuration,
                           double offHours, double overlap, double travel) {

    public ProblemRates {
        throw new UnsupportedOperationException("not implemented yet");
    }

    public static ProblemRates defaults() {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
