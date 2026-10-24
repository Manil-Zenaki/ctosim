package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The answer key of injected problems, as CSV. See specs/14-injected-problems.md. */
public final class AnswerKey {

    private static final List<String> HEADER = List.of("KEY", "enumerator", "family", "problem", "check", "field",
            "value", "clean_value", "related_KEY", "kind");

    private AnswerKey() {
    }

    public static String csv(List<InjectedProblem> problems) {
        Objects.requireNonNull(problems, "problems");
        List<List<String>> rows = new ArrayList<>(problems.size() + 1);
        rows.add(HEADER);
        for (InjectedProblem p : problems) {
            Objects.requireNonNull(p, "problem");
            rows.add(List.of(p.key(), p.enumerator(), p.family(), p.problem(), p.check(), p.field(), p.value(),
                    p.cleanValue(), p.relatedKey(), p.deterministic() ? "deterministic" : "statistical"));
        }
        return SurveyCtoExporter.csv(rows);
    }
}
