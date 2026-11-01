package ctosim;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment6Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment6AssignmentTest {
    private static final List<String> FIVE = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse");

    @ParameterizedTest @ValueSource(ints = {2, 4, 6, 8, 10, 11})
    void fifthFaultUsesCanonicalRoundRobinAssignmentAndLeavesAtLeastHalfClean(int enumeratorCount) throws Exception {
        var run = run(fiveFaultForm(), settings(enumeratorCount * 100, enumeratorCount, fiveFaultSettings())
                .withProblems(rates(0, "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        int m = Math.min(5, enumeratorCount / 2);
        var assigned = FIVE.stream().map(fault -> soleEnumerator(run, fault)).toList();
        assertEquals(m, new HashSet<>(assigned).size());
        for (int k = 0; k < FIVE.size(); k++) assertEquals(assigned.get(k % m), assigned.get(k));
        assertTrue(enumeratorCount - new HashSet<>(assigned).size() >= (enumeratorCount + 1) / 2);
        if (m < 5) {
            assertEquals(1, run.plan().warnings().size());
            assertWarning(run, "", 10);
        } else assertTrue(run.plan().warnings().isEmpty());
        for (var submission : run.submissions()) {
            var ps = run.plan().problems().stream().filter(p -> p.submission() == submission.number()).toList();
            int last = -1;
            for (var p : ps) {
                int current = FIVE.indexOf(p.problem());
                assertTrue(current >= last, "refuse follows all four earlier faults in each submission's records");
                last = current;
            }
        }
        metadata6(run);
    }

    @Test void faultListOrderDoesNotChangeTheCanonicalAssignmentsOrDraws() throws Exception {
        var form = fiveFaultForm();
        var config = settings(800, 8, fiveFaultSettings());
        var forward = run(form, config.withProblems(rates(0, FIVE.toArray(String[]::new))));
        var reverse = run(form, config.withProblems(rates(0, "refuse", "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        for (String fault : FIVE) assertFalse(records(forward, fault).isEmpty());
        sameResults(forward, reverse);
        assertEquals(forward.plan().warnings(), reverse.plan().warnings());
    }

    @ParameterizedTest @ValueSource(ints = {4, 8, 10})
    void inactiveFifthFaultLeavesAllFourEarlierFaultsAndTheirWarningsUnchanged(int enumerators) throws Exception {
        var form = fiveFaultForm();
        String extra = fiveFaultSettings().replace("," + codes("REF"), "");
        var config = settings(enumerators * 100, enumerators, extra);
        var old = run(form, config.withProblems(rates(0, "tooFast", "skipModules", "dontKnow", "fakeRefusal")));
        var disabled = run(form, config.withProblems(rates(0, FIVE.toArray(String[]::new))));
        for (String fault : FIVE.subList(0, 4)) assertFalse(records(old, fault).isEmpty());
        sameResults(old, disabled);
        assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains("refuse")).count());
        assertEquals(old.plan().warnings(), disabled.plan().warnings().stream().filter(w -> !w.contains("refuse")).toList());
    }

    @Test void merelySupplyingRefusalCodesLeavesAllFourEarlierFaultsUnchanged() throws Exception {
        var form = fiveFaultForm();
        var config = settings(800, 8, fiveFaultSettings());
        String[] four = {"tooFast", "skipModules", "dontKnow", "fakeRefusal"};
        var withCodes = run(form, config.withProblems(rates(0, four)));
        var withoutCodes = run(form, settings(800, 8, fiveFaultSettings().replace("," + codes("REF"), ""))
                .withProblems(rates(0, four)));
        sameResults(withoutCodes, withCodes);
        assertEquals(withoutCodes.plan().warnings(), withCodes.plan().warnings());
    }

    @Test void disabledDontKnowDoesNotStealAnEnumeratorFromActiveRefuse() throws Exception {
        var form = selection(false, "ok", "REF");
        var config = settings(400, 4, codes("REF") + "," + fixed("answer", "ok"));
        var alone = run(form, config.withProblems(rates(0, "refuse")));
        var combined = run(form, config.withProblems(rates(0, "dontKnow", "refuse")));
        assertFalse(records(alone, "refuse").isEmpty());
        sameResults(alone, combined);
        assertEquals(1, combined.plan().warnings().size());
        assertTrue(combined.plan().warnings().get(0).contains("dontKnow"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 4})
    void twoMissingCodeListsGiveExactlyOneWarningEachAndAllocateNoFaults(int enumerators) throws Exception {
        var form = selection(false, "ok", "97");
        var config = settings(40, enumerators, "\"specialValues\":[\"97\"]," + fixed("answer", "ok"));
        var disabled = run(form, config.withProblems(rates(0, "refuse", "dontKnow")));
        sameResults(run(form, config), disabled);
        assertEquals(2, disabled.plan().warnings().size());
        for (String fault : List.of("dontKnow", "refuse")) {
            assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains(fault)).count());
        }
    }

    @Test void oneEnumeratorCannotCarryAnActiveRefusalFault() throws Exception {
        var form = selection(false, "ok", "REF");
        var config = settings(40, 1, codes("REF") + "," + fixed("answer", "ok"));
        var actual = run(form, config.withProblems(rates(0, "refuse")));
        sameResults(run(form, config), actual);
        assertEquals(1, actual.plan().warnings().size(), "no cumulation or small-sample warning when m is zero");
        assertWarning(actual, "", 1);
    }

    @ParameterizedTest @ValueSource(ints = {18, 20})
    void smallSampleWarningUsesTheRefusalEnumeratorsInterviewCountNotReplacementCount(int submissions) throws Exception {
        var run = faulty(selection(false, "ok", "REF"), submissions, codes("REF") + "," + fixed("answer", "ok"));
        if (submissions == 20) assertTrue(run.plan().warnings().isEmpty(), "ten interviews is sufficient");
        else {
            assertEquals(1, run.plan().warnings().size());
            assertWarning(run, "", 9);
            // Identify assignment on a larger run with unchanged seed and E.
            var large = faulty(selection(false, "ok", "REF"), 400, codes("REF") + "," + fixed("answer", "ok"));
            assertTrue(run.plan().warnings().get(0).contains(soleEnumerator(large, "refuse")));
        }
    }

    @Test void refusalAssignmentIsUniformOverEnumeratorsAcrossFixedSeeds() throws Exception {
        var form = selection(false, "ok", "REF");
        var config = settings(240, 4, codes("REF") + "," + fixed("answer", "ok"))
                .withProblems(rates(0, "refuse"));
        var counts = new java.util.HashMap<String, Integer>();
        for (int seed = 100; seed < 300; seed++) {
            var run = run(form, config.withSeed(seed));
            counts.merge(soleEnumerator(run, "refuse"), 1, Integer::sum);
        }
        assertEquals(4, counts.size());
        counts.values().forEach(count -> rate(count, 200, .25));
    }
}
