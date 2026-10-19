package ctosim;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 10 Amendment 4 orchestration, exclusively through published APIs. */
final class CliAmendment4Support {
    static final String WIDE = "CLI fieldwork_WIDE.csv";

    static Input input(Path directory) throws Exception {
        return fixture(directory, "amendment4/plan", "survey.xml");
    }

    static ObjectNode settings() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {"seed":731991,"submissions":8,"enumerators":2,"fieldworkDays":30,
                 "start":"2026-03-02T08:17:23","timeZone":"Africa/Addis_Ababa",
                 "missingRate":0,"specialRate":0,"speedViolationRate":0,
                 "repeats":{"min":2,"max":2},"integerRange":{"min":101,"max":108},
                 "area":{"minLatitude":8,"maxLatitude":10,"minLongitude":37,"maxLongitude":40}}
                """);
    }

    static Path configuration(Path directory, ObjectNode settings) throws Exception {
        return config(directory, settings.toString());
    }

    record Expected(LoadedForm form, Fieldwork plan, List<Submission> submissions) { }

    static Expected expected(Input input, Path media, ObjectNode settings) throws Exception {
        LoadedForm form = FormLoader.load(input.form(), media);
        Fieldwork plan = new Fieldwork(form, SimulationConfig.parse(settings.toString(), "oracle.json"));
        return new Expected(form, plan, plan.walkAll());
    }

    static void exports(Input input, Path output, Expected expected, String server) throws Exception {
        SurveyCtoExporter exporter = new SurveyCtoExporter(FieldCatalog.load(input.catalog()), expected.form(), server);
        assertArrayEquals(exporter.wide(expected.submissions()).getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(output.resolve(exporter.wideFileName())), "wide export of plan submissions");
        for (var file : exporter.longFiles(expected.submissions()).entrySet())
            assertArrayEquals(file.getValue().getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(output.resolve(file.getKey())), file.getKey());
    }

    static List<String> expectedWarnings(Expected expected) {
        var result = new ArrayList<String>();
        for (String name : expected.form().missingAttachments()) result.add("attached file " + name + ".csv not found");
        for (String name : expected.form().stubbedFunctions()) {
            if (!List.of("duration", "username").contains(name))
                result.add("function " + name + "() is not implemented by the simulator; it returns a fixed value");
        }
        result.addAll(expected.plan().configWarnings());
        for (Submission submission : expected.submissions()) result.addAll(submission.warnings());
        result.addAll(expected.plan().warnings());
        return result.stream().map(w -> w.replaceAll("\\r\\n|[\\r\\n]", " ")).toList();
    }

    static void summary(Result result, Path output, int count, int warningCount) {
        List<String> lines = result.out().lines().toList();
        assertEquals(2, lines.size(), result.out());
        assertTrue(lines.get(0).contains("cli_fieldwork"), result.out());
        assertTrue(lines.get(0).contains(output.toString()), result.out());
        number(lines.get(0), count);
        number(lines.get(1), warningCount);
        assertTrue(lines.get(1).contains("run-report.txt"), result.out());
    }

    static void number(String text, int number) {
        assertTrue(java.util.regex.Pattern.compile("(?<![0-9])" + number + "(?![0-9])")
                .matcher(text).find(), text);
    }

    static void cycle(List<String> actual, java.util.Set<String> pool) {
        assertTrue(actual.size() >= pool.size(), "fixture covers the complete pool");
        assertEquals(pool, new java.util.HashSet<>(actual.subList(0, pool.size())));
        for (int i = 0; i < actual.size(); i++) assertEquals(actual.get(i % pool.size()), actual.get(i));
    }
}
