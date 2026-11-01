package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment6Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 6: independent draws, code isolation, and per-answer precedence. */
@Isolated("Walks change the JVM time zone")
class InjectedAmendment6PrecedenceTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void dontKnowWinsAndRefuseHasAQuarterChanceAmongUnchangedAnswers(boolean multiple) throws Exception {
        var form = selection(multiple, "ok", "DK", "REF");
        var config = settings(6000, 2, bothCodes() + "," + fixed("answer", "ok"));
        var dkOnly = run(form, config.withProblems(rates(0, "dontKnow")));
        var both = run(form, config.withProblems(rates(0, "refuse", "dontKnow")));
        var dk = records(both, "dontKnow");
        var refusals = records(both, "refuse");
        assertEquals(soleEnumerator(both, "dontKnow"), soleEnumerator(both, "refuse"));
        // Adding the later fault cannot consume the earlier fault's random draws.
        assertEquals(targets(dkOnly, "dontKnow"), targets(both, "dontKnow"));
        rate(dk.size(), 3000, .25);
        rate(refusals.size(), 3000 - dk.size(), .25);
        rate(refusals.size(), 3000, .75 * .25);
        Set<Integer> occupied = new HashSet<>();
        for (var p : both.plan().problems()) {
            assertTrue(occupied.add(p.submission()), "only one fault may change this single answer");
            assertEquals("answer", p.field());
            if (p.problem().equals("refuse")) assertEquals("ok", p.cleanValue());
            assertEquals(p.problem().equals("dontKnow") ? "DK" : "REF", p.value());
            assertEquals(p.value(), value(both.target(p), "answer"));
        }
        for (var submission : both.submissions()) {
            if (!occupied.contains(submission.number())) assertEquals("ok", value(submission, "answer"));
        }
        metadata6(both);
    }

    @Test void independenceAlsoHoldsWhenDontKnowAndRefuseAreRunSeparately() throws Exception {
        var form = selection(false, "ok", "DK", "REF");
        var config = settings(4000, 2, bothCodes() + "," + fixed("answer", "ok"));
        var dk = run(form, config.withProblems(rates(0, "dontKnow")));
        var rf = run(form, config.withProblems(rates(0, "refuse")));
        assertEquals(soleEnumerator(dk, "dontKnow"), soleEnumerator(rf, "refuse"),
                "assignment depends only on seed and E for one active fault");
        var dkTargets = targets(dk, "dontKnow");
        var rfTargets = targets(rf, "refuse");
        rate(dkTargets.size(), 2000, .25);
        rate(rfTargets.size(), 2000, .25);
        rate((int) dkTargets.stream().filter(rfTargets::contains).count(), 2000, .25 * .25);
        assertNotEquals(dkTargets, rfTargets, "faults must not reuse the same Bernoulli stream");
    }

    @ParameterizedTest @ValueSource(strings = {"unoffered", "ok"})
    void anUnusableOrAlreadyPresentDontKnowCodeDoesNotBlockRefuse(String dkCode) throws Exception {
        var form = selection(false, "ok", "REF");
        var run = run(form, settings(1600, 2, "\"dontKnowCodes\":[\"" + dkCode + "\"],"
                + codes("REF") + "," + fixed("answer", "ok"))
                .withProblems(rates(0, "dontKnow", "refuse")));
        assertTrue(records(run, "dontKnow").isEmpty());
        rate(records(run, "refuse").size(), 800, .25);
        truthfulCodeRecords(run, "answer", "ok", List.of("REF"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void eachFaultUsesOnlyItsOwnCodesEvenWhenBothListsAndSpecialsArePresent(String fault) throws Exception {
        var form = selection(false, "ok", "DK", "REF", "97");
        var run = run(form, settings(1000, 2, bothCodes() + ",\"specialValues\":[\"97\"],"
                + fixed("answer", "ok")).withProblems(rates(0, fault)));
        rate(records(run, fault).size(), 500, .25);
        assertTrue(records(run, fault.equals("refuse") ? "dontKnow" : "refuse").isEmpty());
        for (var p : run.plan().problems()) {
            assertEquals(fault.equals("refuse") ? "REF" : "DK", p.value());
            assertEquals("ok", p.cleanValue());
        }
    }

    @Test void refusalPrecedenceIsPerAnswerAndRecordsAreGroupedByFaultBeforeFieldOrder() throws Exception {
        var form = fixture("<z/><a/>", bind("a", "select1", "") + bind("z", "select1", ""),
                choices("z", false, "ok", "DK", "REF") + choices("a", false, "ok", "DK", "REF"));
        var run = run(form, settings(1600, 2, bothCodes()
                + ",\"fields\":{\"z\":{\"value\":\"ok\"},\"a\":{\"value\":\"ok\"}}")
                .withProblems(rates(0, "refuse", "dontKnow")));
        int mixed = 0, refusalPairs = 0;
        for (var submission : run.submissions()) {
            var ps = run.plan().problems().stream().filter(p -> p.submission() == submission.number()).toList();
            assertEquals(ps.size(), ps.stream().map(InjectedProblem::field).distinct().count());
            if (ps.size() == 2 && !ps.get(0).problem().equals(ps.get(1).problem())) {
                mixed++;
                assertEquals(List.of("dontKnow", "refuse"), ps.stream().map(InjectedProblem::problem).toList());
            }
            if (ps.size() == 2 && ps.stream().allMatch(p -> p.problem().equals("refuse"))) {
                refusalPairs++;
                assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList());
            }
            for (var p : ps) {
                if (p.problem().equals("refuse")) assertEquals("ok", p.cleanValue());
                assertEquals(p.problem().equals("dontKnow") ? "DK" : "REF", p.value());
                assertEquals(p.value(), value(submission, p.field()));
            }
        }
        assertTrue(mixed > 0, "a dontKnow on one field must not suppress refuse on another");
        assertTrue(refusalPairs > 0, "refuse draws independently for each answer, not once per interview");
        metadata6(run);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anUnusableOwnListNeverFallsBackToTheOtherFaultsOfferedCodes(String fault) throws Exception {
        var form = selection(false, "ok", "DK", "REF", "97");
        String lists = fault.equals("refuse")
                ? "\"dontKnowCodes\":[\"DK\"]," + codes("unoffered")
                : "\"dontKnowCodes\":[\"unoffered\"]," + codes("REF");
        var run = run(form, settings(200, 2, lists + ",\"specialValues\":[\"97\"],"
                + fixed("answer", "ok")).withProblems(rates(0, fault)));
        assertTrue(run.plan().problems().isEmpty());
        for (var submission : run.submissions()) ordinary(run, submission.number());
    }

    @Test void outlierAnswersAreProtectedFromTheLaterRefusalFault() throws Exception {
        var run = run(number("int", ""), settings(600, 2, codes("-888"))
                .withProblems(rates(1, "refuse")));
        assertEquals(600, records(run, "outlier").size());
        assertTrue(records(run, "refuse").isEmpty());
        for (var p : records(run, "outlier")) assertEquals(p.value(), value(run.target(p), "amount"));
    }

    @Test void answersAlreadyChangedBySkipModulesAreProtectedFromRefuse() throws Exception {
        var form = module(3, false, false, "/data/gate = 'yes'", "yes", "no", "REF");
        var run = run(form, settings(1600, 2, codes("REF")
                + ",\"specialValues\":[\"REF\"]," + fixed("gate", "yes"))
                .withProblems(rates(0, "refuse", "skipModules")));
        var skipped = targets(run, "skipModules");
        assertFalse(skipped.isEmpty());
        assertFalse(records(run, "refuse").isEmpty());
        assertTrue(records(run, "refuse").stream().anyMatch(p -> p.field().equals("gate")),
                "exercise a gate replacement on an answer skipModules left unchanged");
        for (var p : records(run, "refuse")) {
            assertFalse(skipped.contains(p.submission()));
            if (p.field().equals("gate")) assertEquals("yes", p.cleanValue());
            else assertTrue(Set.of("q0", "q1", "q2").contains(p.field()), "only module text is also eligible");
            assertEquals("REF", p.value());
        }
        for (var p : records(run, "skipModules")) assertEquals("no", value(run.target(p), "gate"));
    }
}
