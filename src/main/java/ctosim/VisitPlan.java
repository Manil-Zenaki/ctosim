package ctosim;

/**
 * The form's availability question and the rates of failed visits (number null when absent).
 * See specs/04-simulation-config.md, Amendment 15.
 */
public record VisitPlan(String field, String available, String number,
        double revisitRate, double unreachedRate, int maxVisits) {
}
