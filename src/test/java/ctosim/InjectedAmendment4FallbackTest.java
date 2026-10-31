package ctosim;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form walks share JVM state")
class InjectedAmendment4FallbackTest extends InjectedAmendment4Support {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anUnlocatedPreviousVillageFallsBackEvenWhenGpsTravelWouldBePossible(boolean gps) throws Exception {
        villageTables();
        replaceTable("villages", "A,D1,0,0", "A,D1,bad,0");
        var f = villageForm(gps);
        var c = cfg(2, 1, 4, LOCATED + "," + FORCE_A);
        var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty());
        assertWarning(r, null, 2);
        unchanged(run(f, c), r);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noDistantLocatedViableCandidateFallsBackWithoutUsingTheGpsArea(boolean gps) throws Exception {
        villageTables();
        replaceTable("villages", "B,D1,0,2", "B,D1,0,0.01");
        replaceTable("villages", "C,D2,0,4", "C,D2,0,0.02");
        var f = villageForm(gps);
        var c = cfg(2, 1, 4, LOCATED + "," + FORCE_A);
        var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty());
        assertWarning(r, null, 2);
        unchanged(run(f, c), r);
    }

    @Test void eachFailedVillageTargetGetsASubmissionWarningInNumberOrder() throws Exception {
        villageTables();
        replaceTable("villages", "A,D1,0,0", "A,D1,bad,0");
        var r = run(villageForm(false), cfg(8, 4, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty());
        assertEquals(4, r.plan().warnings().size(), "an unusable village is a per-target failure, not a global GPS warning");
        for (int n = 5; n <= 8; n++)
            FieldworkTestSupport.warning(List.of(r.plan().warnings().get(n - 5)), null, n);
    }

    @Test void aLateVillageTravelTargetFallsBackToTheOrdinaryNextDay() throws Exception {
        villageTables();
        var f = villageForm(false, 9 * 3600);
        var c = cfg(2, 1, 4, LOCATED + "," + FORCE_A);
        var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        unchanged(run(f, c), r);
    }

    @Test void aVillageWithoutCoordinatesConfigurationStillUsesTheOriginalGpsTravelRule() throws Exception {
        villageTables();
        var r = run(villageForm(true), cfg(2, 1, 1, FORCE_A).withProblems(only("travel", .5)));
        assertEquals(Set.of(2), targets(r, "travel"));
        assertEquals(List.of("trace_z", "gps_a", "shape"), r.plan().problems().stream().map(InjectedProblem::field).toList());
        var p = r.plan().problems().get(0);
        travelSchedule(r, p);
        assertTrue(InjectedProblemsTestSupport.distance(r.visit(1), r.visit(2)) + 1e-6
                >= Math.max(20000, 2000 * gap(r.related(p), r.target(p))));
        assertTrue(r.plan().problems().stream().allMatch(record -> record.cleanValue().isEmpty()));
    }

    @Test void coordinatesConfigurationWithoutAVillageQuestionStillUsesTheOriginalGpsTravelRule() throws Exception {
        var f = InjectedProblemsTestSupport.ordinary(0, false, false, true);
        var c = InjectedProblemsTestSupport.config(2, 1, LOCATED + ",\"id\":\"id\",\"interviewsPerDay\":1");
        var r = run(f, c.withProblems(only("travel", .5)));
        assertEquals(Set.of(2), targets(r, "travel"));
        assertEquals(List.of("gps", "trace", "shape"), r.plan().problems().stream().map(InjectedProblem::field).toList());
        var p = r.plan().problems().get(0);
        travelSchedule(r, p);
        assertTrue(InjectedProblemsTestSupport.distance(r.visit(1), r.visit(2)) + 1e-6
                >= Math.max(20000, 2000 * gap(r.related(p), r.target(p))));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noGpsAndNoConfiguredVillageGivesOneGlobalTravelWarning(boolean villageQuestion) throws Exception {
        villageTables();
        var f = villageQuestion ? villageForm(false) : InjectedProblemsTestSupport.singleTimed(0);
        var c = cfg(8, 4, 4, villageQuestion ? FORCE_A : LOCATED);
        var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty());
        // The no-id field warning is unrelated when the form is the one-question control.
        var warnings = r.plan().warnings().stream().filter(w -> w.contains("travel")).toList();
        assertEquals(1, warnings.size(), "one configuration-wide warning, not one per target");
        unchanged(run(f, c), r);
    }

    @Test void zeroTravelRateLeavesLocatedVillageInterviewsUnchangedAndHasNoTravelWarning() throws Exception {
        villageTables();
        var f = villageForm(true);
        var c = cfg(4, 1, 4, LOCATED + "," + FORCE_A);
        var r = run(f, c.withProblems(only("travel", 0)));
        unchanged(run(f, c), r);
        assertTrue(r.plan().problems().isEmpty());
        assertTrue(r.plan().warnings().stream().noneMatch(w -> w.contains("travel")));
    }

    @Test void aSingleSubmissionCannotBeItsOwnPreviousInterview() throws Exception {
        villageTables();
        var r = run(villageForm(false), cfg(1, 1, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", 1)));
        assertTrue(r.plan().problems().isEmpty());
        assertWarning(r, "travel", 1, 0);
        assertEquals("A", value(r.submissions().get(0), "village"));
    }
}
