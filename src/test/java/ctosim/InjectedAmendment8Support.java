package ctosim;

import java.util.*;
import java.util.stream.Collectors;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 8 fixtures and oracles, using public results only. */
final class InjectedAmendment8Support {
    private InjectedAmendment8Support() { }
    static final List<String> SIX = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal", "refuse", "other");
    static String codes(String... codes) {
        return "\"otherCodes\":" + Arrays.stream(codes).map(SimulationConfigTestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
    }
    static String fixed(String... pairs) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) entries.add(SimulationConfigTestSupport.quote(pairs[i])
                + ":{\"value\":" + SimulationConfigTestSupport.quote(pairs[i + 1]) + "}");
        return "\"fields\":{" + String.join(",", entries) + "}";
    }
    static String select(String field, boolean multiple, String... valueLabels) {
        String tag = multiple ? "select" : "select1";
        StringBuilder xml = new StringBuilder("<" + tag + " ref=\"/data/" + field + "\"><label>" + field + "</label>");
        for (int i = 0; i < valueLabels.length; i += 2) xml.append("<item><label>")
                .append(valueLabels[i + 1]).append("</label><value>").append(valueLabels[i]).append("</value></item>");
        return xml.append("</").append(tag).append('>').toString();
    }
    static String relevant(String select, String code) {
        return "relevant=\"selected(/data/" + select + ", '" + code + "')\"";
    }
    static LoadedForm pair(boolean multiple, String selectAttrs, String textAttrs, String... valueLabels) throws Exception {
        return fixture("<answer/><specify/>", bind("answer", multiple ? "select" : "select1", selectAttrs)
                        + bind("specify", "string", relevant("answer", "O") + " " + textAttrs),
                select("answer", multiple, valueLabels) + input("specify"));
    }
    static LoadedForm simple(boolean multiple) throws Exception {
        return pair(multiple, "", "", "a", "abc", "O", "Other", "b", "xyz");
    }
    static Run other(LoadedForm form, int n, String extra) throws Exception {
        return run(form, settings(n, 2, extra).withProblems(rates(0, "other")));
    }
    static List<InjectedProblem> others(Run r) { return records(r, "other"); }
    static void metadata(Run r) throws Exception {
        int previous = 0;
        Set<String> seen = new HashSet<>();
        for (var p : r.plan().problems()) {
            assertTrue(p.submission() >= previous, "submission order"); previous = p.submission();
            if (!p.problem().equals("other")) continue;
            assertEquals("enumerator", p.family());
            assertEquals("ipacheckspecify", p.check());
            assertFalse(p.deterministic());
            assertEquals("", p.relatedKey());
            assertEquals(key(r.target(p)), p.key());
            assertEquals(value(r.target(p), "username"), p.enumerator());
            assertEquals(value(r.target(p), p.field()), p.value());
            assertNotEquals(p.cleanValue(), p.value());
            assertTrue(seen.add(p.submission() + ":" + p.field()), "one record per changed answer");
        }
    }
    static void unchanged(Run r, String field, String expected) throws Exception {
        assertTrue(others(r).stream().noneMatch(p -> p.field().equals(field)), "protected/ineligible answer has no record");
        for (var s : r.submissions()) assertEquals(expected, value(s, field));
    }
    static void singleRecords(Run r, String clean, Set<String> values) throws Exception {
        assertFalse(others(r).isEmpty(), "must observe other injections");
        assertEquals(others(r).size(), targets(r, "other").size());
        String faulty = soleEnumerator(r, "other");
        Set<Integer> changed = targets(r, "other");
        for (var p : others(r)) {
            assertEquals("answer", p.field());
            assertEquals(clean, p.cleanValue());
            assertTrue(values.contains(p.value()), p.toString());
        }
        for (var s : r.submissions()) {
            if (changed.contains(s.number())) assertEquals(faulty, value(s, "username"));
            else assertEquals(clean, value(s, "answer"), "no unrecorded changes or changes by clean enumerators");
        }
        metadata(r);
    }
    static boolean oneNoninitialLetterDeleted(String original, String text) {
        for (int i = 1; i < original.length(); i++) if (Character.isLetter(original.charAt(i))
                && (original.substring(0, i) + original.substring(i + 1)).equals(text)) return true;
        return false;
    }
    static LoadedForm sixFaults() throws Exception {
        String nodes = "<understand/><agree/><gate/><module><a/><b/><c/></module><code/><answer/><specify/>";
        String binds = bind("understand", "select1", "") + bind("agree", "select1", "")
                + bind("gate", "select1", "") + "<bind nodeset=\"/data/module\" relevant=\"/data/gate = 'yes'\"/>"
                + bind("code", "select1", "") + bind("answer", "select1", "")
                + bind("specify", "string", relevant("answer", "O") + " constraint=\". != 'DK' and . != 'RF'\"");
        String body = select("understand", false, "oui", "Yes", "non", "No")
                + select("agree", false, "oui", "Yes", "non", "No")
                + select("gate", false, "yes", "Yes", "no", "No") + "<group ref=\"/data/module\">";
        for (String name : List.of("a", "b", "c")) {
            binds += bind("module/" + name, "string", "constraint=\". != 'DK' and . != 'RF'\"");
            body += input("module/" + name);
        }
        body += "</group>" + select("code", false, "ok", "Ordinary", "DK", "Unknown", "RF", "Refused")
                + select("answer", false, "a", "abc", "O", "Other") + input("specify");
        return fixture(nodes, binds, body);
    }
    static String sixSettings(boolean withOther) {
        return InjectedAmendment2Support.consent(.5) + ",\"dontKnowCodes\":[\"DK\"],\"refuseCodes\":[\"RF\"]"
                + (withOther ? "," + codes("O") : "") + "," + fixed("gate", "yes", "code", "ok", "answer", "a");
    }
}
