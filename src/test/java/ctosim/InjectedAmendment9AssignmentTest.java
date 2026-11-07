package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment9Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment9AssignmentTest {
    @ParameterizedTest @ValueSource(ints = {2, 4, 6, 8, 10, 12, 14, 15})
    void seventhFaultUsesCanonicalRoundRobinAndNeedsFourteenEnumerators(int e) throws Exception {
        var c = settings(e * 140, e, InjectedAmendment8Support.sixSettings(true) + "," + update("2026-03-02", null));
        var r = run(sevenFaultForm(), c.withProblems(rates(0, FAULT, "other", "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        var assigned = SEVEN.stream().map(f -> soleEnumerator(r, f)).toList();
        int m = Math.min(7, e / 2);
        assertEquals(m, new HashSet<>(assigned).size());
        for (int k = 0; k < 7; k++) assertEquals(assigned.get(k % m), assigned.get(k));
        assertTrue(e - m >= (e + 1) / 2, "at least half stay clean");
        assertEquals(140, outdated(r).size(), "every interview of the assigned enumerator is outdated");
        if (m < 7) { assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 14); }
        else assertTrue(r.plan().warnings().isEmpty(), r.plan().warnings().toString());
        int number = 0, last = -1;
        for (var p : r.plan().problems()) {
            assertTrue(p.submission() >= number);
            if (p.submission() != number) last = -1;
            int order = SEVEN.indexOf(p.problem()); assertTrue(order >= last);
            number = p.submission(); last = order;
        }
        metadata(r, OLD, NEW);
    }

    @Test void configuredFaultOrderDoesNotChangeDrawsOrAssignment() throws Exception {
        var f = sevenFaultForm(); var c = settings(700, 6, InjectedAmendment8Support.sixSettings(true)
                + "," + update("2026-03-02", null));
        var forward = run(f, c.withProblems(rates(0, SEVEN.toArray(String[]::new))));
        var reverse = new ArrayList<>(SEVEN); Collections.reverse(reverse);
        var backward = run(f, c.withProblems(rates(0, reverse.toArray(String[]::new))));
        for (String fault : SEVEN) assertFalse(records(forward, fault).isEmpty());
        InjectedAmendment6Support.sameResults(forward, backward);
        assertEquals(forward.plan().warnings(), backward.plan().warnings());
    }

    @Test void addingTheSeventhFaultKeepsEarlierFaultResultsWhenTheSameEnumeratorsCarryThem() throws Exception {
        var f = sevenFaultForm(); var c = settings(700, 6, InjectedAmendment8Support.sixSettings(true)
                + "," + update("2026-03-02", null));
        var six = run(f, c.withProblems(rates(0, SEVEN.subList(0, 6).toArray(String[]::new))));
        var seven = run(f, c.withProblems(rates(0, SEVEN.toArray(String[]::new))));
        for (String fault : SEVEN.subList(0, 6)) {
            assertFalse(records(six, fault).isEmpty()); assertEquals(records(six, fault), records(seven, fault));
        }
        assertFalse(outdated(seven).isEmpty()); onlyVersionMayDiffer(six, seven);
    }

    @ParameterizedTest @ValueSource(ints = {1, 30})
    void oneEnumeratorKeepsTheNormalUpdateAndGetsOnlyTheComparisonWarning(int n) throws Exception {
        var f = simple(NEW); var c = settings(n, 1, update(UPDATE.toString(), null));
        var r = run(f, c.withProblems(rates(0, FAULT)));
        FieldworkTestSupport.sameRun(run(f, c).submissions(), r.submissions());
        normalVersions(r, UPDATE, OLD, NEW); assertTrue(outdated(r).isEmpty());
        assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 1);
    }

    @ParameterizedTest @ValueSource(ints = {18, 20})
    void smallSampleWarningCountsAllInterviewsIncludingThoseBeforeTheUpdate(int n) throws Exception {
        var r = run(simple(NEW), settings(n, 2, update(UPDATE.toString(), null)).withProblems(rates(0, FAULT)));
        assertEquals(n / 2 - 2, outdated(r).size());
        if (n == 20) assertTrue(r.plan().warnings().isEmpty());
        else {
            assertEquals(1, r.plan().warnings().size()); assertWarning(r, soleEnumerator(r, FAULT), 9);
        }
    }

    @Test void assignmentDependsOnSeedAndEnumeratorCountNotAnswersDatesOrSubmissionCount() throws Exception {
        var f = simple(NEW); var c = settings(40, 8, update("2026-03-02", null)).withProblems(rates(0, FAULT));
        String assigned = soleEnumerator(run(f, c), FAULT);
        var other = versioned(NEW, "<different/><extra/>", bind("different", "string", "") + bind("extra", "int", ""),
                input("different") + input("extra"));
        assertEquals(assigned, soleEnumerator(run(other, c), FAULT));
        var more = settings(160, 8, update(UPDATE.toString(), "release-before")).withProblems(rates(0, FAULT));
        assertEquals(assigned, soleEnumerator(run(f, more), FAULT));
    }

    @Test void faultyEnumeratorIsUniformlySelectedAcrossFixedSeeds() throws Exception {
        var f = simple(NEW); var c = settings(8, 8, update("2026-03-02", null)).withProblems(rates(0, FAULT));
        Map<String, Integer> counts = new HashMap<>();
        for (int seed = 1000; seed < 1320; seed++) {
            var r = run(f, c.withSeed(seed)); assertEquals(1, outdated(r).size());
            counts.merge(soleEnumerator(r, FAULT), 1, Integer::sum);
        }
        assertEquals(8, counts.size(), "every enumerator must be observed");
        for (int count : counts.values()) rate(count, 320, 1.0 / 8);
    }
}
