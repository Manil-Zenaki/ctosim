package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment5Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment5LifecycleTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void effectiveSeedCountAndIdReachTheProblemPlanAndAllExports(boolean flag) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.put("id", "unknown_id");
        settings.set("problems", only("reinterview", 0.25));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        var options = new ArrayList<>(List.of("-c", config.toString(), "-n", "17", "--submissions", "12",
                "-s", "33", "--seed", "981", "--id", "household", "--server", "test-survey"));
        if (flag) options.add("--problems");
        Result result = simulate(input, output, options.toArray(String[]::new));
        success(result);
        settings.put("id", "household").put("submissions", 12).put("seed", 981);
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        assertTrue(oracle.count() > 0);
        keyBytes(output, oracle);
        exports(input, output, oracle, "test-survey");
        assertEquals(12, column(output.resolve(WIDE_FILE), "household").size());
        assertTrue(report(output).contains("Seed: 981\nSubmissions: 12\n"));
        summary(result, output, oracle.count(), 3);
        assertTrue(csv(output.resolve(KEY)).stream().skip(1).allMatch(row -> row.get(5).equals("household")));
    }

    @Test void rerunReplacesExistingKeyThenZeroRatesTruncateItToHeader() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", only("shortDuration", 1));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        write(output.resolve(KEY), "old key with stale trailing bytes\n".repeat(200));
        write(output.resolve("keep.txt"), "untouched");
        success(simulate(input, output, "-c", config.toString()));
        Oracle oracle = oracle(input, input.form().getParent(), parsed(settings));
        keyBytes(output, oracle);
        assertTrue(csv(output.resolve(KEY)).size() > 1);
        settings.set("problems", only(null, 0));
        write(config, settings.toString());
        Result second = simulate(input, output, "-c", config.toString());
        success(second);
        assertEquals(HEADER, Files.readString(output.resolve(KEY)));
        summary(second, output, 0, 3);
        assertEquals("untouched", Files.readString(output.resolve("keep.txt")));
    }

    @Test void laterDisabledRunPreservesOldKeyButRemovesItFromReportAndSummary() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", only("shortDuration", 0.25));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        success(simulate(input, output, "-c", config.toString()));
        byte[] previousKey = Files.readAllBytes(output.resolve(KEY));
        settings.remove("problems");
        write(config, settings.toString());
        Result disabled = simulate(input, output, "-c", config.toString());
        success(disabled);
        assertArrayEquals(previousKey, Files.readAllBytes(output.resolve(KEY)), "Files the run does not write are left alone");
        assertFalse(report(output).contains("Problems:"));
        assertFalse(reportFiles(output).contains(KEY));
        assertFalse(disabled.out().contains(KEY));
        assertEquals(2, disabled.out().lines().count());
        exports(input, output, oracle(input, input.form().getParent(), parsed(settings)), "example");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unwriteableAnswerKeyIsStatusOneAndDoesNotWriteOrReplaceRunReport(boolean previousReport) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.put("submissions", 1);
        settings.set("problems", only(null, 0));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        write(output.resolve(KEY).resolve("keep.txt"), "obstruction");
        if (previousReport) write(output.resolve("run-report.txt"), "previous completed report");
        Result result = simulate(input, output, "-c", config.toString());
        failureAt(result, KEY, "answer key", "answer-key");
        assertEquals("obstruction", Files.readString(output.resolve(KEY).resolve("keep.txt")));
        if (previousReport) assertEquals("previous completed report", report(output));
        else assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void disabledRunDoesNotTryToWriteAnObstructedAnswerKeyPath() throws Exception {
        Input input = input(temp.resolve("source"));
        Path output = temp.resolve("output");
        write(output.resolve(KEY).resolve("keep.txt"), "obstruction");
        success(simulate(input, output, "-n", "1"));
        assertEquals("obstruction", Files.readString(output.resolve(KEY).resolve("keep.txt")));
        assertFalse(reportFiles(output).contains(KEY));
    }

    @Test void convertedFormWritesKeyInExportOrderAndRetainsConverterWarnings() throws Exception {
        Path source = workbook(temp.resolve("source"), "enquête.xlsx", JSON.writeValueAsString(
                Map.of("fixture", "amendment5/problems", "warnings", List.of("conversion warning"))));
        var settings = settings();
        settings.set("problems", only("shortDuration", 0.25));
        Path config = config(temp, settings.toString()), output = temp.resolve("output");
        Result result = child(temp.resolve("child"), converterEnvironment(), "UTC", source.toString(),
                "-c", config.toString(), "-o", output.toString(), "--python", python().toString(), "--problems");
        success(result);
        Input converted = new Input(output.resolve("form.xml"), output.resolve("form.fields.json"));
        Oracle oracle = oracle(converted, source.getParent(), parsed(settings));
        keyBytes(output, oracle);
        exports(converted, output, oracle, "example");
        var expected = expectedFiles(converted, oracle);
        expected.addAll(List.of("form.xml", "form.fields.json", "run-report.txt"));
        assertEquals(expected, reportFiles(output));
        var warningList = new ArrayList<>(List.of("conversion warning"));
        warningList.addAll(CliAmendment4Support.expectedWarnings(new CliAmendment4Support.Expected(
                oracle.form(), oracle.plan(), oracle.submissions())));
        assertEquals(warningList, warnings(output));
        assertTrue(report(output).contains("Source: enquête.xlsx\n"));
        summary(result, output, oracle.count(), 3);
    }

    @Test void answerKeyAndAllExportsAreIdenticalAcrossWorkingDirectoriesAndTimeZones() throws Exception {
        var settings = settings();
        settings.set("problems", only("longDuration", 0.25));
        var outputs = new ArrayList<Path>();
        for (String zone : List.of("Pacific/Kiritimati", "America/Los_Angeles")) {
            Path directory = temp.resolve("run" + outputs.size());
            Input input = input(directory.resolve("source"));
            Path config = config(directory, settings.toString()), output = directory.resolve("output");
            Result result = child(directory.resolve("work"), Map.of(), zone, input.form().toString(),
                    "-c", config.toString(), "-o", output.toString());
            success(result);
            assertTrue(Files.size(output.resolve(KEY)) > HEADER.length());
            outputs.add(output);
        }
        identical(snapshot(outputs.get(0)), snapshot(outputs.get(1)));
    }

    @Test void seedChangesReachInjectionWhileRepeatedCallsWithSameSeedAreIdentical() throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings();
        settings.set("problems", only("shortDuration", 0.25));
        Path config = config(temp, settings.toString());
        var keys = new java.util.HashSet<String>();
        for (long seed : List.of(9L, 81L, 731991L)) {
            Path output = temp.resolve("seed" + seed), rerun = temp.resolve("again" + seed);
            success(simulate(input, output, "-c", config.toString(), "-s", Long.toString(seed)));
            success(simulate(input, rerun, "-c", config.toString(), "-s", Long.toString(seed)));
            Oracle oracle = oracle(input, input.form().getParent(), parsed(settings).withSeed(seed));
            keyBytes(output, oracle);
            identical(snapshot(output), snapshot(rerun));
            keys.add(Files.readString(output.resolve(KEY)));
        }
        assertTrue(keys.size() > 1, "Different seeds must not all reuse one answer key");
    }
}
