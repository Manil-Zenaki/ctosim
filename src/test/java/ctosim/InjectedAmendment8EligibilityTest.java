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
class InjectedAmendment8EligibilityTest {
    @ParameterizedTest @ValueSource(strings = {"missing", "earlier", "unrelated", "real_only", "false", "numeric", "unoffered"})
    void anOtherChoiceNeedsALaterRelevantTextThatRefersToThatSelect(String scenario) throws Exception {
        String relevance = switch (scenario) {
            case "unrelated" -> "relevant=\"/data/unrelated = 'yes'\"";
            case "real_only" -> relevant("answer", "a");
            case "false" -> "relevant=\"selected(/data/answer, 'O') and false()\"";
            default -> relevant("answer", "O");
        };
        String target = select("answer", false, "a", "abc", scenario.equals("unoffered") ? "NOT_O" : "O", "Other");
        String detail = scenario.equals("missing") ? "" : input("detail");
        var f = fixture("<unrelated/>" + (scenario.equals("earlier") ? "<detail/><answer/>" : "<answer/><detail/>")
                + "<control/><control_text/>", bind("unrelated", "string", "")
                + bind("answer", "select1", "") + bind("detail", scenario.equals("numeric") ? "int" : "string", relevance)
                + bind("control", "select1", "") + bind("control_text", "string", relevant("control", "O")),
                input("unrelated") + (scenario.equals("earlier") ? detail + target : target + detail)
                        + select("control", false, "a", "xyz", "O", "Other") + input("control_text"));
        var r = other(f, 600, codes("O") + "," + fixed("unrelated", "yes", "answer", "a", "control", "a"));
        unchanged(r, "answer", "a");
        assertFalse(others(r).isEmpty(), "eligible companion proves the fault is active");
        assertTrue(others(r).stream().allMatch(p -> p.field().equals("control")));
    }

    @Test void firstQualifyingTextInFormOrderWinsOverBindAndAlphabeticalOrder() throws Exception {
        var f = fixture("<answer/><z/><a/>", bind("answer", "select1", "")
                        + bind("a", "string", relevant("answer", "O")) + bind("z", "string", relevant("answer", "O")),
                select("answer", false, "a", "abc", "O", "Other") + input("z") + input("a"));
        var r = other(f, 800, codes("O") + "," + fixed("answer", "a", "z", "usual_z", "a", "usual_a"));
        singleRecords(r, "a", Set.of("O"));
        for (var p : others(r)) {
            assertEquals("abc", value(r.target(p), "z"));
            assertEquals("usual_a", value(r.target(p), "a"));
        }
    }

    @Test void aTextRelevantForBothRealAndOtherStillQualifiesWhenItRefersToTheSelect() throws Exception {
        var f = fixture("<answer/><specify/>", bind("answer", "select1", "")
                + bind("specify", "string", "relevant=\"/data/answer != ''\""),
                select("answer", false, "a", "abc", "O", "Other") + input("specify"));
        var r = other(f, 800, codes("O") + "," + fixed("answer", "a", "specify", "ordinary"));
        singleRecords(r, "a", Set.of("O"));
        for (var s : r.submissions()) assertEquals(targets(r, "other").contains(s.number()) ? "abc" : "ordinary", value(s, "specify"));
    }

    @Test void rejectionAtTheFirstSpecifyDoesNotRedirectTheLabelToALaterText() throws Exception {
        var f = fixture("<answer/><first/><second/>", bind("answer", "select1", "")
                + bind("first", "string", relevant("answer", "O") + " constraint=\". = 'usual_first'\"")
                + bind("second", "string", relevant("answer", "O")),
                select("answer", false, "a", "abc", "O", "Other") + input("first") + input("second"));
        var r = other(f, 800, codes("O") + "," + fixed("answer", "a", "first", "usual_first", "second", "usual_second"));
        singleRecords(r, "a", Set.of("O"));
        for (var p : others(r)) {
            assertEquals("usual_first", value(r.target(p), "first")); assertEquals("usual_second", value(r.target(p), "second"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "irrelevant"})
    void blankAndIrrelevantSelectsStayBlank(String mode) throws Exception {
        var f = pair(false, mode.equals("irrelevant") ? "relevant=\"false()\"" : "", "", "a", "abc", "O", "Other");
        var r = other(f, 400, codes("O") + ",\"fields\":{\"answer\":{\"missingRate\":1}}");
        unchanged(r, "answer", "");
        for (var s : r.submissions()) assertEquals("", value(s, "specify"));
    }
}
