import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Spec 11 Amendment 11 only. Lexical smoke checks; see questions K1-K3.
 * Production help is consumed only at the public artifact boundary by help().
 */
public final class LeftOpenHelpChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String plain(String text) {
        text = text.replaceAll("(?s)\\{\\*.*?\\}", " ");
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void has(String text, String regex, String why) {
        require(Pattern.compile(regex).matcher(text).find(), why);
    }

    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,240}(?:" + b + ")|(?:" + b
            + ").{0,240}(?:" + a + ")", why);
    }

    private static final String PROBLEM = "leftopen|(?:form|interview).{0,25}(?:left|stays?|kept|remains?).{0,12}open";
    private static final String PAUSE = "interrupt|pause|suspend|break";
    private static final String FINISH = "finish|complet|finali[sz]|resum|return|come.{0,8}back";
    private static final String MORNING = "(?:next|following).{0,15}morning|morning.{0,20}(?:next|following)|"
        + "(?:start|beginning).{0,15}(?:work|working day).{0,20}(?:next|following).{0,8}day|"
        + "(?:next|following).{0,12}day.{0,20}(?:start|beginning).{0,12}(?:work|working hours)";
    private static final String ELAPSED = "elapsed|wall[- ]clock|interval|time.{0,15}(?:between|from)|endtime\\s*-\\s*starttime";
    private static final String SCREEN = "screen|active.{0,15}(?:time|use)|actively.{0,15}(?:used|open)";

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "problem":
            has(visible, "\\bleftOpen\\b", "Help must name the problem leftOpen exactly");
            near(t, PROBLEM, PAUSE, "Help must explain that the form is interrupted");
            near(t, PAUSE, "(?:another|other|second|next).{0,25}interview",
                "Help must describe another interview during the interruption");
            near(t, "(?:another|other|second|next).{0,25}interview", "meanwhile|during|in between|interven|before|then|after|later|return|come.{0,8}back",
                "Help must explain the intervening interview's timing");
            near(t, PAUSE, FINISH, "Help must explain returning to finish the interrupted form");
            break;
        case "configuration":
            has(visible, "\\bleftOpen\\b", "Help must name the exact configuration key leftOpen");
            near(t, "\\bleftopen\\b", "config|json|setting|key|rate",
                "Help must identify leftOpen as configuration, not only a problem name");
            break;
        case "late_day":
            near(t, PROBLEM, PAUSE, "Help must explain the interrupted form");
            near(t, PAUSE, "late.{0,30}(?:day|afternoon)|end.{0,20}(?:day|work)|too late",
                "Help must describe an interruption late in the working day");
            near(t, "late.{0,30}(?:day|afternoon)|end.{0,20}(?:day|work)|too late", MORNING,
                "Help must connect late interruptions to the next morning");
            near(t, MORNING, FINISH, "Help must describe finishing the form the next morning");
            break;
        case "no_following":
            near(t, PROBLEM, "no.{0,25}(?:interview|submission|visit).{0,20}(?:follow|next|after)|"
                + "no (?:next|following|later|further|subsequent|other|another) (?:interview|submission|visit)|"
                + "(?:last|final) (?:interview|submission|visit)",
                "Help must include the case with no following interview");
            near(t, "no.{0,35}(?:interview|submission|visit)|(?:last|final) (?:interview|submission|visit)", MORNING,
                "Help must connect no following interview to next morning");
            near(t, MORNING, FINISH, "Help must describe next-morning completion without a following interview");
            break;
        case "duration":
            has(visible, "\\bduration\\b", "Help must name duration");
            near(t, "\\bduration\\b", SCREEN, "Help must define duration as time on screen");
            break;
        case "interval":
            has(visible, "\\bstarttime\\b", "Help must name starttime");
            has(visible, "\\bendtime\\b", "Help must name endtime");
            near(t, "starttime", "first.{0,20}open|initial.{0,20}open|open.{0,20}first|start.{0,15}interview|beginning",
                "Help must explain the start of the elapsed interval");
            near(t, "endtime", "finali[sz]|final.{0,20}(?:complet|clos)|finish|complet",
                "Help must explain the end of the elapsed interval");
            near(t, "starttime", "endtime", "Help must connect the two endpoints");
            near(t, "starttime|endtime", ELAPSED, "Help must describe the elapsed start-to-end interval");
            break;
        case "detection":
            near(t, PROBLEM, "check|compar|detect|flag|identif|spot",
                "Help must describe checking for forms left open");
            near(t, "check|compar|detect|flag|identif|spot", "duration",
                "Help must connect the check to duration");
            near(t, "duration", "starttime", "The check must compare duration with starttime/endtime");
            near(t, "duration", "endtime", "The check must compare duration with both endpoints");
            near(t, "duration", "longer|larger|greater|exceed|shorter|smaller|less than|gap|difference|discrepancy|\\s[><]\\s|compar",
                "Help must explain comparing screen duration and elapsed time");
            break;
        default:
            throw new AssertionError("Unknown help-check area: " + area);
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

    private static void rejects(String text, String area, String why) {
        boolean rejected = false;
        try { check(text, area); }
        catch (AssertionError expected) { rejected = true; }
        require(rejected, "Oracle accepted " + why + " in " + area);
    }

    public static void main(String[] args) {
        Map<String, String[]> examples = new LinkedHashMap<>();
        examples.put("problem", new String[] { "Problem {cmd:leftOpen}:",
            "a form is interrupted,", "another interview is done meanwhile,", "then the first is finished." });
        examples.put("configuration", new String[] { "Configuration key", "{cmd:leftOpen}." });
        examples.put("late_day", new String[] { "A form left open is interrupted",
            "late in the day;", "it is finished", "the next morning." });
        examples.put("no_following", new String[] { "A form left open,",
            "when no interview follows,", "is finished", "the next morning." });
        examples.put("duration", new String[] { "{cmd:duration} measures", "time on the screen." });
        examples.put("interval", new String[] { "The elapsed interval goes from {cmd:starttime},",
            "the first opening,", "to {cmd:endtime},", "the finalisation." });
        examples.put("detection", new String[] { "Detect leftOpen:", "compare duration",
            "with the interval from starttime", "to endtime." });
        int omissions = 0;
        for (Map.Entry<String, String[]> e : examples.entrySet()) {
            String complete = String.join("\n", e.getValue());
            check(complete, e.getKey());
            check("{pstd}{bf:" + complete.replace(" ", "\r\n") + "}{p_end}", e.getKey());
            rejects("", e.getKey(), "empty help");
            rejects("{* " + plain(complete) + "}", e.getKey(), "comment-only help");
            for (int i = 0; i < e.getValue().length; i++) {
                List<String> remaining = new ArrayList<>(Arrays.asList(e.getValue()));
                remaining.remove(i);
                rejects(String.join(" ", remaining), e.getKey(), "omitted clause " + i);
                omissions++;
            }
        }
        check("The leftOpen problem pauses a form. A second interview happens in between; the enumerator returns to complete the first.", "problem");
        check("JSON settings: {cmd:{bf:leftOpen}} controls the rate of interrupted forms.", "configuration");
        check("The form stays open after a pause near the end of work; it resumes the following morning.", "late_day");
        check("A form remains open on the last interview; it resumes at the next day's start of work.", "no_following");
        check("duration is active time, excluding pauses.", "duration");
        check("duration records the time on screen; elapsed time can be longer.", "duration");
        check("The time between starttime (initial opening) and endtime (final completion) includes pauses.", "interval");
        check("Check forms left open: the elapsed endtime - starttime is much longer than duration.", "detection");
        check("Flag leftOpen when duration is smaller than the interval between starttime and endtime.", "detection");
        rejects("leftOpen interrupts a form and finishes it later.", "problem", "missing intervening interview");
        rejects("leftopen interrupts a form, another interview happens meanwhile, then it finishes.", "problem", "wrong identifier case");
        rejects("leftOpen is a problem.", "configuration", "no configuration meaning");
        rejects("Configuration key leftopen.", "configuration", "wrong key case");
        rejects("A form left open is interrupted late in the day and finishes that evening.", "late_day", "same-evening fallback");
        rejects("A form left open when no interview follows is finished immediately.", "no_following", "immediate fallback");
        rejects("A form left open is interrupted late in the day and finishes next morning.", "no_following", "only the late-day case");
        rejects("duration is the entire elapsed time including interruptions.", "duration", "wall time called screen time");
        rejects("The interval from starttime to endtime is elapsed time.", "interval", "undefined endpoints");
        rejects("Detect leftOpen by comparing duration to a fixed threshold.", "detection", "missing elapsed-time comparison");
        rejects("Detect leftOpen by comparing starttime to endtime.", "detection", "missing screen duration");
        rejects("leftOpen: duration is screen time; starttime and endtime delimit elapsed time.", "detection", "no check described");
        System.out.println("Amendment 11 help oracle: 7 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
