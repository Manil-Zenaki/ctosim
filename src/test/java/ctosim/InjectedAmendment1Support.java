package ctosim;

import java.util.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box fixtures and spec-derived oracles for spec 14 Amendment 1 only. */
final class InjectedAmendment1Support {
    static final List<String> FAULTS = List.of("tooFast", "skipModules", "dontKnow");
    static final List<String> ORDER = List.of("resent", "reinterview", "short", "long", "offhours",
            "overlap", "travel", "outlier", "tooFast", "skipModules", "dontKnow");
    private InjectedAmendment1Support() { }

    static ProblemRates rates(double outliers, String... faults) {
        return new ProblemRates(0, 0, 0, 0, 0, 0, 0, outliers, List.of(faults));
    }
    static SimulationConfig settings(int n, int e, String extra) throws Exception {
        return SimulationConfig.parse("{\"seed\":819731,\"submissions\":" + n + ",\"enumerators\":" + e
                + ",\"missingRate\":0,\"specialRate\":0,\"speedViolationRate\":0,"
                + "\"start\":\"2026-03-02T08:00:00\",\"timeZone\":\"+03:00\",\"fieldworkDays\":1000,"
                + "\"integerRange\":{\"min\":10,\"max\":20},\"decimalRange\":{\"min\":10,\"max\":20}"
                + (extra.isEmpty() ? "" : "," + extra) + "}", "amendment1.json");
    }
    static String choices(String path, boolean multiple, String... codes) {
        String tag = multiple ? "select" : "select1";
        StringBuilder b = new StringBuilder("<" + tag + " ref=\"/data/" + path + "\"><label>" + path + "</label>");
        for (String code : codes) b.append("<item><label>").append(code).append("</label><value>")
                .append(code).append("</value></item>");
        return b.append("</").append(tag).append('>').toString();
    }
    static LoadedForm number(String type, String attrs) throws Exception {
        return fixture("<amount/>", bind("amount", type, attrs), input("amount"));
    }
    static LoadedForm module(int size, boolean nested, boolean repeat, String condition, String... codes) throws Exception {
        String path = nested ? "module/inside" : "module";
        StringBuilder nodes = new StringBuilder("<gate/><module" + (repeat ? " jr:template=\"\"" : "") + ">");
        if (nested) nodes.append("<inside>");
        StringBuilder binds = new StringBuilder(bind("gate", "select1", "")
                + "<bind nodeset=\"/data/module\" relevant=\"" + condition + "\"/>");
        StringBuilder body = new StringBuilder(choices("gate", false, codes));
        body.append(repeat ? "<repeat nodeset=\"/data/module\">" : "<group ref=\"/data/module\">");
        if (nested) body.append("<group ref=\"/data/module/inside\">");
        for (int i = 0; i < size; i++) {
            nodes.append("<q").append(i).append("/>");
            binds.append(bind(path + "/q" + i, "string", "")); body.append(input(path + "/q" + i));
        }
        if (nested) { nodes.append("</inside>"); body.append("</group>"); }
        nodes.append("</module>"); body.append(repeat ? "</repeat>" : "</group>");
        return fixture(nodes.toString(), binds.toString(), body.toString());
    }
    static LoadedForm roster(boolean counted, String attrs) throws Exception {
        return fixture((counted ? "<size/>" : "") + "<person jr:template=\"\"><name/></person>",
                (counted ? bind("size", "int", attrs) : "") + bind("person/name", "string", ""),
                (counted ? input("size") : "") + "<repeat nodeset=\"/data/person\""
                        + (counted ? " jr:count=\"/data/size\"" : "") + ">" + input("person/name") + "</repeat>");
    }
    static Set<String> enumerators(Run r, String fault) {
        Set<String> names = new TreeSet<>(); records(r, fault).forEach(p -> names.add(p.enumerator())); return names;
    }
    static String soleEnumerator(Run r, String fault) {
        Set<String> names = enumerators(r, fault); assertEquals(1, names.size(), "one assigned enumerator: " + fault);
        return names.iterator().next();
    }
    static List<Submission> interviews(Run r, String enumerator) throws Exception {
        List<Submission> result = new ArrayList<>();
        for (var s : r.submissions()) if (value(s, "username").equals(enumerator)) result.add(s);
        return result;
    }
    static void rate(int actual, int draws, double probability) {
        assertTrue(draws > 0, "the statistical test must have opportunities");
        double tolerance = 7 * Math.sqrt(draws * probability * (1 - probability)) + 2;
        assertEquals(draws * probability, actual, tolerance, "observed replacements / eligible opportunities");
    }
    static void newMetadata(Run r) throws Exception {
        int lastSubmission = 0, lastKind = -1;
        for (var p : r.plan().problems()) {
            assertTrue(p.submission() >= lastSubmission);
            if (p.submission() != lastSubmission) lastKind = -1;
            int kind = ORDER.indexOf(p.problem()); assertTrue(kind >= lastKind); lastKind = kind; lastSubmission = p.submission();
            assertEquals(key(r.target(p)), p.key()); assertEquals(value(r.target(p), "username"), p.enumerator());
            if (kind < 7) continue;
            assertFalse(p.deterministic());
            assertEquals(kind == 7 ? "outlier" : "enumerator", p.family());
            assertEquals(kind == 7 ? "ipacheckoutliers" : "ipacheckenumdb", p.check());
            assertEquals("", p.relatedKey());
            if (!p.field().isEmpty() && !p.field().equals("person")) assertEquals(value(r.target(p), p.field()), p.value());
        }
    }
    static LoadedForm allFaults() throws Exception {
        // A fixed ordinary answer makes each Bernoulli opportunity observable; faults still act after that draw.
        try (var in = InjectedAmendment1Support.class.getResourceAsStream("/ctosim/injected-problems/amendment1/all-faults.xml")) {
            assertNotNull(in); return FormLoader.load(in, "all-faults.xml");
        }
    }
    static String allFaultSettings() {
        return "\"specialValues\":[\"-99\"],\"fields\":{\"gate\":{\"value\":\"yes\"},\"code\":{\"value\":\"ok\"}}";
    }
}
