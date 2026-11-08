package ctosim;

import java.util.List;

/**
 * The form's availability question, the rates of failed visits and the final reasons of a failed visit
 * (number null when absent). See specs/04-simulation-config.md, Amendments 15 and 16.
 */
public record VisitPlan(String field, String available, String number,
        double revisitRate, double unreachedRate, int maxVisits, List<String> finalValues) {

    /** A plan without final values. */
    public VisitPlan(String field, String available, String number,
            double revisitRate, double unreachedRate, int maxVisits) {
        this(field, available, number, revisitRate, unreachedRate, maxVisits, List.of());
    }
}
