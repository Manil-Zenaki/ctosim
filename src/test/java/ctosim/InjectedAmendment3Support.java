package ctosim;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public-result oracles for spec 14 Amendment 3; no implementation hooks. */
final class InjectedAmendment3Support {
    private InjectedAmendment3Support() { }

    static ProblemRates left(double rate) { return mixed(rate, "", 0); }

    static ProblemRates mixed(double left, String other, double rate, String... faults) {
        return new ProblemRates(other.equals("resent") ? rate : 0, other.equals("reinterview") ? rate : 0,
                other.equals("short") ? rate : 0, other.equals("long") ? rate : 0,
                other.equals("offhours") ? rate : 0, other.equals("overlap") ? rate : 0,
                other.equals("travel") ? rate : 0, left, other.equals("outlier") ? rate : 0, List.of(faults));
    }

    static SimulationConfig settings(int n, int e, String extra) throws Exception {
        return InjectedProblemsTestSupport.config(n, e, "\"speedViolationRate\":0"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    /** Snapshots consume no screen time and expose exactly where the pause occurs. */
    static LoadedForm timeline(int questions, int minimum, int startFields) throws Exception {
        int[] minimums = new int[questions]; Arrays.fill(minimums, minimum);
        return timeline(minimums, startFields);
    }

    static LoadedForm timeline(int[] minimums, int startFields) throws Exception {
        String template;
        try (var in = InjectedAmendment3Support.class.getResourceAsStream(
                "/ctosim/injected-problems/amendment3/timeline-template.xml")) {
            assertNotNull(in); template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder nodes = new StringBuilder(), binds = new StringBuilder(), body = new StringBuilder();
        for (int i = 0; i <= minimums.length; i++) {
            if (i > 0) {
                nodes.append("<q").append(i).append("/>");
                binds.append(bind("q" + i, "int", "ct:minimum-seconds=\"" + minimums[i - 1] + "\""));
                body.append(input("q" + i));
            }
            nodes.append("<s").append(i).append("/><w").append(i).append("/>");
            binds.append(bind("s" + i, "int", "ct:calculate-here=\"duration()\" readonly=\"true()\""))
                    .append(bind("w" + i, "string",
                            "ct:calculate-here=\"format-date-time(now(), '%Y-%m-%dT%H:%M:%S')\""));
            body.append(input("s" + i)).append(input("w" + i));
        }
        String startNodes = startFields == 0 ? "" : "<start_z/>" + (startFields == 2 ? "<start_a/>" : "");
        String startBinds = (startFields == 2 ? startBind("start_a") : "")
                + (startFields == 0 ? "" : startBind("start_z"));
        return loadXml(template.replace("__START_NODES__", startNodes).replace("__START_BINDS__", startBinds)
                .replace("__NODES__", nodes).replace("__BINDS__", binds).replace("__BODY__", body));
    }

    private static String startBind(String name) {
        return bind(name, "dateTime", "jr:preload=\"timestamp\" jr:preloadParams=\"start\"");
    }

    static long screen(Submission s, int i) throws Exception { return Long.parseLong(value(s, "s" + i)); }
    static long total(Submission s) throws Exception { return Long.parseLong(value(s, "screen")); }
    static LocalDateTime wall(Submission s, int i) throws Exception { return LocalDateTime.parse(value(s, "w" + i)); }

    record Interruption(int question, long screenAtPause, LocalDateTime begin, LocalDateTime resume, long seconds) { }

    static Interruption interruption(Submission s, int questions) throws Exception {
        long last = 0; int jumps = 0; Interruption result = null;
        assertEquals(s.start(), wall(s, 0)); assertEquals(0, screen(s, 0));
        for (int i = 1; i <= questions; i++) {
            long offScreen = Duration.between(s.start(), wall(s, i)).getSeconds() - screen(s, i);
            assertTrue(offScreen >= last, "The screen clock cannot overtake or rewind the wall clock");
            if (offScreen > last) {
                jumps++;
                result = new Interruption(i, screen(s, i), s.start().plusSeconds(screen(s, i)), wall(s, i), offScreen);
            }
            last = offScreen;
        }
        assertEquals(1, jumps, "Exactly one pause must be visible in a leftOpen target");
        assertNotNull(result);
        assertEquals(last, seconds(s) - total(s), "Elapsed minus screen time is precisely the pause");
        assertEquals(s.end(), wall(s, questions));
        return result;
    }

    static LocalDateTime opening(Run r, Submission s) {
        long day = Math.floorDiv(Duration.between(r.config().start(), s.start()).getSeconds(), 86400);
        return r.config().start().plusDays(day);
    }

    static long minutesBetween(LocalDateTime from, LocalDateTime to, int min, int max) {
        long minutes = FieldworkTestSupport.wholeMinutes(from, to);
        assertTrue(minutes >= min && minutes <= max, "Expected " + min + ".." + max + " minutes, got " + minutes);
        return minutes;
    }

    static InjectedProblem record(Run r, int n) {
        return records(r, "leftOpen").stream().filter(p -> p.submission() == n).findFirst().orElseThrow();
    }

    /** The spec does not prescribe a seed snapshot; every search must find its scenario. */
    static Run find(LoadedForm form, SimulationConfig config, Predicate<Run> scenario) {
        for (int seed = 100; seed < 260; seed++) {
            Run r = run(form, config.withSeed(seed));
            assertFalse(records(r, "leftOpen").isEmpty(), "The requested leftOpen target is never skipped");
            if (scenario.test(r)) return r;
        }
        throw new AssertionError("Fixed seed ensemble did not exercise the required allocation");
    }

    static void enclosed(Run r, int a, int b, int questions) throws Exception {
        Submission target = r.submissions().get(a - 1), inner = r.submissions().get(b - 1);
        assertEquals(key(inner), record(r, a).relatedKey());
        assertEquals(r.visit(a).enumerator(), r.visit(b).enumerator());
        assertTrue(inner.start().isAfter(target.start()));
        assertTrue(inner.end().isBefore(target.end()));
        Interruption pause = interruption(target, questions);
        assertTrue(inner.start().isAfter(pause.begin()));
        minutesBetween(inner.end(), pause.resume(), 10, 40);
    }
}
