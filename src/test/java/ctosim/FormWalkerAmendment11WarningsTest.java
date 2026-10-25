package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FormWalkerAmendment11Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment11WarningsTest {
    @Test void eachMissingConsentFieldAddsAConfigurationWarningAfterExistingWarnings() throws Exception {
        LoadedForm form = choice11("", "0", "1");
        String overrides = "\"fields\":{\"z_override\":{\"value\":\"x\"},\"a_override\":{\"value\":\"x\"}}";
        List<String> existing = new FormWalker(form, settings11(overrides)).configWarnings();
        FormWalker walker = new FormWalker(form, settings11(overrides + ","
                + consent11(1, "z_consent", "answer", "a_consent", "\u00e9_absent")));
        List<String> warnings = walker.configWarnings();
        assertEquals(2, existing.size());
        assertEquals(existing, warnings.subList(0, existing.size()), "Consent diagnostics follow all earlier diagnostics");
        List<String> added = warnings.subList(existing.size(), warnings.size());
        assertEquals(3, added.size());
        for (String name : new String[] {"z_consent", "a_consent", "\u00e9_absent"}) {
            assertEquals(1, added.stream().filter(w -> w.contains(name)).count(), name);
        }
        assertThrows(UnsupportedOperationException.class, () -> warnings.add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> warnings.remove(0));
        List<String> beforeWalk = List.copyOf(warnings);
        Submission result = walker.walk(1);
        assertEquals("1", value(result, "answer"));
        assertEquals(beforeWalk, walker.configWarnings());
        clean11(result);
    }

    @Test void presentConsentFieldsDoNotRequireOrdinaryFieldOverrides() throws Exception {
        SimulationConfig cfg = settings11(consent11(1, "questions_answered", "understood", "consent"));
        FormWalker walker = new FormWalker(fixture11("chain"), cfg);
        assertTrue(walker.configWarnings().isEmpty());
        walker.walk(1);
        assertTrue(cfg.overriddenFields().isEmpty(), "Using consent must not synthesize persistent field overrides");
        assertTrue(walker.configWarnings().isEmpty());
    }

    @Test void fieldNamesAreMatchedExactlyAndAnAbsentLastFieldDoesNotTransferRefusal() throws Exception {
        FormWalker walker = new FormWalker(choice11("", "0", "1"), settings11(consent11(0, "answer", "Answer")));
        assertEquals(1, walker.configWarnings().size());
        assertTrue(walker.configWarnings().get(0).contains("Answer"));
        Submission result = walker.walk(1);
        assertEquals("1", value(result, "answer"), "The literal last field is missing, so earlier fields still receive yes");
        clean11(result);
    }

    @Test void anExistingFieldInARepeatTemplateIsNotReportedAsMissingWhenTheRepeatIsEmpty() throws Exception {
        LoadedForm form = form("<size>0</size><row jr:template=\"\"><answer/></row>",
                bind("size", "int", "") + bind("row/answer", "string", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + choices11("row/answer", "0", "1") + "</repeat>");
        FormWalker walker = new FormWalker(form, settings11(consent11(1, "answer")));
        assertTrue(walker.configWarnings().isEmpty());
        Submission result = walker.walk(1);
        assertEquals(0, count(result, "row"));
        assertEquals(0, result.answered());
        clean11(result);
    }

    @Test void allAbsentConsentFieldsWarnButLeaveOrdinaryQuestionsAlone() throws Exception {
        FormWalker walker = new FormWalker(choice11("", "0", "1"), settings11(consent11(1, "missing")
                + "," + field11("\"value\":\"0\"")));
        assertEquals(1, walker.configWarnings().size());
        assertTrue(walker.configWarnings().get(0).contains("missing"));
        Submission result = walker.walk(1);
        assertEquals("0", value(result, "answer"));
        clean11(result);
    }

    @Test void fallbackWarningsFollowQuestionOrderAndRemainStableAcrossWalks() throws Exception {
        LoadedForm form = form("<first/><last/>", bind("first", "string", "") + bind("last", "string", ""),
                choices11("first", "backup") + choices11("last", "backup"));
        FormWalker walker = new FormWalker(form, settings11(consent11(0, "YES", "NO", new String[] {"first", "last"})));
        Submission original = walker.walk(23);
        assertEquals("backup", value(original, "first"));
        assertEquals("backup", value(original, "last"));
        assertEquals(2, original.warnings().size());
        assertTrue(original.warnings().get(0).contains("first"));
        assertTrue(original.warnings().get(0).contains("YES"));
        assertTrue(original.warnings().get(1).contains("last"));
        assertTrue(original.warnings().get(1).contains("NO"));
        consentWarning11(original, "first", "YES");
        consentWarning11(original, "last", "NO");
        List<String> snapshot = List.copyOf(original.warnings());
        assertThrows(UnsupportedOperationException.class, () -> original.warnings().add("changed"));
        walker.walk(24);
        assertEquals(snapshot, original.warnings());
        same11(original, walker.walk(23));
    }
}
