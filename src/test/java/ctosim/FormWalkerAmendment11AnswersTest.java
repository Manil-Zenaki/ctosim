package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment11Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment11AnswersTest {
    @ParameterizedTest @CsvSource({"1,1", "0,0"})
    void aSingleConsentQuestionUsesTheInclusiveRateEndpoints(int rate, String expected) throws Exception {
        FormWalker walker = new FormWalker(choice11("", "0", "1"), settings11(consent11(rate, "answer")));
        for (int n : new int[] {1, 2, 19, 101, Integer.MAX_VALUE}) {
            Submission result = walker.walk(n);
            assertEquals(expected, value(result, "answer"));
            assertEquals(1, result.answered());
            assertElapsed(result, 1);
            clean11(result);
        }
    }

    @ParameterizedTest @CsvSource({"1,1,1", "0,0,0"})
    void aRelevantChainOnlyRefusesAtItsLastListedField(int rate, String last, int hasFollowup) throws Exception {
        FormWalker walker = new FormWalker(fixture11("chain"), settings11(
                consent11(rate, "questions_answered", "understood", "consent")));
        for (int n = 1; n <= 12; n++) {
            Submission result = walker.walk(n);
            assertEquals("1", value(result, "questions_answered"));
            assertEquals("1", value(result, "understood"));
            assertEquals(last, value(result, "consent"));
            assertEquals(hasFollowup, count(result, "followup"), "Accepted consent must update relevance");
            assertEquals(last, value(result, "consent_copy"), "Dependent calculation must see consent");
            assertEquals(3 + hasFollowup, result.answered());
            assertElapsed(result, 3 + hasFollowup);
            clean11(result);
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"value\":\"0\"", "\"expr\":\"'0'\"", "\"expr\":\"''\"",
            "\"weights\":{\"0\":1,\"1\":0}", "\"missingRate\":1",
            "\"value\":\"0\",\"when\":[{\"if\":\"true()\",\"value\":\"0\"}]"})
    void consentReplacesFixedExpressionWeightedConditionalAndMissingOverrides(String members) throws Exception {
        Submission result = new FormWalker(choice11("", "0", "1"), settings11(
                consent11(1, "answer") + "," + field11(members))).walk(7);
        assertEquals("1", value(result, "answer"));
        assertEquals(1, result.answered());
        clean11(result);
    }

    @Test void refusalReplacesAnOverrideThatWouldOtherwiseAlwaysConsent() throws Exception {
        Submission result = new FormWalker(choice11("", "0", "1"), settings11(
                consent11(0, "answer") + "," + field11("\"value\":\"1\""))).walk(1);
        assertEquals("0", value(result, "answer"));
        clean11(result);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void globalMissingRateOneCannotBlankAnOptionalConsentAnswer(int rate) throws Exception {
        Submission result = new FormWalker(choice11("", "0", "1"), config(
                "\"seed\":110731,\"missingRate\":1," + consent11(rate, "answer"))).walk(1);
        assertEquals(Integer.toString(rate), value(result, "answer"));
        assertEquals(1, result.answered());
        clean11(result);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void customConsentCodesAreStoredExactlyIncludingUnicode(int rate) throws Exception {
        String yes = "Oui_\u00e9\u6771", no = "NON_\u00f1";
        Submission result = new FormWalker(choice11("", no, "1", "0", yes), settings11(
                consent11(rate, yes, no, new String[] {"answer"}))).walk(4);
        assertEquals(rate == 1 ? yes : no, value(result, "answer"));
        clean11(result);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "string"})
    void ordinaryInputsUseTheConfiguredConsentValuesToo(String type) throws Exception {
        Submission result = new FormWalker(single(type, ""), settings11(
                consent11(1, "answer") + "," + field11("\"value\":\"0\""))).walk(2);
        assertEquals("1", value(result, "answer"));
        clean11(result);
    }

    @Test void consentReplacesTheUsualSpecialValueDraw() throws Exception {
        Submission result = new FormWalker(single("int", ""), config("\"seed\":110731,\"missingRate\":0,"
                + "\"specialRate\":1,\"specialValues\":[\"-99\"]," + consent11(1, "answer"))).walk(1);
        assertEquals("1", value(result, "answer"));
        clean11(result);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void everyRepeatInstanceSharesTheSubmissionConsentDecision(int rate) throws Exception {
        Submission result = new FormWalker(fixture11("repeats"), settings11(
                consent11(rate, "understood", "consent"))).walk(3);
        assertEquals(3, count(result, "row"));
        for (int i = 1; i <= 3; i++) {
            assertEquals("1", value(result, "row[" + i + "]/understood"));
            assertEquals(Integer.toString(rate), value(result, "row[" + i + "]/consent"));
        }
        assertEquals(6, result.answered());
        assertElapsed(result, 6);
        clean11(result);
    }

    @Test void anIrrelevantLastFieldDoesNotTransferRefusalToTheLastVisibleQuestion() throws Exception {
        LoadedForm form = form("<first/><last/>", bind("first", "string", "")
                + bind("last", "string", "relevant=\"false()\""), choices11("first", "0", "1")
                + choices11("last", "0", "1"));
        Submission result = new FormWalker(form, settings11(consent11(0, "first", "last"))).walk(1);
        assertEquals("1", value(result, "first"));
        assertEquals(0, count(result, "last"));
        assertEquals(1, result.answered());
        clean11(result);
    }

    @Test void consentDoesNotTurnReadOnlyAndCalculatedFieldsIntoAnsweredQuestions() throws Exception {
        LoadedForm form = form("<note>kept</note><computed/><answer/>",
                bind("note", "string", "readonly=\"true()\"")
                + bind("computed", "string", "calculate=\"'calculated'\"") + bind("answer", "string", ""),
                input("note") + choices11("answer", "0", "1"));
        Submission result = new FormWalker(form, settings11(consent11(1, "note", "computed", "answer"))).walk(1);
        assertEquals("kept", value(result, "note"));
        assertEquals("calculated", value(result, "computed"));
        assertEquals("1", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        clean11(result);
    }

    @Test void withoutTheSettingConsentLikeNamesAndLabelsRetainOrdinaryOverrides() throws Exception {
        Submission result = new FormWalker(fixture11("chain"), settings11("\"fields\":{"
                + "\"questions_answered\":{\"value\":\"1\"},\"understood\":{\"value\":\"1\"},"
                + "\"consent\":{\"value\":\"0\"}}" )).walk(1);
        assertEquals("0", value(result, "consent"));
        assertEquals(0, count(result, "followup"));
        clean11(result);
    }

    @Test void unlistedConsentLikeQuestionKeepsItsOwnMissingRate() throws Exception {
        LoadedForm form = form("<answer/><consent/>", bind("answer", "string", "")
                + bind("consent", "string", ""), choices11("answer", "0", "1") + choices11("consent", "0", "1"));
        Submission result = new FormWalker(form, settings11(consent11(1, "answer")
                + ",\"fields\":{\"consent\":{\"missingRate\":1}}" )).walk(1);
        assertEquals("1", value(result, "answer"));
        assertEquals("", value(result, "consent"));
        assertEquals(1, result.answered());
        clean11(result);
    }
}
