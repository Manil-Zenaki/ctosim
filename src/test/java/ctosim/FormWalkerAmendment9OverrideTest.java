package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment9Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment9OverrideTest {
    @ParameterizedTest @ValueSource(strings = {
            "\"missingRate\":0", "\"texts\":[\"ignored-for-integers\"]", "\"weights\":{\"19\":1}",
            "\"when\":[{\"if\":\"false()\",\"min\":8,\"max\":8}]",
            "\"when\":[{\"if\":\"true()\",\"missingRate\":0}]"})
    void overrideWithoutBoundsStillReceivesRepeatBounds(String members) throws Exception {
        assertCount9(run9(direct9(), 2, 2, field9(members)), 2);
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"min\":7,\"max\":7", "\"min\":6.2,\"max\":7.8",
            "\"min\":0,\"max\":0", "\"min\":7,\"max\":7,\"missingRate\":0"})
    void explicitNumericBoundsArePreservedIncludingFractionalRoundingAndZero(String members) throws Exception {
        int expected = members.contains("\"min\":0") ? 0 : 7;
        assertCount9(run9(direct9(), 2, 2, field9(members)), expected);
    }

    @ParameterizedTest @ValueSource(strings = {"\"value\":7", "\"expr\":\"3 + 4\""})
    void fixedAndExpressionValuesTakePrecedenceOverTheAddedRange(String members) throws Exception {
        assertCount9(run9(direct9(), 2, 2, field9(members)), 7);
    }

    static Stream<Arguments> effectiveBounds() {
        return Stream.of(
                Arguments.of("\"when\":[{\"if\":\"true()\",\"min\":7,\"max\":7}]", 7),
                Arguments.of("\"min\":8,\"max\":8,\"when\":[{\"if\":\"true()\",\"min\":7,\"max\":7}]", 7),
                Arguments.of("\"min\":8,\"max\":8,\"when\":[{\"if\":\"true()\",\"missingRate\":0}]", 8),
                Arguments.of("\"min\":8,\"max\":8,\"when\":[{\"if\":\"false()\",\"min\":7,\"max\":7}]", 8),
                Arguments.of("\"when\":[{\"if\":\"true()\",\"value\":7}]", 7),
                Arguments.of("\"when\":[{\"if\":\"true()\",\"expr\":\"3 + 4\"}]", 7));
    }

    @ParameterizedTest @MethodSource("effectiveBounds")
    void countRangeIsAppliedToTheEffectiveOverrideAfterConditionalRules(String members, int expected) throws Exception {
        assertCount9(run9(direct9(), 2, 2, field9(members)), expected);
    }

    static Stream<Arguments> distributions() {
        return Stream.of(Arguments.of("normal", 4, 4), Arguments.of("normal", -5, 1),
                Arguments.of("normal", 20, 5), Arguments.of("lognormal", 4, 4),
                Arguments.of("lognormal", 20, 5));
    }

    @ParameterizedTest @MethodSource("distributions")
    void addedRangeRetainsDistributionAndClampsItUsingRepeatBounds(String kind, int mean, int expected) throws Exception {
        String override = "\"distribution\":{\"" + kind + "\":{\"mean\":" + mean + ",\"sd\":0}}";
        assertCount9(run9(direct9(), 1, 5, field9(override)), expected);
    }

    @Test void distributionFromAMatchingRuleSurvivesAddedBounds() throws Exception {
        String override = "\"when\":[{\"if\":\"true()\",\"distribution\":{\"normal\":{\"mean\":4,\"sd\":0}}}]";
        assertCount9(run9(direct9(), 1, 5, field9(override)), 4);
    }

    @Test void explicitRangeAndDistributionBothSurvive() throws Exception {
        assertCount9(run9(direct9(), 1, 3, field9(
                "\"min\":6,\"max\":9,\"distribution\":{\"normal\":{\"mean\":8,\"sd\":0}}")), 8);
    }

    @Test void configuredDateBoundsAreNotReplacedEvenThoughIntegerGenerationIgnoresThem() throws Exception {
        assertCount9(run9(direct9(), 2, 2,
                field9("\"min\":\"2026-01-01\",\"max\":\"2026-01-03\"")), 11);
    }

    @Test void repeatElementOverrideDoesNotReplaceTheGlobalRangeForItsCountQuestion() throws Exception {
        assertCount9(run9(direct9(), 2, 2, "\"fields\":{\"row\":{\"min\":8,\"max\":8}}"), 2);
    }

    @Test void optionalCountKeepsConfiguredMissingRateAndClearsItsOldDefault() throws Exception {
        LoadedForm form = direct9("int", "7", "", input("answer"));
        Submission result = run9(form, 2, 2, field9("\"missingRate\":1"));
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void requiredCountStillIgnoresMissingRateOne() throws Exception {
        assertCount9(run9(direct9(), 2, 2, field9("\"missingRate\":1")), 2);
    }

    @Test void optionalCountWithNoOverrideStillHonorsGlobalMissingness() throws Exception {
        LoadedForm form = direct9("int", "", "", input("answer"));
        Submission result = new FormWalker(form, config("\"missingRate\":1,\"repeats\":{\"min\":2,\"max\":2}")).walk(9);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @Test void emptyExpressionRemainsBlankInsteadOfFallingBackToSmallCount() throws Exception {
        Submission result = run9(direct9("int", "", "", input("answer")), 2, 2, field9("\"expr\":\"''\""));
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
    }

    @Test void invalidFixedValueStillReportsConversionFailureInsteadOfGeneratingACount() throws Exception {
        Submission result = run9(direct9(), 2, 2, field9("\"value\":\"not-an-integer\""));
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "not-an-integer");
        assertWarning(result, "answer", "required");
    }

    @Test void syntheticRangeDoesNotMutateConfigurationOrBecomeAnUnknownFieldWarning() throws Exception {
        SimulationConfig cfg = settings9(2, 2, field9("\"missingRate\":0"));
        FormWalker walker = new FormWalker(direct9(), cfg);
        assertTrue(walker.configWarnings().isEmpty());
        assertCount9(walker.walk(1), 2);
        assertTrue(cfg.field("answer").orElseThrow().range().isEmpty());
        assertEquals(0, cfg.field("answer").orElseThrow().missingRate().orElseThrow());
        assertEquals(new NumericRange(11, 11), cfg.integerRange());
        Submission ordinary = new FormWalker(single("int", "required=\"true()\""), cfg).walk(1);
        assertEquals("11", value(ordinary, "answer"), "Sharing configuration must not leak the count override");
    }

    @Test void absentFieldOverrideRemainsAbsentAfterWalking() throws Exception {
        SimulationConfig cfg = settings9(2, 2, "");
        FormWalker walker = new FormWalker(direct9(), cfg);
        assertCount9(walker.walk(1), 2);
        assertTrue(cfg.field("answer").isEmpty());
        assertTrue(cfg.overriddenFields().isEmpty());
        assertTrue(walker.configWarnings().isEmpty());
    }
}
