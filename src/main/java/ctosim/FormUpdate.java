package ctosim;

/**
 * The day a new version of the form is deployed, and the version used before it (null when not named).
 * See specs/04-simulation-config.md, Amendment 14.
 */
public record FormUpdate(java.time.LocalDate date, String previousVersion) {
}
