package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks and locale/time-zone determinism checks change JVM defaults")
class InjectedProblemsContractTest {
    @Test void problemsAreEmptyAndUnmodifiableBeforeFirstWalk() throws Exception {
        var p = new Fieldwork(singleTimed(0), config(3, 1, "").withProblems(ProblemRates.defaults()));
        assertTrue(p.problems().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> p.problems().add(null));
    }
    @Test void absentSettingPreservesTheOrdinaryPlanAndAllZeroRatesHaveTheSameEffect() throws Exception {
        var f = ordinary(0, true, true, true); var c = config(12, 3, "\"id\":\"id\"");
        var clean = run(f, c); var zero = run(f, c.withProblems(only("short", 0)));
        assertTrue(clean.plan().problems().isEmpty()); assertTrue(zero.plan().problems().isEmpty());
        FieldworkTestSupport.sameRun(clean.submissions(), zero.submissions());
        assertEquals(clean.plan().visits(), zero.plan().visits());
        assertEquals(clean.plan().warnings(), zero.plan().warnings());
        for (int n = 1; n <= 12; n++) ordinary(clean, n);
    }
    @ParameterizedTest @CsvSource({"0,0", "0.000000000001,1", "0.0249,1", "0.025,1", "0.06249,2", "0.0625,3", "0.0875,4", "0.125,5"})
    void countIsRateTimesNRoundedHalfUpWithOneForEveryPositiveRate(double rate, int count) throws Exception {
        for (String name : NAMES) {
            // Twenty first interviews ensure enough originals for the five requested duplicates.
            var r = run(ordinary(600, false, false, true),
                    config(40, 20, "\"id\":\"id\"").withProblems(only(name, rate)));
            assertEquals(count, targets(r, name).size(), name + " at rate " + rate);
            assertMetadata(r);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void durationProblemsCanTargetTheOnlySubmissionAndEveryEnumeratorsFirstInterview(String name) throws Exception {
        for (int n : List.of(1, 7)) {
            var r = run(singleTimed(0), config(n, n + 2, "").withProblems(only(name, 1)));
            assertEquals(n, targets(r, name).size()); assertTrue(r.plan().warnings().isEmpty()); assertMetadata(r);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview", "offhours", "overlap", "travel"})
    void nonDurationProblemsCannotTargetFirstInterviewsEvenWhenEExceedsN(String name) throws Exception {
        var r = run(ordinary(600, false, false, true), config(3, 5, "\"id\":\"id\"").withProblems(only(name, 1)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, name, 3, 0);
        for (int n = 1; n <= 3; n++) ordinary(r, n);
    }
    @Test void allocationExhaustionRespectsTableOrderAndReportsRequestedAndAllocatedCounts() throws Exception {
        var r = run(ordinary(600, false, false, true), config(10, 2, "\"id\":\"id\"")
                .withProblems(new ProblemRates(0, 0, 1, 1, 1, 1, 1)));
        assertEquals(10, targets(r, "short").size());
        for (String name : List.of("long", "offhours", "overlap", "travel")) {
            assertTrue(targets(r, name).isEmpty()); assertWarning(r, name, 10, 0);
        }
        assertEquals(4, r.plan().warnings().size());
        for (int i = 0; i < 4; i++) assertTrue(r.plan().warnings().get(i).contains(NAMES.get(i + 3)));
    }
    @Test void durationAllocationUsesTheRemainingSubmissionsWithoutReplacement() throws Exception {
        var r = run(singleTimed(0), config(10, 3, "").withProblems(new ProblemRates(0, 0, .6, .6, 0, 0, 0)));
        assertEquals(6, targets(r, "short").size()); assertEquals(4, targets(r, "long").size());
        assertMetadata(r); assertWarning(r, "long", 6, 4);
        assertEquals(10, representatives(r).size());
    }
    @ParameterizedTest @ValueSource(strings = {"offhours", "overlap", "travel"})
    void nonDurationAllocationStopsAtNMinusE(String name) throws Exception {
        var r = run(ordinary(600, false, false, true), config(4, 3, "\"id\":\"id\"").withProblems(only(name, 1)));
        assertEquals(Set.of(4), targets(r, name)); assertWarning(r, name, 4, 1);
    }
    @Test void allSevenFamiliesCoexistWithoutSharingTargetsAndRecordsDescribeReturnedSubmissions() throws Exception {
        var r = run(ordinary(600, true, true, true), config(100, 50, "\"id\":\"id\"")
                .withProblems(new ProblemRates(.02, .02, .02, .02, .02, .02, .02)));
        for (String name : NAMES) assertEquals(2, targets(r, name).size(), name);
        assertEquals(14, representatives(r).size()); assertMetadata(r);
        var targets = new HashSet<Integer>(); representatives(r).forEach(p -> targets.add(p.submission()));
        for (int n = 1; n <= 100; n++) if (!targets.contains(n)) ordinary(r, n);
    }
    @Test void untargetedUnrelatedInterviewsKeepCleanScheduleLocationsAndDistinctIds() throws Exception {
        var r = run(ordinary(600, false, false, true), config(120, 20, "\"id\":\"id\"")
                .withProblems(new ProblemRates(.025, .025, .025, .025, .025, .025, .025)));
        assertFalse(r.plan().problems().isEmpty()); assertMetadata(r);
        Set<Integer> excluded = new HashSet<>();
        for (var p : representatives(r)) {
            excluded.add(p.submission());
            if (p.problem().equals("overlap") || p.problem().equals("resent")) excluded.add(r.related(p).number());
        }
        Set<String> ids = new HashSet<>(); List<Integer> clean = new ArrayList<>();
        for (int n = 1; n <= 120; n++) if (!excluded.contains(n)) {
            var s = r.submissions().get(n - 1); var v = r.visit(n);
            var opening = r.config().start().plusDays(day(r, n));
            assertFalse(s.start().isBefore(opening)); assertFalse(s.start().isAfter(opening.plusHours(9)));
            assertTrue(ids.add(value(s, "id")), "clean interviews have distinct household IDs");
            for (int earlier : clean) if (r.visit(earlier).enumerator() == v.enumerator()) {
                assertFalse(s.start().isBefore(r.submissions().get(earlier - 1).end()), "clean interviews do not overlap");
                if (day(r, earlier) == day(r, n)) assertTrue(distance(r.visit(earlier), v) <= 3000.0001);
            }
            ordinary(r, n); clean.add(n);
        }
        assertTrue(clean.size() > 80, "exercise a substantial unaffected part of the plan");
    }
    @Test void repeatedCallsReplaceRatherThanAppendProblemsVisitsAndWarnings() throws Exception {
        var r = run(ordinary(600, true, true, true), config(40, 20, "\"id\":\"id\"")
                .withProblems(ProblemRates.defaults()));
        var problems = List.copyOf(r.plan().problems()); var visits = List.copyOf(r.plan().visits());
        var warnings = List.copyOf(r.plan().warnings());
        for (int i = 0; i < 2; i++) {
            FieldworkTestSupport.sameRun(r.submissions(), r.plan().walkAll());
            assertEquals(problems, r.plan().problems()); assertEquals(visits, r.plan().visits());
            assertEquals(warnings, r.plan().warnings());
        }
    }
    @Test void allReturnedListsAreUnmodifiableAfterInjection() throws Exception {
        var r = run(singleTimed(0), config(3, 1, "").withProblems(new ProblemRates(0, 0, 1, 1, 0, 0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().problems().add(null));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().problems().set(0, r.plan().problems().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().problems().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().visits().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().warnings().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> r.submissions().remove(0));
    }
    @Test void sameBytesAndConfigurationReplayAcrossDifferentLocaleAndTimeZone() throws Exception {
        Locale saved = Locale.getDefault(); TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var c = config(40, 20, "\"id\":\"id\"").withProblems(ProblemRates.defaults());
            var a = run(ordinary(600, true, true, true), c);
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
            var b = run(ordinary(600, true, true, true), c);
            FieldworkTestSupport.sameRun(a.submissions(), b.submissions());
            assertEquals(a.plan().visits(), b.plan().visits()); assertEquals(a.plan().warnings(), b.plan().warnings());
            assertEquals(a.plan().problems(), b.plan().problems());
            assertEquals(AnswerKey.csv(a.plan().problems()), AnswerKey.csv(b.plan().problems()));
        } finally { Locale.setDefault(saved); TimeZone.setDefault(zone); }
    }
    @Test void targetsDoNotDependOnAnswersOrHowManyRandomDrawsTheFormConsumes() throws Exception {
        var c = config(80, 40, "").withProblems(new ProblemRates(0, 0, .15, .15, .15, 0, 0));
        var a = run(singleTimed(0), c); var b = run(clockSteps(12, 0), c);
        for (String name : List.of("short", "long", "offhours")) assertEquals(targets(a, name), targets(b, name));
    }
    @ParameterizedTest @ValueSource(strings = {"short", "offhours"})
    void targetSelectionsAreUniformAcrossEligibleSubmissionNumbers(String name) throws Exception {
        int n = 12, e = 4, runs = 240; int[] hits = new int[n + 1];
        var f = singleTimed(0); var c = config(n, e, "").withProblems(only(name, .25));
        Set<Set<Integer>> selections = new HashSet<>();
        for (int seed = 0; seed < runs; seed++) {
            var r = run(f, c.withSeed(812300L + seed)); var set = targets(r, name);
            assertEquals(3, set.size()); selections.add(set); set.forEach(k -> hits[k]++);
        }
        assertTrue(selections.size() > 20, "different seeds produce meaningfully different selections");
        for (int k = 1; k <= n; k++) {
            if (name.equals("offhours") && k <= e) assertEquals(0, hits[k]);
            else assertUniformBin(hits[k], runs, 3.0 / (name.equals("offhours") ? n - e : n), "target " + k);
        }
    }
}
