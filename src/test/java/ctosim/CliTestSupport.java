package ctosim;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box CLI helpers; no production implementation is inspected or mocked. */
final class CliTestSupport {
    static final Path ROOT = Path.of("").toAbsolutePath();
    static final ObjectMapper JSON = new ObjectMapper();
    static final String TITLE = "CLI enquête";
    static final String WIDE = TITLE + "_WIDE.csv";
    static final String LONG = TITLE + ".csv";

    public static final class WorkspaceTempFactory implements TempDirFactory {
        @Override public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
                throws Exception {
            return Files.createTempDirectory(Files.createDirectories(ROOT.resolve(".codex-runs")), "cli-test-");
        }
    }

    record Result(int status, String out, String err) { }
    record Input(Path form, Path catalog) { }

    static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try (var stdout = new PrintStream(out, true, StandardCharsets.UTF_8);
             var stderr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            int status = assertDoesNotThrow(() -> Cli.run(args, stdout, stderr), "run must report failures as statuses");
            return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        }
    }

    static void success(Result result) {
        assertEquals(0, result.status(), () -> "out=" + result.out() + " err=" + result.err());
    }

    static void failure(Result result, int status, String... problemTokens) {
        assertAll(() -> assertEquals(status, result.status(), result.err()),
                () -> assertEquals("", result.out(), "Failure must not print on the supplied out stream"),
                () -> assertFalse(result.err().isBlank(), "Failure must explain the problem"));
        for (String token : problemTokens) assertTrue(result.err().contains(token), result.err());
        if (status == 2) usage(result.err());
    }

    static void usage(String text) {
        assertTrue(Pattern.compile("(?is)ctosim\\s+FORM").matcher(text).find(), text);
    }

    static void failureAt(Result result, String filename, String... steps) {
        failure(result, 1);
        String message = result.err().toLowerCase(java.util.Locale.ROOT);
        assertTrue(result.err().contains(filename) || Arrays.stream(steps).anyMatch(message::contains),
                "Error must identify the failed file or step: " + result.err());
    }

    public static final class RunReturnProbe {
        public static void main(String[] args) {
            int status = Cli.run(args, System.out, System.err);
            System.out.println("CLI_RUN_RETURNED=" + status);
        }
    }

    static String resource(String name) throws Exception {
        try (var in = CliTestSupport.class.getResourceAsStream("/ctosim/cli/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    static Input fixture(Path directory, String fixture, String filename) throws Exception {
        Path form = write(directory.resolve(filename), resource(fixture + ".xml"));
        Path catalog = write(directory.resolve(filename.substring(0, filename.lastIndexOf('.')) + ".fields.json"),
                resource(fixture + ".fields.json"));
        return new Input(form, catalog);
    }

    static Path config(Path directory, String json) throws Exception {
        return write(directory.resolve("settings.json"), json);
    }

    static Result simulate(Input input, Path output, String... options) {
        var args = new ArrayList<>(List.of(input.form().toString(), "-o", output.toString()));
        args.addAll(List.of(options));
        return run(args.toArray(String[]::new));
    }

    static String report(Path output) throws Exception {
        return Files.readString(output.resolve("run-report.txt"), StandardCharsets.UTF_8);
    }

    static List<String> reportFiles(Path output) throws Exception {
        String text = report(output);
        int start = text.indexOf("Files:\n");
        int end = text.indexOf("Warnings:", start);
        assertTrue(start >= 0 && end > start, text);
        return text.substring(start + 7, end).lines().map(line -> {
            assertTrue(line.startsWith("  "), line);
            return line.substring(2);
        }).toList();
    }

    static List<String> warnings(Path output) throws Exception {
        String text = report(output);
        String section = text.substring(text.indexOf("Warnings:"));
        List<String> lines = section.lines().toList();
        int count = Integer.parseInt(lines.get(0).substring("Warnings: ".length()));
        assertEquals(count, lines.size() - 1, "Warning count must equal report entries");
        return lines.subList(1, lines.size()).stream().map(line -> {
            assertTrue(line.startsWith("  "), line);
            return line.substring(2);
        }).toList();
    }

    static List<List<String>> csv(Path path) throws Exception {
        return SurveyCtoExporterTestSupport.csv(Files.readString(path, StandardCharsets.UTF_8));
    }

    static List<String> column(Path path, String name) throws Exception {
        var rows = csv(path);
        int index = rows.get(0).indexOf(name);
        assertTrue(index >= 0, "Missing column " + name);
        return rows.subList(1, rows.size()).stream().map(row -> row.get(index)).toList();
    }

    static Map<String, byte[]> snapshot(Path directory) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList())
                result.put(path.getFileName().toString(), Files.readAllBytes(path));
        }
        return result;
    }

    static void identical(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((name, bytes) -> assertArrayEquals(bytes, actual.get(name), name));
    }

    static Path python() {
        Path executable = ROOT.resolve(System.getProperty("os.name").startsWith("Windows")
                ? ".venv/Scripts/python.exe" : ".venv/bin/python");
        assertTrue(Files.isRegularFile(executable), "Repository's preinstalled Python is required: " + executable);
        return executable;
    }

    static Map<String, String> converterEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PYTHONPATH", ROOT.resolve("src/test/resources/ctosim/cli/converter").toString());
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        return env;
    }

    static Path workbook(Path directory, String name, String scenario) throws Exception {
        return write(directory.resolve(name), scenario);
    }

    // Child processes provide real working directories and isolated environments/time zones.
    // Their output and all other temporary artifacts stay under .codex-runs.
    static Result child(Path directory, Map<String, String> environment, String zone, String... args)
            throws Exception {
        return childMain(directory, environment, zone, "ctosim.Cli", args);
    }

    static Result childMain(Path directory, Map<String, String> environment, String zone, String mainClass,
                            String... args) throws Exception {
        Files.createDirectories(directory);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        classpath = Arrays.stream(classpath.split(Pattern.quote(java.io.File.pathSeparator)))
                .map(entry -> ROOT.resolve(entry).normalize().toString())
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-Duser.timezone=" + zone, "-cp", classpath, mainClass));
        command.addAll(List.of(args));
        Path stdout = directory.resolve("child-stdout.txt");
        Path stderr = directory.resolve("child-stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().remove("CTOSIM_PYTHON");
        if (environment.containsKey("PATH"))
            builder.environment().keySet().removeIf(key -> key.equalsIgnoreCase("PATH"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "CLI child did not terminate within 45 seconds");
            return new Result(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
