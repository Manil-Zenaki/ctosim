import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Java-8-compatible test helper; no dependency on simulator or Stata internals. */
public final class StataTestSupport {
    public static int launch(String[] args) throws Exception {
        Path log = Paths.get(args[0], ".codex-runs", "stata-command", args[1] + ".launch.log");
        Process p = new ProcessBuilder("powershell", "-WindowStyle", "Hidden", "-NoProfile",
                "-ExecutionPolicy", "Bypass", "-File", args[0] + "/src/test/stata/launch.ps1",
                "-Repo", args[0], "-Test", args[1], "-Profile", args[2])
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!p.waitFor(210, TimeUnit.SECONDS)) { p.destroyForcibly(); return 9; }
        return 0; // launch.do reports the child's exact failure log and asserts its sentinel.
    }
    public static int equalFiles(String[] args) throws Exception {
        return Arrays.equals(Files.readAllBytes(Paths.get(args[0])), Files.readAllBytes(Paths.get(args[1]))) ? 0 : 9;
    }
    public static int samePaths(String[] args) throws Exception {
        for (int i = 0; i < args.length; i += 2) {
            if (!Files.isSameFile(Paths.get(args[i]), Paths.get(args[i + 1]))) return 9;
        }
        return 0;
    }
    public static int equalDirectories(String[] args) throws Exception {
        Path a = Paths.get(args[0]), b = Paths.get(args[1]);
        List<String> left, right;
        try (java.util.stream.Stream<Path> s = Files.walk(a)) {
            left = s.filter(Files::isRegularFile).map(p -> a.relativize(p).toString()).sorted().collect(Collectors.toList());
        }
        try (java.util.stream.Stream<Path> s = Files.walk(b)) {
            right = s.filter(Files::isRegularFile).map(p -> b.relativize(p).toString()).sorted().collect(Collectors.toList());
        }
        if (left.isEmpty() || !left.equals(right)) return 9;
        for (String f : left) if (equalFiles(new String[] { a.resolve(f).toString(), b.resolve(f).toString() }) != 0) return 9;
        return 0;
    }
    public static int warningsInLog(String[] args) throws Exception {
        List<String> report = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        String log = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8).replace("\r", "");
        int header = -1;
        for (int i = 0; i < report.size(); i++) if (report.get(i).startsWith("Warnings: ")) header = i;
        if (header < 0) return 9;
        int count = Integer.parseInt(report.get(header).substring(10));
        List<String> lines = Arrays.asList(log.split("\n"));
        int cursor = -1;
        // Wording is unspecified: require the count and word warning on a preceding line.
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase(Locale.ROOT);
            if (line.contains("warning") && line.matches(".*\\b" + count + "\\b.*")) { cursor = i; break; }
        }
        if (cursor < 0) return 9;
        for (int i = 0; i < count; i++) {
            String warning = report.get(header + 1 + i).trim();
            int next = -1, occurrences = 0;
            for (int j = 0; j < lines.size(); j++) {
                if (lines.get(j).contains(warning)) { occurrences++; if (j > cursor && next < 0) next = j; }
            }
            if (next < 0 || occurrences != 1) return 9;
            cursor = next;
        }
        return 0;
    }
    public static int missingDiagnostic(String[] args) throws Exception {
        String text = Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8).stream()
                .filter(s -> !s.startsWith(". ")).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
        String subject = args[1].equals("simulator") ? ".*(simulator|ctosim[.]jar|ctosim_jar).*"
                : args[1].equals("python") ? ".*python.*" : ".*(converter|ctosim_converter).*";
        // Wording is free; recognize alternative ways to identify/provide a dependency.
        boolean remedy = text.matches(".*(ctosim_jar|ctosim_converter|ado.path|install|provide|set |place |copy |put |point |configure).*" );
        return text.matches(subject) && remedy ? 0 : 9;
    }
}
