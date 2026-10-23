package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment5ActivationTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void valuelessFlagBeforeOrAfterFormEnablesDefaultRatesAndPreservesOtherSettings(boolean before) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        Path config = config(temp, settings.toString());
        byte[] original = Files.readAllBytes(config);
        Path output = temp.resolve("output");
        var args = new ArrayList<String>();
        if (before) args.add("--problems");
        args.add(input.form().toString());
        if (!before) args.add("--problems");
        args.addAll(List.of("-c", config.toString(), "-o", output.toString()));
        Result result = run(args.toArray(String[]::new));
        success(result);
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings).withProblems(ProblemRates.defaults()));
        assertTrue(oracle.count() > 0, "Default rates must exercise actual injection");
        keyBytes(output, oracle);
        exports(input, output, oracle, "example");
        summary(result, output, oracle.count(), 3);
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test void flagWithoutConfigurationUsesDefaultsWithEffectiveCountAndSeed() throws Exception {
        Input input = input(temp.resolve("source"));
        Path output = temp.resolve("output");
        Result result = simulate(input, output, "--problems", "-n", "12", "-s", "-731991");
        success(result);
        Oracle oracle = oracle(input, input.form().getParent(), SimulationConfig.defaults()
                .withProblems(ProblemRates.defaults()).withSubmissions(12).withSeed(-731991));
        keyBytes(output, oracle);
        exports(input, output, oracle, "example");
        summary(result, output, oracle.count(), 3);
        assertTrue(report(output).contains("Configuration: defaults\nSeed: -731991\nSubmissions: 12\n"));
    }

    @ParameterizedTest @ValueSource(strings = {"resent", "reinterview", "shortDuration", "longDuration", "offHours", "overlap", "travel"})
    void configuredRatesAloneEnableInjectionAndFlagDoesNotReplaceThem(String rate) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", only(rate, 0.25));
        Path config = config(temp, settings.toString());
        Path configured = temp.resolve("configured"), flagged = temp.resolve("flagged");
        success(simulate(input, configured, "-c", config.toString()));
        success(simulate(input, flagged, "-c", config.toString(), "--problems"));
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        assertTrue(oracle.count() > 0, "Fixture must inject " + rate);
        keyBytes(configured, oracle);
        exports(input, configured, oracle, "example");
        identical(snapshot(configured), snapshot(flagged));
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"travel\":0}"})
    void emptyOrPartialProblemsObjectEnablesDefaultsForUnspecifiedRates(String rates) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", JSON.readTree(rates));
        Path config = config(temp, settings.toString());
        Path output = temp.resolve("output");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        assertTrue(oracle.count() > 0);
        keyBytes(output, oracle);
        summary(result, output, oracle.count(), 3);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void allZeroRatesStillWriteHeaderAndZeroSummariesEvenWithFlag(boolean flag) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", only(null, 0));
        Path config = config(temp, settings.toString());
        Path output = temp.resolve("output");
        var options = new ArrayList<>(List.of("-c", config.toString()));
        if (flag) options.add("--problems");
        Result result = simulate(input, output, options.toArray(String[]::new));
        success(result);
        assertEquals(HEADER, Files.readString(output.resolve(KEY)), "Empty key has header only, no final LF or BOM");
        summary(result, output, 0, 3);
        Oracle clean = oracle(input, input.form().getParent(), parsed(settings).withProblems(null));
        exports(input, output, clean, "example");
        assertTrue(reportFiles(output).contains(KEY));
    }

    @Test void absentFlagAndAbsentSettingLeaveCleanExportsAndNoProblemArtifacts() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        Path config = config(temp, settings.toString());
        Path output = temp.resolve("output");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        assertFalse(Files.exists(output.resolve(KEY)));
        assertFalse(reportFiles(output).contains(KEY));
        assertFalse(report(output).contains("Problems:"));
        assertFalse(result.out().contains(KEY));
        assertEquals(2, result.out().lines().count());
        exports(input, output, oracle(input, input.form().getParent(), parsed(settings)), "example");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void duplicateFlagIsUsageErrorBeforeReadingInputsOrTouchingOutputs(boolean separated) throws Exception {
        Path output = Files.createDirectories(temp.resolve("output"));
        write(output.resolve(KEY), "keep answer key");
        write(output.resolve("run-report.txt"), "keep report");
        var before = snapshot(output);
        var args = new ArrayList<>(List.of("--problems"));
        if (!separated) args.add("--problems");
        args.addAll(List.of(temp.resolve("missing.xml").toString(), "-c", temp.resolve("missing.json").toString(),
                "-o", output.toString()));
        if (separated) args.add("--problems");
        failure(run(args.toArray(String[]::new)), 2, "problems");
        identical(before, snapshot(output));
    }

    @Test void duplicateFlagDoesNotCreateAMissingOutputDirectory() {
        Path output = temp.resolve("missing/output");
        failure(run("--problems", "--problems", temp.resolve("missing.xml").toString(), "-o", output.toString()),
                2, "problems");
        assertFalse(Files.exists(temp.resolve("missing")));
    }

    @Test void flagTakesNoValueAndDoesNotSatisfyTheRequiredForm() {
        failure(run("--problems"), 2);
    }

    @Test void flagDoesNotConsumeAnExtraPositionalArgument() {
        failure(run("--problems", "true", temp.resolve("missing.xml").toString()), 2);
    }

    @ParameterizedTest @ValueSource(strings = {"-h", "--help"})
    void helpOverridesDuplicateFlagAndOtherUsageErrors(String help) {
        Result result = run("--problems", "--problems", "--unknown", help);
        success(result);
        usage(result.out());
        assertEquals("", result.err());
    }

    @ParameterizedTest @ValueSource(strings = {"null", "false", "{\"shortDuration\":-0.01}", "{\"travel\":1.01}", "{\"unknown\":0}"})
    void flagDoesNotMaskInvalidProblemConfiguration(String problems) throws Exception {
        Input input = input(temp.resolve("source"));
        Path config = config(temp, "{\"problems\":" + problems + "}");
        Path output = temp.resolve("output");
        failureAt(simulate(input, output, "-c", config.toString(), "--problems"), "settings.json", "config");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
        assertFalse(Files.exists(output.resolve(KEY)));
    }
}
