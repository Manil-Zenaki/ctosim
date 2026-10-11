package ctosim.stata;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Stata command {@code ctosim} calls through {@code javacall} (spec 11). Stata ignores
 * {@code shell} in batch mode on Windows, and its own Java may be older than the simulator needs,
 * so this class, compiled for Java 8, finds a Java 17 or newer and runs the simulator with it in a
 * separate process. It never throws to Stata: results are written to files the ado-file reads.
 */
public final class StataBridge {

    static final int MINIMUM_JAVA = 17;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern VERSION = Pattern.compile("version \"([0-9]+)(?:\\.([0-9]+))?");

    private StataBridge() {
    }

    /**
     * findJava RESULT: writes to RESULT the java executable to use on its first line, or an empty
     * first line and an explanation on the next lines when there is none.
     */
    public static int findJava(String[] args) {
        String result = args[0];
        List<String> lines = new ArrayList<String>();
        try {
            String java = resolveJava();
            if (java != null) {
                lines.add(java);
            } else {
                lines.add("");
                lines.add("ctosim needs Java " + MINIMUM_JAVA + " or newer; Stata's own Java is version "
                        + System.getProperty("java.version") + ".");
                lines.add("Install a free Java " + MINIMUM_JAVA + " (for example Eclipse Temurin, adoptium.net),"
                        + " or set the environment variable CTOSIM_JAVA to its java executable.");
            }
        } catch (RuntimeException e) {
            lines.clear();
            lines.add("");
            lines.add("ctosim could not look for Java: " + e);
        }
        write(result, lines);
        return 0;
    }

    /**
     * run JAVA JAR CONVERTER OUT ERR STATUS ARGS...: runs the simulator, writing its standard
     * output, standard error and exit status to OUT, ERR and STATUS. CONVERTER, unless it is "-", is
     * put first on the PYTHONPATH of the simulator (for the XLSForm converter).
     */
    public static int run(String[] args) {
        String java = args[0];
        String jar = args[1];
        String converter = "-".equals(args[2]) ? "" : args[2];
        File out = new File(args[3]);
        File err = new File(args[4]);
        String status = args[5];
        List<String> command = new ArrayList<String>(Arrays.asList(java, "-jar", jar));
        command.addAll(Arrays.asList(args).subList(6, args.length));
        int code;
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectOutput(out)
                    .redirectError(err);
            if (converter.length() > 0) {
                Map<String, String> env = builder.environment();
                String old = env.get("PYTHONPATH");
                env.put("PYTHONPATH", old == null || old.isEmpty() ? converter : converter + File.pathSeparator + old);
            }
            code = builder.start().waitFor();
        } catch (IOException e) {
            write(err.getPath(), Arrays.asList("ctosim: the simulator could not be started (" + e.getMessage() + ")"));
            code = 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            code = 1;
        }
        write(status, Arrays.asList(String.valueOf(code)));
        return 0;
    }

    // ---- finding Java ----

    private static String resolveJava() {
        // The first candidate that exists and is a Java 17 or newer (spec 11, Amendment 1).
        String windowsJava = isWindows() ? "java.exe" : "java";
        List<String> candidates = new ArrayList<String>();
        String override = System.getenv("CTOSIM_JAVA");
        if (override != null && !override.isEmpty()) {
            candidates.add(override);
        }
        candidates.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + windowsJava);
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            candidates.add(javaHome + File.separator + "bin" + File.separator + windowsJava);
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(Pattern.quote(File.pathSeparator))) {
                if (!dir.isEmpty()) {
                    candidates.add(dir + File.separator + windowsJava);
                }
            }
        }
        for (String candidate : candidates) {
            if (new File(candidate).isFile() && isRecentJava(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Whether {@code java -version} reports Java 17 or newer. */
    static boolean isRecentJava(String java) {
        try {
            Process process = new ProcessBuilder(java, "-version").redirectErrorStream(true).start();
            StringBuilder text = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            } finally {
                reader.close();
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy();
                return false;
            }
            Matcher m = VERSION.matcher(text);
            if (!m.find()) {
                return false;
            }
            int major = Integer.parseInt(m.group(1));
            if (major == 1 && m.group(2) != null) {
                major = Integer.parseInt(m.group(2)); // "1.8.0_..." is Java 8
            }
            return major >= MINIMUM_JAVA;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    private static void write(String file, List<String> lines) {
        try {
            Writer w = new OutputStreamWriter(new java.io.FileOutputStream(file), UTF_8);
            try {
                for (String line : lines) {
                    w.write(line);
                    w.write('\n');
                }
            } finally {
                w.close();
            }
        } catch (IOException e) {
            // Nothing to report to: the ado-file sees the missing or incomplete file.
        }
    }
}
