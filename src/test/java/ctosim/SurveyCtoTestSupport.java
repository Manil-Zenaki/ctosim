package ctosim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xpath.XPathParseTool;

import static org.junit.jupiter.api.Assertions.*;

final class SurveyCtoTestSupport {
    private SurveyCtoTestSupport() {}

    static String fixture(String name) throws Exception {
        try (var in = SurveyCtoTestSupport.class.getResourceAsStream("/ctosim/surveycto-functions/" + name)) {
            assertNotNull(in, "Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static LoadedForm load(String xml) throws Exception {
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "surveycto-functions.xml");
        }
    }

    static LoadedForm expressionForm(String expression) throws Exception {
        return load(fixture("expression.xml").replace("__EXPRESSION__", attribute(expression)));
    }

    static String attribute(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static Object evaluate(FormEntryController session, String expression) throws Exception {
        FormDef form = session.getModel().getForm();
        return XPathParseTool.parseXPath(expression).eval(form.getMainInstance(), form.getEvaluationContext());
    }

    static Object evaluateAt(FormEntryController session, TreeElement context, String expression) throws Exception {
        FormDef form = session.getModel().getForm();
        var scoped = new EvaluationContext(form.getEvaluationContext(), context.getRef());
        return XPathParseTool.parseXPath(expression).eval(form.getMainInstance(), scoped);
    }

    static TreeElement root(FormEntryController session) {
        return session.getModel().getForm().getMainInstance().getRoot();
    }

    static TreeElement child(TreeElement parent, String name) {
        TreeElement node = parent.getFirstChild(name);
        assertNotNull(node, "Missing child " + name + " of " + parent.getRef());
        return node;
    }

    static Object value(TreeElement node) {
        return node.getValue() == null ? null : node.getValue().getValue();
    }

    static void assertValue(Object expected, Object actual) {
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass(), "XPath result type");
        assertEquals(expected, actual);
    }

    static FormIndex questionIndex(FormEntryController session, TreeElement target) {
        var model = session.getModel();
        FormIndex index = FormIndex.createBeginningOfFormIndex();
        for (int i = 0; i < 10000; i++) {
            index = model.incrementIndex(index);
            if (index.isEndOfFormIndex()) break;
            if (model.getEvent(index) == FormEntryController.EVENT_QUESTION
                    && model.getForm().getChildInstanceRef(index).equals(target.getRef())) return index;
        }
        throw new AssertionError("No question for " + target.getRef());
    }

    static void answer(FormEntryController session, TreeElement node, IAnswerData answer) {
        assertEquals(FormEntryController.ANSWER_OK,
                session.answerQuestion(questionIndex(session, node), answer, true));
    }
}
