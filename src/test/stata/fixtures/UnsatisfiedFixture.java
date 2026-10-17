import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Spec-10 process fixture and independent display oracle; no production dependencies. */
public final class UnsatisfiedFixture {
    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).replace("\r", "");
    }
    private static void write(Path p, String s) throws Exception {
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        Path work = Paths.get(System.getenv("CTOSIM_TEST_WORK"));
        int fields = Integer.parseInt(read(work.resolve("unsatisfied-case.txt")).trim());
        Path out = null;
        String form = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                String key = args[i], value = args[++i];
                if (key.equals("-o") || key.equals("--output")) out = Paths.get(value);
            } else form = args[i];
        }
        if (out == null || form == null) throw new IllegalArgumentException("Fixture needs FORM and saving()");
        Files.createDirectories(out);
        int warnings = fields == 0 ? 0 : fields < 0 ? 2 : fields + 24;
        StringBuilder section = new StringBuilder();
        if (fields > 0) {
            section.append("Unsatisfied constraints: ").append(fields).append('\n');
            for (int f = 1; f <= fields; f++) {
                // Vary optional row/reference lines and preserve literal XPath, braces and Unicode.
                section.append("  question_").append(f);
                if (f % 2 == 1) section.append(", row ").append(f + 9);
                section.append(f == 1 ? ": . > ${limit} and . < 0\n" : ": . = 'caf\u00e9 東京' and . = 'impossible'\n");
                if (f == 1) {
                    section.append("    submissions (25 of 25): ");
                    for (int n = 1; n <= 20; n++) section.append(n == 1 ? "" : ", ").append(n);
                    section.append(", ...\n    in submission 1: limit = 5; note = (blank); place = caf\u00e9 東京\n");
                } else {
                    section.append("    submissions (1 of 25): ").append(f).append('\n');
                }
            }
        }
        StringBuilder report = new StringBuilder("ctosim run report\nForm: Constraint fixture (unsatisfied_fixture, version none)\n");
        report.append("Source: ").append(Paths.get(form).getFileName()).append("\nConfiguration: defaults\nSeed: -713\nSubmissions: 25\n");
        report.append("Fieldwork: 2026-01-05T08:00:00 for 30 days, time zone UTC\nEnumerators: 5\n").append(section);
        report.append("Files:\n  Constraint fixture_WIDE.csv\n  Constraint fixture.csv\n  run-report.txt\nWarnings: ").append(warnings).append('\n');
        for (int w = 1; w <= warnings; w++) {
            report.append("  A4_WARNING_").append(w).append(fields > 0 ? ": no answer met a question constraint" : ": attached file absent").append('\n');
        }
        write(out.resolve("run-report.txt"), report.toString());
        StringBuilder csv = new StringBuilder("id,answer,note\n");
        for (int n = 1; n <= 25; n++) csv.append(n).append(",,\"caf\u00e9 東京, quoted \"\"text\"\"\"\n");
        write(out.resolve("Constraint fixture_WIDE.csv"), csv.toString());
        write(out.resolve("Constraint fixture.csv"), "wrong_long_file\n999\n");
        System.out.println("Wrote 25 submissions of unsatisfied_fixture to " + out);
        System.out.println(warnings + " warnings; see run-report.txt");
        if (fields > 0) {
            List<String> names = new ArrayList<>();
            for (int f = 1; f <= fields; f++) names.add("question_" + f);
            System.out.println(fields + " questions have constraints that could not be satisfied: "
                    + String.join(", ", names) + "; see run-report.txt");
        }
    }

    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static String plain(String s) {
        return s.replace("{c -(}", "{").replace("{c )-}", "}")
                .replaceAll("\\{(?:smcl|txt|text|err|error|res|result|com|input|reset|sf|bf|ul(?: on| off)?)\\}", "")
                .replaceAll("\\{(?:p[^}]*|hline[^}]*|right[^}]*|center[^}]*)\\}", "");
    }
    private static int uniqueLine(List<String> lines, String needle) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(needle)) {
            require(found == -1, "Duplicate display: " + needle);
            found = i;
        }
        require(found >= 0, "Missing display: " + needle);
        return found;
    }
    private static void checkLog(Path smcl, Path reportFile) throws Exception {
        List<String> report = Files.readAllLines(reportFile, StandardCharsets.UTF_8);
        List<String> raw = Files.readAllLines(smcl, StandardCharsets.UTF_8);
        List<String> rendered = new ArrayList<>();
        List<Boolean> red = new ArrayList<>();
        String style = "txt";
        Pattern token = Pattern.compile("\\{(txt|text|err|error|res|result|com|input|reset)\\}");
        for (String line : raw) {
            Matcher m = token.matcher(line);
            int previous = 0;
            boolean allRed = true;
            while (m.find()) {
                if (!plain(line.substring(previous, m.start())).trim().isEmpty())
                    allRed &= style.equals("err") || style.equals("error");
                style = m.group(1);
                previous = m.end();
            }
            if (!plain(line.substring(previous)).trim().isEmpty())
                allRed &= style.equals("err") || style.equals("error");
            rendered.add(plain(line));
            red.add(allRed);
        }
        int header = -1, files = report.indexOf("Files:"), last = -1;
        for (int i = 0; i < report.size(); i++) if (report.get(i).startsWith("Unsatisfied constraints:")) header = i;
        if (header >= 0) {
            require(files > header, "Invalid oracle report");
            for (int i = header; i < files; i++) {
                String expected = report.get(i).trim();
                int expectedCount = 0, actualCount = 0, at = -1;
                for (int j = header; j < files; j++) if (report.get(j).trim().equals(expected)) expectedCount++;
                for (int j = 0; j < rendered.size(); j++) if (rendered.get(j).contains(expected)) {
                    actualCount++;
                    if (j > last && at < 0) at = j;
                }
                require(actualCount == expectedCount, "Missing or duplicated section line: " + expected);
                require(at > last, "Section lines reordered or combined: " + expected);
                require(red.get(at), "Section line not entirely display-as-error: " + expected);
                last = at;
            }
        } else {
            for (String line : rendered) require(!line.contains("Unsatisfied constraints:"), "Invented section for absent report section");
        }
        int sectionEnd = last;
        int warningHeader = -1;
        for (int i = 0; i < report.size(); i++) if (report.get(i).startsWith("Warnings:")) warningHeader = i;
        require(warningHeader >= 0, "Missing oracle warning header");
        int count = Integer.parseInt(report.get(warningHeader).substring(9).trim());
        int firstWarning = rendered.size();
        for (int i = warningHeader + 1; i < report.size(); i++) {
            int at = uniqueLine(rendered, report.get(i).trim());
            require(at > last, "Warning precedes section or warning order changed");
            if (i == warningHeader + 1) firstWarning = at;
            last = at;
        }
        // Simulator stdout itself mentions the warning count. Only a count after the
        // complete section and before the individual warnings satisfies step 3.
        boolean foundCount = false;
        for (int i = sectionEnd + 1; i < firstWarning; i++) {
            String line = rendered.get(i).toLowerCase(Locale.ROOT);
            if (line.contains("warning") && Pattern.compile("\\b" + count + "\\b").matcher(line).find()) foundCount = true;
        }
        require(foundCount, "Warning count not shown between section and warning lines");
    }
    public static int log(String[] args) throws Exception {
        try { checkLog(Paths.get(args[0]), Paths.get(args[1])); return 0; }
        catch (AssertionError e) {
            write(Paths.get(args[0] + ".failure.txt"), e.getMessage() + "\n");
            return 9;
        }
    }
    public static int help(String[] args) throws Exception {
        String source = read(Paths.get(args[0]));
        // Permit SMCL formatting and wrapped prose; avoid any exact sentence requirement.
        String rendered = source.replaceAll("(?i)\\{title:", "@@SECTION {title:");
        // Formatting can nest, for example {synopt:{cmd:r(unsatisfied)}}.
        for (int i = 0; i < 8; i++) rendered = rendered.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        String text = rendered.replaceAll("\\{[^{}]*\\}", " ")
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            int stored = text.indexOf("stored results");
            int next = stored < 0 ? -1 : text.indexOf("@@section", stored);
            require(stored >= 0 && text.substring(stored, next < 0 ? text.length() : next).contains("r(unsatisfied)"), "r(unsatisfied) missing from stored results");
            require(text.contains("unsatisfied constraints"), "Section is not named in help");
            require(text.matches(".*(left blank|left missing|blank answers|missing answers).*"), "Help does not explain blank answers");
            require(text.matches(".*(no answer|no value|cannot be satisfied|could not be satisfied|impossible|unsatisfiable).*"), "Help does not explain unsatisfiable constraints");
            require(text.matches(".*(refer|depend|other answer|other question).*"), "Help does not explain dependence on referenced answers");
            require(text.matches(".*(check|review|inspect|correct|fix).*form.*") || text.matches(".*form.*(check|review|inspect|correct|fix).*"), "Help does not advise checking the form");
            return 0;
        } catch (AssertionError e) {
            if (args.length > 1) write(Paths.get(args[1]), e.getMessage() + "\n");
            return 9;
        }
    }
}
