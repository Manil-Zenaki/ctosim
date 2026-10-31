package ctosim;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Changes locale and time zone; form engine shares JVM state")
class InjectedAmendment4RandomnessTest extends InjectedAmendment4Support {
    @Test void eligibleVillagesAreUniformDespiteTheirOrdinaryChoiceWeightsAndDifferentDistances() throws Exception {
        villageTables();
        var form = villageForm(false);
        int draws = 320, b = 0;
        Set<Visit> points = new HashSet<>();
        for (int seed = 1000; seed < 1000 + draws; seed++) {
            var r = run(form, cfg(2, 1, 4, LOCATED + "," + FORCE_A).withSeed(seed)
                    .withProblems(only("travel", .5)));
            assertEquals(1, villageRecords(r).size());
            String destination = value(r.submissions().get(1), "village");
            assertTrue(Set.of("B", "C").contains(destination));
            if (destination.equals("B")) b++;
            points.add(r.visit(2));
        }
        assertEquals(draws / 2.0, b, 7 * Math.sqrt(draws * .5 * .5) + 2,
                "both viable distant candidates have equal probability, even across differently weighted districts");
        assertTrue(points.size() > 1, "different seeds must affect the drawn visits");
    }

    @Test void repeatedCallsFreshPlansAndLocaleTimeZoneChangesReproduceAllObservableResults() throws Exception {
        villageTables();
        var form = villageForm(true);
        var config = cfg(12, 3, 4, LOCATED).withProblems(new ProblemRates(0, .08333333333333333, 0, 0, 0, 0, .25));
        var r = run(form, config);
        assertFalse(villageRecords(r).isEmpty());
        var visits = java.util.List.copyOf(r.plan().visits());
        var problems = java.util.List.copyOf(r.plan().problems());
        var warnings = java.util.List.copyOf(r.plan().warnings());
        sameRun(r.submissions(), r.plan().walkAll());
        assertEquals(visits, r.plan().visits());
        assertEquals(problems, r.plan().problems());
        assertEquals(warnings, r.plan().warnings());
        var locale = Locale.getDefault(); var zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var again = run(form, config);
            sameRun(r.submissions(), again.submissions());
            assertEquals(visits, again.plan().visits());
            assertEquals(problems, again.plan().problems());
            assertEquals(warnings, again.plan().warnings());
        } finally {
            Locale.setDefault(locale); TimeZone.setDefault(zone);
        }
    }

    @Test void addingGpsDoesNotChangeVillageTravelTargetsOrTheSeededGapDraws() throws Exception {
        villageTables();
        var config = cfg(8, 4, 4, LOCATED + "," + FORCE_A).withProblems(only("travel", .5));
        var without = run(villageForm(false), config);
        var with = run(villageForm(true), config);
        assertEquals(Set.of(5, 6, 7, 8), targets(without, "travel"));
        assertEquals(targets(without, "travel"), targets(with, "travel"));
        for (int n = 5; n <= 8; n++) {
            assertEquals(value(without.submissions().get(n - 1), "village"), value(with.submissions().get(n - 1), "village"));
            assertEquals(gap(without.submissions().get(n - 5), without.submissions().get(n - 1)),
                    gap(with.submissions().get(n - 5), with.submissions().get(n - 1)));
            assertEquals(without.visit(n).latitude(), with.visit(n).latitude());
            assertEquals(without.visit(n).longitude(), with.visit(n).longitude());
        }
    }

    @Test void aVillageTravelAnswerKeyIsEmptyBeforeWalkingAndUnmodifiableAfterwards() throws Exception {
        villageTables();
        var plan = new Fieldwork(villageForm(false), cfg(2, 1, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertTrue(plan.problems().isEmpty());
        plan.walkAll();
        assertEquals(1, plan.problems().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.visits().clear());
    }
}
