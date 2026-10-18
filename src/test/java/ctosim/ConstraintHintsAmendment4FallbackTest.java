package ctosim;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ConstraintHintsAmendment4FallbackTest {
    private static final String IMPOSSIBLE = "^(?=B)A$";

    @ParameterizedTest(name = "exhausted or unusable lookaround pattern: {0}")
    @ValueSource(strings = {
            IMPOSSIBLE, "^(?!A)A$", "^A(?<=B)$", "^A(?<!A)$",
            "^(?=A)(?!A)A$", "^(?=(?=B)A)A$",
            "^(?=A)$", "^(?<=A)A$", "^A(?=B)$",
            "^(?=A)[A&&B]$", "^(?=A[A$"
    })
    void impossibleOrMalformedPatternsTerminateAndUseTheLengthFallback(String expression) throws Exception {
        var g = generator("");
        var q = question(QuestionKind.TEXT, text(6, 6, expression));
        var random = new Random(SEED);
        for (int i = 0; i < 16; i++) {
            String answer = assertDoesNotThrow(() -> g.generate(q, TODAY, random));
            assertNotNull(answer);
            assertTrue(answer.matches("[a-z]{6}"), "Expected length fallback, got " + answer);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"numbers", "numbers_phone"})
    void exhaustedPatternUsesDigitLengthHintsWhenTheAppearanceRequestsThem(String appearance) throws Exception {
        var q = question(QuestionKind.TEXT, appearance, true, text(3, 3, IMPOSSIBLE));
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, 32)) {
            assertTrue(answer.matches("[0-9]{3}"), answer);
        }
    }

    @Test void exhaustedPatternResumesThePrefixRule() throws Exception {
        var hints = new ConstraintHints(null, null, null, null, 4, 4, IMPOSSIBLE,
                "09", Set.of(), Set.of(), null, null, List.of(), List.of());
        var q = question(QuestionKind.TEXT, hints);
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, 32)) {
            assertTrue(answer.matches("09[0-9]{2}"), answer);
        }
    }

    @Test void exhaustedPatternWithoutOtherHintsUsesTheOrdinaryTextRule() throws Exception {
        var q = question(QuestionKind.TEXT, text(null, null, IMPOSSIBLE));
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, 32)) {
            assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
        }
    }

    @Test void anExhaustedPatternDoesNotDisableOtherPatternsOnTheSameGenerator() throws Exception {
        var g = generator("");
        var random = new Random(SEED);
        var impossible = question(QuestionKind.TEXT, text(0, 0, IMPOSSIBLE));
        var possible = question(QuestionKind.TEXT, text(0, 0, "^(?=B)B$"));
        for (int i = 0; i < 16; i++) {
            assertEquals("", g.generate(impossible, TODAY, random));
            assertEquals("B", g.generate(possible, TODAY, random));
        }
    }
}
