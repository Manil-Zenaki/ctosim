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
import static ctosim.CliIdSizingSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliIdSizingPipelineTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(strings = {"district", "village", "household", "category"})
    void expandedAttachmentsAreReloadedBeforePlanningAndExporting(String id) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings(id, 40);
        settings.putObject("fields").putObject(id).put("missingRate", 1);
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString());
        success(result);
        uniqueIds(output, id, 40);
        pipeline(input, input.form().getParent(), output, temp.resolve("oracle"), settings);
        var ages = CliAmendment2Support.lookup(output.resolve("attachments/places.csv"), "household", "age");
        var ids = column(output.resolve(CliIdSizingSupport.WIDE), "household");
        var actualAges = column(output.resolve(CliIdSizingSupport.WIDE), "age");
        for (int i = 0; i < ids.size(); i++) assertEquals(ages.get(ids.get(i)), actualAges.get(i));
        assertEquals(List.of(), warnings(output), "Enough ids must remove exhaustion warnings");
        assertEquals(List.of("CLI sized ids_WIDE.csv", "CLI sized ids.csv", "attachments/categories.csv",
                "attachments/places.csv", "run-report.txt"), reportFiles(output));
        assertTrue(report(output).contains("Fictitious attachments: categories.csv, places.csv\n"));
        assertEquals(3, result.out().lines().count());
        assertTrue(result.out().lines().toList().get(2).contains("places.csv"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cliIdOverridesConfigurationAndLastCountAndSeedReachAttachmentGeneration(boolean beforeForm) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings("district", 4);
        Path config = config(temp, settings.toString()), output = temp.resolve("out");
        byte[] original = Files.readAllBytes(config);
        String[] options = {"--fake-attachments", "--id", "household", "-c", config.toString(),
                "--submissions", "5", "-n", "82", "--seed", "17", "-s", "-901", "-o", output.toString()};
        var args = new java.util.ArrayList<String>();
        if (!beforeForm) args.add(input.form().toString());
        args.addAll(List.of(options));
        if (beforeForm) args.add(input.form().toString());
        success(run(args.toArray(String[]::new)));
        assertEquals(90, rows(output, "places").size() - 1);
        assertEquals(3, new java.util.HashSet<>(values(output, "places", "district")).size());
        settings.put("id", "household").put("submissions", 82).put("seed", -901);
        pipeline(input, input.form().getParent(), output, temp.resolve("oracle"), settings);
        uniqueIds(output, "household", 82);
        assertArrayEquals(original, Files.readAllBytes(config));
        assertTrue(report(output).contains("Seed: -901\nSubmissions: 82\n"));
    }

    @Test void cliCountCanReduceConfiguredExpansionBackToLegacySize() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings("household", 100);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString(), "-n", "27"));
        assertEquals(27, rows(output, "places").size() - 1);
        CliAmendment2Support.assertAttachmentBytes(FakeAttachments.of(
                FormLoader.load(input.form(), input.form().getParent()), settings.get("seed").longValue()), output);
        uniqueIds(output, "household", 27);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unicodeIdFromConfigurationOrCliIsForwardedToTheSizingFactory(boolean cliId) throws Exception {
        Input input = input(temp.resolve("source"));
        String id = "m\u00e9nage";
        write(input.form(), Files.readString(input.form()).replace("household", id));
        write(input.catalog(), Files.readString(input.catalog()).replace("household", id));
        var settings = settings(cliId ? null : id, 28);
        Path output = temp.resolve("out"), config = config(temp, settings.toString());
        Result result = cliId
                ? simulate(input, output, "--fake-attachments", "--id", id, "-c", config.toString())
                : simulate(input, output, "--fake-attachments", "-c", config.toString());
        success(result);
        assertEquals(36, rows(output, "places").size() - 1);
        settings.put("id", id);
        pipeline(input, input.form().getParent(), output, temp.resolve("oracle"), settings);
        uniqueIds(output, id, 28);
    }

    @Test void defaultSubmissionCountAlsoSizesTheFileWhenOnlyCliIdIsGiven() throws Exception {
        Input input = input(temp.resolve("source"));
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "--id", "household"));
        assertEquals(108, rows(output, "places").size() - 1, "Default N=100 rounds up over nine villages");
        uniqueIds(output, "household", 100);
        var defaults = SimulationConfig.defaults();
        CliAmendment2Support.assertAttachmentBytes(FakeAttachments.of(
                FormLoader.load(input.form(), input.form().getParent()), defaults.seed(), "household", 100), output);
    }

    @Test void explicitIdPoolDoesNotChangeWhichFileOrHowManyRowsAreGenerated() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings("category", 12);
        settings.putObject("fields").putObject("category").put("value", "01");
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString()));
        assertEquals(12, rows(output, "categories").size() - 1, "Sizing receives N even when the explicit pool has one id");
        assertEquals(java.util.Collections.nCopies(12, "01"), column(output.resolve(CliIdSizingSupport.WIDE), "category"));
        pipeline(input, input.form().getParent(), output, temp.resolve("oracle"), settings);
        assertEquals(1, warnings(output).size(), "The explicit one-id override still causes reuse");
    }

    @Test void existingMediaIsCopiedExactlyAndNeverExpandedToMeetN() throws Exception {
        Input input = input(temp.resolve("source"));
        Path media = Files.createDirectories(temp.resolve("real-media"));
        Path real = write(media.resolve("places.csv"), resource("id-sizing/places.csv"));
        byte[] bytes = Files.readAllBytes(real);
        var settings = settings("household", 40);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-m", media.toString(),
                "-c", config(temp, settings.toString()).toString()));
        assertArrayEquals(bytes, Files.readAllBytes(output.resolve("attachments/places.csv")));
        assertArrayEquals(bytes, Files.readAllBytes(real));
        assertEquals(3, rows(output, "categories").size() - 1);
        assertTrue(report(output).contains("Fictitious attachments: categories.csv\n"));
        assertFalse(reportFiles(output).contains("attachments/places.csv"), "Only fictitious files are listed");
        pipeline(input, media, output, temp.resolve("oracle"), settings);
    }

    @Test void missingAttachmentIsNotSynthesizedWithoutTheFlagEvenWhenIdNeedsMoreRows() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings("household", 40);
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", config(temp, settings.toString()).toString()));
        assertFalse(Files.exists(output.resolve("attachments")));
        assertFalse(report(output).contains("Fictitious attachments:"));
        assertTrue(warnings(output).contains("attached file places.csv not found"));
    }

    @Test void rerunResizesFromSourceAndDoesNotReusePriorOutputAttachmentsOrId() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings("household", 40);
        Path config = config(temp, settings.toString()), output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config.toString(), "--id", "category", "-n", "12"));
        assertEquals(12, rows(output, "categories").size() - 1);
        success(simulate(input, output, "--fake-attachments", "-c", config.toString()));
        assertEquals(45, rows(output, "places").size() - 1);
        assertEquals(3, rows(output, "categories").size() - 1);
        pipeline(input, input.form().getParent(), output, temp.resolve("oracle"), settings);
    }

    @Test void convertedXlsformUsesEffectiveIdNAndSeedForSizedAttachments() throws Exception {
        Path workbook = workbook(temp.resolve("source"), "survey.XLSX",
                "{\"fixture\":\"id-sizing/cascade\",\"warnings\":[\"converter warning\"]}");
        var settings = settings("district", 3);
        Path config = config(temp, settings.toString()), output = temp.resolve("out");
        Result result = child(temp.resolve("cwd"), converterEnvironment(), "UTC", workbook.toString(),
                "--python", python().toString(), "--fake-attachments", "--id", "household",
                "-n", "28", "-s", "419", "-c", config.toString(), "-o", output.toString());
        success(result);
        settings.put("id", "household").put("submissions", 28).put("seed", 419);
        Input oracle = input(temp.resolve("oracle-source"));
        CliAmendment2Support.assertAttachmentBytes(generated(oracle, oracle.form().getParent(), settings), output);
        assertEquals(36, rows(output, "places").size() - 1);
        uniqueIds(output, "household", 28);
        assertEquals(List.of("converter warning"), warnings(output));
        assertEquals(List.of("CLI sized ids_WIDE.csv", "CLI sized ids.csv", "attachments/categories.csv",
                "attachments/places.csv", "form.xml", "form.fields.json", "run-report.txt"), reportFiles(output));
    }

    @Test void expandedAttachmentsAndExportsAreByteIdenticalAcrossWorkingDirectoriesAndTimeZones() throws Exception {
        var settings = settings("household", 40);
        Map<String, byte[]> first = null;
        for (String zone : List.of("Pacific/Kiritimati", "America/Los_Angeles")) {
            Path root = temp.resolve(zone.replace('/', '_'));
            Input input = input(root.resolve("source"));
            Path config = config(root, settings.toString()), output = root.resolve("out");
            success(child(root.resolve("cwd"), Map.of(), zone, input.form().toString(),
                    "--fake-attachments", "-c", config.toString(), "-o", output.toString()));
            assertEquals(45, rows(output, "places").size() - 1);
            var actual = CliAmendment2Support.tree(output);
            if (first == null) first = actual;
            else identical(first, actual);
        }
    }
}
