package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Fieldwork walks temporarily change the JVM time zone")
class InjectedAmendment3AllocationTest {
    @ParameterizedTest
    @CsvSource({"1,1,1", "10,0,0", "10,0.000001,1", "10,0.249,2", "10,0.25,3", "10,0.251,3", "10,1,10"})
    void countUsesHalfUpRoundingAndAtLeastOneForEveryPositiveRate(int n, double rate, int expected) throws Exception {
        var r = run(timeline(2, 90, 1), settings(n, n, "").withProblems(left(rate)));
        assertEquals(expected, targets(r, "leftOpen").size());
        assertEquals(expected, r.plan().problems().size());
        assertTrue(r.plan().warnings().isEmpty(), r.plan().warnings().toString());
    }

    @Test void everyEnumeratorsFirstAndOnlyInterviewMayBeLeftOpen() throws Exception {
        var r = run(timeline(2, 90, 1), settings(4, 7, "").withProblems(left(1)));
        assertEquals(Set.of(1, 2, 3, 4), targets(r, "leftOpen"));
        for (var p : records(r, "leftOpen")) {
            assertEquals("", p.relatedKey());
            interruption(r.target(p), 2);
        }
    }

    @Test void outlierAllocationPrecedesLeftOpenEvenThoughItsConstructorComponentComesLater() throws Exception {
        var form = timeline(2, 90, 1);
        var c = settings(8, 8, "\"fields\":{\"q1\":{\"distribution\":{\"normal\":{\"mean\":100,\"sd\":10}},\"min\":90,\"max\":110}}");
        var control = run(form, c.withProblems(mixed(0, "outlier", .5)));
        var r = run(form, c.withProblems(mixed(1, "outlier", .5)));
        assertEquals(4, targets(r, "outlier").size());
        assertEquals(targets(control, "outlier"), targets(r, "outlier"));
        Set<Integer> remaining = new TreeSet<>(Set.of(1, 2, 3, 4, 5, 6, 7, 8));
        remaining.removeAll(targets(r, "outlier"));
        assertEquals(remaining, targets(r, "leftOpen"));
        FieldworkTestSupport.warning(r.plan().warnings(), "leftOpen", 8, 4);
    }

    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void earlierDurationTargetsReserveTheirSlotsAndLeaveOnlyTheRemainder(String problem) throws Exception {
        var form = timeline(2, 90, 1); var c = settings(6, 6, "");
        var earlier = run(form, c.withProblems(mixed(0, problem, .5)));
        var r = run(form, c.withProblems(mixed(1, problem, .5)));
        assertEquals(targets(earlier, problem), targets(r, problem));
        assertEquals(3, targets(r, problem).size()); assertEquals(3, targets(r, "leftOpen").size());
        assertTrue(Collections.disjoint(targets(r, problem), targets(r, "leftOpen")));
        FieldworkTestSupport.warning(r.plan().warnings(), "leftOpen", 6, 3);
    }

    @Test void completelyExhaustedAllocationReportsRequestedAndZeroInjected() throws Exception {
        var r = run(timeline(2, 90, 1), settings(3, 3, "").withProblems(mixed(1, "short", 1)));
        assertEquals(Set.of(1, 2, 3), targets(r, "short")); assertTrue(records(r, "leftOpen").isEmpty());
        assertEquals(1, r.plan().warnings().size());
        FieldworkTestSupport.warning(r.plan().warnings(), "leftOpen", 3, 0);
    }

    @Test void unsuccessfulEarlierTargetsRemainReservedForAllocation() throws Exception {
        // With no location question, travel falls back; its reserved slots still cannot be reused.
        var r = run(timeline(2, 90, 1), settings(2, 1, "").withProblems(mixed(1, "travel", .5)));
        assertTrue(records(r, "travel").isEmpty()); assertEquals(Set.of(1), targets(r, "leftOpen"));
        FieldworkTestSupport.warning(r.plan().warnings(), "leftOpen", 2, 1);
        assertTrue(r.plan().warnings().get(0).contains("travel"));
        assertTrue(r.plan().warnings().get(1).contains("leftOpen"));
    }

    @Test void aLeftOpenTargetCannotBeUsedAsADuplicatesOriginal() throws Exception {
        // Resent must reserve 2; leftOpen then reserves 1. No original remains for 2.
        var r = run(timeline(2, 90, 1), settings(2, 1, "").withProblems(mixed(.5, "resent", .5)));
        assertEquals(Set.of(1), targets(r, "leftOpen")); assertTrue(records(r, "resent").isEmpty());
        FieldworkTestSupport.warning(r.plan().warnings(), "resent", 2);
    }

    @Test void missingOrExplicitlyDisabledProblemsLeaveTheOrdinaryPlanUnchanged() throws Exception {
        var form = timeline(3, 90, 1); var c = settings(6, 2, "");
        var ordinary = run(form, c); var disabled = run(form, c.withProblems(left(0)));
        FieldworkTestSupport.sameRun(ordinary.submissions(), disabled.submissions());
        assertEquals(ordinary.plan().visits(), disabled.plan().visits());
        assertEquals(ordinary.plan().warnings(), disabled.plan().warnings());
        assertTrue(disabled.plan().problems().isEmpty());
    }

    @Test void recordsUseEveryStartFieldInFormOrderAndActualSubmissionMetadata() throws Exception {
        var r = run(timeline(2, 90, 2), settings(5, 5, "").withProblems(left(1)));
        assertEquals(10, r.plan().problems().size());
        int previous = 0;
        for (int n : targets(r, "leftOpen")) {
            var group = records(r, "leftOpen").stream().filter(p -> p.submission() == n).toList();
            assertEquals(List.of("start_z", "start_a"), group.stream().map(InjectedProblem::field).toList());
            for (var p : group) {
                assertTrue(p.submission() >= previous); previous = p.submission();
                assertEquals("schedule", p.family()); assertEquals("custom", p.check()); assertTrue(p.deterministic());
                assertEquals(key(r.target(p)), p.key()); assertEquals(value(r.target(p), "username"), p.enumerator());
                assertEquals(r.target(p).start().format(WIDE_TIME), p.value());
                assertEquals("", p.cleanValue()); assertEquals("", p.relatedKey());
            }
        }
    }

    @Test void absentStartFieldProducesOneIsoRecordWithSeconds() throws Exception {
        var r = run(timeline(2, 90, 0), settings(1, 1, "").withProblems(left(1)));
        assertEquals(1, r.plan().problems().size()); var p = record(r, 1);
        assertEquals("", p.field()); assertEquals(r.target(p).start().format(FALLBACK_TIME), p.value());
        assertEquals("", p.cleanValue()); assertEquals("", p.relatedKey());
    }
}
