package ctosim;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoRepeatContextTest {
    static FormEntryController session() throws Exception { return load(fixture("repeats.xml")).newEntryController(); }

    @Test void indexOutsideRepeatsIsOneIncludingNonrepeatGroups() throws Exception {
        var session = session();
        assertEquals(1, value(child(root(session), "outside")));
        assertEquals(1, value(child(child(root(session), "group"), "position")));
        assertValue(1.0, evaluateAt(session, root(session), "index()"));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void indexCalculationsUseOneBasedRepeatPositionEvenThroughAGroup(int row) throws Exception {
        var repeat = root(session()).getChild("row", row);
        assertEquals(row + 1, value(child(repeat, "position")));
        assertEquals(row + 1, value(child(child(repeat, "nested"), "position")));
    }

    @Test void nestedRepeatsUseInnermostPositionAndRestartForEachParent() throws Exception {
        var session = session();
        for (int outer = 0; outer < 2; outer++) {
            var row = root(session).getChild("row", outer);
            int count = outer == 0 ? 3 : 1;
            for (int inner = 0; inner < count; inner++) {
                var instance = row.getChild("inner", inner);
                assertNotNull(instance);
                assertEquals(inner + 1, value(child(instance, "position")));
                assertValue((double) inner + 1, evaluateAt(session, instance, "index()"));
            }
        }
    }

    @Test void repeatContextElementItselfCountsAsTheEnclosingInstance() throws Exception {
        var session = session();
        for (int i = 0; i < 2; i++) assertValue((double) i + 1,
                evaluateAt(session, root(session).getChild("row", i), "index()"));
    }

    @Test void relevanceOnRepeatElementUsesItsOwnPositionIncludingNonRelevantInstances() throws Exception {
        var session = session();
        for (int i = 0; i < 3; i++) {
            var repeat = root(session).getChild("gated", i);
            assertEquals(i != 1, repeat.isRelevant(), "Repeat " + (i + 1));
        }
        assertEquals(3, value(child(root(session).getChild("gated", 2), "position")));
    }

    @Test void relevantBindUsesBoundFieldContextRatherThanTheNavigationCursor() throws Exception {
        var session = session();
        assertTrue(child(root(session).getChild("row", 0), "visible").isRelevant());
        assertFalse(child(root(session).getChild("row", 1), "visible").isRelevant());
    }

    @Test void requiredBindUsesBoundFieldContext() throws Exception {
        var session = session();
        assertFalse(child(root(session).getChild("row", 0), "required").isRequired());
        assertTrue(child(root(session).getChild("row", 1), "required").isRequired());
    }

    @Test void readonlyBindUsesBoundFieldContext() throws Exception {
        var session = session();
        assertTrue(child(root(session).getChild("row", 0), "readonly").isEnabled());
        assertFalse(child(root(session).getChild("row", 1), "readonly").isEnabled());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void constraintUsesTheBoundFieldPositionWhenAnsweringByIndex(int row) throws Exception {
        var session = session();
        var field = child(root(session).getChild("row", row), "constrained");
        var index = questionIndex(session, field);
        assertEquals(ANSWER_CONSTRAINT_VIOLATED, session.answerQuestion(index, new IntegerData(row + 2), true));
        assertEquals(ANSWER_OK, session.answerQuestion(index, new IntegerData(row + 1), true));
    }

    @Test void choiceLabelInsideARepeatResolvesTheCurrentInstanceForBothReferenceStyles() throws Exception {
        var session = session();
        for (int i = 0; i < 2; i++) {
            var label = child(root(session).getChild("row", i), "label");
            String expected = i == 0 ? "Alpha" : "Beta";
            assertEquals(expected, value(label));
            assertValue(expected, evaluateAt(session, label, "choice-label(../choice, ../choice)"));
            assertValue(expected, evaluateAt(session, label, "choice-label(/data/row/choice, /data/row/choice)"));
        }
    }

    @Test void relevantInsideARepeatResolvesTheCurrentInstance() throws Exception {
        var session = session();
        for (int i = 0; i < 2; i++) {
            var context = child(root(session).getChild("row", i), "position");
            assertValue(i == 0, evaluateAt(session, context, "relevant(../visible)"));
        }
    }

    @Test void indexCalculationIsEvaluatedWhenTheEngineCreatesANewRepeat() throws Exception {
        var session = session();
        createRepeat(session, root(session).getChild("row", 0));
        var third = root(session).getChild("row", 2);
        assertNotNull(third);
        assertEquals(3, value(child(third, "position")));
        assertEquals(3, value(child(child(third, "nested"), "position")));
    }

    static void createRepeat(FormEntryController session, TreeElement existing) {
        var model = session.getModel();
        FormIndex index = FormIndex.createBeginningOfFormIndex();
        for (int i = 0; i < 10000; i++) {
            index = model.incrementIndex(index);
            if (index.isEndOfFormIndex()) break;
            if (model.getEvent(index) == EVENT_REPEAT
                    && model.getForm().getChildInstanceRef(index).equals(existing.getRef())) {
                session.jumpToIndex(index);
                session.descendIntoNewRepeat();
                return;
            }
        }
        fail("Cannot locate repeat " + existing.getRef());
    }
}
