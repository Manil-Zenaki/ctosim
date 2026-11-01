package ctosim;

import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment7Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Exercises locale/time-zone independence; walks change the JVM time zone")
class InjectedAmendment7RecordsTest {
    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void cleanValuesAreTheActualGeneratedTextReplacedAndRecordsFollowFormOrder(String fault) throws Exception {
        var f = fixture("<z/><a/>", bind("a", "string", "") + bind("z", "string", ""), input("z") + input("a"));
        var r = textRun(fault, f, 800, teamCodes(fault, "TEAM")
                + ",\"fields\":{\"z\":{\"texts\":[\"Z one\",\"Z two\"]},\"a\":{\"texts\":[\"A one\",\"A two\"]}}");
        int pairs = 0;
        Set<String> cleanTexts = new HashSet<>();
        for (var s : r.submissions()) {
            var ps = records(r, fault).stream().filter(p -> p.submission() == s.number()).toList();
            var byField = ps.stream().collect(Collectors.toMap(InjectedProblem::field, p -> p));
            if (ps.size() == 2) {
                pairs++;
                assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList());
            }
            var clean = r.clean(s.number());
            for (String field : List.of("z", "a")) {
                if (byField.containsKey(field)) {
                    assertEquals(value(clean, field), byField.get(field).cleanValue());
                    assertEquals("TEAM", byField.get(field).value());
                    cleanTexts.add(byField.get(field).cleanValue());
                } else assertEquals(value(clean, field), value(s, field), "ordinary answer draws remain independent");
            }
        }
        assertTrue(pairs > 0);
        assertEquals(Set.of("Z one", "Z two", "A one", "A two"), cleanTexts);
        for (String field : List.of("z", "a"))
            rate((int) records(r, fault).stream().filter(p -> p.field().equals(field)).count(), 400, .25);
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void answerKeyQuotesTextCodesAndTheReplacedTextWithoutLosingUnicode(String fault) throws Exception {
        String code = "拒答,\"unknown\"";
        String clean = "Name, \"ordinary\"";
        var r = textRun(fault, textQuestion(""), 120, teamCodes(fault, code) + "," + fixedText("answer", clean));
        textRecords(r, fault, "answer", clean, List.of(code));
        var rows = AnswerKey.csv(r.plan().problems()).lines().toList();
        assertEquals("KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind", rows.get(0));
        assertEquals(r.plan().problems().size() + 1, rows.size());
        for (int i = 0; i < r.plan().problems().size(); i++) {
            var p = r.plan().problems().get(i);
            assertEquals(String.join(",", p.key(), p.enumerator(), "enumerator", fault, "ipacheckenumdb", "answer",
                    "\"拒答,\"\"unknown\"\"\"", "\"Name, \"\"ordinary\"\"\"", "", "statistical"), rows.get(i + 1));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void repeatedAndFreshWalksReproduceTextSubmissionsRecordsWarningsAndVisits(String fault) throws Exception {
        var f = textQuestion("");
        var c = settings(160, 2, teamCodes(fault, "-999", "拒答") + "," + fixedText("answer", "ordinary"))
                .withProblems(rates(0, fault));
        var plan = new Fieldwork(f, c);
        assertTrue(plan.problems().isEmpty());
        var first = plan.walkAll();
        var problems = List.copyOf(plan.problems());
        var warnings = List.copyOf(plan.warnings());
        var visits = List.copyOf(plan.visits());
        String csv = AnswerKey.csv(problems);
        assertFalse(problems.isEmpty());
        FieldworkTestSupport.sameRun(first, plan.walkAll());
        assertEquals(problems, plan.problems());
        assertEquals(warnings, plan.warnings());
        assertEquals(visits, plan.visits());
        assertEquals(csv, AnswerKey.csv(plan.problems()));
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().clear());
        var fresh = run(f, c);
        FieldworkTestSupport.sameRun(first, fresh.submissions());
        assertEquals(problems, fresh.plan().problems());
        assertEquals(visits, fresh.plan().visits());
        assertEquals(warnings, fresh.plan().warnings());
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void differentSeedsCanChangeTheTextReplacementPattern(String fault) throws Exception {
        var f = textQuestion("");
        var c = settings(120, 2, teamCodes(fault, "TEAM") + "," + fixedText("answer", "ordinary"))
                .withProblems(rates(0, fault));
        Set<Set<Integer>> patterns = new HashSet<>();
        for (int seed = 21; seed <= 24; seed++) {
            var r = run(f, c.withSeed(seed));
            assertFalse(records(r, fault).isEmpty());
            patterns.add(targets(r, fault));
        }
        assertTrue(patterns.size() > 1);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void machineLocaleAndTimeZoneDoNotAffectTextCodeSpellingOrDraws(String fault) throws Exception {
        Locale locale = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            var f = textQuestion("");
            var c = settings(120, 2, teamCodes(fault, "0097", "97.00", "拒答", "İ") + "," + fixedText("answer", "ordinary"))
                    .withProblems(rates(0, fault));
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var first = run(f, c);
            assertFalse(first.plan().problems().isEmpty());
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var second = run(f, c);
            FieldworkTestSupport.sameRun(first.submissions(), second.submissions());
            assertEquals(first.plan().problems(), second.plan().problems());
            assertEquals(first.plan().warnings(), second.plan().warnings());
            assertEquals(first.plan().visits(), second.plan().visits());
            assertEquals(AnswerKey.csv(first.plan().problems()), AnswerKey.csv(second.plan().problems()));
        } finally {
            Locale.setDefault(locale);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }
}
