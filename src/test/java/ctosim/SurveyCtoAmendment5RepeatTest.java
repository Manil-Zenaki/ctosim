package ctosim;

import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.TreeElement;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment5RepeatTest {
    private static LoadedForm repeats(boolean absolute) throws Exception {
        return load(fixture("amendment5-repeats.xml")
                .replace("__ROW_INDEX__", absolute ? "/data/row/member_index" : "../member_index")
                .replace("__ROW_NUMBER__", absolute ? "/data/row/number" : "../number")
                .replace("__INNER_INDEX__", absolute ? "/data/row/inner/member_index" : "../member_index"));
    }

    private static void selection(TreeElement row, boolean expected) {
        assertEquals("[" + expected + "]", value(child(row, "result")), row.getRef().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void memberIndexSelectsTheCorrectRepeatUsingEitherReferenceStyle(boolean absolute) throws Exception {
        var entry = repeats(absolute).newEntryController();
        for (int i = 0; i < 3; i++) {
            var row = root(entry).getChild("row", i);
            assertInstanceOf(IntegerData.class, child(row, "member_index").getValue());
            assertEquals(i + 1, value(child(row, "member_index")));
            selection(row, i != 1);
            assertEquals("[" + (i != 1) + "]", value(child(row, "direct")));
            assertEquals(i != 1, child(row, "visible").isRelevant());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nestedRepeatsUseTheirOwnIndexEvenWhenParentHasAnotherIndex(boolean absolute) throws Exception {
        var entry = repeats(absolute).newEntryController();
        selection(root(entry).getChild("row", 0).getChild("inner", 0), true);
        selection(root(entry).getChild("row", 0).getChild("inner", 1), false);
        selection(root(entry).getChild("row", 1).getChild("inner", 0), true);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void newlyCreatedRepeatUsesItsNewNumericIndex(boolean absolute) throws Exception {
        var entry = repeats(absolute).newEntryController();
        SurveyCtoRepeatContextTest.createRepeat(entry, root(entry).getChild("row", 0));
        var fourth = root(entry).getChild("row", 3);
        assertNotNull(fourth);
        assertEquals(4, value(child(fourth, "member_index")));
        selection(fourth, true);
        assertTrue(child(fourth, "visible").isRelevant());
        SurveyCtoRepeatContextTest.createRepeat(entry, fourth);
        var fifth = root(entry).getChild("row", 4);
        assertNotNull(fifth);
        assertEquals(5, value(child(fifth, "member_index")));
        selection(fifth, false);
        assertFalse(child(fifth, "visible").isRelevant());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changingMembersUpdatesAllRepeatSelectionsInTheirOwnContexts(boolean absolute) throws Exception {
        var entry = repeats(absolute).newEntryController();
        answer(entry, child(root(entry), "valid_members"), new StringData("2"));
        for (int i = 0; i < 3; i++) {
            var row = root(entry).getChild("row", i);
            selection(row, i == 1);
            assertEquals(i == 1, child(row, "visible").isRelevant());
        }
        selection(root(entry).getChild("row", 0).getChild("inner", 0), false);
        selection(root(entry).getChild("row", 0).getChild("inner", 1), true);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void editingARepeatedSecondArgumentDoesNotReadASiblingsValue(boolean absolute) throws Exception {
        var entry = repeats(absolute).newEntryController();
        var first = root(entry).getChild("row", 0);
        var second = root(entry).getChild("row", 1);
        assertEquals("[true]", value(child(first, "by_number")));
        assertEquals("[false]", value(child(second, "by_number")));
        answer(entry, child(first, "number"), new IntegerData(2));
        answer(entry, child(second, "number"), new IntegerData(3));
        assertEquals("[false]", value(child(first, "by_number")));
        assertEquals("[true]", value(child(second, "by_number")));
        answer(entry, child(second, "number"), null);
        assertEquals("[false]", value(child(second, "by_number")));
    }
}
