package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment8TextTest {
    @Test void accentsCaseAndWhitespaceAreNormalizedBeforeTheHalfProbabilityTypo() throws Exception {
        var f = pair(false, "", "", "a", "  ÉLÉPHANT&#x9;  À&#xA; CAFÉ  ", "O", "Other");
        var r = other(f, 4800, codes("O") + "," + fixed("answer", "a"));
        singleRecords(r, "a", Set.of("O"));
        String normalized = "elephant a cafe";
        int typos = 0;
        for (var p : others(r)) {
            String text = value(r.target(p), "specify");
            if (!normalized.equals(text)) {
                typos++;
                assertTrue(oneNoninitialLetterDeleted(normalized, text), "exactly one noninitial letter removed: " + text);
            }
        }
        rate(typos, others(r).size(), .5);
    }

    @ParameterizedTest @ValueSource(strings = {"A", "AB", "ABC", "A--B 3 C", "1234"})
    void fewerThanFourLettersCannotReceiveATypoEvenWhenTheLabelIsLong(String label) throws Exception {
        var r = other(pair(false, "", "", "a", label, "O", "Other"), 500, codes("O") + "," + fixed("answer", "a"));
        assertFalse(others(r).isEmpty());
        for (var p : others(r)) assertEquals(label.toLowerCase(Locale.ROOT), value(r.target(p), "specify"));
    }

    @Test void exactlyFourLettersCanLoseOneLetterButNeverTheFirst() throws Exception {
        var r = other(pair(false, "", "", "a", "ABCD", "O", "Other"), 3200, codes("O") + "," + fixed("answer", "a"));
        int typos = 0;
        assertFalse(others(r).isEmpty());
        for (var p : others(r)) {
            String text = value(r.target(p), "specify");
            assertTrue(Set.of("abcd", "acd", "abd", "abc").contains(text), text);
            if (!text.equals("abcd")) typos++;
        }
        rate(typos, others(r).size(), .5);
    }

    @Test void defaultLanguageAndDisplayedOutputAreUsedInsteadOfChoiceCodesOrAnotherTranslation() throws Exception {
        LoadedForm f;
        try (var in = getClass().getResourceAsStream("/ctosim/injected-problems/amendment8/displayed-label.xml")) {
            assertNotNull(in); f = FormLoader.load(in, "displayed-label.xml");
        }
        var r = other(f, 800, codes("O") + "," + fixed("prefix", "42", "answer", "not_the_label"));
        singleRecords(r, "not_the_label", Set.of("O"));
        for (var p : others(r)) {
            String text = value(r.target(p), "specify");
            assertTrue(text.equals("cafe 42") || oneNoninitialLetterDeleted("cafe 42", text), text);
        }
    }

    @Test void rejectedTypoRetriesTheUnalteredLabel() throws Exception {
        var f = pair(false, "", "constraint=\". = 'abcde' or . = 'usual'\"", "a", "abcde", "O", "Other");
        var r = other(f, 1600, codes("O") + "," + fixed("answer", "a", "specify", "usual"));
        singleRecords(r, "a", Set.of("O"));
        for (var p : others(r)) assertEquals("abcde", value(r.target(p), "specify"));
    }

    @Test void rejectingBothLabelAttemptsUsesTheUsualTextButKeepsTheSelectChangeRecord() throws Exception {
        var f = pair(false, "", "constraint=\". = 'usual'\"", "a", "abcde", "O", "Other");
        var r = other(f, 800, codes("O") + "," + fixed("answer", "a", "specify", "usual"));
        singleRecords(r, "a", Set.of("O"));
        rate(others(r).size(), 400, .25);
        for (var p : others(r)) assertEquals("usual", value(r.target(p), "specify"));
    }

    @Test void specifyConstraintUsesTheChangedSelectAnswer() throws Exception {
        var f = pair(false, "", "constraint=\". = 'usual' or (. = 'abc' and /data/answer = 'O')\"",
                "a", "abc", "O", "Other");
        var r = other(f, 800, codes("O") + "," + fixed("answer", "a", "specify", "usual"));
        singleRecords(r, "a", Set.of("O"));
        for (var p : others(r)) assertEquals("abc", value(r.target(p), "specify"));
    }
}
