package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment3ReportTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;
    private static final Map<String, Integer> ROWS = Map.of("/data/zeta", 7, "/data/alpha", 19);

    @Test void sectionHasExactHeadingIndentationMessagesAndRowsImmediatelyAfterEnumerators() throws Exception {
        Run run = execute(temp, "errors", 1);
        List<Submission> oracle = submissions(run, 1);
        assertEquals(List.of("zeta", "alpha"), oracle.get(0).formErrors().stream().map(FormError::field).toList());
        assertEquals(expected(oracle, ROWS), section(run.report()));
        int enumerators = run.report().indexOf("Enumerators: ");
        assertTrue(enumerators >= 0);
        assertEquals(run.report().indexOf('\n', enumerators) + 1, run.report().indexOf(HEADER));
        assertEquals(1, run.report().split(java.util.regex.Pattern.quote(HEADER), -1).length - 1);
        assertFalse(section(run.report()).contains("in submission"), "Form errors have no references line");
    }

    @Test void fieldOrderFollowsFirstRecordsRatherThanAlphabeticalInstanceOrCatalogOrder() throws Exception {
        Run run = execute(temp, "errors", 3);
        assertEquals(expected(submissions(run, 3), ROWS), section(run.report()));
        assertTrue(section(run.report()).indexOf("  zeta,") < section(run.report()).indexOf("  alpha,"));
        summary(run.result(), 2, "zeta, alpha", 3);
    }

    @ParameterizedTest @ValueSource(ints = {1, 19, 20, 21, 25})
    void submissionListsKeepExactCountsAndTruncateOnlyBeyondTwentyDistinctNumbers(int count) throws Exception {
        Run run = execute(temp, "errors", count);
        assertEquals(expected(submissions(run, count), ROWS), section(run.report()));
        String membership = membership(IntStream.rangeClosed(1, count).boxed().toList(), count);
        assertEquals(2, section(run.report()).split(java.util.regex.Pattern.quote(membership), -1).length - 1);
    }

    @Test void missingCatalogEntryOmitsOnlyTheRowAnnotation() throws Exception {
        Input input = input(temp, "errors");
        var catalog = JSON.readTree(Files.readString(input.catalog()));
        ((com.fasterxml.jackson.databind.node.ArrayNode) catalog.get("fields")).remove(4);
        write(input.catalog(), JSON.writeValueAsString(catalog));
        Run run = execute(temp, input, 2, Map.of());
        assertEquals(expected(submissions(run, 2), Map.of("/data/alpha", 19)), section(run.report()));
        assertTrue(section(run.report()).contains("  zeta: "));
        summary(run.result(), 2, "zeta, alpha", 3);
    }

    @Test void sameNameAtAnUnrelatedCatalogPathCannotSupplyARow() throws Exception {
        Input input = input(temp, "errors");
        write(input.catalog(), Files.readString(input.catalog()).replace("/data/zeta", "/data/unrelated/zeta"));
        Run run = execute(temp, input, 1, Map.of());
        assertEquals(expected(submissions(run, 1), Map.of("/data/alpha", 19)), section(run.report()));
    }

    @ParameterizedTest @ValueSource(ints = {2, 21})
    void allRepeatIndicesAreRemovedAndEqualNamesAtDifferentPathsRemainSeparate(int count) throws Exception {
        Run run = execute(temp, "nested", count);
        List<Submission> oracle = submissions(run, count);
        for (Submission submission : oracle) assertEquals(6, submission.formErrors().size());
        assertEquals(expected(oracle, Map.of("/data/outer/inner/same", 5,
                "/data/left/same", 9, "/data/right/same", 12)), section(run.report()));
        assertTrue(section(run.report()).startsWith(HEADER + "3\n"));
        assertEquals(3, section(run.report()).lines().filter(s -> s.startsWith("    submissions (")).count());
        assertTrue(section(run.report()).contains(membership(IntStream.rangeClosed(1, count).boxed().toList(), count)));
        summary(run.result(), 3, "same, same, same", 3);
    }

    @ParameterizedTest @ValueSource(ints = {40, 80})
    void onlySubmissionsWithRecordsAreListedAndNumbersAreSortedNumerically(int count) throws Exception {
        Input input = input(temp, "errors");
        replace(input, "constraint=\"" + REGEX + "\"", "constraint=\"" + REGEX + "\" relevant=\"/data/gate = 'fail'\"");
        replace(input, REPEATED, "true()");
        Run run = execute(temp, input, count, Map.of("gate", Map.of("texts", List.of("pass", "fail"))));
        List<String> gates = column(run.output().resolve("Form errors_WIDE.csv"), "gate");
        List<Integer> failed = IntStream.range(0, gates.size()).filter(i -> gates.get(i).equals("fail"))
                .map(i -> i + 1).boxed().toList();
        assertTrue(failed.size() > 1 && failed.size() < count, "Fixture must have both outcomes");
        if (count == 80) assertTrue(failed.size() > 20, "Exercise truncation of a sparse membership list");
        assertTrue(failed.stream().anyMatch(n -> n >= 10), "Exercise numeric rather than lexicographic order");
        List<Submission> oracle = submissions(run, count);
        String message = oracle.get(failed.get(0) - 1).formErrors().get(0).message();
        assertEquals(HEADER + "1\n  zeta, row 7: " + message + "\n" + membership(failed, count), section(run.report()));
        summary(run.result(), 1, "zeta", 3);
    }

    @Test void aFieldFirstFailingInALaterSubmissionIsAppendedAfterPreviouslyRecordedFields() throws Exception {
        Input input = input(temp, "errors");
        replace(input, "constraint=\"" + REGEX + "\"", "constraint=\"" + REGEX + "\" relevant=\"/data/gate = 'fail'\"");
        Run run = execute(temp, input, 16, Map.of("gate", Map.of("texts", List.of("pass", "fail"))));
        List<String> gates = column(run.output().resolve("Form errors_WIDE.csv"), "gate");
        assertTrue(gates.contains("pass") && gates.contains("fail"));
        // Choose the opposite gate value to the first draw, ensuring first zeta failure is later.
        String later = gates.get(0).equals("pass") ? "fail" : "pass";
        replace(input, "relevant=\"/data/gate = 'fail'\"", "relevant=\"/data/gate = '" + later + "'\"");
        Run rerun = execute(temp, input, 16, Map.of("gate", Map.of("texts", List.of("pass", "fail"))));
        List<Submission> oracle = submissions(rerun, 16);
        assertEquals(List.of("alpha"), oracle.get(0).formErrors().stream().map(FormError::field).toList());
        assertTrue(oracle.stream().skip(1).anyMatch(s -> s.formErrors().stream().anyMatch(e -> e.field().equals("zeta"))));
        assertEquals(expected(oracle, ROWS), section(rerun.report()));
        summary(rerun.result(), 2, "alpha, zeta", 3);
    }

    @Test void messageComesFromTheFirstRecordEvenWhenLaterSubmissionsHaveDifferentMessages() throws Exception {
        Input input = input(temp, "errors");
        replace(input, REGEX, "regex(., concat('[', /data/gate))");
        replace(input, REPEATED, "true()");
        Run run = execute(temp, input, 12, Map.of("gate", Map.of("texts", List.of("red", "blue", "gold"))));
        List<Submission> oracle = submissions(run, 12);
        assertTrue(oracle.stream().flatMap(s -> s.formErrors().stream()).map(FormError::message).distinct().count() > 1,
                "Fixture must produce different engine messages for the same field");
        assertEquals(expected(oracle, Map.of("/data/zeta", 7)), section(run.report()));
    }

    @Test void messageComesFromFirstRepeatInstanceInsteadOfTheLast() throws Exception {
        Input input = input(temp, "nested");
        replace(input, REGEX, "regex(., concat('[', position(..)))");
        Run run = execute(temp, input, 2, Map.of());
        List<Submission> oracle = submissions(run, 2);
        assertTrue(oracle.get(0).formErrors().stream().limit(4).map(FormError::message).distinct().count() > 1);
        assertEquals(expected(oracle, Map.of("/data/outer/inner/same", 5,
                "/data/left/same", 9, "/data/right/same", 12)), section(run.report()));
    }

    @Test void unicodeFieldsAndMessagesSurviveUtf8AndEveryReportEntryStaysOnOneLine() throws Exception {
        Input input = input(temp, "errors");
        replace(input, "zeta", "réponse");
        replace(input, REGEX, "regex(., '[café&#10;東京&#13;fin')");
        write(input.catalog(), Files.readString(input.catalog()).replace("zeta", "réponse"));
        Run run = execute(temp, input, 1, Map.of());
        List<Submission> oracle = submissions(run, 1);
        assertTrue(oracle.get(0).formErrors().get(0).message().contains("café"));
        assertTrue(oracle.get(0).formErrors().get(0).message().contains("東京"));
        assertEquals(expected(oracle, Map.of("/data/réponse", 7, "/data/alpha", 19)), section(run.report()));
        assertEquals(5, section(run.report()).lines().count());
        assertFalse(run.report().contains("\r"));
        assertFalse(run.report().startsWith("\uFEFF"));
        assertTrue(run.report().endsWith("\n") && !run.report().endsWith("\n\n"));
        assertArrayEquals(run.report().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(run.output().resolve("run-report.txt")));
        summary(run.result(), 2, "réponse, alpha", 3);
    }

    @Test void formErrorsDoNotChangeSuccessfulExitStatusExportsOrOriginalSummaryLines() throws Exception {
        Run run = execute(temp, "errors", 3);
        assertEquals(List.of("continued", "continued", "continued"), column(run.output().resolve("Form errors_WIDE.csv"), "after"));
        assertEquals(List.of("", "", ""), column(run.output().resolve("Form errors_WIDE.csv"), "zeta"));
        assertEquals(List.of("", "", ""), column(run.output().resolve("Form errors_WIDE.csv"), "alpha"));
        for (String filename : reportFiles(run.output())) assertTrue(Files.isRegularFile(run.output().resolve(filename)), filename);
        summary(run.result(), 2, "zeta, alpha", 3);
        List<String> lines = run.result().out().lines().toList();
        assertTrue(lines.get(0).contains("3") && lines.get(0).contains("form_errors")
                && lines.get(0).contains(run.output().toString()), lines.get(0));
        assertTrue(lines.get(1).contains(Integer.toString(warnings(run.output()).size()))
                && lines.get(1).contains("run-report.txt"), lines.get(1));
    }

    @Test void groupingPreservesEveryOriginalWarningIncludingRepeatedInstances() throws Exception {
        Run run = execute(temp, "nested", 2);
        List<Submission> oracle = submissions(run, 2);
        assertEquals(12, oracle.stream().mapToInt(s -> s.formErrors().size()).sum());
        assertEquals(oracle.stream().flatMap(s -> s.warnings().stream()).map(CliWarningsTest::singleLine).toList(), warnings(run.output()));
        assertFalse(run.report().contains("Unsatisfied constraints:"), "Expression exceptions are not unsatisfied constraints");
        summary(run.result(), 3, "same, same, same", 3);
    }
}
