import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent lexical checks of spec 11 Amendment 17; assumptions Q1-Q3. */
public final class HonestSpecifyHelpChecks {
    private static final String SPECIFY = "\\bspecify\\b|\\bwrite[- ]in\\b|\\w*other_specify\\w*";
    private static final String HONEST = "\\b(?:honest|clean|nonfaulty|non-faulty)\\b";
    private static final String PERSON = "\\b(?:enumerators?|interviewers?)\\b";
    private static final String DEFAULT = "\\bdefault\\w*\\b|without (?:more|an? override|customization|configuration|settings)|unless.{0,60}(?:configured|overridden)";
    private static final String STRING = "\"(?:[^\"\\\\]|\\\\.)*\"";

    private static boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    // Strip only SMCL directives, retaining literal JSON braces and quoted keys.
    private static String plain(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            if (raw.startsWith("{*", i)) {
                int depth = 1;
                i += 2;
                while (i < raw.length() && depth > 0) {
                    char c = raw.charAt(i++);
                    if (c == '{') depth++;
                    if (c == '}') depth--;
                }
                out.append(' ');
            } else out.append(raw.charAt(i++));
        }
        String text = out.toString();
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[a-zA-Z][a-zA-Z0-9_ ]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[a-zA-Z][a-zA-Z0-9_ ]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void sentence(String text, String why, String... clauses) {
        for (String s : text.split("(?<=[.!?])\\s+")) {
            boolean ok = true;
            for (String clause : clauses) ok &= matches(s, clause);
            if (ok) return;
        }
        throw new AssertionError(why);
    }

    private static void near(String text, String a, String b, String why) {
        require(matches(text, "(?:" + a + ").{0,300}(?:" + b + ")|(?:" + b
            + ").{0,300}(?:" + a + ")"), why);
    }

    private static void example(String visible) {
        // Accept a field-override fragment as well as a complete JSON config.
        Pattern field = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{[^{}]*?\"texts\"\\s*:\\s*\\[([^\\]]*)\\]");
        Matcher m = field.matcher(visible);
        while (m.find()) {
            String name = m.group(1);
            String values = m.group(2);
            if (!matches(values, "^\\s*" + STRING + "(?:\\s*,\\s*" + STRING + ")+\\s*$")) continue;
            Matcher answers = Pattern.compile(STRING).matcher(values);
            int nonempty = 0;
            while (answers.find()) {
                String answer = answers.group();
                if (answer.substring(1, answer.length() - 1).trim().length() > 0) nonempty++;
            }
            if (nonempty < 2) continue;
            // A suggestive name or nearby prose may identify an arbitrary field as specify.
            String context = visible.substring(Math.max(0, m.start() - 300), Math.min(visible.length(), m.end() + 300));
            if (matches(name.toLowerCase(Locale.ROOT), "specify|write[_-]?in")
                || matches(context.toLowerCase(Locale.ROOT), "(?:" + SPECIFY + ").{0,100}"
                    + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "|" + Pattern.quote(name.toLowerCase(Locale.ROOT))
                    + ".{0,100}(?:" + SPECIFY + ")")) return;
        }
        throw new AssertionError("Help must show a named specify field's texts override with several nonempty string answers");
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "generation":
            sentence(t, "Help must link honest enumerators' specify answers after choosing Other to ordinary text generation",
                HONEST, PERSON, SPECIFY, "\\b(?:choos\\w*|chos\\w*|select\\w*|pick\\w*|tick\\w*)\\s+[\"']?other\\b",
                "(?:as|like|same as|same way as|same rules as).{0,60}(?:any|other|ordinary|normal|regular).{0,30}text (?:questions?|fields?)");
            break;
        case "placeholder":
            near(t, SPECIFY, DEFAULT, "Help must describe the default for specify text");
            sentence(t, "Help must explain the default placeholder contains the field's name and a number",
                DEFAULT, "\\bplaceholder\\w*\\b|dummy text|synthetic text",
                "(?:field|question)(?:'s)? name|name of (?:the )?(?:field|question)",
                "\\b(?:number|integer|numeric suffix|counter|digits?)\\b");
            break;
        case "override":
            require(matches(visible, "\\btexts\\b"), "Help must name the texts field override");
            sentence(t, "Help must explain that a configuration field override with texts gives specify plausible answers",
                "\\btexts\\b", SPECIFY, "config\\w*|json", "override|per[- ]field|field[- ]specific",
                "\\b(?:plausible|realistic|meaningful|natural)\\b", "\\b(?:answers?|responses?|texts?|strings?)\\b");
            break;
        case "example":
            example(visible);
            break;
        case "recoding":
            require(matches(visible, "\\bipacheckspecify\\b"), "Help must name ipacheckspecify for the recoding exercise");
            sentence(t, "Help must connect plausible specify texts to a realistic recoding exercise with ipacheckspecify",
                "\\bipacheckspecify\\b", "\\brecod\\w*\\b", "\\b(?:exercise|practice|training|tutorial)\\b",
                "\\b(?:realistic|plausible|meaningful|natural)\\b");
            near(t, "\\bipacheckspecify\\b", SPECIFY + "|\\btexts\\b", "Help must connect recoding to specify texts");
            break;
        default: throw new AssertionError("Unknown Amendment 17 area: " + area);
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

    private static void rejects(String text, String area) {
        boolean rejected = false;
        try { check(text, area); } catch (AssertionError expected) { rejected = true; }
        require(rejected, "Oracle accepted incorrect/incomplete " + area + ": " + text);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> good = new LinkedHashMap<>();
        good.put("generation", "An honest enumerator's specify answer after choosing Other is drawn as for any text question.");
        good.put("placeholder", "Specify text by default is a placeholder made of the field's name and a number.");
        good.put("override", "A configuration field override with texts gives the specify question plausible answers instead.");
        good.put("example", "For the specify field occupation_detail: {\"fields\": {\"occupation_detail\": {\"texts\": [\"Bicycle repair\", \"Basket weaving\", \"Beekeeping\"]}}}");
        good.put("recoding", "These specify texts make a recoding exercise with ipacheckspecify realistic.");
        String complete = String.join("\n", good.values());
        for (Map.Entry<String, String> e : good.entrySet()) {
            check(e.getValue(), e.getKey());
            check(complete, e.getKey());
            check("{pstd}{bf:{it:" + e.getValue().replace(" ", "\r\n") + "}}{p_end}", e.getKey());
            rejects("", e.getKey());
            rejects("{* {bf:" + complete + "}}", e.getKey());
            rejects(complete.replace(e.getValue(), ""), e.getKey());
        }
        check("Clean interviewers select \u201cOther\u201d and generate write-in responses like other text fields.", "generation");
        check("Without an override, specify is dummy text containing the name of the question plus an integer.", "placeholder");
        check("Use a per-field JSON setting texts for meaningful specify responses.", "override");
        check("{cmd:\"job_specify\"}: {\"texts\": [\"Réparation\", \"Weaving\"]}", "example");
        check("{\"job_specify\":{\"texts\":[\"He said \\\"repair\\\"\",\"Weaving\"]}}", "example");
        check("Plausible write-in responses support recoding practice using ipacheckspecify.", "recoding");
        for (String fragment : Arrays.asList("honest ", "enumerator's ", "specify ", "after choosing Other ", "as for any text question"))
            rejects(good.get("generation").replace(fragment, ""), "generation");
        rejects(good.get("generation").replace("honest", "faulty"), "generation");
        rejects(good.get("generation").replace("Other", "Yes"), "generation");
        rejects(good.get("generation").replace("text question", "select-one question"), "generation");
        for (String fragment : Arrays.asList("by default ", "placeholder ", "field's name", "number"))
            rejects(good.get("placeholder").replace(fragment, ""), "placeholder");
        rejects("Specify text by default is always a plausible occupation.", "placeholder");
        for (String fragment : Arrays.asList("configuration ", "field override ", "texts ", "specify ", "plausible "))
            rejects(good.get("override").replace(fragment, ""), "override");
        rejects(good.get("override").replace("texts", "textPools"), "override");
        for (String bad : Arrays.asList("[]", "[\"Repair\"]", "[1, 2, 3]", "[\"\", \" \" ]", "[\"Repair\",]", "[\"Repair\", 2]"))
            rejects("{\"job_specify\": {\"texts\": " + bad + "}}", "example");
        rejects("{\"texts\": [\"Repair\", \"Weaving\"]}", "example");
        rejects("{\"village_name\": {\"texts\": [\"Adama\", \"Mojo\"]}}", "example");
        rejects("{\"job_specify\": {\"textPools\": [\"Repair\", \"Weaving\"]}}", "example");
        for (String fragment : Arrays.asList("specify texts", "recoding ", "exercise ", "ipacheckspecify", "realistic"))
            rejects(good.get("recoding").replace(fragment, ""), "recoding");
        rejects(good.get("recoding").replace("ipacheckspecify", "ipacheckenumdb"), "recoding");
        if (args.length == 1) Files.write(Paths.get(args[0]), complete.getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 17 help oracle: 5 areas, omissions, SMCL/Unicode/JSON variants and counterexamples passed");
    }
}
