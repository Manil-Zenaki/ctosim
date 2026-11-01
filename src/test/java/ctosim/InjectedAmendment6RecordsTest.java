package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment2Support.*;
import static ctosim.InjectedAmendment6Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Exercises locale/time-zone independence and walks change the JVM time zone")
class InjectedAmendment6RecordsTest {
    @Test void refusalAnswerKeyRowsExposeTheNewProblemWithTheStatisticalKind() throws Exception {
        var form = selection(false, "ok", "REF");
        var config = settings(200, 2, codes("REF") + "," + fixed("answer", "ok"))
                .withProblems(rates(0, "refuse"));
        var before = new Fieldwork(form, config);
        assertTrue(before.problems().isEmpty(), "no records until walkAll");
        var run = run(form, config);
        var records = records(run, "refuse");
        assertFalse(records.isEmpty());
        var rows = AnswerKey.csv(run.plan().problems()).lines().toList();
        assertEquals("KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind", rows.get(0));
        assertEquals(records.size() + 1, rows.size());
        for (int i = 0; i < records.size(); i++) {
            var p = records.get(i);
            assertEquals(String.join(",", p.key(), p.enumerator(), "enumerator", "refuse", "ipacheckenumdb",
                    "answer", "REF", "ok", "", "statistical"), rows.get(i + 1));
        }
    }

    @Test void refusalDoesNotChangeAnAddAnotherAnswerThatControlsAnIndirectRepeatCount() throws Exception {
        var form = extending(true, false, items("yes", "REF"));
        var run = faulty(form, 200, codes("REF") + "," + fixed("more", "yes"));
        assertFalse(records(run, "refuse").isEmpty(), "the roster's ordinary text remains eligible");
        for (var record : records(run, "refuse")) {
            assertEquals("name", record.field());
            assertEquals("REF", record.value());
        }
        for (var submission : run.submissions()) {
            assertEquals(2, count(submission, "person"));
            assertEquals("yes", value(submission, "person[1]/more"));
            assertEquals("yes", value(submission, "person[2]/more"));
        }
    }

    @Test void eachChangedFieldHasItsOrdinaryCleanValueAndRecordsFollowFormOrder() throws Exception {
        var form = fixture("<z/><a/>", bind("a", "int", "") + bind("z", "int", ""), input("z") + input("a"));
        var run = faulty(form, 1000, codes("-888") + ",\"specialValues\":[\"97\"]");
        int paired = 0;
        for (var submission : run.submissions()) {
            var records = records(run, "refuse").stream().filter(p -> p.submission() == submission.number()).toList();
            Map<String, InjectedProblem> byField = records.stream()
                    .collect(Collectors.toMap(InjectedProblem::field, p -> p));
            if (records.size() == 2) {
                paired++;
                assertEquals(List.of("z", "a"), records.stream().map(InjectedProblem::field).toList());
            }
            var clean = run.clean(submission.number());
            for (String field : List.of("z", "a")) {
                if (byField.containsKey(field)) {
                    assertEquals(value(clean, field), byField.get(field).cleanValue());
                    assertEquals(-888, Double.parseDouble(byField.get(field).value()));
                } else assertEquals(value(clean, field), value(submission, field), "independent ordinary answer draws");
            }
        }
        assertTrue(paired > 0, "exercise two records on one submission");
        for (String field : List.of("z", "a")) {
            rate((int) records(run, "refuse").stream().filter(p -> p.field().equals(field)).count(), 500, .25);
        }
        metadata6(run);
    }

    @Test void teamCodesStillLeaveIdsReadOnlyCalculationsAndRepeatCountsAlone() throws Exception {
        var form = fixture("<id/><readonly>17</readonly><calculated/><size/><person jr:template=\"\"><name/></person><amount/>",
                bind("id", "int", "") + bind("readonly", "int", "readonly=\"true()\"")
                        + bind("calculated", "int", "calculate=\"42\"") + bind("size", "int", "")
                        + bind("person/name", "string", "constraint=\". != '1' and . != '2'\"") + bind("amount", "int", ""),
                input("id") + input("readonly") + input("size")
                        + "<repeat nodeset=\"/data/person\" jr:count=\"/data/size\">" + input("person/name") + "</repeat>"
                        + input("amount"));
        var run = faulty(form, 240, codes("1", "2")
                + ",\"id\":\"id\",\"specialValues\":[],\"fields\":{\"id\":{\"min\":10,\"max\":249},\"size\":{\"value\":3},\"amount\":{\"value\":10}}");
        assertFalse(records(run, "refuse").isEmpty(), "an eligible question still receives the fault");
        for (var record : records(run, "refuse")) assertEquals("amount", record.field());
        Set<String> ids = new HashSet<>();
        for (var submission : run.submissions()) {
            assertTrue(ids.add(value(submission, "id")), "survey IDs stay unique");
            assertEquals(value(run.clean(submission.number()), "id"), value(submission, "id"));
            assertEquals("17", value(submission, "readonly"));
            assertEquals("42", value(submission, "calculated"));
            assertEquals("3", value(submission, "size"));
            assertEquals(3, count(submission, "person"));
        }
    }

    @Test void resentCopiesKeepTheirOriginalTeamCodeWithoutAnExtraFaultRecord() throws Exception {
        var form = selection(false, "ok", "DK");
        var run = run(form, settings(600, 2, codes("DK") + "," + fixed("answer", "ok"))
                .withProblems(new ProblemRates(.25, 0, 0, 0, 0, 0, 0, 0, List.of("refuse"))));
        int copiedFaults = 0;
        var faultyTargets = targets(run, "refuse");
        for (var record : records(run, "resent")) {
            var original = run.related(record);
            assertEquals(value(original, "answer"), value(run.target(record), "answer"));
            assertEquals(1, run.plan().problems().stream().filter(p -> p.submission() == record.submission()).count());
            if (faultyTargets.contains(original.number())) copiedFaults++;
        }
        assertTrue(copiedFaults > 0, "exercise a copy whose original actually received a team code");
    }

    @Test void repeatedAndFreshPlansReplayCodesSubmissionsWarningsAndVisits() throws Exception {
        var form = selection(false, "ok", "DK", "REF");
        var config = settings(160, 2, codes("DK", "REF") + "," + fixed("answer", "ok"))
                .withProblems(rates(0, "refuse"));
        var run = run(form, config);
        assertFalse(records(run, "refuse").isEmpty());
        replay2(run);
        var fresh = run(form, config);
        sameResults(run, fresh);
        assertEquals(run.plan().warnings(), fresh.plan().warnings());
    }

    @Test void changingTheSeedCanChangeWhichAnswersReceiveTeamCodes() throws Exception {
        var form = selection(false, "ok", "DK", "REF");
        var config = settings(100, 2, codes("DK", "REF") + "," + fixed("answer", "ok"))
                .withProblems(rates(0, "refuse"));
        Set<Set<Integer>> patterns = new HashSet<>();
        for (int seed = 21; seed <= 28; seed++) {
            var run = run(form, config.withSeed(seed));
            assertFalse(records(run, "refuse").isEmpty());
            patterns.add(targets(run, "refuse"));
        }
        assertTrue(patterns.size() > 1);
    }

    @Test void localeAndMachineTimeZoneDoNotChangeNumericCodeDrawsOrAnswerKeys() throws Exception {
        Locale locale = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            var form = number("decimal", "");
            var config = settings(160, 2, codes("-9.75", "-8.5") + "," + fixed("amount", "10"))
                    .withProblems(rates(0, "refuse"));
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var first = run(form, config);
            String csv = AnswerKey.csv(first.plan().problems());
            assertFalse(records(first, "refuse").isEmpty());
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var second = run(form, config);
            sameResults(first, second);
            assertEquals(first.plan().warnings(), second.plan().warnings());
            assertEquals(csv, AnswerKey.csv(second.plan().problems()));
        } finally {
            Locale.setDefault(locale);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }
}
