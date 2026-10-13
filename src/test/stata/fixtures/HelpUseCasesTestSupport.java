import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Runs the independent documentation assertions without embedding Python in Stata. */
public final class HelpUseCasesTestSupport {
    public static int check(String[] args) throws Exception {
        Path root = Paths.get(args[0]);
        Path logs = root.resolve(".codex-runs/stata-help-use-cases");
        Files.createDirectories(logs);
        Process process = new ProcessBuilder(
                root.resolve(".venv/Scripts/python.exe").toString(), "-B",
                root.resolve("src/test/stata/fixtures/help_use_cases_checks.py").toString(),
                args[1], args[2])
                .redirectErrorStream(true)
                .redirectOutput(logs.resolve(args[1] + ".log").toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return 9;
        }
        return process.exitValue() == 0 ? 0 : 9;
    }
}
