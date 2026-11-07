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

/** Spec 11 Amendment 16, derived from the spec, never command implementation.
 * Lexical assumptions and limitations are recorded in questions P1-P3.
 */
public final class OtherAbuseHelpChecks {
    private static final String KEY = "\\bothercodes\\b";
    private static final String RATE = "\\botherrate\\b";
    private static final String CODE = "\\b(?:codes?|values?|othercodes)\\b";
    private static final String PERSON = "\\b(?:enumerators?|interviewers?)\\b";
    private static final String OTHER = "[\"']?\\bother\\b[\"']?";
    private static final String FAULT = "\\b(?:fault|problem|abuse|misuse)\\s+(?:called\\s+|named\\s+)?"
        + OTHER + "|" + OTHER + "\\s+(?:fault|problem|abuse|misuse)\\b";
    private static final String SELECT = "\\b(?:tick\\w*|select\\w*|choos\\w*|pick\\w*|mark\\w*|chos\\w*)\\s+"
        + "(?:the\\s+)?" + OTHER;
    private static final String NAMED = "\\b(?:nam\\w*|specif\\w*|suppl\\w*|provid\\w*|configur\\w*|list\\w*|defin\\w*|set)\\b";
    private static final String SPECIFY = "\\bspecify\\b|write[- ]in|other[- ]specify";
    private static final String OFFERED = "\\b(?:offered|listed|available|existing|predefined|prelisted|provided)\\s+"
        + "(?:(?:answer|response)\\s+)?(?:choices?|options?|answers?|categories|responses?)\\b|"
        + "\\b(?:choices?|options?|answers?|responses?)\\s+(?:already\\s+)?(?:offered|listed|available|provided)\\b";

    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    private static boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static String plain(String raw) {
        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            if (raw.startsWith("{*", i)) {
                int depth = 1;
                i += 2;
                while (i < raw.length() && depth > 0) {
                    char c = raw.charAt(i++);
                    if (c == '{') depth++;
                    if (c == '}') depth--;
                }
                visible.append(' ');
            } else visible.append(raw.charAt(i++));
        }
        String t = visible.toString();
        for (int i = 0; i < 12; i++) t = t.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return t.replaceAll("\\{[^{}]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void has(String text, String regex, String why) {
        require(matches(text, regex), why);
    }

    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b + ").{0,300}(?:" + a + ")", why);
    }

    private static void sentence(String text, String why, String... clauses) {
        for (String s : text.split("(?<=[.!?])\\s+")) {
            boolean ok = true;
            for (String clause : clauses) ok &= matches(s, clause);
            if (ok) return;
        }
        throw new AssertionError(why);
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "configuration":
            has(visible, "\\botherCodes\\b", "Help must name the otherCodes configuration key");
            near(t, KEY, "config|json|setting|key", "Help must identify otherCodes as configuration");
            break;
        case "codes_meaning":
            sentence(t, "Help must define otherCodes as values used for Other answers", KEY, "\\b(?:codes?|values?)\\b", OTHER);
            break;
        case "user_values":
            sentence(t, "Help must say users name their team's Other codes", KEY, CODE,
                "\\b(?:users?|you|your|team)\\b|survey (?:staff|designer)", NAMED);
            break;
        case "rate":
            has(visible, "\\botherRate\\b", "Help must name the otherRate configuration key");
            sentence(t, "Help must define otherRate as how often honest enumerators choose Other",
                RATE, "how often|frequency|probability|rate|proportion|fraction|percentage|chance",
                "\\b(?:honest|clean|nonfaulty|non-faulty)\\b|not faulty|without faults",
                PERSON, SELECT);
            break;
        case "default":
            // A proportion or equivalent percentage may describe the same default.
            sentence(t, "Help must give otherRate's default as 0.03 (3 percent)", RATE,
                "\\bdefault\\w*\\b", "(?<![\\d.])0?\\.03(?:0*)\\b(?!\\s*(?:%|percent))|"
                    + "(?<![\\d.])3(?:\\.0+)?\\s*(?:%|percent\\b|per cent\\b)");
            break;
        case "selection":
            sentence(t, "Help must explain that the other fault makes the enumerator tick Other",
                FAULT, PERSON, SELECT);
            break;
        case "specify":
            sentence(t, "Help must explain that the other fault types an offered choice in the specify question",
                FAULT, SPECIFY, OFFERED, "\\b(?:typ\\w*|writ\\w*|enter\\w*|cop\\w*|record\\w*|fill\\w*|put\\w*)\\b");
            break;
        case "activation":
            String condition = "only\\s+(?:when|if|with|after)\\b.{0,100}(?:" + CODE + ")|"
                + "\\b(?:requires?|needs?|depends? on)\\b.{0,100}(?:" + CODE + ")|"
                + "(?:without|unless|no|absent|omitted|empty).{0,70}(?:" + CODE + ").{0,100}"
                + "(?:not|never|no |skip|disabl|inactiv)|"
                + "(?:not injected|never injected|disabled|skipped|not enabled).{0,100}"
                + "(?:unless|without|absent|omitted|empty).{0,70}(?:" + CODE + ")";
            sentence(t, "Help must state that other is injected only when the codes are named", FAULT,
                CODE, condition, "inject|activ|enabl|appl|introduc|simulat|occur|skip|disabl");
            break;
        case "detection":
            has(visible, "\\bipacheckspecify\\b", "Help must name ipacheckspecify for detecting Other abuse");
            near(t, "\\bipacheckspecify\\b", FAULT,
                "Help must connect ipacheckspecify to detecting the other fault");
            near(t, "\\bipacheckspecify\\b", "detect|find|found|check|identif|reveal|spot|inspect",
                "Help must explain the checking purpose of ipacheckspecify");
            break;
        case "listing":
            has(visible, "\\bipacheckspecify\\b", "Help must name ipacheckspecify for the specify-text listing");
            sentence(t, "Help must say ipacheckspecify lists specify texts by enumerator", "\\bipacheckspecify\\b",
                "\\b(?:list\\w*|show\\w*|display\\w*|tabulat\\w*|group\\w*|report\\w*)\\b", SPECIFY,
                "\\b(?:texts?|answers?|responses?|entries|strings?)\\b",
                "(?:by|per|for each|grouped by|broken down by)\\s+(?:the\\s+)?" + PERSON);
            break;
        default: throw new AssertionError("Unknown Amendment 16 area: " + area);
        }
    }

    public static int help(String[] args) throws Exception {
        try {
            check(new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8), args[1]);
            return 0;
        } catch (AssertionError failure) {
            Files.write(Paths.get(args[2]), (failure.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
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
        examples.put("configuration", new String[] {"Configuration key", "otherCodes."});
        examples.put("codes_meaning", new String[] {"otherCodes", "holds values for", "Other."});
        examples.put("user_values", new String[] {"Users", "name", "values in otherCodes."});
        examples.put("rate", new String[] {"otherRate is the probability that", "honest", "enumerators", "choose Other."});
        examples.put("default", new String[] {"otherRate", "defaults to", "0.03."});
        examples.put("selection", new String[] {"The other fault makes", "the enumerator", "tick Other."});
        examples.put("specify", new String[] {"The other fault", "types", "an offered choice", "in the specify question."});
        examples.put("activation", new String[] {"The other fault", "is injected", "only if", "codes are named."});
        examples.put("detection", new String[] {"Find", "the other fault", "with ipacheckspecify."});
        examples.put("listing", new String[] {"ipacheckspecify", "lists", "specify", "texts", "by enumerator."});
        int omissions = 0;
        StringBuilder fullHelp = new StringBuilder();
        for (Map.Entry<String, String[]> e : examples.entrySet()) {
            String full = String.join(" ", e.getValue());
            String area = e.getKey();
            fullHelp.append(full).append('\n');
            check(full, area);
            check("{pstd}{bf:" + full.replace(" ", "\r\n") + "}{p_end}", area);
            rejects("", area, "empty help");
            rejects("{* {bf:" + full + "}}", area, "comment-only help");
            rejects("Old unrelated documentation.", area, "missing topic");
            for (int i = 0; i < e.getValue().length; i++) {
                List<String> reduced = new ArrayList<>(Arrays.asList(e.getValue()));
                reduced.remove(i);
                rejects(String.join(" ", reduced), area, "omitted clause " + i);
                omissions++;
            }
        }
        String alternative = "The JSON setting {cmd:otherCodes} holds your team's values for \u201cOther\u201d; you supply them. "
            + "The configuration key {cmd:otherRate} controls how often clean interviewers select \u201cOther\u201d; its default is 3%. "
            + "With the fault {cmd:other}, an interviewer picks Other and writes a listed option in the write-in field. "
            + "Without otherCodes, the other fault is not injected. "
            + "The other fault can be detected using {cmd:ipacheckspecify}, which displays specify answers per interviewer.";
        for (String area : examples.keySet()) {
            check(fullHelp.toString(), area);
            check(alternative, area);
        }
        check("otherRate has a default of .03.", "default");
        check("otherRate: default 3 percent.", "default");
        check("otherRate defaults to 0.0300.", "default");
        check("Injection of the other fault requires configured codes.", "activation");
        check("The other fault is disabled unless otherCodes are supplied.", "activation");
        check("An offered response is entered in the specify field by the other fault.", "specify");
        check("The fault called other makes enumerators choose 'Other'.", "selection");
        rejects("Configuration key othercodes.", "configuration", "incorrect identifier case");
        rejects("otherRate controls how often faulty enumerators choose Other.", "rate", "faulty instead of honest rate");
        rejects("otherRate controls how often honest enumerators refuse to answer.", "rate", "wrong answer rate");
        rejects("missingRate is how often honest enumerators choose Other.", "rate", "wrong configuration key");
        for (String bad : Arrays.asList("0.3", "0.003", "0.04", "3", "30%", "0.03%", "13%", "103%"))
            rejects("otherRate defaults to " + bad + ".", "default", "wrong default " + bad);
        rejects("otherRate may be set to 0.03.", "default", "example mistaken for default");
        rejects("The refuse fault makes the enumerator tick Other.", "selection", "wrong fault");
        rejects("The other fault makes the enumerator tick Yes.", "selection", "wrong selected answer");
        rejects("The other fault types arbitrary text in the specify question.", "specify", "unoffered answer");
        rejects("The other fault types an offered choice in the name question.", "specify", "wrong destination");
        rejects("The refuse fault types an offered choice in the specify question.", "specify", "wrong fault");
        rejects("The other fault is injected when codes are named.", "activation", "sufficient but not necessary condition");
        rejects("The other fault is always injected, whether codes are named or not.", "activation", "unconditional injection");
        rejects("The other fault is injected only if problems is enabled.", "activation", "wrong prerequisite");
        rejects("The other fault is injected. The refuse fault is injected only if codes are named.", "activation", "condition belongs to another fault");
        rejects("Find the other fault with ipacheckenumdb.", "detection", "wrong checking command");
        rejects("Find the refuse fault with ipacheckspecify.", "detection", "wrong detected fault");
        rejects("ipacheckspecify lists specify texts by village.", "listing", "wrong grouping");
        rejects("ipacheckspecify lists specify texts for all interviews combined.", "listing", "pooled texts");
        rejects("ipacheckspecify lists specify counts by enumerator.", "listing", "counts instead of texts");
        rejects("ipacheckenumdb lists specify texts by enumerator.", "listing", "wrong listing command");
        rejects("irrelevant", "unknown", "unknown area");
        if (args.length == 1) Files.write(Paths.get(args[0]), fullHelp.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 16 help oracle: 10 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
