package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment10Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class InjectedAmendment10TargetsTest {
    @ParameterizedTest
    @ValueSource(strings = {"short", "long", "offhours", "overlap", "travel", "outlier", "leftOpen"})
    void targetsFindTheirHouseholdAtFirstVisitEvenWhenAllOrdinaryHouseholdsAreUnreached(String kind) throws Exception {
        String area = kind.equals("travel")
                ? "\"area\":{\"minLatitude\":-20,\"maxLatitude\":20,\"minLongitude\":-20,\"maxLongitude\":20}"
                : POINT;
        var r = run(tracking(), settings10(80, 2, 4, 0, 1, 3, area).withProblems(problem(kind, .1)));
        successfulFirstTargets(r, kind);
        trackingAnswers(r);
        assertTrue(r.plan().visits().stream().anyMatch(v -> !v.found()), "ordinary failures remain");
        for (var p : r.plan().problems()) {
            assertEquals(kind, p.problem());
            assertEquals(key(r.target(p)), p.key());
            assertTrue(r.visit(p.submission()).found());
            assertEquals(1, r.visit(p.submission()).attempt());
        }
    }

    @ParameterizedTest
    @CsvSource({"short,1,1.0,1", "long,1,0.001,1", "outlier,1,1.0,1",
            "short,10,0.25,3", "long,10,0.01,1", "outlier,10,0.25,3",
            "short,37,0.2,7", "long,37,0.2,7", "outlier,37,0.2,7",
            "leftOpen,1,1.0,1", "leftOpen,10,0.25,3"})
    void requestedCountsUseAllSubmissionsAndRetainHalfUpAndPositiveMinimumRules(
            String kind, int n, double rate, int expected) throws Exception {
        var r = run(tracking(), settings10(n, 1, 1, 1, 0, 2, POINT).withProblems(problem(kind, rate)));
        assertEquals(expected, targets(r, kind).size());
        successfulFirstTargets(r, kind);
    }

    @ParameterizedTest @ValueSource(strings = {"short", "long", "outlier", "leftOpen"})
    void everySubmissionCanBeATargetEvenWithNoNaturallySuccessfulHousehold(String kind) throws Exception {
        var r = run(tracking(), settings10(12, 2, 2, 0, 1, 10, POINT).withProblems(problem(kind, 1)));
        assertEquals(12, targets(r, kind).size());
        successfulFirstTargets(r, kind);
        assertEquals(12, r.plan().visits().stream().map(Visit::id).distinct().count());
        assertTrue(r.plan().warnings().isEmpty(), "successful targets create no waiting households");
    }

    @ParameterizedTest @ValueSource(strings = {"short", "long", "outlier"})
    void targetNumbersDoNotDependOnOutcomeRatesOrAvailableRevisitSlots(String kind) throws Exception {
        var form = tracking();
        var found = run(form, settings10(60, 2, 2, 0, 0, 2, POINT).withProblems(problem(kind, .25)));
        for (double revisit : new double[]{0, 1}) {
            var failed = run(form, settings10(60, 2, 2, revisit, 1 - revisit, 2, POINT).withProblems(problem(kind, .25)));
            assertEquals(targets(found, kind), targets(failed, kind), "targets depend on seed and N, not household answers");
        }
    }

    @ParameterizedTest @CsvSource({"short,1,1,1,2", "long,3,2,0,3", "outlier,2,1,0,10"})
    void targetsPostponeWaitingHouseholdsWithoutLosingReorderingOrIncrementingThem(
            String kind, int enumerators, int quota, double revisit, int max) throws Exception {
        var r = run(tracking(), settings10(120, enumerators, quota, revisit, 1 - revisit, max, POINT)
                .withProblems(problem(kind, .3)));
        var selected = targets(r, kind);
        Map<String, Integer> waiting = new HashMap<>();
        Map<String, Visit> first = new HashMap<>();
        int postponed = 0, resumed = 0;
        Set<String> deferred = new HashSet<>();
        for (int n = 1; n <= r.submissions().size(); n++) {
            Visit v = r.visit(n);
            long today = FieldworkTestSupport.day(r.config(), v);
            String next = waiting.entrySet().stream()
                    .filter(e -> r.visit(e.getValue()).enumerator() == v.enumerator())
                    .filter(e -> FieldworkTestSupport.day(r.config(), r.visit(e.getValue())) < today)
                    .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
            if (selected.contains(n)) {
                assertEquals(1, v.attempt()); assertTrue(v.found());
                assertFalse(first.containsKey(v.id()), "target needs a new household, not the queued household");
                if (next != null) { postponed++; deferred.add(next); }
            } else if (next != null) {
                assertEquals(next, v.id(), "next ordinary slot resumes the earliest waiting household");
                Visit previous = r.visit(waiting.get(next));
                assertEquals(previous.attempt() + 1, v.attempt(), "target must not consume an attempt");
                assertEquals(first.get(next).latitude(), v.latitude());
                assertEquals(first.get(next).longitude(), v.longitude());
                if (deferred.remove(next)) resumed++;
                assertEquals(revisit == 1, v.found()); // max=2 for revisited households
            } else {
                assertEquals(1, v.attempt()); assertFalse(v.found());
                assertFalse(first.containsKey(v.id()));
            }
            first.putIfAbsent(v.id(), v);
            waiting.remove(v.id());
            if (!v.found() && v.attempt() < max) waiting.put(v.id(), n);
        }
        assertTrue(postponed > 0, "exercise a target in an otherwise due revisit slot");
        assertTrue(resumed > 0, "exercise a subsequent ordinary slot for a displaced household");
        trackingAnswers(r);
    }

    @Test void repeatedAndFreshWalksReplayTargetsOutcomesDeferredRevisitsAndAnswerKeys() throws Exception {
        var r = run(tracking(), settings10(60, 2, 2, .6, .4, 3, POINT).withProblems(problem("short", .25)));
        successfulFirstTargets(r, "short");
        assertTrue(r.plan().visits().stream().anyMatch(v -> v.attempt() > 1));
        replay(r);
    }

    @Test void multipleProblemKindsReserveDistinctFirstVisitsAndEachKeepTheirNBasedCount() throws Exception {
        var rates = new ProblemRates(0, 0, .1, .1, 0, 0, 0, .1, .1, List.of());
        var r = run(tracking(), settings10(60, 2, 2, 1, 0, 2, POINT).withProblems(rates));
        Set<Integer> all = new HashSet<>();
        for (String kind : List.of("short", "long", "outlier", "leftOpen")) {
            var selected = targets(r, kind); assertEquals(6, selected.size());
            for (int n : selected) assertTrue(all.add(n), "targets cannot be shared across kinds");
            successfulFirstTargets(r, kind);
        }
        assertEquals(24, all.size());
        assertTrue(r.plan().visits().stream().anyMatch(v -> v.attempt() > 1));
    }

    @Test void outcomeAndProblemReplayIsIndependentOfJvmLocaleAndTimeZone() throws Exception {
        Locale locale = Locale.getDefault(); TimeZone zone = TimeZone.getDefault();
        try {
            var form = tracking(); var c = settings10(32, 2, 2, .7, .3, 3, POINT).withProblems(problem("short", .25));
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var baseline = run(form, c);
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var changed = run(form, c);
            assertEquals(baseline.plan().visits(), changed.plan().visits());
            assertEquals(baseline.plan().problems(), changed.plan().problems());
            assertEquals(baseline.plan().warnings(), changed.plan().warnings());
            for (int i = 0; i < 32; i++) FieldworkTestSupport.same(baseline.submissions().get(i), changed.submissions().get(i));
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }
    }

    @Test void differentSeedsCanChangeTargetsAndHouseholdOutcomesWithoutChangingRequestedCount() throws Exception {
        var form = tracking(); Set<Set<Integer>> selections = new HashSet<>(); Set<List<Boolean>> outcomes = new HashSet<>();
        for (int seed = 1; seed <= 8; seed++) {
            var r = run(form, settings10(24, 2, 2, .4, .2, 3, POINT).withSeed(seed).withProblems(problem("short", .25)));
            assertEquals(6, targets(r, "short").size()); selections.add(targets(r, "short"));
            outcomes.add(r.plan().visits().stream().map(Visit::found).toList());
        }
        assertTrue(selections.size() > 1); assertTrue(outcomes.size() > 1);
    }
}
