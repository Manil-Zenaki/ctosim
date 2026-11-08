import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.regex.Pattern;

/** Spec 11 Amendment 20 only. Lexical assumptions are recorded as T1-T3. */
public final class RevisitAnswersHelpChecks {
    private static final String REVISIT = "\\brevisit\\w*\\b|\\b(?:repeat(?:ed)?|subsequent|later|return) visits?\\b|\\bvisited again\\b";
    private static final String ANSWERS = "\\b(?:answers?|responses?|values?)\\b";
    private static final String REPEAT = "(?:\\b(?:repeat(?:s|ed)?(?!\\s+(?:household )?visits?\\b)|cop(?:y|ies|ied)|reus(?:e|es|ed)|retain(?:s|ed)?|preserv(?:e|es|ed)|carr(?:y|ies|ied) (?:forward|over))\\b|\\b(?:same|unchanged) (?:answers?|responses?|values?)\\b)";
    private static final String NEGATED = "\\b(?:not|never|doesn't|don't|cannot|can't) (?:be )?(?:repeat\\w*|cop\\w*|reus\\w*|retain\\w*|preserv\\w*)\\b";
    private static final String BEFORE = "\\b(?:before|preceding|ahead of|prior to) (?:the |a |its |household's )?availability (?:question|field|item)\\b|\\b(?:questions?|fields?|items?) (?:that |which )?precede(?:s)? (?:the )?availability (?:question|field|item)\\b";
    private static final String FIRST = "\\b(?:first|initial|original) visit\\b|\\bvisit (?:one|1)\\b";
    private static final String[] AREAS = {"repeated", "before_availability", "first_visit", "dates", "times", "locations", "media", "identification"};
    private static final String[] TYPES = {"dates?", "times?", "locations?|gps(?: coordinates)?|geopoints?|coordinates?", "media(?: files)?|(?:image|photo|audio|video) files"};
    private static final String GOOD = "A revisit repeats the household's answers given before the availability question at its first visit, dates, times, locations and media files excepted, so that the household keeps the same identification, such as its district, from one visit to the next.";

    private static boolean has(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // Comments are invisible; formatting is not. Keep paragraph boundaries so
    // pronouns in adjacent sentences can explain the preceding revisit rule.
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
        String text = visible.toString().replaceAll("\\{(?:p_end|pstd|p|phang|title|dlgtab)(?: [^{}]*)?\\}", "\n\n");
        for (int i = 0; i < 12; i++)
            text = text.replaceAll("\\{[a-zA-Z][a-zA-Z0-9_ ]*:([^{}]*)\\}", "$1");
        return text.replaceAll("\\{[a-zA-Z][a-zA-Z0-9_ ]*\\}", " ")
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
            .replace('\u00a0', ' ').toLowerCase(Locale.ROOT);
    }

    private static boolean exception(String paragraph, String type) {
        // Keep exception markers and the type in one clause/sentence: an unrelated
        // exception elsewhere must not turn a list of copied types into exclusions.
        String item = "\\b(?:" + type + ")\\b";
        String list = "[^.!?;]{0,180}";
        return has(paragraph, "\\b(?:except(?:ing| for)?|excluding|apart from|other than|but not)\\b" + list + item)
            || has(paragraph, item + list + "\\b(?:excepted|excluded|not (?:repeated|copied|reused|retained|carried over))\\b")
            || has(paragraph, "\\b(?:do not|does not|never) (?:repeat|copy|reuse|retain)\\b" + list + item);
    }

    private static void check(String raw, String area) {
        int index = java.util.Arrays.asList(AREAS).indexOf(area);
        require(index >= 0, "Unknown Amendment 20 area: " + area);
        boolean matched = false;
        for (String block : plain(raw).split("(?:\\r?\\n)[ \\t]*(?:\\r?\\n)+")) {
            String paragraph = block.replaceAll("\\s+", " ").trim();
            if (!has(paragraph, REVISIT) || !has(paragraph, "\\bhouseholds?\\b")) continue;
            boolean repeats = false;
            for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
                // Negation of an exception is legitimate; exclude it from the
                // positive repetition clause rather than vetoing the paragraph.
                String positive = sentence.split("\\b(?:except|excluding|apart from|but not)\\b", 2)[0];
                positive = positive.split(NEGATED, 2)[0];
                if (has(positive, REPEAT + "[^.!?;]{0,100}" + ANSWERS)
                    || has(positive, ANSWERS + "[^.!?;]{0,100}" + REPEAT)
                    || has(positive, "\\b(?:same|unchanged) (?:answers?|responses?|values?)\\b")) repeats = true;
            }
            if (!repeats) continue;
            if (index == 0) matched = true;
            else if (index == 1) matched |= has(paragraph, BEFORE);
            else if (index == 2) matched |= has(paragraph, FIRST);
            else if (index >= 3 && index <= 6) matched |= exception(paragraph, TYPES[index - 3]);
            else matched |= has(paragraph, "\\b(?:same|consistent|unchanged|stable) (?:household )?(?:identification|identity|identifying (?:information|details)|district|administrative (?:identity|details))\\b|\\b(?:identification|identity|district|identifying (?:information|details))\\b.{0,60}\\b(?:same|consistent|unchanged|stable)\\b");
        }
        String[] messages = {
            "Explain that a revisit repeats the household's answers",
            "Limit the repeated answers to those before the availability question",
            "Identify the household's FIRST visit as the source of repeated answers",
            "Exclude dates from the revisit's repeated answers",
            "Exclude times from the revisit's repeated answers",
            "Exclude locations from the revisit's repeated answers",
            "Exclude media files from the revisit's repeated answers",
            "Explain that repeated answers preserve household identification across visits (for example, district)"
        };
        require(matched, messages[index]);
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
        String alternative = "On subsequent visits, the household's responses are copied from the initial visit for questions preceding the availability field. These preserve the same identifying details, excluding dates, times, GPS coordinates and media.";
        for (String area : AREAS) {
            check(GOOD, area);
            check(alternative, area);
            check("{title:Visit outcomes and revisits}{pstd}{bf:{it:" + GOOD.replace(" ", "\r\n") + "}}{p_end}", area);
            check(GOOD.replace("'", "\u2019"), area);
            rejects("", area);
            rejects("{* {bf:" + GOOD + "}}", area);
            rejects("Household revisits have the same survey id and place, on a later day with the same enumerator.", area);
            rejects(GOOD.replace("repeats", "does not repeat"), area);
            rejects(GOOD.replace("revisit", "first interview"), area);
        }
        rejects(GOOD.replace("before", "after"), "before_availability");
        rejects("On repeated visits, the household gives fresh answers to the availability question.", "repeated");
        rejects("On repeat visits, the household draws new answers.", "repeated");
        rejects(GOOD.replace("before", "at"), "before_availability");
        rejects(GOOD.replace("before the availability question", "through the availability question"), "before_availability");
        rejects(GOOD.replace("availability", "consent"), "before_availability");
        rejects(GOOD.replace("first visit", "previous visit"), "first_visit");
        rejects(GOOD.replace("first visit", "latest visit"), "first_visit");
        rejects(GOOD.replace("at its first visit", ""), "first_visit");
        String[] omissions = {"dates, ", "times, ", "locations and ", " and media files"};
        for (int i = 0; i < omissions.length; i++) {
            String area = AREAS[i + 3];
            rejects(GOOD.replace(omissions[i], ""), area);
            rejects(GOOD.replace("excepted", "included"), area);
            check(GOOD.replace("dates, times, locations and media files excepted", "except for dates, times, locations and media files"), area);
            check(GOOD.replace("dates, times, locations and media files excepted", "dates, times, locations and media files are not copied"), area);
            rejects("Household revisits copy dates, times, locations and media files as answers. Elsewhere, signatures are excluded.", area);
        }
        rejects(GOOD.replace("keeps the same identification, such as its district", "changes its identification"), "identification");
        rejects(GOOD.replace("keeps the same identification, such as its district", "keeps the same enumerator"), "identification");
        check(GOOD.replace(", such as its district", ""), "identification");
        check(GOOD.replace("keeps the same identification, such as its district", "keeps its district unchanged"), "identification");
        if (args.length == 1) Files.write(Paths.get(args[0]), GOOD.getBytes(StandardCharsets.UTF_8));
        System.out.println("Amendment 20: all eight help oracle areas passed positive/negative self-checks");
    }
}
