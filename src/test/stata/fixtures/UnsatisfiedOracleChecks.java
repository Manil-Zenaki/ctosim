import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Positive and deliberately broken examples for the test oracle itself. */
public final class UnsatisfiedOracleChecks {
    private static int checks;
    private static void logCase(Path work, String name, String report, String log, boolean good) throws Exception {
        Path r = work.resolve(name + ".txt"), l = work.resolve(name + ".smcl");
        Files.write(r, report.getBytes(StandardCharsets.UTF_8));
        Files.write(l, log.getBytes(StandardCharsets.UTF_8));
        int rc = UnsatisfiedFixture.log(new String[] { l.toString(), r.toString() });
        if ((rc == 0) != good) throw new AssertionError(name + " returned " + rc);
        checks++;
    }
    private static void helpCase(Path work, String name, String text, boolean good) throws Exception {
        Path p = work.resolve(name + ".sthlp");
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        int rc = UnsatisfiedFixture.help(new String[] { p.toString() });
        if ((rc == 0) != good) throw new AssertionError(name + " returned " + rc);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path work = Paths.get(args[0]);
        Files.createDirectories(work);
        String section = "Unsatisfied constraints: 2\n  alpha, row 9: . > ${limit}\n"
                + "    submissions (1 of 1): 1\n    in submission 1: limit = (blank); place = caf\u00e9 東京\n"
                + "  beta: . < 0\n    submissions (1 of 1): 1\n";
        // Identical submission lines are valid for different questions. The oracle
        // must compare ordered occurrences, not require globally unique line text.
        String warnings = "Warnings: 2\n  first warning\n  second warning\n";
        String report = "Enumerators: 5\n" + section + "Files:\n  fixture.csv\n" + warnings;
        String log = "{txt}2 warnings; see run-report.txt\n{err}" + section + "{txt}" + warnings;
        logCase(work, "valid-persistent-red", report, log, true);
        logCase(work, "valid-stata-formatting", report, log.replace("{err}", "{txt}{sf}{ul off}{err}"), true);
        logCase(work, "valid-repeated-red", report, log.replace("\n  ", "\n{err}  "), true);
        logCase(work, "missing-header", report, log.replace("Unsatisfied constraints: 2\n", ""), false);
        logCase(work, "wrong-color", report, log.replace("{err}", "{txt}"), false);
        logCase(work, "one-black-line", report, log.replace("  beta:", "{txt}  beta:"), false);
        logCase(work, "missing-reference", report, log.replace("    in submission 1: limit = (blank); place = caf\u00e9 東京\n", ""), false);
        logCase(work, "changed-text", report, log.replace("${limit}", "5"), false);
        logCase(work, "combined-lines", report, log.replace(". > ${limit}\n", ". > ${limit} "), false);
        logCase(work, "reordered", report, "{err}" + section.replace("  alpha, row 9: . > ${limit}\n", "")
                + "  alpha, row 9: . > ${limit}\n{txt}" + warnings, false);
        logCase(work, "duplicated-section", report, log + "{err}" + section, false);
        logCase(work, "warnings-before-section", report, "{txt}" + warnings + "{err}" + section, false);
        logCase(work, "missing-warning-count", report, log.replace("{txt}Warnings: 2\n", "{txt}"), false);
        logCase(work, "missing-warning", report, log.replace("  second warning\n", ""), false);
        logCase(work, "absent-zero", "Files:\n  f.csv\nWarnings: 0\n", "{txt}0 warnings\n", true);
        logCase(work, "absent-warnings", "Files:\n  f.csv\n" + warnings, "{txt}" + warnings, true);
        logCase(work, "invented-section", "Files:\n  f.csv\nWarnings: 0\n", "{err}Unsatisfied constraints: 0\n{txt}0 warnings\n", false);
        String help = "{title:Stored results}\n{synopt:{cmd:r(unsatisfied)}}number of questions\n"
                + "{title:Unsatisfied constraints}\nQuestions were left blank because no answer met their constraint. "
                + "This usually means the constraint cannot hold given the answers it refers to. Check the form.\n";
        helpCase(work, "valid-help", help, true);
        helpCase(work, "missing-stored-result", help.replace("r(unsatisfied)", "r(warnings)"), false);
        helpCase(work, "result-only-outside-stored-results", help.replace("r(unsatisfied)", "r(warnings)") + "r(unsatisfied)", false);
        helpCase(work, "missing-section-name", help.replace("Unsatisfied constraints", "Diagnostics"), false);
        helpCase(work, "missing-blank-explanation", help.replace("left blank", "omitted"), false);
        helpCase(work, "missing-impossibility-explanation", help.replace("no answer", "an answer"), false);
        helpCase(work, "missing-reference-explanation", help.replace(" given the answers it refers to", ""), false);
        helpCase(work, "missing-form-advice", help.replace("Check the form.", ""), false);
        System.out.println(checks + " independent oracle checks passed");
    }
}
