package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static ctosim.FormWalkerTestSupport.value;
import static ctosim.InjectedProblemsTestSupport.only;
import static ctosim.InjectedProblemsTestSupport.key;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses only test fixtures for spec 13 Amendment 3's attached search cascade. */
@Isolated("Walks temporarily change JVM time zone")
class InjectedProblemsCascadeTest extends FieldworkAmendment3Support {
    @Override LoadedForm attached(String instance, String binds, String body) throws Exception {
        // Duplicate records identify originals by KEY, so this fixture needs instance IDs.
        return super.attached(instance + "<meta><instanceID/></meta>",
                binds + "<bind nodeset=\"/data/meta/instanceID\" type=\"string\" jr:preload=\"uid\"/>", body);
    }

    private static void assertDistinctKeys(List<Submission> submissions) throws Exception {
        var keys = new HashSet<String>();
        for (var submission : submissions) {
            String instanceId = key(submission);
            assertFalse(instanceId.isEmpty(), "cascade fixture must provide a KEY");
            assertTrue(keys.add(instanceId), "each submission must have its own KEY");
        }
    }

    @Test void reinterviewCopiesTheIdAndEveryUpperQuestionFromItsOriginal() throws Exception {
        var c = idConfig(6, "\"enumerators\":3").withProblems(only("reinterview", 1.0 / 3));
        var plan = new Fieldwork(deep(false), c); var submissions = plan.walkAll();
        assertDistinctKeys(submissions);
        assertEquals(2, plan.problems().size()); Set<String> originals = new HashSet<>();
        for (var p : plan.problems()) {
            var target = submissions.get(p.submission() - 1); Submission original = null;
            for (var s : submissions) if (key(s).equals(p.relatedKey())) original = s;
            assertNotNull(original); assertTrue(original.number() < target.number()); assertTrue(originals.add(p.relatedKey()));
            for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(original, field), value(target, field), "reinterview preserves cascade question " + field);
            assertEquals("answer", p.field()); assertEquals(value(target, "answer"), p.value());
            assertEquals(value(original, "answer"), plan.visits().get(target.number() - 1).id());
            assertEquals(key(target), p.key()); assertNotEquals(key(original), p.key());
        }
    }
    @Test void resentCopiesACascadeSubmissionWithoutConsumingAnotherHouseholdId() throws Exception {
        var c = idConfig(6, "\"enumerators\":3").withProblems(only("resent", 1.0 / 3));
        var plan = new Fieldwork(deep(true), c); var submissions = plan.walkAll();
        assertDistinctKeys(submissions);
        assertEquals(2, plan.problems().size()); var targetNumbers = new HashSet<Integer>();
        for (var p : plan.problems()) targetNumbers.add(p.submission());
        var ordinaryIds = new HashSet<String>();
        for (var s : submissions) if (!targetNumbers.contains(s.number())) assertTrue(ordinaryIds.add(value(s, "answer")));
        for (var p : plan.problems()) {
            Submission original = null;
            for (var s : submissions) if (key(s).equals(p.relatedKey())) original = s;
            assertNotNull(original);
            for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(original, field), value(submissions.get(p.submission() - 1), field));
            assertEquals(plan.visits().get(original.number() - 1), plan.visits().get(p.submission() - 1));
        }
    }
}
