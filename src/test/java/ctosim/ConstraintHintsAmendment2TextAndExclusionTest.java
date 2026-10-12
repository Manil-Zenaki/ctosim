package ctosim;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment2Support.*;
import static ctosim.ConstraintHintsTestSupport.*;
import static ctosim.FormWalkerTestSupport.assertUniformBin;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment2TextAndExclusionTest {
    static Stream<Arguments> prefixes() {
        return Stream.of(Arguments.of("09", "", null, null, "09[0-9]{8}"),
                Arguments.of("ab", "", null, null, "ab[a-z]{8}"),
                Arguments.of("é-", "", 5, 5, "é-[a-z]{3}"),
                Arguments.of("x", "numbers", 5, 5, "x[0-9]{4}"),
                Arguments.of("x", "minimal numbers_phone", 5, 5, "x[0-9]{4}"),
                Arguments.of("a9", "", 3, 3, "a9[a-z]"),
                Arguments.of("09", "", 2, 2, "09"),
                Arguments.of("12345", "", 8, null, "12345[0-9]{3,5}"),
                Arguments.of("p", "", 12, null, "p[a-z]{11}"),
                Arguments.of("x", "", null, 3, "x[a-z]{0,2}"));
    }

    @ParameterizedTest @MethodSource("prefixes")
    void prefixUsesWholeAnswerLengthAndTheSpecifiedSuffixAlphabet(String prefix, String appearance,
            Integer min, Integer max, String regex) throws Exception {
        var q = question(QuestionKind.TEXT, appearance, true, hints().prefix(prefix).length(min, max).build());
        var generator = generator(""); var random = new Random(SEED);
        for (int i = 0; i < 500; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertTrue(answer.matches(regex), answer);
        }
    }

    @Test void prefixTotalLengthsAreUniformAndIncludeBothEndpoints() throws Exception {
        var q = question(QuestionKind.TEXT, hints().prefix("09").length(2, 6).build());
        var generator = generator(""); var random = new Random(SEED);
        int n = 16000; int[] sizes = new int[5];
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertTrue(answer.matches("09[0-9]{0,4}"), answer);
            sizes[answer.length() - 2]++;
        }
        for (int i = 0; i < sizes.length; i++) assertUniformBin(sizes[i], n, .2, "total length " + (i + 2));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eachSuffixPositionUsesUniformCharacters(boolean numeric) throws Exception {
        String prefix = numeric ? "09" : "ab";
        int alphabet = numeric ? 10 : 26; char base = numeric ? '0' : 'a';
        var generator = generator(""); var random = new Random(SEED);
        var q = question(QuestionKind.TEXT, hints().prefix(prefix).length(5, 5).build());
        int n = 16000; int[][] bins = new int[3][alphabet];
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertEquals(5, answer.length()); assertTrue(answer.startsWith(prefix));
            for (int j = 0; j < 3; j++) {
                int index = answer.charAt(j + 2) - base;
                assertTrue(index >= 0 && index < alphabet, answer); bins[j][index]++;
            }
        }
        for (int j = 0; j < 3; j++) for (int c = 0; c < alphabet; c++)
            assertUniformBin(bins[j][c], n, 1d / alphabet, "position " + j + " character " + c);
    }

    @Test void usablePatternTakesPrecedenceOverAConflictingPrefix() throws Exception {
        var q = question(QuestionKind.TEXT, hints().prefix("xx").pattern("^09[0-9]{8}$").build());
        var generator = generator(""); var random = new Random(SEED);
        for (int i = 0; i < 100; i++) assertTrue(generator.generate(q, TODAY, random).matches("09[0-9]{8}"));
    }

    @Test void textsOverrideIgnoresPrefixAndLengthHints() throws Exception {
        var q = question(QuestionKind.TEXT, hints().prefix("09").length(10, 10).build());
        var generator = generator("\"fields\":{\"answer\":{\"texts\":[\"plain\",\"café\"]}}");
        var actual = new Random(SEED); var expected = new Random(SEED);
        for (int i = 0; i < 100; i++) assertEquals(generator.generate(question(QuestionKind.TEXT, ConstraintHints.none()), TODAY, expected),
                generator.generate(q, TODAY, actual));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void excludedNumbersAreRedrawnAndKeepOriginalFormatting(QuestionKind kind) throws Exception {
        var h = hints().range(0, 9).exclude("0").build();
        var generator = generator(""); var random = new Random(SEED);
        var q = question(kind, h);
        for (int i = 0; i < 2000; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertNotEquals("0", answer);
            assertTrue(Double.parseDouble(answer) > 0 && Double.parseDouble(answer) <= 9, answer);
            if (kind == QuestionKind.INTEGER) assertTrue(answer.matches("[1-9]"), answer);
        }
    }

    @Test void textExclusionRetriesAfterPrefixGeneration() throws Exception {
        var h = hints().prefix("09").length(3, 3).exclude("090", "091").build();
        var q = question(QuestionKind.TEXT, h);
        var generator = generator(""); var random = new Random(SEED);
        for (int i = 0; i < 2000; i++) assertTrue(generator.generate(q, TODAY, random).matches("09[2-9]"));
    }

    @Test void exclusionRetriesAlsoApplyToOriginalTextRules() throws Exception {
        var generator = generator("");
        var plain = question(QuestionKind.TEXT, ConstraintHints.none());
        String first = generator.generate(plain, TODAY, new Random(SEED));
        var h = hints().exclude(first).build();
        assertNotEquals(first, generator.generate(question(QuestionKind.TEXT, h), TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT"})
    @Timeout(5)
    void entirelyExcludedDomainsTerminateWithinTheRetryBudget(QuestionKind kind) throws Exception {
        var h = kind == QuestionKind.TEXT ? hints().prefix("x").length(1, 1).exclude("x").build()
                : hints().range(7, 7).exclude("7").build();
        // The spec does not prescribe the value returned after exhaustion, only bounded retrying.
        assertDoesNotThrow(() -> generator("").generate(question(kind, h), TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT"})
    void fixedValueStillWinsOverExclusion(QuestionKind kind) throws Exception {
        var h = hints().exclude("7").prefix("09").build();
        assertEquals("7", generator("\"fields\":{\"answer\":{\"value\":7}}")
                .generate(question(kind, h), TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME", "TIME", "BARCODE", "IMAGE", "GEOPOINT", "ACKNOWLEDGE"})
    void inapplicableNewHintsDoNotChangeOriginalDraws(QuestionKind kind) throws Exception {
        var generator = generator("");
        String first = generator.generate(question(kind, ConstraintHints.none()), TODAY, new Random(SEED));
        var h = hints().prefix("09").exclude(first).include("a").count(9, 9).build();
        var actual = new Random(SEED); var expected = new Random(SEED);
        for (int i = 0; i < 50; i++) assertEquals(generator.generate(question(kind, ConstraintHints.none()), TODAY, expected),
                generator.generate(question(kind, h), TODAY, actual));
    }

    @Test void prefixAndExclusionDrawingIsDeterministic() throws Exception {
        var q = question(QuestionKind.TEXT, hints().prefix("09").length(3, 7).exclude("090").build());
        var a = generator(""); var b = generator(""); var ra = new Random(SEED); var rb = new Random(SEED);
        for (int i = 0; i < 1000; i++) assertEquals(a.generate(q, TODAY, ra), b.generate(q, TODAY, rb));
    }
}
