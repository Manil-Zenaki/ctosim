package ctosim;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment2MovementTest {
    private static LoadedForm form2(String eligible, boolean constraint) throws Exception {
        // The id is protected and identifies visits. Amount's U[10,20] upper interval is [25,35].
        return fixture("<id/><amount/>", bind("id", "int", "") + bind("amount", "decimal",
                constraint ? "constraint=\". &gt;= 10 and (. &lt; 24 or (" + eligible + "))\"" : "relevant=\"" + eligible + "\""),
                input("id") + input("amount"));
    }
    private static SimulationConfig config2(int n, ProblemRates problems) throws Exception {
        // Give each visit a distinct id without changing amount's distribution.
        return settings(n, 2, "\"id\":\"id\",\"fields\":{\"id\":{\"min\":10,\"max\":" + (n + 9) + "}}")
                .withProblems(problems);
    }
    private static String eligibility(Run control, Collection<Integer> numbers) throws Exception {
        Set<String> ids = new HashSet<>();
        for (var s : control.submissions()) assertTrue(ids.add(value(s, "id")), "fixture ids must identify distinct visits");
        List<String> terms = new ArrayList<>();
        for (int n : numbers) terms.add("/data/id = '" + value(control.submissions().get(n - 1), "id") + "'");
        return terms.isEmpty() ? "false()" : String.join(" or ", terms);
    }
    private static Run singleWithRoom() throws Exception {
        var f = form2("true()", false);
        for (int seed = 1; seed <= 80; seed++) {
            var control = run(f, config2(12, rates(1.0 / 12)).withSeed(seed));
            assertEquals(1, records(control, "outlier").size());
            if (records(control, "outlier").get(0).submission() <= 9) return control;
        }
        return fail("fixed seed ensemble must exercise an outlier with three later submissions");
    }
    private static void moved(Run r, int expected) throws Exception {
        assertEquals(Set.of(expected), targets(r, "outlier")); assertEquals(1, records(r, "outlier").size());
        var p = records(r, "outlier").get(0);
        assertEquals("amount", p.field()); assertEquals(value(r.submissions().get(expected - 1), "amount"), p.value());
        double x = Double.parseDouble(p.value()); assertTrue(x >= 25 && x <= 35);
        assertEquals(value(r.clean(expected), "amount"), p.cleanValue(), "cleanValue belongs to the destination visit");
        metadata2(r);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aFailedTargetMovesToTheNextSubmissionThatCanAcceptAnOutlier(boolean constraint) throws Exception {
        var control = singleWithRoom(); int origin = records(control, "outlier").get(0).submission();
        List<Integer> eligible = new ArrayList<>(); for (int n = origin + 1; n <= 12; n++) eligible.add(n);
        var r = run(form2(eligibility(control, eligible), constraint), control.config());
        moved(r, origin + 1); assertTrue(r.plan().warnings().isEmpty(), "successful movement has no failed-origin warning");
        ordinary(r, origin);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void movementContinuesPastSeveralIneligibleVisitsUntilTheFirstSuccess(boolean constraint) throws Exception {
        var control = singleWithRoom(); int origin = records(control, "outlier").get(0).submission();
        var r = run(form2(eligibility(control, List.of(origin + 3, 12)), constraint), control.config());
        moved(r, origin + 3); assertTrue(r.plan().warnings().isEmpty());
        for (int n = origin; n < origin + 3; n++) ordinary(r, n);
        replay2(r);
    }

    @Test void theLastSubmissionIsAnEligibleDestination() throws Exception {
        var control = singleWithRoom();
        var r = run(form2(eligibility(control, List.of(12)), false), control.config());
        moved(r, 12); assertTrue(r.plan().warnings().isEmpty());
    }

    @Test void exhaustedMovementWarnsOnlyOnceAndNamesTheOriginalTarget() throws Exception {
        var control = singleWithRoom(); int origin = records(control, "outlier").get(0).submission();
        var r = run(form2("false()", true), control.config());
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size());
        assertWarning(r, "", origin);
        assertFalse(r.plan().warnings().get(0).matches("(?s).*\\b20\\b.*"));
        for (int n = 1; n <= 12; n++) ordinary(r, n);
    }

    @Test void movementNeverWrapsToAnEarlierEligibleSubmission() throws Exception {
        var f = form2("true()", false); Run control = null;
        for (int seed = 1; seed <= 80; seed++) {
            var trial = run(f, config2(8, rates(.125)).withSeed(seed));
            assertEquals(1, records(trial, "outlier").size());
            if (records(trial, "outlier").get(0).submission() > 1) { control = trial; break; }
        }
        assertNotNull(control); int origin = records(control, "outlier").get(0).submission();
        var r = run(form2(eligibility(control, List.of(1)), false), control.config());
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", origin);
        double ordinary = Double.parseDouble(value(r.submissions().get(0), "amount")); assertTrue(ordinary >= 10 && ordinary <= 20);
    }

    @Test void anOutlierDrawnAtTheLastSubmissionCannotMove() throws Exception {
        var f = form2("true()", false); boolean exercised = false;
        for (int seed = 1; seed <= 80; seed++) {
            var control = run(f, config2(4, rates(.25)).withSeed(seed));
            assertEquals(1, records(control, "outlier").size());
            if (!targets(control, "outlier").equals(Set.of(4))) continue;
            var r = run(form2(eligibility(control, List.of(1, 2, 3)), false), control.config());
            assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 4);
            exercised = true; break;
        }
        assertTrue(exercised, "fixed seed ensemble covers a last-submission origin");
    }

    @Test void movementSkipsTargetsOfEarlierProblemsEvenWhenTheyCanAcceptTheValue() throws Exception {
        var f = form2("true()", false); boolean exercised = false;
        var problems = new ProblemRates(0, 0, .3, 0, 0, 0, 0, .1, List.of());
        for (int seed = 1; seed <= 100; seed++) {
            var control = run(f, config2(10, problems).withSeed(seed));
            assertEquals(1, records(control, "outlier").size());
            int origin = records(control, "outlier").get(0).submission(); Set<Integer> reserved = targets(control, "short");
            if (origin == 10 || !reserved.contains(origin + 1)) continue;
            int expected = origin + 1; while (expected <= 10 && reserved.contains(expected)) expected++;
            if (expected > 10) continue;
            List<Integer> eligible = new ArrayList<>(); for (int n = origin + 1; n <= 10; n++) eligible.add(n);
            var r = run(form2(eligibility(control, eligible), false), control.config());
            moved(r, expected); assertEquals(reserved, targets(r, "short")); assertTrue(r.plan().warnings().isEmpty());
            exercised = true; break;
        }
        assertTrue(exercised, "exercise a reserved target immediately after the failed outlier");
    }

    @Test void twoMovedOutliersNeverClaimTheSameDestinationOrAnOriginalOutlierTarget() throws Exception {
        var f = form2("true()", false); boolean exercised = false;
        for (int seed = 1; seed <= 80; seed++) {
            var control = run(f, config2(12, rates(2.0 / 12)).withSeed(seed));
            var originals = targets(control, "outlier"); assertEquals(2, originals.size());
            int lastOrigin = Collections.max(originals);
            if (lastOrigin > 10) continue;
            List<Integer> destinations = List.of(lastOrigin + 1, lastOrigin + 2);
            var r = run(form2(eligibility(control, destinations), false), control.config());
            assertEquals(new TreeSet<>(destinations), targets(r, "outlier")); assertEquals(2, records(r, "outlier").size());
            assertTrue(r.plan().warnings().isEmpty()); metadata2(r); exercised = true; break;
        }
        assertTrue(exercised);
    }

    @Test void twoFailedOriginsCompetingForOneDestinationLeaveOneWarningForAnOriginal() throws Exception {
        var f = form2("true()", false); boolean exercised = false;
        for (int seed = 1; seed <= 80; seed++) {
            var control = run(f, config2(10, rates(.2)).withSeed(seed)); var originals = targets(control, "outlier");
            assertEquals(2, originals.size()); if (originals.contains(10)) continue;
            var r = run(form2(eligibility(control, List.of(10)), false), control.config()); moved(r, 10);
            assertEquals(1, r.plan().warnings().size()); String warning = r.plan().warnings().get(0);
            assertTrue(originals.stream().anyMatch(n -> warning.matches("(?s).*\\b" + n + "\\b.*")), warning);
            exercised = true; break;
        }
        assertTrue(exercised);
    }

    @Test void aReservedOriginalOutlierTargetIsNeverReusedAfterItsOwnInjectionFails() throws Exception {
        var r = run(form2("false()", false), config2(5, rates(1)));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(5, r.plan().warnings().size());
        for (int n = 1; n <= 5; n++) assertWarning(r, "", n);
    }

    @Test void anEnumeratorFaultDoesNotReserveItsSubmissionsAgainstMovedOutliers() throws Exception {
        var f = form2("true()", false); boolean exercised = false;
        for (int seed = 1; seed <= 80; seed++) {
            var control = run(f, config2(12, rates(1.0 / 12, "tooFast")).withSeed(seed));
            assertEquals(1, records(control, "outlier").size()); int origin = records(control, "outlier").get(0).submission();
            var laterFaulty = targets(control, "tooFast").stream().filter(n -> n > origin).sorted().collect(Collectors.toList());
            if (laterFaulty.isEmpty()) continue;
            int destination = laterFaulty.get(0);
            var r = run(form2(eligibility(control, List.of(destination)), false), control.config());
            moved(r, destination); assertTrue(targets(r, "tooFast").contains(destination)); exercised = true; break;
        }
        assertTrue(exercised);
    }
}
