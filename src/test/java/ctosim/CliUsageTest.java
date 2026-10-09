package ctosim;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CliUsageTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(strings = {"-h", "--help"})
    void helpPrintsUsageOnOutAndSucceedsWithoutAForm(String flag) {
        Result result = run(flag);
        success(result);
        usage(result.out());
        assertEquals("", result.err());
    }

    @ParameterizedTest @ValueSource(strings = {"-h", "--help"})
    void helpIgnoresAllOtherArgumentsAndDoesNotTouchFiles(String flag) throws Exception {
        Path output = temp.resolve("untouched");
        Result result = run("--unknown", "one.bad", "two.bad", "-n", "nope", "-c",
                temp.resolve("missing.json").toString(), "-o", output.toString(), flag, "--seed");
        success(result);
        usage(result.out());
        assertEquals("", result.err());
        assertFalse(Files.exists(output));
    }

    @Test void noArgumentsIsAUsageProblem() {
        failure(run(), 2);
    }

    @Test void optionsWithoutAFormAreAUsageProblem() {
        Path output = temp.resolve("out");
        failure(run("-n", "2", "-o", output.toString()), 2);
        assertFalse(Files.exists(output));
    }

    @Test void twoFormsAreRejectedBeforeEitherIsRead() {
        failure(run("absent-first.xml", "absent-second.xml"), 2);
    }

    @ParameterizedTest @ValueSource(strings = {"--unknown", "-x", "--submisions"})
    void unknownOptionIsNamedAndFollowedByUsage(String option) {
        Result result = run("nonexistent.xml", option);
        failure(result, 2, option);
        int problem = result.err().indexOf(option);
        int usage = result.err().toLowerCase(java.util.Locale.ROOT).indexOf("ctosim");
        assertTrue(problem < usage, result.err());
    }

    @ParameterizedTest @ValueSource(strings = {"-c", "--config", "-n", "--submissions", "-s", "--seed",
            "-o", "--output", "-m", "--media", "--server", "--python"})
    void everyValuedOptionRequiresAFollowingArgument(String option) {
        failure(run("nonexistent.xml", option), 2, option);
    }

    static Stream<Arguments> invalidNumbers() {
        return Stream.concat(
                Stream.of("0", "-1", "1000001", "2147483648", "999999999999999999999999", "1.0", "1e2", "abc", "")
                        .flatMap(value -> Stream.of("-n", "--submissions").map(option -> Arguments.of(option, value))),
                Stream.of("9223372036854775808", "-9223372036854775809", "1.0", "1e2", "abc", "")
                        .flatMap(value -> Stream.of("-s", "--seed").map(option -> Arguments.of(option, value))));
    }

    @ParameterizedTest @MethodSource("invalidNumbers")
    void invalidNumericOptionsAreUsageErrorsBeforeAnyIo(String option, String value) {
        Path output = temp.resolve("not-created");
        Result result = run("nonexistent.xml", "-c", temp.resolve("missing.json").toString(),
                "-o", output.toString(), option, value);
        failure(result, 2);
        assertTrue(result.err().contains(option)
                || result.err().toLowerCase(java.util.Locale.ROOT).contains(option.contains("s") ? "seed" : "submissions"), result.err());
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest @ValueSource(strings = {"survey.xls", "survey.csv", "survey", "survey.xml.bak", "survey.xlsx.tmp", ""})
    void unsupportedFormSuffixIsAUsageErrorEvenIfTheFileDoesNotExist(String name) {
        failure(run(name), 2);
    }

    @ParameterizedTest @ValueSource(strings = {"1", "1000000"})
    void submissionLimitsAreAcceptedByArgumentValidation(String count) {
        // Missing config deliberately stops the run before one million submissions are walked.
        Path config = temp.resolve("missing-config.json");
        failure(run("missing.xml", "-n", count, "-c", config.toString()), 1, config.getFileName().toString());
    }

    @Test void usageFailurePreservesAnExistingOutputDirectoryByteForByte() throws Exception {
        Path output = Files.createDirectories(temp.resolve("out"));
        write(output.resolve("run-report.txt"), "existing report\n");
        write(output.resolve("keep.bin"), "sentinel");
        var before = snapshot(output);
        failure(run("missing.xml", "-o", output.toString(), "--unknown"), 2);
        identical(before, snapshot(output));
    }

    @Test void nullArgumentArrayThrowsNullPointerException() {
        try (var stream = new PrintStream(OutputStream.nullOutputStream())) {
            assertThrows(NullPointerException.class, () -> Cli.run(null, stream, stream));
        }
    }

    @Test void runReturnsAStatusToItsCallerWithoutCallingSystemExit() throws Exception {
        Result result = childMain(temp, java.util.Map.of(), "UTC", "ctosim.CliTestSupport$RunReturnProbe");
        assertEquals(0, result.status(), result.err());
        assertEquals("CLI_RUN_RETURNED=2", result.out().strip());
        usage(result.err());
    }

    @Test void nullOutputStreamThrowsNullPointerException() {
        try (var stream = new PrintStream(OutputStream.nullOutputStream())) {
            assertThrows(NullPointerException.class, () -> Cli.run(new String[]{"--help"}, null, stream));
        }
    }

    @Test void nullErrorStreamThrowsNullPointerException() {
        try (var stream = new PrintStream(OutputStream.nullOutputStream())) {
            assertThrows(NullPointerException.class, () -> Cli.run(new String[]{}, stream, null));
        }
    }
}
