package ctosim;

import java.util.List;
import java.util.stream.Collectors;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 6: public-API fixtures and independent result checks. */
final class InjectedAmendment6Support {
    private InjectedAmendment6Support() { }

    static String codes(String... values) {
        return "\"refuseCodes\":" + List.of(values).stream()
                .map(SimulationConfigTestSupport::quote).collect(Collectors.joining(",", "[", "]"));
    }

    static LoadedForm selection(boolean multiple, String... values) throws Exception {
        return fixture("<answer/>", bind("answer", multiple ? "select" : "select1", ""),
                choices("answer", multiple, values));
    }

    static String fixed(String field, String value) {
        return "\"fields\":{" + SimulationConfigTestSupport.quote(field) + ":{\"value\":"
                + SimulationConfigTestSupport.quote(value) + "}}";
    }

    static Run faulty(LoadedForm form, int submissions, String extra) throws Exception {
        return run(form, settings(submissions, 2, extra).withProblems(rates(0, "refuse")));
    }

    static void unchanged(Run run) {
        assertTrue(records(run, "refuse").isEmpty());
        for (var submission : run.submissions()) ordinary(run, submission.number());
    }

    static void truthfulCodeRecords(Run run, String field, String clean, List<String> allowed) throws Exception {
        var records = records(run, "refuse");
        assertFalse(records.isEmpty(), "exercise actual replacements");
        assertEquals(records.size(), targets(run, "refuse").size(), "one record per replaced answer");
        String faulty = soleEnumerator(run, "refuse");
        var changed = targets(run, "refuse");
        for (var record : records) {
            assertEquals(field, record.field());
            assertEquals(clean, record.cleanValue());
            assertTrue(allowed.contains(record.value()), record.toString());
            assertNotEquals(record.cleanValue(), record.value());
        }
        for (var submission : run.submissions()) {
            String actual = value(submission, field);
            if (changed.contains(submission.number())) {
                assertEquals(faulty, value(submission, "username"));
                assertTrue(allowed.contains(actual));
            } else assertEquals(clean, actual, "unrecorded answers, including clean enumerators, stay ordinary");
        }
        metadata6(run);
    }

    static void sameResults(Run expected, Run actual) {
        FieldworkTestSupport.sameRun(expected.submissions(), actual.submissions());
        assertEquals(expected.plan().visits(), actual.plan().visits());
        assertEquals(expected.plan().problems(), actual.plan().problems());
    }

    static void metadata6(Run run) throws Exception {
        int previous = 0;
        for (var record : run.plan().problems()) {
            assertTrue(record.submission() >= previous, "records follow submission order");
            previous = record.submission();
            if (!record.problem().equals("refuse")) continue;
            assertEquals("enumerator", record.family());
            assertEquals("ipacheckenumdb", record.check());
            assertFalse(record.deterministic());
            assertEquals("", record.relatedKey());
            assertEquals(key(run.target(record)), record.key());
            assertEquals(value(run.target(record), "username"), record.enumerator());
            assertEquals(value(run.target(record), record.field()), record.value());
            assertNotEquals(record.cleanValue(), record.value());
        }
    }

    static String bothCodes() {
        return "\"dontKnowCodes\":[\"DK\"]," + codes("REF");
    }

    static LoadedForm fiveFaultForm() throws Exception {
        try (var in = InjectedAmendment6Support.class.getResourceAsStream(
                "/ctosim/injected-problems/amendment6/five-faults.xml")) {
            assertNotNull(in);
            return FormLoader.load(in, "five-faults.xml");
        }
    }

    static String fiveFaultSettings() {
        return InjectedAmendment2Support.consent(.5)
                + ",\"dontKnowCodes\":[\"-99\"]," + codes("REF")
                + ",\"fields\":{\"gate\":{\"value\":\"yes\"},\"code\":{\"value\":\"ok\"}}";
    }
}
