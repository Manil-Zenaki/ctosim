package ctosim;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;

import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

final class SimulatedSessionTestSupport {
    static final LocalDateTime START = LocalDateTime.of(2040, 2, 28, 23, 59, 58);
    static final long SEED = 0x61A72B39C45DL;
    static final DateTimeFormatter NOW_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", java.util.Locale.ROOT);
    static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private SimulatedSessionTestSupport() {}

    static String fixture(String name) throws Exception {
        try (InputStream in = SimulatedSessionTestSupport.class.getResourceAsStream(
                "/ctosim/simulated-sessions/" + name)) {
            assertNotNull(in, "Missing fixture: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static LoadedForm load() throws Exception { return loadXml(fixture("session.xml")); }

    static LoadedForm loadXml(String xml) throws Exception {
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "simulated-session.xml");
        }
    }

    static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    static LoadedForm expression(String type, String attributes, String actions) throws Exception {
        return expression(type, attributes, actions, "");
    }

    private static LoadedForm expression(String type, String attributes, String actions, String tickActions) throws Exception {
        return loadXml(fixture("expression-template.xml").replace("__TYPE__", type)
                .replace("__ATTRIBUTES__", attributes).replace("__ACTIONS__", actions)
                .replace("__TICK_ACTIONS__", tickActions));
    }

    static LoadedForm valueChangedAction(String type, String call) throws Exception {
        return expression(type, "", "", "<setvalue event=\"xforms-value-changed\" ref=\"/data/result\" value=\""
                + attribute(call) + "\"/>");
    }

    static LoadedForm calculation(String type, String expression) throws Exception {
        return expression(type, "calculate=\"" + attribute(expression) + "\"", "");
    }

    static TreeElement node(FormEntryController session, String path) {
        TreeElement node = session.getModel().getForm().getMainInstance().getRoot();
        for (String name : path.split("/")) {
            node = node.getFirstChild(name);
            assertNotNull(node, "Missing node: " + path);
        }
        return node;
    }

    static Object value(FormEntryController session, String path) {
        var answer = node(session, path).getValue();
        return answer == null ? null : answer.getValue();
    }

    static double number(FormEntryController session, String path) {
        return assertInstanceOf(Number.class, value(session, path), path).doubleValue();
    }

    static LocalDate date(FormEntryController session, String path) {
        return assertInstanceOf(Date.class, value(session, path), path).toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate();
    }

    static void assertTime(FormEntryController session, String path, LocalDateTime expected) {
        Date actual = assertInstanceOf(Date.class, value(session, path), path);
        assertEquals(expected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), actual.getTime(), path);
    }

    static void assertFormattedTime(FormEntryController session, String path, LocalDateTime expected) {
        // Amendment 3: preserve the function's time inside the expression, before calculation storage.
        assertEquals(expected.format(NOW_FORMAT), assertInstanceOf(String.class, value(session, path), path), path);
    }

    static void assertUuid(Object actual) {
        String uuid = assertInstanceOf(String.class, actual);
        assertTrue(uuid.matches(UUID_PATTERN), () -> "Expected lowercase version-4 UUID: " + uuid);
    }

    static void assertRandom(FormEntryController session, String path) {
        double draw = number(session, path);
        assertTrue(Double.isFinite(draw) && draw >= 0 && draw < 1, () -> path + " out of [0,1): " + draw);
    }

    static FormIndex question(FormEntryController session, String path) {
        var target = node(session, path).getRef();
        session.jumpToIndex(FormIndex.createBeginningOfFormIndex());
        for (int i = 0; i < 20; i++) {
            int event = session.stepToNextEvent();
            if (event == EVENT_QUESTION && session.getModel().getForm()
                    .getChildInstanceRef(session.getModel().getFormIndex()).equals(target)) {
                return session.getModel().getFormIndex();
            }
            if (event == EVENT_END_OF_FORM) break;
        }
        throw new AssertionError("No question: " + path);
    }

    static void tick(FormEntryController session, int tick) {
        assertEquals(ANSWER_OK, session.answerQuestion(question(session, "tick"), new IntegerData(tick), true));
    }

    static void answer(FormEntryController session, String text) {
        assertEquals(ANSWER_OK, session.answerQuestion(question(session, "answer"), new StringData(text), true));
    }

    static void finish(FormEntryController session) { session.getModel().getForm().postProcessInstance(); }

    static Map<String, Object> snapshot(FormEntryController session) {
        Map<String, Object> result = new LinkedHashMap<>();
        snapshot(session.getModel().getForm().getMainInstance().getRoot(), "", result);
        return result;
    }

    private static void snapshot(TreeElement node, String parent, Map<String, Object> result) {
        String path = parent + "/" + node.getName() + "[" + node.getMult() + "]";
        Object value = node.getValue() == null ? null : node.getValue().getValue();
        // Copy mutable dates so later changes cannot silently alter the expected snapshot.
        result.put(path, value instanceof Date ? new Date(((Date) value).getTime()) : value);
        for (int i = 0; i < node.getNumChildren(); i++) snapshot(node.getChildAt(i), path, result);
    }
}
