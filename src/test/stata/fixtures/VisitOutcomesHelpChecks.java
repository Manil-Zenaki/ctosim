import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Independent lexical oracle for spec 11 Amendment 19; assumptions S1-S3. */
public final class VisitOutcomesHelpChecks {
    private static final String HH = "\\bhouseholds?\\b";
    private static final String PERSON = "\\b(?:enumerators?|interviewers?)\\b";
    private static final String REVISIT = "\\brevisit\\w*\\b|\\bvisit(?:ed|s)? again\\b|\\brepeat(?:ed)? visits?\\b";
    private static final String FOUND = "\\b(?:found|reached|available)\\b";
    private static final String DEFAULT = "\\bdefault\\w*\\b|(?:if|when) (?:omitted|not (?:set|specified))";
    private static final Map<String, Rule> RULES = new LinkedHashMap<>();
    private static final Map<String, String> GOOD = new LinkedHashMap<>();

    private static final class Rule {
        final String[] identifiers;
        final List<String[]> requirements = new ArrayList<>();
        Rule(String... identifiers) { this.identifiers = identifiers; }
        Rule needs(String message, String... clauses) {
            String[] row = new String[clauses.length + 1];
            row[0] = message;
            System.arraycopy(clauses, 0, row, 1, clauses.length);
            requirements.add(row);
            return this;
        }
    }

    private static Rule area(String name, String good, String... identifiers) {
        Rule rule = new Rule(identifiers);
        RULES.put(name, rule);
        GOOD.put(name, good);
        return rule;
    }

    static {
        area("configuration", "The visits key belongs in the configuration file.", "visits")
            .needs("Identify visits as a configuration key", "\\bvisits\\b", "config\\w*|json", "\\bkey\\b|setting|option");
        area("field", "In visits, field names the availability question.", "field")
            .needs("Explain field names the availability question", "\\bfield\\b", "availab\\w*", "question|variable");
        area("available", "The available value is the availability question's value for a household found; available defaults to \"1\".", "available")
            .needs("Explain available is the availability question's successful value", "\\bavailable\\b", "value|code", "availability question|question.{0,60}availability")
            .needs("Give available's default value of 1", "\\bavailable\\b", DEFAULT, "(?:[\"']?\\b1\\b[\"']?|\\bone\\b)(?!\\d|\\.\\d)");
        area("number", "The optional number setting names the visit number question.", "number")
            .needs("Explain number is an optional visit-number question", "\\bnumber\\b", "optional|not required|may (?:name|specify|set)", "visit[ -]number|number of (?:the |a )?visit|visit count", "question|variable");
        area("revisit_rate", "revisitRate is the share of households found only after failed visits; its default is 10%.", "revisitRate")
            .needs("Explain revisitRate is households found only after failed visits", "\\brevisitrate\\b", "share|fraction|proportion|percent|rate", HH, FOUND,
                "only after.{0,50}(?:failed|unsuccessful) visits?|after (?:one or more|at least one) (?:failed|unsuccessful) visits?")
            .needs("Give revisitRate's default of 10 percent", "\\brevisitrate\\b", DEFAULT, "(?<![\\d.])(?:0?\\.10*\\b(?!\\d|\\.\\d|\\s*(?:%|percent|per cent))|10(?:\\.0+)?\\s*(?:%|percent|per cent)|ten (?:percent|per cent))");
        area("unreached_rate", "unreachedRate is the share of households never found; its default is 2.5%.", "unreachedRate")
            .needs("Explain unreachedRate is households never found", "\\bunreachedrate\\b", "share|fraction|proportion|percent|rate", HH,
                "never (?:found|reached|available)|not (?:found|reached) (?:at all|on any visit)")
            .needs("Give unreachedRate's default of 2.5 percent", "\\bunreachedrate\\b", DEFAULT, "(?<![\\d.])(?:0?\\.0250*\\b(?!\\d|\\.\\d|\\s*(?:%|percent|per cent))|2\\.50*\\s*(?:%|percent|per cent)|two and (?:a |one )?half (?:percent|per cent))");
        area("max_visits", "maxVisits is the maximum number of visits made to one household; it defaults to 3.", "maxVisits")
            .needs("Explain maxVisits caps visits per household", "\\bmaxvisits\\b", "maximum|most|limit|cap|at most", "\\bvisits\\b", "(?:one|each|a|per) household")
            .needs("Give maxVisits's default of three", "\\bmaxvisits\\b", DEFAULT, "\\b(?:3|three)\\b(?!\\.\\d)");
        area("explicit_field", "The simulator never guesses which question holds availability.")
            .needs("Say the simulator does not guess the availability question", "simulat\\w*|ctosim", "availab\\w*", "question|field|variable",
                "never (?:guess\\w*|infer\\w*)|(?:does not|doesn't|will not|cannot) (?:guess\\w*|infer\\w*)|not (?:guessed|inferred|automatically (?:chosen|detected))");
        area("failed_value", "A failed visit answers the availability question with another of its values.")
            .needs("Explain failed visits use another allowed availability value", "(?:failed|unsuccessful) visits?", "availab\\w*", "question|field",
                "another of its values|(?:another|other|different).{0,50}(?:allowed|possible|available|question'?s?|its) (?:values?|codes?)|(?:another|other|different) (?:values?|codes?).{0,50}(?:of|from|for).{0,30}(?:question|field)");
        area("form_logic", "After a failed visit, the rest of the form follows the form's own logic.")
            .needs("Explain the rest of a failed-visit form follows its own logic", "(?:failed|unsuccessful) visits?", "rest|remain\\w*|subsequent", "form", "own logic|form'?s? (?:logic|relevance|skip)|skip logic");
        area("final_values", "The optional final values name terminal reasons such as a household that has moved.", "final")
            .needs("Describe optional final outcome values and terminal reasons", "\\bfinal\\b", "value|code|outcome", "optional|may (?:set|specify|name)|can (?:set|specify|name)",
                "reason|moved|relocat\\w*|terminal|permanent");
        area("final_stops", "After a final outcome, the household is not visited again.", "final")
            .needs("Say final outcomes stop further visits to that household", "\\bfinal\\b", HH,
                "(?:not|never) (?:be )?(?:visited again|revisited)|no (?:more|further|repeat) visits|stops? (?:further |future )?(?:visits|revisits)");
        area("final_exclusion", "A household found later never gets a final value.", "final")
            .needs("Say households found later never receive final values", HH, "(?:found|reached|available) later|(?:later|eventually|subsequently) (?:found|reached|available)", "\\bfinal\\b",
                "never (?:gets?|receives?|has|have|takes?)|(?:does not|do not|cannot|can't) (?:get|receive|have|take)|not (?:given|assigned)");
        area("later_day", "A household is revisited on a later day.")
            .needs("Say revisits happen on a later day", HH, REVISIT, "later (?:day|date)|subsequent (?:day|date)|following day|next day");
        area("same_enumerator", "A household is revisited by the same enumerator.")
            .needs("Say revisits keep the enumerator", HH, REVISIT, "same " + PERSON + "|" + PERSON + ".{0,25}(?:stays?|remains?) the same");
        area("same_id", "A household is revisited with the same survey id.")
            .needs("Say revisits keep the survey id", HH, REVISIT, "same (?:survey |household )?(?:id|identifier)\\b|(?:survey |household )?(?:id|identifier).{0,25}(?:stays?|remains?) the same");
        area("same_place", "A household is revisited at the same place.")
            .needs("Say revisits keep the location", HH, REVISIT, "same (?:place|location|coordinates|gps(?: coordinates)?)|(?:place|location|coordinates).{0,25}(?:stays?|remains?) the same");
        area("pending", "Households still waiting at the end of the simulation are reported in a warning.")
            .needs("Explain warnings for households still waiting when simulation ends", HH, "waiting|pending|awaiting|unfinished",
                "end of (?:the |a )?(?:simulation|run)|(?:simulation|run) (?:ends|finishes)|simulation'?s? end", "warn\\w*");
        area("tracking", "IPA's ipatracksurvey counts completed interviews through outcome(), given the availability question and its available value.", "ipatracksurvey", "outcome")
            .needs("Explain ipatracksurvey uses outcome() to count completed interviews", "\\bipatracksurvey\\b", "\\boutcome\\s*\\([^)]*\\)", "count\\w*", "complet\\w* (?:interviews?|submissions?)")
            .needs("Explain outcome() takes availability question and available value", "\\boutcome\\s*\\([^)]*\\)", "availability question|question.{0,30}availability", "available (?:value|code)|value.{0,40}(?:available|found)");
        area("duplicate_ids", "A revisited household's id appears in several submissions, which ipacheckids flags.", "ipacheckids")
            .needs("Explain repeated household ids across submissions are flagged by ipacheckids", REVISIT, HH, "\\bid\\b|identifier", "several|multiple|more than one|repeated|duplicate", "submissions?|records?|rows?", "\\bipacheckids\\b", "flag\\w*|report\\w*|detect\\w*|identif\\w*");
        area("ids_filter", "For revisited households, ipacheckids avoids flagging their ids if the check keeps only visits where the household was found.", "ipacheckids")
            .needs("Explain ipacheckids must retain only found visits to avoid flagging revisit ids", "\\bipacheckids\\b", REVISIT, HH,
                "(?:keeps?|retain\\w*|includ\\w*|us(?:e|es|ing)|check\\w*) only|restrict\\w* (?:the )?(?:check|data|visits).{0,30}to|filter\\w* .{0,30}(?:to|for)",
                "(?:household|households).{0,30}(?:was |were |is |are )?(?:found|reached|available)|(?:found|reached|available) (?:households|visits)");
        area("answer_key", "The answer key does not list revisited households' repeated ids.")
            .needs("Say revisits or their repeated ids are not listed in the answer key", "answer[ -]key", REVISIT,
                "does not list|doesn't list|not (?:listed|included|recorded)|excludes?|omits?|never (?:lists|includes|records)");
        area("replacements", "Replacement households are not simulated.")
            .needs("Say replacement households are not simulated", "replacement households?|household replacements?",
                "not simulat\\w*|never simulat\\w*|(?:does not|doesn't) simulat\\w*|not (?:supported|modelled|modeled)|no simulat\\w*");
    }

    private static boolean has(String text, String expression) {
        return Pattern.compile(expression).matcher(text).find();
    }

    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    // Remove comments before unwrapping nested visible SMCL formatting.
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

    private static void check(String raw, String name) {
        Rule rule = RULES.get(name);
        require(rule != null, "Unknown Amendment 19 area: " + name);
        String visible = plain(raw);
        for (String id : rule.identifiers)
            require(has(visible, "\\b" + Pattern.quote(id) + "\\b"), "Help must name " + id);
        String text = visible.toLowerCase(Locale.ROOT);
        for (String[] requirement : rule.requirements) {
            boolean matched = false;
            for (String sentence : text.split("(?<=[.!?])\\s+")) {
                boolean ok = true;
                for (int i = 1; i < requirement.length; i++) ok &= has(sentence, requirement[i]);
                if (ok) { matched = true; break; }
            }
            require(matched, requirement[0]);
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
        require(rejected, "Oracle accepted incomplete/incorrect " + area + ": " + text);
    }

    public static void main(String[] args) throws Exception {
        String complete = String.join("\n", GOOD.values());
        for (Map.Entry<String, String> e : GOOD.entrySet()) {
            check(e.getValue(), e.getKey());
            check(complete, e.getKey());
            check("{pstd}{bf:{it:" + e.getValue().replace(" ", "\r\n") + "}}{p_end}", e.getKey());
            rejects("", e.getKey());
            rejects("{* {bf:" + complete + "}}", e.getKey());
            rejects(complete.replace(e.getValue(), ""), e.getKey());
        }
        check("visits is a JSON setting.", "configuration");
        check("The availability variable is named by field.", "field");
        check("available is the availability question's code and defaults to one.", "available");
        check("number may name the visit-number variable.", "number");
        check("revisitRate is the fraction of households reached after at least one unsuccessful visit, default .10.", "revisit_rate");
        check("unreachedRate is the proportion of households never reached, default .025.", "unreached_rate");
        check("maxVisits caps visits per household, default three.", "max_visits");
        check("The availability field is not inferred by ctosim.", "explicit_field");
        check("An unsuccessful visit sets the availability field to a different allowed code.", "failed_value");
        check("On unsuccessful visits, remaining form questions follow its own logic.", "form_logic");
        check("You may specify final outcome codes for permanent reasons.", "final_values");
        check("With final outcomes, no further visits are made to the household.", "final_stops");
        check("An eventually reached household cannot receive final codes.", "final_exclusion");
        check("A household is visited again on a subsequent date.", "later_day");
        check("A household is visited again by the same interviewer.", "same_enumerator");
        check("Household revisits retain the same identifier.", "same_id");
        check("Revisited households have the same GPS coordinates.", "same_place");
        check("A warning lists pending households when the run ends.", "pending");
        check("To count completed submissions, ipatracksurvey uses outcome(availability 1), the availability question and available code.", "tracking");
        check("ipacheckids detects duplicate identifiers in multiple records for revisited households.", "duplicate_ids");
        check("For household revisits, restrict the check ipacheckids to found visits.", "ids_filter");
        check("Repeated visits are not included in the answer-key.", "answer_key");
        check("The simulator does not simulate replacement households.", "replacements");
        check(GOOD.get("unreached_rate").replace("2.5%", "0.025"), "unreached_rate");
        check(GOOD.get("revisit_rate").replace("10%", "0.1"), "revisit_rate");
        check(GOOD.get("available").replace("\"", "\u201c"), "available");

        String[][] mutations = {
            {"configuration", "configuration", "output"}, {"configuration", "visits", "visit"},
            {"field", "availability", "consent"}, {"field", "field", "questionName"},
            {"available", "availability", "consent"}, {"available", "defaults", "is set"},
            {"available", "\"1\"", "\"0\""}, {"available", "\"1\"", "\"11\""},
            {"number", "optional", "required"}, {"number", "visit number", "household size"},
            {"revisit_rate", "only after failed", "before failed"}, {"revisit_rate", "households", "enumerators"},
            {"revisit_rate", "10%", "1%"}, {"revisit_rate", "10%", "0.10%"},
            {"revisit_rate", "default", "example"}, {"revisit_rate", "revisitRate", "revisitrate"},
            {"unreached_rate", "never", "always"}, {"unreached_rate", "2.5%", "25%"},
            {"unreached_rate", "2.5%", "0.25"}, {"unreached_rate", "default", "example"},
            {"max_visits", "maximum", "minimum"}, {"max_visits", "one household", "one enumerator"},
            {"max_visits", "3", "4"}, {"max_visits", "3", "30"}, {"max_visits", "defaults", "is set"},
            {"explicit_field", "never", "always"}, {"explicit_field", "availability", "consent"},
            {"failed_value", "another of its values", "an arbitrary value"}, {"failed_value", "failed", "successful"},
            {"failed_value", "availability", "consent"}, {"form_logic", "own logic", "fixed template"},
            {"form_logic", "failed", "successful"},
            {"final_values", "optional", "required"}, {"final_values", "final", "temporary"},
            {"final_stops", "not visited again", "visited again"}, {"final_stops", "final", "temporary"},
            {"final_exclusion", "never gets", "always gets"}, {"final_exclusion", "found later", "never found"},
            {"later_day", "later", "same"}, {"later_day", "day", "hour"},
            {"same_enumerator", "same", "different"}, {"same_id", "same", "new"},
            {"same_id", "survey id", "submission KEY"}, {"same_place", "same", "different"},
            {"same_place", "place", "village"}, {"pending", "warning", "answer key"},
            {"pending", "end", "start"}, {"pending", "still waiting", "already reached"},
            {"tracking", "completed", "attempted"}, {"tracking", "outcome()", "id()"},
            {"tracking", "availability question", "consent question"}, {"tracking", "available value", "refusal value"},
            {"tracking", "ipatracksurvey", "ipacheckenumdb"},
            {"duplicate_ids", "several", "one"}, {"duplicate_ids", "ipacheckids", "ipacheckversions"},
            {"duplicate_ids", "revisited", "unreached"}, {"ids_filter", "only", "all"},
            {"ids_filter", "was found", "was absent"}, {"ids_filter", "ipacheckids", "ipacheckversions"},
            {"answer_key", "does not list", "lists"}, {"answer_key", "revisited", "replacement"},
            {"replacements", "not simulated", "simulated"}, {"replacements", "Replacement", "Revisited"}
        };
        for (String[] m : mutations) rejects(GOOD.get(m[0]).replace(m[1], m[2]), m[0]);
        if (args.length == 1) Files.write(Paths.get(args[0]), complete.getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 19 help oracle: " + RULES.size() + " areas, omissions, variants, and " + mutations.length + " clause mutations passed");
    }
}
