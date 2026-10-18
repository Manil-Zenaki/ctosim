package ctosim;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryController;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures and assertions derived from spec 07 Amendment 10, without walker internals. */
final class FormWalkerAmendment10Support {
    static final String REPEATED = "string(/data/source/phone)";
    static final String BAD_CONSTRAINT = ". != " + REPEATED;
    static final String SOURCE = "<two>2</two><source jr:template=\"\"><phone/></source>"
            + "<source><phone>11</phone></source><source><phone>22</phone></source>";
    static final String SOURCE_BINDS = bind("two", "int", "") + bind("source/phone", "string", "");
    static final String SOURCE_BODY = "<repeat nodeset=\"/data/source\" jr:count=\"/data/two\">"
            + input("source/phone") + "</repeat>";

    private FormWalkerAmendment10Support() { }

    static LoadedForm errorForm(String instance, String binds, String body) throws Exception {
        return loadXml(resource("amendment10/template.xml")
                .replace("__INSTANCE__", SOURCE + instance)
                .replace("__BINDS__", SOURCE_BINDS + binds)
                .replace("__BODY__", SOURCE_BODY + body));
    }

    static String attr(String name, String expression) {
        return name + "=\"" + expression.replace("&", "&amp;").replace("<", "&lt;")
                .replace("\"", "&quot;") + "\"";
    }

    static LoadedForm failingQuestion(String initial, String attributes) throws Exception {
        return errorForm("<answer>" + initial + "</answer><after/>",
                bind("answer", "string", attr("constraint", BAD_CONSTRAINT) + " " + attributes)
                        + bind("after", "string", ""), input("answer") + input("after"));
    }

    static SimulationConfig config10(String extra) throws Exception {
        return config("\"seed\":701001,\"missingRate\":0,\"specialRate\":0"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static Submission walk10(LoadedForm form) throws Exception {
        return new FormWalker(form, config10("\"fields\":{\"answer\":{\"value\":\"candidate\"},"
                + "\"after\":{\"value\":\"continued\"}}" )).walk(27);
    }

    static FormError onlyError(Submission result, String field) {
        assertEquals(1, result.formErrors().size(), "One record per expression error, without generator retries");
        FormError error = result.formErrors().get(0);
        assertEquals(field, error.field());
        assertDiagnostics(result);
        return error;
    }

    static void assertDiagnostics(Submission result) {
        int previousWarning = -1;
        for (FormError error : result.formErrors()) {
            assertEquals(result.number(), error.submission());
            assertFalse(error.field().isBlank());
            assertTrue(error.path().startsWith("/data"), error.path());
            assertTrue(error.path().contains("/" + error.field()), error.path());
            assertNotNull(error.message());
            assertFalse(error.message().isBlank());
            assertFalse(error.message().contains("\n"));
            assertFalse(error.message().contains("\r"));
            int next = -1;
            for (int i = previousWarning + 1; i < result.warnings().size(); i++) {
                String warning = result.warnings().get(i);
                if (warning.contains(error.field()) && warning.contains(error.message())) {
                    next = i;
                    break;
                }
            }
            assertTrue(next > previousWarning, "Each error must have its own warning in occurrence order: "
                    + result.warnings());
            previousWarning = next;
        }
        for (String warning : result.warnings())
            assertTrue(warning.startsWith("submission " + result.number() + ":"), warning);
    }

    static void assertIndex(String path, String repeat, int index) {
        assertTrue(Pattern.compile("(?:^|/)" + Pattern.quote(repeat) + "\\[" + index + "\\](?:/|$)")
                .matcher(path).find(), path);
    }

    static void assertNotCutShort(Submission result) {
        assertTrue(result.warnings().stream().noneMatch(w -> w.toLowerCase(Locale.ROOT).contains("cut short")),
                result.warnings().toString());
    }

    static LoadedForm multipleFailures() throws Exception {
        // Body order deliberately differs from primary-instance and bind order.
        return errorForm("<first/><second/><between/><after/>",
                bind("first", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("second", "string", attr("constraint", BAD_CONSTRAINT))
                        + bind("between", "string", "") + bind("after", "string", ""),
                input("second") + input("between") + input("first") + input("after"));
    }

    static List<String> fields(Submission result) {
        return result.formErrors().stream().map(FormError::field).toList();
    }

    /** Capture the engine diagnostic independently, by answering through its public API. */
    static RuntimeException engineAnswerFailure(LoadedForm form, String field, String answer) {
        var session = form.newSimulatedSession(new SimulatedClock(LocalDateTime.of(2026, 1, 5, 8, 0)), 701001);
        for (int step = 0; step < 100; step++) {
            int event = session.stepToNextEvent();
            if (event == FormEntryController.EVENT_QUESTION
                    && session.getModel().getQuestionPrompt().getIndex().getReference().getNameLast().equals(field))
                return assertThrows(RuntimeException.class, () -> session.answerQuestion(new StringData(answer), true));
            if (event == FormEntryController.EVENT_END_OF_FORM) break;
        }
        throw new AssertionError("Fixture did not reach " + field);
    }

    static String singleLine(String message) {
        return message.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }
}
