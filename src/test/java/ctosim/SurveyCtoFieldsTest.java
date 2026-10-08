package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoFieldsTest {
    static Stream<Arguments> labels() {
        return Stream.of(Arguments.of("choice-label(/data/choice, 'a')", "Apple"),
                Arguments.of("choice-label(/data/choice, 'b')", "Banana"),
                Arguments.of("choice-label(/data/choice, /data/selection)", "Banana"),
                Arguments.of("choice-label(/data/choice, concat('', 'b'))", "Banana"),
                Arguments.of("choice-label(/data/choice, 1)", "One"),
                Arguments.of("choice-label(/data/choice, '1')", "One"),
                Arguments.of("choice-label(/data/choice, '東京')", "Japanese choice"),
                Arguments.of("choice-label(/data/choice, '01')", ""),
                Arguments.of("choice-label(/data/choice, 'A')", ""),
                Arguments.of("choice-label(/data/choice, ' a')", ""),
                Arguments.of("choice-label(/data/choice, '')", ""),
                Arguments.of("choice-label(/data/choice, 'missing')", ""),
                Arguments.of("choice-label(/data/plain, 'a')", ""),
                Arguments.of("choice-label(/data/absent, 'a')", ""),
                Arguments.of("choice-label(/data/outer, 'a')", ""),
                Arguments.of("choice-label(/data/many, 'a')", "Apple"),
                Arguments.of("choice-label(/data/many, 'b')", "Banana"),
                Arguments.of("choice-label(/data/many, 'a b')", ""));
    }

    @ParameterizedTest @MethodSource("labels")
    void choiceLabelResolvesTheRequestedValueInTheReferencedSelectQuestion(String expression, String expected) throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        session.setLanguage("English");
        assertValue(expected, evaluate(session, expression));
    }

    @Test void choiceLabelAcceptsRelativeFieldReference() throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        session.setLanguage("English");
        assertValue("Banana", evaluateAt(session, child(root(session), "label"), "choice-label(../choice, ../selection)"));
    }

    @Test void changingLanguageChangesLabelsImmediatelyAndDoesNotAffectOtherSessions() throws Exception {
        var form = load(fixture("fields.xml"));
        var first = form.newEntryController();
        var second = form.newEntryController();
        first.setLanguage("English"); second.setLanguage("English");
        assertEquals("Banana", evaluate(first, "choice-label(/data/choice, 'b')"));
        first.setLanguage("French");
        assertEquals("Banane mûre", evaluate(first, "choice-label(/data/choice, 'b')"));
        assertEquals("Banana", evaluate(second, "choice-label(/data/choice, 'b')"));
        first.setLanguage("English");
        assertEquals("Banana", evaluate(first, "choice-label(/data/choice, 'b')"));
    }

    @Test void choiceLabelCalculationUpdatesWhenItsValueArgumentChanges() throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        session.setLanguage("English");
        assertEquals("Banana", value(child(root(session), "label")));
        answer(session, child(root(session), "selection"), new StringData("a"));
        assertEquals("Apple", value(child(root(session), "label")));
    }

    static Stream<Arguments> relevanceCases() {
        return Stream.of(Arguments.of("/data/plain", true), Arguments.of("/data/unanswered", true),
                Arguments.of("/data/outer", true), Arguments.of("/data/outer/inner/field", true),
                Arguments.of("/data/outer/inner/own_hidden", false), Arguments.of("/data/absent", false));
    }

    @ParameterizedTest @MethodSource("relevanceCases")
    void relevantReportsNodeRelevanceRatherThanWhetherItHasAnAnswer(String reference, boolean expected) throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        assertValue(expected, evaluate(session, "relevant(" + reference + ")"));
    }

    @Test void relevanceIncludesEveryAncestorAndReflectsChangesAtEvaluationTime() throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        var parent = child(root(session), "parent_on");
        var nested = child(root(session), "child_on");
        String field = "relevant(/data/outer/inner/field)";
        assertEquals(true, evaluate(session, field));
        answer(session, parent, new IntegerData(0));
        assertEquals(false, evaluate(session, field));
        answer(session, nested, new IntegerData(0));
        answer(session, parent, new IntegerData(1));
        assertEquals(false, evaluate(session, field));
        answer(session, nested, new IntegerData(1));
        assertEquals(true, evaluate(session, field));
        assertEquals(false, evaluate(session, "relevant(/data/outer/inner/own_hidden)"));
    }

    @Test void ownRelevanceChangesAndRelativeReferencesAreEvaluatedInContext() throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        var context = child(root(session), "plain");
        assertValue(true, evaluateAt(session, context, "relevant(../direct)"));
        answer(session, child(root(session), "child_on"), new IntegerData(0));
        assertValue(false, evaluateAt(session, context, "relevant(../direct)"));
        answer(session, child(root(session), "child_on"), new IntegerData(1));
        assertValue(true, evaluateAt(session, context, "relevant(../direct)"));
    }

    @Test void emptyTreatsANonRelevantFieldAsEmptyEvenWhenItPreviouslyHadAValue() throws Exception {
        var session = load(fixture("fields.xml")).newEntryController();
        assertValue(false, evaluate(session, "empty(/data/direct)"));
        answer(session, child(root(session), "child_on"), new IntegerData(0));
        assertValue(true, evaluate(session, "empty(/data/direct)"));
        assertValue(true, evaluate(session, "empty(/data/outer/inner/field)"));
    }

    @Test void scalarCalculationRecomputesAfterAReferencedAnswerChanges() throws Exception {
        var session = expressionForm("upper(/data/text)").newEntryController();
        assertEquals("[ABC CAFÉ 東京]", value(child(root(session), "result")));
        answer(session, child(root(session), "text"), new StringData("changed"));
        assertEquals("[CHANGED]", value(child(root(session), "result")));
    }
}
