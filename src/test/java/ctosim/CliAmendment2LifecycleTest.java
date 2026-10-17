package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

class CliAmendment2LifecycleTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void flagTakesNoValueBeforeOrAfterFormOrBetweenOptions(int position) throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        String[] args = switch (position) {
            case 0 -> new String[]{"--fake-attachments", input.form().toString(), "-n", "1", "-o", output.toString()};
            case 1 -> new String[]{input.form().toString(), "--fake-attachments", "-n", "1", "-o", output.toString()};
            default -> new String[]{"-n", "1", "--fake-attachments", "-o", output.toString(), input.form().toString()};
        };
        success(run(args));
        assertTrue(Files.isRegularFile(output.resolve("attachments/a_ages.csv")));
    }

    @Test void repeatedFlagIsIdempotent() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path config = config(temp, CLEAN_CONFIG), first = temp.resolve("first"), second = temp.resolve("second");
        success(fake(input, first, config));
        success(fake(input, second, config, "--fake-attachments", "--fake-attachments"));
        identical(tree(first), tree(second));
    }

    @Test void omittingFlagRetainsMissingWarningsAndDoesNotInventAnswersOrFiles() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output"), config = config(temp, CLEAN_CONFIG);
        Result result = simulate(input, output, "-c", config.toString(), "-n", "2");
        success(result);
        assertFalse(Files.exists(output.resolve("attachments")));
        assertFalse(report(output).contains("Fictitious attachments:"));
        assertTrue(warnings(output).containsAll(List.of("attached file a_ages.csv not found", "attached file z_people.csv not found")));
        assertEquals(List.of("OLD-KEY", "OLD-KEY"), column(output.resolve(WIDE_FILE), "qkey"));
        assertEquals(List.of("", ""), column(output.resolve(WIDE_FILE), "person"));
        assertEquals(2, result.out().lines().count());
    }

    @Test void subsequentRunWithoutFlagDoesNotRetainSyntheticKeysOrAttachmentsFromPriorRun() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path config = config(temp, CLEAN_CONFIG), first = temp.resolve("fake"), second = temp.resolve("plain");
        success(fake(input, first, config));
        success(simulate(input, second, "-c", config.toString(), "-n", "2"));
        assertEquals(List.of("OLD-KEY", "OLD-KEY"), column(second.resolve(WIDE_FILE), "qkey"));
        assertEquals(List.of("", ""), column(second.resolve(WIDE_FILE), "person"));
        assertFalse(report(second).contains("Fictitious attachments:"));
        assertTrue(warnings(second).contains("attached file z_people.csv not found"));
    }

    @Test void noMissingFilesLeavesExportsUnchangedAndDoesNotTouchAnAttachmentsPath() throws Exception {
        Input input = lookups(temp.resolve("input"));
        write(input.form().getParent().resolve("z_people.csv"), "id,full_name,count\nOLD-KEY,Actual person,1\n");
        write(input.form().getParent().resolve("a_ages.csv"), "code,age\nREAL,31\n");
        Path config = config(temp, "{\"missingRate\":0,\"fields\":{\"otherkey\":{\"value\":\"REAL\"}}}");
        Path plain = temp.resolve("plain"), flagged = temp.resolve("flagged");
        write(flagged.resolve("attachments"), "leave this unrelated file alone");
        success(simulate(input, plain, "-c", config.toString(), "-n", "8"));
        success(fake(input, flagged, config));
        for (String name : List.of(WIDE_FILE, MAIN_FILE, REPEAT_FILE))
            assertArrayEquals(Files.readAllBytes(plain.resolve(name)), Files.readAllBytes(flagged.resolve(name)));
        assertEquals("leave this unrelated file alone", Files.readString(flagged.resolve("attachments")));
        assertEquals(warnings(plain), warnings(flagged));
    }

    @Test void formWithNoAttachmentReferencesAlsoNeedsNoAttachmentsDirectory() throws Exception {
        Input input = fixture(temp.resolve("input"), "simple", "survey.xml");
        Path output = temp.resolve("output");
        write(output.resolve("attachments"), "unrelated sentinel");
        success(simulate(input, output, "--fake-attachments", "-n", "1"));
        assertEquals("unrelated sentinel", Files.readString(output.resolve("attachments")));
        assertEquals(2, csv(output.resolve(WIDE)).size());
    }

    @Test void rerunReplacesGeneratedAndCopiedFilesButPreservesUnrelatedOutputAndInputs() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Map<String, byte[]> original = tree(input.form().getParent());
        Path output = temp.resolve("output"), config = config(temp, CLEAN_CONFIG);
        success(fake(input, output, config));
        Map<String, byte[]> clean = tree(output);
        write(output.resolve("attachments/a_ages.csv"), "stale fictitious data with trailing junk");
        write(output.resolve("attachments/real.csv"), "stale copied data");
        write(output.resolve("attachments/keep.txt"), "keep nested");
        write(output.resolve("unrelated.csv"), "keep top level");
        success(fake(input, output, config));
        Map<String, byte[]> rerun = tree(output);
        assertEquals("keep nested", Files.readString(output.resolve("attachments/keep.txt")));
        assertEquals("keep top level", Files.readString(output.resolve("unrelated.csv")));
        rerun.remove("attachments/keep.txt"); rerun.remove("unrelated.csv");
        identical(clean, rerun);
        identical(original, tree(input.form().getParent()));
    }

    @Test void sameSeedIsByteIdenticalIncludingAttachmentsAcrossWorkingDirectoriesAndTimeZones() throws Exception {
        Map<String, byte[]> first = null;
        for (String zone : List.of("Pacific/Honolulu", "Asia/Kolkata")) {
            Path cwd = Files.createDirectories(temp.resolve(zone.replace('/', '_')));
            lookups(cwd.resolve("input"));
            config(cwd, CLEAN_CONFIG);
            success(child(cwd, Map.of(), zone, "input/survey.xml", "--fake-attachments",
                    "-c", "settings.json", "-o", "output", "-n", "3", "-s", "829"));
            Map<String, byte[]> current = tree(cwd.resolve("output"));
            assertTrue(current.containsKey("attachments/z_people.csv"));
            if (first == null) first = current; else identical(first, current);
        }
    }

    @Test void changedSeedChangesInventedRandomValuesAndRerunningFirstSeedRestoresThem() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path config = config(temp, CLEAN_CONFIG), output = temp.resolve("output");
        success(fake(input, output, config, "-s", "17"));
        Map<String, byte[]> first = tree(output);
        success(fake(input, output, config, "-s", "18"));
        assertFalse(java.util.Arrays.equals(first.get("attachments/z_people.csv"),
                Files.readAllBytes(output.resolve("attachments/z_people.csv"))), "50 random names/counts should change");
        success(fake(input, output, config, "-s", "17"));
        identical(first, tree(output));
    }

    @ParameterizedTest @ValueSource(strings = {"directory", "generated-file", "copied-file"})
    void attachmentWriteOrCopyFailureReturnsOneWithNoSuccessOutputOrReport(String obstruction) throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        if (obstruction.equals("directory")) write(output.resolve("attachments"), "not a directory");
        else write(output.resolve("attachments/" + (obstruction.equals("generated-file") ? "a_ages.csv" : "real.csv"))
                .resolve("child"), "nonempty directory where a file must be written");
        Result result = fake(input, output, config(temp, CLEAN_CONFIG));
        failureAt(result, "attachments", "copy", "write", "output", "attachment");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void invalidMediaIsStillAnInputFailureWithTheFlag() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output"), media = write(temp.resolve("not-a-directory"), "x");
        failureAt(fake(input, output, config(temp, CLEAN_CONFIG), "--media", media.toString()),
                "not-a-directory", "media");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void invalidConfigurationIsRejectedBeforeAnySyntheticOutputIsWritten() throws Exception {
        Input input = lookups(temp.resolve("input"));
        Path output = temp.resolve("output");
        failureAt(fake(input, output, config(temp, "{\"submissions\":0}")), "settings.json", "config");
        assertFalse(Files.exists(output));
    }

    @Test void unknownOptionIsDetectedBeforeMissingInputsOrOutputCreationEvenWithTheFlag() {
        Path output = temp.resolve("output");
        Result result = run("--fake-attachments", temp.resolve("absent.xml").toString(),
                "--unknown-amendment2", "-c", temp.resolve("absent.json").toString(), "-o", output.toString());
        failure(result, 2, "--unknown-amendment2");
        assertFalse(Files.exists(output));
    }

    @Test void flagAloneDoesNotSupplyTheRequiredForm() {
        failure(run("--fake-attachments"), 2);
    }

    @Test void flagDoesNotConsumeABooleanValueAsAnOptionalArgument() {
        Path output = temp.resolve("output");
        failure(run("--fake-attachments", "true", temp.resolve("absent.xml").toString(),
                "-o", output.toString()), 2);
        assertFalse(Files.exists(output));
    }

    @Test void helpStillIgnoresTheFlagAndOtherInvalidArgumentsWithoutWritingFiles() {
        Path output = temp.resolve("output");
        Result result = run("--fake-attachments", "--bad-option", "--help", "-o", output.toString());
        success(result);
        usage(result.out());
        assertEquals("", result.err());
        assertFalse(Files.exists(output));
    }
}
