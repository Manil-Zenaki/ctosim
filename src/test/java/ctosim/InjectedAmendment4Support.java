package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 4: attached fixtures and public-result oracles only. */
abstract class InjectedAmendment4Support extends FieldworkAmendment4Support {
    void villageTables() throws Exception {
        for (String name : List.of("districts", "villages", "households")) {
            try (var in = getClass().getResourceAsStream(
                    "/ctosim/injected-problems/amendment4/" + name + ".csv")) {
                assertNotNull(in);
                Files.writeString(media.resolve("a4-" + name + ".csv"),
                        new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
            }
        }
    }

    LoadedForm villageForm(boolean gps) throws Exception { return villageForm(gps, 0); }

    LoadedForm villageForm(boolean gps, int seconds) throws Exception {
        String nodes = METADATA + "<username/>" + (gps ? "<trace_z/>" : "")
                + "<district/><village/><answer/><work/>" + (gps ? "<hidden/><gps_a/><shape/>" : "");
        String binds = METADATA_BINDS
                + bind("username", "string", "jr:preload=\"property\" jr:preloadParams=\"username\"")
                + bind("district", "string", "") + bind("village", "string", "")
                + bind("answer", "string", "")
                + bind("work", "string", "ct:minimum-seconds=\"" + seconds + "\"");
        if (gps) binds += bind("shape", "geoshape", "") + bind("gps_a", "geopoint", "")
                + bind("hidden", "geopoint", "relevant=\"false()\"") + bind("trace_z", "geotrace", "");
        String body = (gps ? input("trace_z") : "")
                + select("district", "search('a4-districts')", "code")
                + select("village", VILLAGE_SEARCH, "code") + control(ID_SEARCH, "code") + input("work")
                + (gps ? input("hidden") + input("gps_a") + input("shape") : "");
        return attached(nodes, binds, body);
    }

    static SimulationConfig cfg(int n, int e, int quota, String extra) throws Exception {
        return InjectedProblemsTestSupport.config(n, e, "\"id\":\"answer\",\"interviewsPerDay\":" + quota
                + ",\"speedViolationRate\":0" + (extra.isEmpty() ? "" : "," + extra));
    }

    static List<InjectedProblem> villageRecords(Run r) {
        return records(r, "travel").stream().filter(p -> p.field().equals("village")).toList();
    }

    static double[] center(String village) {
        return switch (village) {
            case "A" -> new double[]{0, 0};
            case "B" -> new double[]{0, 2};
            case "C" -> new double[]{0, 4};
            case "N" -> new double[]{0, .1};
            default -> throw new AssertionError("Unexpected located village: " + village);
        };
    }

    static void nearVillage(Run r, int n) throws Exception {
        double[] p = center(value(r.submissions().get(n - 1), "village"));
        near(r.visit(n), p[0], p[1]);
    }

    static void travelSchedule(Run r, InjectedProblem p) throws Exception {
        Submission previous = r.related(p), target = r.target(p);
        assertEquals(target.number() - r.config().enumerators(), previous.number(),
                "related interview is this enumerator's predecessor, not the preceding row");
        assertEquals(r.visit(previous.number()).enumerator(), r.visit(target.number()).enumerator());
        assertEquals(day(r, previous.number()), day(r, target.number()));
        long g = gap(previous, target);
        assertTrue(g >= 10 && g <= 40, "travel has no additional move-time charge");
        assertFalse(target.start().isAfter(r.config().start().plusDays(day(r, target.number())).plusHours(9)));
    }

    static void distanceFence(Run r, InjectedProblem p) throws Exception {
        double[] a = center(value(r.related(p), "village")), b = center(value(r.target(p), "village"));
        assertTrue(FieldworkTestSupport.distance(a[0], a[1], b[0], b[1]) + 1e-6
                >= Math.max(20000, 2000 * gap(r.related(p), r.target(p))));
    }

    static void unchanged(Run clean, Run actual) {
        sameRun(clean.submissions(), actual.submissions());
        assertEquals(clean.plan().visits(), actual.plan().visits());
    }

    @FunctionalInterface interface Scenario { boolean matches(Run r) throws Exception; }

    static Run find(LoadedForm form, SimulationConfig config, Scenario scenario) throws Exception {
        for (int seed = 100; seed < 340; seed++) {
            Run r = run(form, config.withSeed(seed));
            if (scenario.matches(r)) return r;
        }
        throw new AssertionError("Fixed seed ensemble did not exercise the required scenario");
    }

    static void firstOfNextDay(Run r, int prior, int next) {
        assertEquals(day(r, prior) + 1, day(r, next));
        long minutes = wholeMinutes(r.config().start().plusDays(day(r, next)),
                r.submissions().get(next - 1).start());
        assertTrue(minutes >= 0 && minutes <= 60);
    }
}
