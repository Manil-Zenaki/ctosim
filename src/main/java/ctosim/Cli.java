package ctosim;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The ctosim command: XLSForm to simulated SurveyCTO exports. See specs/10-cli.md. */
public final class Cli {

    static final String USAGE = String.join("\n",
            "Usage: ctosim FORM [options]",
            "  FORM                    SurveyCTO XLSForm (.xlsx) or converted XForm (.xml with its .fields.json)",
            "  -c, --config FILE       simulation configuration (JSON)",
            "  -n, --submissions N     number of submissions (1 to 1000000)",
            "  -s, --seed N            seed",
            "  -o, --output DIR        output directory (default: ctosim-out/<FORM name>)",
            "  -m, --media DIR         attached files (default: FORM's directory)",
            "      --server NAME       server name in attachment URLs (default: example)",
            "      --python EXE        Python used to convert an XLSForm (default: $CTOSIM_PYTHON or python)",
            "      --fake-attachments  invent the attached files (preload) the form reads but the media lacks",
            "  -h, --help              show this help");

    static final String REPORT = "run-report.txt";
    private static final Set<String> SIMULATED_FUNCTIONS = Set.of("duration", "username");
    private static final Set<String> OPTIONS = Set.of("-c", "--config", "-n", "--submissions", "-s", "--seed",
            "-o", "--output", "-m", "--media", "--server", "--python");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT);

    private Cli() {
    }

    /** A usage problem: exit status 2. */
    private static final class UsageException extends Exception {
        UsageException(String message) {
            super(message);
        }
    }

    /** An input or output problem: exit status 1. */
    private static final class RunException extends Exception {
        RunException(String message) {
            super(message);
        }
    }

    private record Options(Path form, boolean xlsx, Path config, Integer submissions, Long seed, Path output,
                           Path media, String server, String python, boolean fakeAttachments) { }

    /** Where fictitious attached files are written, in the output directory (spec 10, Amendment 2). */
    static final String FAKE_DIRECTORY = "attachments";

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        for (String arg : args) {
            Objects.requireNonNull(arg, "argument");
            if (arg.equals("-h") || arg.equals("--help")) {
                out.println(USAGE);
                return 0;
            }
        }
        Options options;
        try {
            options = parse(args);
        } catch (UsageException e) {
            err.println(e.getMessage());
            err.println(USAGE);
            return 2;
        }
        try {
            String[] summary = simulate(options);
            for (String line : summary) {
                out.println(line);
            }
            return 0;
        } catch (RunException e) {
            err.println("ctosim: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("ctosim: the run failed (" + e.getMessage() + ")");
            return 1;
        }
    }

    public static void main(String[] args) {
        // UTF-8, so that file names with accents reach a calling program intact.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    // ---- arguments ----

    private static Options parse(String[] args) throws UsageException {
        String form = null;
        String config = null;
        String submissions = null;
        String seed = null;
        String output = null;
        String media = null;
        String server = "example";
        String python = null;
        boolean fake = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--fake-attachments")) {
                fake = true;
                continue;
            }
            if (arg.startsWith("-") && arg.length() > 1) {
                if (!OPTIONS.contains(arg)) {
                    throw new UsageException("unknown option " + arg);
                }
                if (i + 1 >= args.length) {
                    throw new UsageException("option " + arg + " needs a value");
                }
                String value = args[++i];
                switch (arg) {
                    case "-c", "--config" -> config = value;
                    case "-n", "--submissions" -> submissions = value;
                    case "-s", "--seed" -> seed = value;
                    case "-o", "--output" -> output = value;
                    case "-m", "--media" -> media = value;
                    case "--server" -> server = value;
                    case "--python" -> python = value;
                    default -> throw new UsageException("unknown option " + arg);
                }
            } else if (form == null) {
                form = arg;
            } else {
                throw new UsageException("only one FORM is allowed (got " + form + " and " + arg + ")");
            }
        }
        if (form == null) {
            throw new UsageException("FORM is missing");
        }
        String lower = form.toLowerCase(Locale.ROOT);
        boolean xlsx = lower.endsWith(".xlsx");
        if (!xlsx && !lower.endsWith(".xml")) {
            throw new UsageException("FORM must be an XLSForm (.xlsx) or an XForm (.xml): " + form);
        }
        Integer n = null;
        if (submissions != null) {
            try {
                n = Integer.parseInt(submissions);
            } catch (NumberFormatException e) {
                n = null;
            }
            if (n == null || n < 1 || n > SimulationConfig.MAX_SUBMISSIONS) {
                throw new UsageException("-n must be an integer from 1 to " + SimulationConfig.MAX_SUBMISSIONS
                        + ", not " + submissions);
            }
        }
        Long s = null;
        if (seed != null) {
            try {
                s = Long.parseLong(seed);
            } catch (NumberFormatException e) {
                throw new UsageException("-s must be an integer, not " + seed);
            }
        }
        Path formPath = Path.of(form);
        String base = formPath.getFileName().toString();
        base = base.substring(0, base.length() - (xlsx ? 5 : 4));
        Path out = output != null ? Path.of(output) : Path.of("ctosim-out", base);
        Path parent = formPath.toAbsolutePath().getParent();
        Path mediaDir = media != null ? Path.of(media) : parent;
        if (python == null) {
            String env = System.getenv("CTOSIM_PYTHON");
            python = env == null || env.isEmpty() ? "python" : env;
        }
        return new Options(formPath, xlsx, config == null ? null : Path.of(config), n, s, out, mediaDir, server, python,
                fake);
    }

    // ---- the run ----

    private static String[] simulate(Options o) throws RunException {
        SimulationConfig config = SimulationConfig.defaults();
        if (o.config() != null) {
            try {
                config = SimulationConfig.load(o.config());
            } catch (ConfigException e) {
                throw new RunException(e.getMessage());
            }
        }
        if (o.submissions() != null) {
            config = config.withSubmissions(o.submissions());
        }
        if (o.seed() != null) {
            config = config.withSeed(o.seed());
        }
        if (!Files.isRegularFile(o.form())) {
            throw new RunException(o.form() + ": the form file does not exist");
        }
        try {
            Files.createDirectories(o.output());
        } catch (IOException e) {
            throw new RunException(o.output() + ": the output directory cannot be created (" + e + ")");
        }

        List<String> warnings = new ArrayList<>();
        Path xform;
        Path catalogPath;
        if (o.xlsx()) {
            xform = o.output().resolve("form.xml");
            catalogPath = o.output().resolve("form.fields.json");
            warnings.addAll(convert(o, xform));
        } else {
            xform = o.form();
            String name = xform.getFileName().toString();
            catalogPath = xform.resolveSibling(name.substring(0, name.length() - 4) + ".fields.json");
            if (!Files.isRegularFile(catalogPath)) {
                throw new RunException(catalogPath + ": the field catalog of " + name + " does not exist");
            }
        }

        LoadedForm form;
        FieldCatalog catalog;
        try {
            form = FormLoader.load(xform, o.media());
        } catch (FormLoadException e) {
            throw new RunException(e.getMessage());
        }
        FakeAttachments fake = null;
        if (o.fakeAttachments()) {
            fake = FakeAttachments.of(form, config.seed());
            if (!fake.files().isEmpty()) {
                form = withFakeAttachments(o, xform, fake);
                for (Map.Entry<String, List<String>> key : fake.keyAnswers().entrySet()) {
                    config = config.withFieldIfAbsent(key.getKey(), keyOverride(key.getValue()));
                }
            }
        }
        try {
            catalog = FieldCatalog.load(catalogPath);
        } catch (IOException e) {
            throw new RunException(e.getMessage());
        }
        for (String name : form.missingAttachments()) {
            warnings.add("attached file " + name + ".csv not found");
        }
        for (String name : form.stubbedFunctions()) {
            if (SIMULATED_FUNCTIONS.contains(name)) {
                continue; // stubs in ordinary sessions only; simulated sessions implement them (spec 06)
            }
            warnings.add("function " + name + "() is not implemented by the simulator; it returns a fixed value");
        }
        FormWalker walker = new FormWalker(form, config);
        warnings.addAll(walker.configWarnings());
        List<Submission> submissions = new ArrayList<>();
        for (int k = 1; k <= config.submissions(); k++) {
            Submission s = walker.walk(k);
            submissions.add(s);
            warnings.addAll(s.warnings());
        }

        SurveyCtoExporter exporter;
        Map<String, String> longFiles;
        try {
            exporter = new SurveyCtoExporter(catalog, form, o.server());
            longFiles = exporter.longFiles(submissions);
        } catch (IllegalArgumentException e) {
            throw new RunException(e.getMessage());
        }
        try {
            exporter.writeAll(submissions, o.output());
        } catch (IOException e) {
            throw new RunException(o.output() + ": the exports cannot be written (" + e + ")");
        }

        List<String> files = new ArrayList<>();
        files.add(exporter.wideFileName());
        files.addAll(longFiles.keySet());
        List<String> fakeNames = new ArrayList<>();
        if (fake != null) {
            for (String name : fake.files().keySet()) {
                fakeNames.add(name + ".csv");
                files.add(FAKE_DIRECTORY + "/" + name + ".csv"); // after the long files (spec 10, Amendment 2)
            }
        }
        if (o.xlsx()) {
            files.add("form.xml");
            files.add("form.fields.json");
        }
        files.add(REPORT);
        Map<String, List<UnsatisfiedConstraint>> byQuestion = unsatisfiedByQuestion(submissions);
        List<String> unsatisfied = unsatisfiedSection(byQuestion, catalog, config.submissions());
        String report = report(o, form, config, files, warnings, fakeNames, unsatisfied);
        try {
            Files.writeString(o.output().resolve(REPORT), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RunException(o.output().resolve(REPORT) + ": the run report cannot be written (" + e + ")");
        }
        String[] lines = {
            "Simulated " + config.submissions() + " submissions of " + form.formId() + " into " + o.output(),
            warnings.size() + " warnings; see " + REPORT};
        List<String> all = new ArrayList<>(List.of(lines));
        if (!unsatisfied.isEmpty()) {
            List<String> fields = new ArrayList<>();
            for (List<UnsatisfiedConstraint> records : byQuestion.values()) {
                fields.add(records.get(0).field());
            }
            all.add(fields.size() + " questions whose constraints could not be satisfied: "
                    + String.join(", ", fields) + "; see " + REPORT);
        }
        if (!fakeNames.isEmpty()) {
            all.add("Fictitious attachments, with invented content: " + String.join(", ", fakeNames)
                    + " (in " + FAKE_DIRECTORY + "/)");
        }
        return all.toArray(new String[0]);
    }

    /**
     * How a key question is answered: among its keys, or within 1 to N when the keys are row
     * numbers, since texts do not apply to integer questions (spec 10, Amendment 2).
     */
    private static FieldOverride keyOverride(List<String> keys) {
        for (int i = 0; i < keys.size(); i++) {
            if (!keys.get(i).equals(Integer.toString(i + 1))) {
                return FieldOverride.ofTexts(keys);
            }
        }
        return FieldOverride.withRange(null, new NumericRange(1, keys.size()));
    }

    /**
     * Writes the fictitious files and copies of the media directory's CSV files to the output's
     * attachments directory, and loads the form again with it (spec 10, Amendment 2).
     */
    private static LoadedForm withFakeAttachments(Options o, Path xform, FakeAttachments fake) throws RunException {
        Path dir = o.output().resolve(FAKE_DIRECTORY);
        try {
            Files.createDirectories(dir);
            if (o.media() != null && Files.isDirectory(o.media())) {
                try (var listing = Files.list(o.media())) {
                    for (Path csv : (Iterable<Path>) listing::iterator) {
                        String name = csv.getFileName().toString();
                        if (Files.isRegularFile(csv) && name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                            Files.copy(csv, dir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            for (Map.Entry<String, String> file : fake.files().entrySet()) {
                Files.writeString(dir.resolve(file.getKey() + ".csv"), file.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RunException(dir + ": the fictitious attachments cannot be written (" + e + ")");
        }
        try {
            return FormLoader.load(xform, dir);
        } catch (FormLoadException e) {
            throw new RunException(e.getMessage());
        }
    }

    /** The unsatisfied constraints of all submissions by question path without repeat indices, in order. */
    private static Map<String, List<UnsatisfiedConstraint>> unsatisfiedByQuestion(List<Submission> submissions) {
        Map<String, List<UnsatisfiedConstraint>> byQuestion = new java.util.LinkedHashMap<>();
        for (Submission s : submissions) {
            for (UnsatisfiedConstraint u : s.unsatisfiedConstraints()) {
                byQuestion.computeIfAbsent(u.path().replaceAll("\\[\\d+]", ""), k -> new ArrayList<>()).add(u);
            }
        }
        return byQuestion;
    }

    /** The "Unsatisfied constraints" section of the report (spec 10, Amendment 1); empty when there is none. */
    private static List<String> unsatisfiedSection(Map<String, List<UnsatisfiedConstraint>> byQuestion,
                                                   FieldCatalog catalog, int n) {
        if (byQuestion.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> rows = new HashMap<>();
        for (FieldInfo f : catalog.fields()) {
            rows.putIfAbsent(f.xpath(), f.row());
        }
        List<String> section = new ArrayList<>();
        section.add("Unsatisfied constraints: " + byQuestion.size());
        for (Map.Entry<String, List<UnsatisfiedConstraint>> e : byQuestion.entrySet()) {
            UnsatisfiedConstraint first = e.getValue().get(0);
            Integer row = rows.get(e.getKey());
            section.add("  " + first.field() + (row == null ? "" : ", row " + row) + ": " + first.constraint());
            java.util.TreeSet<Integer> numbers = new java.util.TreeSet<>();
            for (UnsatisfiedConstraint u : e.getValue()) {
                numbers.add(u.submission());
            }
            StringBuilder list = new StringBuilder();
            int shown = 0;
            for (int number : numbers) {
                if (shown == 20) {
                    list.append(", ...");
                    break;
                }
                list.append(shown++ == 0 ? "" : ", ").append(number);
            }
            section.add("    submissions (" + numbers.size() + " of " + n + "): " + list);
            if (!first.references().isEmpty()) {
                List<String> values = new ArrayList<>();
                first.references().forEach((name, value) -> values.add(name + " = " + (value.isEmpty() ? "(blank)" : value)));
                section.add("    in submission " + first.submission() + ": " + String.join("; ", values));
            }
        }
        section.replaceAll(line -> line.replaceAll("\\R", " "));
        return section;
    }

    /** Runs the Python converter; returns its warnings. */
    private static List<String> convert(Options o, Path xform) throws RunException {
        List<String> command = List.of(o.python(), "-m", "ctosim_xlsform", o.form().toString(), "-o", xform.toString());
        Process process;
        try {
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            throw new RunException("the converter could not be started with Python '" + o.python()
                    + "' (" + e.getMessage() + "); set --python or CTOSIM_PYTHON");
        }
        String stderr;
        int status;
        try (InputStream in = process.getErrorStream()) {
            stderr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            status = process.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RunException("interrupted while converting " + o.form().getFileName());
        }
        if (status != 0) {
            throw new RunException(o.form().getFileName() + ": the conversion failed (exit status " + status + ")"
                    + (stderr.isBlank() ? "" : ":\n" + stderr.strip()));
        }
        List<String> warnings = new ArrayList<>();
        for (String line : stderr.split("\\R")) {
            if (!line.isBlank()) {
                warnings.add(line);
            }
        }
        return warnings;
    }

    private static String report(Options o, LoadedForm form, SimulationConfig config, List<String> files,
                                 List<String> warnings, List<String> fakeNames, List<String> unsatisfied) {
        StringBuilder r = new StringBuilder();
        line(r, "ctosim run report");
        line(r, "Form: " + form.title() + " (" + form.formId() + ", version "
                + (form.version() == null ? "none" : form.version()) + ")");
        line(r, "Source: " + o.form().getFileName());
        line(r, "Configuration: " + (o.config() == null ? "defaults" : o.config().getFileName()));
        line(r, "Seed: " + config.seed());
        line(r, "Submissions: " + config.submissions());
        line(r, "Fieldwork: " + ISO.format(config.start()) + " for " + config.fieldworkDays()
                + " days, time zone " + config.timeZone().getId());
        line(r, "Enumerators: " + config.enumerators());
        if (!fakeNames.isEmpty()) {
            line(r, "Fictitious attachments: " + String.join(", ", fakeNames));
        }
        for (String text : unsatisfied) {
            line(r, text);
        }
        line(r, "Files:");
        for (String file : files) {
            line(r, "  " + file);
        }
        line(r, "Warnings: " + warnings.size());
        for (String warning : warnings) {
            line(r, "  " + warning.replaceAll("\\R", " "));
        }
        return r.toString();
    }

    private static void line(StringBuilder r, String text) {
        r.append(text).append('\n');
    }
}
