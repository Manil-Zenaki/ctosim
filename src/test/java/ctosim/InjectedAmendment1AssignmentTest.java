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
class InjectedAmendment1AssignmentTest {
    @ParameterizedTest @ValueSource(ints = {2, 3, 4, 5, 6, 7})
    void faultsUseAtMostHalfTheEnumeratorsAndWrapInCanonicalOrder(int e) throws Exception {
        var r = run(allFaults(), settings(e * 100, e, allFaultSettings()).withProblems(rates(0, "dontKnow", "skipModules", "tooFast")));
        int m = Math.min(3, e / 2);
        String fast = soleEnumerator(r, "tooFast"), skip = soleEnumerator(r, "skipModules"), dk = soleEnumerator(r, "dontKnow");
        assertEquals(m, new HashSet<>(List.of(fast, skip, dk)).size());
        if (m == 1) { assertEquals(fast, skip); assertEquals(skip, dk); }
        if (m == 2) { assertNotEquals(fast, skip); assertEquals(fast, dk); }
        assertEquals(100, targets(r, "tooFast").size(), "the fault affects every assigned interview");
        if (m < 3) {
            assertEquals(1, r.plan().warnings().size());
            assertTrue(r.plan().warnings().get(0).matches("(?s).*\\b6\\b.*"), "warning gives twice the fault count");
        } else assertTrue(r.plan().warnings().isEmpty());
        newMetadata(r);
    }
    @Test void oneEnumeratorCannotReceiveAnyFault() throws Exception {
        var r = run(allFaults(), settings(30, 1, allFaultSettings()).withProblems(rates(0, "tooFast", "skipModules", "dontKnow")));
        assertTrue(r.plan().problems().isEmpty()); assertFalse(r.plan().warnings().isEmpty());
        for (var s : r.submissions()) ordinary(r, s.number());
        // The spec is ambiguous about also emitting the cumulation warning when m=0; do not fix a count.
        assertTrue(r.plan().warnings().stream().anyMatch(w -> w.toLowerCase(Locale.ROOT).contains("enumerator")));
    }
    @Test void noFaultsWithOneEnumeratorProduceNoFaultWarning() throws Exception {
        var r = run(singleTimed(0), settings(1, 1, "").withProblems(rates(0)));
        assertTrue(r.plan().problems().isEmpty()); assertTrue(r.plan().warnings().isEmpty()); ordinary(r, 1);
    }
    @ParameterizedTest @ValueSource(ints = {2, 4})
    void aSubsetOfFaultsUsesOnlyItsOwnMembersAndItsOwnCumulationThreshold(int e) throws Exception {
        var r = run(allFaults(), settings(e * 100, e, allFaultSettings()).withProblems(rates(0, "dontKnow", "skipModules")));
        assertTrue(records(r, "tooFast").isEmpty());
        String skip = soleEnumerator(r, "skipModules"), dk = soleEnumerator(r, "dontKnow");
        if (e == 2) {
            assertEquals(skip, dk); assertEquals(1, r.plan().warnings().size());
            assertTrue(r.plan().warnings().get(0).matches("(?s).*\\b4\\b.*"));
        } else { assertNotEquals(skip, dk); assertTrue(r.plan().warnings().isEmpty()); }
    }
    @Test void anAssignedEnumeratorWithZeroSubmissionsStillGetsASmallSampleWarning() throws Exception {
        boolean observed = false; var f = singleTimed(0);
        for (int seed = 1; seed <= 20; seed++) {
            var c = settings(4, 4, "").withSeed(seed).withProblems(rates(0, "tooFast"));
            var full = run(f, c); String assigned = soleEnumerator(full, "tooFast");
            var small = run(f, c.withSubmissions(1));
            if (!value(small.submissions().get(0), "username").equals(assigned)) {
                observed = true; assertTrue(small.plan().problems().isEmpty());
                assertWarning(small, assigned, 0); break;
            }
        }
        assertTrue(observed, "the fixed seed ensemble must exercise an unused faulty enumerator");
    }
    @ParameterizedTest @ValueSource(ints = {1, 9, 10})
    void smallSampleWarningNamesEachFaultyEnumeratorAndItsExactCount(int perEnumerator) throws Exception {
        var r = run(singleTimed(0), settings(perEnumerator * 2, 2, "").withProblems(rates(0, "tooFast")));
        String who = soleEnumerator(r, "tooFast");
        assertEquals(perEnumerator, interviews(r, who).size());
        if (perEnumerator < 10) {
            assertEquals(1, r.plan().warnings().size());
            assertWarning(r, who, perEnumerator);
        } else assertTrue(r.plan().warnings().isEmpty());
    }
    @Test void assignmentDependsOnlyOnSeedAndEnumeratorCountNotAnswersOrSubmissionCount() throws Exception {
        var a = run(singleTimed(0), settings(60, 6, "").withProblems(rates(0, "tooFast")));
        var b = run(clockSteps(5, 90), settings(120, 6, "").withProblems(rates(0, "tooFast")));
        assertEquals(enumerators(a, "tooFast"), enumerators(b, "tooFast"));
    }
    @Test void orderingTheConfiguredFaultListDifferentlyDoesNotChangeAssignmentsOrResults() throws Exception {
        var c = settings(160, 4, allFaultSettings()); var f = allFaults();
        var a = run(f, c.withProblems(rates(0, "tooFast", "skipModules", "dontKnow")));
        var b = run(f, c.withProblems(rates(0, "dontKnow", "tooFast", "skipModules")));
        assertEquals(a.plan().problems(), b.plan().problems());
        assertEquals(a.plan().warnings(), b.plan().warnings());
        for (int i = 0; i < a.submissions().size(); i++) assertSameSubmission(a.submissions().get(i), b.submissions().get(i));
    }
    @Test void enumeratorSelectionIsUniformAcrossFixedSeeds() throws Exception {
        var f = singleTimed(0); Map<String, Integer> frequencies = new HashMap<>();
        for (int seed = 1; seed <= 240; seed++) {
            var r = run(f, settings(4, 4, "").withSeed(seed).withProblems(rates(0, "tooFast")));
            frequencies.merge(soleEnumerator(r, "tooFast"), 1, Integer::sum);
        }
        assertEquals(4, frequencies.size()); frequencies.values().forEach(n -> rate(n, 240, .25));
    }
    @Test void replayReplacesRecordsAndIsIndependentOfLocaleAndTimeZone() throws Exception {
        Locale oldLocale = Locale.getDefault(); TimeZone oldZone = TimeZone.getDefault();
        try {
            var f = allFaults(); var c = settings(80, 2, allFaultSettings()).withProblems(rates(0, "tooFast", "skipModules", "dontKnow"));
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var a = run(f, c); var expected = List.copyOf(a.plan().problems()); var warnings = List.copyOf(a.plan().warnings());
            Locale.setDefault(Locale.FRANCE); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kathmandu"));
            var next = a.plan().walkAll(); var b = run(allFaults(), c);
            assertEquals(expected, a.plan().problems()); assertEquals(expected, b.plan().problems());
            assertEquals(warnings, b.plan().warnings()); assertEquals(a.plan().visits(), b.plan().visits());
            for (int i = 0; i < next.size(); i++) {
                assertSameSubmission(a.submissions().get(i), next.get(i));
                assertSameSubmission(next.get(i), b.submissions().get(i));
                assertEquals(next.get(i).submitted(), b.submissions().get(i).submitted());
            }
            assertThrows(UnsupportedOperationException.class, () -> a.plan().problems().clear());
        } finally { Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone); }
    }
}
