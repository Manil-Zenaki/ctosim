package ctosim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment5Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Changes JVM locale/time zone and form engine state")
class FieldworkAmendment5RandomnessTest {
    @Test void replayResetsHouseholdsOutcomesQueuesAndWarnings() throws Exception {
        var config = config5(49, 3, 3, .6, .2, 4, "");
        var plan = new Fieldwork(simple(), config);
        assertTrue(plan.visits().isEmpty());
        assertTrue(plan.warnings().isEmpty());
        var firstRows = List.copyOf(plan.walkAll());
        var firstVisits = List.copyOf(plan.visits());
        var firstWarnings = List.copyOf(plan.warnings());
        sameRun(firstRows, plan.walkAll());
        assertEquals(firstVisits, plan.visits());
        assertEquals(firstWarnings, plan.warnings());
        var fresh = new Fieldwork(simple(), config);
        sameRun(firstRows, fresh.walkAll());
        assertEquals(firstVisits, fresh.visits());
        assertEquals(firstWarnings, fresh.warnings());
        assertTrue(firstVisits.stream().anyMatch(v -> v.attempt() > 1), "exercise queued state");
        assertThrows(UnsupportedOperationException.class, () -> plan.visits().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> plan.configWarnings().add("extra"));
    }

    @Test void outcomesAndSchedulingDoNotDependOnMachineLocaleOrTimeZone() throws Exception {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            var config = config5(31, 2, 3, .7, .3, 3, "");
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var baseline = new Fieldwork(simple(), config);
            var rows = List.copyOf(baseline.walkAll());
            var visits = List.copyOf(baseline.visits());
            var warnings = List.copyOf(baseline.warnings());
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var changed = new Fieldwork(simple(), config);
            sameRun(rows, changed.walkAll());
            assertEquals(visits, changed.visits());
            assertEquals(warnings, changed.warnings());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }

    @Test void extendingThePlanKeepsEveryEarlierVisitAndSubmission() throws Exception {
        var config = config5(19, 2, 4, .6, .3, 4, "");
        var shortPlan = new Fieldwork(simple(), config);
        var prefix = List.copyOf(shortPlan.walkAll());
        var visits = List.copyOf(shortPlan.visits());
        var longer = new Fieldwork(simple(), config.withSubmissions(41));
        sameRun(prefix, longer.walkAll().subList(0, 19));
        assertEquals(visits, longer.visits().subList(0, 19));
    }

    @Test void outcomesAreIndependentOfAnswerAndLocationDraws() throws Exception {
        // Quota one isolates day selection from question duration. Unrelated extra
        // questions must not perturb the separately seeded household outcomes.
        var config = config5(120, 2, 1, .55, .25, 4, SMALL_AREA);
        var baseline = new Fieldwork(simple(), config);
        baseline.walkAll();
        var extra = fixture("<noise/><gps/><answer/><status/><attempt/>",
                bind("noise", "string", "") + bind("gps", "geopoint", "")
                        + bind("answer", "int", "") + bind("status", "string", "")
                        + bind("attempt", "int", ""),
                input("noise") + input("gps") + input("answer")
                        + choices("status", "yes", "absent", "busy") + input("attempt"));
        var changed = new Fieldwork(extra, config);
        changed.walkAll();
        assertEquals(baseline.visits(), changed.visits());
    }

    @Test void theFailedValueDependsOnSeedAndSubmissionNumberNotEarlierAnswers() throws Exception {
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"}");
        var plain = fixture("<status/>", bind("status", "string", ""), choices("status", "yes", "a", "b", "c"));
        var noisy = fixture("<noise/><status/>", bind("noise", "string", "") + bind("status", "string", ""),
                input("noise") + choices("status", "yes", "a", "b", "c"));
        var a = new FormWalker(plain, config);
        var b = new FormWalker(noisy, config);
        for (int n = 1; n <= 70; n++) {
            assertEquals(value(a.walk(n, supplied(1, false)), "status"), value(b.walk(n, supplied(1, false)), "status"));
        }
    }

    @Test void differentSeedsChangeOutcomePatternsAndFailedChoiceSequences() throws Exception {
        var config = config5(90, 1, 1, .5, .25, 4, SMALL_AREA);
        var first = new Fieldwork(simple(), config);
        var second = new Fieldwork(simple(), config.withSeed(981273));
        var rows = first.walkAll();
        var otherRows = second.walkAll();
        assertNotEquals(first.visits().stream().map(v -> v.attempt() + ":" + v.found()).toList(),
                second.visits().stream().map(v -> v.attempt() + ":" + v.found()).toList());
        assertNotEquals(column5(rows, "status"), column5(otherRows, "status"));
    }

    @ParameterizedTest @ValueSource(ints = {2, 5, 10})
    void revisitedHouseholdsFailUniformlyFromOneToMaxMinusOneTimesBeforeSuccess(int max) throws Exception {
        // Fixed seed; quota one completes each household before admitting another.
        // Drop only the last incomplete household, not any outcome-dependent subset.
        var plan = new Fieldwork(simple(), config5(max * 240, 1, 1, 1, 0, max, SMALL_AREA));
        plan.walkAll();
        Map<String, List<Visit>> grouped = households(plan.visits());
        int[] successes = new int[max + 1];
        int completed = 0;
        for (List<Visit> visits : grouped.values()) {
            for (int i = 0; i < visits.size() - 1; i++) assertFalse(visits.get(i).found());
            Visit last = visits.get(visits.size() - 1);
            assertFalse(visits.get(0).found());
            if (!last.found()) continue;
            assertTrue(last.attempt() >= 2 && last.attempt() <= max);
            assertEquals(last.attempt(), visits.size());
            successes[last.attempt()]++;
            completed++;
        }
        assertTrue(completed >= 239, "enough complete households for a distribution check");
        for (int attempt = 2; attempt <= max; attempt++) {
            assertUniformBin(successes[attempt], completed, 1.0 / (max - 1), "success on attempt " + attempt);
        }
    }

    @Test void mixedOutcomeRatesApplyToHouseholdsRatherThanSubmissionCounts() throws Exception {
        var config = config5(2200, 1, 1, .35, .2, 4, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        FieldworkAmendment5PlanTest.checkQueue(plan, config, rows, 4);
        int immediate = 0, revisited = 0, unreached = 0;
        for (List<Visit> visits : households(plan.visits()).values()) {
            Visit last = visits.get(visits.size() - 1);
            if (!last.found() && last.attempt() < 4) continue;
            if (!last.found()) unreached++;
            else if (last.attempt() == 1) immediate++;
            else revisited++;
            for (int i = 0; i < visits.size() - 1; i++) assertFalse(visits.get(i).found());
        }
        int total = immediate + revisited + unreached;
        assertTrue(total >= 549);
        assertUniformBin(immediate, total, .45, "found first time");
        assertUniformBin(revisited, total, .35, "found on a revisit");
        assertUniformBin(unreached, total, .2, "never found after maxVisits");
    }

    private static Map<String, List<Visit>> households(List<Visit> visits) {
        Map<String, List<Visit>> result = new HashMap<>();
        for (Visit visit : visits) result.computeIfAbsent(visit.id(), ignored -> new ArrayList<>()).add(visit);
        return result;
    }
}
