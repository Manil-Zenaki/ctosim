package ctosim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 fixtures and black-box oracles; no implementation or private API access. */
final class InjectedProblemsTestSupport {
    static final List<String> NAMES = List.of("resent", "reinterview", "short", "long", "offhours", "overlap", "travel");
    static final List<String> FAMILIES = List.of("duplicate", "duplicate", "duration", "duration", "schedule", "schedule", "travel");
    static final List<String> CHECKS = List.of("ipacheckids", "ipacheckids", "ipacheckoutliers", "ipacheckoutliers", "ipachecktimeuse", "custom", "custom");
    static final DateTimeFormatter WIDE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.ENGLISH);
    static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
    private InjectedProblemsTestSupport() { }

    static SimulationConfig config(int n, int e, String extra) throws Exception {
        return SimulationConfig.parse("{\"seed\":731991,\"submissions\":" + n + ",\"enumerators\":" + e
                + ",\"start\":\"2026-03-02T08:17:23\",\"timeZone\":\"+03:00\",\"fieldworkDays\":1000,"
                + "\"missingRate\":0,\"specialRate\":0,\"integerRange\":{\"min\":1,\"max\":1000000}"
                + (extra.isEmpty() ? "" : "," + extra) + "}", "injected-problems.json");
    }
    static ProblemRates only(String name, double rate) {
        double[] r = new double[7]; r[NAMES.indexOf(name)] = rate;
        return new ProblemRates(r[0], r[1], r[2], r[3], r[4], r[5], r[6]);
    }
    static LoadedForm fixture(String instance, String binds, String body) throws Exception {
        try (var in = InjectedProblemsTestSupport.class.getResourceAsStream("/ctosim/injected-problems/form-template.xml")) {
            assertNotNull(in);
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__INSTANCE__", instance).replace("__BINDS__", binds).replace("__BODY__", body);
            return FormLoader.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "problems.xml");
        }
    }
    static LoadedForm ordinary(int minimum, boolean starts, boolean durations, boolean locations) throws Exception {
        String nodes = "<id/><answer/>";
        String binds = bind("id", "int", "") + bind("answer", "string", "ct:minimum-seconds=\"" + minimum + "\"");
        String body = input("id") + input("answer");
        if (starts) {
            nodes += "<start_z/><start_a/>";
            // Reverse bind order to ensure records use form order, not alphabetical/bind order.
            binds += bind("start_a", "dateTime", "jr:preload=\"timestamp\" jr:preloadParams=\"start\"")
                    + bind("start_z", "dateTime", "jr:preload=\"timestamp\" jr:preloadParams=\"start\"");
        }
        if (durations) {
            nodes += "<duration_z/><duration_a/>";
            binds += bind("duration_a", "int", "calculate=\"duration()\"")
                    + bind("duration_z", "int", "calculate=\"duration()\"");
        }
        if (locations) {
            for (String name : List.of("gps", "trace", "shape")) {
                nodes += "<" + name + "/>";
                binds += bind(name, name.equals("gps") ? "geopoint" : "geo" + name, "");
                body += input(name);
            }
        }
        return fixture(nodes, binds, body);
    }
    static LoadedForm singleTimed(int minimum) throws Exception {
        return fixture("<answer/>", bind("answer", "string", "ct:minimum-seconds=\"" + minimum + "\""), input("answer"));
    }
    static LoadedForm clockSteps(int questions, int minimum) throws Exception {
        StringBuilder nodes = new StringBuilder(), binds = new StringBuilder(), body = new StringBuilder();
        for (int i = 0; i < questions; i++) {
            nodes.append("<q").append(i).append("/><t").append(i).append("/>");
            binds.append(bind("q" + i, "int", "ct:minimum-seconds=\"" + minimum + "\""))
                    .append(bind("t" + i, "int", "ct:calculate-here=\"duration()\" readonly=\"true()\""));
            body.append(input("q" + i)).append(input("t" + i));
        }
        return fixture(nodes.toString(), binds.toString(), body.toString());
    }
    record Run(LoadedForm form, SimulationConfig config, Fieldwork plan, List<Submission> submissions,
               Map<String, Submission> keyed) {
        Submission target(InjectedProblem p) { return submissions.get(p.submission() - 1); }
        Submission related(InjectedProblem p) throws Exception {
            if (keyed.isEmpty()) for (var s : submissions) keyed.put(key(s), s);
            Submission found = keyed.get(p.relatedKey());
            assertNotNull(found, "Related KEY is not a returned submission: " + p); return found;
        }
        Visit visit(int n) { return plan.visits().get(n - 1); }
        Submission clean(int n) { return new FormWalker(form, config.withProblems(null)).walk(n, visit(n)); }
    }
    static Run run(LoadedForm form, SimulationConfig config) {
        Fieldwork plan = new Fieldwork(form, config);
        return new Run(form, config, plan, plan.walkAll(), new HashMap<>());
    }
    static String key(Submission s) throws Exception { return value(s, "meta/instanceID"); }
    static List<InjectedProblem> records(Run run, String name) {
        return run.plan().problems().stream().filter(p -> p.problem().equals(name)).toList();
    }
    static Set<Integer> targets(Run run, String name) {
        Set<Integer> result = new TreeSet<>();
        records(run, name).forEach(p -> result.add(p.submission())); return result;
    }
    static List<InjectedProblem> representatives(Run run) {
        Map<Integer, InjectedProblem> map = new TreeMap<>();
        run.plan().problems().forEach(p -> map.putIfAbsent(p.submission(), p));
        return List.copyOf(map.values());
    }
    static void assertMetadata(Run run) throws Exception {
        int previous = 0;
        Map<Integer, String> assigned = new HashMap<>();
        for (InjectedProblem p : run.plan().problems()) {
            assertTrue(p.submission() >= previous, "answer-key records sorted by submission"); previous = p.submission();
            int type = NAMES.indexOf(p.problem()); assertTrue(type >= 0, "known problem");
            assertEquals(FAMILIES.get(type), p.family());
            assertEquals(type == 0 && run.config().id().isEmpty() ? "ipacheckdups" : CHECKS.get(type), p.check());
            assertEquals(type != 2 && type != 3, p.deterministic());
            String old = assigned.putIfAbsent(p.submission(), p.problem());
            assertTrue(old == null || old.equals(p.problem()), "at most one problem per target");
            if (type != 2 && type != 3) assertTrue(p.submission() > run.config().enumerators());
            assertEquals(key(run.target(p)), p.key()); assertFalse(p.key().isEmpty());
            assertEquals(value(run.target(p), "username"), p.enumerator());
            if (!p.relatedKey().isEmpty()) assertTrue(run.related(p).number() < p.submission());
        }
    }
    static long gap(Submission previous, Submission target) {
        return FieldworkTestSupport.wholeMinutes(previous.end(), target.start());
    }
    static double distance(Visit a, Visit b) {
        return FieldworkTestSupport.distance(a.latitude(), a.longitude(), b.latitude(), b.longitude());
    }
    static long day(Run r, int n) { return FieldworkTestSupport.day(r.config(), r.visit(n)); }
    static void ordinary(Run r, int n) { FieldworkTestSupport.same(r.clean(n), r.submissions().get(n - 1)); }
    static void assertWarning(Run r, String name, int... numbers) { FieldworkTestSupport.warning(r.plan().warnings(), name, numbers); }
}
