package ctosim;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box fixtures for spec 07 Amendment 9; all children are read-only. */
final class FormWalkerAmendment9Support {
    private FormWalkerAmendment9Support() { }

    static String attribute9(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    static String row9(String name) {
        return "<" + name + " jr:template=\"\"><marker>kept</marker></" + name + ">";
    }

    static String rowBind9(String name) {
        return bind(name + "/marker", "string", "readonly=\"true()\"");
    }

    static String repeat9(String name, String countPath) {
        return "<repeat nodeset=\"/data/" + name + "\" jr:count=\"" + attribute9(countPath) + "\">"
                + input(name + "/marker") + "</repeat>";
    }

    static LoadedForm direct9(String type, String initial, String attributes, String control) throws Exception {
        return form("<answer>" + initial + "</answer>" + row9("row"),
                bind("answer", type, attributes) + rowBind9("row"), control + repeat9("row", "/data/answer"));
    }

    static LoadedForm direct9() throws Exception {
        return direct9("int", "", "required=\"true()\"", input("answer"));
    }

    static SimulationConfig settings9(int min, int max, String extra) throws Exception {
        return config("\"seed\":907731,\"missingRate\":0,\"specialRate\":0,"
                + "\"integerRange\":{\"min\":11,\"max\":11},"
                + "\"repeats\":{\"min\":" + min + ",\"max\":" + max + "}"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static String field9(String members) {
        return "\"fields\":{\"answer\":{" + members + "}}";
    }

    static Submission run9(LoadedForm form, int min, int max, String extra) throws Exception {
        return new FormWalker(form, settings9(min, max, extra)).walk(9);
    }

    static void assertCount9(Submission result, int expected) throws Exception {
        assertEquals(Integer.toString(expected), value(result, "answer"));
        assertEquals(expected, count(result, "row"), "The generated count must control actual instances");
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }
}
