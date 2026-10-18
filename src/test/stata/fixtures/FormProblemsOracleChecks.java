import java.nio.file.*;
import java.util.*;

/** Validate the independent log checker with accepted and deliberately broken logs. */
public final class FormProblemsOracleChecks {
    private static int checks;
    private static void check(Path dir, String name, Path report, String log, String mode, int error, boolean good) throws Exception {
        Path p = dir.resolve(name + ".smcl");
        FormProblemsFixture.write(p, log);
        int rc = FormProblemsFixture.log(new String[] {p.toString(), report.toString(), mode, String.valueOf(error)});
        if ((rc == 0) != good) throw new AssertionError(name + " returned " + rc + ": "
                + (Files.exists(Paths.get(p + ".failure.txt")) ? FormProblemsFixture.read(Paths.get(p + ".failure.txt")) : ""));
        checks++;
    }
    private static String summary(Path report, int e, int u) {
        return "{err}The form has problems: " + e + " form errors (which would block an enumerator), "
                + u + " unsatisfied constraints.\nReport: " + report.toAbsolutePath().normalize() + "\n";
    }
    private static String section(String report, String name) {
        return String.join("\n", FormProblemsFixture.section(Arrays.asList(report.split("\n")), name)) + "\n";
    }
    private static void help(Path dir, String name, String text, boolean good) throws Exception {
        Path p = dir.resolve(name + ".sthlp");
        FormProblemsFixture.write(p, text);
        int rc = FormProblemsFixture.help(new String[] { p.toString() });
        if ((rc == 0) != good) throw new AssertionError(name + " returned " + rc);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]).toAbsolutePath();
        Files.createDirectories(dir);
        Path report = dir.resolve("run-report.txt");
        String r = FormProblemsFixture.report(3, 2, 2, 1, "survey.xml");
        FormProblemsFixture.write(report, r);
        String e = section(r, "FORM ERRORS (");
        String u = section(r, "Unsatisfied constraints:");
        String a = section(r, "Fictitious attachments:");
        String w = section(r, "Warnings:");
        String prefix = "{txt}2 warnings; see run-report.txt\n{err}" + e + "{txt}" + a + "{err}" + u + "{txt}" + w;
        String last = summary(report, 3, 2);
        String valid = prefix + "{txt}(3 vars, 25 obs)\n" + last;
        check(dir, "valid", report, valid, "full", 0, true);
        check(dir, "red-per-line", report, valid.replace("\nsubmissions", "\n{err}submissions"), "full", 0, true);
        check(dir, "command-echo-ignored", report, valid + "{com}. quietly log close a6\n{txt}", "full", 0, true);
        check(dir, "alternate-section-order", report, "{err}" + u + e + "{txt}" + a + w + last, "full", 0, true);
        check(dir, "tail", report, last, "tail", 0, true);
        check(dir, "failure-tail", report, last + "{err}r(459);\n", "tail", 459, true);
        check(dir, "failure-full", report, valid + "{err}r(459);\n", "full", 459, true);
        check(dir, "missing-section-header", report, valid.replace(e.split("\n")[0] + "\n", ""), "full", 0, false);
        check(dir, "missing-detail", report, valid.replace("error_field_2: required answer cannot be obtained\n", ""), "full", 0, false);
        check(dir, "duplicated-section", report, "{err}" + e + valid, "full", 0, false);
        check(dir, "wrong-color", report, valid.replace("{err}" + e, "{txt}" + e), "full", 0, false);
        check(dir, "one-black-line", report, valid.replace("error_field_2:", "{txt}error_field_2:"), "full", 0, false);
        check(dir, "altered-xpath", report, valid.replace("${limit}", "5"), "full", 0, false);
        check(dir, "combined-lines", report, valid.replace("\"quoted\"\n", "\"quoted\" "), "full", 0, false);
        check(dir, "lost-last-record", report, valid.replace("error_field_3, row 12: required answer cannot be obtained\n", ""), "full", 0, false);
        check(dir, "missing-warning-count", report, valid.replace("Warnings: 2\n", ""), "full", 0, false);
        check(dir, "warning-before-section", report, "{txt}" + a + w + "{err}" + e + u + last, "full", 0, false);
        check(dir, "missing-warning", report, valid.replace("A6_WARNING_2: inspect the form\n", ""), "full", 0, false);
        check(dir, "missing-attachment", report, valid.replace(a, ""), "full", 0, false);
        check(dir, "missing-final", report, prefix, "full", 0, false);
        check(dir, "wrong-final-color", report, prefix + last.replace("{err}", "{txt}"), "full", 0, false);
        check(dir, "message-before-import", report, prefix + last + "{txt}(3 vars, 25 obs)\n", "full", 0, false);
        check(dir, "extra-output", report, valid + "{txt}Finished\n", "full", 0, false);
        check(dir, "extra-red-output", report, valid + "{err}Finished\n", "full", 0, false);
        check(dir, "swapped-counts", report, prefix + summary(report, 2, 3), "full", 0, false);
        check(dir, "missing-zero-count", report, prefix + last.replace("2 unsatisfied constraints", "unsatisfied constraints"), "full", 0, false);
        check(dir, "missing-blocking-meaning", report, prefix + last.replace(" (which would block an enumerator)", ""), "full", 0, false);
        check(dir, "relative-report", report, prefix + last.replace(report.toString(), "run-report.txt"), "full", 0, false);
        check(dir, "wrong-report", report, prefix + last.replace("run-report.txt", "other-report.txt"), "full", 0, false);
        check(dir, "summary-after-error", report, prefix + "{err}r(459);\n" + last, "full", 459, false);
        check(dir, "no-tail-message", report, "", "tail", 0, false);
        check(dir, "template-output-leaked", report, prefix + "{txt}A6_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED\n" + last, "full", 0, false);
        // Each side of the OR and the zero/zero boundary.
        for (int[] counts : new int[][] {{1, 0}, {0, 2}, {0, 0}}) {
            int fe = counts[0], uc = counts[1];
            FormProblemsFixture.write(report, FormProblemsFixture.report(fe, uc, 0, 0, "survey.xml"));
            String tail = fe + uc > 0 ? summary(report, fe, uc) : "";
            check(dir, "boundary-" + fe + "-" + uc, report, tail, "tail", 0, true);
            if (fe + uc == 0) check(dir, "invented-final", report, summary(report, 0, 0), "tail", 0, false);
        }
        String h = "{title:Stored results}\n{synopt:{cmd:r(formerrors)}}number of form errors\n"
                + "{title:FORM ERRORS}\nThe section identifies errors that would block an enumerator. "
                + "After the data load, a final red message gives form errors, unsatisfied constraints and the run report path.\n";
        help(dir, "valid-help", h, true);
        help(dir, "missing-help-result", h.replace("r(formerrors)", "r(other)"), false);
        help(dir, "missing-help-final", h.replace("After the data load, a final red message", "A red message"), false);
        help(dir, "missing-help-color", h.replace(" red", ""), false);
        System.out.println(checks + " Amendment 6 oracle checks passed");
    }
}
