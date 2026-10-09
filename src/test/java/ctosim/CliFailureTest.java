package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CliFailureTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @Test void missingFormIsAnInputError() {
        Path output = temp.resolve("out");
        failureAt(run(temp.resolve("absent.xml").toString(), "-o", output.toString()), "absent.xml", "form", "input");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void missingConfigIsReportedBeforeFormOrOutputIsProcessed() {
        Path output = temp.resolve("out");
        failure(run(temp.resolve("absent.xml").toString(), "-c", temp.resolve("absent-config.json").toString(),
                "-o", output.toString()), 1, "absent-config.json");
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest @ValueSource(strings = {"{", "[]", "{\"submissions\":0}", "{\"seed\":\"bad\"}",
            "{\"unknownKey\":true}", "{\"timeZone\":\"not/a/timezone\"}"})
    void invalidConfigurationRemainsAnInputErrorEvenWithValidOverrides(String json) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path config = config(temp, json);
        Path output = temp.resolve("out");
        failure(simulate(input, output, "-c", config.toString(), "-n", "1", "-s", "5"), 1, "settings.json");
        assertFalse(Files.exists(output));
    }

    @Test void directoryCannotBeReadAsConfiguration() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path config = Files.createDirectories(temp.resolve("config-directory"));
        failure(simulate(input, temp.resolve("out"), "-c", config.toString()), 1, "config-directory");
    }

    @Test void missingXmlCatalogIsAnInputErrorAndDoesNotProduceAReport() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Files.delete(input.catalog());
        Path output = temp.resolve("out");
        failureAt(simulate(input, output, "-n", "1"), "survey.fields.json", "catalog");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @ParameterizedTest @ValueSource(strings = {"{", "[]", "{}", "{\"form_id\":\"cli_fixture\"}",
            "{\"form_id\":\"cli_fixture\",\"fields\":[{\"name\":\"answer\"}]}"})
    void invalidCatalogIsAnInputError(String json) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        write(input.catalog(), json);
        Path output = temp.resolve("out");
        failureAt(simulate(input, output, "-n", "1"), "survey.fields.json", "catalog");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @ParameterizedTest @ValueSource(strings = {"not xml", "<broken>", "<root/>"})
    void unloadableFormIsAnInputError(String xml) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        write(input.form(), xml);
        Path output = temp.resolve("out");
        failureAt(simulate(input, output, "-n", "1"), "survey.xml", "form", "load");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nonexistentOrRegularFileMediaDirectoryIsAnInputError(boolean regularFile) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path media = temp.resolve("invalid-media");
        if (regularFile) write(media, "file,not,directory");
        Path output = temp.resolve("out");
        failureAt(simulate(input, output, "-n", "1", "-m", media.toString()), "invalid-media", "media", "load");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void outputDirectoryOrItsParentBeingAFileIsAnOutputError(boolean parent) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path blocker = write(temp.resolve("blocked-output"), "unchanged");
        Path output = parent ? blocker.resolve("child") : blocker;
        failureAt(simulate(input, output, "-n", "1"), "blocked-output", "output", "directory");
        assertEquals("unchanged", Files.readString(blocker));
    }

    @ParameterizedTest @ValueSource(strings = {"CLI enquête_WIDE.csv", "CLI enquête.csv", "run-report.txt"})
    void unwritableExportOrReportIsAnOutputError(String blockedName) throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path output = temp.resolve("out");
        Path blocker = Files.createDirectories(output.resolve(blockedName));
        write(blocker.resolve("keep"), "sentinel");
        failureAt(simulate(input, output, "-n", "1"), blockedName,
                blockedName.equals("run-report.txt") ? "report" : "export", "writ");
        assertEquals("sentinel", Files.readString(blocker.resolve("keep")));
        assertFalse(Files.isRegularFile(output.resolve("run-report.txt")));
    }

    @Test void exporterFailureIsCaughtRatherThanEscapingRun() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        // Spec 08 requires instanceID in each submitted instance.
        write(input.form(), resource("simple.xml")
                .replace("<meta><instanceID/></meta>", "")
                .replace("<bind nodeset=\"/data/meta/instanceID\" type=\"string\" readonly=\"true()\" jr:preload=\"uid\"/>", ""));
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-n", "1");
        failure(result, 1);
        assertTrue(result.err().toLowerCase(java.util.Locale.ROOT).matches("(?s).*(export|submission|instance).*"), result.err());
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void failedRerunDoesNotRewriteAnEarlierSuccessfulReport() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "1"));
        byte[] original = Files.readAllBytes(output.resolve("run-report.txt"));
        write(input.catalog(), "not JSON");
        failure(simulate(input, output, "-n", "1"), 1);
        assertArrayEquals(original, Files.readAllBytes(output.resolve("run-report.txt")));
    }

    @Test void walkNavigationLimitFailureReturnsOneWithoutAReport() throws Exception {
        Input input = fixture(temp, "runaway", "runaway.xml");
        Path output = temp.resolve("out");
        Result result = child(temp.resolve("working"), Map.of(), "UTC", input.form().toString(),
                "-o", output.toString(), "-n", "1");
        failure(result, 1);
        assertTrue(result.err().toLowerCase(java.util.Locale.ROOT).matches("(?s).*(walk|submission).*"), result.err());
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void mainReturnsUsageFailureAsProcessExitStatus() throws Exception {
        failure(child(temp, Map.of(), "UTC", "--bad-option"), 2, "--bad-option");
    }

    @Test void mainReturnsInputFailureAsProcessExitStatus() throws Exception {
        failure(child(temp, Map.of(), "UTC", "missing.xml", "-n", "1"), 1, "missing.xml");
    }

    @Test void mainReturnsHelpAsProcessExitStatusZero() throws Exception {
        Result result = child(temp, Map.of(), "UTC", "--help");
        success(result);
        usage(result.out());
        assertEquals("", result.err());
    }
}
