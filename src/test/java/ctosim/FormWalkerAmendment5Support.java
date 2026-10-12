package ctosim;

import java.util.List;
import java.util.Locale;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec-derived fixtures; all interaction with the walker uses its public API. */
final class FormWalkerAmendment5Support {
    private FormWalkerAmendment5Support() { }

    static LoadedForm amended5(String instance, String binds, String body) throws Exception {
        return amended5(instance, binds, body, "");
    }

    static LoadedForm amended5(String instance, String binds, String body, String actions) throws Exception {
        return loadXml(resource("amendment5/template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body).replace("__ACTIONS__", actions));
    }

    static String constraint(String expression) {
        return "constraint=\"" + expression.replace("&", "&amp;").replace("<", "&lt;")
                .replace("\"", "&quot;") + "\"";
    }

    static SimulationConfig settings(String extra) throws Exception {
        return config("\"seed\":815031,\"missingRate\":0,\"specialRate\":0"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static Submission run(LoadedForm form, String extra) throws Exception {
        return new FormWalker(form, settings(extra)).walk(7);
    }

    static LoadedForm late(String initial, String type, String rule, String attributes) throws Exception {
        return amended5("<answer>" + initial + "</answer><gate>0</gate>",
                bind("answer", type, constraint("/data/gate = 0 or (" + rule + ")") + " " + attributes)
                + bind("gate", "int", ""), input("answer") + input("gate"));
    }

    static String gateAnd(String answerOverride) {
        return "\"fields\":{\"gate\":{\"value\":1}"
                + (answerOverride.isEmpty() ? "" : ",\"answer\":{" + answerOverride + "}") + "}";
    }

    static List<String> warningsFor(Submission result, String field) {
        return result.warnings().stream().filter(w -> w.contains(field)).toList();
    }

    static void assertFinalFailure(Submission result, String field, boolean required) {
        List<String> warnings = warningsFor(result, field);
        assertEquals(required ? 2 : 1, warnings.size(), () -> "Warnings for " + field + ": " + warnings);
        String failure = warnings.get(0).toLowerCase(Locale.ROOT);
        assertTrue(failure.contains("constraint") && failure.contains("end"), failure);
        if (required) assertTrue(warnings.get(1).toLowerCase(Locale.ROOT).contains("required"), warnings.toString());
        for (String warning : warnings) assertTrue(warning.startsWith("submission " + result.number() + ":"), warning);
    }

    static String selectControl(String name, boolean multiple) {
        String tag = multiple ? "select" : "select1";
        return "<" + tag + " ref=\"/data/" + name + "\"><label>Choice</label>"
                + "<item><label>Bad</label><value>bad</value></item>"
                + "<item><label>Good</label><value>good</value></item></" + tag + ">";
    }
}
