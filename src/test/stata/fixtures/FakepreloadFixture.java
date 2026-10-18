import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Independent process fixture for spec 11 Amendment 5. No production dependencies. */
public final class FakepreloadFixture {
    private static void write(Path p, String s) throws Exception {
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        // Spec 10's process boundary uses UTF-8, including on Windows.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8.name()));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8.name()));
        if (args.length == 2 && args[0].equals("--check-oracle")) {
            selfCheck(Paths.get(args[1]));
            return;
        }
        Path work = Paths.get(System.getenv("CTOSIM_TEST_WORK"));
        write(work.resolve("invoked.txt"), "invoked\n");
        write(work.resolve("arguments.txt"), String.join("\n", args) + "\n");
        String[] scenario = read(work.resolve("fakepreload-case.txt")).trim().split("\\s+");
        String mode = scenario[0];
        int warnings = Integer.parseInt(scenario[1]);
        int unsatisfied = Integer.parseInt(scenario[2]);
        int status = Integer.parseInt(scenario[3]);
        if (status != 0) {
            System.err.println("A5_SIMULATOR_ERROR_" + status);
            System.exit(status);
        }
        Path out = null;
        String form = null;
        int flags = 0;
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (key.equals("--fake-attachments")) { flags++; continue; }
            if (key.startsWith("-")) {
                require(Arrays.asList("-n", "--submissions", "-s", "--seed", "-c", "--config",
                    "-o", "--output", "-m", "--media", "--server", "--python").contains(key), "Unknown argument " + key);
                require(i + 1 < args.length, "Missing value for " + key);
                String value = args[++i];
                if (key.equals("-o") || key.equals("--output")) out = Paths.get(value);
            } else {
                require(form == null, "Multiple positional arguments (boolean option acquired a value?)");
                form = key;
            }
        }
        require(flags <= 1, "Duplicate --fake-attachments");
        require(form != null && out != null, "Fixture requires FORM and saving()");
        Files.createDirectories(out);
        List<String> names = mode.equals("many") ? Arrays.asList("Zebra.csv", "alpha.csv", "m\u00e9nages.csv")
            : mode.equals("one") ? Arrays.asList("people.csv") : Collections.<String>emptyList();
        String fake = names.isEmpty() ? "" : "Fictitious attachments: " + String.join(", ", names) + "\n";
        String section = unsatisfied == 0 ? "" : "Unsatisfied constraints: 1\n  impossible, row 8: . > 0 and . < 0\n    submissions (3 of 3): 1, 2, 3\n";
        StringBuilder report = new StringBuilder("ctosim run report\nForm: Preload fixture (preload_fixture, version none)\n");
        report.append("Source: ").append(Paths.get(form).getFileName()).append("\nConfiguration: defaults\nSeed: -713\nSubmissions: 3\n")
            .append("Fieldwork: 2026-01-05T08:00:00 for 30 days, time zone UTC\nEnumerators: 5\n")
            .append(fake).append(section).append("Files:\n  Preload fixture_WIDE.csv\n  Preload fixture.csv\n");
        if (!names.isEmpty()) {
            Files.createDirectories(out.resolve("attachments"));
            for (String name : names) {
                write(out.resolve("attachments").resolve(name), "id,name\n1,Invented\n");
                report.append("  attachments/").append(name).append('\n');
            }
            // Copied real media must not be returned as fictitious simply because it is in this directory.
            write(out.resolve("attachments/present.csv"), "id,name\n1,Real\n");
        }
        report.append("  run-report.txt\nWarnings: ").append(warnings).append('\n');
        for (int w = 1; w <= warnings; w++) report.append("  A5_WARNING_").append(w).append(": unrelated warning\n");
        write(out.resolve("run-report.txt"), report.toString());
        write(out.resolve("Preload fixture_WIDE.csv"), "id,answer,note\n1,,\"caf\u00e9, quoted \"\"text\"\"\"\n2,2.5,\"line one\nline two\"\n3,-4,\u6771\u4eac\n");
        write(out.resolve("Preload fixture.csv"), "wrong_long_file\n999\n");
        System.out.println("Wrote 3 submissions of preload_fixture to " + out);
        System.out.println(warnings + " warnings; see run-report.txt");
        if (!names.isEmpty()) System.out.println("Invented content for " + String.join(", ", names));
    }

    private static void checkLog(Path log, Path report) throws Exception {
        List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        List<String> expected = Files.readAllLines(report, StandardCharsets.UTF_8);
        String fake = null;
        int warningIndex = -1;
        for (int i = 0; i < expected.size(); i++) {
            if (expected.get(i).startsWith("Fictitious attachments:")) fake = expected.get(i);
            if (expected.get(i).startsWith("Warnings:")) warningIndex = i;
        }
        require(warningIndex >= 0, "Invalid fixture report");
        int fakeAt = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Fictitious attachments:")) {
                require(fake != null, "Invented fictitious-attachments display for absent report line");
                require(lines.get(i).contains(fake), "Report line changed or truncated");
                require(fakeAt < 0, "Report line displayed more than once");
                fakeAt = i;
            }
        }
        require(fake == null || fakeAt >= 0, "Fictitious attachments report line not displayed");
        int last = fakeAt;
        int firstWarning = lines.size();
        for (int w = warningIndex + 1; w < expected.size(); w++) {
            String warning = expected.get(w).trim();
            int found = -1;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(warning)) {
                require(found < 0, "Warning duplicated: " + warning);
                found = i;
            }
            require(found > last, "Missing warning or warning precedes attachments line: " + warning);
            if (w == warningIndex + 1) firstWarning = found;
            last = found;
        }
        String count = expected.get(warningIndex).substring("Warnings:".length()).trim();
        boolean countShown = false;
        for (int i = fakeAt + 1; i < firstWarning; i++) {
            String line = lines.get(i).toLowerCase(Locale.ROOT);
            if (line.contains("warning") && Pattern.compile("\\b" + count + "\\b").matcher(line).find()) countShown = true;
        }
        require(countShown, "Warning count must follow attachment line and precede warning details");
    }
    public static int log(String[] args) throws Exception {
        try { checkLog(Paths.get(args[0]), Paths.get(args[1])); return 0; }
        catch (AssertionError e) { write(Paths.get(args[0] + ".failure.txt"), e.getMessage()); return 9; }
    }
    public static int help(String[] args) throws Exception {
        String text = read(Paths.get(args[0]));
        for (int i = 0; i < 8; i++) text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        text = text.replaceAll("\\{[^{}]*\\}", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            require(text.contains("fakepreload"), "Help does not name fakepreload");
            require(text.matches(".*(missing|unavailable|not available|absent).*"), "Help does not describe unavailable preload");
            require(text.matches(".*(invented|fictitious|fabricated|synthetic).*"), "Help does not describe invented values");
            require(text.matches(".*(form.{0,100}(use|read|refer)|(?:use|read|refer).{0,100}form).*"), "Help does not explain inference from the form's use");
            require(text.matches(".*test(ing|s)?.{0,100}(form|analysis).*"), "Help does not explain testing purpose");
            require(text.contains("attachments/"), "Help does not name attachments/ output directory");
            require(text.matches(".*(output|saving).*"), "Help does not locate files under output");
            require(text.matches(".*(read|inspect|view|open).*"), "Help does not explain reading generated files");
            require(text.matches(".*(edit|modify|change).*"), "Help does not explain editing generated files");
            require(text.contains("media("), "Help does not mention media() reuse");
            require(text.matches(".*(reus|use again|subsequent|later run).*"), "Help does not explain reuse");
            return 0;
        } catch (AssertionError e) { write(Paths.get(args[1]), e.getMessage()); return 9; }
    }
    private static void selfCheck(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path report = dir.resolve("report.txt"), log = dir.resolve("log.txt");
        String header = "Fictitious attachments: Zebra.csv, alpha.csv, m\u00e9nages.csv\n";
        String warning = "A5_WARNING_1: unrelated warning\n";
        write(report, header + "Warnings: 1\n  " + warning);
        write(log, "1 warnings; see run-report.txt\n" + header + "1 warning\n" + warning);
        checkLog(log, report);
        String[] invalid = {"1 warning\n" + warning, header + header + "1 warning\n" + warning,
            "1 warning\n" + warning + header, "1 warning\n" + header + warning,
            header + "1 warning\n" + warning + warning, header + "1 warning\n"};
        for (String bad : invalid) {
            write(log, bad);
            boolean rejected = false;
            try { checkLog(log, report); } catch (AssertionError e) { rejected = true; }
            require(rejected, "Display oracle accepted a deliberately invalid log");
        }
        write(report, "Warnings: 0\n");
        write(log, "0 warnings\n");
        checkLog(log, report);
        write(log, header + "0 warnings\n");
        boolean rejected = false;
        try { checkLog(log, report); } catch (AssertionError e) { rejected = true; }
        require(rejected, "Display oracle accepted an invented report line");
        System.out.println("Fakepreload display oracle self-checks passed");
    }
}
