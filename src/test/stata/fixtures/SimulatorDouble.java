import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Test-only executable for spec 11's documented spec 10 process boundary. */
public final class SimulatorDouble {
    public static void main(String[] args) throws Exception {
        Path work = Paths.get(System.getenv("CTOSIM_TEST_WORK"));
        Path fixtures = Paths.get(System.getenv("CTOSIM_TEST_FIXTURES"));
        Files.write(work.resolve("invoked.txt"), "invoked\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        Files.write(work.resolve("arguments.txt"), Arrays.asList(args), StandardCharsets.UTF_8);
        Files.write(work.resolve("runtime.txt"), Arrays.asList(
                System.getProperty("java.home"), String.valueOf(System.getenv("PYTHONPATH"))), StandardCharsets.UTF_8);
        Properties mode = new Properties();
        if (Files.exists(work.resolve("scenario.txt"))) {
            try (Reader in = Files.newBufferedReader(work.resolve("scenario.txt"), StandardCharsets.UTF_8)) { mode.load(in); }
        }
        int exit = Integer.parseInt(mode.getProperty("exit", "0"));
        System.out.println("SIMULATOR_STDOUT_11");
        System.err.println("SIMULATOR_STDERR_11");
        if (exit != 0) {
            System.err.println("SIMULATOR_FAILURE_11_STATUS_" + exit);
            System.exit(exit);
        }
        String form = null;
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) options.put(args[i], args[++i]);
            else if (form == null) form = args[i];
            else throw new IllegalArgumentException("Unexpected extra positional argument");
        }
        if (form == null) throw new IllegalArgumentException("Missing FORM");
        String base = Paths.get(form).getFileName().toString().replaceFirst("\\.[^.]+$", "");
        // Return values are controlled independently of invocation recording.
        // This catches wrappers that invent r() from requested options instead of the report.
        String nText = mode.getProperty("report_n", "3");
        String seed = mode.getProperty("report_seed", "-713");
        int n = Integer.parseInt(nText);
        Path out = Paths.get(options.getOrDefault("-o", options.getOrDefault("--output", "ctosim-out/" + base)));
        Files.createDirectories(out);
        String title = "Survey échantillon";
        // Fixed adversarial CSV, padded or truncated by complete records for n != 3.
        String csv = new String(Files.readAllBytes(fixtures.resolve("wide.csv")), StandardCharsets.UTF_8);
        if (n != 3) {
            String header = csv.substring(0, csv.indexOf('\n') + 1);
            StringBuilder rows = new StringBuilder(header);
            for (int i = 0; i < n; i++) rows.append("Jan 5 2026,uuid:" + i + ",42,3.25,plain,7,,001,yes\n");
            csv = rows.toString();
        }
        Files.write(out.resolve(title + "_WIDE.csv"), csv.getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve(title + ".csv"), "wrong_long_file\n999\n".getBytes(StandardCharsets.UTF_8));
        int warnings = Integer.parseInt(mode.getProperty("warnings", "0"));
        StringBuilder report = new StringBuilder("ctosim run report\nForm: " + title + " (fixture_id_11, version none)\n");
        report.append("Source: " + Paths.get(form).getFileName() + "\nConfiguration: defaults\nSeed: " + seed + "\nSubmissions: " + n + "\n");
        report.append("Fieldwork: 2026-01-05T08:00:00 for 30 days, time zone UTC\nEnumerators: 5\nFiles:\n  " + title + "_WIDE.csv\n  " + title + ".csv\n  run-report.txt\nWarnings: " + warnings + "\n");
        for (int i = 0; i < warnings; i++) report.append("  WARNING_11_" + (i + 1) + ": café attachment missing\n");
        Files.write(out.resolve("run-report.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
    }
}
