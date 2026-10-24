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

/** Spec 11 Amendment 9 documentation smoke checks, independent of implementation.
 * These check visible prose, not exact sentences or SMCL layout. See questions I1.
 */
public final class FaultyEnumeratorsHelpChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String plain(String text) {
        // Preserve nested visible content, but not SMCL directives or comments.
        text = text.replaceAll("(?s)\\{\\*.*?\\}", " ");
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void has(String text, String regex, String why) {
        require(Pattern.compile(regex).matcher(text).find(), why);
    }

    private static void near(String text, String first, String second, String why) {
        has(text, "(?:" + first + ").{0,200}(?:" + second + ")|(?:" + second
            + ").{0,200}(?:" + first + ")", why);
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "behaviors":
            near(t, "enumerator", "fault|too fast|skip.{0,30}module|don't know",
                "Help must explain that enumerators carry faults");
            has(t, "too fast|too quickly|excessive speed|unusually (fast|quick)|rush(ing|es)?\\b",
                "Help must explain the too-fast enumerator fault");
            has(t, "filter|screening|gate(keeping)? question",
                "Help must identify the filter questions used to skip modules");
            near(t, "\\bno\\b|negative (answer|response)", "skip.{0,40}module|avoid.{0,40}module|bypass.{0,40}module",
                "Help must explain answering No to skip modules");
            near(t, "fewer|reduc(e|es|ed|ing)|under.?report|shorten|smaller",
                "repeat.{0,40}(instance|count)|roster|number of repeats",
                "Help must explain declaring fewer repeat instances / shortening rosters");
            near(t, "don't know|do not know|dontknow", "code|special value",
                "Help must explain abuse of don't-know codes");
            break;
        case "allocation":
            has(t, "enumerator", "Help must identify enumerators as the unit receiving faults");
            has(t, "(one|1|single).{0,45}(per|for each|for every).{0,25}fault|each fault.{0,60}(own|separate|different|distinct) enumerator",
                "Help must explain one enumerator per fault when enough are available");
            has(t, "(at least half|half or more|at least (50\\s*%|50 percent)).{0,100}(clean|fault.free|without faults|no faults)|"
                + "(at most half|no more than half|at most (50\\s*%|50 percent)).{0,100}(faulty|faults)|"
                + "(clean|fault.free).{0,100}(at least half|half or more|at least 50\\s*%)",
                "Help must explain that at least half the enumerators remain clean");
            near(t, "more enumerators|increase.{0,50}enumerator|larger.{0,40}(team|enumerator)|"
                + "(twice|2.{0,5}\\*).{0,40}(fault|number)|six enumerators|6 enumerators",
                "per fault|each fault|every fault|separate.{0,35}fault|distinct.{0,35}fault|one.{0,35}fault",
                "Help must explain increasing enumerators to obtain one per fault");
            break;
        case "outliers":
            near(t, "outlier", "number|numeric|integer|decimal|value|answer",
                "Help must describe outliers in numeric answers");
            near(t, "outlier", "far|extreme|unusual|distan|outlying|abnormal",
                "Help must explain that an outlier is far from ordinary values");
            near(t, "outlier", "constraint", "Help must relate outliers to form constraints");
            near(t, "constraint", "within|satisf|obey|respect|accept|meet|comply|valid|allow",
                "Help must explain that outliers remain accepted by constraints");
            break;
        case "configuration":
            has(t, "config|json|setting", "Help must describe configuration");
            has(visible, "\\boutliers\\b", "Help must name the configuration key outliers");
            has(visible, "\\bfaults\\b", "Help must name the configuration key faults");
            near(t, "\\boutliers\\b", "rate|fraction|proportion|share|probability|percent",
                "Help must explain that outliers controls how often outliers are injected");
            near(t, "\\bfaults\\b", "list|array|choos|select|enable|disable|types?|kinds?|toofast|skipmodules|dontknow",
                "Help must explain that faults chooses the enumerator faults to inject");
            break;
        default:
            throw new AssertionError("Unknown help-check area: " + area);
        }
    }

    // Called through Stata's documented javacall interface. A failed expectation
    // returns nonzero; the do-file rethrows it rather than swallowing the error.
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

    public static void main(String[] args) throws Exception {
        Map<String, String[]> examples = new LinkedHashMap<>();
        examples.put("behaviors", new String[] {
            "Faulty enumerators carry the following behaviors.",
            "One works too fast.",
            "Another answers filter questions.",
            "It says No to skip modules.",
            "It declares fewer repeat instances.",
            "Another abuses don't know codes."
        });
        examples.put("allocation", new String[] {
            "There is one enumerator per fault when possible.",
            "At least half of the team stays clean.",
            "Use more enumerators to obtain a separate worker for each fault."
        });
        examples.put("outliers", new String[] {
            "An outlier is an extreme numeric answer.",
            "It remains within the form's constraints."
        });
        examples.put("configuration", new String[] {
            "Configuration keys:", "{cmd:outliers} sets the injection rate.",
            "{bf:faults} lists the enumerator faults to inject."
        });
        int mutations = 0;
        for (Map.Entry<String, String[]> entry : examples.entrySet()) {
            String area = entry.getKey();
            String[] clauses = entry.getValue();
            String complete = String.join("\n", clauses);
            check(complete, area);
            check("{pstd}{bf:" + complete.replace(" ", "\r\n") + "}{p_end}", area);
            rejects("", area, "empty help");
            rejects("{* " + complete + "}", area, "comment-only documentation");
            for (int i = 0; i < clauses.length; i++) {
                List<String> missing = new ArrayList<>(Arrays.asList(clauses));
                missing.remove(i);
                rejects(String.join(" ", missing), area, "omitted clause " + i);
                mutations++;
            }
        }
        // Equivalent phrasing and Unicode punctuation must not force exact prose.
        check("Faulty enumerators rush interviews. They give negative responses to screening questions "
            + "to bypass modules and under-report roster sizes. They abuse \u201cdon\u2019t know\u201d special values.", "behaviors");
        check("Each fault has its own enumerator when possible; no more than half of enumerators are faulty. "
            + "Increase the number of enumerators for one worker per fault.", "allocation");
        check("Outliers are unusual numbers accepted by the question's constraint.", "outliers");
        check("JSON settings: {cmd:{bf:outliers}} is the fraction of submissions; "
            + "{cmd:faults} selects the behaviors to enable.", "configuration");
        rejects("Outliers are extreme numbers outside the constraints.", "outliers", "constraint violations");
        rejects("An outlier is a numeric answer within its constraints.", "outliers", "ordinary answers only");
        rejects("There is one enumerator per fault. Half the team is faulty. "
            + "Use more enumerators for each fault.", "allocation", "missing clean-half guarantee");
        rejects("Configuration keys: outlier and faulty.", "configuration", "misspelled keys");
        rejects("Configuration supports outliers and faults.", "configuration", "unexplained key names");
        System.out.println("Amendment 9 help oracle: positive variants, " + mutations
            + " clause omissions, empty/comment-only help and semantic counterexamples passed");
    }
}
