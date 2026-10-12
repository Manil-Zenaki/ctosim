package ctosim;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static ctosim.ConstraintHintsTestSupport.attribute;
import static ctosim.FormWalkerTestSupport.*;

/** Test data builders, based only on spec 09 Amendment 2. */
final class ConstraintHintsAmendment2Support {
    private ConstraintHintsAmendment2Support() { }

    static Hints hints() { return new Hints(); }

    static final class Hints {
        Bound lower, upper;
        DateBound earliest, latest;
        Integer minLength, maxLength, minSelected, maxSelected;
        String pattern, prefix;
        Set<String> excluded = new LinkedHashSet<>(), included = new LinkedHashSet<>();
        List<ConstraintHints> alternatives = new ArrayList<>();
        List<String> candidates = new ArrayList<>();

        Hints range(double lo, double hi) { lower = new Bound(lo, true); upper = new Bound(hi, true); return this; }
        Hints dates(LocalDate lo, LocalDate hi) { earliest = new DateBound(lo, true); latest = new DateBound(hi, true); return this; }
        Hints length(Integer lo, Integer hi) { minLength = lo; maxLength = hi; return this; }
        Hints count(Integer lo, Integer hi) { minSelected = lo; maxSelected = hi; return this; }
        Hints prefix(String p) { prefix = p; return this; }
        Hints pattern(String p) { pattern = p; return this; }
        Hints exclude(String... values) { excluded.addAll(List.of(values)); return this; }
        Hints include(String... values) { included.addAll(List.of(values)); return this; }
        Hints alternatives(ConstraintHints... values) { alternatives.addAll(List.of(values)); return this; }
        Hints candidates(String... values) { candidates.addAll(List.of(values)); return this; }
        ConstraintHints build() {
            return new ConstraintHints(lower, upper, earliest, latest, minLength, maxLength, pattern,
                    prefix, excluded, included, minSelected, maxSelected, alternatives, candidates);
        }
    }

    static Question select(QuestionKind kind, ConstraintHints h, String... choices) {
        return new Question("answer", kind, List.of(choices), "", true, h);
    }

    static String constraint(String name, String type, String expression) {
        return bind(name, type, "required=\"true()\" constraint=\"" + attribute(expression) + "\"");
    }

    static String selectControl(String name, boolean multiple, String... choices) {
        String tag = multiple ? "select" : "select1";
        StringBuilder xml = new StringBuilder("<" + tag + " ref=\"/data/" + name + "\"><label>Choice</label>");
        for (String choice : choices) xml.append("<item><label>").append(choice)
                .append("</label><value>").append(choice).append("</value></item>");
        return xml.append("</").append(tag).append(">").toString();
    }
}
