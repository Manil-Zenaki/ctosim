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

class CliConverterTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    private Result convert(Path source, Path output, Map<String, String> environment, String... options)
            throws Exception {
        List<String> args = new ArrayList<>(List.of(source.toString(), "-o", output.toString(), "-n", "2"));
        args.addAll(List.of(options));
        return child(temp.resolve("working"), environment, "UTC", args.toArray(String[]::new));
    }

    @ParameterizedTest @ValueSource(strings = {".xlsx", ".XLSX", ".XlSx"})
    void xlsxRunsExactConverterCommandAndListsConversionFilesAfterExports(String suffix) throws Exception {
        Path source = workbook(temp.resolve("source with spaces é"), "survey.v2" + suffix,
                "{\"warnings\":[\"first converter warning\",\"second converter warning\"]}");
        Path output = temp.resolve("new parents/output space é");
        Result result = convert(source, output, converterEnvironment(), "--python", python().toString());
        success(result);
        var invocation = JSON.readTree(Files.readString(source.getParent().resolve("converter-invocation.json")));
        assertEquals(List.of(source.toString(), "-o", output.resolve("form.xml").toString()),
                JSON.convertValue(invocation.get("args"), List.class));
        assertTrue(Files.isRegularFile(output.resolve("form.xml")));
        assertTrue(Files.isRegularFile(output.resolve("form.fields.json")));
        assertEquals(3, csv(output.resolve(WIDE)).size());
        assertEquals(List.of(WIDE, LONG, "form.xml", "form.fields.json", "run-report.txt"), reportFiles(output));
        assertEquals(List.of("first converter warning", "second converter warning"), warnings(output));
        assertTrue(report(output).contains("Source: " + source.getFileName() + "\n"));
        assertTrue(result.out().lines().toList().get(1).contains("2"), result.out());
    }

    @Test void converterWarningsComeBeforeAllLoaderAndWalkerWarnings() throws Exception {
        Path source = workbook(temp.resolve("source"), "survey.xlsx",
                "{\"fixture\":\"warnings\",\"warnings\":[\"z converter warning\",\"a converter warning\",\"z converter warning\"]}");
        Path config = config(temp, CliWarningsTest.warningConfig());
        Path output = temp.resolve("out");
        success(convert(source, output, converterEnvironment(), "--python", python().toString(), "-c", config.toString()));
        List<String> expected = new ArrayList<>(List.of("z converter warning", "a converter warning", "z converter warning"));
        expected.addAll(CliWarningsTest.expectedWarnings(new Input(output.resolve("form.xml"), output.resolve("form.fields.json")),
                source.getParent(), config).stream().map(CliWarningsTest::singleLine).toList());
        assertEquals(expected, warnings(output));
    }

    @Test void xlsxMediaDefaultsToOriginalWorkbookDirectoryNotConvertedXmlDirectory() throws Exception {
        Path source = workbook(temp.resolve("source"), "survey.xlsx", "{\"fixture\":\"warnings\"}");
        write(source.getParent().resolve("a_people.csv"), "id,name\n1,Original directory\n");
        Path output = temp.resolve("out");
        write(output.resolve("a_people.csv"), "id,name\n1,Wrong directory\n");
        success(convert(source, output, converterEnvironment(), "--python", python().toString()));
        assertEquals(List.of("Original directory", "Original directory"), column(output.resolve("Warnings CLI_WIDE.csv"), "alookup"));
    }

    @Test void nonzeroConverterExitIncludesItsErrorOutputAndDoesNotWriteReport() throws Exception {
        Path source = workbook(temp.resolve("source"), "failed.xlsx",
                "{\"exit\":7,\"warnings\":[\"diagnostic one\",\"diagnostic two\"]}");
        Path output = temp.resolve("out");
        Result result = convert(source, output, converterEnvironment(), "--python", python().toString());
        failure(result, 1, "controlled conversion failure for failed.xlsx", "diagnostic one", "diagnostic two");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void missingPythonExecutableIsNamedInAnInputError() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Path output = temp.resolve("out");
        String executable = temp.resolve("does-not-exist-python").toString();
        failure(run(source.toString(), "-o", output.toString(), "--python", executable), 1, "does-not-exist-python");
        assertFalse(Files.exists(output.resolve("run-report.txt")));
    }

    @Test void nonemptyPythonEnvironmentVariableSelectsExecutable() throws Exception {
        Path source = workbook(temp.resolve("source"), "survey.xlsx", "{}");
        Map<String, String> environment = converterEnvironment();
        environment.put("CTOSIM_PYTHON", python().toString());
        success(convert(source, temp.resolve("out"), environment));
        var invocation = JSON.readTree(Files.readString(source.getParent().resolve("converter-invocation.json")));
        assertTrue(Files.isSameFile(python(), Path.of(invocation.get("python").asText())));
    }

    @Test void invalidPythonEnvironmentVariableIsUsedAndNamed() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Map<String, String> environment = converterEnvironment();
        environment.put("CTOSIM_PYTHON", temp.resolve("missing-env-python").toString());
        failure(convert(source, temp.resolve("out"), environment), 1, "missing-env-python");
    }

    @Test void explicitPythonOverridesEnvironmentAndRepeatedPythonKeepsLastExecutable() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Map<String, String> environment = converterEnvironment();
        environment.put("CTOSIM_PYTHON", temp.resolve("missing-env-python").toString());
        success(convert(source, temp.resolve("out"), environment, "--python", temp.resolve("missing-option-python").toString(),
                "--python", python().toString()));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unsetOrEmptyPythonEnvironmentFallsBackToPythonOnPath(boolean empty) throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Map<String, String> environment = converterEnvironment();
        environment.put("PATH", python().getParent() + java.io.File.pathSeparator + System.getenv("PATH"));
        if (empty) environment.put("CTOSIM_PYTHON", "");
        success(convert(source, temp.resolve("out"), environment));
        var invocation = JSON.readTree(Files.readString(temp.resolve("converter-invocation.json")));
        assertTrue(Files.isSameFile(python(), Path.of(invocation.get("python").asText())));
    }

    @Test void xmlInputNeverStartsPythonEvenWithAnInvalidExecutable() throws Exception {
        Input input = fixture(temp, "simple", "survey.xml");
        success(simulate(input, temp.resolve("out"), "-n", "1", "--python", temp.resolve("missing-python").toString()));
    }

    @Test void usageIsValidatedBeforeConverterStartsOrOutputIsCreated() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Path output = temp.resolve("out");
        failure(convert(source, output, converterEnvironment(), "--python", python().toString(), "--unknown"), 2, "--unknown");
        assertFalse(Files.exists(temp.resolve("converter-invocation.json")));
        assertFalse(Files.exists(output));
    }

    @Test void invalidConfigurationStopsBeforeConversionAndOutputCreation() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Path config = config(temp, "{\"submissions\":0}");
        Path output = temp.resolve("out");
        failureAt(convert(source, output, converterEnvironment(), "--python", python().toString(),
                "-c", config.toString()), "settings.json", "config");
        assertFalse(Files.exists(temp.resolve("converter-invocation.json")));
        assertFalse(Files.exists(output));
    }

    @Test void xlsxOutputsAreByteIdenticalAcrossWorkingDirectoriesAndTimeZones() throws Exception {
        Path source = workbook(temp.resolve("source"), "survey.xlsx", "{\"warnings\":[\"stable warning\"]}");
        for (String dir : List.of("first", "second")) {
            Path cwd = temp.resolve(dir);
            success(child(cwd, converterEnvironment(), dir.equals("first") ? "UTC" : "America/Los_Angeles",
                    source.toString(), "-o", "out", "-n", "2", "--python", python().toString()));
        }
        identical(snapshot(temp.resolve("first/out")), snapshot(temp.resolve("second/out")));
    }

    @Test void existingConvertedFilesAreReplacedAndUnrelatedFilesSurvive() throws Exception {
        Path source = workbook(temp, "survey.xlsx", "{}");
        Path output = temp.resolve("out");
        write(output.resolve("form.xml"), "stale form");
        write(output.resolve("form.fields.json"), "stale catalog");
        write(output.resolve("keep.txt"), "untouched");
        success(convert(source, output, converterEnvironment(), "--python", python().toString()));
        assertEquals(resource("simple.xml"), Files.readString(output.resolve("form.xml")));
        assertEquals(resource("simple.fields.json"), Files.readString(output.resolve("form.fields.json")));
        assertEquals("untouched", Files.readString(output.resolve("keep.txt")));
    }
}
