package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("These tests change the default locale and time zone and restore them")
class InjectedAmendment3RandomnessTest {
    @Test void targetsAreUniformWithoutReplacementAcrossAllSubmissionNumbers() throws Exception {
        var form = timeline(1, 90, 1); var c = settings(8, 8, "").withProblems(left(.25));
        int draws = 240; int[] counts = new int[8]; Set<Set<Integer>> patterns = new HashSet<>();
        for (int seed = 0; seed < draws; seed++) {
            var r = run(form, c.withSeed(seed)); var selected = targets(r, "leftOpen");
            assertEquals(2, selected.size()); assertEquals(2, r.plan().problems().size());
            patterns.add(selected); for (int n : selected) counts[n - 1]++;
        }
        assertTrue(patterns.size() > 1, "Different seeds meaningfully change targets");
        for (int i = 0; i < counts.length; i++) assertUniformBin(counts[i], draws, .25, "target " + (i + 1));
    }

    @Test void targetsDependOnSeedAndSubmissionCountRatherThanAnswersFormOrEnumeratorCount() throws Exception {
        var form = timeline(2, 90, 1);
        var c = settings(10, 2, "").withProblems(left(.3));
        var first = run(form, c);
        var answers = run(form, settings(10, 2, "\"fields\":{\"q1\":{\"value\":999999},\"q2\":{\"missingRate\":1}}")
                .withProblems(left(.3)));
        var differentForm = run(timeline(7, 120, 0), settings(10, 10, "").withProblems(left(.3)));
        assertEquals(3, targets(first, "leftOpen").size());
        assertEquals(targets(first, "leftOpen"), targets(answers, "leftOpen"));
        assertEquals(targets(first, "leftOpen"), targets(differentForm, "leftOpen"));
    }

    @Test void interruptionFractionsAreUniformAndNotAlwaysAtTheMidpoint() throws Exception {
        int draws = 480; int[] bins = new int[3];
        var r = run(timeline(10, 90, 1), settings(draws, draws, "").withProblems(left(1)));
        assertEquals(draws, targets(r, "leftOpen").size());
        for (var s : r.submissions()) {
            int q = interruption(s, 10).question();
            assertTrue(q >= 2 && q <= 8);
            // f intervals [.2,.4], (.4,.6], (.6,.8] each span two equal question advances.
            bins[q <= 4 ? 0 : q <= 6 ? 1 : 2]++;
        }
        for (int i = 0; i < bins.length; i++) assertUniformBin(bins[i], draws, 1.0 / 3, "fraction third " + i);
    }

    @Test void theSameSeedAndSubmissionUseTheSameFractionWhenScreenTimeIsScaled() throws Exception {
        var c = settings(20, 20, "").withProblems(left(1));
        var first = run(timeline(10, 90, 1), c);
        var doubled = run(timeline(10, 180, 1), c);
        for (int i = 0; i < 20; i++) {
            var a = first.submissions().get(i); var b = doubled.submissions().get(i);
            assertEquals(2 * total(a), total(b));
            assertEquals(interruption(a, 10).question(), interruption(b, 10).question(),
                    "A seed/number-only fraction crosses the same question when all advances double");
        }
    }

    @Test void noSuccessorResumeOffsetsAreUniformWholeMinutesIncludingBothBoundaries() throws Exception {
        int draws = 480; int[] bins = new int[3];
        var r = run(timeline(2, 90, 1), settings(draws, draws, "").withProblems(left(1)));
        for (var s : r.submissions()) {
            long minutes = minutesBetween(opening(r, s).plusDays(1), interruption(s, 2).resume(), 0, 60);
            bins[minutes <= 19 ? 0 : minutes <= 39 ? 1 : 2]++;
        }
        assertUniformBin(bins[0], draws, 20.0 / 61, "0..19");
        assertUniformBin(bins[1], draws, 20.0 / 61, "20..39");
        assertUniformBin(bins[2], draws, 21.0 / 61, "40..60");
    }

    @Test void bothGapsAroundAnEnglobedInterviewAreUniformWholeMinutes() throws Exception {
        int enumerators = 500; int draws = 0; int[] before = new int[3], after = new int[3];
        var r = run(timeline(4, 90, 1), settings(2 * enumerators, enumerators, "").withProblems(left(.25)));
        assertEquals(250, targets(r, "leftOpen").size());
        for (var p : records(r, "leftOpen")) {
            if (p.relatedKey().isEmpty()) continue;
            draws++;
            var a = r.target(p); var b = r.related(p); var pause = interruption(a, 4);
            int first = (int) minutesBetween(pause.begin(), b.start(), 10, 40);
            int second = (int) minutesBetween(b.end(), pause.resume(), 10, 40);
            before[first <= 19 ? 0 : first <= 29 ? 1 : 2]++;
            after[second <= 19 ? 0 : second <= 29 ? 1 : 2]++;
        }
        assertTrue(draws >= 50, "Enough actual englobed interviews to assess both draws");
        for (int[] bins : List.of(before, after)) {
            assertUniformBin(bins[0], draws, 10.0 / 31, "10..19");
            assertUniformBin(bins[1], draws, 10.0 / 31, "20..29");
            assertUniformBin(bins[2], draws, 11.0 / 31, "30..40");
        }
    }

    @Test void repeatedCallsFreshLoadsAndLocaleChangesReplayTheWholePlan() throws Exception {
        var c = settings(12, 3, "").withProblems(left(.25));
        var r = run(timeline(4, 90, 2), c);
        var problems = List.copyOf(r.plan().problems()); var visits = List.copyOf(r.plan().visits());
        var warnings = List.copyOf(r.plan().warnings());
        assertEquals(3, targets(r, "leftOpen").size());
        Locale locale = Locale.getDefault(); TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            FieldworkTestSupport.sameRun(r.submissions(), r.plan().walkAll());
            assertEquals(problems, r.plan().problems()); assertEquals(visits, r.plan().visits());
            assertEquals(warnings, r.plan().warnings());
            var fresh = run(timeline(4, 90, 2), c);
            FieldworkTestSupport.sameRun(r.submissions(), fresh.submissions());
            assertEquals(problems, fresh.plan().problems()); assertEquals(visits, fresh.plan().visits());
            assertEquals(warnings, fresh.plan().warnings());
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }
    }

    @Test void resultsAreInitiallyEmptyAndRemainUnmodifiableAfterLeftOpenInjection() throws Exception {
        var plan = new Fieldwork(timeline(2, 90, 1), settings(1, 1, "").withProblems(left(1)));
        assertTrue(plan.problems().isEmpty()); assertTrue(plan.visits().isEmpty()); assertTrue(plan.warnings().isEmpty());
        var submissions = plan.walkAll(); assertEquals(1, plan.problems().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.visits().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add("mutation"));
        assertThrows(UnsupportedOperationException.class, () -> submissions.clear());
    }
}
