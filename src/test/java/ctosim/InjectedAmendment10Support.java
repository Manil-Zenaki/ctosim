package ctosim;

import java.util.*;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.choices;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 10: public API fixtures and independent visit assertions. */
final class InjectedAmendment10Support {
    static final String POINT = "\"area\":{\"minLatitude\":0,\"maxLatitude\":0,\"minLongitude\":0,\"maxLongitude\":0}";
    private InjectedAmendment10Support() { }

    static String visits(String field, String available, String number, double revisit, double unreached, int max) {
        return "\"visits\":{\"field\":\"" + field + "\",\"available\":\"" + available + "\""
                + (number == null ? "" : ",\"number\":\"" + number + "\"")
                + ",\"revisitRate\":" + revisit + ",\"unreachedRate\":" + unreached + ",\"maxVisits\":" + max + "}";
    }

    static SimulationConfig settings10(int n, int e, int quota, double revisit, double unreached, int max, String extra) throws Exception {
        return config(n, e, "\"id\":\"id\",\"interviewsPerDay\":" + quota + ","
                + visits("status", "yes", "attempt", revisit, unreached, max)
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static ProblemRates problem(String name, double rate) {
        if (name.equals("leftOpen")) return new ProblemRates(0, 0, 0, 0, 0, 0, 0, rate, 0, List.of());
        double[] rates = new double[8];
        int index = name.equals("outlier") ? 7 : NAMES.indexOf(name);
        rates[index] = rate;
        return new ProblemRates(rates[0], rates[1], rates[2], rates[3], rates[4], rates[5], rates[6], rates[7], List.of());
    }

    static LoadedForm tracking() throws Exception {
        try (var in = InjectedAmendment10Support.class.getResourceAsStream(
                "/ctosim/injected-problems/amendment10/tracking.xml")) {
            assertNotNull(in); return FormLoader.load(in, "tracking.xml");
        }
    }

    static void successfulFirstTargets(Run r, String kind) throws Exception {
        var selected = targets(r, kind);
        assertFalse(selected.isEmpty(), "the fixture must exercise " + kind);
        for (int n : selected) {
            Visit visit = r.visit(n);
            assertEquals(1, visit.attempt(), "target is not a revisit: " + n);
            assertTrue(visit.found(), "target household is found: " + n);
            assertEquals("yes", value(r.submissions().get(n - 1), "status"));
            assertEquals("1", value(r.submissions().get(n - 1), "attempt"));
        }
    }

    static void noTrackingRecords(Run r) {
        for (var p : r.plan().problems())
            assertFalse(Set.of("status", "attempt").contains(p.field()), "tracking answers have no problem record: " + p);
    }

    static void trackingAnswers(Run r) throws Exception {
        for (var s : r.submissions()) {
            Visit v = r.visit(s.number());
            assertEquals(Integer.toString(v.attempt()), value(s, "attempt"));
            assertEquals(v.found() ? "yes" : "absent", value(s, "status"));
        }
    }

    static LoadedForm protectedCodes(String numberType, boolean selectNumber) throws Exception {
        return fixture("<id/><status/><attempt/><detail/><answer/>",
                bind("id", "int", "") + bind("status", "select1", "")
                        + bind("attempt", numberType, "")
                        + bind("detail", "string", "relevant=\"/data/status = 'O'\"")
                        + bind("answer", "string", ""),
                input("id") + choices("status", false, "yes", "absent", "DK", "RF", "O")
                        + (selectNumber ? choices("attempt", false, "1", "999", "O") : input("attempt"))
                        + input("detail") + input("answer"));
    }

    static void replay(Run r) {
        var visits = List.copyOf(r.plan().visits());
        var problems = List.copyOf(r.plan().problems());
        var warnings = List.copyOf(r.plan().warnings());
        var again = r.plan().walkAll();
        assertEquals(visits, r.plan().visits()); assertEquals(problems, r.plan().problems());
        assertEquals(warnings, r.plan().warnings());
        for (int i = 0; i < again.size(); i++) FieldworkTestSupport.same(r.submissions().get(i), again.get(i));
        var fresh = run(r.form(), r.config());
        assertEquals(visits, fresh.plan().visits()); assertEquals(problems, fresh.plan().problems());
        assertEquals(warnings, fresh.plan().warnings());
        for (int i = 0; i < again.size(); i++) FieldworkTestSupport.same(again.get(i), fresh.submissions().get(i));
    }
}
