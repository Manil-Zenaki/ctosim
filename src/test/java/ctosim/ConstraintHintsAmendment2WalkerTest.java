package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment2Support.*;
import static ctosim.ConstraintHintsTestSupport.attribute;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(60)
class ConstraintHintsAmendment2WalkerTest {
    @ParameterizedTest @CsvSource({"int,. = -999,-999", "decimal,. = 2.5,2.5", "string,. = 'café',café"})
    void candidatesRescueRejectedFixedAnswersUsingTheQuestionsType(String type, String expression, String expected) throws Exception {
        var loaded = single(type, "required=\"true()\" constraint=\"" + attribute(expression) + "\"");
        var s = fixed(loaded, "0");
        assertEquals(expected, value(s, "answer"));
        assertEquals(1, s.answered()); assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void rejectedEarlierCandidateDoesNotPreventALaterCandidateBeingAccepted() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\"" + attribute("(. = 7 or . = 9) and . * 2 = 18") + "\"");
        var s = fixed(loaded, "0");
        assertEquals("9", value(s, "answer"));
        assertEquals(1, s.answered()); assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void firstAcceptedCandidateWinsInConstraintOrder() throws Exception {
        var loaded = single("string", "required=\"true()\" constraint=\". = 'first' or . = 'second'\"");
        var s = fixed(loaded, "neither");
        assertEquals("first", value(s, "answer")); assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void closedIntegerEndpointCandidatesAreTriedAfterAllGeneratorProposalsFail() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\"" + attribute(". > 100 and . < 105 and . * 2 = 208") + "\"");
        var s = fixed(loaded, "0");
        assertEquals("104", value(s, "answer")); assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectCandidatesAreConvertedThroughTheOfferedChoices(boolean multiple) throws Exception {
        String wanted = multiple ? "b c" : "b";
        var loaded = form("<answer/>", constraint("answer", multiple ? "select" : "select1", ". = '" + wanted + "'"),
                selectControl("answer", multiple, "a", "b", "c"));
        var s = fixed(loaded, "a");
        assertEquals(wanted, value(s, "answer")); assertEquals(1, s.answered());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void candidatesAreNotTriedWhenAnOrdinaryProposalIsAccepted() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\". &gt;= 1 and . &lt;= 10\"");
        var s = fixed(loaded, "5");
        assertEquals("5", value(s, "answer")); assertEquals(1, s.answered());
    }

    @Test void impossibleIntegerConstraintLeavesBlankAndProducesConstraintAndRequiredWarningsAfterFallbacks() throws Exception {
        // Candidates 100 and 104 fail, and no integer can satisfy twice its value being 207.
        var loaded = single("int", "required=\"true()\" constraint=\"" + attribute(". >= 100 and . <= 104 and . * 2 = 207") + "\"");
        var s = fixed(loaded, "0");
        assertEquals("", value(s, "answer")); assertEquals(0, s.answered());
        assertWarning(s, "answer", "constraint"); assertWarning(s, "answer", "required");
    }

    @Test void failedDrawsAndMultipleCandidateAttemptsAdvanceTheClockOnlyOnce() throws Exception {
        var loaded = single("int", "required=\"true()\" xmlns:sim=\"urn:ctosim:xforms\" sim:minimum-seconds=\"61\" constraint=\""
                + attribute("(. = 7 or . = 9) and . * 2 = 18") + "\"");
        var s = fixed(loaded, "0");
        assertEquals("9", value(s, "answer")); assertEquals(61, seconds(s));
    }

    @Test void finalCheckRecomputesConditionalCandidatesAndRepairsWithoutMovingTheClockOrAnsweredCount() throws Exception {
        LoadedForm loaded;
        try (var in = getClass().getResourceAsStream("/ctosim/constraint-hints/amendment2/late-candidate.xml")) {
            assertNotNull(in); loaded = loadXml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        // No answer override: the valid default is kept at navigation. The default text generator
        // produces "answer N", so all final-repair proposals fail the literal equality.
        var s = new FormWalker(loaded, config("\"missingRate\":0,\"fields\":{\"gate\":{\"value\":1}}" )).walk(1);
        assertEquals("repaired", value(s, "answer"));
        assertEquals(1, s.answered()); assertEquals(61, seconds(s));
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void commonConstraintShapesReachValidAnswersWithoutConstraintWarnings() throws Exception {
        var loaded = form("<number/><phone/><multiple/><single/>",
                constraint("number", "int", "(. >= 700 and . <= 710) or . = 999 or . = 888")
                + constraint("phone", "string", "substr(., 0, 1) = '0' and string-length(.) = 10")
                + constraint("multiple", "select", "if(selected(., '-99'), count-selected(.) = 1, count-selected(.) >= 2)")
                + constraint("single", "select1", "not(selected(., 'bad'))"),
                input("number") + input("phone") + selectControl("multiple", true, "a", "b", "c", "-99")
                + selectControl("single", false, "bad", "good"));
        var walker = new FormWalker(loaded, completeConfig());
        for (int n = 1; n <= 30; n++) {
            var s = walker.walk(n);
            assertFalse(value(s, "number").isEmpty(), "The common range-or-code constraint must be answered");
            int number = Integer.parseInt(value(s, "number"));
            assertTrue(number >= 700 && number <= 710 || number == 999 || number == 888);
            assertTrue(value(s, "phone").matches("0[0-9]{9}"));
            var choices = Arrays.asList(value(s, "multiple").split(" "));
            if (choices.contains("-99")) assertEquals(1, choices.size()); else assertTrue(choices.size() >= 2);
            assertEquals("good", value(s, "single"));
            assertEquals(4, s.answered()); assertTrue(s.warnings().isEmpty(), s.warnings().toString());
        }
    }
}
