package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change JVM time zone")
class InjectedProblemsDuplicateTest {
    @Test void resentDoesNotCopyItsOriginalsWarnings() throws Exception {
        var f = fixture("<impossible/>", bind("impossible", "int", "constraint=\"false()\" required=\"true()\""), input("impossible"));
        var r = run(f, config(2, 1, "").withProblems(only("resent", .5)));
        assertEquals(1, r.plan().problems().size()); assertFalse(r.submissions().get(0).warnings().isEmpty());
        assertTrue(r.submissions().get(1).warnings().isEmpty());
    }
    @Test void resentUploadDelayIsUniformInWholeMinutesBetweenOneAndOneHundredTwenty() throws Exception {
        int draws = 600;
        var r = run(singleTimed(0), config(draws * 2, draws, "").withProblems(only("resent", .5)));
        assertEquals(draws, r.plan().problems().size()); int[] bins = new int[6];
        for (var p : r.plan().problems()) {
            long delay = FieldworkTestSupport.wholeMinutes(r.related(p).submitted(), r.target(p).submitted());
            assertTrue(delay >= 1 && delay <= 120); bins[(int) (delay - 1) / 20]++;
        }
        for (int bin = 0; bin < 6; bin++) assertUniformBin(bins[bin], draws, 1.0 / 6, "resent delay bucket " + bin);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void resentCopiesTheWholeInstanceExceptItsOwnKeyAndHasALaterUpload(boolean id) throws Exception {
        var r = run(ordinary(0, true, true, true), config(40, 20, id ? "\"id\":\"id\"" : "")
                .withProblems(only("resent", .25)));
        assertEquals(10, r.plan().problems().size());
        for (var p : r.plan().problems()) {
            var original = r.related(p); var target = r.target(p);
            assertNotEquals(key(original), key(target)); assertEquals(key(r.clean(target.number())), key(target));
            var originalXml = xml(original); var targetXml = xml(target);
            originalXml.getElementsByTagNameNS("*", "instanceID").item(0).setTextContent("KEY");
            targetXml.getElementsByTagNameNS("*", "instanceID").item(0).setTextContent("KEY");
            assertTrue(originalXml.isEqualNode(targetXml), "only instanceID may change in a resent instance");
            assertEquals(original.start(), target.start()); assertEquals(original.end(), target.end());
            long delay = FieldworkTestSupport.wholeMinutes(original.submitted(), target.submitted());
            assertTrue(delay >= 1 && delay <= 120); assertTrue(target.warnings().isEmpty());
            assertEquals(r.visit(original.number()), r.visit(target.number()));
            assertEquals(id ? "id" : "", p.field()); assertEquals(id ? value(target, "id") : "", p.value());
        }
        assertTrue(records(r, "overlap").isEmpty(), "a copied interval is recorded only as a duplicate");
        assertMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void originalsAreEarlierUntargetedSubmissionsAndAreNeverReused(String name) throws Exception {
        var r = run(ordinary(0, false, false, false), config(80, 40, "\"id\":\"id\"")
                .withProblems(name.equals("resent") ? new ProblemRates(.1, .1, .05, .05, 0, 0, 0)
                        : new ProblemRates(.05, .15, .05, .05, 0, 0, 0)));
        Set<Integer> targets = new HashSet<>(); representatives(r).forEach(p -> targets.add(p.submission()));
        Set<String> originals = new HashSet<>(); int duplicates = 0;
        for (var p : r.plan().problems()) if (p.family().equals("duplicate")) {
            var original = r.related(p); assertTrue(original.number() < p.submission());
            assertFalse(targets.contains(original.number()), "even a later-allocated duration target cannot be an original");
            assertTrue(originals.add(p.relatedKey()), "originals are exclusive across both duplicate types"); duplicates++;
        }
        assertEquals(16, duplicates);
    }
    @Test void aReservedDurationTargetCannotServeAsADuplicateOriginal() throws Exception {
        var r = run(singleTimed(0), config(2, 1, "").withProblems(new ProblemRates(.5, 0, .5, 0, 0, 0, 0)));
        assertEquals(Set.of(1), targets(r, "short")); assertTrue(targets(r, "resent").isEmpty());
        assertWarning(r, "resent", 2); ordinary(r, 2);
    }
    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview"})
    void missingOriginalLeavesAnOrdinaryVisitAndWarnsForEachSkippedTarget(String name) throws Exception {
        var r = run(ordinary(0, false, false, false), config(6, 1, "\"id\":\"id\"").withProblems(only(name, 5.0 / 6)));
        assertEquals(1, targets(r, name).size(), "only submission 1 is available as an original");
        var skipped = new ArrayList<Integer>();
        for (int n = 2; n <= 6; n++) if (!targets(r, name).contains(n)) {
            skipped.add(n); assertWarning(r, name, n); ordinary(r, n);
        }
        assertEquals(4, r.plan().warnings().size());
        for (int i = 0; i < skipped.size(); i++) FieldworkTestSupport.warning(List.of(r.plan().warnings().get(i)), name, skipped.get(i));
    }
    @Test void reinterviewUsesTheOriginalIdButKeepsItsOwnVisitAnswersAndKey() throws Exception {
        var r = run(ordinary(0, true, true, false), config(40, 20, "\"id\":\"id\"").withProblems(only("reinterview", .25)));
        assertEquals(10, r.plan().problems().size());
        for (var p : r.plan().problems()) {
            var target = r.target(p); var original = r.related(p);
            assertEquals(value(original, "id"), value(target, "id")); assertEquals(value(original, "id"), r.visit(target.number()).id());
            assertEquals("id", p.field()); assertEquals(value(target, "id"), p.value());
            assertEquals(((target.number() - 1) % r.config().enumerators()) + 1, r.visit(target.number()).enumerator());
            assertNotEquals(key(original), key(target)); ordinary(r, target.number());
        }
        assertMetadata(r);
    }
    @Test void reinterviewWithoutConfiguredIdWarnsOnceAndInjectsNothing() throws Exception {
        var f = ordinary(0, false, false, false); var c = config(20, 10, "");
        var clean = run(f, c); var r = run(f, c.withProblems(only("reinterview", .25)));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "reinterview");
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void reinterviewWhoseOriginalIdIsBlankIsSkipped() throws Exception {
        var f = fixture("<id/><answer/>", bind("id", "int", "relevant=\"false()\"") + bind("answer", "string", ""), input("id") + input("answer"));
        var r = run(f, config(2, 1, "\"id\":\"id\"").withProblems(only("reinterview", .5)));
        assertEquals("", value(r.submissions().get(0), "id")); assertTrue(r.plan().problems().isEmpty());
        assertWarning(r, "reinterview"); ordinary(r, 2);
    }
    @Test void rejectedOriginalIdKeepsTheUsualAcceptedAnswerAndWarnsAboutTheTarget() throws Exception {
        var f = fixture("<id/>", bind("id", "int", "constraint=\". = if(today() = date('2026-03-02'), 1, 2)\""), input("id"));
        var r = run(f, config(2, 1, "\"id\":\"id\",\"interviewsPerDay\":1,\"fields\":{\"id\":{\"min\":1,\"max\":2}}")
                .withProblems(only("reinterview", .5)));
        assertEquals("1", value(r.submissions().get(0), "id")); assertEquals("2", value(r.submissions().get(1), "id"));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
    }
    @Test void resentDoesNotConsumeAVisitOrDelayItsAssignedEnumeratorsNextInterview() throws Exception {
        var f = singleTimed(600); var base = config(3, 1, "\"interviewsPerDay\":2");
        int checked = 0;
        for (long seed = 40; seed < 65; seed++) {
            var r = run(f, base.withSeed(seed).withProblems(only("resent", 1.0 / 3)));
            if (!targets(r, "resent").equals(Set.of(2))) continue;
            checked++; var previous = r.submissions().get(0); var next = r.submissions().get(2);
            long gap = gap(previous, next); assertTrue(gap >= 10 && gap <= 40);
            assertEquals(0, day(r, 3), "the resent copy must not consume the second interview quota");
            ordinary(r, 3);
        }
        assertTrue(checked > 0, "fixed seed ensemble must exercise a middle resent target");
    }
    @Test void originalChoiceIsUniformAndDependsOnSeedAndTargetNumberNotAnswers() throws Exception {
        int draws = 240; int[] hits = new int[5];
        var c = config(5, 4, "").withProblems(only("resent", .2));
        var small = singleTimed(0); var large = clockSteps(4, 0);
        for (int seed = 0; seed < draws; seed++) {
            var a = run(small, c.withSeed(7000L + seed)); var b = run(large, c.withSeed(7000L + seed));
            assertEquals(1, a.plan().problems().size()); assertEquals(1, b.plan().problems().size());
            int original = a.related(a.plan().problems().get(0)).number(); hits[original]++;
            assertEquals(original, b.related(b.plan().problems().get(0)).number());
        }
        for (int n = 1; n <= 4; n++) assertUniformBin(hits[n], draws, .25, "original " + n);
    }
}
