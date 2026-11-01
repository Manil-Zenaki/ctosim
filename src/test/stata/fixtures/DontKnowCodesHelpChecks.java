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

/** Independent lexical checks for spec 11 Amendment 13; assumptions M1-M3.
 * Reads only the public help artifact, never command implementation.
 */
public final class DontKnowCodesHelpChecks {
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

    // Locality is a test heuristic, not a specification-imposed sentence length.
    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b
            + ").{0,300}(?:" + a + ")", why);
    }

    private static final String KEY = "\\bdontknowcodes\\b";
    private static final String FAULT = "\\bdontknow\\b";
    private static final String CODE = "\\bcodes?\\b|\\bvalues?\\b";
    private static final String NAMED = "nam(?:e|es|ed|ing)|specif|suppl|provid|configur|list|set|defin";
    private static final String INJECT = "inject|activ|enabl|appl|introduc|generat|simulat|occur";

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "configuration":
            has(visible, "\\bdontKnowCodes\\b", "Help must name the exact dontKnowCodes configuration key");
            near(t, KEY, "config|json|setting|key", "Help must identify dontKnowCodes as configuration");
            break;
        case "unknown_answers":
            near(t, KEY, "don'?t[- ]know|do not know|does not know|unknown answers?",
                "Help must connect dontKnowCodes to don't-know answers");
            near(t, KEY, CODE, "Help must describe answer codes or values");
            break;
        case "refused_answers":
            near(t, KEY, "refus|declin(?:e|ed|es|ing).{0,30}(?:answer|respond)",
                "Help must connect dontKnowCodes to refused answers too");
            near(t, KEY, CODE, "Help must describe answer codes or values");
            break;
        case "user_values":
            near(t, KEY, CODE, "Help must describe values supplied through dontKnowCodes");
            near(t, KEY, "user|\\byou\\b|\\byour\\b|team|survey.{0,15}(?:staff|designer)",
                "Help must identify the user or team supplying the codes");
            near(t, CODE, NAMED, "Help must explain that users name their codes");
            break;
        case "fault_source":
            has(visible, "\\bdontKnow\\b", "Help must name the exact dontKnow fault");
            near(t, FAULT, KEY, "Help must connect the dontKnow fault to dontKnowCodes");
            has(t, FAULT + "[^.!?]{0,100}(?:us(?:e|es|ing)|draw|tak(?:e|es|ing)|select|pick|sampl|sourc)"
                + "[^.!?]{0,100}(?:" + CODE + "|" + KEY + ")|"
                + "(?:" + CODE + "|" + KEY + ")[^.!?]{0,100}(?:used|drawn|taken|selected|picked|sampled)"
                + "[^.!?]{0,100}" + FAULT,
                "Help must explain that dontKnow uses those configured codes");
            break;
        case "activation":
            has(visible, "\\bdontKnow\\b", "Help must name the dontKnow fault whose activation is conditional");
            near(t, FAULT, CODE + "|" + KEY, "Help must connect fault activation to codes");
            // Accept either the necessary-condition statement or its absent-codes equivalent.
            String only = "only (?:when|if|with|after).{0,90}(?:" + CODE + "|" + KEY + "|" + NAMED + ")";
            String requires = "(?:requir|depend|needs?).{0,90}(?:" + CODE + "|" + KEY + ")";
            String absent = "(?:without|absent|omitt|not (?:named|specified|provided|configured)|no codes|empty).{0,100}"
                + "(?:not|never|no |skip|disabl|inactiv)|"
                + "(?:not injected|never injected|not enabled|disabled|skipped).{0,100}"
                + "(?:unless|without|absent|omitt|empty|not (?:named|specified|provided|configured))";
            near(t, FAULT, only + "|" + requires + "|" + absent,
                "Help must state that dontKnow is injected only with named codes");
            near(t, FAULT, INJECT + "|skip|disabl",
                "Help must describe fault injection or activation, not just a code list");
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
        examples.put("configuration", new String[] { "Configuration key", "{cmd:dontKnowCodes}" });
        examples.put("unknown_answers", new String[] { "dontKnowCodes:", "values for", "don't know." });
        examples.put("refused_answers", new String[] { "dontKnowCodes:", "values for", "refused answers." });
        examples.put("user_values", new String[] { "dontKnowCodes:", "the values", "named by", "the user." });
        examples.put("fault_source", new String[] { "The dontKnow fault", "draws from", "dontKnowCodes." });
        examples.put("activation", new String[] { "dontKnow", "is injected", "only when", "codes are named." });
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
        check("JSON settings: {cmd:{bf:dontKnowCodes}}.", "configuration");
        check("dontKnowCodes lists codes meaning \u201cdon\u2019t know\u201d.", "unknown_answers");
        check("dontKnowCodes: codes for a respondent who declined to answer.", "refused_answers");
        check("In dontKnowCodes you supply your team's answer values.", "user_values");
        check("The fault dontKnow selects from the team's codes in dontKnowCodes.", "fault_source");
        check("The codes in dontKnowCodes are used by the dontKnow fault.", "fault_source");
        check("The dontKnow fault is enabled only if you provide dontKnowCodes.", "activation");
        check("Without dontKnowCodes, no dontKnow fault is injected.", "activation");
        check("dontKnow is not injected when dontKnowCodes is omitted.", "activation");
        check("Injection of dontKnow requires user-supplied codes.", "activation");
        check("dontKnow is skipped unless codes are specified.", "activation");
        rejects("Configuration dontknowcodes: values for don't know and refused.", "configuration", "wrong key case");
        rejects("Configuration specialValues: values for don't know and refused.", "configuration", "old key only");
        rejects("dontKnowCodes: codes for refused answers.", "unknown_answers", "refusals alone");
        rejects("dontKnowCodes: codes for don't know answers.", "refused_answers", "unknown answers alone");
        rejects("dontKnowCodes: automatically inferred values.", "user_values", "inferred codes without user input");
        rejects("dontKnow draws codes from specialValues.", "fault_source", "old code source only");
        rejects("dontKnowCodes lists the values the team uses for don't know and refused. The fault is called dontKnow.",
            "fault_source", "nearby team usage without a fault-source relationship");
        rejects("dontknow uses dontKnowCodes.", "fault_source", "wrong fault case");
        rejects("dontKnowCodes lists the team's codes. dontKnow is always injected.", "activation", "unconditional injection");
        rejects("dontKnow is injected when codes are named.", "activation", "sufficient condition alone");
        rejects("dontKnow is injected whether or not codes are named.", "activation", "optional codes");
        rejects("dontKnow uses named codes.", "activation", "source without activation rule");
        rejects("dontKnowCodes {* configuration key}", "configuration", "requirement hidden in comment");
        rejects("irrelevant", "unknown", "unknown area");
        System.out.println("Amendment 13 help oracle: 6 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
