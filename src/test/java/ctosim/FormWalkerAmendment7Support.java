package ctosim;

import static ctosim.FormWalkerTestSupport.*;

/** Public-API fixtures for spec 07 Amendment 7; no production implementation oracle. */
final class FormWalkerAmendment7Support {
    private FormWalkerAmendment7Support() { }

    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    static SimulationConfig settings7(String extra) throws Exception {
        return config("\"seed\":770031,\"missingRate\":0,\"specialRate\":0"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static String field7(String members) {
        return "\"fields\":{\"answer\":{" + members + "}}";
    }

    static String rule7(String condition, String settings) {
        return "{\"if\":" + quote(condition) + "," + settings + "}";
    }

    static Submission answer7(LoadedForm form, String members) throws Exception {
        return new FormWalker(form, settings7(field7(members))).walk(7);
    }

    static LoadedForm fixture7(String name) throws Exception {
        return loadXml(resource("amendment7/" + name));
    }

    static LoadedForm default7(String type, String initial, String attributes) throws Exception {
        return form("<answer>" + initial + "</answer>", bind("answer", type, attributes), input("answer"));
    }
}
