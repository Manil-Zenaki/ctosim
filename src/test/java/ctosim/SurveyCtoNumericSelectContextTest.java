package ctosim;

import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoNumericSelectContextTest {
    private static FormEntryController repeats(boolean absolute) throws Exception {
        return load(fixture("numeric-select-repeats.xml")
                .replace("__ROW_NUMBER__", absolute ? "/data/row/number" : "../number")
                .replace("__INNER_NUMBER__", absolute ? "/data/row/inner/number" : "../number"))
                .newEntryController();
    }

    private static Object result(TreeElement row) { return value(child(row, "result")); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void numericArgumentsResolveInEachRepeatInstanceForRelativeAndAbsoluteReferences(boolean absolute) throws Exception {
        var session = repeats(absolute);
        var first = root(session).getChild("row", 0);
        var second = root(session).getChild("row", 1);
        assertEquals("[true:1:1]", result(first));
        assertEquals("[false:1:2]", result(second));
        assertTrue(child(first, "visible").isRelevant());
        assertFalse(child(second, "visible").isRelevant());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedRepeatArgumentsUseTheInnermostInstance(boolean absolute) throws Exception {
        var session = repeats(absolute);
        var first = root(session).getChild("row", 0);
        var second = root(session).getChild("row", 1);
        assertEquals("[true:1:2.5]", result(first.getChild("inner", 0)));
        assertEquals("[false:1:-3.25]", result(first.getChild("inner", 1)));
        assertEquals("[false:1:0]", result(second.getChild("inner", 0)));
    }

    @Test void editingOneRepeatDoesNotUseOrChangeItsSiblingsNumericValue() throws Exception {
        var session = repeats(false);
        var first = root(session).getChild("row", 0);
        var second = root(session).getChild("row", 1);
        answer(session, child(first, "number"), new IntegerData(2));
        assertEquals("[false:1:2]", result(first));
        assertEquals("[false:1:2]", result(second));
        answer(session, child(second, "number"), new IntegerData(1));
        assertEquals("[false:1:2]", result(first));
        assertEquals("[true:1:1]", result(second));
    }

    @Test void newlyCreatedRepeatEvaluatesNumericSelectExpressions() throws Exception {
        var session = repeats(false);
        SurveyCtoRepeatContextTest.createRepeat(session, root(session).getChild("row", 0));
        var added = root(session).getChild("row", 2);
        assertNotNull(added);
        assertEquals("[true:1:1]", result(added));
        assertTrue(child(added, "visible").isRelevant());
    }

    @Test void explicitlyStringWrappedArgumentsKeepNativeBehaviorInRepeats() throws Exception {
        var session = load(fixture("numeric-select-repeats.xml")
                .replace("__ROW_NUMBER__", "string(../number)")
                .replace("__INNER_NUMBER__", "string(../number)")).newEntryController();
        var first = root(session).getChild("row", 0);
        var second = root(session).getChild("row", 1);
        assertEquals("[true:1:1]", result(first));
        assertEquals("[false:1:2]", result(second));
        assertEquals("[true:1:2.5]", result(first.getChild("inner", 0)));
        assertEquals("[false:1:-3.25]", result(first.getChild("inner", 1)));
        answer(session, child(second, "number"), new IntegerData(1));
        assertEquals("[true:1:1]", result(second));
        SurveyCtoRepeatContextTest.createRepeat(session, first);
        assertEquals("[true:1:1]", result(root(session).getChild("row", 2)));
    }

    @Test void dependentCalculatesFollowNumericResultsStoredInStringDeclaredFields() throws Exception {
        var session = load(fixture("numeric-select-template.xml")
                .replace("calculate=\"1\"", "calculate=\"/data/number\"")
                .replace("__RESULT_BIND__", "calculate=\"concat('[', selected(/data/arm, '1'), ':', count-selected(/data/arm), ':', selected-at(/data/arm, 0), ']')\"")
                .replace("__TARGET_BIND__", "")
                .replace("__ACTION__", "")).newEntryController();
        assertEquals("[true:1:1]", result(root(session)));
        answer(session, child(root(session), "number"), new IntegerData(2));
        assertInstanceOf(IntegerData.class, child(root(session), "arm").getValue());
        assertEquals("[false:1:2]", result(root(session)));
    }

    @Test void randomOnceCalculatedArmBranchesAccordingToItsStoredNumberInEverySession() throws Exception {
        String xml = fixture("numeric-select-template.xml")
                .replace("calculate=\"1\"", "calculate=\"once(int(2*random())+1)\"")
                .replace("__RESULT_BIND__", "calculate=\"concat('[', count-selected(/data/arm), ':', selected-at(/data/arm, 0), ']')\"")
                .replace("__TARGET_BIND__", "relevant=\"selected(/data/arm, '1')\"")
                .replace("__ACTION__", "");
        var loaded = load(xml);
        for (int i = 0; i < 8; i++) {
            var session = loaded.newEntryController();
            var arm = child(root(session), "arm");
            int number = assertInstanceOf(Integer.class, value(arm));
            assertTrue(number == 1 || number == 2);
            assertEquals("[1:" + number + "]", result(root(session)));
            assertEquals(number == 1, child(root(session), "target").isRelevant());
        }
    }
}
