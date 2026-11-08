package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment10Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class InjectedAmendment10DuplicatesTest {
    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void duplicateOriginalsAreFoundVisitsEvenWhenFailedVisitsAreEarlierAndHaveIds(String kind) throws Exception {
        var r = run(tracking(), settings10(180, 3, 3, .6, .2, 3, POINT).withProblems(problem(kind, .15)));
        assertFalse(records(r, kind).isEmpty());
        assertTrue(r.plan().visits().stream().anyMatch(v -> !v.found()));
        Set<String> used = new HashSet<>();
        Set<Integer> selected = targets(r, kind);
        for (var p : records(r, kind)) {
            Submission original = r.related(p);
            Visit ov = r.visit(original.number());
            assertTrue(ov.found(), "a failed visit must never be a duplicate original");
            assertTrue(original.number() < p.submission());
            assertFalse(selected.contains(original.number()));
            assertTrue(used.add(p.relatedKey()), "original cannot be reused by another duplicate");
            assertEquals("yes", value(original, "status"));
            assertEquals(value(original, "id"), value(r.target(p), "id"));
            assertEquals(value(original, "id"), p.value());
            assertEquals("id", p.field());
            assertEquals(key(r.target(p)), p.key());
            assertEquals("duplicate", p.family()); assertEquals("ipacheckids", p.check());
            assertTrue(p.deterministic());
        }
        if (kind.equals("reinterview")) successfulFirstTargets(r, kind);
    }

    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void anEarlierFailedVisitWithAnIdCannotSatisfyTheOnlyDuplicateTarget(String kind) throws Exception {
        var r = run(tracking(), settings10(2, 1, 1, 1, 0, 2, POINT).withProblems(problem(kind, .5)));
        assertFalse(r.visit(1).found());
        assertFalse(value(r.submissions().get(0), "id").isEmpty());
        assertTrue(records(r, kind).isEmpty(), "failed first visit is not an eligible original");
        assertWarning(r, kind, 2);
    }

    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void noDuplicateIsInventedWhenEveryPotentialOriginalIsUnreached(String kind) throws Exception {
        var r = run(tracking(), settings10(32, 2, 2, 0, 1, 3, POINT).withProblems(problem(kind, .25)));
        assertTrue(records(r, kind).isEmpty());
        assertTrue(r.plan().warnings().stream().anyMatch(w -> w.contains(kind)), "missing originals are diagnosed");
    }

    @Test void aSuccessfulRevisitCanBeTheOnlyOriginalOfAReinterview() throws Exception {
        var form = tracking(); int observed = 0;
        // For N=3, the only possible found original is visit 2, a successful revisit.
        // A target at 2 falls back; a target at 3 must be able to use 2.
        for (int seed = 1; seed <= 24; seed++) {
            var r = run(form, settings10(3, 1, 1, 1, 0, 2, POINT)
                    .withSeed(seed).withProblems(problem("reinterview", 1.0 / 3)));
            for (var p : records(r, "reinterview")) {
                observed++;
                assertEquals(3, p.submission()); assertEquals(2, r.related(p).number());
                assertEquals(2, r.visit(2).attempt()); assertTrue(r.visit(2).found());
                assertEquals(1, r.visit(3).attempt()); assertTrue(r.visit(3).found());
                assertEquals("1", value(r.target(p), "attempt"), "reinterview has its own first-visit number");
                assertEquals("2", value(r.related(p), "attempt"));
                assertEquals(value(r.related(p), "id"), value(r.target(p), "id"));
            }
        }
        assertTrue(observed > 0, "found revisits must not be excluded from original eligibility");
    }

    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void duplicateTargetsWithFirstVisitOriginalsStaySuccessfulFirstVisits(String kind) throws Exception {
        var r = run(tracking(), settings10(120, 3, 4, 0, .5, 2, POINT).withProblems(problem(kind, .15)));
        successfulFirstTargets(r, kind);
        for (var p : records(r, kind)) assertEquals(1, r.visit(r.related(p).number()).attempt());
    }

    @ParameterizedTest @CsvSource({"false,1,0,2", "true,1,0,2", "false,0,1,10", "true,0,1,10"})
    void legitimateFailedVisitsAndRepeatedIdsHaveNoAnswerKeyRecords(
            boolean configuredProblems, double revisit, double unreached, int max) throws Exception {
        var c = settings10(40, 2, 2, revisit, unreached, max, POINT);
        if (configuredProblems) c = c.withProblems(problem("short", 0));
        var r = run(tracking(), c);
        assertTrue(r.plan().visits().stream().anyMatch(v -> !v.found()));
        assertTrue(r.plan().visits().stream().anyMatch(v -> v.attempt() > 1));
        assertTrue(r.plan().visits().stream().map(Visit::id).distinct().count() < 40);
        assertTrue(r.plan().problems().isEmpty());
        assertEquals(AnswerKey.csv(List.of()), AnswerKey.csv(r.plan().problems()), "legitimate repeated ids leave header-only CSV");
    }

    @Test void mixedRunListsOnlyInjectedProblemsAndNeverLegitimateFailedVisitsOrRevisits() throws Exception {
        var r = run(tracking(), settings10(80, 2, 2, 1, 0, 2, POINT).withProblems(problem("short", .2)));
        var selected = targets(r, "short"); assertEquals(16, selected.size());
        int failures = 0, revisits = 0;
        for (int n = 1; n <= 80; n++) {
            Visit v = r.visit(n);
            if (!v.found()) failures++;
            if (v.attempt() > 1) revisits++;
            if (!v.found() || v.attempt() > 1) assertFalse(selected.contains(n));
        }
        assertTrue(failures > 0); assertTrue(revisits > 0);
        assertEquals(16, r.plan().problems().size(), "one elapsed-duration record per injected short interview");
        assertEquals(17, AnswerKey.csv(r.plan().problems()).lines().count());
        assertTrue(r.plan().problems().stream().allMatch(p -> p.problem().equals("short")));
    }
}
