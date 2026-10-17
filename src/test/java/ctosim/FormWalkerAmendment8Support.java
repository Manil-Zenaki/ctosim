package ctosim;

import java.util.List;
import java.util.Locale;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.settings;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 07 Amendment 8 only; no implementation introspection. */
final class FormWalkerAmendment8Support {
    private FormWalkerAmendment8Support() { }

    static LoadedForm fixture8(String name) throws Exception {
        return loadXml(resource("amendment8/" + name));
    }

    static Submission walk8(LoadedForm form) throws Exception {
        return new FormWalker(form, settings("")).walk(23);
    }

    static List<String> constraintWarnings(Submission result) {
        return result.warnings().stream()
                .filter(w -> w.toLowerCase(Locale.ROOT).contains("constraint")).toList();
    }

    static void assertRecordsMatchWarnings(Submission result) {
        List<String> warnings = constraintWarnings(result);
        List<UnsatisfiedConstraint> records = result.unsatisfiedConstraints();
        assertEquals(warnings.size(), records.size(), "One record per constraint-failure warning");
        for (int i = 0; i < records.size(); i++) {
            UnsatisfiedConstraint record = records.get(i);
            String warning = warnings.get(i);
            assertEquals(result.number(), record.submission());
            assertFalse(record.path().isBlank());
            assertTrue(record.path().contains(record.field()), record.toString());
            assertTrue(warning.contains(record.path()), "Record path must occur in its warning: " + warning);
            assertTrue(warning.startsWith("submission " + result.number() + ":"), warning);
            assertEquals(warning.toLowerCase(Locale.ROOT).contains("end"), record.atEndOfForm(), warning);
        }
    }

    static UnsatisfiedConstraint onlyRecord(Submission result) {
        assertEquals(1, result.unsatisfiedConstraints().size());
        assertRecordsMatchWarnings(result);
        return result.unsatisfiedConstraints().get(0);
    }
}
