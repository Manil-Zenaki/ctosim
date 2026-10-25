package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Fieldwork walks temporarily change the JVM time zone")
class InjectedAmendment3InteractionsTest {
    @ParameterizedTest @ValueSource(strings = {"offhours", "overlap"})
    void conflictingNextTargetPreventsEnglobingAndIsNotBypassedForALaterInterview(String other) throws Exception {
        var r = find(timeline(10, 90, 1), settings(3, 1, "").withProblems(mixed(1.0 / 3, other, 1.0 / 3)),
                x -> targets(x, "leftOpen").equals(Set.of(1)) && targets(x, other).equals(Set.of(2)));
        assertEquals("", record(r, 1).relatedKey(), "A conflicting B does not make submission 3 the englobed interview");
        var a = r.submissions().get(0);
        minutesBetween(opening(r, a).plusDays(1), interruption(a, 10).resume(), 0, 60);
    }

    @Test void aReservedTravelSuccessorIsExcludedEvenWhenItCannotInjectALocationProblem() throws Exception {
        var r = run(timeline(4, 90, 1), settings(2, 1, "").withProblems(mixed(.5, "travel", .5)));
        assertEquals(Set.of(1), targets(r, "leftOpen")); assertTrue(records(r, "travel").isEmpty());
        assertEquals("", record(r, 1).relatedKey());
        var a = r.submissions().get(0);
        minutesBetween(opening(r, a).plusDays(1), interruption(a, 4).resume(), 0, 60);
    }

    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void anEnglobedDurationTargetKeepsItsOwnTimingProblemAndAnswerKey(String other) throws Exception {
        var r = find(timeline(10, 0, 2), settings(2, 1, "").withProblems(mixed(.5, other, .5)),
                x -> targets(x, "leftOpen").equals(Set.of(1)) && targets(x, other).equals(Set.of(2)));
        enclosed(r, 1, 2, 10);
        assertEquals(1, records(r, other).size()); var duration = records(r, other).get(0);
        assertEquals("screen", duration.field()); assertEquals(value(r.submissions().get(1), "screen"), duration.value());
        assertEquals(value(r.clean(2), "screen"), duration.cleanValue());
        assertEquals(total(r.submissions().get(1)), seconds(r.submissions().get(1)), "B has no pause of its own");
        assertEquals(List.of("leftOpen", "leftOpen", other), r.plan().problems().stream().map(InjectedProblem::problem).toList());
        for (var p : records(r, "leftOpen")) assertEquals(key(r.submissions().get(1)), p.relatedKey());
    }

    @Test void anEnglobedOutlierTargetRetainsItsChangedAnswer() throws Exception {
        var c = settings(2, 1, "\"fields\":{\"q1\":{\"min\":90,\"max\":110,"
                + "\"distribution\":{\"normal\":{\"mean\":100,\"sd\":10}}}}")
                .withProblems(mixed(.5, "outlier", .5));
        var r = find(timeline(4, 90, 1), c,
                x -> targets(x, "leftOpen").equals(Set.of(1)) && targets(x, "outlier").equals(Set.of(2)));
        enclosed(r, 1, 2, 4);
        assertEquals(1, records(r, "outlier").size()); var p = records(r, "outlier").get(0);
        assertEquals(p.value(), value(r.target(p), p.field()));
        assertNotEquals(p.cleanValue(), p.value());
        assertTrue(r.target(p).unsatisfiedConstraints().isEmpty());
    }

    @Test void aMovingOutlierSkipsAReservedLeftOpenTarget() throws Exception {
        var c = settings(3, 3, "\"id\":\"id\",\"fields\":{\"amount\":{\"min\":90,\"max\":110,"
                + "\"distribution\":{\"normal\":{\"mean\":100,\"sd\":10}}}}")
                .withProblems(mixed(1.0 / 3, "outlier", 1.0 / 3));
        String nodes = "<id/><amount/>";
        String idBind = bind("id", "int", "ct:minimum-seconds=\"90\"");
        String body = input("id") + input("amount");
        var control = find(fixture(nodes, idBind + bind("amount", "int", ""), body), c,
                x -> targets(x, "outlier").equals(Set.of(1)) && targets(x, "leftOpen").equals(Set.of(2)));
        String firstId = value(control.submissions().get(0), "id");
        var restricted = fixture(nodes, idBind + bind("amount", "int", "relevant=\"/data/id != " + firstId + "\""), body);
        var r = run(restricted, control.config());
        assertEquals(Set.of(2), targets(r, "leftOpen"));
        assertEquals(Set.of(3), targets(r, "outlier"), "The eligible amount on submission 2 belongs to a reserved target");
        assertTrue(r.plan().warnings().isEmpty(), r.plan().warnings().toString());
    }

    @Test void anEnglobedReinterviewKeepsItsOriginalIdAndDuplicateRecord() throws Exception {
        var c = settings(3, 1, "\"id\":\"q1\"").withProblems(mixed(1.0 / 3, "reinterview", 1.0 / 3));
        var r = find(timeline(4, 90, 1), c,
                x -> targets(x, "leftOpen").equals(Set.of(2)) && targets(x, "reinterview").equals(Set.of(3)));
        enclosed(r, 2, 3, 4);
        var duplicate = records(r, "reinterview").get(0);
        assertEquals(key(r.submissions().get(0)), duplicate.relatedKey());
        assertEquals(value(r.submissions().get(0), "q1"), value(r.submissions().get(2), "q1"));
        assertNotEquals(value(r.submissions().get(1), "q1"), value(r.submissions().get(2), "q1"));
    }

    @Test void resentCopiesAreSkippedWhenChoosingTheNextRealInterview() throws Exception {
        var c = settings(4, 1, "\"id\":\"q1\"").withProblems(mixed(.25, "resent", .25));
        var r = find(timeline(4, 90, 1), c,
                x -> targets(x, "leftOpen").equals(Set.of(2)) && targets(x, "resent").equals(Set.of(3)));
        enclosed(r, 2, 4, 4);
        assertEquals(key(r.submissions().get(0)), records(r, "resent").get(0).relatedKey());
        assertEquals(r.submissions().get(0).start(), r.submissions().get(2).start());
        assertEquals(r.submissions().get(0).end(), r.submissions().get(2).end());
        assertEquals(1, r.plan().problems().stream().filter(p -> p.submission() == 3).count());
    }

    @Test void onlyResentCopiesRemainingMeansThereIsNoEnglobedInterview() throws Exception {
        var r = find(timeline(4, 90, 1), settings(3, 1, "").withProblems(mixed(1.0 / 3, "resent", 1.0 / 3)),
                x -> targets(x, "leftOpen").equals(Set.of(2)) && targets(x, "resent").equals(Set.of(3)));
        assertEquals("", record(r, 2).relatedKey());
        var a = r.submissions().get(1);
        minutesBetween(opening(r, a).plusDays(1), interruption(a, 4).resume(), 0, 60);
    }

    @Test void faultyEnumeratorStillAppliesTooFastAndItsRecordsFollowLeftOpen() throws Exception {
        var form = timeline(10, 0, 1); var c = settings(20, 2, "\"interviewsPerDay\":10");
        var baseline = run(form, c.withProblems(mixed(0, "", 0, "tooFast")));
        var r = run(form, c.withProblems(mixed(1, "", 0, "tooFast")));
        assertEquals(20, targets(r, "leftOpen").size());
        assertEquals(targets(baseline, "tooFast"), targets(r, "tooFast"));
        assertEquals(10, records(r, "tooFast").size());
        for (var s : r.submissions()) {
            assertEquals(total(baseline.submissions().get(s.number() - 1)), total(s));
            var pause = interruption(s, 10);
            assertTrue(pause.screenAtPause() >= .2 * total(s));
            assertTrue(screen(s, pause.question() - 1) < .8 * total(s));
            var ordered = r.plan().problems().stream().filter(p -> p.submission() == s.number())
                    .map(InjectedProblem::problem).toList();
            assertEquals(targets(r, "tooFast").contains(s.number())
                    ? List.of("leftOpen", "tooFast") : List.of("leftOpen"), ordered);
        }
    }

    @Test void anEnglobedInterviewOfAFaultyEnumeratorKeepsItsFault() throws Exception {
        var form = timeline(10, 0, 1);
        var r = find(form, settings(4, 2, "").withProblems(mixed(.25, "", 0, "tooFast")),
                x -> targets(x, "leftOpen").equals(Set.of(1)) && targets(x, "tooFast").contains(3));
        enclosed(r, 1, 3, 10);
        var control = run(form, r.config().withProblems(mixed(0, "", 0, "tooFast")));
        var inner = r.submissions().get(2);
        assertEquals(total(control.submissions().get(2)), total(inner));
        var fault = records(r, "tooFast").stream().filter(p -> p.submission() == 3).findFirst().orElseThrow();
        assertEquals(value(inner, "screen"), fault.value());
        assertEquals(value(r.clean(3), "screen"), fault.cleanValue());
        assertEquals(total(inner), seconds(inner));
    }
}
