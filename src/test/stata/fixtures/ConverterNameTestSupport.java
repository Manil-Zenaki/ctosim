import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** Starts an isolated batch session; never runs the command's subprocess for it. */
public final class ConverterNameTestSupport {
    public static int launch(String[] args) throws Exception {
        Path logs = Paths.get(args[0], ".codex-runs", "stata-converter-name");
        Files.createDirectories(logs);
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File",
                args[0] + "/src/test/stata/converter-name-launch.ps1",
                "-Repo", args[0], "-Test", args[1], "-Profile", args[2])
                .redirectErrorStream(true)
                .redirectOutput(logs.resolve(args[1] + "-" + args[2] + ".log").toFile()).start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); return 9; }
        return process.exitValue() == 0 ? 0 : 9;
    }
}
