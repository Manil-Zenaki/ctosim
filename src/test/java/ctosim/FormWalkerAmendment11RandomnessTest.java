package ctosim;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment11Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(180)
class FormWalkerAmendment11RandomnessTest {
    @ParameterizedTest @ValueSource(doubles = {0.2, 0.7})
    void theRateAppliesToTheWholeChainRatherThanEachQuestionIndependently(double rate) throws Exception {
        checkChainRate(settings11(consent11(rate, "questions_answered", "understood", "consent")), rate, 900);
    }

    @Test void anOmittedConsentRateUsesTheConfiguredDefaultOfNinetyPercent() throws Exception {
        checkChainRate(settings11("\"consent\":{\"fields\":[\"questions_answered\",\"understood\",\"consent\"]}"), 0.9, 900);
    }

    private static void checkChainRate(SimulationConfig config, double rate, int draws) throws Exception {
        FormWalker walker = new FormWalker(fixture11("chain"), config);
        int consents = 0;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            assertEquals("1", value(result, "questions_answered"));
            assertEquals("1", value(result, "understood"));
            String consent = value(result, "consent");
            assertTrue(Set.of("0", "1").contains(consent), "Every chain reaches its last question");
            if (consent.equals("1")) consents++;
            clean11(result);
        }
        assertTrue(consents > 0 && consents < draws);
        assertUniformBin(consents, draws, rate, "Whole-submission consent rate (fixed seed, seven-sigma tolerance)");
    }

    @Test void consentReplayDoesNotDependOnWalkOrderOrOtherWalkers() throws Exception {
        String members = consent11(0.63, "questions_answered", "understood", "consent");
        FormWalker first = new FormWalker(fixture11("chain"), settings11(members));
        FormWalker second = new FormWalker(fixture11("chain"), settings11(members));
        FormWalker unrelated = new FormWalker(fixture11("chain"), settings11(members).withSeed(88891));
        for (int n : new int[] {31, 1, 80, 19, 31, 2}) {
            Submission expected = first.walk(n);
            unrelated.walk(n);
            second.walk(n + 100);
            same11(expected, second.walk(n));
            same11(expected, first.walk(n));
        }
    }

    @Test void changingSeedChangesTheConsentSequenceAndSubmissionNumbersCanHaveBothOutcomes() throws Exception {
        SimulationConfig cfg = settings11(consent11(0.5, "answer"));
        FormWalker first = new FormWalker(choice11("", "0", "1"), cfg);
        FormWalker second = new FormWalker(choice11("", "0", "1"), cfg.withSeed(909321));
        Set<String> outcomes = new HashSet<>();
        boolean differs = false;
        for (int n = 1; n <= 80; n++) {
            String answer = value(first.walk(n), "answer");
            outcomes.add(answer);
            differs |= !answer.equals(value(second.walk(n), "answer"));
        }
        assertEquals(Set.of("0", "1"), outcomes);
        assertTrue(differs, "Independent seeds should not produce the same 80 consent decisions");
    }

    @Test void extraAnswersAndRepeatDrawsBeforeConsentCannotChangeItsDecision() throws Exception {
        LoadedForm noisy = form("<noise/><row jr:template=\"\"><item/></row><answer/>",
                bind("noise", "int", "") + bind("row/item", "string", "") + bind("answer", "string", ""),
                input("noise") + "<repeat nodeset=\"/data/row\">" + input("row/item") + "</repeat>"
                + choices11("answer", "0", "1"));
        SimulationConfig cfg = settings11(consent11(0.43, "answer") + ",\"repeats\":{\"min\":1,\"max\":4}");
        FormWalker shortWalker = new FormWalker(choice11("", "0", "1"), cfg);
        FormWalker noisyWalker = new FormWalker(noisy, cfg);
        for (int n = 1; n <= 60; n++) {
            assertEquals(value(shortWalker.walk(n), "answer"), value(noisyWalker.walk(n), "answer"),
                    "Consent is a submission decision, apart from answer draws");
        }
    }

    @Test void drawingConsentForAnUnansweredFieldDoesNotConsumeTheAnswersRandomSequence() throws Exception {
        LoadedForm form = form("<unused>note</unused><a/><b/><c/>", bind("unused", "string", "readonly=\"true()\"")
                + bind("a", "int", "") + bind("b", "int", "") + bind("c", "int", ""),
                input("unused") + input("a") + input("b") + input("c"));
        FormWalker plain = new FormWalker(form, settings11(""));
        FormWalker consent = new FormWalker(form, settings11(consent11(0.37, "unused")));
        for (int n = 1; n <= 40; n++) {
            Submission expected = plain.walk(n), actual = consent.walk(n);
            for (String field : new String[] {"a", "b", "c"}) assertEquals(value(expected, field), value(actual, field));
            clean11(actual);
        }
    }

    @Test void aRepeatDoesNotRedrawConsentForEachInstance() throws Exception {
        FormWalker walker = new FormWalker(fixture11("repeats"), settings11(consent11(0.5, "understood", "consent")));
        Set<String> outcomes = new HashSet<>();
        for (int n = 1; n <= 60; n++) {
            Submission result = walker.walk(n);
            String first = value(result, "row[1]/consent");
            outcomes.add(first);
            for (int i = 1; i <= 3; i++) {
                assertEquals("1", value(result, "row[" + i + "]/understood"));
                assertEquals(first, value(result, "row[" + i + "]/consent"));
            }
        }
        assertEquals(Set.of("0", "1"), outcomes);
    }
}
