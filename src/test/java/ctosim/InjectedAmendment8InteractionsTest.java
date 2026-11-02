package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment8InteractionsTest {
    private static final String LISTS = "\"dontKnowCodes\":[\"DK\"],\"refuseCodes\":[\"RF\"]," + codes("O");
    private static LoadedForm withTeamCodes() throws Exception {
        return pair(false, "", "", "a", "abc", "O", "Other", "DK", "Unknown", "RF", "Refused");
    }

    @Test void dontKnowAndRefuseGoFirstAndOtherGetsOneQuarterOfTheRemainingAnswers() throws Exception {
        var c = settings(6000, 2, LISTS + "," + fixed("answer", "a"));
        var f = withTeamCodes();
        var earlier = run(f, c.withProblems(rates(0, "dontKnow", "refuse")));
        var all = run(f, c.withProblems(rates(0, "other", "refuse", "dontKnow")));
        Set<Integer> dk = targets(all, "dontKnow"), rf = targets(all, "refuse"), ot = targets(all, "other");
        assertEquals(targets(earlier, "dontKnow"), dk);
        assertEquals(targets(earlier, "refuse"), rf);
        assertEquals(soleEnumerator(all, "dontKnow"), soleEnumerator(all, "other"));
        assertEquals(soleEnumerator(all, "refuse"), soleEnumerator(all, "other"));
        assertTrue(Collections.disjoint(dk, ot)); assertTrue(Collections.disjoint(rf, ot));
        rate(dk.size(), 3000, .25); rate(rf.size(), 3000 - dk.size(), .25);
        rate(ot.size(), 3000 - dk.size() - rf.size(), .25);
        for (var s : all.submissions()) {
            assertEquals(dk.contains(s.number()) ? "DK" : rf.contains(s.number()) ? "RF" : ot.contains(s.number()) ? "O" : "a", value(s, "answer"));
            if (ot.contains(s.number())) assertEquals("abc", value(s, "specify"), "other's specify is protected from both text faults");
        }
        for (var p : others(all)) { assertEquals("answer", p.field()); assertEquals("a", p.cleanValue()); }
        metadata(all);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void otherUsesDrawsIndependentOfEachEarlierCodeFault(String fault) throws Exception {
        var c = settings(4000, 2, LISTS + "," + fixed("answer", "a"));
        var f = withTeamCodes();
        Set<Integer> earlier = targets(run(f, c.withProblems(rates(0, fault))), fault);
        Set<Integer> other = targets(run(f, c.withProblems(rates(0, "other"))), "other");
        rate(earlier.size(), 2000, .25); rate(other.size(), 2000, .25);
        rate((int) other.stream().filter(earlier::contains).count(), 2000, .25 * .25);
        assertNotEquals(earlier, other);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anEarlierAttemptWithoutAnOfferedCodeDoesNotBlockOther(String fault) throws Exception {
        var r = run(simple(false), settings(2000, 2, LISTS + "," + fixed("answer", "a"))
                .withProblems(rates(0, fault, "other")));
        assertTrue(records(r, fault).isEmpty(), "specify text inserted by other is protected too");
        singleRecords(r, "a", Set.of("O")); rate(others(r).size(), 1000, .25);
    }

    @Test void answersChangedBySkipModulesAreProtectedButUnchangedAnswersRemainEligible() throws Exception {
        var f = fixture("<answer/><module><a/><b/><c/></module><specify/>", bind("answer", "select1", "")
                + "<bind nodeset=\"/data/module\" relevant=\"/data/answer = 'yes'\"/>"
                + bind("module/a", "string", "") + bind("module/b", "string", "") + bind("module/c", "string", "")
                + bind("specify", "string", relevant("answer", "O")),
                select("answer", false, "yes", "abc", "no", "No", "O", "Other")
                + "<group ref=\"/data/module\">" + input("module/a") + input("module/b") + input("module/c") + "</group>" + input("specify"));
        var r = run(f, settings(2400, 2, codes("O") + ",\"specialValues\":[\"O\"]," + fixed("answer", "yes"))
                .withProblems(rates(0, "skipModules", "other")));
        var skipped = targets(r, "skipModules");
        assertFalse(skipped.isEmpty()); assertFalse(others(r).isEmpty());
        assertTrue(Collections.disjoint(skipped, targets(r, "other")));
        rate(others(r).size(), 1200 - skipped.size(), .25);
        for (var p : records(r, "skipModules")) assertEquals("no", value(r.target(p), "answer"));
        for (var p : others(r)) assertEquals("yes", p.cleanValue());
    }

    @Test void resentCopiesKeepSelectAndSpecifyAndCarryOnlyTheirDuplicateRecord() throws Exception {
        var r = run(simple(false), settings(1200, 2, codes("O") + "," + fixed("answer", "a"))
                .withProblems(new ProblemRates(.25, 0, 0, 0, 0, 0, 0, 0, List.of("other"))));
        int copiedFaults = 0;
        for (var p : records(r, "resent")) {
            for (String field : List.of("answer", "specify")) assertEquals(value(r.related(p), field), value(r.target(p), field));
            assertEquals(1, r.plan().problems().stream().filter(q -> q.submission() == p.submission()).count());
            if (value(r.related(p), "answer").equals("O")) copiedFaults++;
        }
        assertTrue(copiedFaults > 0, "exercise copies of actual other injections");
    }
}
