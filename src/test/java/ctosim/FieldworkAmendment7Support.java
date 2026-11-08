package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box fixtures for spec 13 Amendment 7; no implementation oracle. */
final class FieldworkAmendment7Support {
    private FieldworkAmendment7Support() { }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    static String field(String name, String members) { return quote(name) + ":{" + members + "}"; }
    static String expr(String expression) { return "\"expr\":" + quote(expression); }
    static String byAttempt(String first, String later) {
        return "\"value\":" + quote(first) + ",\"when\":[{\"if\":\"/data/attempt > 1\",\"value\":"
                + quote(later) + "}]";
    }

    static SimulationConfig config7(int n, int enumerators, int quota, int max,
            boolean eventuallyFound, String extra) throws Exception {
        return FieldworkAmendment5Support.config5(n, enumerators, quota,
                eventuallyFound ? 1 : 0, eventuallyFound ? 0 : 1, max, extra);
    }

    static SimulationConfig config7(String fields) throws Exception {
        return config7(3, 1, 1, 3, false, fields.isEmpty() ? "" : "\"fields\":{" + fields + "}");
    }

    static String choices(String path, boolean multiple, String... codes) {
        String tag = multiple ? "select" : "select1";
        StringBuilder b = new StringBuilder("<" + tag + " ref=\"/data/" + path + "\"><label>Choice</label>");
        for (String code : codes) b.append("<item><label>").append(code)
                .append("</label><value>").append(code).append("</value></item>");
        return b.append("</").append(tag).append('>').toString();
    }

    static LoadedForm form7(String nodes, String binds, String before, String after) throws Exception {
        return form7(nodes, binds, before, after, "");
    }

    static LoadedForm form7(String nodes, String binds, String before, String after, String secondary) throws Exception {
        try (var in = FieldworkAmendment7Support.class.getResourceAsStream(
                "/ctosim/fieldwork-plan/amendment7/form-template.xml")) {
            assertNotNull(in);
            return loadXml(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__NODES__", nodes).replace("__BINDS__", binds)
                    .replace("__BEFORE__", before).replace("__AFTER__", after)
                    .replace("__SECONDARY__", secondary));
        }
    }

    static LoadedForm one(String type, String attrs, String control) throws Exception {
        return form7("<identity/>", bind("identity", type, attrs), control, "");
    }

    static LoadedForm textForm() throws Exception { return one("string", "", input("identity")); }

    static List<Submission> three(LoadedForm form, String fields) throws Exception {
        var plan = new Fieldwork(form, config7(fields));
        var rows = plan.walkAll();
        assertEquals(List.of(1, 2, 3), plan.visits().stream().map(Visit::attempt).toList(),
                "fixture must revisit one household twice");
        return rows;
    }

    static void values(List<Submission> rows, String path, String... expected) throws Exception {
        List<String> actual = new ArrayList<>();
        for (var row : rows) actual.add(value(row, path));
        assertEquals(List.of(expected), actual, path);
    }

    static void repeatsFirst(List<Submission> rows, String path) throws Exception {
        String first = value(rows.get(0), path);
        for (int i = 1; i < rows.size(); i++) assertEquals(first, value(rows.get(i), path),
                path + " on revisit " + (i + 1));
    }
}
