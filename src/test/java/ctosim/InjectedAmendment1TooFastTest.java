package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment1TooFastTest {
    @Test void everyQuestionIsScaledByPointFourWithHalfUpRoundingAndAnswersUnchanged() throws Exception {
        var r = run(clockSteps(10, 0), settings(40, 2, "").withProblems(rates(0, "tooFast")));
        assertEquals(20, records(r, "tooFast").size());
        for (var p : records(r, "tooFast")) {
            var clean = r.clean(p.submission()); var actual = r.target(p); long baseBefore = 0, actualBefore = 0;
            for (int i = 0; i < 10; i++) {
                assertEquals(value(clean, "q" + i), value(actual, "q" + i));
                long base = Long.parseLong(value(clean, "t" + i)), changed = Long.parseLong(value(actual, "t" + i));
                assertEquals(Math.max(1, Math.round((base - baseBefore) * .4)), changed - actualBefore);
                baseBefore = base; actualBefore = changed;
            }
            assertEquals("", p.field()); assertEquals(Long.toString(seconds(actual)), p.value());
            assertEquals(Long.toString(seconds(clean)), p.cleanValue()); assertEquals(clean.answered(), actual.answered());
        }
        for (var s : r.submissions()) if (!targets(r, "tooFast").contains(s.number())) ordinary(r, s.number());
        newMetadata(r);
    }
    @Test void minimumTimeAppliesAfterScalingAndCanEraseTheDurationDifference() throws Exception {
        var r = run(clockSteps(3, 90), settings(24, 2, "").withProblems(rates(0, "tooFast")));
        assertEquals(12, records(r, "tooFast").size());
        for (var p : records(r, "tooFast")) {
            for (int q = 0; q < 3; q++) assertEquals(Long.toString((q + 1) * 90L), value(r.target(p), "t" + q));
            assertEquals("270", p.value()); assertEquals("270", p.cleanValue());
        }
    }
    @Test void exactDurationCalculationsGetRecordsInInstanceOrderWithSameVisitCleanValues() throws Exception {
        var f = fixture("<q/><z/><a/><wrapped/><hidden/>", bind("q", "string", "")
                + bind("a", "int", "calculate=\"duration()\"") + bind("z", "int", "calculate=\" duration() \"")
                + bind("wrapped", "int", "calculate=\"once(duration())\"")
                + bind("hidden", "int", "calculate=\"duration()\" relevant=\"false()\""), input("q"));
        var r = run(f, settings(24, 2, "").withProblems(rates(0, "tooFast")));
        assertEquals(24, records(r, "tooFast").size());
        for (int n : targets(r, "tooFast")) {
            var ps = records(r, "tooFast").stream().filter(p -> p.submission() == n).toList();
            assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList());
            for (var p : ps) assertEquals(value(r.clean(n), p.field()), p.cleanValue());
        }
        newMetadata(r);
    }
    @Test void allIrrelevantDurationFieldsUseTheElapsedSecondsFallback() throws Exception {
        var f = fixture("<q/><elapsed/>", bind("q", "string", "")
                + bind("elapsed", "int", "calculate=\"duration()\" relevant=\"false()\""), input("q"));
        var r = run(f, settings(24, 2, "").withProblems(rates(0, "tooFast")));
        assertEquals(12, records(r, "tooFast").size());
        for (var p : records(r, "tooFast")) {
            assertEquals("", p.field()); assertEquals(Long.toString(seconds(r.target(p))), p.value());
            assertEquals(Long.toString(seconds(r.clean(p.submission()))), p.cleanValue());
        }
    }
    @Test void aFormWithNoHandledQuestionsStillRecordsZeroDuration() throws Exception {
        var f = fixture("<note>kept</note>", bind("note", "string", "readonly=\"true()\""), input("note"));
        var r = run(f, settings(2, 2, "").withProblems(rates(0, "tooFast")));
        assertEquals(1, records(r, "tooFast").size()); var p = records(r, "tooFast").get(0);
        assertEquals("0", p.value()); assertEquals("0", p.cleanValue()); assertEquals(0, r.target(p).answered());
    }
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void durationAndFaultFactorsMultiplyBeforeRoundingAndEachCleanValueKeepsTheOtherProblem(String problem) throws Exception {
        var c = settings(40, 2, ""); var f = clockSteps(12, 0);
        double shortRate = problem.equals("short") ? 1 : 0, longRate = problem.equals("long") ? 1 : 0;
        var r = run(f, c.withProblems(new ProblemRates(0, 0, shortRate, longRate, 0, 0, 0, 0, List.of("tooFast"))));
        var durationOnly = run(f, c.withProblems(new ProblemRates(0, 0, shortRate, longRate, 0, 0, 0)));
        for (var p : records(r, "tooFast")) {
            var base = r.clean(p.submission()); var actual = r.target(p);
            double lower = problem.equals("short") ? .15 : 3, upper = problem.equals("short") ? .30 : 6;
            long baseBefore = 0, actualBefore = 0, fastOnlySeconds = 0;
            for (int q = 0; q < 12; q++) {
                long b = Long.parseLong(value(base, "t" + q)), a = Long.parseLong(value(actual, "t" + q));
                long step = b - baseBefore, actualStep = a - actualBefore;
                // All observations must admit one common duration factor; the 0.4 is applied before rounding.
                lower = Math.max(lower, actualStep == 1 ? 0 : (actualStep - .5) / (.4 * step));
                upper = Math.min(upper, (actualStep + .5) / (.4 * step));
                assertTrue(lower < upper + 1e-10, "multiply both factors before rounding each question");
                fastOnlySeconds += Math.max(1, Math.round(.4 * step)); baseBefore = b; actualBefore = a;
            }
            var d = records(r, problem).stream().filter(x -> x.submission() == p.submission()).findFirst().orElseThrow();
            assertEquals(Long.toString(fastOnlySeconds), d.cleanValue());
            assertEquals(Long.toString(seconds(durationOnly.submissions().get(p.submission() - 1))), p.cleanValue());
            assertEquals(d.value(), p.value());
        }
        assertEquals(20, records(r, "tooFast").size()); newMetadata(r);
    }
    @ParameterizedTest @ValueSource(doubles = {0, .1, .34, 1})
    void speedViolationProbabilityIsTripledAndCappedWithoutChangingTheMinimum(double baseRate) throws Exception {
        var f = fixture("<q/><violations/>", bind("q", "string", "ct:minimum-seconds=\"90\"")
                + bind("violations", "int", "ct:speed-violations=\"count\""), input("q"));
        // settings() fixes speedViolationRate=0; use the older config helper for this single varying setting.
        var c = config(1200, 2, "\"speedViolationRate\":" + baseRate).withProblems(rates(0, "tooFast"));
        var r = run(f, c); String who = soleEnumerator(r, "tooFast"); int faulty = 0, clean = 0;
        for (var s : r.submissions()) {
            assertEquals(90, seconds(s)); String report = value(s, "violations");
            assertTrue(report.isEmpty() || report.equals("1"));
            if (!report.isEmpty()) { if (value(s, "username").equals(who)) faulty++; else clean++; }
        }
        rate(faulty, 600, Math.min(1, 3 * baseRate)); rate(clean, 600, baseRate);
        if (baseRate == 0) assertEquals(0, faulty);
        if (3 * baseRate >= 1) assertEquals(600, faulty);
    }
}
