package ctosim;

import java.util.List;
import java.util.stream.Collectors;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 5: public-API fixtures and independent result checks. */
final class InjectedAmendment5Support {
    private InjectedAmendment5Support() { }

    static String codes(String... values) {
        return "\"dontKnowCodes\":" + List.of(values).stream()
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
        return run(form, settings(submissions, 2, extra).withProblems(rates(0, "dontKnow")));
    }

    static void unchanged(Run run) {
        assertTrue(records(run, "dontKnow").isEmpty());
        for (var submission : run.submissions()) ordinary(run, submission.number());
    }

    static void truthfulCodeRecords(Run run, String field, String clean, List<String> allowed) throws Exception {
        var records = records(run, "dontKnow");
        assertFalse(records.isEmpty(), "exercise actual replacements");
        assertEquals(records.size(), targets(run, "dontKnow").size(), "one record per replaced answer");
        String faulty = soleEnumerator(run, "dontKnow");
        var changed = targets(run, "dontKnow");
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
        newMetadata(run);
    }

    static void sameResults(Run expected, Run actual) {
        FieldworkTestSupport.sameRun(expected.submissions(), actual.submissions());
        assertEquals(expected.plan().visits(), actual.plan().visits());
        assertEquals(expected.plan().problems(), actual.plan().problems());
    }
}
