package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static ctosim.FormWalkerAmendment12Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change the JVM default time zone")
class FormWalkerAmendment12AdjustedTimeTest {
    @ParameterizedTest @ValueSource(strings = {"short", "long"})
    void screenTimeSumsEachScaledAndRoundedQuestionAdvance(String problem) throws Exception {
        var form = timeline(12, "0");
        var config = settings(8, 1, "").withProblems(new ProblemRates(0, 0,
                problem.equals("short") ? 1 : 0, problem.equals("long") ? 1 : 0, 0, 0, 0));
        Fieldwork plan = new Fieldwork(form, config);
        var results = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals(problem)));
        for (Submission s : results) {
            Submission clean = new FormWalker(form, config.withProblems(null))
                    .walk(s.number(), plan.visits().get(s.number() - 1));
            double lower = problem.equals("short") ? .15 : 3;
            double upper = problem.equals("short") ? .30 : 6;
            long previousClean = 0, previousActual = 0;
            for (int i = 1; i <= 12; i++) {
                long base = integer(clean, "s" + i), actual = integer(s, "s" + i);
                long baseStep = base - previousClean, actualStep = actual - previousActual;
                assertTrue(actualStep >= 1);
                // Every observed step must admit the same per-submission factor.
                lower = Math.max(lower, actualStep == 1 ? 0 : (actualStep - .5) / baseStep);
                upper = Math.min(upper, (actualStep + .5) / baseStep);
                assertTrue(lower <= upper + 1e-10, "One factor precedes rounding each individual advance");
                assertEquals(0, offScreen(s, i), "A duration factor is screen time, not a pause");
                previousClean = base;
                previousActual = actual;
            }
            totals(s, previousActual);
            assertEquals(previousActual, seconds(s));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0", "90.1"})
    void pausedFaultyEnumeratorAccumulatesScaledScreenTimeThenAppliesMinimums(String minimum) throws Exception {
        var form = timeline(10, minimum);
        var config = settings(2, 2, "").withProblems(new ProblemRates(
                0, 0, 0, 0, 0, 0, 0, 1, 0, List.of("tooFast")));
        Fieldwork plan = new Fieldwork(form, config);
        var results = plan.walkAll();
        var faultyNumbers = plan.problems().stream().filter(p -> p.problem().equals("tooFast"))
                .map(InjectedProblem::submission).distinct().toList();
        assertEquals(1, faultyNumbers.size());
        for (Submission s : results) {
            Submission clean = new FormWalker(form, config.withProblems(null))
                    .walk(s.number(), plan.visits().get(s.number() - 1));
            boolean faulty = faultyNumbers.contains(s.number());
            long previousClean = 0, previousActual = 0;
            for (int i = 1; i <= 10; i++) {
                long base = integer(clean, "s" + i), actual = integer(s, "s" + i);
                long expectedStep = minimum.equals("90.1") ? 91
                        : faulty ? Math.max(1, Math.round((base - previousClean) * .4)) : base - previousClean;
                assertEquals(expectedStep, actual - previousActual,
                        "A pause must not enter the advance that is scaled or clamped");
                previousClean = base;
                previousActual = actual;
            }
            totals(s, previousActual);
            assertTrue(seconds(s) > previousActual, "Both visits are left open");
            assertEquals(seconds(s) - previousActual, offScreen(s, 10));
        }
    }

    @Test void finalConstraintRepairDoesNotAddScreenTime() throws Exception {
        var form = fixture("<early>1</early><later>0</later><snapshot/>",
                bind("early", "int", "constraint=\". &gt;= /data/later\" ct:minimum-seconds=\"600\"")
                + bind("later", "int", "ct:minimum-seconds=\"90\"") + screenBind("snapshot"),
                input("early") + input("later") + input("snapshot"));
        Submission s = new FormWalker(form, settings("\"fields\":{\"later\":{\"value\":9}}")).walk(1);
        assertTrue(integer(s, "early") >= 9, "The kept answer must be repaired at final checking");
        assertEquals(90, integer(s, "snapshot"));
        totals(s, 90);
        assertEquals(90, seconds(s));
        assertEquals(1, s.answered());
    }

    @Test void pausedFinalConstraintClearingDoesNotAddScreenTime() throws Exception {
        var form = fixture("<early>1</early><later>0</later><snapshot/>",
                bind("early", "int", "constraint=\"/data/later = 0\" ct:minimum-seconds=\"600\"")
                + bind("later", "int", "ct:minimum-seconds=\"90\"") + screenBind("snapshot"),
                input("early") + input("later") + input("snapshot"));
        Submission s = pause(form, pausedSettings("\"fields\":{\"later\":{\"value\":9}}")).submission();
        assertEquals("", value(s, "early"));
        assertEquals(90, integer(s, "snapshot"));
        totals(s, 90);
        assertTrue(seconds(s) > 90);
        assertEquals(1, s.answered());
    }
}
