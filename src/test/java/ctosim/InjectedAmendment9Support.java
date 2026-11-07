package ctosim;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.w3c.dom.Document;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 9 oracles: only public API and serialized instances. */
final class InjectedAmendment9Support {
    static final String FAULT = "outdatedVersion";
    static final String NEW = "2601051230";
    static final String OLD = "2601051229";
    static final LocalDate UPDATE = LocalDate.of(2026, 3, 4);
    static final List<String> SEVEN = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse", "other", FAULT);

    private InjectedAmendment9Support() { }

    static String update(String date, String previous) {
        return "\"formUpdate\":{\"date\":" + SimulationConfigTestSupport.quote(date)
                + (previous == null ? "" : ",\"previousVersion\":" + SimulationConfigTestSupport.quote(previous)) + "}";
    }

    static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    static LoadedForm versioned(String version, String nodes, String binds, String body) throws Exception {
        try (var in = InjectedAmendment9Support.class.getResourceAsStream(
                "/ctosim/injected-problems/amendment9/form-template.xml")) {
            assertNotNull(in);
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__VERSION__", version == null ? "" : "version=\"" + attribute(version) + "\"")
                    .replace("__INSTANCE__", nodes).replace("__BINDS__", binds).replace("__BODY__", body);
            return loadXml(source);
        }
    }

    static LoadedForm simple(String version) throws Exception {
        return versioned(version, "<answer/>", bind("answer", "int", ""), input("answer"));
    }

    static LoadedForm timed(int minimum) throws Exception {
        return versioned(NEW, "<answer/>", bind("answer", "int", "ct:minimum-seconds=\"" + minimum + "\""), input("answer"));
    }

    static String version(Submission s) throws Exception {
        return xml(s).getDocumentElement().getAttribute("version");
    }

    static List<InjectedProblem> outdated(Run r) { return records(r, FAULT); }

    static void onlyVersionMayDiffer(Run clean, Run changed) throws Exception {
        assertEquals(clean.plan().visits(), changed.plan().visits(), "version cannot change the plan");
        assertEquals(clean.submissions().size(), changed.submissions().size());
        for (int i = 0; i < clean.submissions().size(); i++) {
            Submission a = clean.submissions().get(i), b = changed.submissions().get(i);
            assertEquals(a.number(), b.number());
            assertEquals(a.start(), b.start()); assertEquals(a.end(), b.end());
            assertEquals(a.submitted(), b.submitted()); assertEquals(a.answered(), b.answered());
            assertEquals(a.warnings(), b.warnings());
            Document left = xml(a), right = xml(b);
            left.getDocumentElement().removeAttribute("version");
            right.getDocumentElement().removeAttribute("version");
            assertTrue(left.getDocumentElement().isEqualNode(right.getDocumentElement()),
                    "all answers, metadata and non-version attributes stay equal for submission " + a.number());
        }
    }

    static void normalVersions(Run r, LocalDate date, String previous, String current) throws Exception {
        for (var s : r.submissions()) {
            assertTrue(xml(s).getDocumentElement().hasAttribute("version"), "version is stored in every instance");
            assertEquals(s.start().toLocalDate().isBefore(date) ? previous : current, version(s),
                    "version follows interview start, submission " + s.number());
        }
    }

    static void metadata(Run r, String previous, String current) throws Exception {
        int last = 0;
        for (var p : outdated(r)) {
            assertTrue(p.submission() > last, "one version record per submission, in number order"); last = p.submission();
            assertEquals("enumerator", p.family()); assertEquals("ipacheckversions", p.check());
            assertTrue(p.deterministic()); assertEquals("formdef_version", p.field());
            assertEquals(previous, p.value()); assertEquals(current, p.cleanValue());
            assertEquals("", p.relatedKey());
            assertEquals(key(r.target(p)), p.key()); assertFalse(p.key().isEmpty());
            assertEquals(value(r.target(p), "username"), p.enumerator());
            assertEquals(version(r.target(p)), p.value());
        }
    }

    static LoadedForm sevenFaultForm() throws Exception {
        String nodes = "<understand/><agree/><gate/><module><a/><b/><c/></module><code/><answer/><specify/>";
        String binds = bind("understand", "select1", "") + bind("agree", "select1", "")
                + bind("gate", "select1", "") + "<bind nodeset=\"/data/module\" relevant=\"/data/gate = 'yes'\"/>"
                + bind("code", "select1", "") + bind("answer", "select1", "")
                + bind("specify", "string", InjectedAmendment8Support.relevant("answer", "O")
                        + " constraint=\". != 'DK' and . != 'RF'\"");
        String body = choices("understand", false, "oui", "non") + choices("agree", false, "oui", "non")
                + choices("gate", false, "yes", "no") + "<group ref=\"/data/module\">";
        for (String name : List.of("a", "b", "c")) {
            binds += bind("module/" + name, "string", "constraint=\". != 'DK' and . != 'RF'\"");
            body += input("module/" + name);
        }
        body += "</group>" + choices("code", false, "ok", "DK", "RF")
                + InjectedAmendment8Support.select("answer", false, "a", "abc", "O", "Other") + input("specify");
        return versioned(NEW, nodes, binds, body);
    }
}
