package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment3LifecycleTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    private static void addField(Input input, String name, String type, int row) throws Exception {
        var catalog = JSON.readTree(Files.readString(input.catalog()));
        ((com.fasterxml.jackson.databind.node.ArrayNode) catalog.get("fields")).add(JSON.valueToTree(
                Map.of("name", name, "type", type, "xpath", "/data/" + name, "row", row)));
        write(input.catalog(), JSON.writeValueAsString(catalog));
    }

    @ParameterizedTest @ValueSource(strings = {"constraints", "attachments", "both"})
    void errorsPrecedeEveryOtherOptionalReportSectionAndFollowEveryOtherStdoutLine(String optional) throws Exception {
        Input input = input(temp, "errors");
        boolean constraints = !optional.equals("attachments");
        boolean attachments = !optional.equals("constraints");
        if (constraints) {
            replace(input, "<after/>", "<impossible/><after/>");
            replace(input, "</model>", "<bind nodeset=\"/data/impossible\" type=\"string\" constraint=\"false()\"/></model>");
            replace(input, "</h:body>", "<select1 ref=\"/data/impossible\"><label>Impossible</label>"
                    + "<item><label>Only</label><value>only</value></item></select1></h:body>");
            addField(input, "impossible", "select_one options", 21);
        }
        if (attachments) {
            replace(input, "<after/>", "<key/><lookup/><after/>");
            replace(input, "</model>", "<bind nodeset=\"/data/key\" type=\"string\"/>"
                    + "<bind nodeset=\"/data/lookup\" type=\"string\" calculate=\"pulldata('people','label','id',/data/key)\"/></model>");
            replace(input, "</h:body>", "<input ref=\"/data/key\"><label>Key</label></input></h:body>");
            addField(input, "key", "text", 22);
            addField(input, "lookup", "calculate", 23);
        }
        Run run = execute(temp, input, 1, Map.of(), attachments ? new String[]{"--fake-attachments"} : new String[0]);
        String report = run.report();
        assertEquals(report.indexOf('\n', report.indexOf("Enumerators: ")) + 1, report.indexOf(HEADER));
        int endErrors = report.indexOf(HEADER) + section(report).length();
        if (attachments) {
            assertEquals(endErrors, report.indexOf("Fictitious attachments: people.csv\n"));
            assertTrue(Files.isRegularFile(run.output().resolve("attachments/people.csv")));
        }
        if (constraints) {
            assertTrue(report.contains("Unsatisfied constraints: 1\n  impossible, row 21: false()\n"));
            assertTrue(report.indexOf("Unsatisfied constraints:") >= endErrors);
            if (attachments) assertTrue(report.indexOf("Fictitious attachments:") < report.indexOf("Unsatisfied constraints:"));
            else assertEquals(endErrors, report.indexOf("Unsatisfied constraints:"));
        }
        summary(run.result(), 2, "zeta, alpha", 3 + (constraints ? 1 : 0) + (attachments ? 1 : 0));
        List<String> lines = run.result().out().lines().toList();
        if (constraints) assertTrue(lines.get(2).contains("impossible") && lines.get(2).contains("run-report.txt"));
        if (attachments) assertTrue(lines.get(lines.size() - 2).contains("people.csv"));
    }

    @ParameterizedTest @ValueSource(strings = {"healthy", "irrelevant", "unsatisfied", "config-warning"})
    void noErrorSectionOrAlertAppearsWithoutStructuredFormErrors(String scenario) throws Exception {
        Input input = input(temp, "errors");
        if (scenario.equals("irrelevant")) {
            replace(input, "constraint=", "relevant=\"false()\" constraint=");
        } else {
            replace(input, REGEX, scenario.equals("unsatisfied") ? "false()" : "true()");
            replace(input, REPEATED, "true()");
        }
        Map<String, Object> fields = scenario.equals("config-warning")
                ? Map.of("unknown_field", Map.of("value", "ignored")) : Map.of();
        Run run = execute(temp, input, 1, fields);
        assertTrue(submissions(run, 1).get(0).formErrors().isEmpty());
        assertFalse(run.report().contains(HEADER));
        assertFalse(run.result().out().contains("FORM ERRORS:"));
        assertEquals(scenario.equals("unsatisfied") ? 3 : 2, run.result().out().lines().count());
        if (scenario.equals("unsatisfied")) assertTrue(run.report().contains("Unsatisfied constraints: 1\n"));
        if (scenario.equals("config-warning")) assertFalse(warnings(run.output()).isEmpty());
    }

    @Test void oneErrorProducesASingularFieldCountAndFinalAlert() throws Exception {
        Input input = input(temp, "errors");
        replace(input, REPEATED, "true()");
        Run run = execute(temp, input, 1, Map.of());
        assertEquals(expected(submissions(run, 1), Map.of("/data/zeta", 7)), section(run.report()));
        assertTrue(section(run.report()).startsWith(HEADER + "1\n"));
        summary(run.result(), 1, "zeta", 3);
    }

    @Test void endOfFormErrorsFollowErrorsRecordedDuringTheWalk() throws Exception {
        Input input = input(temp, "errors");
        replace(input, "<gate/>", "<gate>safe</gate>");
        replace(input, REGEX, "if(/data/gate = 'safe', true(), regex(., '['))");
        String control = "<input ref=\"/data/gate\"><label>Gate</label></input>";
        replace(input, control, "");
        replace(input, "</h:body>", control + "</h:body>");
        Run run = execute(temp, input, 2, Map.of("gate", Map.of("value", "unsafe")));
        List<Submission> oracle = submissions(run, 2);
        assertEquals(List.of("alpha", "zeta"), oracle.get(0).formErrors().stream().map(FormError::field).toList());
        assertEquals(expected(oracle, Map.of("/data/zeta", 7, "/data/alpha", 19)), section(run.report()));
        summary(run.result(), 2, "alpha, zeta", 3);
    }

    @Test void rerunReplacesOldErrorGroupsAndSubmissionCountsAndThenRemovesTheSectionWhenHealthy() throws Exception {
        Input input = input(temp, "errors");
        Run first = execute(temp, input, 21, Map.of());
        assertTrue(section(first.report()).contains("submissions (21 of 21):"));
        replace(input, REPEATED, "true()");
        Run second = execute(temp, input, 1, Map.of());
        assertEquals(expected(submissions(second, 1), Map.of("/data/zeta", 7)), section(second.report()));
        summary(second.result(), 1, "zeta", 3);
        replace(input, REGEX, "true()");
        Run third = execute(temp, input, 1, Map.of());
        assertFalse(third.report().contains(HEADER));
        assertFalse(third.result().out().contains("FORM ERRORS:"));
        assertEquals(2, third.result().out().lines().count());
    }

    @Test void identicalRunsWriteByteIdenticalReportsAndExportsAtDifferentLocations() throws Exception {
        Run first = execute(temp, "nested", 3);
        Path other = temp.resolve("other-output");
        success(simulate(first.input(), other, "-c", first.config().toString()));
        identical(snapshot(first.output()), snapshot(other));
        assertTrue(first.report().contains(HEADER));
        assertFalse(first.report().contains(temp.toString()));
    }

    @Test void reportsWithErrorsRemainDeterministicAcrossProcessWorkingDirectoriesAndTimeZones() throws Exception {
        Input input = input(temp, "errors");
        Path settings = config(temp, "{\"submissions\":2,\"seed\":701001,\"missingRate\":0,\"specialRate\":0}");
        Path first = temp.resolve("east-output");
        Path second = temp.resolve("west-output");
        Result east = child(temp.resolve("east-work"), Map.of(), "Pacific/Kiritimati", input.form().toString(),
                "-c", settings.toString(), "-o", first.toString());
        Result west = child(temp.resolve("west-work"), Map.of(), "America/Los_Angeles", input.form().toString(),
                "-c", settings.toString(), "-o", second.toString());
        success(east);
        success(west);
        summary(east, 2, "zeta, alpha", 3);
        summary(west, 2, "zeta, alpha", 3);
        identical(snapshot(first), snapshot(second));
    }

    @Test void convertedFormsUseGeneratedCatalogRowsAndKeepConverterWarningsAndExports() throws Exception {
        Path source = workbook(temp.resolve("source"), "errors.xlsx",
                JSON.writeValueAsString(Map.of("fixture", "amendment3/errors", "warnings", List.of("converter diagnostic"))));
        Path settings = config(temp, "{\"submissions\":1,\"seed\":701001,\"missingRate\":0,\"specialRate\":0}");
        Path output = temp.resolve("output");
        Result result = child(temp.resolve("child"), converterEnvironment(), "UTC", source.toString(),
                "-c", settings.toString(), "-o", output.toString(), "--python", python().toString());
        success(result);
        Run run = new Run(new Input(output.resolve("form.xml"), output.resolve("form.fields.json")), settings, output, result, report(output));
        assertEquals(expected(submissions(run, 1), Map.of("/data/zeta", 7, "/data/alpha", 19)), section(run.report()));
        assertEquals("converter diagnostic", warnings(output).get(0));
        assertTrue(reportFiles(output).containsAll(List.of("form.xml", "form.fields.json", "run-report.txt")));
        assertEquals(1, column(output.resolve("Form errors_WIDE.csv"), "after").size());
        summary(result, 2, "zeta, alpha", 3);
    }

    @Test void converterWarningThatLooksLikeAFormErrorDoesNotCreateAnErrorSectionOrAlert() throws Exception {
        String warning = "FORM ERRORS: 1 phantom would block an enumerator; run-report.txt";
        Path source = workbook(temp.resolve("source"), "healthy.xlsx",
                JSON.writeValueAsString(Map.of("fixture", "simple", "warnings", List.of(warning))));
        Path output = temp.resolve("output");
        Result result = child(temp.resolve("child"), converterEnvironment(), "UTC", source.toString(),
                "-n", "1", "-o", output.toString(), "--python", python().toString());
        success(result);
        assertEquals(List.of(warning), warnings(output));
        assertFalse(report(output).contains(HEADER));
        assertFalse(result.out().contains("FORM ERRORS:"));
        assertEquals(2, result.out().lines().count());
    }
}
