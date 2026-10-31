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

/** Spec 11 Amendment 12 only; independent lexical help oracle, questions L1-L3.
 * help() reads the public help artifact, never the command implementation.
 */
public final class VillagesHelpChecks {
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

    // A lexical locality heuristic, not a spec-imposed prose length limit.
    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b
            + ").{0,300}(?:" + a + ")", why);
    }

    private static final String ENUM = "enumerator|interviewer|fieldworker|field worker";
    private static final String VILLAGE = "\\bvillages?\\b";
    private static final String VISIT = "interview|submission|visit";
    private static final String COORD = "coordinates?|latitude.{0,60}longitude|longitude.{0,60}latitude";
    private static final String GAP = "time.{0,35}(?:between|gap|available)|(?:elapsed|available).{0,20}time|"
        + "gap.{0,20}(?:time|interview|visit)|interval|minutes between";
    private static final String SUCCESSIVE = "successive|consecutive|successi(?:on|ve)|previous|preceding|"
        + "earlier.{0,25}(?:interview|visit)|next.{0,25}(?:interview|visit)";
    private static final String DISTANCE = "distance|kilomet|\\bkm\\b|how far";
    private static final String CHECK = "check|detect|flag|identif|spot|compar";

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "daily_village":
            near(t, ENUM, "(?:one|same|single|1).{0,15}village",
                "Help must describe one declared village per enumerator");
            near(t, "(?:one|same|single|1).{0,15}village", "\\bday\\b|daily",
                "Help must scope the declared village to a day");
            near(t, VILLAGE, "declar|report|answer|record|village question",
                "Help must explain that the village is declared in the answers");
            break;
        case "relocation":
            near(t, VILLAGE, "move|switch|relocat|goes|go to",
                "Help must explain moving to another village");
            near(t, "households?", "none left|no (?:more |remaining )?household|exhaust|run(?:s)? out|"
                + "used up|all.{0,35}(?:visited|received|interviewed)|no longer.{0,15}(?:free|available|viable)",
                "Help must explain that the current village's households are exhausted");
            near(t, VILLAGE, "nearest|closest", "Help must describe the nearest eligible destination");
            near(t, "nearest|closest", "(?:free|available|remaining|unvisited|unused|uninterviewed).{0,20}households?|"
                + "households?.{0,20}(?:free|available|remaining|left|unvisited)",
                "Help must say the destination still has free households");
            break;
        case "configuration":
            has(visible, "\\bvillages\\b", "Help must name the exact villages configuration key");
            near(t, "\\bvillages\\b", "config|json|setting|key", "Help must identify villages as configuration");
            near(t, "\\bvillages\\b", "latitude", "Help must document the latitude column");
            near(t, "\\bvillages\\b", "longitude", "Help must document the longitude column");
            near(t, "latitude|longitude", "columns?|headers?", "Help must describe column names, not coordinate constants");
            near(t, VILLAGE, "preload|attached.{0,25}(?:list|csv)|village list.{0,25}(?:csv|file)",
                "Help must identify the preloaded village list as the source");
            near(t, "columns?|headers?", "user|you|specif|nam(?:e|es|ed|ing)|choos|suppl",
                "Help must explain that users name the coordinate columns");
            break;
        case "coordinates":
            near(t, VILLAGE, COORD, "Help must identify the villages' coordinates");
            near(t, VISIT, "take(?:s)? place|occur|locat|position|place[ds]?|conduct|held|happen",
                "Help must explain where the interviews take place");
            near(t, VISIT, COORD, "Help must connect interview locations to village coordinates");
            break;
        case "travel":
            has(visible, "\\btravel\\b", "Help must name the travel problem");
            near(t, "\\btravel\\b", VILLAGE, "Help must explain travel through declared villages");
            near(t, VILLAGE, "declar|report|answer|record", "Help must describe the declared village");
            near(t, VILLAGE, "too far|too distant|unreach|impossible|cannot.{0,15}reach|can't.{0,15}reach|implausib",
                "Help must explain an implausibly distant village");
            near(t, VILLAGE, SUCCESSIVE, "Help must connect travel to the previous interview's village");
            near(t, "\\btravel\\b", GAP, "Help must explain that the available time is insufficient");
            break;
        case "no_gps":
            near(t, "\\btravel\\b", VILLAGE, "Help must connect travel without GPS to villages");
            near(t, "\\btravel\\b", "without.{0,35}(?:gps|location questions?|geopoint)|"
                + "no.{0,20}(?:gps|location questions?|geopoint)|"
                + "(?:gps|location questions?|geopoint).{0,25}(?:not required|not needed|unnecessary|absent)|"
                + "whether or not.{0,35}(?:gps|location questions?|geopoint)",
                "Help must explicitly explain that travel can be seen without GPS");
            break;
        case "detection":
            near(t, "\\btravel\\b", CHECK, "Help must describe checking for travel problems");
            near(t, CHECK, DISTANCE, "Help must compare distance for the travel check");
            near(t, DISTANCE, COORD, "Help must derive distance from coordinates");
            near(t, COORD, VILLAGE, "Help must use village coordinates for the check");
            near(t, DISTANCE, SUCCESSIVE, "Help must compare successive interviews");
            near(t, SUCCESSIVE, ENUM, "Help must scope successive interviews to an enumerator");
            near(t, DISTANCE, GAP, "Help must compare village distance with time between interviews");
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
        examples.put("daily_village", new String[] { "Each enumerator", "declares", "one village", "a day." });
        examples.put("relocation", new String[] { "The enumerator moves to another village",
            "when its households are exhausted;", "it chooses the nearest", "with free households." });
        examples.put("configuration", new String[] { "Configuration key", "{cmd:villages}",
            "uses latitude", "and longitude", "columns", "of the preloaded list,", "named by the user." });
        examples.put("coordinates", new String[] { "Interviews", "take place at", "the villages'", "coordinates." });
        examples.put("travel", new String[] { "Problem {cmd:travel}:", "an interview declared in a village",
            "too far", "from the previous interview", "for the time between them." });
        examples.put("no_gps", new String[] { "{cmd:travel}", "through villages", "can be seen without GPS." });
        examples.put("detection", new String[] { "Check travel:", "compare distance between",
            "coordinates of the villages", "of successive interviews", "of an enumerator",
            "with the time between them." });
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
        check("An interviewer reports the same village throughout each working day.", "daily_village");
        check("When all households have been visited, the enumerator switches village to the closest with households remaining.", "relocation");
        check("In JSON settings, {cmd:{bf:villages}} specifies your latitude and longitude headers in the attached village CSV list.", "configuration");
        check("Visits occur near the village\u2019s latitude and longitude.", "coordinates");
        check("travel: the reported village is unreachable from the preceding visit in the available time.", "travel");
        check("Village travel is detectable whether or not the form has geopoint questions.", "no_gps");
        check("For travel, compare the interval and distance using village coordinates for an interviewer's consecutive visits.", "detection");
        check("travel is detected from villages; GPS is not required.", "no_gps");
        rejects("Each enumerator works in one village a day.", "daily_village", "no declared-answer meaning");
        rejects("The enumerator moves to the nearest village with free households.", "relocation", "no exhaustion trigger");
        rejects("When households are exhausted, move to a random village with free households.", "relocation", "random destination");
        rejects("When households are exhausted, move to the nearest village.", "relocation", "no eligible-household condition");
        rejects("Configuration villages: latitude and longitude values come from the preloaded list, specified by the user.", "configuration", "constants rather than columns");
        rejects("Configuration villages: latitude and longitude columns named by the user.", "configuration", "no preloaded list");
        rejects("The preloaded villages have coordinates.", "coordinates", "no interview-location consequence");
        rejects("travel: a declared village too far from the previous interview.", "travel", "no time-gap meaning");
        rejects("travel without GPS is impossible.", "no_gps", "no village-based alternative");
        rejects("travel through villages uses GPS.", "no_gps", "GPS independence absent");
        rejects("Check travel by comparing distance between GPS coordinates of an enumerator's successive interviews with the time between them.", "detection", "GPS coordinates only");
        rejects("Check travel by comparing distance between village coordinates of successive interviews with time between them.", "detection", "no enumerator grouping");
        rejects("Check travel by comparing distance between village coordinates of an enumerator's interviews with the time between them.", "detection", "no successive-interview requirement");
        rejects("Check travel by comparing distance between village coordinates of an enumerator's successive interviews to a fixed limit.", "detection", "no time comparison");
        rejects("Check travel: compare village coordinates of an enumerator's successive interviews with the time between them.", "detection", "no distance calculation");
        rejects("irrelevant", "unknown", "unknown area");
        System.out.println("Amendment 12 help oracle: 7 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
