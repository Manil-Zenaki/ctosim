package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 10 Amendment 3: public pipeline oracle, with no implementation inspection. */
final class CliAmendment3Support {
    static final String HEADER = "FORM ERRORS (an enumerator would be blocked): ";
    static final String REGEX = "regex(., '[')";
    static final String REPEATED = ". != string(/data/source/phone)";

    record Run(Input input, Path config, Path output, Result result, String report) { }

    static Input input(Path temp, String name) throws Exception {
        return fixture(temp.resolve("source"), "amendment3/" + name, "survey.xml");
    }

    static void replace(Input input, String from, String to) throws Exception {
        String xml = Files.readString(input.form());
        assertTrue(xml.contains(from), "Fixture replacement target missing: " + from);
        write(input.form(), xml.replace(from, to));
    }

    static Run execute(Path temp, Input input, int count, Map<String, Object> fields, String... options)
            throws Exception {
        Path settings = config(temp, JSON.writeValueAsString(Map.of("seed", 701001,
                "submissions", count, "missingRate", 0, "specialRate", 0, "fields", fields)));
        Path output = temp.resolve("output");
        var args = new ArrayList<>(List.of("-c", settings.toString()));
        args.addAll(List.of(options));
        Result result = simulate(input, output, args.toArray(String[]::new));
        success(result);
        return new Run(input, settings, output, result, report(output));
    }

    static Run execute(Path temp, String fixture, int count) throws Exception {
        return execute(temp, input(temp, fixture), count, Map.of("after", Map.of("value", "continued")));
    }

    static List<Submission> submissions(Run run, int count) throws Exception {
        var walker = new FormWalker(FormLoader.load(run.input().form(), run.input().form().getParent()),
                SimulationConfig.load(run.config()));
        List<Submission> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) result.add(walker.walk(i));
        return result;
    }

    static String section(String report) {
        int start = report.indexOf(HEADER);
        assertTrue(start >= 0, "Missing form-error section:\n" + report);
        int end = report.indexOf("Files:\n", start);
        assertTrue(end > start, report);
        for (String next : List.of("Fictitious attachments: ", "Unsatisfied constraints: ")) {
            int position = report.indexOf(next, start);
            if (position >= 0) end = Math.min(end, position);
        }
        return report.substring(start, end);
    }

    static String membership(List<Integer> numbers, int total) {
        return "    submissions (" + numbers.size() + " of " + total + "): "
                + numbers.stream().limit(20).map(Object::toString).collect(Collectors.joining(", "))
                + (numbers.size() > 20 ? ", ..." : "") + "\n";
    }

    static String unindexed(String path) { return path.replaceAll("\\[\\d+\\]", ""); }

    // Messages belong to the engine, so obtain them from the independent public walk.
    // Catalog rows and all formatting expectations remain independent CLI assertions.
    static String expected(List<Submission> submissions, Map<String, Integer> rows) {
        Map<String, List<FormError>> groups = new LinkedHashMap<>();
        for (Submission submission : submissions) for (FormError error : submission.formErrors())
            groups.computeIfAbsent(unindexed(error.path()), ignored -> new ArrayList<>()).add(error);
        assertFalse(groups.isEmpty(), "Fixture must actually produce structured form errors");
        StringBuilder text = new StringBuilder(HEADER).append(groups.size()).append('\n');
        groups.forEach((path, records) -> {
            FormError first = records.get(0);
            text.append("  ").append(first.field());
            if (rows.containsKey(path)) text.append(", row ").append(rows.get(path));
            text.append(": ").append(first.message()).append('\n');
            var numbers = new TreeSet<Integer>();
            for (FormError error : records) numbers.add(error.submission());
            text.append(membership(List.copyOf(numbers), submissions.size()));
        });
        return text.toString();
    }

    static void summary(Result result, int count, String names, int lineCount) {
        List<String> lines = result.out().lines().toList();
        assertEquals(lineCount, lines.size(), result.out());
        String last = lines.get(lines.size() - 1);
        assertTrue(last.startsWith("FORM ERRORS:"), last);
        assertEquals(1, lines.stream().filter(s -> s.startsWith("FORM ERRORS:")).count());
        assertTrue(Pattern.compile("(?<![0-9])" + count + "(?![0-9])").matcher(last).find(), last);
        assertTrue(last.contains(names), last);
        assertTrue(last.contains("run-report.txt"), last);
        String lower = last.toLowerCase(Locale.ROOT);
        assertTrue(lower.contains("block") && lower.contains("enumerator"), last);
    }

    private CliAmendment3Support() { }
}
