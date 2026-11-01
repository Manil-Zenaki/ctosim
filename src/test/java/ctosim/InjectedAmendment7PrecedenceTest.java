package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment7Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment7PrecedenceTest {
    private static String lists() {
        return teamCodes("dontKnow", "DK") + "," + teamCodes("refuse", "REF");
    }

    @Test void dontKnowChangesOneQuarterOfTextAndRefuseChangesOneQuarterOfTheRemainder() throws Exception {
        var f = textQuestion("");
        var c = settings(6000, 2, lists() + "," + fixedText("answer", "ordinary"));
        var dk = run(f, c.withProblems(rates(0, "dontKnow")));
        var both = run(f, c.withProblems(rates(0, "refuse", "dontKnow")));
        var d = targets(both, "dontKnow");
        var r = targets(both, "refuse");
        assertEquals(targets(dk, "dontKnow"), d, "adding refuse preserves dontKnow draws");
        assertEquals(soleEnumerator(both, "dontKnow"), soleEnumerator(both, "refuse"));
        assertTrue(Collections.disjoint(d, r), "dontKnow changes protect that answer from refuse");
        rate(d.size(), 3000, .25);
        rate(r.size(), 3000 - d.size(), .25);
        for (var p : both.plan().problems()) {
            assertEquals(p.problem().equals("dontKnow") ? "DK" : "REF", p.value());
            // Amendment 6 leaves dontKnow's combined-run cleanValue unspecified.
            if (p.problem().equals("refuse")) assertEquals("ordinary", p.cleanValue());
        }
        for (var s : both.submissions()) {
            assertEquals(d.contains(s.number()) ? "DK" : r.contains(s.number()) ? "REF" : "ordinary",
                    value(s, "answer"));
        }
        metadata(both);
    }

    @Test void theTwoFaultsUseIndependentTextReplacementDraws() throws Exception {
        var f = textQuestion("");
        var c = settings(4000, 2, lists() + "," + fixedText("answer", "ordinary"));
        var dk = run(f, c.withProblems(rates(0, "dontKnow")));
        var rf = run(f, c.withProblems(rates(0, "refuse")));
        var d = targets(dk, "dontKnow");
        var r = targets(rf, "refuse");
        assertEquals(soleEnumerator(dk, "dontKnow"), soleEnumerator(rf, "refuse"));
        rate(d.size(), 2000, .25);
        rate(r.size(), 2000, .25);
        rate((int) d.stream().filter(r::contains).count(), 2000, .25 * .25);
        assertNotEquals(d, r);
    }

    @ParameterizedTest @ValueSource(strings = {"rejected", "ordinary"})
    void aDontKnowDrawThatLeavesTheTextUnchangedDoesNotBlockRefuse(String dkCode) throws Exception {
        var f = textQuestion("constraint=\". = 'ordinary' or . = 'REF'\"");
        var r = run(f, settings(2000, 2, teamCodes("dontKnow", dkCode) + ","
                + teamCodes("refuse", "REF") + "," + fixedText("answer", "ordinary"))
                .withProblems(rates(0, "dontKnow", "refuse")));
        assertTrue(records(r, "dontKnow").isEmpty());
        rate(records(r, "refuse").size(), 1000, .25);
        textRecords(r, "refuse", "answer", "ordinary", List.of("REF"));
    }

    @Test void precedenceIsPerTextAnswerAndRecordsAreGroupedByFaultThenFormOrder() throws Exception {
        var f = fixture("<z/><a/>", bind("a", "string", "") + bind("z", "string", ""),
                input("z") + input("a"));
        var r = run(f, settings(2000, 2, lists()
                + ",\"fields\":{\"z\":{\"value\":\"first\"},\"a\":{\"value\":\"second\"}}")
                .withProblems(rates(0, "refuse", "dontKnow")));
        int mixed = 0;
        Set<String> pairs = new HashSet<>();
        for (var s : r.submissions()) {
            var ps = r.plan().problems().stream().filter(p -> p.submission() == s.number()).toList();
            if (ps.size() == 2) {
                if (!ps.get(0).problem().equals(ps.get(1).problem())) {
                    mixed++;
                    assertEquals(List.of("dontKnow", "refuse"), ps.stream().map(InjectedProblem::problem).toList());
                } else {
                    pairs.add(ps.get(0).problem());
                    assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList());
                }
            }
            for (var p : ps) {
                if (p.problem().equals("refuse"))
                    assertEquals(p.field().equals("z") ? "first" : "second", p.cleanValue());
                assertEquals(p.problem().equals("dontKnow") ? "DK" : "REF", p.value());
            }
        }
        assertTrue(mixed > 0, "a change on z does not protect a");
        assertEquals(Set.of("dontKnow", "refuse"), pairs, "exercise independent draws on both fields for each fault");
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void eachTextFaultUsesItsOwnListDespiteOtherAvailableCodes(String fault) throws Exception {
        var r = textRun(fault, textQuestion(""), 800,
                lists() + ",\"specialValues\":[\"97\"]," + fixedText("answer", "ordinary"));
        textRecords(r, fault, "answer", "ordinary", List.of(fault.equals("dontKnow") ? "DK" : "REF"));
        rate(records(r, fault).size(), 400, .25);
    }
}
