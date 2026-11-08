package ctosim;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment6Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment6TerminationTest {
    @Test void earlyFinalTerminationAllocatesIdsAndCountsExhaustionForEachNewHousehold() throws Exception {
        var plan = new Fieldwork(reasonForm("moved"),
                config6(4, 1, 1, 0, 1, 10, "[\"moved\"]",
                        "\"fields\":{\"answer\":{\"texts\":[\"101\",\"202\"]}}"));
        plan.walkAll();
        var ids = plan.visits().stream().map(Visit::id).toList();
        assertNotEquals(ids.get(0), ids.get(1));
        assertEquals(ids.get(0), ids.get(2));
        assertEquals(ids.get(1), ids.get(3));
        assertTrue(plan.visits().stream().allMatch(v -> v.attempt() == 1 && !v.found()));
        assertEquals(1, plan.warnings().size(), "only household ID exhaustion, no waiting warning");
        warning(plan.warnings(), "answer", 2, 4);
    }

    @ParameterizedTest @ValueSource(ints = {2, 10})
    void aFinalFirstFailureEndsAnUnreachedHouseholdAndFreesItsNextSlot(int max) throws Exception {
        var plan = new Fieldwork(reasonForm("moved"),
                config6(20, 2, 2, 0, 1, max, "[\"moved\"]", ""));
        var rows = plan.walkAll();
        assertEquals(20, rows.size());
        assertEquals(20, households(plan.visits()).size());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(1, plan.visits().get(i).attempt());
            assertFalse(plan.visits().get(i).found());
            assertEquals("moved", value(rows.get(i), "status"));
            assertEquals("", value(rows.get(i), "details"), "final reason still follows form relevance");
        }
        assertTrue(plan.warnings().isEmpty(), "terminated households do not wait");
    }

    @Test void aFinalFailureOnARevisitRemovesTheHouseholdImmediately() throws Exception {
        var plan = new Fieldwork(finalOnlyAfterFirstAttempt(),
                config6(8, 1, 2, 0, 1, 10, "[\"moved\"]", ""));
        var rows = plan.walkAll();
        assertEquals(List.of(1, 1, 2, 2, 1, 1, 2, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(List.of("absent", "absent", "moved", "moved", "absent", "absent", "moved", "moved"), reasons(rows));
        for (List<Integer> indices : households(plan.visits()).values()) {
            assertEquals(2, indices.size());
            Visit first = plan.visits().get(indices.get(0));
            Visit second = plan.visits().get(indices.get(1));
            assertEquals(first.latitude(), second.latitude());
            assertEquals(first.longitude(), second.longitude());
            assertEquals(first.enumerator(), second.enumerator());
        }
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void finalOnTheLastAllowedAttemptStillLeavesNoWaitingHousehold() throws Exception {
        var plan = new Fieldwork(finalOnlyAfterFirstAttempt(),
                config6(2, 1, 1, 0, 1, 2, "[\"moved\"]", ""));
        var rows = plan.walkAll();
        assertEquals(List.of("absent", "moved"), reasons(rows));
        assertEquals(List.of(1, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void singleFinalFailureAtTheEndOfThePlanDoesNotProduceAWaitingWarning() throws Exception {
        var plan = new Fieldwork(reasonForm("moved"),
                config6(1, 1, 1, 0, 1, 10, "[\"moved\"]", ""));
        assertEquals("moved", value(plan.walkAll().get(0), "status"));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void aRevisitedHouseholdStillWaitsAfterAnAllFinalFallbackAtThePlanBoundary() throws Exception {
        var plan = new Fieldwork(reasonForm("moved"),
                config6(1, 1, 1, 1, 0, 2, "[\"moved\"]", ""));
        assertEquals("moved", value(plan.walkAll().get(0), "status"));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 1);
    }

    @Test void anUnofferedFinalReasonDoesNotShortenUnreachedHouseholds() throws Exception {
        var plan = new Fieldwork(reasonForm("absent"),
                config6(12, 1, 1, 0, 1, 3, "[\"moved\"]", ""));
        var rows = plan.walkAll();
        assertEquals(4, households(plan.visits()).size());
        for (List<Integer> indices : households(plan.visits()).values()) {
            assertEquals(3, indices.size());
            assertEquals(List.of(1, 2, 3), indices.stream().map(i -> plan.visits().get(i).attempt()).toList());
        }
        assertTrue(reasons(rows).stream().allMatch("absent"::equals));
        assertTrue(plan.warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"1,1", "1,4", "3,4"})
    void finalFailuresAreRemovedWithoutDroppingOrReorderingOtherWaitingHouseholds(int enumerators, int quota) throws Exception {
        var config = config6(173, enumerators, quota, 0, 1, 4, "[\"moved\"]", "");
        var plan = new Fieldwork(reasonForm("absent", "moved"), config);
        var rows = plan.walkAll();
        checkUnreachedQueue(plan, config, reasons(rows), Set.of("moved"), 4);
        assertTrue(plan.visits().stream().anyMatch(v -> v.attempt() > 1));
        assertTrue(reasons(rows).contains("moved"));
    }

    @Test void waitingWarningCountsOnlyNonfinalFailuresEvenWhenThePlanStopsOnItsFirstDay() throws Exception {
        var config = config6(36, 12, 3, 0, 1, 10, "[\"moved\"]", "");
        var plan = new Fieldwork(reasonForm("absent", "moved"), config);
        var rows = plan.walkAll();
        int waiting = 0;
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(0, day(config, plan.visits().get(i)));
            assertEquals(1, plan.visits().get(i).attempt());
            if (value(rows.get(i), "status").equals("absent")) waiting++;
        }
        assertTrue(waiting > 0 && waiting < 36, "both kinds of failure must be exercised");
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, waiting);
    }

    @Test void unreachedFailureReasonsAreUniformAcrossFinalAndNonfinalChoices() throws Exception {
        var config = config6(1200, 1, 1, 0, 1, 4, "[\"moved\",\"deceased\"]",
                "\"fields\":{\"status\":{\"weights\":{\"absent\":100,\"moved\":0,\"deceased\":0}}}");
        var plan = new Fieldwork(reasonForm("absent", "moved", "deceased"), config);
        var reasons = reasons(plan.walkAll());
        for (String reason : List.of("absent", "moved", "deceased")) {
            int count = (int) reasons.stream().filter(reason::equals).count();
            assertUniformBin(count, 1200, 1.0 / 3, reason);
        }
        checkUnreachedQueue(plan, config, reasons, Set.of("moved", "deceased"), 4);
    }

    @Test void mixedOutcomesUseRestrictionsPerHouseholdRatherThanGlobally() throws Exception {
        var plan = new Fieldwork(reasonForm("absent", "moved"),
                config6(360, 1, 1, .5, .5, 3, "[\"moved\"]", ""));
        var reasons = reasons(plan.walkAll());
        int successful = 0, terminated = 0;
        for (List<Integer> indices : households(plan.visits()).values()) {
            boolean found = indices.stream().anyMatch(i -> plan.visits().get(i).found());
            boolean finalSeen = indices.stream().anyMatch(i -> reasons.get(i).equals("moved"));
            assertFalse(found && finalSeen, "a household later found had an offered nonfinal alternative");
            if (found) successful++;
            if (finalSeen) {
                terminated++;
                assertEquals("moved", reasons.get(indices.get(indices.size() - 1)), "final is its last visit");
            }
        }
        assertTrue(successful > 0);
        assertTrue(terminated > 0);
    }

    /** Independent queue model: all households in these cases have unreached outcomes. */
    private static void checkUnreachedQueue(Fieldwork plan, SimulationConfig config,
            List<String> reasons, Set<String> finals, int max) {
        Map<String, Integer> waiting = new HashMap<>();
        Map<String, Visit> firsts = new HashMap<>();
        List<Visit> visits = plan.visits();
        assertEquals(reasons.size(), visits.size());
        for (int i = 0; i < visits.size(); i++) {
            Visit visit = visits.get(i);
            long today = day(config, visit);
            Integer due = waiting.values().stream()
                    .filter(j -> visits.get(j).enumerator() == visit.enumerator() && day(config, visits.get(j)) < today)
                    .min(Comparator.naturalOrder()).orElse(null);
            assertFalse(visit.found());
            if (due == null) {
                assertEquals(1, visit.attempt(), "no household was due at submission " + (i + 1));
                assertNull(firsts.put(visit.id(), visit), "new household gets a fresh ID");
            } else {
                Visit previous = visits.get(due);
                assertEquals(previous.id(), visit.id(), "earliest waiting household is next");
                assertEquals(previous.attempt() + 1, visit.attempt());
                assertEquals(previous.latitude(), visit.latitude());
                assertEquals(previous.longitude(), visit.longitude());
            }
            if (finals.contains(reasons.get(i)) || visit.attempt() == max) waiting.remove(visit.id());
            else waiting.put(visit.id(), i);
        }
        if (waiting.isEmpty()) assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
        else {
            assertEquals(1, plan.warnings().size());
            warning(plan.warnings(), null, waiting.size());
        }
    }
}
