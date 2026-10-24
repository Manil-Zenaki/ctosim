import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Independent spec 11 Amendment 8 process fixture and output/help oracles. */
public final class InjectedProblemsFixture {
    private static final String HEADER = "KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind\n";
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
    private static void write(Path path, String text) throws Exception {
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }
    private static String plain(String text) {
        for (int i = 0; i < 8; i++) text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static void helpCheck(String raw, String area) {
        String t = plain(raw);
        if (area.equals("option")) {
            require(t.matches(".*\\bproblems\\b.*"), "Document the problems option");
            require(t.matches(".*(inject|introduc|deliberate|on purpose).*"), "Explain intentional problems");
            require(t.contains("r(problems)"), "Document r(problems)");
            require(t.contains("r(answerkey)"), "Document r(answerkey)");
        } else if (area.equals("families")) {
            for (String[] alternatives : new String[][] {
                {"resent", "resen", "sent twice", "same submission"},
                {"reinterview", "re-interview", "interviewed again"},
                {"short"}, {"long"}, {"offhours", "off-hours", "outside working hours"},
                {"overlap"}, {"travel", "impossible trip"}}) {
                boolean found = false;
                for (String word : alternatives) found |= t.contains(word);
                require(found, "List problem " + alternatives[0]);
            }
        } else if (area.equals("key")) {
            for (String column : HEADER.trim().split(",")) {
                require(Pattern.compile("(?<![a-z_])" + Pattern.quote(column.toLowerCase(Locale.ROOT)) + "(?![a-z_])").matcher(t).find(),
                        "Document answer-key column " + column);
            }
            require(t.contains("answer-key.csv"), "Name the key file");
            require(t.matches(".*(import delimited|insheet).*answer-key[.]csv.*"), "Show how to load the CSV key");
            require(t.matches(".*merge\\s+(1:1|1:m|m:1)\\s+key\\b.*"), "Show a merge on KEY");
        } else throw new AssertionError("Unknown help area");
    }
    private static void logCheck(String raw, int count, int warnings) {
        // The simulator's own warning/key summary is step 2, not the command's
        // required step-3 summary. The fixture marks the end of its stdout.
        int endOfSimulator = raw.lastIndexOf("A8_SIMULATOR_END");
        if (endOfSimulator >= 0) raw = raw.substring(endOfSimulator + "A8_SIMULATOR_END".length());
        // A run log contains only the command invocation and its output, no echoed assertions.
        List<String> lines = new ArrayList<>();
        for (String line : raw.replace("\r", "").split("\n")) {
            if (!line.startsWith(". ") && !line.startsWith("> ")) lines.add(line.trim());
        }
        if (count < 0) {
            require(!String.join("\n", lines).contains("answer-key.csv"), "Do not announce an absent/stale key");
            return;
        }
        int cursor = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).toLowerCase(Locale.ROOT);
            if (line.contains("warning") && line.matches(".*\\b" + warnings + "\\b.*")) cursor = i;
        }
        require(cursor >= 0, "Missing warning count before problem summary");
        for (int w = 1; w <= warnings; w++) {
            int next = -1;
            for (int i = cursor + 1; i < lines.size(); i++) if (lines.get(i).contains("WARNING_11_" + w + ":")) { next = i; break; }
            require(next >= 0, "Missing or reordered warning " + w);
            cursor = next;
        }
        boolean found = false;
        for (int i = cursor + 1; i < lines.size(); i++) {
            // Wording, color, and one-line versus wrapped path are not specified.
            String summary = String.join("\n", lines.subList(i, Math.min(i + 3, lines.size()))).toLowerCase(Locale.ROOT);
            // A numeric directory component is not the reported submission count.
            String withoutPaths = summary.replaceAll("\"[^\"\\r\\n]*answer-key[.]csv\"", "<key>")
                    .replaceAll("(?:[a-z]:[/\\\\]|//)[^\\r\\n]*?answer-key[.]csv", "<key>")
                    .replace('\n', ' ');
            if (summary.contains("answer-key.csv") && withoutPaths.matches(".*\\b" + count + "\\b.*") &&
                    (withoutPaths.contains("problem") || withoutPaths.contains("inject"))) found = true;
        }
        require(found, "After all warnings, show affected submissions and answer-key path");
    }
    public static int help(String[] args) throws Exception {
        try { helpCheck(read(Paths.get(args[0])), args[1]); return 0; }
        catch (AssertionError e) { write(Paths.get(args[2]), e.getMessage()); return 9; }
    }
    public static int log(String[] args) throws Exception {
        try { logCheck(read(Paths.get(args[0])), Integer.parseInt(args[1]), Integer.parseInt(args[2])); return 0; }
        catch (AssertionError e) { write(Paths.get(args[0] + ".failure.txt"), e.getMessage()); return 9; }
    }
    private static void rejects(Runnable check) {
        boolean rejected = false;
        try { check.run(); } catch (AssertionError e) { rejected = true; }
        require(rejected, "Oracle accepted an invalid sample");
    }
    private static void selfCheck() {
        String warnings = "Warnings: 2\nWARNING_11_1: first\nWARNING_11_2: second\n";
        String summary = "2 submissions have injected problems\nC:/output/answer-key.csv\n";
        logCheck(warnings + summary, 2, 2);
        logCheck("Warnings: 0\n0 problems: answer-key.csv\n", 0, 0);
        logCheck("Warnings: 0\n", -1, 0);
        rejects(() -> logCheck(summary + warnings, 2, 2));
        rejects(() -> logCheck(warnings + summary.replace("2 submissions", "4 submissions"), 2, 2));
        rejects(() -> logCheck(warnings + "4 problems: C:/output/2/answer-key.csv\n", 2, 2));
        rejects(() -> logCheck(warnings + summary.replace("answer-key.csv", "other.csv"), 2, 2));
        rejects(() -> logCheck(warnings, 2, 2));
        rejects(() -> logCheck(summary, -1, 0));
        rejects(() -> logCheck("Warnings: 0\n2 problems: answer-key.csv\nA8_SIMULATOR_END\nWarnings: 0\n", 2, 0));
        logCheck(summary + "A8_SIMULATOR_END\n" + warnings + summary, 2, 2);
        String option = "{cmd:problems} injects problems. {cmd:r(problems)} {cmd:r(answerkey)}";
        helpCheck(option, "option");
        rejects(() -> helpCheck(option.replace("r(answerkey)", ""), "option"));
        String families = "resent reinterview short long offhours overlap travel";
        helpCheck(families, "families");
        for (String word : families.split(" ")) rejects(() -> helpCheck(families.replace(word, ""), "families"));
        String key = HEADER + "{cmd:import delimited using answer-key.csv, clear} {cmd:merge m:1 KEY using data}";
        helpCheck(key, "key");
        for (String column : HEADER.trim().split(",")) {
            if (!column.equals("KEY")) rejects(() -> helpCheck(key.replace(column, ""), "key"));
        }
        rejects(() -> helpCheck(key.replace("merge m:1 KEY", "merge m:m KEY"), "key"));
        System.out.println("Amendment 8 log/help positive and negative oracle checks passed");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-check")) { selfCheck(); return; }
        List<String> valued = new ArrayList<>();
        for (String arg : args) if (!arg.equals("--problems") && !arg.equals("--fake-attachments")) valued.add(arg);
        SimulatorDouble.main(valued.toArray(new String[0]));
        Path work = Paths.get(System.getenv("CTOSIM_TEST_WORK"));
        Files.write(work.resolve("arguments.txt"), Arrays.asList(args), StandardCharsets.UTF_8);
        int count = Integer.parseInt(read(work.resolve("injected-case.txt")).trim());
        String form = null, output = null;
        for (int i = 0; i < valued.size(); i++) {
            String arg = valued.get(i);
            if (arg.startsWith("-")) {
                String value = valued.get(++i);
                if (arg.equals("-o") || arg.equals("--output")) output = value;
            } else if (form == null) form = arg;
        }
        if (output == null) output = "ctosim-out/" + Paths.get(form).getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path out = Paths.get(output), report = out.resolve("run-report.txt");
        if (count >= 0) {
            String text = read(report).replace("Files:\n", "Problems: " + count + " injected, listed in answer-key.csv\nFiles:\n");
            text = text.replace("  run-report.txt\n", "  answer-key.csv\n  run-report.txt\n");
            write(report, text);
            StringBuilder key = new StringBuilder(HEADER);
            // Two start records per target: key rows are NOT the affected-submission count.
            for (int i = 0; i < count; i++) for (String field : Arrays.asList("start_a", "start_b")) {
                key.append("uuid:").append(i == 0 ? "one" : "two").append(",enumerator01,schedule,offhours,ipachecktimeuse,")
                   .append(field).append(",2026-01-05T20:00:00,,,deterministic\n");
            }
            write(out.resolve("answer-key.csv"), key.toString());
        }
        Matcher warningCount = Pattern.compile("(?m)^Warnings: (\\d+)$").matcher(read(report));
        require(warningCount.find(), "Fixture report needs a warning count");
        System.out.println("Warnings: " + warningCount.group(1));
        if (count >= 0) System.out.println(count + " injected problems, listed in answer-key.csv");
        System.out.println("A8_SIMULATOR_END");
    }
}
