package ctosim;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Amendment 2 orchestration oracle, using only published component APIs. */
final class CliAmendment2Support {
    static final String WIDE_FILE = "Fake CLI_WIDE.csv";
    static final String MAIN_FILE = "Fake CLI.csv";
    static final String REPEAT_FILE = "Fake CLI-children.csv";
    static final List<String> INVENTED = List.of("a_ages", "z_people");
    static final String CLEAN_CONFIG = "{\"missingRate\":0,\"specialRate\":0}";

    static Input lookups(Path directory) throws Exception {
        Input input = fixture(directory, "amendment2/lookups", "survey.xml");
        write(directory.resolve("real.csv"), resource("amendment2/real.csv"));
        return input;
    }

    static Result fake(Input input, Path output, Path configuration, String... extra) {
        var options = new ArrayList<>(List.of("--fake-attachments", "-n", "8",
                "-c", configuration.toString()));
        options.addAll(List.of(extra));
        return simulate(input, output, options.toArray(String[]::new));
    }

    static FakeAttachments expected(Input input, Path media, long seed) throws Exception {
        return FakeAttachments.of(FormLoader.load(input.form(), media), seed);
    }

    static void assertAttachmentBytes(FakeAttachments expected, Path output) throws Exception {
        for (var entry : expected.files().entrySet()) {
            assertArrayEquals(entry.getValue().getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(output.resolve("attachments/" + entry.getKey() + ".csv")),
                    "CLI must persist FakeAttachments content exactly: " + entry.getKey());
        }
    }

    static Map<String, byte[]> tree(Path directory) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                result.put(directory.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
        }
        return result;
    }

    static List<List<String>> fictitiousCsv(Path path) throws Exception {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("\n"), "Spec 12 requires a final LF in fictitious attachments");
        // Remove only the required record terminator; preserve quoted cells and all other bytes.
        return SurveyCtoExporterTestSupport.csv(text.substring(0, text.length() - 1));
    }

    static Map<String, String> lookup(Path csvFile, String key, String value) throws Exception {
        var rows = fictitiousCsv(csvFile);
        int k = rows.get(0).indexOf(key), v = rows.get(0).indexOf(value);
        assertTrue(k >= 0 && v >= 0, rows.get(0).toString());
        Map<String, String> lookup = new LinkedHashMap<>();
        for (var row : rows.subList(1, rows.size())) lookup.put(row.get(k), row.get(v));
        return lookup;
    }

    static void assertLookupColumn(Path output, String key, String calculated,
                                   String file, String csvKey, String csvValue) throws Exception {
        var values = lookup(output.resolve("attachments/" + file + ".csv"), csvKey, csvValue);
        var keys = column(output.resolve(WIDE_FILE), key);
        var actual = column(output.resolve(WIDE_FILE), calculated);
        assertFalse(keys.isEmpty());
        for (int i = 0; i < keys.size(); i++) {
            assertTrue(values.containsKey(keys.get(i)), "Answer must be a generated key: " + keys.get(i));
            assertEquals(values.get(keys.get(i)), actual.get(i), "Reloaded lookup in submission " + (i + 1));
        }
    }

    static void assertPublicPipeline(Input input, Path originalMedia, Path output, Path oracleDirectory,
                                     String configJson, long seed, int count,
                                     String... integerKeyQuestions) throws Exception {
        FakeAttachments generated = expected(input, originalMedia, seed);
        Path media = Files.createDirectories(oracleDirectory.resolve("media"));
        try (var sources = Files.list(originalMedia)) {
            for (Path source : sources.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv")).toList())
                Files.copy(source, media.resolve(source.getFileName()));
        }
        for (var entry : generated.files().entrySet())
            write(media.resolve(entry.getKey() + ".csv"), entry.getValue());
        ObjectNode settings = (ObjectNode) JSON.readTree(configJson);
        ObjectNode fields = settings.with("fields");
        generated.keyAnswers().forEach((name, keys) -> {
            if (fields.has(name)) return;
            // The test fixture declares which key questions are integers.
            if (List.of(integerKeyQuestions).contains(name)) {
                assertEquals(java.util.stream.IntStream.rangeClosed(1, keys.size())
                        .mapToObj(Integer::toString).toList(), keys, "Integer keys are row numbers");
                fields.putObject(name).put("min", 1).put("max", keys.size());
            } else {
                fields.putObject(name).set("texts", JSON.valueToTree(keys));
            }
        });
        SimulationConfig config = SimulationConfig.parse(settings.toString(), "oracle.json")
                .withSeed(seed).withSubmissions(count);
        LoadedForm reloaded = FormLoader.load(input.form(), media);
        FormWalker walker = new FormWalker(reloaded, config);
        var submissions = new ArrayList<Submission>();
        for (int i = 1; i <= count; i++) submissions.add(walker.walk(i));
        SurveyCtoExporter exporter = new SurveyCtoExporter(FieldCatalog.load(input.catalog()), reloaded, "example");
        assertEquals(exporter.wide(submissions), Files.readString(output.resolve(exporter.wideFileName())));
        for (var entry : exporter.longFiles(submissions).entrySet())
            assertEquals(entry.getValue(), Files.readString(output.resolve(entry.getKey())), entry.getKey());
    }

    static void inventedSummary(String line, List<String> names) {
        for (String name : names) {
            assertTrue(line.contains(name + ".csv"), "Summary must name generated files: " + line);
        }
        assertTrue(line.toLowerCase(java.util.Locale.ROOT).matches(".*(invent|fictiti|fabricat|synthetic).*"),
                "Summary must disclose invented content: " + line);
    }
}
