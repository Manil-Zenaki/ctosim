package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 10 Amendment 1, exercised exclusively through the published pipeline. */
@Isolated
class CliAmendment1ReportTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    private record Run(Input input, Path config, Path output, Result result, String report) { }

    private Input input(String name) throws Exception {
        return fixture(temp.resolve("source"), "amendment1/" + name, "survey.xml");
    }

    private Run execute(Input input, int count, Map<String, Object> fields) throws Exception {
        Path settings = config(temp, JSON.writeValueAsString(Map.of(
                "seed", 1827, "submissions", count, "missingRate", 0, "specialRate", 0, "fields", fields)));
        Path output = temp.resolve("output");
        Result result = simulate(input, output, "-c", settings.toString());
        success(result);
        return new Run(input, settings, output, result, report(output));
    }

    private Run execute(String name, int count) throws Exception {
        return execute(input(name), count, Map.of());
    }

    private static String section(String report) {
        int start = report.indexOf("Unsatisfied constraints: ");
        assertTrue(start >= 0, "Missing unsatisfied-constraints section:\n" + report);
        int end = report.indexOf("Files:\n", start);
        assertTrue(end > start, "Section must precede Files:\n" + report);
        return report.substring(start, end);
    }

    private static String numbers(int count) {
        return IntStream.rangeClosed(1, Math.min(count, 20)).mapToObj(Integer::toString)
                .collect(Collectors.joining(", ")) + (count > 20 ? ", ..." : "");
    }

    private static String membership(int count) {
        return "    submissions (" + count + " of " + count + "): " + numbers(count) + "\n";
    }

    private static String questionsSection(int count) {
        return "Unsatisfied constraints: 2\n"
                + "  zeta, row 7: . = 'never'\n" + membership(count)
                + "  alpha, row 19: false()\n" + membership(count);
    }

    @Test void sectionHasExactIndentationAndAppearsBetweenEnumeratorsAndFiles() throws Exception {
        Run run = execute("questions", 1);
        int enumerators = run.report().indexOf("Enumerators: ");
        int start = run.report().indexOf("Unsatisfied constraints: ");
        assertTrue(enumerators >= 0);
        assertEquals(run.report().indexOf('\n', enumerators) + 1, start);
        assertEquals(questionsSection(1), section(run.report()));
        assertEquals(1, run.report().split("Unsatisfied constraints:", -1).length - 1);
    }

    @Test void questionsUseFirstRecordOrderInsteadOfAlphabeticalOrCatalogOrder() throws Exception {
        assertEquals(questionsSection(3), section(execute("questions", 3).report()));
    }

    @ParameterizedTest @ValueSource(ints = {1, 19, 20, 21, 25})
    void submissionListsTruncateOnlyAfterTheTwentiethNumberWhileCountsStayExact(int count) throws Exception {
        assertEquals(questionsSection(count), section(execute("questions", count).report()));
    }

    @Test void questionWithoutCatalogEntryOmitsOnlyItsRowAnnotation() throws Exception {
        Input input = input("questions");
        var catalog = JSON.readTree(Files.readString(input.catalog()));
        ((com.fasterxml.jackson.databind.node.ArrayNode) catalog.get("fields")).remove(1);
        write(input.catalog(), JSON.writeValueAsString(catalog));
        assertEquals(questionsSection(1).replace("zeta, row 7:", "zeta:"),
                section(execute(input, 1, Map.of()).report()));
    }

    @Test void catalogEntryWithSameNameAtAnotherPathDoesNotSupplyTheRow() throws Exception {
        Input input = input("questions");
        write(input.catalog(), Files.readString(input.catalog()).replace("/data/zeta", "/data/elsewhere/zeta"));
        assertEquals(questionsSection(1).replace("zeta, row 7:", "zeta:"),
                section(execute(input, 1, Map.of()).report()));
    }

    @Test void referencesPreserveExpressionOrderUnicodeAndZeroAndLabelOnlyEmptyValuesBlank() throws Exception {
        Run run = execute("references", 2);
        assertEquals("""
                Unsatisfied constraints: 1
                  answer, row 5: (.=${zref}) and (.=${aref}) and (.=${mref})
                    submissions (2 of 2): 1, 2
                    in submission 1: zref = café 東京; aref = (blank); mref = 0
                """, section(run.report()));
    }

    @Test void referenceLineIsAbsentWhenTheFirstRecordHasNoReferences() throws Exception {
        assertFalse(section(execute("questions", 1).report()).contains("in submission"));
    }

    @Test void isolatedCrAndLfInConstraintAndReferenceValuesEachBecomeOneSpace() throws Exception {
        Input input = input("references");
        write(input.form(), Files.readString(input.form())
                .replace("café 東京", "café&#10;東京&#13;fin")
                .replace(") and (", ")&#10;and&#13;("));
        Run run = execute(input, 1, Map.of());
        assertEquals("""
                Unsatisfied constraints: 1
                  answer, row 5: (.=${zref}) and (.=${aref}) and (.=${mref})
                    submissions (1 of 1): 1
                    in submission 1: zref = café 東京 fin; aref = (blank); mref = 0
                """, section(run.report()));
        byte[] bytes = Files.readAllBytes(run.output().resolve("run-report.txt"));
        assertEquals(run.report(), new String(bytes, StandardCharsets.UTF_8));
        assertFalse(run.report().startsWith("\uFEFF"));
        assertFalse(run.report().contains("\r"));
        assertTrue(run.report().endsWith("\n"));
        assertFalse(run.report().endsWith("\n\n"));
    }

    @ParameterizedTest @ValueSource(ints = {3, 21})
    void allNestedRepeatIndicesAreRemovedAndEachSubmissionIsCountedOnce(int count) throws Exception {
        Run run = execute("nested", count);
        assertEquals("Unsatisfied constraints: 3\n"
                + "  same, row 5: . = 'never' and (${marker}>0)\n" + membership(count)
                + "    in submission 1: marker = 1\n"
                + "  same, row 9: false()\n" + membership(count)
                + "  same, row 12: . = 'absent'\n" + membership(count), section(run.report()));
    }

    @Test void referenceValuesComeFromTheRecordedFailureRatherThanTheFinalExport() throws Exception {
        Input input = input("references");
        write(input.form(), Files.readString(input.form())
                .replace("nodeset=\"/data/zref\" type=\"string\" readonly=\"true()\"",
                        "nodeset=\"/data/zref\" type=\"string\"")
                .replace("</h:body>", "<input ref=\"/data/zref\"><label>Change later</label></input></h:body>"));
        Run run = execute(input, 1, Map.of("zref", Map.of("value", "changed later")));
        assertEquals(List.of("changed later"), column(run.output().resolve("Constraint references_WIDE.csv"), "zref"));
        assertTrue(section(run.report()).contains("    in submission 1: zref = café 東京; aref = (blank); mref = 0\n"));
        assertFalse(section(run.report()).contains("changed later"));
    }

    @Test void onlyTheFirstSubmissionsReferencesAreShownEvenWhenLaterValuesDiffer() throws Exception {
        Input input = input("references");
        write(input.form(), Files.readString(input.form())
                .replace("nodeset=\"/data/zref\" type=\"string\" readonly=\"true()\"",
                        "nodeset=\"/data/zref\" type=\"string\"")
                .replace("<h:body>", "<h:body><input ref=\"/data/zref\"><label>Reference</label></input>"));
        Run run = execute(input, 8, Map.of("zref", Map.of("texts", List.of("red", "blue", "green", "gold"))));
        List<String> values = column(run.output().resolve("Constraint references_WIDE.csv"), "zref");
        assertTrue(values.stream().distinct().count() > 1, "Fixture must exercise changing reference values");
        assertEquals("Unsatisfied constraints: 1\n"
                + "  answer, row 5: (.=${zref}) and (.=${aref}) and (.=${mref})\n" + membership(8)
                + "    in submission 1: zref = " + values.get(0) + "; aref = (blank); mref = 0\n",
                section(run.report()));
    }

    @Test void groupingDoesNotDeduplicateTheOriginalWarningCases() throws Exception {
        Run run = execute("nested", 2);
        List<String> expected = new ArrayList<>();
        FormWalker walker = new FormWalker(FormLoader.load(run.input().form(), run.input().form().getParent()),
                SimulationConfig.load(run.config()));
        for (int number = 1; number <= 2; number++) expected.addAll(walker.walk(number).warnings());
        assertEquals(12, expected.size(), "Four repeated and two grouped questions fail in each submission");
        assertEquals(expected.stream().map(CliWarningsTest::singleLine).toList(), warnings(run.output()));
    }

    @Test void requiredWarningsDoNotCreateExtraQuestionsOrSubmissionMemberships() throws Exception {
        Input input = input("questions");
        write(input.form(), Files.readString(input.form()).replace(" constraint=", " required=\"true()\" constraint="));
        Run run = execute(input, 2, Map.of());
        assertEquals(questionsSection(2), section(run.report()));
        assertEquals(8, warnings(run.output()).size(), "Each of four failures retains both warnings");
    }

    @Test void endOfFormFailuresFollowEarlierRecordsRatherThanQuestionBodyOrder() throws Exception {
        Run run = execute(input("end-check"), 2, Map.of("gate", Map.of("value", "closed")));
        assertEquals("""
                Unsatisfied constraints: 2
                  alpha, row 3: false()
                    submissions (2 of 2): 1, 2
                  zeta, row 2: (${gate}='open')
                    submissions (2 of 2): 1, 2
                    in submission 1: gate = closed
                """, section(run.report()));
    }

    @Test void partialFailuresListActualSubmissionNumbersInNumericOrder() throws Exception {
        Run run = execute("subset", 32);
        List<String> gates = column(run.output().resolve("Subset constraints_WIDE.csv"), "gate");
        List<Integer> failures = IntStream.range(0, gates.size()).filter(i -> gates.get(i).equals("fail"))
                .map(i -> i + 1).boxed().toList();
        assertTrue(failures.size() > 1 && failures.size() < 32,
                "Fixed-seed fixture must include both passing and failing submissions");
        assertTrue(failures.stream().anyMatch(n -> n >= 10), "Exercise numeric rather than lexical sorting");
        String listed = failures.stream().limit(20).map(Object::toString).collect(Collectors.joining(", "))
                + (failures.size() > 20 ? ", ..." : "");
        assertEquals("Unsatisfied constraints: 1\n"
                + "  answer, row 3: (${gate}='pass')\n"
                + "    submissions (" + failures.size() + " of 32): " + listed + "\n"
                + "    in submission " + failures.get(0) + ": gate = fail\n", section(run.report()));
    }

    @ParameterizedTest @ValueSource(strings = {"satisfied", "blank", "conversion", "irrelevant"})
    void noSectionOrThirdSummaryLineAppearsWhenNoConstraintFailureWasRecorded(String scenario) throws Exception {
        Input input = input("questions");
        String xml = Files.readString(input.form());
        Map<String, Object> fields = Map.of();
        switch (scenario) {
            case "satisfied" -> xml = xml.replace(". = 'never'", "true()").replace("false()", "true()");
            case "blank" -> fields = Map.of("zeta", Map.of("missingRate", 1), "alpha", Map.of("missingRate", 1));
            case "conversion" -> fields = Map.of("zeta", Map.of("value", "not-a-choice"),
                    "alpha", Map.of("value", "not-a-choice"));
            case "irrelevant" -> xml = xml.replace(" constraint=", " relevant=\"false()\" constraint=");
            default -> fail("Unknown test scenario");
        }
        write(input.form(), xml);
        Run run = execute(input, 2, fields);
        assertFalse(run.report().contains("Unsatisfied constraints:"), run.report());
        int enumerators = run.report().indexOf("Enumerators: ");
        assertTrue(enumerators >= 0);
        assertEquals(run.report().indexOf('\n', enumerators) + 1, run.report().indexOf("Files:\n"));
        assertEquals(2, run.result().out().lines().count(), run.result().out());
        if (scenario.equals("conversion")) assertEquals(4, warnings(run.output()).size());
    }

    @Test void aQuestionFirstFailingInALaterSubmissionFollowsQuestionsAlreadyRecorded() throws Exception {
        Input input = input("subset");
        write(input.form(), Files.readString(input.form())
                .replace("<answer/>", "<answer/><always/>")
                .replace("</model>", "<bind nodeset=\"/data/always\" type=\"string\" constraint=\"false()\"/></model>")
                .replace("</h:body>", "<select1 ref=\"/data/always\"><label>Always</label>"
                        + "<item><label>Only</label><value>only</value></item></select1></h:body>"));
        // Deliberately leave 'always' out of the catalog: grouping still includes it.
        Run run = execute(input, 12, Map.of());
        List<String> gates = column(run.output().resolve("Subset constraints_WIDE.csv"), "gate");
        assertTrue(gates.contains("fail") && gates.contains("pass"));
        List<String> expectedOrder = gates.get(0).equals("pass") ? List.of("always", "answer") : List.of("answer", "always");
        List<String> actualOrder = section(run.report()).lines()
                .filter(line -> line.startsWith("  ") && !line.startsWith("    "))
                .map(line -> line.substring(2).split("[:,]", 2)[0]).toList();
        assertEquals(expectedOrder, actualOrder);
        List<String> lines = run.result().out().lines().toList();
        assertEquals(3, lines.size());
        assertSummary(lines.get(2), 2, String.join(", ", expectedOrder));
    }

    @Test void thirdSummaryLineNamesQuestionCountOrderedNamesAndReportAfterTheOriginalTwoLines() throws Exception {
        Run run = execute("questions", 3);
        List<String> lines = run.result().out().lines().toList();
        assertEquals(3, lines.size(), run.result().out());
        assertTrue(lines.get(0).contains("3") && lines.get(0).contains("constraint_report")
                && lines.get(0).contains(run.output().toString()), lines.get(0));
        assertTrue(lines.get(1).contains("6") && lines.get(1).contains("run-report.txt"), lines.get(1));
        assertSummary(lines.get(2), 2, "zeta, alpha");
    }

    private static void assertSummary(String line, int count, String orderedNames) {
        assertTrue(java.util.regex.Pattern.compile("(?<![0-9])" + count + "(?![0-9])").matcher(line).find(), line);
        assertTrue(line.contains(orderedNames), line);
        assertTrue(line.contains("run-report.txt"), line);
        // The amendment specifies meaning, not an exact sentence (questions file, item 13).
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        assertTrue(lower.contains("constraint"), line);
        assertTrue(lower.contains("satisf") && (lower.contains("not") || lower.contains("unsatisf")
                || lower.contains("unable") || lower.contains("couldn't")), line);
    }

    @Test void thirdSummaryCountsQuestionsRatherThanWarningsAndRetainsEqualNamesAtDistinctPaths() throws Exception {
        Run run = execute("nested", 2);
        List<String> lines = run.result().out().lines().toList();
        assertEquals(3, lines.size());
        assertSummary(lines.get(2), 3, "same, same, same");
    }

    @Test void oneQuestionSummaryIsPresentEvenWhenOnlyOneSubmissionFailed() throws Exception {
        Run run = execute("references", 1);
        List<String> lines = run.result().out().lines().toList();
        assertEquals(3, lines.size());
        assertSummary(lines.get(2), 1, "answer");
    }

    @Test void unicodeQuestionNamesArePreservedInTheSectionAndThirdSummaryLine() throws Exception {
        Input input = input("references");
        write(input.form(), Files.readString(input.form()).replace("answer", "réponse"));
        write(input.catalog(), Files.readString(input.catalog()).replace("answer", "réponse"));
        Run run = execute(input, 1, Map.of());
        assertTrue(section(run.report()).contains("  réponse, row 5: "));
        List<String> lines = run.result().out().lines().toList();
        assertEquals(3, lines.size());
        assertSummary(lines.get(2), 1, "réponse");
    }

    @Test void repeatedCallsDoNotLeakConstraintGroupsAndOverwriteTheOldSection() throws Exception {
        Input input = input("questions");
        Run first = execute(input, 2, Map.of());
        assertEquals(questionsSection(2), section(first.report()));
        write(input.form(), Files.readString(input.form()).replace(". = 'never'", "true()").replace("false()", "true()"));
        Run second = execute(input, 1, Map.of());
        assertFalse(second.report().contains("Unsatisfied constraints:"));
        assertEquals(2, second.result().out().lines().count());
    }

    @Test void reportsWithConstraintSectionsAreByteIdenticalAcrossOutputLocations() throws Exception {
        Run run = execute("nested", 3);
        Path other = temp.resolve("other-output");
        success(simulate(run.input(), other, "-c", run.config().toString()));
        identical(snapshot(run.output()), snapshot(other));
        assertFalse(run.report().contains(temp.toString()));
    }

    @Test void convertedFormsUseTheirGeneratedCatalogAndKeepConverterWarnings() throws Exception {
        Path source = workbook(temp.resolve("workbook"), "constraints.xlsx",
                "{\"fixture\":\"amendment1/questions\",\"warnings\":[\"converter diagnostic\"]}");
        Path settings = config(temp, "{\"submissions\":1,\"missingRate\":0,\"specialRate\":0}");
        Path output = temp.resolve("converted");
        Result result = child(temp.resolve("child"), converterEnvironment(), "UTC", source.toString(),
                "-c", settings.toString(), "-o", output.toString(), "--python", python().toString());
        success(result);
        assertEquals(questionsSection(1), section(report(output)));
        assertEquals("converter diagnostic", warnings(output).get(0));
        assertEquals(3, warnings(output).size());
        assertEquals(List.of("Constraint report_WIDE.csv", "Constraint report.csv", "form.xml",
                "form.fields.json", "run-report.txt"), reportFiles(output));
        assertEquals(3, result.out().lines().count());
    }

    @Test void constraintReportsRemainIdenticalAcrossProcessTimeZonesAndWorkingDirectories() throws Exception {
        Input first = fixture(temp.resolve("first-input"), "amendment1/references", "survey.xml");
        Input second = fixture(temp.resolve("second-input"), "amendment1/references", "survey.xml");
        Path settings = config(temp, "{\"submissions\":2,\"seed\":1827,\"missingRate\":0,\"specialRate\":0}");
        Path firstOutput = temp.resolve("first-output");
        Path secondOutput = temp.resolve("second-output");
        success(child(temp.resolve("first-working"), Map.of(), "Pacific/Kiritimati", first.form().toString(),
                "-c", settings.toString(), "-o", firstOutput.toString()));
        success(child(temp.resolve("second-working"), Map.of(), "America/Los_Angeles", second.form().toString(),
                "-c", settings.toString(), "-o", secondOutput.toString()));
        identical(snapshot(firstOutput), snapshot(secondOutput));
        assertEquals(section(report(firstOutput)), section(report(secondOutput)));
    }

    @Test void converterWarningTextAboutAConstraintDoesNotInventAStructuredFailure() throws Exception {
        String warning = "submission 1: field 'phantom' (/data/phantom): its constraint could not be satisfied";
        Path source = workbook(temp.resolve("workbook"), "healthy.xlsx",
                JSON.writeValueAsString(Map.of("fixture", "simple", "warnings", List.of(warning))));
        Path output = temp.resolve("converted");
        Result result = child(temp.resolve("child"), converterEnvironment(), "UTC", source.toString(),
                "-n", "1", "-o", output.toString(), "--python", python().toString());
        success(result);
        assertEquals(List.of(warning), warnings(output));
        assertFalse(report(output).contains("Unsatisfied constraints:"));
        assertEquals(2, result.out().lines().count());
    }
}
