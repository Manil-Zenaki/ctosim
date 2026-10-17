package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

class CliAmendment2PipelineTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @Test void generatedFilesAreWrittenExactlyWithTheDefaultSeed() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("new/parents/output");
        success(simulate(input, output, "--fake-attachments", "-n", "1"));
        FakeAttachments generated = expected(input, input.form().getParent(), 1);
        assertEquals(INVENTED, List.copyOf(generated.files().keySet()), "Fixture must exercise two missing files");
        assertAttachmentBytes(generated, output);
        for (String name : INVENTED) assertEquals(51, csv(output.resolve("attachments/" + name + ".csv")).size());
    }

    @ParameterizedTest @ValueSource(strings = {"-9223372036854775808", "9223372036854775807", "0", "-81"})
    void finalCommandLineSeedOverridesConfigurationForEveryGeneratedFile(String seed) throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path config = config(temp, "{\"seed\":18,\"submissions\":2,\"missingRate\":0}");
        Path output = temp.resolve("output");
        success(fake(input, output, config, "-s", "42", "--seed", seed, "--submissions", "1"));
        assertAttachmentBytes(expected(input, input.form().getParent(), Long.parseLong(seed)), output);
        assertTrue(report(output).contains("Seed: " + seed + "\nSubmissions: 1\n"));
    }

    @Test void configurationSeedIsUsedWhenNoSeedOptionIsGiven() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, "{\"seed\":781,\"missingRate\":0}")));
        assertAttachmentBytes(expected(input, input.form().getParent(), 781), output);
    }

    @Test void generatedKeysReplaceAnUnconfiguredInstanceDefaultAndReloadedCalculationsMatchRows() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG), "-n", "32"));
        assertLookupColumn(output, "qkey", "person", "z_people", "id", "full_name");
        assertLookupColumn(output, "otherkey", "age", "a_ages", "code", "age");
        assertLookupColumn(output, "qkey", "child_count", "z_people", "id", "count");
        assertFalse(column(output.resolve(WIDE_FILE), "qkey").contains("OLD-KEY"));
    }

    @Test void reloadedPreloadsActivateBranchesAndDriveRepeatExports() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG)));
        assertTrue(column(output.resolve(WIDE_FILE), "followup").stream().allMatch(s -> !s.isEmpty()));
        int total = column(output.resolve(WIDE_FILE), "child_count").stream().mapToInt(Integer::parseInt).sum();
        assertTrue(total >= 8 && total <= 32);
        assertEquals(total + 1, csv(output.resolve(REPEAT_FILE)).size(), "Every fictitious count drives a repeat");
        assertTrue(column(output.resolve(REPEAT_FILE), "item").stream().allMatch(s -> !s.isEmpty()));
    }

    @Test void allRealCsvFilesAreCopiedByteForByteAndRemainReadableAfterReload() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path media = input.form().getParent();
        write(media.resolve("unused.csv"), "id,text\r\n7,\"unread, é\"\r\n");
        Map<String, byte[]> before = tree(media);
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG)));
        for (String name : List.of("real.csv", "unused.csv"))
            assertArrayEquals(before.get(name), Files.readAllBytes(output.resolve("attachments/" + name)), name);
        assertEquals(java.util.Collections.nCopies(8, "Réel, déjà là"), column(output.resolve(WIDE_FILE), "real_value"));
        identical(before, tree(media));
    }

    @Test void anExistingFileWinsOverInventionWhileTheOtherMissingFileIsStillBuilt() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path real = write(input.form().getParent().resolve("z_people.csv"),
                "id,full_name,count\nOLD-KEY,Actual person,2\n");
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG)));
        assertArrayEquals(Files.readAllBytes(real), Files.readAllBytes(output.resolve("attachments/z_people.csv")));
        assertEquals(java.util.Collections.nCopies(8, "OLD-KEY"), column(output.resolve(WIDE_FILE), "qkey"));
        assertEquals(java.util.Collections.nCopies(8, "Actual person"), column(output.resolve(WIDE_FILE), "person"));
        assertLookupColumn(output, "otherkey", "age", "a_ages", "code", "age");
        assertTrue(report(output).contains("Fictitious attachments: a_ages.csv\n"));
    }

    @ParameterizedTest @CsvSource({"--media", "-m"})
    void explicitMediaDirectoryControlsBothMissingDetectionAndCopying(String option) throws Exception {
        Input input = lookups(temp.resolve("input"));
        write(input.form().getParent().resolve("z_people.csv"), "id,full_name,count\nOLD-KEY,Wrong directory,1\n");
        Path media = Files.createDirectories(temp.resolve("selected media é"));
        write(media.resolve("real.csv"), "id,label\nR1,Selected directory\n");
        write(media.resolve("extra.csv"), "a,b\n1,2\n");
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, CLEAN_CONFIG), option, media.toString()));
        assertAttachmentBytes(expected(input, media, 1), output);
        assertTrue(report(output).contains("Fictitious attachments: a_ages.csv, z_people.csv\n"));
        assertEquals(java.util.Collections.nCopies(8, "Selected directory"), column(output.resolve(WIDE_FILE), "real_value"));
        assertArrayEquals(Files.readAllBytes(media.resolve("extra.csv")),
                Files.readAllBytes(output.resolve("attachments/extra.csv")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"value\":\"USER-KEY\"}", "{\"texts\":[\"USER-KEY\"]}",
            "{\"expr\":\"'USER-KEY'\"}", "{\"when\":[{\"if\":\"true()\",\"value\":\"USER-KEY\"}]}"})
    void anyExplicitAnswerOverrideTakesPrecedenceOverSuggestedKeys(String override) throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        String json = "{\"missingRate\":0,\"fields\":{\"qkey\":" + override + "}}";
        success(fake(input, output, config(temp, json)));
        assertEquals(java.util.Collections.nCopies(8, "USER-KEY"), column(output.resolve(WIDE_FILE), "qkey"));
        assertLookupColumn(output, "otherkey", "age", "a_ages", "code", "age");
        assertTrue(Files.isRegularFile(output.resolve("attachments/z_people.csv")), "Override does not suppress file creation");
    }

    @Test void merelyNamingAFieldInConfigurationSuppressesSuggestedTexts() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        String json = "{\"missingRate\":0,\"fields\":{\"qkey\":{\"missingRate\":0}}}";
        success(fake(input, output, config(temp, json)));
        var keys = lookup(output.resolve("attachments/z_people.csv"), "id", "full_name").keySet();
        assertTrue(column(output.resolve(WIDE_FILE), "qkey").stream().allMatch(s -> !s.isEmpty() && !keys.contains(s)),
                "Do not merge suggested texts into an existing field entry, even one without an answer setting");
        assertPublicPipeline(input, input.form().getParent(), output, temp.resolve("oracle"), json, 1, 8);
    }

    @Test void injectingKeysPreservesOtherSettingsAndExplicitFieldsExactly() throws Exception {
        Input input = lookups(temp.resolve("input"));
        String json = """
                {"seed":932,"submissions":2,"missingRate":0,"specialRate":0,
                 "start":"2027-02-03T04:05:06","fieldworkDays":4,"timeZone":"Africa/Addis_Ababa",
                 "enumerators":9,"speedViolationRate":0,"integerRange":{"min":7,"max":7},
                 "fields":{"followup":{"value":"Keep é"},"item":{"texts":["First","Second"]}}}
                """;
        Path config = config(temp, json), output = temp.resolve("output");
        byte[] before = Files.readAllBytes(config);
        success(fake(input, output, config, "-n", "3", "-s", "-17"));
        assertPublicPipeline(input, input.form().getParent(), output, temp.resolve("oracle"), json, -17, 3);
        assertArrayEquals(before, Files.readAllBytes(config), "Synthetic field settings must not rewrite user configuration");
        assertTrue(report(output).contains("Fieldwork: 2027-02-03T04:05:06 for 4 days, time zone Africa/Addis_Ababa\nEnumerators: 9\n"));
    }

    @Test void suggestedTextsStillObeyGlobalMissingRateForOptionalQuestions() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, "{\"missingRate\":1}")));
        assertTrue(column(output.resolve(WIDE_FILE), "qkey").stream().allMatch(String::isEmpty));
        assertLookupColumn(output, "otherkey", "age", "a_ages", "code", "age");
    }

    @Test void integerKeySuggestionsHaveExactlyTheSpecifiedTextsOverrideSemantics() throws Exception {
        Input input = lookups(temp.resolve("input"));
        write(input.form(), Files.readString(input.form()).replace(
                "nodeset=\"/data/otherkey\" type=\"string\"", "nodeset=\"/data/otherkey\" type=\"int\""));
        write(input.catalog(), Files.readString(input.catalog()).replace(
                "\"name\":\"otherkey\",\"type\":\"text\"", "\"name\":\"otherkey\",\"type\":\"integer\""));
        String json = "{\"missingRate\":0,\"integerRange\":{\"min\":700,\"max\":700}}";
        Path output = temp.resolve("output");
        success(fake(input, output, config(temp, json)));
        assertPublicPipeline(input, input.form().getParent(), output, temp.resolve("oracle"), json, 1, 8);
    }

    @Test void searchOnlyKeysNeedNoTextOverridesAndReloadProducesCascadingChoices() throws Exception {
        Input input = fixture(temp.resolve("input"), "amendment2/search", "survey.xml");
        Path output = temp.resolve("résultat é/output");
        success(fake(input, output, config(temp, CLEAN_CONFIG), "-n", "24"));
        FakeAttachments generated = expected(input, input.form().getParent(), 1);
        assertTrue(generated.keyAnswers().isEmpty(), "Selects are not TEXT/INTEGER key questions");
        assertAttachmentBytes(generated, output);
        var regions = column(output.resolve("Search CLI_WIDE.csv"), "region");
        var villages = column(output.resolve("Search CLI_WIDE.csv"), "village");
        var labels = column(output.resolve("Search CLI_WIDE.csv"), "label");
        for (int i = 0; i < regions.size(); i++) {
            assertTrue(List.of("1", "2", "3").contains(regions.get(i)));
            assertTrue(villages.get(i).matches(regions.get(i) + "[123]"), "Cascade must read the reloaded media");
            assertEquals("LIBELLÉ " + villages.get(i), labels.get(i));
        }
        assertTrue(warnings(output).isEmpty(), warnings(output).toString());
    }
}
