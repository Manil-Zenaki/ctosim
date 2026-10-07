package ctosim;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.xpath.XPathParseTool;

import static org.junit.jupiter.api.Assertions.*;

final class FormLoaderTestSupport {
    private FormLoaderTestSupport() {}

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = FormLoaderTestSupport.class.getResourceAsStream("/ctosim/form-loader/" + name)) {
            assertNotNull(in, "Missing test fixture: " + name);
            return in.readAllBytes();
        }
    }

    static String fixtureText(String name) throws IOException {
        return new String(fixture(name), StandardCharsets.UTF_8);
    }

    static LoadedForm loadFixture(String name) throws Exception {
        try (InputStream in = new ByteArrayInputStream(fixture(name))) {
            return FormLoader.load(in, name);
        }
    }

    static LoadedForm loadXml(String xml, String source) throws FormLoadException {
        return FormLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), source);
    }

    static LoadedForm expressionForm(String expression) throws Exception {
        return loadXml(fixtureText("expression-template.xml").replace("__EXPRESSION__", attribute(expression)),
                "expression.xml");
    }

    static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static TreeElement node(FormEntryController session, String name) {
        TreeElement node = session.getModel().getForm().getMainInstance().getRoot().getFirstChild(name);
        assertNotNull(node, "Missing instance node: " + name);
        return node;
    }

    static Object value(FormEntryController session, String name) {
        var answer = node(session, name).getValue();
        return answer == null ? null : answer.getValue();
    }

    static Object evaluate(FormEntryController session, String expression) throws Exception {
        FormDef form = session.getModel().getForm();
        return XPathParseTool.parseXPath(expression).eval(form.getMainInstance(), form.getEvaluationContext());
    }

    static void moveToQuestion(FormEntryController session, String name) {
        moveToQuestion(session, node(session, name));
    }

    static void moveToQuestion(FormEntryController session, TreeElement target) {
        // All fixtures have fewer than ten events; the bound prevents a broken session from hanging the suite.
        for (int i = 0; i < 30; i++) {
            int event = session.stepToNextEvent();
            if (event == FormEntryController.EVENT_QUESTION
                    && session.getModel().getForm().getChildInstanceRef(session.getModel().getFormIndex())
                    .equals(target.getRef())) {
                return;
            }
            if (event == FormEntryController.EVENT_END_OF_FORM) break;
        }
        fail("No question for " + target.getRef());
    }
}
