package ctosim;

import java.util.Date;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormLoaderPreloadTestSupport.*;
import static ctosim.FormLoaderTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderMetadataPreloadSessionTest {
    @Test void allPreloadsCoexistInAnInitializedSessionWithoutCallerConfiguration() throws Exception {
        long before = System.currentTimeMillis();
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        long after = System.currentTimeMillis();
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals("simulated", value(session, "operator"));
        for (String field : new String[] {"device", "subscriber", "sim", "phone"}) {
            assertNull(node(session, field).getValue(), field + " must have no answer object");
        }
        assertCurrentTimestamp(session, "started", before, after);
        assertNull(node(session, "finished").getValue());
        identifier(session, "identifier");
    }

    @Test void metadataPreloadsDoNotPreventOrdinaryDefaultsAndCalculations() throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        assertEquals("initial", value(session, "answer"));
        assertEquals(7, value(session, "amount"));
        assertEquals(14, value(session, "doubled"));
    }

    @Test void calculationsCanReadThePreloadedUsernameImmediately() throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        assertEquals("simulated", value(session, "operator_copy"));
    }

    @Test void repeatedPropertyBindsAlsoPopulateNestedInstanceFields() throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        var nested = node(session, "audit").getFirstChild("second_operator");
        assertNotNull(nested);
        assertNotNull(nested.getValue());
        assertEquals("simulated", nested.getValue().getValue());
        assertEquals("simulated", value(session, "operator"));
    }

    @Test void nestedUidBindIsInitializedAlongsideTheTopLevelUid() throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        var nested = node(session, "audit").getFirstChild("second_identifier");
        assertNotNull(nested);
        assertNotNull(nested.getValue());
        assertFalse(assertInstanceOf(String.class, nested.getValue().getValue()).isBlank());
        identifier(session, "identifier");
    }

    @Test void preloadNamesAreNotReportedAsStubbedXPathFunctions() throws Exception {
        LoadedForm form = loadFixture(ALL_PRELOADS);
        assertTrue(form.stubbedFunctions().isEmpty());
        form.newEntryController();
        assertTrue(form.stubbedFunctions().isEmpty());
    }

    @Test void postProcessingMixedMetadataPopulatesEndWithoutChangingStartOrUid() throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        long start = assertInstanceOf(Date.class, value(session, "started")).getTime();
        String uid = identifier(session, "identifier");
        long before = System.currentTimeMillis();
        session.getModel().getForm().postProcessInstance();
        long after = System.currentTimeMillis();
        assertCurrentTimestamp(session, "finished", before, after);
        assertEquals(start, assertInstanceOf(Date.class, value(session, "started")).getTime());
        assertEquals(uid, identifier(session, "identifier"));
    }

    @Test void postProcessingOneSessionDoesNotPopulateEndInOtherSessions() throws Exception {
        LoadedForm form = loadFixture(ALL_PRELOADS);
        var first = form.newEntryController();
        var alreadyOpen = form.newEntryController();
        first.getModel().getForm().postProcessInstance();
        assertNotNull(node(first, "finished").getValue());
        assertNull(node(alreadyOpen, "finished").getValue());
        assertNull(node(form.newEntryController(), "finished").getValue());
    }

    @ParameterizedTest(name = "answering a normal question preserves preloaded {0}")
    @ValueSource(strings = {"operator", "started", "identifier"})
    void answeringAnOrdinaryQuestionDoesNotRegenerateMetadata(String field) throws Exception {
        var session = loadFixture(ALL_PRELOADS).newEntryController();
        Object original = value(session, field);
        assertNotNull(original);
        // Snapshot the Date as a number so an in-place mutation cannot hide a change.
        if (original instanceof Date date) original = date.getTime();
        moveToQuestion(session, "answer");
        assertEquals(ANSWER_OK, session.answerQuestion(new StringData("changed"), true));
        Object after = value(session, field);
        if (after instanceof Date date) after = date.getTime();
        assertEquals(original, after);
    }
}
