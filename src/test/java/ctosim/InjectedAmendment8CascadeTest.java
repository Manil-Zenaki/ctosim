package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.rates;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment8CascadeTest extends FieldworkAmendment3Support {
    @Override LoadedForm attached(String instance, String binds, String body) throws Exception {
        // Make each protected select a potential Other/specify pair, plus one unprotected positive control.
        Map<String, String> otherValues = Map.of("region", "R2", "district", "D2", "village", "B", "answer", "É02");
        for (String field : List.of("region", "district", "village", "answer")) {
            instance += "<detail_" + field + "/>";
            binds += bind("detail_" + field, "string", relevant(field, otherValues.get(field)));
            body += input("detail_" + field);
        }
        instance += "<probe/><probe_detail/><username/><meta><instanceID/></meta>";
        binds += bind("probe", "select1", "") + bind("probe_detail", "string", relevant("probe", "O"))
                + bind("username", "string", "jr:preload=\"property\" jr:preloadParams=\"username\"")
                + bind("meta/instanceID", "string", "jr:preload=\"uid\"");
        body += InjectedAmendment8Support.select("probe", false, "a", "abc", "O", "Other") + input("probe_detail");
        return super.attached(instance, binds, body);
    }
    private static String options() {
        return "\"enumerators\":2," + codes("R2", "D2", "B", "É02", "O")
                + "," + fixed("probe", "a");
    }
    @Test void surveyIdAndEveryUpperCascadeSelectRemainProtected() throws Exception {
        var f = deep(false); int positive = 0;
        for (int seed = 1; seed <= 40; seed++) {
            var c = idConfig(6, options()).withSeed(seed);
            var clean = run(f, c.withProblems(null)); var actual = run(f, c.withProblems(rates(0, "other")));
            for (int i = 0; i < 6; i++) for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(clean.submissions().get(i), field), value(actual.submissions().get(i), field));
            for (var p : others(actual)) { positive++; assertEquals("probe", p.field()); }
        }
        assertTrue(positive > 0, "unprotected eligible companion receives other");
    }
    @Test void cascadeAnswersCopiedByReinterviewsCannotBeChangedByOther() throws Exception {
        var f = deep(false); int copies = 0, positive = 0;
        for (int seed = 1; seed <= 40; seed++) {
            var r = run(f, idConfig(6, options()).withSeed(seed)
                    .withProblems(new ProblemRates(0, 1.0 / 3, 0, 0, 0, 0, 0, 0, List.of("other"))));
            for (var p : records(r, "reinterview")) {
                copies++;
                for (String field : List.of("region", "district", "village", "answer")) assertEquals(value(r.related(p), field), value(r.target(p), field));
            }
            for (var p : others(r)) { positive++; assertEquals("probe", p.field()); }
        }
        assertTrue(copies > 0); assertTrue(positive > 0);
    }
}
