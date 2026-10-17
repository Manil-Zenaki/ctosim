package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 09 Amendment 3; expectations use Java's full-match oracle, not a regex generator. */
class ConstraintHintsAmendment3PatternTest {
    static final String EXAMPLE = "([^a-z]+)";
    // ASCII witnesses plus BMP letters/digits used by the Unicode cases below.
    private static final String READABLE =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 \u00e9\u03a9\u4e2d\u0665";

    static void assertReadableMatch(String expression, String answer) {
        assertNotNull(answer, "Required pattern answer must not be null");
        Pattern pattern = Pattern.compile(expression);
        assertTrue(pattern.matcher(answer).matches(), () -> "Not a full match for " + expression + ": " + answer);
        for (int offset = 0; offset < answer.length();) {
            int cp = answer.codePointAt(offset);
            int next = offset + Character.charCount(cp);
            if (!Character.isLetterOrDigit(cp) && cp != ' ') {
                for (int replacement : READABLE.codePoints().toArray()) {
                    String candidate = answer.substring(0, offset)
                            + new String(Character.toChars(replacement)) + answer.substring(next);
                    assertFalse(pattern.matcher(candidate).matches(),
                            "Replaceable U+" + Integer.toHexString(cp) + " at offset " + offset
                            + " in <" + answer + "> for " + expression
                            + "; readable replacement U+" + Integer.toHexString(replacement));
                }
            }
            offset = next;
        }
    }

    static List<String> draws(String expression, int count, long seed) throws Exception {
        // Empty is an unmistakable documented fallback for every nonempty pattern below.
        return AnswerGeneratorTestSupport.draws(generator(""),
                question(QuestionKind.TEXT, text(0, 0, expression)), TODAY, new Random(seed), count);
    }

    @Test void motivatingNegatedClassProducesReadableFullMatches() throws Exception {
        for (String answer : draws(EXAMPLE, 256, SEED)) assertReadableMatch(EXAMPLE, answer);
    }

    @Test void motivatingNegatedClassCannotIntroduceALabelArgumentOpener() throws Exception {
        for (String answer : draws(EXAMPLE, 256, SEED + 1)) {
            assertNotNull(answer);
            assertFalse(answer.contains("${"), "Both characters are replaceable in the motivating pattern: " + answer);
        }
    }

    @ParameterizedTest(name = "readability and full matching: {0}")
    @ValueSource(strings = {
            "[^a-z]{1}", "[^a-z]{2,9}", "[^a-z]{256}", "[^0-9]{8}",
            ".{12}", "[\\s\\S]{12}", "\\w{12}", "\\W{12}", "[^a-zA-Z0-9 ]{6}",
            "[A!]{8}", "[7@]{8}", "[ !]{8}", "[A7 !]{8}",
            "[\\t ]{8}", "[\\r\\n ]{8}", "[${A]{12}",
            "[\u00e9!]{8}", "[\u03a9@]{8}", "[\u4e2d#]{8}", "[\u0665!]{8}",
            "[A!][0?][ #][b$]", "(?:[A!]{2}){3}",
            "(?:[A!][0?]|[B@][1#])", "[A!]?x[B@]+", "^[A!]{4}$",
            "(?=.*!)[A!]{4}", "(?=.{4}$)[A!]+", "([A!])\\1",
            "(?:a!|bB)", "(?:AB|!!)"
    })
    void everyGeneratedPositionPrefersReadableCharactersWhenTheWholePatternAllowsIt(String expression) throws Exception {
        for (String answer : draws(expression, 96, SEED)) {
            assertNotNull(answer);
            // Rule 1 still explicitly permits unsupported patterns to fall back to length 0.
            if (!answer.isEmpty()) assertReadableMatch(expression, answer);
        }
    }

    @ParameterizedTest(name = "mandatory punctuation survives: {0}")
    @ValueSource(strings = {
            "!", "[!@#]{6}", "[^\\p{L}\\p{N} ]{6}",
            "[A!]{3}-[0?]{3}", "\\([A!]{2}\\)",
            "[A!]{2}\\.[0?]{2}", "[A!]{2}@[B#]{2}\\.com",
            "\\$\\{[A!]{2}\\}", " ! ", "\\t"
    })
    void requiredSymbolsAndWhitespaceArePreservedInsteadOfStrippedOrGloballySanitized(String expression) throws Exception {
        for (String answer : draws(expression, 64, SEED)) {
            assertNotNull(answer);
            if (!answer.isEmpty()) assertReadableMatch(expression, answer);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "^$", "[A!]{0}", "[A!]*", "[A!]?"})
    void emptyAndOptionalPatternsDoNotAcquirePaddingOrUnnecessarySymbols(String expression) throws Exception {
        for (String answer : draws(expression, 96, SEED)) assertReadableMatch(expression, answer);
    }

    @Test void readableChoicesUseFreshRandomCharactersNotOnlyRandomLengths() throws Exception {
        var firstCharacters = new HashSet<Integer>();
        for (String answer : draws(EXAMPLE, 512, SEED)) {
            assertReadableMatch(EXAMPLE, answer);
            firstCharacters.add(answer.codePointAt(0));
        }
        assertTrue(firstCharacters.size() > 1, "A fixed readable substitution is not a random character choice");
    }

    @ParameterizedTest @ValueSource(strings = {"[AB!]{8}", "[01?]{8}", "[A7 !]{8}"})
    void readableChoicesRemainRandomAtEveryPositionOfSupportedFixedLengthPatterns(String expression) throws Exception {
        var answers = draws(expression, 1024, SEED);
        var matches = answers.stream().filter(s -> !s.isEmpty()).toList();
        for (String answer : matches) assertReadableMatch(expression, answer);
        // No uniform law is specified. Require diversity, not equal category/character frequencies.
        if (matches.size() >= 256) {
            for (int position = 0; position < 8; position++) {
                int index = position;
                assertTrue(matches.stream().map(s -> s.charAt(index)).distinct().count() > 1,
                        "Readable choice is constant at position " + position + " for " + expression);
            }
        }
    }

    @Test void sameRandomStateReplaysAllReadableAnswers() throws Exception {
        assertEquals(draws(EXAMPLE, 128, SEED), draws(EXAMPLE, 128, SEED));
    }

    @Test void differentSeedsChangeReadableAnswerSequences() throws Exception {
        assertNotEquals(draws(EXAMPLE, 128, SEED), draws(EXAMPLE, 128, SEED + 1));
    }

    @Test void replayAlsoWorksWithAnAlreadyAdvancedRandomState() throws Exception {
        var first = new Random(SEED);
        var second = new Random(SEED);
        for (int i = 0; i < 37; i++) { first.nextLong(); second.nextLong(); }
        var q = question(QuestionKind.TEXT, text(null, null, EXAMPLE));
        assertEquals(AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, first, 128),
                AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, second, 128));
    }

    @Test @ResourceLock("java.util.Locale.default")
    void readableCharacterChoiceDoesNotDependOnTheDefaultLocale() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            var first = draws(EXAMPLE, 128, SEED);
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(first, draws(EXAMPLE, 128, SEED));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
