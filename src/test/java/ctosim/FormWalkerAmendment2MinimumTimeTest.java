package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static ctosim.FormWalkerAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendment2MinimumTimeTest {
    @ParameterizedTest
    @CsvSource({"60,60", "60.0001,61", "61,61", "61.01,62", "120.9,121", "86400.1,86401"})
    void minimumAtOrAboveTheDrawCeilingRoundsUpWithoutAnExtraDelay(String minimum, long expected) throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"" + minimum + "\""), settings(0));
        for (int n = 1; n <= 12; n++) assertEquals(expected, seconds(walker.walk(n)));
    }

    @Test void intermediateMinimumClampsTheUniformDrawInsteadOfResamplingAboveTheMinimum() throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"20.1\""), settings(0));
        int draws = 2800;
        int[] frequencies = new int[61];
        for (int n = 1; n <= draws; n++) {
            long elapsed = seconds(walker.walk(n));
            assertTrue(elapsed >= 21 && elapsed <= 60, "ceil(20.1) <= elapsed <= 60");
            frequencies[(int) elapsed]++;
        }
        // Original draws 5..21 (17 of 56 integers) all become 21.
        assertUniformBin(frequencies[21], draws, 17.0 / 56, "clamped mass");
        for (int s = 22; s <= 60; s++) assertUniformBin(frequencies[s], draws, 1.0 / 56, "seconds " + s);
        assertTrue(frequencies[60] > 0, "Upper endpoint remains attainable");
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-a-number", "1 + 90", "-10", "0", "0.01", "4.9", "5"})
    void invalidOrLowMinimumKeepsTheOrdinaryFiveToSixtySecondRange(String minimum) throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"" + minimum + "\""), settings(0));
        int min = 60;
        int max = 5;
        for (int n = 1; n <= 1120; n++) {
            int elapsed = (int) seconds(walker.walk(n));
            assertTrue(elapsed >= 5 && elapsed <= 60);
            min = Math.min(min, elapsed);
            max = Math.max(max, elapsed);
        }
        assertEquals(5, min);
        assertEquals(60, max);
    }

    @ParameterizedTest @ValueSource(strings = {"ct", "alias"})
    void minimumAttributeIsRecognizedByNamespaceUri(String prefix) throws Exception {
        assertEquals(91, seconds(new FormWalker(timed(prefix + ":minimum-seconds=\"90.1\""), settings(0)).walk(1)));
    }

    @ParameterizedTest @ValueSource(strings = {"minimum-seconds", "other:minimum-seconds"})
    void similarlyNamedAttributesOutsideTheSpecifiedNamespaceDoNotSetAMinimum(String attribute) throws Exception {
        Submission result = new FormWalker(timed(attribute + "=\"100\""), settings(1)).walk(1);
        assertElapsed(result, 1);
        assertEquals("", value(result, "violations"));
    }

    @ParameterizedTest @ValueSource(strings = {"blank", "conversion", "constraint", "accepted"})
    void everyHandledOutcomeAdvancesOnceAndCanRecordOnlyOneViolation(String outcome) throws Exception {
        String attrs = "ct:minimum-seconds=\"70.1\"";
        if (outcome.equals("constraint")) attrs += " constraint=\"false()\"";
        LoadedForm form = amended2("<answer/>" + REPORTS,
                bind("answer", "int", attrs) + REPORT_BINDS, input("answer"));
        String fields = switch (outcome) {
            case "blank" -> "\"missingRate\":1";
            case "conversion" -> "\"fields\":{\"answer\":{\"value\":\"invalid-integer\"}}";
            default -> "\"fields\":{\"answer\":{\"value\":7}}";
        };
        Submission result = walk(form, "\"speedViolationRate\":1," + fields);
        assertEquals(71, seconds(result), "Even fifty failed attempts consume one handled-question advance");
        assertEquals(1, integer(result, "violations"));
        assertEquals("answer", value(result, "violated"));
        assertEquals(outcome.equals("accepted") ? 1 : 0, result.answered());
        assertEquals(outcome.equals("accepted") ? "7" : "", value(result, "answer"));
    }

    @ParameterizedTest @ValueSource(strings = {"readonly", "existing", "unsupported", "irrelevant", "calculateHere", "noControl"})
    void questionsNotHandledUnderRuleFiveOrSixNeitherWaitNorViolate(String kind) throws Exception {
        String attributes = "ct:minimum-seconds=\"100\"";
        String initial = kind.equals("existing") ? "kept" : "";
        String body = input("answer");
        switch (kind) {
            case "readonly" -> attributes += " readonly=\"true()\"";
            case "irrelevant" -> attributes += " relevant=\"false()\"";
            case "calculateHere" -> attributes += " ct:calculate-here=\"'calculated'\"";
            case "unsupported" -> body = "<rank ref=\"/data/answer\"><label>Rank</label>"
                    + "<item><label>A</label><value>a</value></item></rank>";
            case "noControl" -> body = "";
            default -> { }
        }
        LoadedForm form = amended2("<answer>" + initial + "</answer>" + REPORTS,
                bind("answer", "string", attributes) + REPORT_BINDS, body);
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(0, seconds(result));
        assertEquals(0, result.answered());
        assertEquals("", value(result, "violations"));
        assertEquals("", value(result, "violated"));
    }

    @Test void minimumAppliesAgainWhenAnOverrideReplacesAnExistingAnswer() throws Exception {
        LoadedForm form = amended2("<answer>old</answer>" + REPORTS,
                bind("answer", "string", "ct:minimum-seconds=\"75\"") + REPORT_BINDS, input("answer"));
        Submission result = walk(form, "\"speedViolationRate\":1,\"fields\":{\"answer\":{\"value\":\"new\"}}");
        assertEquals(75, seconds(result));
        assertEquals("new", value(result, "answer"));
        assertEquals(1, integer(result, "violations"));
    }

    @Test void finalDurationAndNextQuestionClockSeeTheEnforcedMinimum() throws Exception {
        LoadedForm form = amended2("<first/><snapshot/><last/><elapsed/>",
                bind("first", "string", "ct:minimum-seconds=\"80.1\"")
                + bind("snapshot", "int", "ct:calculate-here=\"duration()\"")
                + bind("last", "string", "ct:minimum-seconds=\"65\"")
                + bind("elapsed", "int", "calculate=\"duration()\""),
                input("first") + input("snapshot") + input("last"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(81, integer(result, "snapshot"));
        assertEquals(146, seconds(result));
        assertEquals(146, integer(result, "elapsed"));
        assertEquals(2, result.answered());
    }
}
