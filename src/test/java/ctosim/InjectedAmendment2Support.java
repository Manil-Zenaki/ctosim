package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Amendment 2 fixtures and public-result oracles; never inspects implementation. */
final class InjectedAmendment2Support {
    private InjectedAmendment2Support() { }
    static String consent(double rate) {
        return "\"consent\":{\"fields\":[\"understand\",\"agree\"],\"rate\":" + rate
                + ",\"yes\":\"oui\",\"no\":\"non\"}";
    }
    static LoadedForm resource2(String name) throws Exception {
        return loadXml(text2(name));
    }
    static String text2(String name) throws Exception {
        try (var in = InjectedAmendment2Support.class.getResourceAsStream(
                "/ctosim/injected-problems/amendment2/" + name + ".xml")) {
            assertNotNull(in, name); return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    static LoadedForm extending(boolean indirect, boolean chain, String codes) throws Exception {
        return extending(indirect, chain ? 4 : 2, "if(../more = 'yes', 1, 0)", codes);
    }
    static LoadedForm extending(boolean indirect, int limit, String contribution, String codes) throws Exception {
        // Per-instance calculations keep the count responsive as each repeat answer changes.
        String expression = "min(sum(/data/person/flag) + 1, " + limit + ")";
        String binds = bind("total", "int", "calculate=\"" + expression + "\"")
                + bind("person/flag", "int", "calculate=\"" + contribution + "\"")
                + (indirect ? bind("alias", "int", "calculate=\"/data/total\"") : "");
        String xml = text2("add-another-template").replace("<total/>", "<total/><alias/>")
                .replace("<more/>", "<more/><flag/>")
                .replace("__COUNT__", indirect ? "/data/alias" : "/data/total")
                .replace("__BINDS__", binds).replace("__CHOICES__", codes);
        return loadXml(xml);
    }
    static String items(String... codes) {
        StringBuilder b = new StringBuilder();
        for (String c : codes) b.append("<item><label>").append(c).append("</label><value>").append(c).append("</value></item>");
        return b.toString();
    }
    static void metadata2(Run r) throws Exception {
        List<String> order = List.of("resent", "reinterview", "short", "long", "offhours", "overlap", "travel",
                "outlier", "tooFast", "skipModules", "dontKnow", "fakeRefusal");
        int previous = 0, previousKind = -1;
        for (var p : r.plan().problems()) {
            assertTrue(p.submission() >= previous);
            if (p.submission() != previous) previousKind = -1;
            int kind = order.indexOf(p.problem()); assertTrue(kind >= previousKind);
            previous = p.submission(); previousKind = kind;
            assertEquals(key(r.target(p)), p.key());
            assertEquals(value(r.target(p), "username"), p.enumerator());
            if (kind >= 7) {
                assertFalse(p.deterministic()); assertEquals("", p.relatedKey());
                assertEquals(kind == 7 ? "outlier" : "enumerator", p.family());
                assertEquals(kind == 7 ? "ipacheckoutliers" : "ipacheckenumdb", p.check());
            }
        }
    }
    static void replay2(Run r) {
        var problems = List.copyOf(r.plan().problems()); var visits = List.copyOf(r.plan().visits());
        var warnings = List.copyOf(r.plan().warnings());
        var again = r.plan().walkAll();
        assertEquals(problems, r.plan().problems()); assertEquals(visits, r.plan().visits());
        assertEquals(warnings, r.plan().warnings());
        for (int i = 0; i < again.size(); i++) FieldworkTestSupport.same(r.submissions().get(i), again.get(i));
        assertThrows(UnsupportedOperationException.class, () -> r.plan().problems().clear());
    }
}
