package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static org.junit.jupiter.api.Assertions.*;

/** Only public APIs from specs 07, 13 and 14; no private pause hook is assumed. */
final class FormWalkerAmendment12Support {
    private FormWalkerAmendment12Support() { }

    static final String TOTALS = "<elapsed/><twice/><dependent/><frozen/>";
    static final String TOTAL_BINDS = bind("elapsed", "int", "calculate=\"duration()\"")
            + bind("twice", "int", "calculate=\"2 * duration()\"")
            + bind("dependent", "int", "calculate=\"/data/elapsed + 7\"")
            + bind("frozen", "int", "calculate=\"once(duration())\"");

    static LoadedForm fixture(String nodes, String binds, String body) throws Exception {
        return loadXml(resource("amendment12/template.xml")
                .replace("__INSTANCE__", METADATA + TOTALS + nodes)
                .replace("__BINDS__", METADATA_BINDS + TOTAL_BINDS + binds)
                .replace("__BODY__", body));
    }

    static String screenBind(String path) {
        return bind(path, "int", "ct:calculate-here=\"duration()\" readonly=\"true()\"");
    }

    static String wallBind(String path) {
        return bind(path, "string", "ct:calculate-here=\"format-date-time(now(), '%Y-%m-%dT%H:%M:%S')\"");
    }

    /** Each observation is a calculate_here, so observing never consumes screen time. */
    static LoadedForm timeline(int questions, String minimum) throws Exception {
        StringBuilder nodes = new StringBuilder("<s0/><w0/>");
        StringBuilder binds = new StringBuilder(screenBind("s0") + wallBind("w0"));
        StringBuilder body = new StringBuilder(input("s0") + input("w0"));
        for (int i = 1; i <= questions; i++) {
            nodes.append("<q").append(i).append("/><s").append(i).append("/><w").append(i).append("/>");
            binds.append(bind("q" + i, "int", "ct:minimum-seconds=\"" + minimum + "\""))
                    .append(screenBind("s" + i)).append(wallBind("w" + i));
            body.append(input("q" + i)).append(input("s" + i)).append(input("w" + i));
        }
        return fixture(nodes.toString(), binds.toString(), body.toString());
    }

    static SimulationConfig settings(String extra) throws ConfigException {
        return settings(1, 1, extra);
    }

    static SimulationConfig settings(int submissions, int enumerators, String extra) throws ConfigException {
        return config("\"seed\":731991,\"submissions\":" + submissions + ",\"enumerators\":" + enumerators + ","
                + "\"start\":\"2026-03-02T08:00:00\",\"timeZone\":\"+03:00\",\"fieldworkDays\":2,"
                + "\"missingRate\":0,\"specialRate\":0,\"speedViolationRate\":0"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    // Explicitly disable all unrelated problems, including the default faults.
    static SimulationConfig pausedSettings(String extra) throws ConfigException {
        return settings(extra).withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, 0, 1, 0, List.of()));
    }

    record Paused(LoadedForm form, SimulationConfig config, Fieldwork plan, Submission submission) {
        Submission withoutPause() {
            return new FormWalker(form, config.withProblems(null)).walk(submission.number(),
                    plan.visits().get(submission.number() - 1));
        }
    }

    static Paused pause(LoadedForm form, SimulationConfig config) {
        Fieldwork plan = new Fieldwork(form, config);
        List<Submission> submissions = plan.walkAll();
        assertEquals(1, submissions.size(), "One visit forces the no-englobed-interview pause path");
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals("leftOpen")),
                "The public fieldwork plan must actually request a pause");
        return new Paused(form, config, plan, submissions.get(0));
    }

    static LocalDateTime wall(Submission s, int index) throws Exception {
        return LocalDateTime.parse(value(s, "w" + index));
    }

    static long offScreen(Submission s, int index) throws Exception {
        return Duration.between(s.start(), wall(s, index)).getSeconds() - integer(s, "s" + index);
    }

    static void totals(Submission s, long expected) throws Exception {
        assertEquals(expected, integer(s, "elapsed"), "Final duration is screen time");
        assertEquals(2 * expected, integer(s, "twice"), "A wrapped duration call also sees screen time");
        assertEquals(expected + 7, integer(s, "dependent"), "Final recalculation updates dependents");
        assertEquals(0, integer(s, "frozen"), "once(duration()) retains its opening value");
    }

    static void metadata(Submission s) throws Exception {
        assertEquals(s.start(), OffsetDateTime.parse(value(s, "started")).toLocalDateTime());
        assertEquals(s.end(), OffsetDateTime.parse(value(s, "finished")).toLocalDateTime());
        Duration upload = Duration.between(s.end(), s.submitted());
        assertEquals(0, upload.getNano());
        assertEquals(0, upload.getSeconds() % 60);
        assertTrue(upload.toMinutes() >= 1 && upload.toMinutes() <= 240,
                "Upload is 1..240 whole minutes after finalisation, not after screen duration");
    }
}
