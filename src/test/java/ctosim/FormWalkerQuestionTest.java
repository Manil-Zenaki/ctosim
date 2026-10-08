package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormWalkerQuestionTest {
    @Test void plainQuestionIsAnsweredOnceAndAdvancesTheClock() throws Exception {
        Submission result = fixed(single("string", ""), "Hello");
        assertEquals("Hello", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void unicodeAndXmlSpecialCharactersRoundTripThroughUtf8Serialization() throws Exception {
        String answer = "Été 東京 😀 & <tag> 'quote'";
        assertEquals(answer, value(fixed(single("string", ""), answer), "answer"));
    }

    @Test void unicodeElementNameIsRecognizedAndReceivesItsOverride() throws Exception {
        LoadedForm loaded = form("<café/>", bind("café", "string", ""), input("café"));
        FormWalker walker = new FormWalker(loaded, config("\"missingRate\":0,\"fields\":{\"café\":{\"value\":\"東京\"}}"));
        assertTrue(walker.configWarnings().isEmpty());
        assertEquals("東京", value(walker.walk(1), "café"));
    }

    @Test void nestedQuestionUsesItsLeafNameForOverrides() throws Exception {
        LoadedForm loaded = form("<group><answer/></group>", bind("group/answer", "string", ""),
                "<group ref=\"/data/group\"><label>Group</label>" + input("group/answer") + "</group>");
        Submission result = fixed(loaded, "leaf override");
        assertEquals("leaf override", value(result, "group/answer"));
        assertEquals(1, result.answered());
    }

    @Test void textWithoutAppearanceUsesLeafNameAsGeneratorPrefix() throws Exception {
        LoadedForm loaded = form("<group><answer/></group>", bind("group/answer", "string", ""), input("group/answer"));
        String answer = value(new FormWalker(loaded, completeConfig()).walk(1), "group/answer");
        assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
    }

    @ParameterizedTest @ValueSource(strings = {"numbers", "numbers_phone", "compact numbers_phone"})
    void appearanceIsPassedToTheAnswerGenerator(String appearance) throws Exception {
        LoadedForm loaded = form("<answer/>", bind("answer", "string", ""),
                "<input ref=\"/data/answer\" appearance=\"" + appearance + "\"><label>Phone</label></input>");
        assertTrue(value(new FormWalker(loaded, completeConfig()).walk(1), "answer").matches("[0-9]{8}"));
    }

    @Test void readOnlyQuestionsWithAndWithoutValuesAreNotAnsweredEvenWithOverrides() throws Exception {
        LoadedForm loaded = form("<note>Keep me</note><answer/>",
                bind("note", "string", "readonly=\"true()\"") + bind("answer", "string", "readonly=\"true()\""),
                input("note") + input("answer"));
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"note\":{\"value\":\"replace\"},\"answer\":{\"value\":\"replace\"}}");
        assertEquals("Keep me", value(result, "note"));
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void existingInstanceValuesIncludingZeroAreKeptWithoutClockAdvance() throws Exception {
        LoadedForm loaded = form("<answer>original</answer><zero>0</zero>",
                bind("answer", "string", "") + bind("zero", "int", ""), input("answer") + input("zero"));
        Submission result = new FormWalker(loaded, completeConfig()).walk(1);
        assertEquals("original", value(result, "answer"));
        assertEquals("0", value(result, "zero"));
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
    }

    @Test void aFixedOverrideReplacesAnExistingValue() throws Exception {
        LoadedForm loaded = form("<answer>original</answer>", bind("answer", "string", ""), input("answer"));
        Submission result = fixed(loaded, "replacement");
        assertEquals("replacement", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void anOverrideWithoutFixedValueStillCausesExistingValueToBeRegenerated() throws Exception {
        LoadedForm loaded = form("<answer>old</answer>", bind("answer", "string", ""), input("answer"));
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"answer\":{\"texts\":[\"new\"]}}");
        assertEquals("new", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @Test void blankGenerationClearsAnOverriddenExistingValue() throws Exception {
        LoadedForm loaded = form("<answer>old</answer>", bind("answer", "string", ""), input("answer"));
        Submission result = walk(loaded, "\"fields\":{\"answer\":{\"missingRate\":1}}");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @Test void calculatedAndActionPopulatedValuesAreKeptWhenReached() throws Exception {
        LoadedForm loaded = form("<first/><calculated/><action/>",
                bind("first", "int", "") + bind("calculated", "int", "calculate=\"/data/first * 2\"")
                        + bind("action", "string", ""),
                "<input ref=\"/data/first\"><label>First</label>"
                        + "<setvalue event=\"xforms-value-changed\" ref=\"/data/action\" value=\"'from action'\"/>"
                        + "</input>" + input("calculated") + input("action"));
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"first\":{\"value\":7}}");
        assertEquals("14", value(result, "calculated"));
        assertEquals("from action", value(result, "action"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void overrideReplacesAnActionPopulatedEditableQuestion() throws Exception {
        LoadedForm loaded = form("<answer/>", bind("answer", "string", ""), input("answer"),
                "<setvalue event=\"odk-instance-first-load\" ref=\"/data/answer\" value=\"'action default'\"/>", "");
        Submission result = fixed(loaded, "override");
        assertEquals("override", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void relevanceIsEvaluatedAfterEarlierAnswersAndIrrelevantNodesAreOmitted(int gate) throws Exception {
        LoadedForm loaded = form("<gate/><shown/><hidden>secret</hidden><group><child/></group>",
                bind("gate", "int", "") + bind("shown", "string", "relevant=\"/data/gate = 1\"")
                        + bind("hidden", "string", "relevant=\"false()\"")
                        + "<bind nodeset=\"/data/group\" relevant=\"/data/gate = 1\"/>"
                        + bind("group/child", "string", ""),
                input("gate") + input("shown") + input("hidden")
                        + "<group ref=\"/data/group\"><label>Conditional</label>" + input("group/child") + "</group>");
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"gate\":{\"value\":" + gate + "}}");
        assertEquals(gate, count(result, "shown"));
        assertEquals(gate, count(result, "group"));
        assertEquals(0, count(result, "hidden"));
        assertEquals(gate == 1 ? 3 : 1, result.answered());
        assertElapsed(result, gate == 1 ? 3 : 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void optionalBlankDoesNotCountAsAnAnswerButAdvancesTheClock() throws Exception {
        Submission result = walk(single("string", ""), "\"missingRate\":1");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void requirednessIsReevaluatedAfterEarlierAnswers() throws Exception {
        LoadedForm loaded = form("<gate/><answer/>", bind("gate", "int", "")
                + bind("answer", "string", "required=\"/data/gate = 1\""), input("gate") + input("answer"));
        for (int gate : new int[]{0, 1}) {
            Submission result = walk(loaded, "\"missingRate\":1,\"fields\":{\"gate\":{\"value\":" + gate + ",\"missingRate\":0}}");
            assertEquals(gate == 1, !value(result, "answer").isEmpty());
            assertEquals(1 + gate, result.answered());
            assertElapsed(result, 2);
        }
    }

    @Test void readOnlyExpressionIsEvaluatedAfterEarlierAnswers() throws Exception {
        LoadedForm loaded = form("<gate/><answer/>", bind("gate", "int", "")
                + bind("answer", "string", "readonly=\"/data/gate = 1\""), input("gate") + input("answer"));
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"gate\":{\"value\":1}}");
        assertEquals("", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void valueChangedActionsSeeTheAdvancedSimulatedClockAtEachQuestion() throws Exception {
        StringBuilder instance = new StringBuilder("<first/><second/><at_first/><at_second/><day_second/><elapsed_second/>");
        StringBuilder binds = new StringBuilder(bind("first", "string", "") + bind("second", "string", "")
                + bind("at_first", "dateTime", "") + bind("at_second", "dateTime", "")
                + bind("day_second", "date", "") + bind("elapsed_second", "int", ""));
        StringBuilder body = new StringBuilder("""
            <input ref="/data/first"><label>First</label>
              <setvalue event="xforms-value-changed" ref="/data/at_first" value="now()"/>
            </input>
            """);
        // Enough skipped fields that even minimum erroneous advances would exceed 60 seconds.
        for (int i = 0; i < 20; i++) {
            instance.append("<note_").append(i).append("/>");
            binds.append(bind("note_" + i, "string", "readonly=\"true()\""));
            body.append(input("note_" + i));
        }
        body.append("""
            <input ref="/data/second"><label>Second</label>
              <setvalue event="xforms-value-changed" ref="/data/at_second" value="now()"/>
              <setvalue event="xforms-value-changed" ref="/data/day_second" value="today()"/>
              <setvalue event="xforms-value-changed" ref="/data/elapsed_second" value="duration()"/>
            </input>
            """);
        Submission result = walk(form(instance.toString(), binds.toString(), body.toString()),
                "\"missingRate\":0,\"start\":\"2040-02-29T23:59:59\",\"fieldworkDays\":1");
        java.time.LocalDateTime first = java.time.OffsetDateTime.parse(value(result, "at_first")).toLocalDateTime();
        java.time.LocalDateTime second = java.time.OffsetDateTime.parse(value(result, "at_second")).toLocalDateTime();
        assertEquals(result.start(), first);
        long firstAdvance = java.time.Duration.between(first, second).getSeconds();
        long secondAdvance = java.time.Duration.between(second, result.end()).getSeconds();
        assertTrue(firstAdvance >= 5 && firstAdvance <= 60);
        assertTrue(secondAdvance >= 5 && secondAdvance <= 60);
        assertEquals(second.toLocalDate().toString(), value(result, "day_second"));
        assertEquals(firstAdvance, Long.parseLong(value(result, "elapsed_second")));
        assertEquals(2, result.answered());
    }

    @Test void acceptedConstraintAnswerIsCountedOnce() throws Exception {
        Submission result = fixed(single("int", "constraint=\". &gt;= 3 and . &lt;= 7\""), "7");
        assertEquals("7", value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
        assertElapsed(result, 1);
    }

    @Test void impossibleConstraintLeavesBlankWarnsAndContinuesToNextQuestion() throws Exception {
        LoadedForm loaded = form("<answer/><after/>", bind("answer", "int", "constraint=\"false()\"")
                + bind("after", "string", ""), input("answer") + input("after"));
        Submission result = walk(loaded, "\"missingRate\":0,\"fields\":{\"answer\":{\"value\":4},\"after\":{\"value\":\"done\"}}");
        assertEquals("", value(result, "answer"));
        assertEquals("done", value(result, "after"));
        assertEquals(1, result.answered());
        assertWarning(result, "answer", "constraint");
        assertEquals(1, result.warnings().size());
        assertElapsed(result, 2); // Retry attempts do not each advance the clock.
    }

    @Test void requiredUnsatisfiedConstraintAlsoProducesARequiredWarning() throws Exception {
        Submission result = fixed(single("int", "required=\"true()\" constraint=\"false()\""), "3");
        assertEquals("", value(result, "answer"));
        assertWarning(result, "answer", "constraint");
        assertWarning(result, "answer", "required");
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "long", "decimal", "date", "time", "dateTime", "geopoint"})
    void unconvertibleAnswerIsBlankAndWarningNamesItsValue(String type) throws Exception {
        Submission result = fixed(single(type, ""), "not-a-valid-value");
        assertEquals("", value(result, "answer"));
        assertWarning(result, "answer", "not-a-valid-value");
        assertEquals(1, result.warnings().size());
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @Test void requiredConversionFailureAlsoProducesARequiredWarning() throws Exception {
        Submission result = fixed(single("int", "required=\"true()\""), "bad-number");
        assertWarning(result, "answer", "bad-number");
        assertWarning(result, "answer", "required");
        assertEquals(0, result.answered());
    }

    @Test void failedOverrideDoesNotKeepTheOriginalDefault() throws Exception {
        LoadedForm loaded = form("<answer>12</answer>", bind("answer", "int", ""), input("answer"));
        Submission result = fixed(loaded, "bad-number");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
    }

    @Test void warningsFollowNavigationOrderRatherThanAlphabeticalOrder() throws Exception {
        LoadedForm loaded = form("<z_first/><a_second/><m_last/>",
                bind("z_first", "int", "") + bind("a_second", "int", "constraint=\"false()\"")
                        + bind("m_last", "date", ""),
                input("z_first") + input("a_second") + input("m_last"));
        SimulationConfig cfg = config("""
            "missingRate":0,"fields":{"z_first":{"value":"bad-first"},
            "a_second":{"value":1},"m_last":{"value":"bad-last"}}
            """);
        Submission result = new FormWalker(loaded, cfg).walk(23);
        assertEquals(3, result.warnings().size());
        List<String> names = List.of("z_first", "a_second", "m_last");
        for (int i = 0; i < names.size(); i++) {
            assertTrue(result.warnings().get(i).startsWith("submission 23:"));
            assertTrue(result.warnings().get(i).contains(names.get(i)));
        }
        assertElapsed(result, 3);
    }
}
