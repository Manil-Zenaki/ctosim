package ctosim;

import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static ctosim.SurveyCtoAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment4ContextTest {
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void nestedRepeatFilteringRetainsTheCallingParentAndGlobalReferences(int parent) throws Exception {
        var session = load(fixture("amendment4-nested.xml")).newEntryController();
        var total = child(root(session).getChild("family", parent), "total");
        assertEquals(parent == 0 ? "[40]" : "[200]", value(total));
        assertEquals(parent == 0 ? "[2]" : "[1]",
                value(child(root(session).getChild("family", parent), "includedCount")));
    }

    @Test void changingOneParentsFilterDoesNotChangeTheOtherParentsResult() throws Exception {
        var session = load(fixture("amendment4-nested.xml")).newEntryController();
        answer(session, child(root(session).getChild("family", 0), "wanted"), new StringData("even"));
        assertEquals("[20]", value(child(root(session).getChild("family", 0), "total")));
        assertEquals("[200]", value(child(root(session).getChild("family", 1), "total")));
    }

    @Test void changingAGlobalPredicateDependencyUpdatesBothParents() throws Exception {
        var session = load(fixture("amendment4-nested.xml")).newEntryController();
        answer(session, child(root(session), "enabled"), new IntegerData(0));
        for (int i = 0; i < 2; i++) assertEquals("[0]", value(child(root(session).getChild("family", i), "total")));
    }

    @ParameterizedTest @CsvSource({"sum,47", "count,3"})
    void engineCreatedRepeatCanBecomeIncludedAfterItsAnswersAreEntered(String function, String expected) throws Exception {
        var session = repeats(conditional(function, ODD), THREE).newEntryController();
        SurveyCtoRepeatContextTest.createRepeat(session, root(session).getChild("row", 0));
        var created = root(session).getChild("row", 3);
        assertNotNull(created);
        answer(session, child(created, "score"), new StringData("7"));
        answer(session, child(created, "kind"), new StringData("odd"));
        assertEquals("[" + expected + "]", value(child(root(session), "result")));
    }
}
