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
class InjectedAmendment2RefusalTest {
    private static final String FIXED = "\"fields\":{\"gate\":{\"value\":\"yes\"},\"code\":{\"value\":\"ok\"}},\"specialValues\":[\"-99\"]";

    @ParameterizedTest @ValueSource(doubles = {.9, .75, .5, .2})
    void onlyWouldConsentInterviewsAreConvertedAtTheSpecifiedConditionalRate(double probability) throws Exception {
        var f = resource2("consent"); var c = settings(1600, 2, consent(probability));
        var clean = run(f, c); var r = run(f, c.withProblems(rates(0, "fakeRefusal")));
        var ps = records(r, "fakeRefusal"); String faulty = soleEnumerator(r, "fakeRefusal");
        Set<Integer> changed = targets(r, "fakeRefusal");
        int eligible = 0, faultyConsents = 0, otherConsents = 0;
        for (var s : r.submissions()) {
            boolean assigned = value(s, "username").equals(faulty);
            String before = value(clean.submissions().get(s.number() - 1), "agree");
            String after = value(s, "agree");
            assertEquals("oui", value(s, "understand"), "only the last configured consent field refuses");
            if (assigned && before.equals("oui")) eligible++;
            if (after.equals("oui")) { if (assigned) faultyConsents++; else otherConsents++; }
            if (!assigned || before.equals("non")) assertEquals(before, after, "natural refusals and clean enumerators are untouched");
            assertEquals(!before.equals(after), changed.contains(s.number()), "one truthful record per extra refusal");
            if (after.equals("non")) for (String field : List.of("a", "b", "c")) assertEquals("", value(s, "interview/" + field));
        }
        double replacement = Math.min(1, (1 - probability) / probability);
        if (replacement == 1) assertEquals(eligible, ps.size()); else rate(ps.size(), eligible, replacement);
        rate(faultyConsents, 800, Math.max(0, 2 * probability - 1)); rate(otherConsents, 800, probability);
        for (var p : ps) { assertEquals("agree", p.field()); assertEquals("non", p.value()); assertEquals("oui", p.cleanValue()); }
        metadata2(r);
    }

    @ParameterizedTest @ValueSource(doubles = {0, 1})
    void zeroAndCertainConsentProduceNoInventedRefusalRecords(double probability) throws Exception {
        var f = resource2("consent"); var c = settings(30, 2, consent(probability));
        var r = run(f, c.withProblems(rates(0, "fakeRefusal")));
        assertTrue(records(r, "fakeRefusal").isEmpty());
        for (var s : r.submissions()) {
            assertEquals("oui", value(s, "understand")); assertEquals(probability == 0 ? "non" : "oui", value(s, "agree"));
        }
    }

    @Test void withoutConsentTheFaultWarnsOnceAndDoesNotConsumeAnEnumeratorSlot() throws Exception {
        var f = resource2("consent"); var c = settings(40, 2, FIXED);
        var control = run(f, c.withProblems(rates(0, "tooFast")));
        var r = run(f, c.withProblems(rates(0, "tooFast", "fakeRefusal")));
        assertEquals(control.plan().problems(), r.plan().problems());
        assertEquals(control.plan().visits(), r.plan().visits());
        for (int i = 0; i < 40; i++) FieldworkTestSupport.same(control.submissions().get(i), r.submissions().get(i));
        assertEquals(1, r.plan().warnings().size());
        assertTrue(r.plan().warnings().get(0).contains("fakeRefusal"));
    }

    @Test void fakeRefusalAloneWithoutConsentDoesNotGuessConsentFromTheForm() throws Exception {
        var f = resource2("consent"); var c = settings(30, 2, FIXED);
        var clean = run(f, c); var r = run(f, c.withProblems(rates(0, "fakeRefusal")));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size());
        assertTrue(r.plan().warnings().get(0).contains("fakeRefusal"));
        for (int i = 0; i < 30; i++) FieldworkTestSupport.same(clean.submissions().get(i), r.submissions().get(i));
    }

    @Test void oneEnumeratorCannotBeAssignedFakeRefusal() throws Exception {
        var r = run(resource2("consent"), settings(60, 1, consent(.5)).withProblems(rates(0, "fakeRefusal")));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size());
        for (var s : r.submissions()) assertEquals(value(r.clean(s.number()), "agree"), value(s, "agree"));
    }

    @ParameterizedTest @ValueSource(ints = {2, 4, 6, 8})
    void fourthFaultFollowsCanonicalAssignmentAndWrapsAfterTheCleanHalf(int e) throws Exception {
        var r = run(resource2("consent"), settings(e * 120, e, consent(.5) + "," + FIXED)
                .withProblems(rates(0, "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        List<String> names = new ArrayList<>();
        for (String fault : List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal")) names.add(soleEnumerator(r, fault));
        int m = Math.min(4, e / 2); assertEquals(m, new HashSet<>(names).size());
        for (int i = m; i < 4; i++) assertEquals(names.get(i % m), names.get(i));
        if (m < 4) { assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 8); }
        else assertTrue(r.plan().warnings().isEmpty());
        metadata2(r);
    }

    @Test void configuredOrderDoesNotChangeAssignmentAndFakeRefusalRecordsComeLast() throws Exception {
        var f = resource2("consent"); var c = settings(500, 2, consent(.5) + "," + FIXED);
        var a = run(f, c.withProblems(rates(0, "fakeRefusal", "dontKnow", "tooFast", "skipModules")));
        var b = run(f, c.withProblems(rates(0, "tooFast", "skipModules", "dontKnow", "fakeRefusal")));
        assertEquals(a.plan().problems(), b.plan().problems());
        assertTrue(a.plan().problems().stream().anyMatch(p -> p.problem().equals("fakeRefusal")));
        int shared = 0;
        for (var p : records(a, "fakeRefusal")) {
            var ps = a.plan().problems().stream().filter(q -> q.submission() == p.submission()).toList();
            assertEquals("fakeRefusal", ps.get(ps.size() - 1).problem()); if (ps.size() > 2) shared++;
        }
        assertTrue(shared > 0); metadata2(a); replay2(a);
    }

    @ParameterizedTest @ValueSource(doubles = {0, .5, 1})
    void skipModulesLeavesEveryConsentFieldAloneButStillClosesOtherModulesHalfTheTime(double probability) throws Exception {
        var f = resource2("consent"); var c = settings(1200, 2, consent(probability) + "," + FIXED);
        var clean = run(f, c); var r = run(f, c.withProblems(rates(0, "skipModules")));
        rate(records(r, "skipModules").size(), 600, .5);
        for (var p : records(r, "skipModules")) assertEquals("gate", p.field());
        for (var s : r.submissions()) for (String field : List.of("understand", "agree"))
            assertEquals(value(clean.submissions().get(s.number() - 1), field), value(s, field));
    }

    @Test void refusalDrawsDoNotDependOnEarlierOrdinaryAnswerDraws() throws Exception {
        String original = text2("consent");
        String noisy = original.replace("<understand/>", "<noise/><understand/>")
                .replace("<h:body>", "<h:body>" + input("noise"))
                .replace("</model>", bind("noise", "string", "") + "</model>");
        var c = settings(200, 2, consent(.75)).withProblems(rates(0, "fakeRefusal"));
        var a = run(loadXml(original), c); var b = run(loadXml(noisy), c);
        assertFalse(records(a, "fakeRefusal").isEmpty());
        assertEquals(targets(a, "fakeRefusal"), targets(b, "fakeRefusal"));
        assertEquals(enumerators(a, "fakeRefusal"), enumerators(b, "fakeRefusal"));
    }

    @Test void resentCopiesHaveOnlyResentRecordsEvenWhenOriginalConsentWasFalsified() throws Exception {
        var r = run(resource2("consent"), settings(600, 2, consent(.5))
                .withProblems(new ProblemRates(.3, 0, 0, 0, 0, 0, 0, 0, List.of("fakeRefusal"))));
        int copiedFaults = 0;
        for (var p : records(r, "resent")) {
            var source = r.related(p); var copy = r.target(p);
            assertEquals(value(source, "agree"), value(copy, "agree"));
            assertEquals(1, r.plan().problems().stream().filter(q -> q.submission() == p.submission()).count());
            if (targets(r, "fakeRefusal").contains(source.number())) copiedFaults++;
        }
        assertTrue(copiedFaults > 0, "exercise a resent copy of an actual fake refusal");
    }

    @Test void missingConsentWarningSitsBetweenOriginalProblemAndOutlierWarnings() throws Exception {
        var r = run(singleTimed(0), settings(2, 2, "").withProblems(
                new ProblemRates(0, .5, 0, 0, 0, 0, 0, .5, List.of("fakeRefusal"))));
        var warnings = r.plan().warnings();
        // Reinterview has no target above E and no configured id: do not impose the number of its warnings.
        int fake = -1, lastOriginal = -1;
        for (int i = 0; i < warnings.size(); i++) {
            if (warnings.get(i).contains("reinterview")) lastOriginal = i;
            if (warnings.get(i).contains("fakeRefusal")) { assertEquals(-1, fake); fake = i; }
        }
        assertTrue(lastOriginal >= 0); assertTrue(fake > lastOriginal);
        assertTrue(fake < warnings.size() - 1, "failed outlier warning follows fault warnings");
    }

    @Test void customUnicodeConsentValuesArePreservedExactlyInTheAnswerKey() throws Exception {
        String xml = text2("consent").replace("oui", "accepté").replace("non", "refusé");
        String setting = consent(.5).replace("oui", "accepté").replace("non", "refusé");
        var r = run(loadXml(xml), settings(200, 2, setting).withProblems(rates(0, "fakeRefusal")));
        assertFalse(records(r, "fakeRefusal").isEmpty());
        for (var p : records(r, "fakeRefusal")) {
            assertEquals("accepté", p.cleanValue()); assertEquals("refusé", p.value());
            assertEquals("refusé", value(r.target(p), "agree"));
        }
        assertTrue(AnswerKey.csv(r.plan().problems()).contains("refusé"));
    }
}
