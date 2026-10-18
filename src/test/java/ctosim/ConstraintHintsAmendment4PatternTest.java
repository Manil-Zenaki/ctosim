package ctosim;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 09 Amendment 4: only public generator results and Java full matches are observed. */
@Timeout(20)
class ConstraintHintsAmendment4PatternTest {
    static final String EXAMPLE = "^(?![0-9]+$)[A-Z0-9 ]{5,100}$";

    static List<String> draws(String expression, int count, long seed) throws Exception {
        // Empty is the documented length fallback, distinguishable from nonempty matches.
        return AnswerGeneratorTestSupport.draws(generator(""),
                question(QuestionKind.TEXT, text(0, 0, expression)), TODAY, new Random(seed), count);
    }

    static void assertFullMatch(String expression, String answer) {
        assertNotNull(answer);
        assertTrue(Pattern.matches(expression, answer),
                () -> "Expected a full match for " + expression + ", got <" + answer + ">");
    }

    @Test void motivatingRealFormPatternProducesCapitalsDigitsAndSpacesButNotOnlyDigits() throws Exception {
        for (String answer : draws(EXAMPLE, 256, SEED)) assertFullMatch(EXAMPLE, answer);
    }

    @ParameterizedTest(name = "assertion removal preserves the consuming pattern: {0}")
    @ValueSource(strings = {
            "^(?=A)A$", "^(?!B)A$", "^A(?<=A)$", "^A(?<!B)$",
            "^(?=AB)A(?=B)B$", "^A(?<=A)B(?<!C)$",
            "^(?=A)(?!B)A(?<=A)(?<!B)$",
            "^(?=(?:AB|AC)$)A[BC]$",
            "^(?=(?=A)A)A$", "^(?!(?!A))A$",
            "^A(?<=(?:A))$", "^A(?<!(?:B|C))$",
            "^(?=[()A]$)A$", "^(?![()]$)A$",
            "^(?=\\(A\\)$)\\(A\\)$",
            "^(?:A(?=B)B|C(?=D)D)$",
            "^(?=A)(?:AB){2,4}$", "^(?=A)A[AB]{99}$",
            "^(?=A)(?:A)?A$",
            "^(?=A)A[(?=)]$", "^(?=A)A\\(\\?=Z\\)$"
    })
    void allFourAssertionsNestedGroupsEscapesAndNonassertionGroupsRemainUsable(String expression) throws Exception {
        // Every possible draw of the stripped pattern satisfies the original assertion.
        for (String answer : draws(expression, 32, SEED)) assertFullMatch(expression, answer);
    }

    @Test void quotedAssertionLikeTextIsPreservedWhenTheConsumingPatternIsSupported() throws Exception {
        String consuming = "A\\Q(?=literal)\\E";
        String expression = "^(?=A)" + consuming + "$";
        boolean supported = draws(consuming, 16, SEED).stream().anyMatch(s -> !s.isEmpty());
        for (String answer : draws(expression, 32, SEED)) {
            assertNotNull(answer);
            if (supported || !answer.isEmpty()) assertFullMatch(expression, answer);
        }
    }

    @ParameterizedTest(name = "original assertion filters proposals: {0}")
    @ValueSource(strings = {
            "^(?=A)[AB]{4}$", "^(?!A)[AB]{4}$",
            "^[AB]{4}(?<=A)$", "^[AB]{4}(?<!A)$",
            "^(?=A)[AB]{4}(?<=B)$", "(?!AB)[AB]{2}",
            "^(?=AB|BA)[AB]{2}$"
    })
    void rejectedStrippedDrawsNeverEscapeAsAnswersAndLaterDrawsCanSucceed(String expression) throws Exception {
        int matches = 0;
        for (String answer : draws(expression, 256, SEED)) {
            assertNotNull(answer);
            // Exhausting the bounded retry budget is allowed, even for a satisfiable pattern.
            if (!answer.isEmpty()) {
                assertFullMatch(expression, answer);
                matches++;
            }
        }
        assertTrue(matches > 0, "Lookaround must not cause immediate unconditional fallback");
    }

    @ParameterizedTest(name = "readability after lookaround fallback: {0}")
    @ValueSource(strings = {
            "^(?=.{8}$)[AB!]{8}$", "^(?!z)[7@]{8}$",
            "^[ !]{8}(?<!z)$", "^(?=.{8}$)[\u00e9!]{8}$",
            "^(?=.{8}$)[\u0665!]{8}$", "^(?=.{8}$)[\\t ]{8}$",
            "^(?=.{5}$)![AB!]{4}$", "^(?=.{4}$)\\$\\{[AB!]\\}$"
    })
    void readableSubstitutionsAndRequiredPunctuationUseTheWholeOriginalPattern(String expression) throws Exception {
        for (String answer : draws(expression, 64, SEED)) {
            ConstraintHintsAmendment3PatternTest.assertReadableMatch(expression, answer);
        }
    }

    @Test void punctuationRequiredOnlyByAnAssertionIsNotReturnedAsAnInvalidReadableReplacement() throws Exception {
        String expression = "^(?=.*!)[AB!]{4}$";
        for (String answer : draws(expression, 128, SEED)) {
            assertNotNull(answer);
            if (!answer.isEmpty()) {
                ConstraintHintsAmendment3PatternTest.assertReadableMatch(expression, answer);
            }
        }
    }

    @Test void aSuccessfulEmptyMatchIsNotMistakenForFailedPatternGeneration() throws Exception {
        var q = question(QuestionKind.TEXT, text(6, 6, "^(?!A)$"));
        var g = generator("");
        var random = new Random(SEED);
        for (int i = 0; i < 32; i++) assertEquals("", g.generate(q, TODAY, random));
    }

    @ParameterizedTest @ValueSource(strings = {EXAMPLE, "^(?=A)[AB]{4}$", "^(?=B)A$"})
    void sameAlreadyAdvancedRandomStateReplaysSuccessRejectionAndExhaustion(String expression) throws Exception {
        var first = new Random(SEED);
        var second = new Random(SEED);
        for (int i = 0; i < 37; i++) { first.nextLong(); second.nextLong(); }
        var q = question(QuestionKind.TEXT, text(5, 5, expression));
        assertEquals(AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, first, 128),
                AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, second, 128));
        assertEquals(first.nextLong(), second.nextLong(), "Replay also leaves the random state equal");
    }

    @Test void acceptedLookaroundAnswersRemainRandomAcrossCallsAndSeeds() throws Exception {
        String expression = "^(?=A)A[BC]{8}$";
        var first = draws(expression, 128, SEED);
        var second = draws(expression, 128, SEED + 1);
        for (String answer : first) assertFullMatch(expression, answer);
        for (String answer : second) assertFullMatch(expression, answer);
        assertTrue(first.stream().distinct().count() > 1, "Fresh calls must draw fresh answers");
        assertNotEquals(first, second, "A fixed repair string must not replace random drawing");
    }
}
