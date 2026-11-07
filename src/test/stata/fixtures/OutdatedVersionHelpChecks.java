import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Spec 11 Amendment 18 only. Lexical help checks; assumptions R1-R3. */
public final class OutdatedVersionHelpChecks {
    private static final String PERSON = "\\b(?:enumerators?|interviewers?)\\b";
    private static final String OLD = "previous(?:version| version)|old(?:er)? version|prior version";
    private static final String NEW = "(?:form'?s?|current|new|updated) version|version of the form";
    private static final String DEPLOY = "\\b(?:deploy\\w*|releas\\w*|updat\\w*|roll(?:ed)? out)\\b";

    private static boolean has(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static void require(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

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
            .replace('\u2212', '-').replace('\u00a0', ' ')
            .replaceAll("\\s+", " ").trim();
    }

    private static void sentence(String text, String why, String... clauses) {
        for (String s : text.split("(?<=[.!?])\\s+")) {
            boolean ok = true;
            for (String clause : clauses) ok &= has(s, clause);
            if (ok) return;
        }
        throw new AssertionError(why);
    }

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "configuration":
            require(has(visible, "\\bformUpdate\\b"), "Help must name the formUpdate configuration key");
            sentence(t, "Help must identify formUpdate as configuration", "\\bformupdate\\b", "config\\w*|json");
            break;
        case "date":
            require(has(visible, "\\bformUpdate\\b") && has(visible, "\\bdate\\b"), "Help must name formUpdate and date");
            sentence(t, "Help must explain formUpdate.date is when a new form version is deployed",
                "\\bformupdate\\b", "\\bdate\\b", "new.{0,30}(?:form|version)|updated form", DEPLOY);
            break;
        case "previous":
            require(has(visible, "\\bpreviousVersion\\b"), "Help must name previousVersion");
            sentence(t, "Help must explain previousVersion is the version used before deployment",
                "\\bpreviousversion\\b", "\\b(?:used|use|using|held|recorded)\\b", "\\bbefore\\b|prior to", "date|" + DEPLOY);
            break;
        case "default":
            sentence(t, "Help must give previousVersion's default: numeric form version minus one",
                "\\bpreviousversion\\b", "\\bdefault\\w*\\b|if omitted|when omitted",
                "(?:form'?s?|current) version.{0,30}(?:minus (?:one|1)|-\\s*1\\b)|one less than.{0,30}(?:form'?s?|current) version",
                "(?:if|when|for|provided|assuming).{0,60}(?:numeric|number|integer)|numeric (?:form'?s? )?version");
            break;
        case "before":
            require(has(visible, "\\bformdef_version\\b"), "Help must name formdef_version");
            sentence(t, "Help must assign previous version to formdef_version for submissions started before the update date",
                "submissions?|interviews?|forms?", "\\b(?:start\\w*|beg[ai]n\\w*)\\b.{0,50}(?:before|prior to)",
                "date|" + DEPLOY, "\\bformdef_version\\b", OLD);
            break;
        case "after":
            require(has(visible, "\\bformdef_version\\b"), "Help must name formdef_version");
            sentence(t, "Help must assign the form's version to other submissions, including those started on the deployment date",
                "\\bformdef_version\\b", NEW,
                "(?:start\\w*|beg[ai]n\\w*).{0,60}(?:on or after|on and after|at or after|from.{0,30}(?:date|update)|not before)"
                    + "|\\b(?:others|otherwise|all other submissions|remaining submissions)\\b");
            break;
        case "fault":
            require(has(visible, "\\boutdatedVersion\\b"), "Help must name the outdatedVersion fault");
            sentence(t, "Help must explain the outdatedVersion fault keeps the previous version after the update",
                "\\boutdatedversion\\b", "\\bfault\\w*\\b", PERSON,
                "\\b(?:keep\\w*|kept|retain\\w*|continu\\w*|us(?:e|es|ing))\\b", OLD,
                "(?:after|following|beyond).{0,60}(?:" + DEPLOY + "|date)");
            break;
        case "activation":
            require(has(visible, "\\boutdatedVersion\\b") && has(visible, "\\bformUpdate\\b"),
                "Help must link outdatedVersion activation to formUpdate");
            sentence(t, "Help must say outdatedVersion requires formUpdate (not merely that it can be enabled with it)",
                "\\boutdatedversion\\b", "\\bformupdate\\b",
                "(?:only (?:when|if|with)|requires?|needs?).{0,80}formupdate"
                    + "|without.{0,50}formupdate.{0,80}(?:not|never|no|disabled)"
                    + "|formupdate.{0,60}(?:absent|omitted|not (?:named|set|configured|specified)).{0,80}(?:no|not|never|disabled)");
            break;
        case "detection":
            require(has(visible, "\\bipacheckversions\\b"), "Help must name ipacheckversions");
            sentence(t, "Help must link ipacheckversions to detecting outdated form versions",
                "\\bipacheckversions\\b", "\\boutdatedversion\\b|\\boutdated.{0,30}version|\\bold.{0,30}version|\\bversion.{0,30}(?:fault|problem)",
                "\\b(?:find\\w*|found|detect\\w*|check\\w*|identif\\w*|flag\\w*|reveal\\w*)\\b");
            break;
        case "staffing":
            sentence(t, "Help must explain seven faults need fourteen enumerators for one enumerator per fault",
                "(?:\\bseven|\\b7) faults?", "(?:\\bfourteen|\\b14) " + "(?:enumerators?|interviewers?)",
                "(?:one|1|an?) (?:enumerator|interviewer) (?:per|for each) fault|(?:one|1) (?:per|for each) fault",
                "need\\w*|requir\\w*");
            break;
        default: throw new AssertionError("Unknown Amendment 18 area: " + area);
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
        Map<String, String> good = new LinkedHashMap<>();
        good.put("configuration", "The configuration key formUpdate describes a form deployment.");
        good.put("date", "formUpdate.date is the date a new version of the form is deployed.");
        good.put("previous", "previousVersion is the version used before that deployment date.");
        good.put("default", "previousVersion defaults to the form's version minus one when it is a number.");
        good.put("before", "Submissions started before the deployment date have the previous version in formdef_version.");
        good.put("after", "Submissions started on or after the deployment date have the form's version in formdef_version.");
        good.put("fault", "The outdatedVersion fault means the enumerator keeps the previous version after the update.");
        good.put("activation", "outdatedVersion is injected only when formUpdate is named.");
        good.put("detection", "ipacheckversions detects the outdatedVersion fault.");
        good.put("staffing", "With seven faults, one enumerator per fault needs fourteen enumerators.");
        String complete = String.join("\n", good.values());
        for (Map.Entry<String, String> e : good.entrySet()) {
            check(e.getValue(), e.getKey());
            check(complete, e.getKey());
            check("{pstd}{bf:{it:" + e.getValue().replace(" ", "\r\n") + "}}{p_end}", e.getKey());
            rejects("", e.getKey());
            rejects("{* {bf:" + complete + "}}", e.getKey());
            rejects(complete.replace(e.getValue(), ""), e.getKey());
        }
        check("Set formUpdate in your JSON settings.", "configuration");
        check("The date in formUpdate records when the updated form is released.", "date");
        check("The version used prior to the update is previousVersion.", "previous");
        check("If omitted, previousVersion is one less than the current version, provided that version is numeric.", "default");
        check("previousVersion defaults to the form\u2019s version \u2212 1 when numeric.", "default");
        check("Interviews beginning prior to the update use the prior version in formdef_version.", "before");
        check("Otherwise formdef_version holds the current version.", "after");
        check("In the outdatedVersion fault, an interviewer continues using the old version following deployment.", "fault");
        check("Without formUpdate, outdatedVersion is never injected.", "activation");
        check("The outdatedVersion fault requires formUpdate.", "activation");
        check("Outdated form versions are found with ipacheckversions.", "detection");
        check("For 7 faults, 14 interviewers are required to assign one per fault.", "staffing");
        String[][] mutations = {
            {"configuration", "configuration", "display"},
            {"configuration", "formUpdate", "formupdate"},
            {"date", "formUpdate.", ""}, {"date", "new version", "old version"},
            {"date", "deployed", "withdrawn"}, {"date", "date", "label"},
            {"previous", "previousVersion", "nextVersion"}, {"previous", "before", "after"},
            {"default", "defaults", "is set"}, {"default", "minus one", "plus one"},
            {"default", "minus one", "minus two"}, {"default", "when it is a number", ""},
            {"default", "form's version", "submission count"},
            {"before", "started", "finished"}, {"before", "before", "after"},
            {"before", "previous version", "current version"}, {"before", "formdef_version", "KEY"},
            {"after", "on or after", "after"}, {"after", "started", "finished"},
            {"after", "form's version", "previous version"}, {"after", "formdef_version", "KEY"},
            {"fault", "fault", "setting"}, {"fault", "enumerator", "server"},
            {"fault", "previous version", "current version"}, {"fault", "after", "before"},
            {"fault", "outdatedVersion", "oldVersion"},
            {"activation", "only when", "when"}, {"activation", "formUpdate", "problems"},
            {"activation", "outdatedVersion", "travel"},
            {"detection", "ipacheckversions", "ipacheckenumdb"}, {"detection", "outdatedVersion", "travel"},
            {"staffing", "seven", "six"}, {"staffing", "fourteen", "twelve"},
            {"staffing", "one enumerator per fault", "one enumerator for all faults"}
        };
        for (String[] m : mutations) rejects(good.get(m[0]).replace(m[1], m[2]), m[0]);
        if (args.length == 1) Files.write(Paths.get(args[0]), complete.getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 18 help oracle: ten areas, omissions, variants, and " + mutations.length + " clause mutations passed");
    }
}
