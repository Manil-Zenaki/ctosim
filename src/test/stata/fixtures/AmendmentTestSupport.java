import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Independent batch bridge, compatible with Stata's Java 11 and newer. */
public final class AmendmentTestSupport {
    public static int launch(String[] args) throws Exception {
        Path root = Paths.get(args[0], ".codex-runs", "stata-amendment");
        Files.createDirectories(root);
        Process process = new ProcessBuilder("powershell", "-WindowStyle", "Hidden", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-File",
                args[0] + "/src/test/stata/amendment-launch.ps1",
                "-Repo", args[0], "-Test", args[1], "-Profile", args[2])
                .redirectErrorStream(true).redirectOutput(root.resolve(args[1] + "-" + args[2] + ".log").toFile()).start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) { process.destroyForcibly(); return 9; }
        return process.exitValue() == 0 ? 0 : 9;
    }

    public static int ownVersion(String[] args) throws Exception {
        String version = System.getProperty("java.specification.version");
        if (version.startsWith("1.")) version = version.substring(2);
        Files.write(Paths.get(args[0]), Arrays.asList(version), StandardCharsets.UTF_8);
        return 0;
    }

    public static int diagnostic(String[] args) throws Exception {
        StringBuilder output = new StringBuilder();
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8))
            if (!line.startsWith(". ") && !line.startsWith("> ")) output.append(line).append(' ');
        String text = output.toString().toLowerCase(Locale.ROOT);
        boolean minimum = text.matches(".*java.{0,100}17.*")
                && text.matches(".*(newer|later|higher|least|minimum|17\\+|>= *17).*" );
        boolean own = text.contains("stata") && text.matches(".*\\b" + args[1] + "([.][0-9]+)*\\b.*");
        boolean remedy = text.contains("ctosim_java") ||
                (text.contains("temurin") && text.matches(".*(install|download|provide).*"));
        return minimum && own && remedy ? 0 : 9;
    }
}
