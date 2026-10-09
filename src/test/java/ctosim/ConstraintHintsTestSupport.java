package ctosim;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.javarosa.form.api.FormEntryController;
import static org.junit.jupiter.api.Assertions.*;

final class ConstraintHintsTestSupport {
    static final LocalDate TODAY = LocalDate.of(2040, 3, 1);
    static final long SEED = 0x09C0FFEE;

    private ConstraintHintsTestSupport() { }

    static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    static LoadedForm extractionForm(String type, String expression) throws Exception {
        try (var in = ConstraintHintsTestSupport.class.getResourceAsStream(
                "/ctosim/constraint-hints/extraction.xml")) {
            assertNotNull(in);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__TYPE__", type).replace("__CONSTRAINT__", attribute(expression));
            return FormWalkerTestSupport.loadXml(xml);
        }
    }

    static FormEntryController session(String type, String expression) throws Exception {
        var session = extractionForm(type, expression).newSimulatedSession(
                new SimulatedClock(LocalDateTime.of(2040, 3, 1, 12, 30)), SEED);
        SimulatedSessionTestSupport.question(session, "group/answer");
        return session;
    }

    static ConstraintHints extract(String expression) throws Exception {
        var session = session("string", expression);
        return assertDoesNotThrow(() -> ConstraintHints.forCurrentQuestion(session), expression);
    }

    static ConstraintHints numeric(Double lower, boolean li, Double upper, boolean ui) {
        return new ConstraintHints(lower == null ? null : new Bound(lower, li),
                upper == null ? null : new Bound(upper, ui), null, null, null, null, null);
    }

    static ConstraintHints dates(LocalDate lower, boolean li, LocalDate upper, boolean ui) {
        return new ConstraintHints(null, null, lower == null ? null : new DateBound(lower, li),
                upper == null ? null : new DateBound(upper, ui), null, null, null);
    }

    static ConstraintHints text(Integer min, Integer max, String pattern) {
        return new ConstraintHints(null, null, null, null, min, max, pattern);
    }

    static Question question(QuestionKind kind, ConstraintHints hints) {
        return question(kind, "", true, hints);
    }

    static Question question(QuestionKind kind, String appearance, boolean required, ConstraintHints hints) {
        List<String> choices = kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE
                ? List.of("a", "b", "c") : List.of();
        return new Question("answer", kind, choices, appearance, required, hints);
    }

    static AnswerGenerator generator(String members) throws ConfigException {
        return new AnswerGenerator(SimulationConfig.parse("{" + members + "}", "constraint-hints.json"));
    }
}
