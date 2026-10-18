package ctosim;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment4PatternTest.*;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment4IntegrationTest {
    @ParameterizedTest @ValueSource(strings = {
            EXAMPLE, "^(?=A)A[BC]{3}$", "^(?!Z)A[BC]{3}$",
            "^[BC]{3}A(?<=A)$", "^[BC]{3}A(?<!Z)$"
    })
    void extractedRegexRetainsAssertionsAndGeneratesFullMatches(String expression) throws Exception {
        var hints = extract("regex(., '" + expression + "')");
        assertEquals(expression, hints.pattern());
        var q = question(QuestionKind.TEXT, hints);
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, 64)) {
            assertFullMatch(expression, answer);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "numbers", "numbers_phone"})
    void successfulLookaroundPatternStillTakesPriorityOverLengthAndAppearance(String appearance) throws Exception {
        var q = question(QuestionKind.TEXT, appearance, true, text(0, 0, EXAMPLE));
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, 64)) {
            assertFullMatch(EXAMPLE, answer);
        }
    }

    @Test void fixedOverrideStillWinsOverLookaroundGeneration() throws Exception {
        var q = question(QuestionKind.TEXT, text(null, null, EXAMPLE));
        assertEquals("fixed!", generator("\"fields\":{\"answer\":{\"value\":\"fixed!\"}}")
                .generate(q, TODAY, new Random(SEED)));
    }

    @Test void textsOverrideStillWinsOverLookaroundGeneration() throws Exception {
        var q = question(QuestionKind.TEXT, text(null, null, EXAMPLE));
        assertEquals("text!", generator("\"fields\":{\"answer\":{\"texts\":[\"text!\"]}}")
                .generate(q, TODAY, new Random(SEED)));
    }

    @Test void missingRuleStillRunsBeforeLookaroundGeneration() throws Exception {
        var q = question(QuestionKind.TEXT, "", false, text(null, null, EXAMPLE));
        assertNull(generator("\"missingRate\":1").generate(q, TODAY, new Random(SEED)));
    }

    @Test void lookaroundPatternsAlsoWorkInsideChosenAlternatives() throws Exception {
        var branch = text(0, 0, EXAMPLE);
        var hints = new ConstraintHints(null, null, null, null, null, null, null,
                null, Set.of(), Set.of(), null, null, List.of(branch, branch), List.of());
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), question(QuestionKind.TEXT, hints), 64)) {
            assertFullMatch(EXAMPLE, answer);
        }
    }

    @Test void walkerAnswersLookaroundConstrainedQuestionsWithoutConstraintWarnings() throws Exception {
        LoadedForm form;
        try (var in = getClass().getResourceAsStream("/ctosim/constraint-hints/amendment4/lookarounds.xml")) {
            assertNotNull(in);
            form = FormLoader.load(in, "lookarounds.xml");
        }
        var walker = new FormWalker(form, FormWalkerTestSupport.completeConfig());
        for (int number = 1; number <= 50; number++) {
            Submission submission = walker.walk(number);
            assertFullMatch(EXAMPLE, FormWalkerTestSupport.value(submission, "answer"));
            assertFullMatch("^(?=AB)AB[CD]{3}$", FormWalkerTestSupport.value(submission, "ahead"));
            assertFullMatch("^[CD]{3}AB(?<=AB)$", FormWalkerTestSupport.value(submission, "behind"));
            assertFullMatch("^[CD]{3}AB(?<!ZZ)$", FormWalkerTestSupport.value(submission, "notbehind"));
            assertEquals(4, submission.answered());
            assertTrue(submission.warnings().isEmpty(), submission.warnings().toString());
        }
    }
}
