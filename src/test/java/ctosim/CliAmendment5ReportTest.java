package ctosim;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment5ReportTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(ints = {1, 3, 24})
    void summaryCountsDistinctAffectedSubmissionsRatherThanDurationRecords(int n) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.put("submissions", n);
        settings.set("problems", only("shortDuration", n == 24 ? 0.25 : 1));
        Path config = config(temp, settings.toString());
        Path output = temp.resolve("output");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        int affected = n == 24 ? 6 : n;
        var rows = csv(output.resolve(KEY));
        assertEquals(2 * affected + 1, rows.size(), "Two duration fields per affected submission");
        assertEquals(affected, rows.subList(1, rows.size()).stream().map(row -> row.get(0)).distinct().count());
        for (int i = 1; i < rows.size(); i += 2) {
            assertEquals("duration_z", rows.get(i).get(5));
            assertEquals("durée", rows.get(i + 1).get(5));
            assertEquals(rows.get(i).get(0), rows.get(i + 1).get(0));
            assertEquals("short", rows.get(i).get(3));
        }
        summary(result, output, affected, 3);
        String report = report(output);
        assertTrue(report.contains("Enumerators: 2\n" + problemLine(affected) + "Files:\n"), report);
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        keyBytes(output, oracle);
        var files = expectedFiles(input, oracle);
        files.add("run-report.txt");
        assertEquals(files, reportFiles(output), "Key follows all long exports");
    }

    @Test void keyPreservesUtf8WithoutBomAndReportRetainsLfFraming() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.put("submissions", 1);
        settings.set("problems", only("longDuration", 1));
        Path config = config(temp, settings.toString()), output = temp.resolve("résultats");
        success(simulate(input, output, "-c", config.toString()));
        byte[] bytes = Files.readAllBytes(output.resolve(KEY));
        String key = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(key.startsWith(HEADER + "\n"));
        assertTrue(key.contains(",durée,"));
        assertFalse(key.endsWith("\n"), "CSV follows spec 08, unlike the run report");
        assertArrayEquals(key.getBytes(StandardCharsets.UTF_8), bytes);
        String report = report(output);
        assertTrue(report.startsWith("ctosim run report\n"));
        assertTrue(report.endsWith("\n"));
        assertFalse(report.endsWith("\n\n"));
        assertFalse(report.contains("\r"));
        assertFalse(report.contains(temp.toString()));
    }

    @Test void enabledButIneligibleProblemsWriteHeaderAndReportActualZeroWithWarnings() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.put("submissions", 1);
        settings.set("problems", only("resent", 1));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        assertEquals(HEADER, Files.readString(output.resolve(KEY)));
        summary(result, output, 0, 3);
        assertTrue(warnings(output).stream().anyMatch(w -> w.contains("resent")));
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        assertEquals(0, oracle.count());
        assertEquals(CliAmendment4Support.expectedWarnings(new CliAmendment4Support.Expected(
                oracle.form(), oracle.plan(), oracle.submissions())), warnings(output));
    }

    @Test void fallbackWarningsAreRetainedAfterSubmissionWarningsAndDoNotCountAsInjectedProblems() throws Exception {
        Input input = fixture(temp.resolve("source"), "warnings", "survey.xml");
        var settings = settings();
        settings.remove("id");
        var rates = only("reinterview", 0.25);
        rates.put("travel", 0.25);
        settings.set("problems", rates);
        var fields = settings.putObject("fields");
        fields.putObject("nonexistent").put("value", "ignored");
        fields.putObject("bad").put("value", "invalid\rinteger\nvalue");
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        assertEquals(0, oracle.count(), "No configured id and no location question");
        assertFalse(oracle.plan().warnings().isEmpty());
        assertFalse(oracle.plan().configWarnings().isEmpty());
        assertTrue(oracle.submissions().stream().anyMatch(s -> !s.warnings().isEmpty()));
        assertEquals(CliAmendment4Support.expectedWarnings(new CliAmendment4Support.Expected(
                oracle.form(), oracle.plan(), oracle.submissions())), warnings(output));
        assertEquals(HEADER, Files.readString(output.resolve(KEY)));
        summary(result, output, 0, 3);
    }

    // Bits select constraint, fictitious attachment and form-error sections independently.
    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void problemReportLineFollowsOlderSectionsAndStdoutLinePrecedesTheirSummaries(int sections) throws Exception {
        Input input = input(temp.resolve("source"));
        boolean constraints = (sections & 1) != 0, attachments = (sections & 2) != 0, errors = (sections & 4) != 0;
        if (constraints) addQuestion(input, "impossible", "select_one options", "constraint=\"false()\"",
                "<select1 ref=\"/data/impossible\"><label>Impossible</label><item><label>Only</label><value>only</value></item></select1>");
        if (attachments) {
            addQuestion(input, "key", "text", "", "<input ref=\"/data/key\"><label>Key</label></input>");
            addQuestion(input, "lookup", "calculate", "calculate=\"pulldata('people','label','id',/data/key)\"", "");
        }
        if (errors) addQuestion(input, "broken", "text", "constraint=\"regex(., '[')\"",
                "<input ref=\"/data/broken\"><label>Broken</label></input>");
        var settings = settings();
        settings.put("submissions", 1);
        settings.set("problems", only("shortDuration", 1));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        var options = new ArrayList<>(List.of("-c", config.toString()));
        if (attachments) options.add("--fake-attachments");
        Result result = simulate(input, output, options.toArray(String[]::new));
        success(result);
        String report = report(output);
        int problems = report.indexOf(problemLine(1));
        assertTrue(problems > report.indexOf("Enumerators:"), report);
        for (String section : List.of("FORM ERRORS (an enumerator would be blocked):", "Fictitious attachments:", "Unsatisfied constraints:")) {
            int position = report.indexOf(section);
            if (position >= 0) assertTrue(position < problems, report);
        }
        assertTrue(report.contains(problemLine(1) + "Files:\n"));
        summary(result, output, 1, 3 + Integer.bitCount(sections));
        var lines = result.out().lines().toList();
        int next = 3;
        if (constraints) {
            assertTrue(report.contains("Unsatisfied constraints: 1\n"));
            assertTrue(lines.get(next++).contains("impossible"));
        }
        if (attachments) {
            assertTrue(report.contains("Fictitious attachments: people.csv\n"));
            assertTrue(lines.get(next++).contains("people.csv"));
        }
        if (errors) {
            assertTrue(report.contains("FORM ERRORS (an enumerator would be blocked): 1\n"));
            assertTrue(lines.get(next).startsWith("FORM ERRORS:"));
        }
        var expectedFiles = new ArrayList<>(List.of(WIDE_FILE, "CLI problèmes.csv", "CLI problèmes-children.csv", KEY));
        if (attachments) expectedFiles.add("attachments/people.csv");
        expectedFiles.add("run-report.txt");
        assertEquals(expectedFiles, reportFiles(output));
        if (attachments) assertTrue(Files.isRegularFile(output.resolve("attachments/people.csv")));
    }

    private static void addQuestion(Input input, String name, String type, String attributes, String control) throws Exception {
        String xml = Files.readString(input.form());
        xml = xml.replace("<meta>", "<" + name + "/><meta>")
                .replace("</model>", "<bind nodeset=\"/data/" + name + "\" type=\"string\" " + attributes + "/></model>")
                .replace("</h:body>", control + "</h:body>");
        write(input.form(), xml);
        var catalog = JSON.readTree(Files.readString(input.catalog()));
        ((ArrayNode) catalog.get("fields")).add(JSON.valueToTree(
                Map.of("name", name, "type", type, "xpath", "/data/" + name, "row", 20 + catalog.get("fields").size())));
        write(input.catalog(), catalog.toString());
    }
}
