import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Independent spec-10 report producer and spec-11 Amendment-6 output oracle. */
public final class FormProblemsFixture {
    static void require(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static void write(Path p, String text) throws Exception {
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
    }
    static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).replace("\r", "");
    }
    public static void main(String[] args) throws Exception {
        Path work = Paths.get(System.getenv("CTOSIM_TEST_WORK"));
        String[] mode = read(work.resolve("form-problems-case.txt")).trim().split("\\s+");
        int errors = Integer.parseInt(mode[0]), unsatisfied = Integer.parseInt(mode[1]);
        int warnings = Integer.parseInt(mode[2]), fake = Integer.parseInt(mode[3]);
        int status = Integer.parseInt(mode[4]);
        if (status != 0) {
            System.err.println("A6_SIMULATOR_FAILURE_" + status);
            System.exit(status);
        }
        Path out = null;
        String form = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--fake-attachments")) continue;
            if (args[i].startsWith("-")) {
                String key = args[i], value = args[++i];
                if (key.equals("-o") || key.equals("--output")) out = Paths.get(value);
            } else form = args[i];
        }
        require(out != null && form != null, "Fixture requires FORM and saving()");
        Files.createDirectories(out);
        write(out.resolve("run-report.txt"), report(errors, unsatisfied, warnings, fake, Paths.get(form).getFileName().toString()));
        StringBuilder csv = new StringBuilder("id,answer,note\n");
        for (int n = 1; n <= 25; n++) csv.append(n).append(",,\"caf\u00e9, quoted \"\"text\"\"\"\n");
        write(out.resolve("Problems_WIDE.csv"), csv.toString());
        write(out.resolve("Problems.csv"), "wrong_long_file\n999\n");
        System.out.println("Wrote 25 submissions of form_problems_fixture to " + out);
        System.out.println(warnings + " warnings; see run-report.txt");
        if (unsatisfied > 0) System.out.println(unsatisfied + " questions have constraints that could not be satisfied; see run-report.txt");
        if (fake > 0) System.out.println("Invented attached files: people.csv, villages.csv");
        // This simulator summary is deliberately not the report section header, and
        // cannot satisfy the command's post-load, absolute-path final diagnostic.
        if (errors > 0) System.out.println("FORM ERRORS: " + errors + "; errors in this form would block an enumerator; see run-report.txt");
    }
    static String report(int errors, int unsatisfied, int warnings, int fake, String source) {
        StringBuilder r = new StringBuilder("ctosim run report\nForm: Problems (form_problems_fixture, version none)\nSource: " + source
                + "\nConfiguration: defaults\nSeed: -713\nSubmissions: 25\n"
                + "Fieldwork: 2026-01-05T08:00:00 for 30 days, time zone UTC\nEnumerators: 5\n");
        if (errors > 0) {
            r.append("FORM ERRORS (an enumerator would be blocked): ").append(errors).append('\n');
            for (int f = 1; f <= errors; f++) {
                r.append("  error_field_").append(f);
                if (f % 2 == 1) r.append(", row ").append(f + 9);
                r.append(f == 1 ? ": cannot evaluate ${limit}; caf\u00e9 \u6771\u4eac \"quoted\"\n" : ": required answer cannot be obtained\n");
                if (f == 1) {
                    r.append("    submissions (25 of 25): ");
                    for (int n = 1; n <= 20; n++) r.append(n == 1 ? "" : ", ").append(n);
                    r.append(", ...\n");
                } else r.append("    submissions (1 of 25): 2\n");
            }
        }
        if (fake > 0) r.append("Fictitious attachments: people.csv, villages.csv\n");
        if (unsatisfied > 0) {
            r.append("Unsatisfied constraints: ").append(unsatisfied).append('\n');
            for (int f = 1; f <= unsatisfied; f++) {
                r.append("  constraint_field_").append(f).append(", row ").append(100 + f).append(": . > ${limit} and . < 0\n");
                r.append("    submissions (1 of 25): 2\n");
                if (f % 2 == 1) r.append("    in submission 2: limit = 5; note = (blank)\n");
            }
        }
        r.append("Files:\n  Problems_WIDE.csv\n  Problems.csv\n");
        if (fake > 0) r.append("  attachments/people.csv\n  attachments/villages.csv\n");
        r.append("  run-report.txt\nWarnings: ").append(warnings).append('\n');
        for (int w = 1; w <= warnings; w++) r.append("  A6_WARNING_").append(w).append(": inspect the form\n");
        return r.toString();
    }

    static final class Line {
        final String text;
        final boolean red;
        Line(String t, boolean r) { text = t; red = r; }
    }
    static String plain(String s) {
        return s.replace("{c -(}", "{").replace("{c )-}", "}")
                .replaceAll("\\{(?:smcl|txt|text|err|error|res|result|com|input|reset|sf|bf|ul(?: on| off)?)\\}", "")
                .replaceAll("\\{(?:p[^}]*|hline[^}]*|right[^}]*|center[^}]*|.-)\\}", "");
    }
    static List<Line> output(String raw) {
        List<Line> result = new ArrayList<>();
        Pattern styles = Pattern.compile("\\{(txt|text|err|error|res|result|com|input|reset)\\}");
        String style = "txt";
        for (String line : raw.split("\n")) {
            Matcher m = styles.matcher(line);
            int from = 0;
            boolean red = true;
            StringBuilder visible = new StringBuilder();
            do {
                boolean found = m.find();
                String part = plain(line.substring(from, found ? m.start() : line.length()));
                if (!style.equals("com") && !style.equals("input") && !part.trim().isEmpty()) {
                    visible.append(part);
                    red &= style.equals("err") || style.equals("error");
                }
                if (!found) break;
                style = m.group(1);
                from = m.end();
            } while (true);
            String text = visible.toString().trim();
            if (!text.isEmpty() && !text.equals("{smcl}") && !text.matches("[.>] .*")) result.add(new Line(text, red));
        }
        return result;
    }
    static int count(List<String> report, String prefix) {
        for (String s : report) if (s.startsWith(prefix)) return Integer.parseInt(s.substring(s.lastIndexOf(':') + 1).trim());
        return 0;
    }
    static List<String> section(List<String> report, String prefix) {
        List<String> result = new ArrayList<>();
        boolean inside = false;
        for (String s : report) {
            if (s.startsWith(prefix)) { inside = true; result.add(s.trim()); }
            else if (inside && s.startsWith(" ")) result.add(s.trim());
            else if (inside) break;
        }
        return result;
    }
    static boolean number(String text, int n, String noun) {
        String boundary = "\\b" + n + "\\b";
        return Pattern.compile("(?:" + boundary + "\\s+(?:" + noun + ")|(?:" + noun
                + ")\\s*(?:\\([^)]*\\)\\s*)?[:=]?\\s*" + boundary + ")", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    static boolean problemSentence(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.contains("form") && lower.matches(".*\\b(problem|problems|issue|issues|error|errors)\\b.*");
    }
    static void checkLog(Path log, Path reportPath, String mode, int errorCode) throws Exception {
        List<String> report = Files.readAllLines(reportPath, StandardCharsets.UTF_8);
        List<Line> lines = output(read(log));
        int e = count(report, "FORM ERRORS ("), u = count(report, "Unsatisfied constraints:"), w = count(report, "Warnings:");
        if (errorCode != 0) {
            // Stata's own error trailer is not command output. It must follow the summary.
            int errorAt = -1;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).text.matches("r\\(" + errorCode + "\\);?")) { errorAt = i; break; }
            if (errorAt >= 0) {
                for (int i = errorAt; i < lines.size(); i++) require(lines.get(i).text.matches("r\\(" + errorCode + "\\);?"), "Output follows do-file error trailer");
                lines = new ArrayList<>(lines.subList(0, errorAt));
            }
        }
        int lastSection = -1;
        if (mode.equals("full")) {
            // Aggregate multiplicities: two fields can have identical submissions lines.
            List<String> expected = new ArrayList<>();
            expected.addAll(section(report, "FORM ERRORS ("));
            expected.addAll(section(report, "Unsatisfied constraints:"));
            for (String needle : new HashSet<>(expected)) {
                long actual = lines.stream().filter(l -> l.text.contains(needle)).count();
                require(actual == Collections.frequency(expected, needle), "Missing/duplicated section line: " + needle);
            }
            // Do not impose an order between the two sections, only within each.
            for (String prefix : Arrays.asList("FORM ERRORS (", "Unsatisfied constraints:")) {
                List<String> block = section(report, prefix);
                if (block.isEmpty()) {
                    for (Line l : lines) require(!l.text.contains(prefix), "Invented absent section: " + prefix);
                    continue;
                }
                int cursor = -1;
                for (String needle : block) {
                    int at = -1;
                    for (int i = cursor + 1; i < lines.size(); i++) if (lines.get(i).text.contains(needle)) { at = i; break; }
                    require(at > cursor, "Reordered/combined section lines: " + needle);
                    require(lines.get(at).red, "Section line is not entirely red: " + needle);
                    cursor = at;
                }
                lastSection = Math.max(lastSection, cursor);
            }
            int firstWarning = lines.size(), previous = lastSection;
            for (String needle : section(report, "Warnings:").subList(1, section(report, "Warnings:").size())) {
                int at = -1, occurrences = 0;
                for (int i = 0; i < lines.size(); i++) if (lines.get(i).text.contains(needle)) { at = i; occurrences++; }
                require(occurrences == 1 && at > previous, "Missing, duplicated or reordered warning: " + needle);
                firstWarning = Math.min(firstWarning, at);
                previous = at;
            }
            boolean warningCount = false;
            for (int i = lastSection + 1; i < firstWarning; i++) if (number(lines.get(i).text, w, "warnings?")) warningCount = true;
            require(warningCount, "Warning count must follow sections and precede warning details");
            for (String attachment : section(report, "Fictitious attachments:")) {
                int at = -1, occurrences = 0;
                for (int i = 0; i < lines.size(); i++) if (lines.get(i).text.contains(attachment)) { at = i; occurrences++; }
                require(occurrences == 1 && at < firstWarning, "Attachment section swallowed or duplicated by form errors");
            }
        }
        if (e == 0 && u == 0) {
            for (Line line : lines) require(!problemSentence(line.text), "Invented final form-problem message");
            return;
        }
        // The final message may occupy several lines; require an uninterrupted red
        // suffix containing the meaning/counts/path, with no unrelated output after it.
        int start = lines.size();
        while (start > 0 && lines.get(start - 1).red) start--;
        require(start < lines.size(), "Final output is not red (or final message is missing)");
        StringBuilder tail = new StringBuilder();
        for (int i = start; i < lines.size(); i++) tail.append(lines.get(i).text).append(' ');
        String summary = tail.toString().replace('\\', '/');
        String path = reportPath.toAbsolutePath().normalize().toString().replace('\\', '/');
        require(problemSentence(summary), "Final message must identify problems in the form");
        require(number(summary, e, "(?:form\\s+)?errors?(?:\\(s\\))?"), "Final message has wrong/missing form-error count");
        require(number(summary, u, "unsatisfied\\s+constraints?(?:\\(s\\))?"), "Final message has wrong/missing unsatisfied count");
        String lower = summary.toLowerCase(Locale.ROOT);
        require(lower.contains("enumerator") && lower.matches(".*(block|prevent|cannot|unable|stop).*"), "Final message must explain enumerator blocking");
        require(summary.toLowerCase(Locale.ROOT).contains(path.toLowerCase(Locale.ROOT)), "Final message lacks the report's absolute path");
        String end = lines.get(lines.size() - 1).text.replace('\\', '/');
        require(end.toLowerCase(Locale.ROOT).contains(path.toLowerCase(Locale.ROOT)) || problemSentence(end)
                || number(end, e, "(?:form\\s+)?errors?") || number(end, u, "unsatisfied\\s+constraints?"), "Unrelated output follows final message");
        for (Line l : lines) require(!l.text.contains("A6_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED")
                && !l.text.contains("A6_FAILING_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"), "Template output leaked");
    }
    public static int log(String[] args) throws Exception {
        try { checkLog(Paths.get(args[0]), Paths.get(args[1]), args[2], Integer.parseInt(args[3])); return 0; }
        catch (AssertionError e) { write(Paths.get(args[0] + ".failure.txt"), e.getMessage() + "\n"); return 9; }
    }
    public static int help(String[] args) throws Exception {
        String text = read(Paths.get(args[0]));
        for (int i = 0; i < 8; i++) text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        text = text.replaceAll("\\{[^{}]*\\}", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            require(text.contains("r(formerrors)"), "Help omits r(formerrors)");
            require(text.contains("form errors"), "Help omits FORM ERRORS section");
            require(text.contains("enumerator") && text.matches(".*(block|prevent|cannot|unable|stop).*"), "Help omits enumerator-blocking meaning");
            require(text.matches(".*(final|last|after (?:the )?(?:data|import|load)|at the end).*"), "Help omits final message timing");
            require(text.matches(".*\\b(red|error style|as error)\\b.*"), "Help omits red final diagnostic");
            require(text.contains("unsatisfied") && text.matches(".*(report|run-report[.]txt).*"), "Help omits final diagnostic content");
            return 0;
        } catch (AssertionError e) {
            if (args.length > 1) write(Paths.get(args[1]), e.getMessage() + "\n");
            return 9;
        }
    }
}
