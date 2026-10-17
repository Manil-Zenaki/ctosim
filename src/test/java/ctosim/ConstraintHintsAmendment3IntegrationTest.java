package ctosim;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment3PatternTest.*;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment3IntegrationTest {
    @Test void extractedRegexHintsProduceReadableAnswers() throws Exception {
        var hints = extract("regex(., '([^a-z]+)')");
        assertEquals(EXAMPLE, hints.pattern());
        var q = question(QuestionKind.TEXT, hints);
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 128)) {
            assertReadableMatch(EXAMPLE, answer);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "numbers", "numbers_phone"})
    void readabilityStillUsesThePatternAheadOfConflictingLengthAndAppearance(String appearance) throws Exception {
        var q = question(QuestionKind.TEXT, appearance, true, text(0, 0, EXAMPLE));
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 128)) {
            assertReadableMatch(EXAMPLE, answer);
        }
    }

    @Test void fixedValuesAreNotSanitizedEvenWhenTheyMatchThePattern() throws Exception {
        var q = question(QuestionKind.TEXT, text(null, null, EXAMPLE));
        var g = generator("\"fields\":{\"answer\":{\"value\":\"${!\"}}");
        assertEquals("${!", g.generate(q, TODAY, new Random(SEED)));
    }

    @Test void textsOverridesAreNotSanitizedEvenWhenTheyMatchThePattern() throws Exception {
        var q = question(QuestionKind.TEXT, text(null, null, EXAMPLE));
        var g = generator("\"fields\":{\"answer\":{\"texts\":[\"${!\"]}}");
        assertEquals("${!", g.generate(q, TODAY, new Random(SEED)));
    }

    @Test void missingRuleStillRunsBeforePatternReadability() throws Exception {
        var q = question(QuestionKind.TEXT, "", false, text(null, null, EXAMPLE));
        assertNull(generator("\"missingRate\":1").generate(q, TODAY, new Random(SEED)));
    }

    @Test void patternsInsideChosenAlternativesAlsoProduceReadableAnswers() throws Exception {
        var pattern = text(null, null, EXAMPLE);
        var hints = new ConstraintHints(null, null, null, null, null, null, null,
                null, java.util.Set.of(), java.util.Set.of(), null, null,
                List.of(pattern, pattern), List.of());
        var q = question(QuestionKind.TEXT, hints);
        for (String answer : AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 128)) {
            assertReadableMatch(EXAMPLE, answer);
        }
    }

    @Test void walkerCanDisplayPatternAnswersInALaterQuestionLabel() throws Exception {
        LoadedForm form;
        try (var in = getClass().getResourceAsStream("/ctosim/constraint-hints/amendment3/readable-label.xml")) {
            assertNotNull(in);
            form = FormLoader.load(in, "readable-label.xml");
        }
        var walker = new FormWalker(form, FormWalkerTestSupport.completeConfig());
        for (int number = 1; number <= 50; number++) {
            int submissionNumber = number;
            Submission submission = assertDoesNotThrow(() -> walker.walk(submissionNumber));
            assertReadableMatch(EXAMPLE, FormWalkerTestSupport.value(submission, "answer"));
            assertEquals("1", FormWalkerTestSupport.value(submission, "confirm"));
            assertEquals(2, submission.answered());
            assertTrue(submission.warnings().isEmpty(), submission.warnings().toString());
        }
    }
}
