package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CliRunTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @Test void defaultsProduceOneHundredSubmissionsAndTheExactReport() throws Exception {
        Input input = fixture(temp.resolve("input"), "simple", "survey.xml");
        Path output = temp.resolve("deep/missing/output");
        Result result = simulate(input, output);
        success(result);
        assertEquals(101, csv(output.resolve(WIDE)).size());
        assertEquals(101, csv(output.resolve(LONG)).size());
        assertEquals("""
                ctosim run report
                Form: CLI enquête (cli_fixture, version 007)
                Source: survey.xml
                Configuration: defaults
                Seed: 1
                Submissions: 100
                Fieldwork: 2026-01-05T08:00:00 for 30 days, time zone UTC
                Enumerators: 5
                Files:
                  CLI enquête_WIDE.csv
                  CLI enquête.csv
                  run-report.txt
                Warnings: 0
                """, report(output));
    }

    @Test void successfulSummaryHasTwoLinesWithCountsFormDirectoryAndReportName() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-n", "3");
        success(result);
        List<String> lines = result.out().lines().toList();
        assertEquals(2, lines.size(), result.out());
        assertAll(() -> assertTrue(lines.get(0).contains("3"), result.out()),
                () -> assertTrue(lines.get(0).contains("cli_fixture"), result.out()),
                () -> assertTrue(lines.get(0).contains(output.toString()), result.out()),
                () -> assertTrue(lines.get(1).contains("0"), result.out()),
                () -> assertTrue(lines.get(1).contains("run-report.txt"), result.out()));
    }

    @ParameterizedTest @ValueSource(strings = {".xml", ".XML", ".XmL"})
    void xmlSuffixIsCaseInsensitiveAndCatalogReplacesOnlyTheLastExtension(String suffix) throws Exception {
        Input input = fixture(temp, "simple", "survey.v2" + suffix);
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "1"));
        assertEquals(2, csv(output.resolve(WIDE)).size());
        assertFalse(Files.exists(output.resolve("form.xml")));
        assertFalse(Files.exists(output.resolve("form.fields.json")));
        assertEquals(List.of(WIDE, LONG, "run-report.txt"), reportFiles(output));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shortAndLongOptionsWorkOnBothSidesOfFormAndOverrideConfig(boolean longOptions) throws Exception {
        Input input = fixture(temp.resolve("input"), "simple", "survey.xml");
        Path config = config(temp, """
                {"submissions":7,"seed":17,"start":"2027-02-03T04:05:06","fieldworkDays":9,
                 "timeZone":"Africa/Addis_Ababa","enumerators":12,
                 "fields":{"answer":{"value":"déjà 家庭"},"quantity":{"value":41}}}
                """);
        Path output = temp.resolve("space and unicode é/out");
        String c = longOptions ? "--config" : "-c";
        String n = longOptions ? "--submissions" : "-n";
        String s = longOptions ? "--seed" : "-s";
        String o = longOptions ? "--output" : "-o";
        String m = longOptions ? "--media" : "-m";
        success(run(n, "2", s, "-23", c, config.toString(), input.form().toString(),
                o, output.toString(), m, input.form().getParent().toString(), "--server", "demo-42"));
        assertEquals(List.of("déjà 家庭", "déjà 家庭"), column(output.resolve(WIDE), "answer"));
        assertEquals(List.of("41", "41"), column(output.resolve(WIDE), "quantity"));
        String report = report(output);
        assertAll(() -> assertTrue(report.contains("Seed: -23\n")),
                () -> assertTrue(report.contains("Submissions: 2\n")),
                () -> assertTrue(report.contains("Configuration: settings.json\n")),
                () -> assertTrue(report.contains("Fieldwork: 2027-02-03T04:05:06 for 9 days, time zone Africa/Addis_Ababa\n")),
                () -> assertTrue(report.contains("Enumerators: 12\n")));
        assertTrue(column(output.resolve(WIDE), "photo").stream()
                .allMatch(url -> url.startsWith("https://demo-42.surveycto.com/")));
    }

    @Test void configurationSuppliesCountAndSeedWhenNeitherIsOverridden() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path config = config(temp, "{\"submissions\":4,\"seed\":87}");
        Path output = temp.resolve("out");
        success(simulate(input, output, "--config", config.toString()));
        assertEquals(5, csv(output.resolve(WIDE)).size());
        assertTrue(report(output).contains("Seed: 87\nSubmissions: 4\n"));
    }

    @ParameterizedTest @ValueSource(strings = {"-9223372036854775808", "9223372036854775807", "0", "-1"})
    void everyBoundaryLongSeedIsAcceptedExactly(String seed) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "1", "--seed", seed));
        assertTrue(report(output).contains("Seed: " + seed + "\n"));
    }

    @Test void duplicateOptionsKeepLastValuesAcrossAliases() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path config = config(temp, "{\"submissions\":8,\"seed\":88}");
        Path output = temp.resolve("chosen");
        Path unused = temp.resolve("unused");
        success(run("-c", temp.resolve("unread.json").toString(), "--config", config.toString(),
                "-n", "6", "--submissions", "2", "--seed", "11", "-s", "12",
                "-o", unused.toString(), "--output", output.toString(),
                "-m", temp.resolve("missing-media").toString(), "--media", temp.toString(),
                "--server", "first", "--server", "last", input.form().toString()));
        assertFalse(Files.exists(unused));
        assertEquals(3, csv(output.resolve(WIDE)).size());
        assertTrue(report(output).contains("Seed: 12\nSubmissions: 2\n"));
        assertTrue(column(output.resolve(WIDE), "photo").get(0).startsWith("https://last.surveycto.com/"));
    }

    @Test void defaultServerIsExample() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "1"));
        assertTrue(column(output.resolve(WIDE), "photo").get(0).startsWith("https://example.surveycto.com/"));
    }

    @Test void outputReplacesOwnedFilesAndPreservesUnrelatedFilesAndXmlSource() throws Exception {
        Input input = fixture(temp.resolve("in"), "simple", "survey.xml");
        byte[] formBefore = Files.readAllBytes(input.form());
        byte[] catalogBefore = Files.readAllBytes(input.catalog());
        Path output = Files.createDirectories(temp.resolve("out"));
        for (String name : List.of(WIDE, LONG, "run-report.txt")) write(output.resolve(name), "stale trailing junk");
        Path keep = write(output.resolve("unrelated.csv"), "keep me\n");
        Path nested = write(output.resolve("nested/keep.txt"), "nested sentinel");
        success(simulate(input, output, "-n", "1"));
        var first = snapshot(output);
        success(simulate(input, output, "-n", "1"));
        identical(first, snapshot(output));
        assertEquals("keep me\n", Files.readString(keep));
        assertEquals("nested sentinel", Files.readString(nested));
        assertArrayEquals(formBefore, Files.readAllBytes(input.form()));
        assertArrayEquals(catalogBefore, Files.readAllBytes(input.catalog()));
        assertEquals(List.of(WIDE, LONG, "run-report.txt"), reportFiles(output));
        assertEquals(2, csv(output.resolve(WIDE)).size());
        assertFalse(report(output).contains("stale"));
    }

    @Test void reportUsesUtf8LfOnlyOneFinalLfAndNoAbsolutePaths() throws Exception {
        Input input = fixture(temp.resolve("private input"), "simple", "enquête.v2.xml");
        Path config = config(temp.resolve("private config"), "{\"submissions\":1}");
        Path output = temp.resolve("private output");
        success(simulate(input, output, "-c", config.toString()));
        byte[] bytes = Files.readAllBytes(output.resolve("run-report.txt"));
        String report = report(output);
        assertAll(() -> assertFalse(report.startsWith("\ufeff")),
                () -> assertFalse(report.contains("\r")),
                () -> assertTrue(report.endsWith("\n")),
                () -> assertFalse(report.endsWith("\n\n")),
                () -> assertFalse(report.contains(temp.toString())),
                () -> assertTrue(report.contains("Source: enquête.v2.xml\n")),
                () -> assertArrayEquals(report.getBytes(java.nio.charset.StandardCharsets.UTF_8), bytes));
    }

    @Test void repeatsExportInCatalogOrderAndMissingVersionIsNone() throws Exception {
        Input input = fixture(temp, "repeats", "repeats.xml");
        Path config = config(temp, "{\"submissions\":2,\"repeats\":{\"min\":2,\"max\":2}}");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", config.toString()));
        assertTrue(report(output).contains("Form: Repeat CLI (repeat_cli, version none)\n"));
        assertEquals(List.of("Repeat CLI_WIDE.csv", "Repeat CLI.csv", "Repeat CLI-zeta.csv",
                "Repeat CLI-alpha.csv", "run-report.txt"), reportFiles(output));
        assertEquals(3, csv(output.resolve("Repeat CLI_WIDE.csv")).size());
        assertEquals(5, csv(output.resolve("Repeat CLI-zeta.csv")).size());
        assertEquals(5, csv(output.resolve("Repeat CLI-alpha.csv")).size());
    }

    @Test void exportedBytesEqualWalkingSubmissionsOneThroughNWithEffectiveConfiguration() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path config = config(temp, "{\"submissions\":9,\"seed\":99,\"enumerators\":13,\"timeZone\":\"+03:00\"}");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", config.toString(), "-n", "3", "-s", "42", "--server", "expected"));
        LoadedForm form = FormLoader.load(input.form(), temp);
        FormWalker walker = new FormWalker(form, SimulationConfig.load(config).withSubmissions(3).withSeed(42));
        List<Submission> submissions = new ArrayList<>();
        for (int n = 1; n <= 3; n++) submissions.add(walker.walk(n));
        SurveyCtoExporter exporter = new SurveyCtoExporter(FieldCatalog.load(input.catalog()), form, "expected");
        assertEquals(exporter.wide(submissions), Files.readString(output.resolve(exporter.wideFileName())));
        for (var entry : exporter.longFiles(submissions).entrySet())
            assertEquals(entry.getValue(), Files.readString(output.resolve(entry.getKey())), entry.getKey());
        assertEquals(3, column(output.resolve(WIDE), "instanceID").stream().distinct().count());
    }

    @Test void seedChangesAffectExportedData() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path first = temp.resolve("first"), second = temp.resolve("second");
        success(simulate(input, first, "-n", "3", "-s", "123"));
        success(simulate(input, second, "-n", "3", "-s", "456"));
        assertNotEquals(Files.readString(first.resolve(WIDE)), Files.readString(second.resolve(WIDE)));
    }

    @Test void defaultOutputUsesWorkingDirectoryAndRemovesOnlyFinalExtension() throws Exception {
        Input input = fixture(temp.resolve("input elsewhere"), "simple", "survey.v2.XML");
        Path cwd = temp.resolve("working directory");
        success(child(cwd, Map.of(), "UTC", input.form().toString(), "-n", "1"));
        Path output = cwd.resolve("ctosim-out/survey.v2");
        assertTrue(Files.isRegularFile(output.resolve("run-report.txt")));
        assertEquals(2, csv(output.resolve(WIDE)).size());
        assertFalse(Files.exists(input.form().getParent().resolve("ctosim-out")));
    }

    @Test void changingWorkingDirectoryAndMachineTimeZoneDoesNotChangeAnyOutputBytes() throws Exception {
        for (String dir : List.of("first", "second")) fixture(temp.resolve(dir), "simple", "survey.xml");
        success(child(temp.resolve("first"), Map.of(), "Pacific/Honolulu", "survey.xml", "-n", "3", "-s", "42"));
        success(child(temp.resolve("second"), Map.of(), "Asia/Kathmandu", "survey.xml", "-n", "3", "-s", "42"));
        identical(snapshot(temp.resolve("first/ctosim-out/survey")), snapshot(temp.resolve("second/ctosim-out/survey")));
    }
}
