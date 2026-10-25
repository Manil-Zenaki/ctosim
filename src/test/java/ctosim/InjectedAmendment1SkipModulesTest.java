package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment1SkipModulesTest {
    private static final String YES = "\"fields\":{\"gate\":{\"value\":\"yes\"}},\"specialValues\":[\"-99\"]";

    @ParameterizedTest @ValueSource(ints = {2, 3, 4})
    void aGroupMustContainAtLeastThreeQuestionsToBeAModule(int size) throws Exception {
        var f = module(size, false, false, "/data/gate = 'yes'", "yes", "no");
        var r = run(f, settings(800, 2, YES).withProblems(rates(0, "skipModules")));
        if (size == 2) { assertTrue(r.plan().problems().isEmpty()); return; }
        assertEquals(1, enumerators(r, "skipModules").size()); rate(records(r, "skipModules").size(), 400, .5);
        for (var p : records(r, "skipModules")) {
            assertEquals("gate", p.field()); assertEquals("yes", p.cleanValue()); assertEquals("no", p.value());
            for (int i = 0; i < size; i++) assertEquals("", value(r.target(p), "module/q" + i));
        }
        newMetadata(r);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedQuestionsCountAndBothGroupsAndRepeatsCanBeControlled(boolean repeat) throws Exception {
        var r = run(module(3, true, repeat, "/data/gate = 'yes'", "yes", "no"),
                settings(400, 2, YES + ",\"repeats\":{\"min\":1,\"max\":1}").withProblems(rates(0, "skipModules")));
        rate(records(r, "skipModules").size(), 200, .5);
        for (var p : records(r, "skipModules")) { assertEquals("gate", p.field()); assertEquals("no", p.value()); }
    }
    @Test void replacementsAreUniformAmongOfferedClosingChoicesAndExcludeSpecialValues() throws Exception {
        var r = run(module(3, false, false, "/data/gate = 'yes'", "yes", "no", "later", "-99"),
                settings(1600, 2, YES).withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 800, .5);
        assertTrue(ps.stream().allMatch(p -> Set.of("no", "later").contains(p.value())));
        rate((int) ps.stream().filter(p -> p.value().equals("no")).count(), ps.size(), .5);
    }
    @Test void noOpenModuleMeansNoReplacement() throws Exception {
        var r = run(module(3, false, false, "/data/gate = 'yes'", "yes", "no"),
                settings(100, 2, "\"fields\":{\"gate\":{\"value\":\"no\"}}").withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("no", value(s, "gate"));
    }
    @Test void aSpecialValueAsTheOnlyClosingChoiceCannotBeUsed() throws Exception {
        var r = run(module(3, false, false, "/data/gate = 'yes'", "yes", "-99"),
                settings(100, 2, YES).withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("yes", value(s, "gate"));
    }
    @Test void aNumericFilterControllingAGroupIsNotASelectOneController() throws Exception {
        var f = fixture("<gate/><module><a/><b/><c/></module>", bind("gate", "int", "")
                + "<bind nodeset=\"/data/module\" relevant=\"/data/gate = 1\"/>"
                + bind("module/a", "string", "") + bind("module/b", "string", "") + bind("module/c", "string", ""),
                input("gate") + "<group ref=\"/data/module\">" + input("module/a") + input("module/b") + input("module/c") + "</group>");
        var r = run(f, settings(100, 2, "\"fields\":{\"gate\":{\"value\":1}}").withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("1", value(s, "gate"));
    }
    @Test void aChoiceMustCloseEveryCurrentlyOpenControlledModule() throws Exception {
        String nodes = "<gate/>", binds = bind("gate", "select1", ""), body = choices("gate", false, "both", "left", "right", "neither");
        for (String name : List.of("left", "right")) {
            nodes += "<" + name + "><a/><b/><c/></" + name + ">";
            binds += "<bind nodeset=\"/data/" + name + "\" relevant=\"/data/gate = 'both' or /data/gate = '" + name + "'\"/>";
            body += "<group ref=\"/data/" + name + "\">";
            for (String q : List.of("a", "b", "c")) { binds += bind(name + "/" + q, "string", ""); body += input(name + "/" + q); }
            body += "</group>";
        }
        var r = run(fixture(nodes, binds, body), settings(400, 2, "\"fields\":{\"gate\":{\"value\":\"both\"}}")
                .withProblems(rates(0, "skipModules")));
        rate(records(r, "skipModules").size(), 200, .5);
        for (var p : records(r, "skipModules")) { assertEquals("both", p.cleanValue()); assertEquals("neither", p.value()); }
    }
    @Test void noChoiceClosingAllOpenModulesKeepsTheOrdinaryAnswer() throws Exception {
        String nodes = "<gate/>", binds = bind("gate", "select1", ""), body = choices("gate", false, "both", "left", "right");
        for (String name : List.of("left", "right")) {
            nodes += "<" + name + "><a/><b/><c/></" + name + ">";
            binds += "<bind nodeset=\"/data/" + name + "\" relevant=\"/data/gate = 'both' or /data/gate = '" + name + "'\"/>";
            body += "<group ref=\"/data/" + name + "\">";
            for (String q : List.of("a", "b", "c")) { binds += bind(name + "/" + q, "string", ""); body += input(name + "/" + q); }
            body += "</group>";
        }
        var r = run(fixture(nodes, binds, body), settings(100, 2, "\"fields\":{\"gate\":{\"value\":\"both\"}}")
                .withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("both", value(s, "gate"));
    }
    @Test void aReplacementMayOpenAnotherModuleProvidedItClosesAllOriginallyOpenModules() throws Exception {
        String nodes = "<gate/>", binds = bind("gate", "select1", ""), body = choices("gate", false, "left", "right");
        for (String name : List.of("left", "right")) {
            nodes += "<" + name + "><a/><b/><c/></" + name + ">";
            binds += "<bind nodeset=\"/data/" + name + "\" relevant=\"/data/gate = '" + name + "'\"/>";
            body += "<group ref=\"/data/" + name + "\">";
            for (String q : List.of("a", "b", "c")) { binds += bind(name + "/" + q, "string", ""); body += input(name + "/" + q); }
            body += "</group>";
        }
        var r = run(fixture(nodes, binds, body), settings(400, 2, "\"fields\":{\"gate\":{\"value\":\"left\"}}")
                .withProblems(rates(0, "skipModules")));
        rate(records(r, "skipModules").size(), 200, .5);
        for (var p : records(r, "skipModules")) {
            assertEquals("left", p.cleanValue()); assertEquals("right", p.value());
            assertEquals("", value(r.target(p), "left/a")); assertFalse(value(r.target(p), "right/a").isEmpty());
        }
    }
    @Test void moduleRelevanceIsEvaluatedWithOtherCurrentAnswers() throws Exception {
        // Even though gate occurs in the expression, lock=0 makes every offered choice irrelevant.
        var f = fixture("<lock>0</lock><gate/><module><a/><b/><c/></module>",
                bind("lock", "int", "readonly=\"true()\"") + bind("gate", "select1", "")
                + "<bind nodeset=\"/data/module\" relevant=\"/data/lock = 1 and /data/gate = 'yes'\"/>"
                + bind("module/a", "string", "") + bind("module/b", "string", "") + bind("module/c", "string", ""),
                choices("gate", false, "yes", "no") + "<group ref=\"/data/module\">"
                + input("module/a") + input("module/b") + input("module/c") + "</group>");
        var r = run(f, settings(100, 2, YES).withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rostersAreShortenedOneQuarterOfTheTimeUniformlyBelowTheOrdinaryCount(boolean counted) throws Exception {
        String extra = counted ? "\"fields\":{\"size\":{\"value\":5}}" : "\"repeats\":{\"min\":5,\"max\":5}";
        var r = run(roster(counted, ""), settings(1200, 2, extra).withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 600, .25); int[] bins = new int[4];
        for (var p : ps) {
            assertEquals(counted ? "size" : "person", p.field()); assertEquals("5", p.cleanValue());
            int n = Integer.parseInt(p.value()); assertTrue(n >= 1 && n <= 4); bins[n - 1]++;
            assertEquals(n, count(r.target(p), "person"));
            if (counted) assertEquals(p.value(), value(r.target(p), "size"));
        }
        for (int bin : bins) rate(bin, ps.size(), .25);
        for (var s : r.submissions()) if (!targets(r, "skipModules").contains(s.number())) assertEquals(5, count(s, "person"));
    }
    @Test void countLowerBoundUsesTheSmallestAllowedWholeNumber() throws Exception {
        var r = run(roster(true, "constraint=\". &gt; 2.2\""), settings(800, 2,
                "\"fields\":{\"size\":{\"value\":5}}").withProblems(rates(0, "skipModules")));
        var ps = records(r, "skipModules"); rate(ps.size(), 400, .25);
        for (var p : ps) assertTrue(Set.of("3", "4").contains(p.value()));
        rate((int) ps.stream().filter(p -> p.value().equals("3")).count(), ps.size(), .5);
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 3})
    void aCountAtOrBelowItsLowerBoundIsKept(int initial) throws Exception {
        String attrs = initial == 3 ? "constraint=\". &gt;= 3\"" : "";
        var r = run(roster(true, attrs), settings(60, 2, "\"fields\":{\"size\":{\"value\":" + initial + "}}")
                .withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals(initial, count(s, "person"));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void anUncountedRosterAlreadyAtZeroOrOneIsNotShortened(int initial) throws Exception {
        var r = run(roster(false, ""), settings(60, 2, "\"repeats\":{\"min\":" + initial + ",\"max\":" + initial + "}")
                .withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals(initial, count(s, "person"));
    }
    @Test void anEngineRejectedShorterCountKeepsTheOriginalAndProducesNoRecord() throws Exception {
        var r = run(roster(true, "constraint=\". * 2 = 10\""), settings(100, 2,
                "\"fields\":{\"size\":{\"value\":5}}").withProblems(rates(0, "skipModules")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) { assertEquals("5", value(s, "size")); assertEquals(5, count(s, "person")); }
    }
    @Test void multipleRosterReplacementsAreRecordedInFormOrder() throws Exception {
        String nodes = "<z/><rz jr:template=\"\"><name/></rz><a/><ra jr:template=\"\"><name/></ra>";
        String binds = bind("a", "int", "") + bind("z", "int", "")
                + bind("rz/name", "string", "") + bind("ra/name", "string", "");
        String body = input("z") + "<repeat nodeset=\"/data/rz\" jr:count=\"/data/z\">" + input("rz/name") + "</repeat>"
                + input("a") + "<repeat nodeset=\"/data/ra\" jr:count=\"/data/a\">" + input("ra/name") + "</repeat>";
        var r = run(fixture(nodes, binds, body), settings(400, 2,
                "\"fields\":{\"z\":{\"value\":3},\"a\":{\"value\":3}}").withProblems(rates(0, "skipModules")));
        int pairs = 0;
        for (var s : r.submissions()) {
            var ps = records(r, "skipModules").stream().filter(p -> p.submission() == s.number()).toList();
            if (ps.size() == 2) { pairs++; assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList()); }
            for (var p : ps) {
                assertEquals("3", p.cleanValue()); assertEquals(p.value(), value(s, p.field()));
                assertEquals(Integer.parseInt(p.value()), count(s, p.field().equals("z") ? "rz" : "ra"));
            }
        }
        assertTrue(pairs > 0, "exercise two replaced counts in the same submission");
    }
}
