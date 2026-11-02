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
class InjectedAmendment8ChoiceTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oneQuarterOfEligibleAnswersChangeAndSpecifyHasNoSeparateRecord(boolean multiple) throws Exception {
        var r = other(simple(multiple), 3200, codes("O") + "," + fixed("answer", "a"));
        rate(others(r).size(), 1600, .25);
        singleRecords(r, "a", Set.of("O"));
        for (var s : r.submissions()) assertEquals(targets(r, "other").contains(s.number()) ? "abc" : "", value(s, "specify"));
    }

    @Test void multipleSelectReplacesOneRealChoiceUniformlyAndKeepsTheChoicesOrder() throws Exception {
        var f = pair(true, "", "", "z", "zed", "O", "Other", "a", "aye", "m", "emm", "DK", "Unknown", "RF", "Refused");
        var r = other(f, 4800, codes("O") + ",\"dontKnowCodes\":[\"DK\"],\"refuseCodes\":[\"RF\"],"
                + fixed("answer", "z a m DK RF"));
        Map<String, String> expected = Map.of("O a m DK RF", "zed", "z O m DK RF", "aye", "z O a DK RF", "emm");
        singleRecords(r, "z a m DK RF", expected.keySet());
        rate(others(r).size(), 2400, .25);
        for (var entry : expected.entrySet()) {
            int count = (int) others(r).stream().filter(p -> p.value().equals(entry.getKey())).count();
            assertTrue(count > 0); rate(count, others(r).size(), 1.0 / 3);
        }
        for (var p : others(r)) assertEquals(expected.get(p.value()), value(r.target(p), "specify"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void otherChoiceIsUniformAmongOnlyThoseWithSpecifyQuestions(boolean multiple) throws Exception {
        var f = fixture("<answer/><first/><second/>", bind("answer", multiple ? "select" : "select1", "")
                + bind("first", "string", relevant("answer", "O1")) + bind("second", "string", relevant("answer", "O2")),
                select("answer", multiple, "O0", "No specify", "a", "abc", "O1", "First other", "O2", "Second other")
                        + input("first") + input("second"));
        var r = other(f, 4000, codes("O0", "O2", "O1", "not_offered") + "," + fixed("answer", "a"));
        singleRecords(r, "a", Set.of("O1", "O2"));
        rate(others(r).size(), 2000, .25);
        rate((int) others(r).stream().filter(p -> p.value().equals("O1")).count(), others(r).size(), .5);
        for (var p : others(r)) {
            assertEquals("abc", value(r.target(p), p.value().equals("O1") ? "first" : "second"));
            assertEquals("", value(r.target(p), p.value().equals("O1") ? "second" : "first"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"O", "a O", "DK", "RF", "DK RF"})
    void existingOtherOrNoRealChoiceLeavesTheCleanAnswerAlone(String clean) throws Exception {
        var f = pair(true, "", "", "a", "abc", "O", "Other", "DK", "Unknown", "RF", "Refused");
        var r = other(f, 400, codes("O") + ",\"dontKnowCodes\":[\"DK\"],\"refuseCodes\":[\"RF\"]," + fixed("answer", clean));
        unchanged(r, "answer", clean);
    }

    @Test void specialValuesAloneDoNotExcludeARealChoice() throws Exception {
        var r = other(pair(false, "", "", "97", "abc", "O", "Other"), 800,
                codes("O") + ",\"specialValues\":[\"97\"]," + fixed("answer", "97"));
        singleRecords(r, "97", Set.of("O"));
        rate(others(r).size(), 400, .25);
    }

    @ParameterizedTest @ValueSource(strings = {"096", "96.0", "OTHER", "Autre"})
    void otherCodesMatchExactStringsRatherThanNumbersCaseOrAccents(String real) throws Exception {
        var r = other(pair(false, "", "", real, "abc", "O", "Other"), 800,
                codes("O", "96", "other", "Autré") + "," + fixed("answer", real));
        singleRecords(r, real, Set.of("O"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anEngineRejectedSelectReplacementKeepsItsCleanAnswer(boolean multiple) throws Exception {
        var r = other(pair(multiple, "constraint=\"not(selected(., 'O'))\"", "", "a", "abc", "O", "Other"),
                600, codes("O") + "," + fixed("answer", "a"));
        unchanged(r, "answer", "a");
        for (var s : r.submissions()) assertEquals("", value(s, "specify"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aRejectedDrawDoesNotRetryAnotherOtherChoiceOrFilterItOutBeforeDrawing(boolean multiple) throws Exception {
        var f = fixture("<answer/><first/><second/>",
                bind("answer", multiple ? "select" : "select1", "constraint=\"not(selected(., 'O2'))\"")
                        + bind("first", "string", relevant("answer", "O1")) + bind("second", "string", relevant("answer", "O2")),
                select("answer", multiple, "a", "abc", "O1", "Accepted other", "O2", "Rejected other")
                        + input("first") + input("second"));
        var r = other(f, 4800, codes("O1", "O2") + "," + fixed("answer", "a"));
        singleRecords(r, "a", Set.of("O1"));
        rate(others(r).size(), 2400, .25 * .5);
        for (var p : others(r)) assertEquals("abc", value(r.target(p), "first"));
        for (var s : r.submissions()) assertEquals("", value(s, "second"));
    }

    @Test void eachEligibleAnswerGetsItsOwnProbabilityRatherThanOneReplacementPerInterview() throws Exception {
        var f = fixture("<z/><zs/><a/><as/>", bind("a", "select1", "") + bind("as", "string", relevant("a", "O"))
                        + bind("z", "select1", "") + bind("zs", "string", relevant("z", "O")),
                select("z", false, "x", "abc", "O", "Other") + input("zs")
                        + select("a", false, "x", "xyz", "O", "Other") + input("as"));
        var r = other(f, 3200, codes("O") + "," + fixed("z", "x", "a", "x"));
        rate(others(r).size(), 3200, .25);
        int doubled = 0;
        for (var s : r.submissions()) {
            var ps = others(r).stream().filter(p -> p.submission() == s.number()).toList();
            if (ps.size() == 2) { doubled++; assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList()); }
            for (var p : ps) assertEquals(p.field().equals("z") ? "abc" : "xyz", value(s, p.field().equals("z") ? "zs" : "as"));
        }
        assertTrue(doubled > 0); rate(doubled, 1600, .25 * .25); metadata(r);
    }
}
