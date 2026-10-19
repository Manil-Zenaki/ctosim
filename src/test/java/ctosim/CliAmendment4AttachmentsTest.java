package ctosim;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment4AttachmentsTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;
    static final String KEYS_WIDE = "CLI plan keys_WIDE.csv";

    private Input keys(boolean integer) throws Exception {
        Input input = fixture(temp.resolve("source"), "amendment4/keys", "survey.xml");
        if (integer) {
            write(input.form(), Files.readString(input.form()).replace("nodeset=\"/data/key\" type=\"string\"",
                    "nodeset=\"/data/key\" type=\"int\""));
            write(input.catalog(), Files.readString(input.catalog()).replace("\"type\":\"text\"", "\"type\":\"integer\""));
        }
        return input;
    }

    /** Build the effective configuration before constructing the public plan. */
    private Expected fakeExpected(Input input, ObjectNode settings, boolean integer, Path output) throws Exception {
        FakeAttachments generated = FakeAttachments.of(FormLoader.load(input.form(), input.form().getParent()),
                settings.get("seed").longValue());
        CliAmendment2Support.assertAttachmentBytes(generated, output);
        Path media = Files.createDirectories(temp.resolve("oracle-media"));
        for (var file : generated.files().entrySet()) write(media.resolve(file.getKey() + ".csv"), file.getValue());
        var effective = settings.deepCopy();
        ObjectNode fields = effective.with("fields");
        var keys = generated.keyAnswers().get("key");
        assertNotNull(keys);
        assertEquals(50, new HashSet<>(keys).size(), "fixture provides 50 distinct IDs");
        if (!fields.has("key")) {
            if (integer) fields.putObject("key").put("min", 1).put("max", keys.size());
            else fields.putObject("key").set("texts", JSON.valueToTree(keys));
        }
        return expected(input, media, effective);
    }

    @ParameterizedTest
    @CsvSource({"false,false,50", "false,true,51", "true,false,50", "true,true,51"})
    void suggestedKeysAreInstalledBeforePlanningForConfigOrCliId(boolean integer, boolean cliId, int count) throws Exception {
        Input input = keys(integer);
        var settings = settings();
        settings.put("submissions", count).put("missingRate", 1);
        settings.with("integerRange").put("min", 700).put("max", 700);
        if (!cliId) settings.put("id", "key");
        Path output = temp.resolve("out"), config = configuration(temp, settings);
        Result result = cliId
                ? simulate(input, output, "--fake-attachments", "--id", "key", "-c", config.toString())
                : simulate(input, output, "--fake-attachments", "-c", config.toString());
        success(result);
        settings.put("id", "key");
        Expected expected = fakeExpected(input, settings, integer, output);
        exports(input, output, expected, "example");
        var ids = column(output.resolve(KEYS_WIDE), "key");
        var rows = CliAmendment2Support.lookup(output.resolve("attachments/people.csv"), "id", "full_name");
        cycle(ids, rows.keySet());
        var names = column(output.resolve(KEYS_WIDE), "person");
        for (int i = 0; i < count; i++) assertEquals(rows.get(ids.get(i)), names.get(i));
        assertEquals(count > 50 ? 1 : 0, warnings(output).size());
        assertEquals(expectedWarnings(expected), warnings(output));
        assertEquals(3, result.out().lines().count(), "only the ordinary and fictitious-content summaries");
        number(result.out().lines().toList().get(1), count > 50 ? 1 : 0);
        assertTrue(report(output).contains("Fictitious attachments: people.csv\n"));
    }

    @ParameterizedTest @ValueSource(strings = {"value", "texts", "missingRate"})
    void explicitKeyOverrideIsPreservedWhenTheSameQuestionIsTheSurveyId(String override) throws Exception {
        Input input = keys(false);
        var settings = settings();
        settings.put("submissions", 4).put("id", "key");
        ObjectNode key = settings.putObject("fields").putObject("key");
        switch (override) {
            case "value" -> key.put("value", "K0007");
            case "texts" -> key.putArray("texts").add("K0003").add("K0017");
            default -> key.put("missingRate", 0);
        }
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", configuration(temp, settings).toString()));
        Expected expected = fakeExpected(input, settings, false, output);
        exports(input, output, expected, "example");
        assertEquals(expectedWarnings(expected), warnings(output));
        assertEquals(1, warnings(output).size(), "exhausted explicit pool, or no possible IDs for missingRate alone");
        if (override.equals("value")) cycle(column(output.resolve(KEYS_WIDE), "key"), Set.of("K0007"));
        if (override.equals("texts")) cycle(column(output.resolve(KEYS_WIDE), "key"), Set.of("K0003", "K0017"));
    }

    @Test void withoutFakeAttachmentsTheTextKeyHasNoPossibleIdsAndBothWarningsRemain() throws Exception {
        Input input = keys(false);
        var settings = settings();
        settings.put("id", "key").put("submissions", 2);
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", configuration(temp, settings).toString()));
        Expected expected = expected(input, input.form().getParent(), settings);
        assertEquals(expectedWarnings(expected), warnings(output));
        assertEquals("attached file people.csv not found", warnings(output).get(0));
        assertEquals(2, warnings(output).size());
        assertTrue(warnings(output).get(1).contains("key"));
        assertFalse(Files.exists(output.resolve("attachments")));
    }

    @Test void converterWarningsPrecedePlanWarningsAndConvertedFormsAlsoUseThePlan() throws Exception {
        Path workbook = workbook(temp.resolve("source"), "survey.XLSX", """
                {"fixture":"amendment4/keys","warnings":["conversion first","conversion second"]}
                """);
        var settings = settings();
        settings.put("submissions", 51);
        Path config = configuration(temp, settings), output = temp.resolve("out");
        Result result = child(temp.resolve("cwd"), converterEnvironment(), "UTC", workbook.toString(),
                "--python", python().toString(), "--fake-attachments", "--id", "key", "-s", "713",
                "-c", config.toString(), "-o", output.toString());
        success(result);
        settings.put("seed", 713).put("id", "key");
        Input converted = new Input(output.resolve("form.xml"), output.resolve("form.fields.json"));
        // Inspect the original, empty source media when constructing the oracle, not CLI output attachments.
        Input oracleInput = fixture(temp.resolve("oracle-source"), "amendment4/keys", "survey.xml");
        Expected expected = fakeExpected(oracleInput, settings, false, output);
        exports(converted, output, expected, "example");
        var allWarnings = new java.util.ArrayList<>(List.of("conversion first", "conversion second"));
        allWarnings.addAll(expectedWarnings(expected));
        assertEquals(allWarnings, warnings(output));
        assertEquals(3, allWarnings.size());
        assertEquals(List.of(KEYS_WIDE, "CLI plan keys.csv", "attachments/people.csv",
                "form.xml", "form.fields.json", "run-report.txt"), reportFiles(output));
    }
}
