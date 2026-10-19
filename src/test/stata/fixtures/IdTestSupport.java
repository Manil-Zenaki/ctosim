import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Test-only Amendment 7 documentation oracle and flag-aware process fixture. */
public final class IdTestSupport {
    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    private static String plain(String text) {
        for (int i = 0; i < 8; i++) text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static void check(String raw, String area) {
        String t = plain(raw);
        if (area.equals("option")) {
            require(t.matches(".*\\bid\\s*\\([^)]*\\).*"), "Help must document id(name)");
            require(t.matches(".*(survey|household|respondent).{0,80}(id|identifier).*"), "Help must explain the survey id");
            require(t.matches(".*(question|field).*"), "Help must say id names a question");
            require(t.matches(".*(distinct|unique|different).{0,60}(ids?\\b|identif)|.*(ids?\\b|identif).{0,60}(distinct|unique|different).*"), "Help must explain distinct survey ids");
        } else {
            require(area.equals("plan"), "Unknown oracle area");
            require(t.matches(".*(no overlap|never overlap|non.overlap|do not overlap|cannot overlap|without overlap).*"), "Help must explain no overlapping interviews");
            require(t.contains("enumerator"), "Help must describe the plan per enumerator");
            require(t.matches(".*start.{0,120}(working hours|work hours|workday|working day).*"), "Help must describe interview starts within working hours");
            require(t.matches(".*(one|single|same) village.{0,100}(day|daily).*"), "Help must describe one village a day");
            require(t.contains("fieldworkdays"), "Help must name fieldworkDays");
            require(t.matches(".*(longer|extend|exceed|beyond|more days|extra days|overrun).*"), "Help must explain extension beyond fieldworkDays");
            require(t.matches(".*(fit|enough|capacity|long interview|interviews.{0,60}(take|require)).*"), "Help must explain why interviews extend fieldwork");
            require(t.contains("warn"), "Help must explain the overflow warning");
        }
    }
    public static int help(String[] args) throws Exception {
        try {
            check(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8), args[1]);
            return 0;
        } catch (AssertionError e) {
            Files.write(Paths.get(args[2]), (e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            return 9;
        }
    }
    private static void selfCheck() {
        String[] option = {"{cmd:id({it:name})}", "names the survey id question.", "Each submission has a distinct identifier."};
        String[] plan = {"Interviews never overlap.", "The plan applies per enumerator.",
            "Interviews start within working hours.", "One village per day.", "FieldworkDays is the planned duration.",
            "Fieldwork can last longer.", "Interviews may not fit.", "A warning is shown."};
        for (String area : Arrays.asList("option", "plan")) {
            String[] clauses = area.equals("option") ? option : plan;
            check(String.join("\n", clauses), area);
            for (int i = 0; i < clauses.length; i++) {
                List<String> missing = new ArrayList<>(Arrays.asList(clauses));
                missing.remove(i);
                boolean rejected = false;
                try { check(String.join(" ", missing), area); }
                catch (AssertionError expected) { rejected = true; }
                require(rejected, "Help oracle missed omitted " + area + " clause " + i);
            }
        }
        System.out.println("Amendment 7 help oracle positive and omission checks passed");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-check")) { selfCheck(); return; }
        // Preserve the original arguments; the older fixture only supports valued options.
        List<String> valued = new ArrayList<>();
        for (String arg : args) if (!arg.equals("--fake-attachments")) valued.add(arg);
        SimulatorDouble.main(valued.toArray(new String[0]));
        Files.write(Paths.get(System.getenv("CTOSIM_TEST_WORK"), "arguments.txt"), Arrays.asList(args), StandardCharsets.UTF_8);
    }
}
