package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment11Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment11FallbackTest {
    private static String custom(int rate) {
        return consent11(rate, "YES", "NO", new String[] {"answer"});
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void aMissingConsentChoiceFallsBackToTheOrdinaryOverrideAndWarns(int rate) throws Exception {
        Submission result = new FormWalker(choice11("", "backup"), settings11(custom(rate)
                + "," + field11("\"value\":\"backup\""))).walk(17);
        assertEquals("backup", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        consentWarning11(result, "answer", rate == 1 ? "YES" : "NO");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
        assertTrue(result.formErrors().isEmpty());
    }

    @Test void fallbackWithoutAnOverrideUsesTheOrdinaryChoiceGenerator() throws Exception {
        Submission result = new FormWalker(choice11("", "backup"), settings11(custom(1))).walk(1);
        assertEquals("backup", value(result, "answer"));
        consentWarning11(result, "answer", "YES");
    }

    @Test void choiceMembershipIsExactRatherThanCaseInsensitive() throws Exception {
        Submission result = new FormWalker(choice11("", "yes", "backup"), settings11(custom(1)
                + "," + field11("\"value\":\"backup\""))).walk(1);
        assertEquals("backup", value(result, "answer"));
        consentWarning11(result, "answer", "YES");
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void choicesExcludedByTheCurrentFilterAreNotOfferedConsentValues(int rate) throws Exception {
        Submission result = new FormWalker(fixture11("filtered"), settings11(custom(rate)
                + ",\"fields\":{\"answer\":{\"value\":\"backup\"},\"after\":{\"value\":\"done\"}}" )).walk(12);
        assertEquals("backup", value(result, "answer"));
        assertEquals("done", value(result, "after"));
        consentWarning11(result, "answer", rate == 1 ? "YES" : "NO");
        assertEquals(2, result.answered());
    }

    @Test void anEmptyFilteredChoiceListWarnsAndFallsBackToTheOrdinaryBlankOutcome() throws Exception {
        LoadedForm form = loadXml(resource("amendment11/filtered.xml")
                .replace("<allowed>backup</allowed>", "<allowed>nothing-offered</allowed>"));
        Submission result = new FormWalker(form, settings11(custom(1)
                + ",\"fields\":{\"after\":{\"value\":\"done\"}}" )).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals("done", value(result, "after"));
        consentWarning11(result, "answer", "YES");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
        assertTrue(result.formErrors().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void aConstraintRejectedConsentValueFallsBackToAnAcceptedOrdinaryValue(int rate) throws Exception {
        Submission result = new FormWalker(choice11("constraint=\". = 'backup'\"", "YES", "NO", "backup"),
                settings11(custom(rate) + "," + field11("\"value\":\"backup\""))).walk(5);
        assertEquals("backup", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        consentWarning11(result, "answer", rate == 1 ? "YES" : "NO");
        assertTrue(result.unsatisfiedConstraints().isEmpty(), "A rejected consent proposal alone is not exhaustion");
        assertTrue(result.formErrors().isEmpty());
    }

    @Test void aConsentCodeThatCannotConvertFallsBackToTheUsualTypedAnswer() throws Exception {
        Submission result = new FormWalker(single("int", ""), settings11(custom(1)
                + "," + field11("\"value\":7"))).walk(1);
        assertEquals("7", value(result, "answer"));
        assertEquals(1, result.answered());
        consentWarning11(result, "answer", "YES");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void fallbackRestoresTheOrdinaryConditionalExpressionOverride() throws Exception {
        Submission result = new FormWalker(choice11("", "backup"), settings11(custom(1) + ","
                + field11("\"value\":\"wrong\",\"when\":[{\"if\":\"true()\",\"expr\":\"'backup'\"}]"))).walk(1);
        assertEquals("backup", value(result, "answer"));
        consentWarning11(result, "answer", "YES");
    }

    @Test void fallbackRestoresTheOrdinaryMissingRate() throws Exception {
        Submission result = new FormWalker(choice11("", "backup"), settings11(custom(1) + ","
                + field11("\"missingRate\":1"))).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        consentWarning11(result, "answer", "YES");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void fallbackStillSearchesWhenTheUsualOverrideAlsoBreaksTheConstraint() throws Exception {
        Submission result = new FormWalker(choice11("constraint=\". = 'backup'\"", "YES", "NO", "backup"),
                settings11(custom(1) + "," + field11("\"value\":\"NO\""))).walk(1);
        assertEquals("backup", value(result, "answer"));
        assertEquals(1, result.answered());
        consentWarning11(result, "answer", "YES");
        assertTrue(result.unsatisfiedConstraints().isEmpty());
    }

    @Test void anImpossibleConstraintRetainsTheUsualExhaustionAndRequiredDiagnostics() throws Exception {
        Submission result = new FormWalker(choice11("constraint=\"false()\" required=\"true()\"", "YES", "NO"),
                settings11(custom(1) + "," + field11("\"value\":\"NO\""))).walk(8);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        consentWarning11(result, "answer", "YES");
        assertWarning(result, "answer", "constraint");
        assertWarning(result, "answer", "required");
        assertEquals(1, result.unsatisfiedConstraints().size());
        assertEquals("answer", result.unsatisfiedConstraints().get(0).field());
        assertFalse(result.unsatisfiedConstraints().get(0).atEndOfForm());
        assertTrue(result.formErrors().isEmpty());
    }

    @Test void fallbackOnAnEarlierFieldDoesNotChangeTheLastFieldsRefusal() throws Exception {
        LoadedForm form = form("<first/><last/>", bind("first", "string", "") + bind("last", "string", ""),
                choices11("first", "backup") + choices11("last", "YES", "NO"));
        Submission result = new FormWalker(form, settings11(consent11(0, "YES", "NO", new String[] {"first", "last"})
                + ",\"fields\":{\"first\":{\"value\":\"backup\"},\"last\":{\"value\":\"YES\"}}" )).walk(1);
        assertEquals("backup", value(result, "first"));
        assertEquals("NO", value(result, "last"));
        consentWarning11(result, "first", "YES");
        assertEquals(2, result.answered());
    }
}
