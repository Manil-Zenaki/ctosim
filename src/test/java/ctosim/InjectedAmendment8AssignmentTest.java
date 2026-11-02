package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment6Support.sameResults;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment8AssignmentTest {
    @ParameterizedTest @ValueSource(ints = {2, 4, 6, 8, 10, 12, 13})
    void sixthFaultUsesCanonicalRoundRobinAndNeedsTwelveEnumeratorsToAvoidCumulation(int e) throws Exception {
        var r = run(sixFaults(), settings(e * 140, e, sixSettings(true))
                .withProblems(rates(0, "other", "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        var assigned = SIX.stream().map(f -> soleEnumerator(r, f)).toList();
        int m = Math.min(6, e / 2);
        assertEquals(m, new HashSet<>(assigned).size());
        for (int k = 0; k < SIX.size(); k++) assertEquals(assigned.get(k % m), assigned.get(k));
        assertTrue(e - m >= (e + 1) / 2);
        if (m < 6) { assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 12); }
        else assertTrue(r.plan().warnings().isEmpty(), r.plan().warnings().toString());
        for (var s : r.submissions()) {
            int last = -1;
            for (var p : r.plan().problems()) if (p.submission() == s.number()) {
                int index = SIX.indexOf(p.problem()); assertTrue(index >= last); last = index;
            }
        }
        metadata(r);
    }

    @Test void reversingTheConfiguredFaultsDoesNotChangeAssignmentsOrDraws() throws Exception {
        var f = sixFaults(); var c = settings(1200, 10, sixSettings(true));
        var forward = run(f, c.withProblems(rates(0, SIX.toArray(String[]::new))));
        var reverse = run(f, c.withProblems(rates(0, "other", "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        for (String fault : SIX) assertFalse(records(forward, fault).isEmpty());
        sameResults(forward, reverse); assertEquals(forward.plan().warnings(), reverse.plan().warnings());
    }

    @ParameterizedTest @ValueSource(ints = {4, 10, 12})
    void missingOtherCodesRemovesTheSixthFaultBeforeAssigningTheFiveEarlierOnes(int e) throws Exception {
        var f = sixFaults(); var c = settings(e * 140, e, sixSettings(false));
        var five = run(f, c.withProblems(rates(0, SIX.subList(0, 5).toArray(String[]::new))));
        var disabled = run(f, c.withProblems(rates(0, SIX.toArray(String[]::new))));
        for (String fault : SIX.subList(0, 5)) assertFalse(records(five, fault).isEmpty());
        sameResults(five, disabled);
        assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains("other")).count());
        assertEquals(five.plan().warnings(), disabled.plan().warnings().stream().filter(w -> !w.contains("other")).toList());
    }

    @Test void nonemptyCodesWithNoEligibleQuestionStillOccupyTheirAssignedFaultSlot() throws Exception {
        var f = sixFaults();
        // The form offers O, not absent; all five earlier faults remain observable.
        var c = settings(1200, 10, sixSettings(true).replace("[\"O\"]", "[\"absent\"]"));
        var r = run(f, c.withProblems(rates(0, SIX.toArray(String[]::new))));
        for (String fault : SIX.subList(0, 5)) assertFalse(records(r, fault).isEmpty());
        assertTrue(others(r).isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 12);
    }

    @Test void codesAloneAndAnExplicitEmptyFaultListDoNotRequestOtherInjection() throws Exception {
        var f = simple(false); var c = settings(80, 2, codes("O") + "," + fixed("answer", "a"));
        var absent = run(f, c); var empty = run(f, c.withProblems(rates(0)));
        sameResults(absent, empty); unchanged(absent, "answer", "a");
        assertTrue(absent.plan().warnings().isEmpty()); assertTrue(empty.plan().warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {1, 60})
    void oneEnumeratorCannotCarryOtherAndProducesTheComparisonWarning(int n) throws Exception {
        var f = simple(false); var c = settings(n, 1, codes("O") + "," + fixed("answer", "a"));
        var r = run(f, c.withProblems(rates(0, "other")));
        sameResults(run(f, c), r); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 1);
    }

    @ParameterizedTest @ValueSource(ints = {18, 20})
    void smallSampleWarningUsesNineVersusTenInterviewsRatherThanInjectionCount(int n) throws Exception {
        var f = simple(false); String extra = codes("O") + "," + fixed("answer", "a");
        var r = other(f, n, extra);
        if (n == 20) assertTrue(r.plan().warnings().isEmpty());
        else {
            assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 9);
            assertTrue(r.plan().warnings().get(0).contains(soleEnumerator(other(f, 400, extra), "other")));
        }
    }

    @Test void missingCodesGiveOneWarningAndDoNotBorrowSpecialOrTeamCodes() throws Exception {
        var f = simple(false);
        var c = settings(80, 2, "\"specialValues\":[\"O\"],\"dontKnowCodes\":[\"O\"]," + fixed("answer", "a"));
        var r = run(f, c.withProblems(rates(0, "other")));
        sameResults(run(f, c), r); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "other");
    }

    @Test void missingOtherCodesWarningFollowsOriginalProblemsAndPrecedesOutlierWarnings() throws Exception {
        var r = run(simple(false), settings(80, 2, fixed("answer", "a"))
                .withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, .1, .1, List.of("other"))));
        var warnings = r.plan().warnings();
        // No GPS/village gives one travel warning; eight targets have no numeric outlier question.
        assertEquals(10, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("travel")); assertTrue(warnings.get(1).contains("other"));
        assertEquals(1, warnings.stream().filter(w -> w.contains("other")).count());
    }
}
