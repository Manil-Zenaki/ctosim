package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

class CliAmendment2ReportTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @Test void fictitiousNamesUseFilesMapOrderImmediatelyAfterEnumerators() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG)));
        String report = report(output);
        assertTrue(report.contains("Enumerators: 5\nFictitious attachments: a_ages.csv, z_people.csv\nFiles:\n"), report);
        assertEquals(1, report.lines().filter(s -> s.startsWith("Fictitious attachments:")).count());
        assertFalse(report.contains("Fictitious attachments: real.csv"), "A copied real file is not fictitious");
    }

    @Test void generatedPathsFollowEveryLongExportAndPrecedeTheReport() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG)));
        // Whether copied real files are listed is unspecified; verify all prescribed entries and their order.
        List<String> required = List.of(WIDE_FILE, MAIN_FILE, REPEAT_FILE,
                "attachments/a_ages.csv", "attachments/z_people.csv", "run-report.txt");
        List<String> files = reportFiles(output);
        assertEquals(WIDE_FILE, files.get(0));
        assertEquals(required, files.stream().filter(required::contains).toList());
        assertEquals("run-report.txt", files.get(files.size() - 1));
        for (String name : files) assertTrue(Files.isRegularFile(output.resolve(name)), name);
    }

    @Test void generatedFilesRemoveMissingAttachmentWarningsAndUpdateBothWarningCounts() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        Result result = fake(input, output, config(temp, CLEAN_CONFIG));
        success(result);
        assertEquals(List.of(), warnings(output));
        assertTrue(report(output).endsWith("Warnings: 0\n"));
        List<String> lines = result.out().lines().toList();
        assertEquals(3, lines.size(), result.out());
        assertTrue(lines.get(0).contains("8") && lines.get(0).contains("fake_cli")
                && lines.get(0).contains(output.toString()), result.out());
        assertTrue(lines.get(1).matches(".*\\b0\\b.*") && lines.get(1).contains("run-report.txt"), result.out());
        inventedSummary(lines.get(2), INVENTED);
    }

    @Test void inventedAttachmentDisclosurePrecedesConstraintSectionAndFollowsAllOtherStdoutLines() throws Exception {
        Input input = lookups(temp.resolve("input"));
        String json = "{\"missingRate\":0,\"fields\":{\"fail\":{\"value\":\"yes\"}}}";
        Path output = temp.resolve("output");
        Result result = fake(input, output, config(temp, json), "-n", "2");
        success(result);
        String report = report(output);
        assertTrue(report.contains("Enumerators: 5\nFictitious attachments: a_ages.csv, z_people.csv\n"
                + "Unsatisfied constraints: 1\n  impossible, row 10: . < 0 and . > 0\n"
                + "    submissions (2 of 2): 1, 2\nFiles:\n"), report);
        assertEquals(2, warnings(output).size(), "Every unsatisfied-constraint warning remains");
        assertTrue(warnings(output).stream().allMatch(s -> s.contains("impossible")));
        List<String> lines = result.out().lines().toList();
        assertEquals(4, lines.size(), result.out());
        assertTrue(lines.get(1).matches(".*\\b2\\b.*") && lines.get(1).contains("run-report.txt"));
        assertTrue(lines.get(2).contains("impossible") && lines.get(2).contains("run-report.txt"));
        inventedSummary(lines.get(3), INVENTED);
    }

    @Test void configurationWarningsAreRetainedAfterGeneratedMissingWarningsAreRemoved() throws Exception {
        Input input = lookups(temp.resolve("input"));
        String json = "{\"missingRate\":0,\"fields\":{\"ghost\":{\"value\":\"keep warning\"}}}";
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, json)));
        assertEquals(1, warnings(output).size());
        assertTrue(warnings(output).get(0).contains("ghost"));
    }

    @Test void singleUnicodeAttachmentKeepsUtf8LfAndRelativeReportPaths() throws Exception {
        Input input = fixture(temp.resolve("entrée é"), "amendment2/search", "enquête.xml");
        Path output = temp.resolve("sortie é");
        Result result = fake(input, output, config(temp, CLEAN_CONFIG), "-n", "1");
        success(result);
        String report = report(output);
        assertTrue(report.contains("Enumerators: 5\nFictitious attachments: lieuxé.csv\nFiles:\n"));
        assertEquals(List.of("Search CLI_WIDE.csv", "Search CLI.csv", "attachments/lieuxé.csv", "run-report.txt"),
                reportFiles(output));
        byte[] bytes = Files.readAllBytes(output.resolve("run-report.txt"));
        assertArrayEquals(report.getBytes(StandardCharsets.UTF_8), bytes);
        assertFalse(report.startsWith("\ufeff"));
        assertFalse(report.contains("\r"));
        assertTrue(report.endsWith("\n") && !report.endsWith("\n\n"));
        assertFalse(report.contains(temp.toString()));
        assertFalse(report.contains(temp.toString().replace('\\', '/')));
        List<String> lines = result.out().lines().toList();
        assertEquals(3, lines.size());
        inventedSummary(lines.get(2), List.of("lieuxé"));
    }

    @Test void xlsformConversionPrecedesGenerationAndItsWarningsAndFileOrderArePreserved() throws Exception {
        Path inputDirectory = Files.createDirectories(temp.resolve("source é"));
        Path workbook = workbook(inputDirectory, "enquête.XLSX", """
                {"fixture":"amendment2/lookups","warnings":["converter first","converter second"]}
                """);
        write(inputDirectory.resolve("real.csv"), resource("amendment2/real.csv"));
        Path configuration = config(temp, CLEAN_CONFIG), output = temp.resolve("output");
        Result result = child(temp.resolve("cwd"), converterEnvironment(), "UTC", "--fake-attachments",
                workbook.toString(), "-o", output.toString(), "-c", configuration.toString(),
                "-n", "2", "-s", "41", "--python", python().toString());
        success(result);
        Input converted = new Input(output.resolve("form.xml"), output.resolve("form.fields.json"));
        assertAttachmentBytes(expected(converted, inputDirectory, 41), output);
        assertLookupColumn(output, "qkey", "person", "z_people", "id", "full_name");
        assertEquals(List.of("converter first", "converter second"), warnings(output));
        List<String> required = List.of(WIDE_FILE, MAIN_FILE, REPEAT_FILE, "attachments/a_ages.csv",
                "attachments/z_people.csv", "form.xml", "form.fields.json", "run-report.txt");
        assertEquals(required, reportFiles(output).stream().filter(required::contains).toList());
        assertTrue(report(output).contains("Source: enquête.XLSX\n"));
        List<String> lines = result.out().lines().toList();
        assertEquals(3, lines.size(), result.out());
        inventedSummary(lines.get(2), INVENTED);
    }
}
