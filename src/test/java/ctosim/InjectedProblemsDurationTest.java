package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change JVM time zone")
class InjectedProblemsDurationTest {
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void everyQuestionAdvanceUsesOneCommonFactorWithNearestSecondRounding(String name) throws Exception {
        int questions = 12;
        var r = run(clockSteps(questions, 0), config(40, 5, "").withProblems(only(name, 1)));
        assertEquals(40, targets(r, name).size());
        for (var p : representatives(r)) {
            var target = r.target(p); var clean = r.clean(p.submission());
            double lower = name.equals("short") ? .15 : 3, upper = name.equals("short") ? .30 : 6;
            long oldClean = 0, oldTarget = 0;
            for (int q = 0; q < questions; q++) {
                assertEquals(value(clean, "q" + q), value(target, "q" + q), "answers must keep their random stream");
                long cleanTime = Long.parseLong(value(clean, "t" + q));
                long targetTime = Long.parseLong(value(target, "t" + q));
                long base = cleanTime - oldClean, actual = targetTime - oldTarget;
                assertTrue(base >= 5 && base <= 60); assertTrue(actual >= 1);
                // Each rounded advance constrains the single latent factor; their intersection must be nonempty.
                lower = Math.max(lower, actual == 1 ? 0 : (actual - .5) / base);
                upper = Math.min(upper, (actual + .5) / base);
                assertTrue(lower < upper + 1e-12, "all questions must admit the same factor for submission " + p.submission());
                oldClean = cleanTime; oldTarget = targetTime;
            }
            assertEquals(oldTarget, seconds(target)); assertEquals(clean.answered(), target.answered());
            assertEquals("", p.field()); assertEquals(Long.toString(seconds(target)), p.value());
            assertEquals(Long.toString(seconds(clean)), p.cleanValue()); assertEquals("", p.relatedKey());
        }
        assertMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void everyDurationFieldGetsARecordInFormOrderWithItsSameVisitCounterfactual(String name) throws Exception {
        var r = run(ordinary(0, true, true, false), config(16, 2, "\"id\":\"id\"").withProblems(only(name, .5)));
        assertEquals(16, r.plan().problems().size());
        for (int n : targets(r, name)) {
            var records = r.plan().problems().stream().filter(p -> p.submission() == n).toList();
            assertEquals(List.of("duration_z", "duration_a"), records.stream().map(InjectedProblem::field).toList());
            var target = r.submissions().get(n - 1); var clean = r.clean(n);
            for (var p : records) {
                assertEquals(value(target, p.field()), p.value()); assertEquals(value(clean, p.field()), p.cleanValue());
                assertEquals(Long.toString(seconds(target)), p.value()); assertEquals("", p.relatedKey());
            }
            assertEquals(value(clean, "id"), value(target, "id")); assertEquals(value(clean, "answer"), value(target, "answer"));
        }
    }
    @Test void irrelevantDurationFieldsDoNotProduceRecords() throws Exception {
        var f = fixture("<q/><elapsed/><hidden/>", bind("q", "string", "")
                + bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("hidden", "int", "calculate=\"duration()\" relevant=\"false()\""), input("q"));
        var r = run(f, config(1, 1, "").withProblems(only("short", 1)));
        assertEquals(List.of("elapsed"), r.plan().problems().stream().map(InjectedProblem::field).toList());
    }
    @Test void shortDurationHonorsEachQuestionsMinimumAfterScaling() throws Exception {
        var r = run(clockSteps(4, 90), config(12, 2, "").withProblems(only("short", 1)));
        for (var s : r.submissions()) {
            for (int q = 0; q < 4; q++) assertEquals(Long.toString((q + 1) * 90L), value(s, "t" + q));
            assertEquals(360, seconds(s));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void noHandledQuestionsMeansZeroDurationEvenForAnAbnormalDurationTarget(String name) throws Exception {
        var f = fixture("<elapsed/><note>unchanged</note>", bind("elapsed", "int", "calculate=\"duration()\"")
                + bind("note", "string", "readonly=\"true()\""), input("note"));
        var r = run(f, config(1, 1, "").withProblems(only(name, 1)));
        assertEquals(1, r.plan().problems().size()); var p = r.plan().problems().get(0);
        assertEquals("0", p.value()); assertEquals("0", p.cleanValue()); assertEquals(0, seconds(r.target(p)));
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void scalingDoesNotChangeAnswersSkippedByTheMissingRate(String name) throws Exception {
        var c = config(20, 2, "\"fields\":{\"q0\":{\"missingRate\":1},\"q2\":{\"missingRate\":1}}")
                .withProblems(only(name, 1));
        var r = run(clockSteps(4, 0), c);
        for (var p : representatives(r)) {
            var clean = r.clean(p.submission()); var actual = r.target(p);
            assertEquals(2, actual.answered());
            for (int q = 0; q < 4; q++) assertEquals(value(clean, "q" + q), value(actual, "q" + q));
            assertEquals("", value(actual, "q0")); assertEquals("", value(actual, "q2"));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void factorsAreSpreadUniformlyAcrossTheirSpecifiedInterval(String name) throws Exception {
        int draws = 600; var r = run(clockSteps(8, 0), config(draws, draws, "").withProblems(only(name, 1)));
        double low = name.equals("short") ? .15 : 3, high = name.equals("short") ? .30 : 6;
        double total = 0; int[] bins = new int[3];
        for (var s : r.submissions()) {
            double factor = (double) seconds(s) / seconds(r.clean(s.number())); total += factor;
            // Eight independent rounding errors can shift this estimate by at most 4/base seconds.
            int bin = Math.max(0, Math.min(2, (int) ((factor - low) / (high - low) * 3))); bins[bin]++;
        }
        assertEquals((low + high) / 2, total / draws, (high - low) * .10, "uniform factor mean");
        for (int i = 0; i < 3; i++) assertUniformBin(bins[i], draws, 1.0 / 3, "factor third " + i);
    }
    @Test void scalingPreservesConstraintsRelevanceAndDependentCalculations() throws Exception {
        var f = fixture("<n/><twice/><shown/><hidden/>", bind("n", "int", "constraint=\". &gt;= 10 and . &lt;= 20\"")
                + bind("twice", "int", "calculate=\"/data/n * 2\"")
                + bind("shown", "string", "relevant=\"/data/n &gt;= 10\"")
                + bind("hidden", "string", "relevant=\"/data/n &lt; 0\""), input("n") + input("shown") + input("hidden"));
        var r = run(f, config(20, 5, "").withProblems(new ProblemRates(0, 0, .5, .5, 0, 0, 0)));
        for (var p : representatives(r)) {
            var s = r.target(p); int n = Integer.parseInt(value(s, "n"));
            assertTrue(n >= 10 && n <= 20); assertEquals(Integer.toString(n * 2), value(s, "twice"));
            assertFalse(value(s, "shown").isEmpty()); assertEquals("", value(s, "hidden"));
            assertEquals(value(r.clean(s.number()), "n"), value(s, "n"));
        }
    }
}
