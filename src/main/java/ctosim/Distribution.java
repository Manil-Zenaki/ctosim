package ctosim;

/** A distribution numbers are drawn from: kind "normal" or "lognormal". See specs/04-simulation-config.md, Amendment 3. */
public record Distribution(String kind, double mean, double sd) { }
