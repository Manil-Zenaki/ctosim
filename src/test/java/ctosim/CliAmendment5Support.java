package ctosim;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Amendment 5 orchestration oracle using only the APIs published in specs 04, 10, 13 and 14. */
final class CliAmendment5Support {
    static final String KEY = "answer-key.csv";
    static final String HEADER = "KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind";
    static final String WIDE_FILE = "CLI problèmes_WIDE.csv";
    static final List<String> RATE_NAMES = List.of("resent", "reinterview", "shortDuration",
            "longDuration", "offHours", "overlap", "travel");

    static Input input(Path directory) throws Exception {
        return fixture(directory, "amendment5/problems", "survey.xml");
    }

    static ObjectNode settings() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {"seed":731991,"submissions":24,"enumerators":2,"fieldworkDays":100,
                 "start":"2026-03-02T08:17:23","timeZone":"Africa/Addis_Ababa",
                 "missingRate":0,"specialRate":0,"speedViolationRate":0,
                 "repeats":{"min":2,"max":2},"integerRange":{"min":1,"max":1000000},
                 "area":{"minLatitude":-20,"maxLatitude":20,"minLongitude":-20,"maxLongitude":20},
                 "id":"household"}
                """);
    }

    static ObjectNode only(String name, double rate) {
        ObjectNode rates = JSON.createObjectNode();
        RATE_NAMES.forEach(key -> rates.put(key, 0));
        // Spec 04 Amendment 7 defaults omitted keys to outliers and enumerator faults.
        rates.put("outliers", 0);
        rates.putArray("faults");
        // Spec 04 Amendment 9 defaults an omitted leftOpen rate to 0.02.
        rates.put("leftOpen", 0);
        if (name != null) rates.put(name, rate);
        return rates;
    }

    record Oracle(LoadedForm form, Fieldwork plan, List<Submission> submissions) {
        int count() { return (int) plan.problems().stream().map(InjectedProblem::submission).distinct().count(); }
    }

    static Oracle oracle(Input input, Path media, SimulationConfig config) throws Exception {
        LoadedForm form = FormLoader.load(input.form(), media);
        Fieldwork plan = new Fieldwork(form, config);
        return new Oracle(form, plan, plan.walkAll());
    }

    static SimulationConfig parsed(ObjectNode settings) throws Exception {
        return SimulationConfig.parse(settings.toString(), "oracle.json");
    }

    static void keyBytes(Path output, Oracle oracle) throws Exception {
        assertArrayEquals(AnswerKey.csv(oracle.plan().problems()).getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(output.resolve(KEY)), "Answer key must serialize the same plan used for exports");
    }

    static void exports(Input input, Path output, Oracle oracle, String server) throws Exception {
        SurveyCtoExporter exporter = new SurveyCtoExporter(FieldCatalog.load(input.catalog()), oracle.form(), server);
        assertArrayEquals(exporter.wide(oracle.submissions()).getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(output.resolve(exporter.wideFileName())));
        for (var entry : exporter.longFiles(oracle.submissions()).entrySet())
            assertArrayEquals(entry.getValue().getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(output.resolve(entry.getKey())), entry.getKey());
    }

    static List<String> expectedFiles(Input input, Oracle oracle) throws Exception {
        SurveyCtoExporter exporter = new SurveyCtoExporter(FieldCatalog.load(input.catalog()), oracle.form(), "example");
        var names = new ArrayList<String>();
        names.add(exporter.wideFileName());
        names.addAll(exporter.longFiles(oracle.submissions()).keySet());
        names.add(KEY);
        return names;
    }

    static String problemLine(int count) {
        return "Problems: " + count + " injected, listed in answer-key.csv\n";
    }

    static void summary(Result result, Path output, int count, int totalLines) throws Exception {
        List<String> lines = result.out().lines().toList();
        assertEquals(totalLines, lines.size(), result.out());
        assertTrue(lines.get(1).contains("run-report.txt"), result.out());
        CliAmendment4Support.number(lines.get(1), warnings(output).size());
        CliAmendment4Support.number(lines.get(2), count);
        assertTrue(lines.get(2).contains(KEY), result.out());
        assertEquals(1, lines.stream().filter(s -> s.contains(KEY)).count(), result.out());
        assertEquals(1, report(output).lines().filter(s -> s.equals(problemLine(count).strip())).count());
    }

    private CliAmendment5Support() { }
}
