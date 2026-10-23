package ctosim;

/** One line of the answer key: a problem put into a submission on purpose. See specs/14-injected-problems.md. */
public record InjectedProblem(int submission, String key, String enumerator, String family, String problem,
                              String check, String field, String value, String cleanValue, String relatedKey,
                              boolean deterministic) {
}
