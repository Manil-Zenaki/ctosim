package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment1InteractionsTest {
    @Test void dontKnowAndAnOutlierCanAffectDifferentAnswersOnTheSameSubmission() throws Exception {
        var f = fixture("<amount/><code/>", bind("amount", "decimal", "constraint=\". &gt;= 0\"")
                + bind("code", "select1", ""), input("amount") + choices("code", false, "ok", "-99"));
        var r = run(f, settings(600, 2, "\"specialValues\":[\"-99\"],\"fields\":{\"code\":{\"value\":\"ok\"}}")
                .withProblems(rates(1, "dontKnow")));
        assertEquals(600, records(r, "outlier").size()); rate(records(r, "dontKnow").size(), 300, .25);
        for (var p : records(r, "dontKnow")) {
            assertEquals("code", p.field()); assertEquals("ok", p.cleanValue()); assertEquals("-99", p.value());
            var other = records(r, "outlier").stream().filter(x -> x.submission() == p.submission()).findFirst().orElseThrow();
            assertEquals("amount", other.field()); assertEquals(value(r.clean(p.submission()), "amount"), other.cleanValue());
        }
        newMetadata(r);
    }
    @Test void faultyEnumeratorsCanCarryOutliersAndRecordsFollowTheAmendedProblemOrder() throws Exception {
        var r = run(number("decimal", ""), settings(40, 2, "").withProblems(rates(1, "tooFast")));
        assertEquals(40, records(r, "outlier").size()); assertEquals(20, records(r, "tooFast").size());
        for (int n : targets(r, "tooFast")) {
            var ps = r.plan().problems().stream().filter(p -> p.submission() == n).toList();
            assertEquals(List.of("outlier", "tooFast"), ps.stream().map(InjectedProblem::problem).toList());
            assertEquals(value(r.clean(n), "amount"), ps.get(0).cleanValue());
            assertEquals(Long.toString(seconds(r.clean(n))), ps.get(1).cleanValue());
        }
        newMetadata(r);
    }
    @Test void tooFastCleanDurationKeepsTheSkippedModulesOfTheSameSubmission() throws Exception {
        var c = settings(300, 2, allFaultSettings()); var f = allFaults();
        var both = run(f, c.withProblems(rates(0, "tooFast", "skipModules")));
        var skipping = run(f, c.withProblems(rates(0, "skipModules")));
        assertFalse(records(both, "skipModules").isEmpty());
        assertEquals(targets(both, "skipModules"), targets(skipping, "skipModules"));
        for (var p : records(both, "tooFast")) {
            assertEquals(Long.toString(seconds(skipping.submissions().get(p.submission() - 1))), p.cleanValue());
        }
    }
    @Test void faultsAndOriginalDurationTargetsCoexistAndFaultRecordsStayAfterDurationRecords() throws Exception {
        var r = run(allFaults(), settings(300, 2, allFaultSettings()).withProblems(
                new ProblemRates(0, 0, 1, 0, 0, 0, 0, 0, List.of("tooFast", "skipModules", "dontKnow"))));
        assertEquals(300, records(r, "short").size());
        for (String name : FAULTS) assertFalse(records(r, name).isEmpty());
        newMetadata(r);
        for (var s : r.submissions()) {
            var ps = r.plan().problems().stream().filter(p -> p.submission() == s.number()).toList();
            assertEquals("short", ps.get(0).problem());
        }
    }
    @Test void resentCopiesKeepFaultyOriginalAnswersAndTimesButHaveOnlyTheirResentRecord() throws Exception {
        var r = run(allFaults(), settings(120, 2, allFaultSettings()).withProblems(
                new ProblemRates(.2, 0, 0, 0, 0, 0, 0, 0, List.of("tooFast", "skipModules", "dontKnow"))));
        assertFalse(records(r, "resent").isEmpty());
        for (var p : records(r, "resent")) {
            var copy = r.target(p); var original = r.related(p);
            assertEquals(List.of(p), r.plan().problems().stream().filter(x -> x.submission() == p.submission()).toList());
            assertEquals(original.start(), copy.start()); assertEquals(original.end(), copy.end());
            assertEquals(original.answered(), copy.answered()); assertTrue(copy.warnings().isEmpty());
            assertEquals(original.instanceXml().replace(key(original), key(copy)), copy.instanceXml());
        }
    }
    @Test void surveyIdNumbersAreProtectedFromDontKnowWhileOtherNumbersAreChanged() throws Exception {
        var f = fixture("<id/><answer/>", bind("id", "int", "") + bind("answer", "int", ""), input("id") + input("answer"));
        var r = run(f, config(800, 2, "\"id\":\"id\",\"specialValues\":[\"-99\"]").withProblems(rates(0, "dontKnow")));
        assertFalse(records(r, "dontKnow").isEmpty()); Set<String> ids = new HashSet<>();
        for (var s : r.submissions()) {
            assertEquals(value(r.clean(s.number()), "id"), value(s, "id")); assertTrue(ids.add(value(s, "id")));
        }
        assertTrue(records(r, "dontKnow").stream().allMatch(p -> p.field().equals("answer")));
    }
    @Test void protectedSurveyIdFiltersAreNotChangedBySkipModules() throws Exception {
        var f = module(3, false, false, "/data/gate = 'yes'", "yes", "no");
        for (int seed = 1; seed <= 30; seed++) {
            var c = settings(2, 2, "\"id\":\"gate\"").withSeed(seed);
            var actual = run(f, c.withProblems(rates(0, "skipModules"))); var clean = run(f, c.withProblems(null));
            assertTrue(actual.plan().problems().isEmpty());
            for (int i = 0; i < 2; i++) assertEquals(value(clean.submissions().get(i), "gate"), value(actual.submissions().get(i), "gate"));
        }
    }
    @Test void warningsForFaultsAreBetweenOriginalProblemWarningsAndOutlierWarnings() throws Exception {
        var r = run(singleTimed(0), settings(4, 2, "").withProblems(
                new ProblemRates(0, .1, 0, 0, 0, 0, 0, .25, List.of("tooFast"))));
        String who = soleEnumerator(r, "tooFast"); var warnings = r.plan().warnings();
        assertEquals(3, warnings.size());
        assertTrue(warnings.get(0).toLowerCase(Locale.ROOT).contains("reinterview"));
        assertTrue(warnings.get(1).contains(who));
        assertTrue(warnings.get(1).matches("(?s).*\\b2\\b.*"));
        // Only one outlier target is requested; its no-eligible-field warning comes last.
        assertTrue(warnings.get(2).matches("(?s).*\\b[1-4]\\b.*"));
    }
    @Test void answerKeySerializesAllNewFamiliesAsStatisticalInSuppliedOrder() {
        var ps = List.of(
                new InjectedProblem(1, "key,一", "enum", "outlier", "outlier", "ipacheckoutliers", "amount", "130", "100", "", false),
                new InjectedProblem(1, "key,一", "enum", "enumerator", "tooFast", "ipacheckenumdb", "", "4", "10", "", false),
                new InjectedProblem(1, "key,一", "enum", "enumerator", "skipModules", "ipacheckenumdb", "gate", "no", "yes", "", false),
                new InjectedProblem(1, "key,一", "enum", "enumerator", "dontKnow", "ipacheckenumdb", "code", "-99", "ok", "", false));
        String csv = AnswerKey.csv(ps);
        assertEquals("KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind\n"
                + "\"key,一\",enum,outlier,outlier,ipacheckoutliers,amount,130,100,,statistical\n"
                + "\"key,一\",enum,enumerator,tooFast,ipacheckenumdb,,4,10,,statistical\n"
                + "\"key,一\",enum,enumerator,skipModules,ipacheckenumdb,gate,no,yes,,statistical\n"
                + "\"key,一\",enum,enumerator,dontKnow,ipacheckenumdb,code,-99,ok,,statistical", csv);
    }
}
