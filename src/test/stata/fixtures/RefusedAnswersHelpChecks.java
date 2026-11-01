import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent spec 11 Amendment 14 help oracle. See assumptions N1-N3.
 * Only the public help artifact is read; no command implementation is used.
 */
public final class RefusedAnswersHelpChecks {
    private static final String KEY = "\\brefusecodes\\b";
    private static final String DK_KEY = "\\bdontknowcodes\\b";
    private static final String FAULT = "\\brefuse\\b";
    private static final String DK_FAULT = "\\bdontknow\\b";
    private static final String CODE = "\\bcodes?\\b|\\bvalues?\\b";
    private static final String UNKNOWN = "don'?t[- ]know|do not know|does not know|unknown answers?";
    private static final String REFUSED = "refus(?:ed|al|als)|declin(?:e|ed|es|ing).{0,30}(?:answer|respond)";
    private static final String NAMED = "nam(?:e|es|ed|ing)|specif|suppl|provid|configur|list|set|defin";

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String plain(String text) {
        // Remove even nested comment content before exposing visible SMCL text.
        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < text.length();) {
            if (text.startsWith("{*", i)) {
                int depth = 1;
                i += 2;
                while (i < text.length() && depth > 0) {
                    char c = text.charAt(i++);
                    if (c == '{') depth++;
                    if (c == '}') depth--;
                }
                visible.append(' ');
            } else visible.append(text.charAt(i++));
        }
        text = visible.toString();
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static void has(String text, String regex, String why) {
        require(matches(text, regex), why);
    }

    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b
            + ").{0,300}(?:" + a + ")", why);
    }

    private static void noCombinedCodes(String t) {
        // Check every definition, including obsolete prose elsewhere in otherwise valid help.
        // Separate the two keys so a neighbouring refuseCodes definition is not a contradiction.
        for (String sentence : t.split("[.!?;]")) {
            Matcher keys = Pattern.compile(DK_KEY).matcher(sentence);
            while (keys.find()) {
                String after = sentence.substring(keys.end()).split(KEY + "|" + DK_FAULT + "|" + FAULT, 2)[0];
                String positive = after.replaceAll(
                    "(?:not(?!\\s+know)|never|(?:don't|doesn't)\\s+(?:hold|contain|include|store|list|accept)|"
                    + "no longer|exclud\\w*|rather than|instead of)[^,;.!?]{0,120}(?:" + REFUSED + ")", " ");
                String before = sentence.substring(0, keys.start());
                boolean backwardsClaim = matches(before, "(?:" + REFUSED + ")[^.!?]{0,100}"
                    + "(?:included|held|stored|listed|configured|specified|named|supplied|provided)[^.!?]{0,40}"
                    + "(?:in|under|by|through|with)\\s*$")
                    && !matches(before, "not|never|no longer|exclud");
                require(!matches(positive, REFUSED) && !backwardsClaim,
                    "Help must not describe dontKnowCodes as holding refused-answer codes (Amendment 13 is superseded)");
            }
        }
    }

    private static void detection(String visible, String t, boolean refusal) {
        String fault = refusal ? FAULT : DK_FAULT;
        String other = refusal ? DK_FAULT : FAULT;
        String label = refusal ? "refuse" : "dontKnow";
        String meaning = refusal ? "refus(?:al|ed)(?:[- ](?:answer|response)s?)?" : UNKNOWN;
        String rate = "(?:" + meaning + ")[^.!?]{0,35}(?:rate|proportion|fraction|percentage)|"
            + "(?:rate|proportion|fraction|percentage)[^.!?]{0,35}(?:" + meaning + ")";
        has(visible, "\\b" + label + "\\b", "Help must name the exact " + label + " fault");
        has(visible, "\\bipacheckenumdb\\b", "Help must name ipacheckenumdb for detecting " + label);
        // A nearby description of the other fault must not supply this fault's rate.
        String gap = "(?:(?!" + other + ")[^.!?]){0,180}";
        has(t, fault + gap + "(?:" + rate + ")|(?:" + rate + ")" + gap + fault,
            "Help must connect " + label + " to its " + (refusal ? "refusal" : "don't-know") + " rate");
        near(t, fault, "\\bipacheckenumdb\\b", "Help must connect " + label + " detection to ipacheckenumdb");
        near(t, "\\bipacheckenumdb\\b", rate, "Help must describe the rate checked by ipacheckenumdb");
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "dont_know_scope":
            has(visible, "\\bdontKnowCodes\\b", "Help must retain the exact dontKnowCodes key");
            near(t, DK_KEY, UNKNOWN, "Help must define dontKnowCodes for the team's don't-know answers");
            near(t, DK_KEY, CODE, "Help must describe dontKnowCodes as codes or values");
            near(t, DK_KEY, "team|\\byour\\b|user|survey.{0,15}(?:staff|designer)",
                "Help must identify the team's don't-know codes");
            noCombinedCodes(t);
            break;
        case "no_combined_codes":
            has(visible, "\\bdontKnowCodes\\b", "Help must document dontKnowCodes, not simply remove the key");
            noCombinedCodes(t);
            break;
        case "configuration":
            has(visible, "\\brefuseCodes\\b", "Help must name the exact refuseCodes configuration key");
            near(t, KEY, "config|json|setting|key", "Help must identify refuseCodes as configuration");
            break;
        case "refused_answers":
            near(t, KEY, REFUSED, "Help must define refuseCodes for refused-to-answer responses");
            near(t, KEY, "answer|respond|response", "Help must describe refusal to answer");
            near(t, KEY, CODE, "Help must describe refused-answer codes or values");
            break;
        case "user_values":
            near(t, KEY, CODE, "Help must describe values supplied through refuseCodes");
            near(t, KEY, "user|\\byou\\b|\\byour\\b|team|survey.{0,15}(?:staff|designer)",
                "Help must identify the user or team supplying refuseCodes");
            near(t, KEY, NAMED, "Help must explain that users name their refused-answer codes");
            break;
        case "fault_source":
            has(visible, "\\brefuse\\b", "Help must name the exact refuse fault");
            near(t, FAULT, KEY, "Help must connect refuse to refuseCodes");
            near(t, FAULT, "enumerator|interviewer", "Help must explain the enumerator's faulty answers");
            has(t, FAULT + "[^.!?]{0,100}(?:us(?:e|es|ing)|draw|tak(?:e|es|ing)|select|pick|sampl|answer|respond)"
                + "[^.!?]{0,100}(?:" + CODE + "|" + KEY + ")|"
                + "(?:" + CODE + "|" + KEY + ")[^.!?]{0,100}(?:used|drawn|taken|selected|picked|sampled|answered)"
                + "[^.!?]{0,100}" + FAULT,
                "Help must explain that refuse makes the enumerator answer with those codes");
            break;
        case "activation":
            has(visible, "\\brefuse\\b", "Help must name the refuse fault whose injection is conditional");
            near(t, FAULT, CODE + "|" + KEY, "Help must connect fault activation to refused-answer codes");
            String only = "only (?:when|if|with|after).{0,90}(?:" + CODE + "|" + KEY + "|" + NAMED + ")";
            String requires = "(?:requir|depend|needs?).{0,90}(?:" + CODE + "|" + KEY + ")";
            String absent = "(?:without|absent|omitt|not (?:named|specified|provided|configured)|no codes|empty).{0,100}"
                + "(?:not|never|no |skip|disabl|inactiv)|"
                + "(?:not injected|never injected|not enabled|disabled|skipped).{0,100}"
                + "(?:unless|without|absent|omitt|empty|not (?:named|specified|provided|configured))";
            near(t, FAULT, only + "|" + requires + "|" + absent,
                "Help must state that refuse is injected only when codes are named");
            near(t, FAULT, "inject|activ|enabl|appl|introduc|generat|simulat|occur|skip|disabl",
                "Help must describe fault injection or activation");
            break;
        case "refusal_detection": detection(visible, t, true); break;
        case "dont_know_detection": detection(visible, t, false); break;
        default: throw new AssertionError("Unknown help-check area: " + area);
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

    public static void main(String[] args) throws Exception {
        Map<String, String[]> examples = new LinkedHashMap<>();
        examples.put("dont_know_scope", new String[] { "dontKnowCodes:", "the team's", "codes for", "don't know only." });
        examples.put("configuration", new String[] { "Configuration key", "{cmd:refuseCodes}." });
        examples.put("refused_answers", new String[] { "refuseCodes:", "values for", "refused", "answers." });
        examples.put("user_values", new String[] { "refuseCodes:", "the values", "named by", "the user." });
        examples.put("fault_source", new String[] { "The refuse fault", "makes the enumerator", "answer with", "refuseCodes." });
        examples.put("activation", new String[] { "refuse", "is injected", "only when", "codes are named." });
        examples.put("refusal_detection", new String[] { "refuse", "is found by", "ipacheckenumdb's", "refusal", "rate." });
        examples.put("dont_know_detection", new String[] { "dontKnow", "is found by", "ipacheckenumdb's", "don't know", "rate." });
        int omissions = 0;
        StringBuilder completeHelp = new StringBuilder();
        for (Map.Entry<String, String[]> e : examples.entrySet()) {
            String complete = String.join("\n", e.getValue());
            completeHelp.append(complete).append('\n');
            check(complete, e.getKey());
            check("{pstd}{bf:" + complete.replace(" ", "\r\n") + "}{p_end}", e.getKey());
            rejects("", e.getKey(), "empty help");
            rejects("{* " + complete + "}", e.getKey(), "comment-only help, including nested SMCL");
            for (int i = 0; i < e.getValue().length; i++) {
                // Detection vocabulary need not use a particular verb (e.g. table rows are valid).
                if (e.getKey().endsWith("detection") && i == 1) continue;
                List<String> remaining = new ArrayList<>(Arrays.asList(e.getValue()));
                remaining.remove(i);
                rejects(String.join(" ", remaining), e.getKey(), "omitted clause " + i);
                omissions++;
            }
        }
        check("dontKnowCodes: your codes for \u201cdon\u2019t know\u201d. refuseCodes: refused answers.", "dont_know_scope");
        check("refuseCodes holds refused codes, while dontKnowCodes holds your don't-know codes only.", "dont_know_scope");
        check("dontKnowCodes holds your don't-know codes only, while refuseCodes holds refused codes.", "dont_know_scope");
        check("dontKnowCodes: your don't-know values, not refused answers.", "dont_know_scope");
        check("dontKnowCodes no longer holds refused codes; it holds your don't know values.", "no_combined_codes");
        check("JSON settings: {cmd:{bf:refuseCodes}}.", "configuration");
        check("refuseCodes: codes for a respondent who declines to answer.", "refused_answers");
        check("In refuseCodes you supply your team's answer values.", "user_values");
        check("The enumerator's fault refuse selects from refuseCodes.", "fault_source");
        check("The codes in refuseCodes are used by an enumerator with the refuse fault.", "fault_source");
        check("The refuse fault is enabled only if you provide refuseCodes.", "activation");
        check("Without refuseCodes, no refuse fault is injected.", "activation");
        check("refuse is not injected when refuseCodes is omitted.", "activation");
        check("Injection of refuse requires user-supplied codes.", "activation");
        check("refuse is skipped unless codes are specified.", "activation");
        String both = "refuse is found by the refusal rate of ipacheckenumdb, as dontKnow is by its don't know rate.";
        check(both, "refusal_detection");
        check(both, "dont_know_detection");
        check("ipacheckenumdb: the percentage of refused answers identifies refuse.", "refusal_detection");
        check("ipacheckenumdb: the proportion of don't-know answers identifies dontKnow.", "dont_know_detection");
        rejects("Configuration refusecodes.", "configuration", "wrong key case");
        rejects("Configuration dontKnowCodes: refused answers.", "configuration", "old key only");
        rejects("refuseCodes: codes for don't know answers.", "refused_answers", "wrong answer meaning");
        rejects("refuseCodes: automatically inferred values.", "user_values", "inferred codes without user input");
        rejects("The enumerator's refuse fault draws codes from dontKnowCodes.", "fault_source", "wrong code source");
        rejects("refuseCodes lists the codes the team uses. The enumerator's fault is called refuse.",
            "fault_source", "team usage without fault-source relationship");
        rejects("refuseCodes lists the team's codes. refuse is always injected.", "activation", "unconditional injection");
        rejects("refuse is injected when codes are named.", "activation", "sufficient condition alone");
        rejects("refuse is injected whether or not codes are named.", "activation", "optional codes");
        rejects("refuse uses named codes.", "activation", "source without activation rule");
        rejects("fakeRefusal is found by the refusal rate of ipacheckenumdb.", "refusal_detection", "wrong fault");
        rejects("refuse is found by the consent rate of ipacheckenumdb.", "refusal_detection", "consent instead of answer refusal");
        String swapped = "refuse is found by ipacheckenumdb's don't know rate. dontKnow is found by ipacheckenumdb's refusal rate.";
        rejects(swapped, "refusal_detection", "swapped rates");
        rejects(swapped, "dont_know_detection", "swapped rates");
        for (String old : Arrays.asList(
                "dontKnowCodes holds the team's don't know and refused codes.",
                "dontKnowCodes: values for refused answers.",
                "Refused-answer codes are included in dontKnowCodes.")) {
            rejects(old, "no_combined_codes", "obsolete combined definition: " + old);
            rejects(completeHelp + "\n" + old, "no_combined_codes", "contradiction after valid documentation");
        }
        check(completeHelp.toString(), "no_combined_codes");
        rejects("", "no_combined_codes", "missing documentation");
        rejects("{* {cmd:dontKnowCodes} includes refused answers}", "no_combined_codes", "comment-only help");
        rejects("irrelevant", "unknown", "unknown area");
        // Optional synthetic fixture output supports testing the actual Stata wrappers independently.
        if (args.length == 1) Files.write(Paths.get(args[0]), completeHelp.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 14 help oracle: 9 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
