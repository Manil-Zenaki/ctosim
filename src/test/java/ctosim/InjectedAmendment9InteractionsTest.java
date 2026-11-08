package ctosim;

import java.time.LocalDate;
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
class InjectedAmendment9InteractionsTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resentCopiesKeepTheirOriginalVersionAndHaveOnlyTheirResentRecord(boolean fault) throws Exception {
        var c = settings(120, 2, "\"interviewsPerDay\":1," + update("2026-03-10", null))
                .withProblems(new ProblemRates(.25, 0, 0, 0, 0, 0, 0, 0, fault ? List.of(FAULT) : List.of()));
        var r = run(simple(NEW), c); var copies = records(r, "resent");
        assertFalse(copies.isEmpty());
        int oldCopies = 0, faultyOriginals = 0;
        for (var p : copies) {
            var target = r.target(p); var original = r.related(p);
            assertEquals(version(original), version(target));
            assertEquals(original.start(), target.start());
            assertEquals(List.of(p), r.plan().problems().stream().filter(q -> q.submission() == p.submission()).toList());
            if (version(original).equals(OLD)) oldCopies++;
            if (targets(r, FAULT).contains(original.number())) faultyOriginals++;
        }
        assertTrue(oldCopies > 0, "exercise copying an old version");
        if (fault) assertTrue(faultyOriginals > 0, "exercise copying an outdated original without a second fault record");
        else assertTrue(outdated(r).isEmpty());
        metadata(r, OLD, NEW);
    }

    @Test void aReinterviewUsesItsOwnDateRatherThanCopyingItsOriginalsVersion() throws Exception {
        var f = simple(NEW);
        var c = InjectedProblemsTestSupport.config(80, 2, "\"id\":\"answer\"")
                .withProblems(only("reinterview", .25));
        var baseline = run(f, c);
        assertFalse(records(baseline, "reinterview").isEmpty());
        InjectedProblem pair = null;
        for (var p : records(baseline, "reinterview")) {
            if (baseline.related(p).start().toLocalDate().isBefore(baseline.target(p).start().toLocalDate())) {
                pair = p; break;
            }
        }
        assertNotNull(pair, "fixture must contain interviews on different dates");
        LocalDate date = baseline.target(pair).start().toLocalDate();
        var updated = run(f, InjectedProblemsTestSupport.config(80, 2, "\"id\":\"answer\"," + update(date.toString(), null))
                .withProblems(only("reinterview", .25)));
        assertEquals(records(baseline, "reinterview"), records(updated, "reinterview"));
        normalVersions(updated, date, OLD, NEW);
        assertEquals(OLD, version(updated.related(pair)));
        assertEquals(NEW, version(updated.target(pair)));
        assertTrue(outdated(updated).isEmpty());
    }

    @Test void reinterviewsOfTheFaultyEnumeratorCanAlsoCarryAnOutdatedVersion() throws Exception {
        var r = run(simple(NEW), InjectedProblemsTestSupport.config(120, 2,
                "\"id\":\"answer\"," + update("2026-03-02", null))
                .withProblems(new ProblemRates(0, .3, 0, 0, 0, 0, 0, 0, List.of(FAULT))));
        String faulty = soleEnumerator(r, FAULT); int shared = 0;
        for (var p : records(r, "reinterview")) {
            boolean assigned = value(r.target(p), "username").equals(faulty);
            assertEquals(assigned, targets(r, FAULT).contains(p.submission()));
            assertEquals(assigned ? OLD : NEW, version(r.target(p)));
            if (assigned) {
                shared++;
                assertEquals(List.of("reinterview", FAULT), r.plan().problems().stream()
                        .filter(q -> q.submission() == p.submission()).map(InjectedProblem::problem).toList());
            }
        }
        assertTrue(shared > 0, "reinterview copying does not protect the version from its enumerator's fault");
    }

    @Test void durationProblemsCanCoexistWithTheVersionFaultWithoutChangingItsCleanValue() throws Exception {
        var f = simple(NEW); var c = settings(100, 2, update("2026-03-02", null));
        var clean = run(f, c.withProblems(new ProblemRates(0, 0, .3, 0, 0, 0, 0, 0, List.of())));
        var faulty = run(f, c.withProblems(new ProblemRates(0, 0, .3, 0, 0, 0, 0, 0, List.of(FAULT))));
        assertEquals(records(clean, "short"), records(faulty, "short"));
        onlyVersionMayDiffer(clean, faulty); metadata(faulty, OLD, NEW);
        Set<Integer> both = new HashSet<>(targets(faulty, "short")); both.retainAll(targets(faulty, FAULT));
        assertFalse(both.isEmpty());
        for (int n : both) assertEquals(List.of("short", FAULT), faulty.plan().problems().stream()
                .filter(p -> p.submission() == n).map(InjectedProblem::problem).toList());
    }
}
