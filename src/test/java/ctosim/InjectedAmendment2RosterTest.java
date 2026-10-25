package ctosim;

import java.util.*;
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
class InjectedAmendment2RosterTest {
    private static final String YES = "\"fields\":{\"more\":{\"value\":\"yes\"}}";

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void countedAndEnumeratorControlledRostersAreShortenedOneQuarterOfTheTime(boolean counted) throws Exception {
        String extra = counted ? "\"fields\":{\"size\":{\"value\":5}}" : "\"repeats\":{\"min\":5,\"max\":5}";
        var r = run(roster(counted, ""), settings(1600, 2, extra).withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 800, .25); int[] bins = new int[4];
        assertEquals(1, enumerators(r, "skipModules").size());
        for (var p : ps) {
            assertEquals(counted ? "size" : "person", p.field()); assertEquals("5", p.cleanValue());
            int count = Integer.parseInt(p.value()); assertTrue(count >= 1 && count < 5); bins[count - 1]++;
            assertEquals(count, count(r.target(p), "person"));
            if (counted) assertEquals(p.value(), value(r.target(p), "size"));
        }
        for (int bin : bins) rate(bin, ps.size(), .25);
        for (var s : r.submissions()) if (!targets(r, "skipModules").contains(s.number())) assertEquals(5, count(s, "person"));
        metadata2(r);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void oneOrSeveralCalculationsConnectAnAddAnotherQuestionToItsCount(boolean indirect) throws Exception {
        String yes = "yes", no = "no";
        var f = extending(indirect, false, items(yes, no));
        var c = settings(1600, 2, "\"fields\":{\"more\":{\"value\":\"" + yes + "\"}}");
        assertEquals(2, count(new FormWalker(f, c).walk(1), "person"), "fixture starts with an extendable ordinary roster");
        assertEquals(1, count(new FormWalker(f, settings(1, 1,
                "\"fields\":{\"more\":{\"value\":\"no\"}}")).walk(1), "person"), "fixture stops when answered no");
        var r = run(f, c.withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 800, .25);
        assertEquals(ps.size(), targets(r, "skipModules").size(), "one record per shortened roster, never one per answer");
        for (var p : ps) {
            assertEquals("person", p.field()); assertEquals("1", p.value()); assertEquals("2", p.cleanValue());
            assertEquals(1, count(r.target(p), "person")); assertEquals(no, value(r.target(p), "person[1]/more"));
        }
        for (var s : r.submissions()) if (!targets(r, "skipModules").contains(s.number())) {
            assertEquals(2, count(s, "person")); assertEquals(yes, value(s, "person[1]/more"));
        }
        metadata2(r);
    }

    @Test void successiveAddAnotherDecisionsStopTheRosterAndReportItsFinalCounterfactualCount() throws Exception {
        var f = extending(true, true, items("yes", "no"));
        assertEquals(4, count(new FormWalker(f, settings(1, 1, YES)).walk(1), "person"));
        assertEquals(1, count(new FormWalker(f, settings(1, 1,
                "\"fields\":{\"more\":{\"value\":\"no\"}}")).walk(1), "person"));
        var r = run(f, settings(1600, 2, YES).withProblems(rates(0, "skipModules")));
        String faulty = soleEnumerator(r, "skipModules"); int[] counts = new int[4];
        for (var s : interviews(r, faulty)) counts[count(s, "person") - 1]++;
        for (int i = 0; i < 3; i++) rate(counts[i], 800, .25 * Math.pow(.75, i));
        rate(counts[3], 800, Math.pow(.75, 3));
        var ps = records(r, "skipModules"); assertEquals(counts[0] + counts[1] + counts[2], ps.size());
        assertEquals(ps.size(), targets(r, "skipModules").size());
        for (var p : ps) {
            assertEquals("person", p.field()); assertEquals("4", p.cleanValue());
            int n = count(r.target(p), "person"); assertEquals(Integer.toString(n), p.value());
            assertEquals("no", value(r.target(p), "person[" + n + "]/more"));
            assertEquals(4, count(r.clean(p.submission()), "person"));
        }
    }

    @Test void replacementChoicesMinimizeCountAreUniformAndNeverUseSpecialValues() throws Exception {
        var f = extending(true, false, items("yes", "no", "later", "-99"));
        var r = run(f, settings(2000, 2, YES + ",\"specialValues\":[\"-99\"]").withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 1000, .25); int no = 0;
        for (var p : ps) {
            String answer = value(r.target(p), "person[1]/more");
            assertTrue(Set.of("no", "later").contains(answer)); if (answer.equals("no")) no++;
            assertEquals("1", p.value());
        }
        rate(no, ps.size(), .5);
    }

    @Test void alreadyMinimalOrdinaryChoiceIsKeptWithoutAProblem() throws Exception {
        var r = run(extending(true, false, items("yes", "no")), settings(100, 2,
                "\"fields\":{\"more\":{\"value\":\"no\"}}").withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) {
            assertEquals(count(r.clean(s.number()), "person"), count(s, "person"));
            assertEquals("no", value(s, "person[1]/more"));
        }
    }

    @Test void aQuestionInsideARepeatThatDoesNotAffectItsCountIsNotAnExtender() throws Exception {
        var f = loadXml(text2("add-another-template").replace("__COUNT__", "/data/total")
                .replace("__BINDS__", bind("total", "int", "calculate=\"2\""))
                .replace("__CHOICES__", items("yes", "no")));
        var r = run(f, settings(100, 2, YES).withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) {
            assertEquals(2, count(s, "person"));
            for (int i = 1; i <= 2; i++) assertEquals("yes", value(s, "person[" + i + "]/more"));
        }
    }

    @Test void offeredChoicesWithTheSameCountDoNotTriggerShortening() throws Exception {
        var f = extending(true, false, items("no", "later"));
        var r = run(f, settings(100, 2, "\"fields\":{\"more\":{\"value\":\"later\"}}")
                .withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("later", value(s, "person[1]/more"));
    }

    @Test void sameSeedReplaysRosterDecisionsAndNewSeedsCanChangeThem() throws Exception {
        var f = extending(true, true, items("yes", "no")); var c = settings(100, 2, YES).withProblems(rates(0, "skipModules"));
        var r = run(f, c); assertFalse(r.plan().problems().isEmpty()); replay2(r);
        Set<Set<Integer>> selections = new HashSet<>();
        for (int seed = 1; seed <= 8; seed++) selections.add(targets(run(f, c.withSeed(seed)), "skipModules"));
        assertTrue(selections.size() > 1);
    }

    @Test void replacementChoosesTheFewestInstancesRatherThanJustAnySmallerCount() throws Exception {
        var f = extending(false, 3, "if(../more = 'yes', 2, if(../more = 'middle', 1, 0))",
                items("yes", "middle", "no"));
        assertEquals(3, count(new FormWalker(f, settings(1, 1, YES)).walk(1), "person"));
        assertEquals(1, count(new FormWalker(f, settings(1, 1,
                "\"fields\":{\"more\":{\"value\":\"no\"}}")).walk(1), "person"));
        var r = run(f, settings(1200, 2, YES).withProblems(rates(0, "skipModules")));
        rate(records(r, "skipModules").size(), 600, .25);
        for (var p : records(r, "skipModules")) {
            assertEquals("person", p.field()); assertEquals("3", p.cleanValue()); assertEquals("1", p.value());
            assertEquals("no", value(r.target(p), "person[1]/more"));
        }
    }

    @Test void twoShortenedAddAnotherRostersGetSeparateRecordsInFormOrder() throws Exception {
        String nodes = "", binds = "", body = "";
        for (String name : List.of("z", "a")) {
            nodes += "<total_" + name + "/><roster_" + name + " jr:template=\"\"><more_" + name + "/><flag/></roster_" + name + ">";
            binds += bind("total_" + name, "int", "calculate=\"min(sum(/data/roster_" + name + "/flag) + 1, 2)\"")
                    + bind("roster_" + name + "/flag", "int", "calculate=\"if(../more_" + name + " = 'yes', 1, 0)\"")
                    + bind("roster_" + name + "/more_" + name, "select1", "");
            body += "<repeat nodeset=\"/data/roster_" + name + "\" jr:count=\"/data/total_" + name + "\">"
                    + choices("roster_" + name + "/more_" + name, false, "yes", "no") + "</repeat>";
        }
        var r = run(fixture(nodes, binds, body), settings(800, 2,
                "\"fields\":{\"more_z\":{\"value\":\"yes\"},\"more_a\":{\"value\":\"yes\"}}")
                .withProblems(rates(0, "skipModules")));
        int pairs = 0;
        for (var s : r.submissions()) {
            var ps = records(r, "skipModules").stream().filter(p -> p.submission() == s.number()).toList();
            assertEquals(ps.size(), ps.stream().map(InjectedProblem::field).distinct().count());
            if (ps.size() == 2) {
                pairs++; assertEquals(List.of("roster_z", "roster_a"), ps.stream().map(InjectedProblem::field).toList());
            }
            for (var p : ps) {
                assertEquals("2", p.cleanValue()); assertEquals("1", p.value()); assertEquals(1, count(s, p.field()));
            }
        }
        assertTrue(pairs > 0, "exercise both rosters shortened on the same interview"); metadata2(r);
    }
}
