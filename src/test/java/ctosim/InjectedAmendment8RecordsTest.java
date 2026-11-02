package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment6Support.sameResults;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Exercises locale/time-zone independence; walks change the JVM time zone")
class InjectedAmendment8RecordsTest {
    @Test void cleanValueIsTheActualOrdinaryDrawAndItsReplacedLabelIsUsed() throws Exception {
        var r = other(simple(false), 1000, codes("O") + ",\"otherRate\":0");
        assertFalse(others(r).isEmpty());
        Map<Integer, InjectedProblem> changed = new HashMap<>();
        others(r).forEach(p -> changed.put(p.submission(), p));
        Set<String> labels = new HashSet<>();
        for (var s : r.submissions()) {
            String clean = value(r.clean(s.number()), "answer");
            if (changed.containsKey(s.number())) {
                var p = changed.get(s.number());
                assertEquals(clean, p.cleanValue()); assertEquals("O", p.value());
                String label = clean.equals("a") ? "abc" : "xyz";
                assertEquals(label, value(s, "specify")); labels.add(label);
            } else assertEquals(clean, value(s, "answer"));
        }
        assertEquals(Set.of("abc", "xyz"), labels); metadata(r);
    }

    @Test void answerKeyContainsOnlyTheSelectRecordWithTheNewCheckAndStatisticalKind() throws Exception {
        var r = other(simple(false), 200, codes("O") + "," + fixed("answer", "a"));
        assertFalse(others(r).isEmpty());
        var rows = AnswerKey.csv(r.plan().problems()).lines().toList();
        assertEquals("KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind", rows.get(0));
        assertEquals(others(r).size() + 1, rows.size());
        for (int i = 0; i < others(r).size(); i++) {
            var p = others(r).get(i);
            assertEquals(String.join(",", p.key(), p.enumerator(), "enumerator", "other", "ipacheckspecify",
                    "answer", "O", "a", "", "statistical"), rows.get(i + 1));
        }
    }

    @Test void repeatedAndFreshWalksReproduceSubmissionsSpecifyTyposVisitsWarningsAndRecords() throws Exception {
        var f = pair(false, "", "", "a", "ÉLÉPHANT", "O", "Other");
        var c = settings(200, 2, codes("O") + "," + fixed("answer", "a")).withProblems(rates(0, "other"));
        var plan = new Fieldwork(f, c); assertTrue(plan.problems().isEmpty());
        var first = plan.walkAll(); var problems = List.copyOf(plan.problems());
        var visits = List.copyOf(plan.visits()); var warnings = List.copyOf(plan.warnings());
        assertFalse(problems.isEmpty());
        FieldworkTestSupport.sameRun(first, plan.walkAll());
        assertEquals(problems, plan.problems()); assertEquals(visits, plan.visits()); assertEquals(warnings, plan.warnings());
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().clear());
        var fresh = run(f, c); FieldworkTestSupport.sameRun(first, fresh.submissions());
        assertEquals(problems, fresh.plan().problems()); assertEquals(visits, fresh.plan().visits()); assertEquals(warnings, fresh.plan().warnings());
        assertEquals(AnswerKey.csv(problems), AnswerKey.csv(fresh.plan().problems()));
    }

    @Test void differentSeedsGiveMoreThanOneReplacementPattern() throws Exception {
        var f = simple(false); var c = settings(200, 2, codes("O") + "," + fixed("answer", "a")).withProblems(rates(0, "other"));
        Set<Set<Integer>> patterns = new HashSet<>();
        for (int seed = 21; seed <= 24; seed++) {
            var r = run(f, c.withSeed(seed)); assertFalse(others(r).isEmpty()); patterns.add(targets(r, "other"));
        }
        assertTrue(patterns.size() > 1);
    }

    @Test void labelLowercasingAndRandomnessIgnoreMachineLocaleAndTimeZone() throws Exception {
        Locale locale = Locale.getDefault(), display = Locale.getDefault(Locale.Category.DISPLAY), format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            var f = pair(false, "", "", "a", "  I É  ", "O", "Other");
            var c = settings(200, 2, codes("O") + "," + fixed("answer", "a")).withProblems(rates(0, "other"));
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var first = run(f, c); assertFalse(others(first).isEmpty());
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var second = run(f, c); sameResults(first, second);
            assertEquals(first.plan().warnings(), second.plan().warnings());
            for (var p : others(second)) assertEquals("i e", value(second.target(p), "specify"));
        } finally {
            Locale.setDefault(locale); Locale.setDefault(Locale.Category.DISPLAY, display); Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }
}
