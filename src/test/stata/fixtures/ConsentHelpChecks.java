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

/** Spec 11 Amendment 10: lexical help checks, not simulator tests.
 * Expectations come only from the spec. See questions J1-J3 for prose limits.
 */
public final class ConsentHelpChecks {
    private static void require(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }

    private static String plain(String text) {
        // SMCL comments are not documentation; nested visible markup is.
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

    private static void near(String text, String first, String second, String why) {
        has(text, "(?:" + first + ").{0,220}(?:" + second + ")|(?:" + second
            + ").{0,220}(?:" + first + ")", why);
    }

    private static final String QUESTIONS = "question|field";
    private static final String NAMED = "nam(e|es|ed|ing)|specif(y|ies|ied)|list(ed|s)?|configur(e|ed)|identif(y|ied)";
    private static final String CONSENT_RATE = "consent.{0,30}(rate|fraction|share|proportion|percent)|"
        + "(rate|fraction|share|proportion|percent).{0,40}consent";

    private static void check(String raw, String area) {
        String visible = plain(raw);
        String t = visible.toLowerCase(Locale.ROOT);
        switch (area) {
        case "configuration":
            has(visible, "\\bconsent\\b", "Help must name the exact configuration key consent");
            near(t, "\\bconsent\\b", "config|json|setting|object|key",
                "Help must identify consent as configuration");
            near(t, "consent", QUESTIONS, "Help must describe the consent questions");
            near(t, QUESTIONS, NAMED, "Help must explain naming the consent questions");
            near(t, NAMED, "\\byou\\b|\\buser\\b|suppl(y|ied)|config",
                "Help must explain that the user supplies the consent questions");
            break;
        case "rate":
            has(t, CONSENT_RATE, "Help must describe the rate of consenting respondents");
            near(t, CONSENT_RATE, "default", "Help must identify a default consent rate");
            near(t, "default", "(?<![\\d.])(?:90\\s*(?:%|percent\\b|per cent\\b)|0\\.9(?:0)*(?!\\d)|ninety percent\\b)",
                "Help must give the consent rate default as 90 percent (0.9)");
            break;
        case "activation":
            near(t, "consent", "per[- ]interview|each interview|every interview|per submission|each submission|per visit|each visit",
                "Help must explain that consent is simulated per interview");
            // Accept both an only-if statement and an explicit absent-configuration rule.
            has(t, "(?:only.{0,35}(?:when|if|for)|requires?).{0,120}(?:" + QUESTIONS + ").{0,100}(?:" + NAMED + ")|"
                + "(?:only.{0,35}(?:when|if|for)|requires?).{0,100}(?:" + NAMED + ").{0,100}(?:" + QUESTIONS + ")|"
                + "(?:without|unless|no).{0,100}(?:" + NAMED + ").{0,100}(?:" + QUESTIONS + ").{0,120}(?:not|no|ordinary|normal)|"
                + "(?:without|unless|no).{0,100}(?:" + QUESTIONS + ").{0,100}(?:" + NAMED + ").{0,120}(?:not|no|ordinary|normal)",
                "Help must explain that consent simulation requires named questions");
            break;
        case "fake_refusals":
            has(visible, "\\bfakeRefusal\\b", "Help must name the fault fakeRefusal exactly");
            near(t, "fakerefusal", "refus", "Help must explain the declared refusals");
            near(t, "refus", "(?:avoid|skip|evade|escape).{0,45}interview|interview.{0,45}(?:avoid|skip|evade)",
                "Help must explain declaring refusals to avoid interviews");
            near(t, "refus", "twice|double|two times|2 times|2x",
                "Help must explain about twice the refusal rate");
            near(t, "twice|double|two times|2 times|2x", "other|clean|non.faulty|honest|normal",
                "Help must compare refusals with those of other enumerators");
            near(t, "twice|double|two times|2 times|2x", "about|roughly|approximately|around|on average|expected|average",
                "Help must describe the doubling as approximate or on average");
            break;
        case "detection":
            has(visible, "\\bipacheckenumdb\\b", "Help must identify ipacheckenumdb");
            near(t, "fakerefusal|fake refus|false refus|fabricated refus", "ipacheckenumdb",
                "Help must connect fake refusals to ipacheckenumdb");
            near(t, "ipacheckenumdb", CONSENT_RATE,
                "Help must explain detection through the consent rate");
            break;
        case "rosters":
            near(t, "roster|repeat", "add[- ]another|another (member|person|household member)|add (one )?more",
                "Help must describe rosters built by add-another questions");
            near(t, "add[- ]another|another (member|person|household member)|add (one )?more",
                "shorten|fewer|reduc|under.?report|smaller|cut short|early stop|stop.{0,15}early",
                "Help must explain shortening add-another rosters");
            break;
        case "outliers":
            near(t, "outlier", "constraint", "Help must relate outlier placement to constraints");
            near(t, "constraint", "accept|satisf|respect|obey|meet|within|allow|valid",
                "Help must explain that the form's constraint accepts the outlier");
            near(t, "constraint", "depend.{0,60}(?:other|another|earlier|previous).{0,25}(?:answer|response|field|question)|"
                + "refer.{0,60}(?:other|another|earlier|previous).{0,25}(?:answer|response|field|question)|"
                + "(?:other|another|earlier|previous).{0,25}(?:answer|response).{0,60}(?:depend|constraint)|cross[- ]field",
                "Help must include constraints depending on other answers");
            break;
        default:
            throw new AssertionError("Unknown help-check area: " + area);
        }
    }

    // Stata propagates a nonzero javacall result through the helper do-file.
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
        examples.put("configuration", new String[] {
            "Configuration key {cmd:consent}:", "The user names the questions."
        });
        examples.put("rate", new String[] {
            "The consent rate is configurable.", "Its default is 90 %."
        });
        examples.put("activation", new String[] {
            "Consent is simulated per interview.", "Only when questions are named is it enabled."
        });
        examples.put("fake_refusals", new String[] {
            "Fault {cmd:fakeRefusal}:", "Enumerators declare refusals to avoid interviews.",
            "About twice as many refusals as the others."
        });
        examples.put("detection", new String[] {
            "Detect fakeRefusal", "with ipacheckenumdb", "through its consent rate."
        });
        examples.put("rosters", new String[] {
            "Rosters built by add another questions", "can be shortened."
        });
        examples.put("outliers", new String[] {
            "Outliers are placed", "where the form's constraint accepts them,",
            "even when it depends on other answers."
        });
        int omissions = 0;
        for (Map.Entry<String, String[]> entry : examples.entrySet()) {
            String area = entry.getKey();
            String[] clauses = entry.getValue();
            String complete = String.join("\n", clauses);
            check(complete, area);
            check("{pstd}{bf:" + complete.replace(" ", "\r\n") + "}{p_end}", area);
            rejects("", area, "empty help");
            rejects("{* " + plain(complete) + "}", area, "comment-only help");
            for (int i = 0; i < clauses.length; i++) {
                List<String> missing = new ArrayList<>(Arrays.asList(clauses));
                missing.remove(i);
                rejects(String.join(" ", missing), area, "omitted clause " + i);
                omissions++;
            }
        }
        check("JSON settings: {cmd:{bf:consent}} identifies the consent fields you specify.", "configuration");
        check("The proportion who consent has a default of 0.9.", "rate");
        check("Consent rate: ninety percent by default.", "rate");
        check("The consent rate defaults to 90\u00a0percent.", "rate");
        check("Consent is drawn for each submission only if you specify the consent fields.", "activation");
        check("Consent is only simulated per interview when you name the questions.", "activation");
        check("Consent is simulated for each interview only for the fields you name.", "activation");
        check("Per-interview consent simulation requires explicitly named questions.", "activation");
        check("Consent is drawn for each visit. Without named consent questions it is not simulated separately.", "activation");
        check("The fakeRefusal fault fabricates refusals to skip interviews; on average the refusal rate is double "
            + "that of clean enumerators.", "fake_refusals");
        check("The consent rate in {help ipacheckenumdb:ipacheckenumdb} reveals fabricated refusals.", "detection");
        check("Repeats controlled by \u201cadd\u2011another\u201d questions can be cut short.", "rosters");
        check("Outliers satisfy cross-field constraints that refer to earlier responses.", "outliers");

        rejects("Consent is a normal question.", "configuration", "no configuration or user-supplied names");
        rejects("The consent rate defaults to 80 percent.", "rate", "wrong default");
        rejects("The consent rate is 90 percent.", "rate", "no default identified");
        rejects("Consent is simulated per interview automatically for every form.", "activation", "unconditional simulation");
        rejects("Consent is simulated only when questions are named.", "activation", "missing per-interview unit");
        rejects("fakeRefusal declares refusals to avoid interviews, about half as many as others.", "fake_refusals", "wrong ratio");
        rejects("fakeRefusal declares refusals to avoid interviews, exactly twice as many as others.", "fake_refusals", "exact rather than average ratio");
        rejects("fakeRefusal declares refusals to avoid interviews, about twice as many.", "fake_refusals", "missing comparison group");
        rejects("fakeRefusal is found with ipacheckenumdb using duration.", "detection", "wrong detection metric");
        rejects("fakeRefusal is found with another check using the consent rate.", "detection", "missing check name");
        rejects("Rosters with a declared count can be shortened.", "rosters", "only count-based rosters");
        rejects("Add another questions build rosters.", "rosters", "no shortening");
        rejects("Outliers respect fixed numeric constraints.", "outliers", "no dependent constraints");
        rejects("Outliers violate constraints that depend on other answers.", "outliers", "constraint violations");
        System.out.println("Amendment 10 help oracle: 7 areas, " + omissions
            + " clause omissions, wording/SMCL variants and semantic counterexamples passed");
    }
}
