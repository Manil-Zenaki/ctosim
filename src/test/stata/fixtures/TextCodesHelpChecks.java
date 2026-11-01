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

/** Spec 11 Amendment 15 public-help checks, independent of production code.
 * Lexical assumptions and limits are recorded in questions O1-O3.
 */
public final class TextCodesHelpChecks {
    private static final String CODE = "\\b(?:codes?|values?|dontknowcodes|refusecodes)\\b";
    private static final String TEXT = "\\b(?:text|string)[- ](?:valued[- ]|typed[- ])?(?:questions?|fields?|variables?|answers?|responses?)\\b";
    private static final String NUMBER = "\\b(?:numbers?|numeric(?:al)?(?:[- ]values?)?)\\b";
    private static final String STRING = "\\b(?:strings?|quoted[- ](?:text|values?|codes?))\\b";
    private static final String MISMATCH = "\\btype[- ]mismatch\\b|\\bincompatible[- ]types?\\b";
    private static final String ALONE = "(?:" + NUMBER + ").{0,45}(?:alone|only)|"
        + "(?:only|just|solely).{0,45}(?:" + NUMBER + ")|numeric[- ]only|without.{0,35}(?:string|quoted)";

    private static void require(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    private static boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static String plain(String raw) {
        // SMCL comments must not satisfy a visible-documentation requirement.
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
        String text = visible.toString();
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[^{}:]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[^{}]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void has(String text, String regex, String why) {
        require(matches(text, regex), why);
    }

    private static void near(String text, String a, String b, String why) {
        has(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b
            + ").{0,300}(?:" + a + ")", why);
    }

    private static void fault(String visible, String name) {
        has(visible, "\\b" + name + "\\b", "Help must name the exact " + name + " fault");
        String t = visible.toLowerCase(Locale.ROOT);
        String identifier = "\\b" + name.toLowerCase(Locale.ROOT) + "\\b";
        boolean found = false;
        for (String sentence : t.split("[.!?]")) {
            if (!matches(sentence, identifier) || !matches(sentence, TEXT)) continue;
            // Avoid a promise limited to numeric questions, or explicitly excluding text.
            if (matches(sentence, "(?:not|never|exclud\\w*|except)[^,;]{0,45}(?:" + TEXT + ")")) continue;
            if (matches(sentence, CODE)
                    && matches(sentence, "team|your|configur|user|dontknowcodes|refusecodes")
                    && matches(sentence, "\\b(?:writ\\w*|us(?:e|es|ing|ed)|insert\\w*|put\\w*|fill\\w*|answer\\w*|record\\w*|stor\\w*)\\b")) found = true;
        }
        require(found, "Help must explain that " + name + " writes the team's codes in text questions too");
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "dont_know": fault(visible, "dontKnow"); break;
        case "refuse": fault(visible, "refuse"); break;
        case "representations":
            has(visible, "\\bipacheckenumdb\\b", "Help must identify ipacheckenumdb as the consumer of both code forms");
            near(t, "\\bipacheckenumdb\\b", CODE, "Help must connect ipacheckenumdb to the supplied codes");
            near(t, "\\bipacheckenumdb\\b", NUMBER, "Help must advise supplying codes as numbers to ipacheckenumdb");
            near(t, "\\bipacheckenumdb\\b", STRING, "Help must advise supplying codes as strings to ipacheckenumdb");
            has(t, "(?:" + NUMBER + ").{0,65}(?:and|as well as|plus).{0,65}(?:" + STRING + ")|"
                + "(?:" + STRING + ").{0,65}(?:and|as well as|plus).{0,65}(?:" + NUMBER + ")",
                "Help must recommend both numeric and string representations, not either one");
            near(t, "\\bipacheckenumdb\\b", "giv|suppl|pass|specif|provid|includ|enter|list",
                "Help must tell users to supply both code forms to ipacheckenumdb");
            break;
        case "example":
            // An illustrative value is free, but the quoted and unquoted code must agree.
            has(visible, "\\bdontknow\\(\\s*([+-]?[0-9]+)\\s*,\\s*\"\\1\"\\s*\\)",
                "Help must illustrate dontknow() with a code as both a number and the same string, e.g. dontknow(-999, \"-999\")");
            break;
        case "number_alone":
            has(visible, "\\bipacheckenumdb\\b", "Help must identify the checking command that fails");
            near(t, "\\bipacheckenumdb\\b", MISMATCH, "Help must explain ipacheckenumdb's type mismatch");
            near(t, ALONE, MISMATCH, "Help must connect numeric-only codes to the type mismatch");
            near(t, MISMATCH, "stop|fail|abort|error|halt", "Help must say the mismatch stops or fails the check");
            break;
        case "text_trigger":
            near(t, MISMATCH, TEXT, "Help must connect the type mismatch to a text variable in the data");
            near(t, TEXT, "as soon as|once|when|if|contain|hold|present|encounter|includ|exist",
                "Help must explain that encountering a text variable triggers the mismatch");
            break;
        default: throw new AssertionError("Unknown Amendment 15 area: " + area);
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
        examples.put("dont_know", new String[] {"dontKnow", "writes", "the team's", "codes in", "text questions too."});
        examples.put("refuse", new String[] {"refuse", "writes", "the team's", "codes in", "text questions too."});
        examples.put("representations", new String[] {"Supply", "ipacheckenumdb", "each code as", "a number", "and", "a string."});
        examples.put("example", new String[] {"{cmd:dontknow(-999, \"-999\")}"});
        examples.put("number_alone", new String[] {"ipacheckenumdb:", "with the number alone", "it stops", "on type mismatch."});
        examples.put("text_trigger", new String[] {"A type mismatch happens", "as soon as the data hold", "a text variable."});
        int omissions = 0;
        StringBuilder completeHelp = new StringBuilder();
        for (Map.Entry<String, String[]> entry : examples.entrySet()) {
            String full = String.join("\n", entry.getValue());
            String area = entry.getKey();
            completeHelp.append(full).append('\n');
            check(full, area);
            check("{pstd}{bf:" + full.replace(" ", "\r\n") + "}{p_end}", area);
            rejects("", area, "empty help");
            rejects("{* " + full + "}", area, "comment-only help with nested markup");
            rejects("Unrelated older documentation.", area, "missing topic");
            for (int i = 0; i < entry.getValue().length; i++) {
                List<String> reduced = new ArrayList<>(Arrays.asList(entry.getValue()));
                reduced.remove(i);
                rejects(String.join(" ", reduced), area, "omitted clause " + i);
                omissions++;
            }
        }
        String combined = "The faults {cmd:dontKnow} and {cmd:refuse} also write your codes in text questions.";
        check(combined, "dont_know");
        check(combined, "refuse");
        check("Text fields are filled with the team's configured values by dontKnow and refuse.", "dont_know");
        check("Text fields are filled with the team's configured values by dontKnow and refuse.", "refuse");
        check("dontKnow uses dontKnowCodes for string variables too.", "dont_know");
        check("refuse records refuseCodes in string fields too.", "refuse");
        check("Give ipacheckenumdb the codes as strings as well as numbers.", "representations");
        check("Provide ipacheckenumdb each code as a numeric value and quoted text.", "representations");
        check("{cmd:{bf:dontknow( -888 , \"-888\" )}}", "example");
        check("dontknow(0, \u201c0\u201d)", "example");
        check("dontknow(999, \"999\")", "example");
        check("Using only numeric codes makes ipacheckenumdb fail with a type-mismatch error.", "number_alone");
        check("ipacheckenumdb aborts with incompatible types without the string form.", "number_alone");
        check("Once a string field is encountered, the command reports a type mismatch.", "text_trigger");
        check("Type mismatch when the data include a text variable.", "text_trigger");
        for (String area : Arrays.asList("dont_know", "refuse")) {
            String name = area.equals("dont_know") ? "dontKnow" : "refuse";
            String other = area.equals("dont_know") ? "refuse" : "dontKnow";
            rejects(name + " writes the team's codes only in numeric questions.", area, "numeric questions only");
            rejects(name + " never writes the team's codes in text questions.", area, "explicit text exclusion");
            rejects(name + " writes codes in numeric questions. " + other
                + " writes the team's codes in text questions.", area, "only the other fault supports text");
            rejects(name + " writes random text in text questions.", area, "arbitrary text instead of team codes");
        }
        rejects("Supply ipacheckenumdb each code as a number or a string.", "representations", "either representation alone");
        rejects("Supply ipacheckenumdb each code as a number.", "representations", "numeric-only guidance");
        rejects("Supply ipacheckenumdb each code as a string.", "representations", "string-only guidance");
        for (String bad : Arrays.asList("dontknow(-999)", "dontknow(\"-999\")", "dontknow(-999, -999)",
                "dontknow(-999, \"-888\")", "dontknow(-999, \"999\")", "dontknow(999, \"-999\")",
                "dontKnow(-999, \"-999\")", "refuse(-999, \"-999\")"))
            rejects(bad, "example", "incorrect example " + bad);
        rejects("With a number alone ipacheckenumdb stops on file not found.", "number_alone", "wrong failure");
        rejects("ipacheckenumdb stops on type mismatch with a string alone.", "number_alone", "wrong representation blamed");
        rejects("A type mismatch happens when the data hold only numeric variables.", "text_trigger", "wrong triggering type");
        rejects("The data hold a text variable and cause file not found.", "text_trigger", "wrong error for text variables");
        for (String area : examples.keySet()) {
            check(completeHelp.toString(), area);
            check(completeHelp.toString().replace("team's", "team\u2019s"), area);
        }
        rejects("irrelevant", "unknown", "unknown area");
        // Optional output is used for independent end-to-end Stata-wrapper probes.
        if (args.length == 1) Files.write(Paths.get(args[0]), completeHelp.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 15 help oracle: 6 areas, " + omissions
            + " clause omissions, SMCL/wording variants and counterexamples passed");
    }
}
