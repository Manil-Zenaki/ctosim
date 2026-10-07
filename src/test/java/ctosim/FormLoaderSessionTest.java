package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ctosim.FormLoaderTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderSessionTest {
    @TempDir Path temp;

    @Test void everyNewSessionStartsAtBeginningOfForm() throws Exception {
        LoadedForm form = loadFixture("sessions.xml");
        FormEntryController first = form.newEntryController();
        assertEquals(EVENT_BEGINNING_OF_FORM, first.getModel().getEvent());
        moveToQuestion(first, "answer");
        assertEquals(EVENT_BEGINNING_OF_FORM, form.newEntryController().getModel().getEvent());
        assertEquals(EVENT_QUESTION, first.getModel().getEvent(), "Opening a session must not reset another session");
    }

    @Test void defaultValuesArePresentBeforeNavigation() throws Exception {
        FormEntryController session = loadFixture("sessions.xml").newEntryController();
        assertEquals("initial", value(session, "answer"));
        assertEquals(7, value(session, "amount"));
    }

    @Test void calculationsAndTheirDependenciesAreEvaluatedBeforeNavigation() throws Exception {
        FormEntryController session = loadFixture("sessions.xml").newEntryController();
        assertEquals(14, value(session, "doubled"));
        assertEquals("initial:14", value(session, "summary"));
    }

    @Test void answersAreInvisibleToSessionsOpenedBeforeAndAfterTheAnswer() throws Exception {
        LoadedForm form = loadFixture("sessions.xml");
        FormEntryController first = form.newEntryController();
        FormEntryController alreadyOpen = form.newEntryController();
        moveToQuestion(first, "answer");
        assertEquals(ANSWER_OK, first.answerQuestion(new StringData("changed — 東京"), true));
        assertEquals("changed — 東京", value(first, "answer"));
        assertEquals("initial", value(alreadyOpen, "answer"));
        FormEntryController openedLater = form.newEntryController();
        assertEquals("initial", value(openedLater, "answer"));
        moveToQuestion(alreadyOpen, "answer");
        assertEquals(ANSWER_OK, alreadyOpen.answerQuestion(new StringData("second"), true));
        assertEquals("changed — 東京", value(first, "answer"));
        assertEquals("initial", value(openedLater, "answer"));
    }

    @Test void recalculationInOneSessionDoesNotChangeAnotherInstance() throws Exception {
        LoadedForm form = loadFixture("sessions.xml");
        FormEntryController first = form.newEntryController();
        FormEntryController second = form.newEntryController();
        moveToQuestion(first, "amount");
        assertEquals(ANSWER_OK, first.answerQuestion(new IntegerData(11), true));
        assertEquals(22, value(first, "doubled"));
        assertEquals("initial:22", value(first, "summary"));
        assertEquals(7, value(second, "amount"));
        assertEquals(14, value(second, "doubled"));
        assertEquals("initial:14", value(second, "summary"));
        assertEquals(14, value(form.newEntryController(), "doubled"));
    }

    @Test void sessionsHaveDistinctBackingInstances() throws Exception {
        LoadedForm form = loadFixture("minimal.xml");
        FormEntryController a = form.newEntryController();
        FormEntryController b = form.newEntryController();
        assertNotSame(a, b);
        assertNotSame(a.getModel().getForm().getMainInstance(), b.getModel().getForm().getMainInstance());
        assertNotSame(node(a, "answer"), node(b, "answer"));
    }

    @Test void answersInsideNestedRepeatsAreAlsoIsolated() throws Exception {
        LoadedForm form = loadFixture("nested-sessions.xml");
        FormEntryController first = form.newEntryController();
        FormEntryController second = form.newEntryController();
        TreeElement firstName = memberName(first);
        assertEquals("Initial member", firstName.getValue().getValue());
        moveToQuestion(first, firstName);
        assertEquals(ANSWER_OK, first.answerQuestion(new StringData("Changed member"), true));
        assertEquals("Changed member", firstName.getValue().getValue());
        assertEquals("Initial member", memberName(second).getValue().getValue());
        assertEquals("Initial member", memberName(form.newEntryController()).getValue().getValue());
    }

    private static TreeElement memberName(FormEntryController session) {
        TreeElement member = node(session, "household").getChild("member", 0);
        assertNotNull(member, "The initial repeat instance must be present");
        TreeElement name = member.getFirstChild("name");
        assertNotNull(name);
        return name;
    }

    @Test void manySessionsRemainUsableAndRetainTheirOwnAnswers() throws Exception {
        LoadedForm form = loadFixture("sessions.xml");
        List<FormEntryController> sessions = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            FormEntryController session = form.newEntryController();
            assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
            assertEquals("initial", value(session, "answer"));
            moveToQuestion(session, "answer");
            assertEquals(ANSWER_OK, session.answerQuestion(new StringData("answer-" + i), true));
            sessions.add(session);
        }
        for (int i = 0; i < sessions.size(); i++) {
            assertEquals("answer-" + i, value(sessions.get(i), "answer"));
        }
        assertEquals("initial", value(form.newEntryController(), "answer"));
    }

    @Test void sessionsCanBeOpenedAfterTheCallerClosesTheSourceStream() throws Exception {
        LoadedForm form;
        try (var in = new FormLoaderLoadingTest.TrackedStream(fixture("sessions.xml"), 8)) {
            form = FormLoader.load(in, "closed-source.xml");
        }
        assertEquals("initial:14", value(form.newEntryController(), "summary"));
        assertEquals("initial:14", value(form.newEntryController(), "summary"));
    }

    @Test void sessionsUseTheLoadedDefinitionEvenAfterItsFileIsRemoved() throws Exception {
        Path source = Files.write(temp.resolve("sessions.xml"), fixture("sessions.xml"));
        LoadedForm form = FormLoader.load(source);
        Files.delete(source);
        assertEquals("initial:14", value(form.newEntryController(), "summary"));
    }
}
