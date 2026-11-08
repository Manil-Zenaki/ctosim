package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Checks locale/time zone independence and the engine's global state")
class FieldworkAmendment7RandomnessTest {
    private static SimulationConfig randomConfig(int n) throws Exception {
        return config7(n, 2, 2, 3, false, "\"fields\":{" + field("identity", "\"texts\":[\"Zoë\",\"李\",\"Amina\",\"0007\"]") + "}");
    }

    @Test void repeatedCallsAndFreshLoadsReproduceIdentificationAndResetHouseholdHistory() throws Exception {
        var cfg = randomConfig(24);
        var plan = new Fieldwork(textForm(), cfg);
        var first = plan.walkAll();
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        sameRun(first, plan.walkAll());
        assertEquals(visits, plan.visits());
        assertEquals(warnings, plan.warnings());
        var fresh = new Fieldwork(textForm(), cfg);
        sameRun(first, fresh.walkAll());
        assertEquals(visits, fresh.visits());
        assertEquals(warnings, fresh.warnings());
    }

    @Test void localeAndTimeZoneDoNotChangeTheReplayedIdentification() throws Exception {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var left = new Fieldwork(textForm(), randomConfig(12));
            var expected = left.walkAll();
            var visits = List.copyOf(left.visits());
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var right = new Fieldwork(textForm(), randomConfig(12));
            sameRun(expected, right.walkAll());
            assertEquals(visits, right.visits());
            assertEquals(left.warnings(), right.warnings());
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }
    }

    @Test void extendingThePlanKeepsThePrefixIncludingRevisits() throws Exception {
        var shortPlan = new Fieldwork(textForm(), randomConfig(9));
        var shortRows = shortPlan.walkAll();
        var longPlan = new Fieldwork(textForm(), randomConfig(30));
        var longRows = longPlan.walkAll();
        sameRun(shortRows, longRows.subList(0, 9));
        assertEquals(shortPlan.visits(), longPlan.visits().subList(0, 9));
    }

    @Test void differentSeedsCanChangeFirstVisitIdentitiesWhileEachHouseholdRetainsItsOwn() throws Exception {
        var cfg = randomConfig(24);
        Set<List<String>> sequences = new HashSet<>();
        for (long seed : List.of(19L, 157L, 9001L)) {
            var plan = new Fieldwork(textForm(), cfg.withSeed(seed));
            var rows = plan.walkAll();
            Map<String, String> originals = new HashMap<>();
            List<String> firstAnswers = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                var v = plan.visits().get(i);
                String answer = value(rows.get(i), "identity");
                if (v.attempt() == 1) { originals.put(v.id(), answer); firstAnswers.add(answer); }
                else assertEquals(originals.get(v.id()), answer);
            }
            sequences.add(firstAnswers);
        }
        assertTrue(sequences.size() > 1, "seed must be able to change first-visit identities");
    }
}
