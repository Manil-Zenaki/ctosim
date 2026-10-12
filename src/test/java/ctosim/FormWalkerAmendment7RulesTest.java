package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment7Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment7RulesTest {
    @Test void firstTrueRuleWinsAndLaterRulesAreNotEvaluated() throws Exception {
        Submission result = answer7(single("string", ""), "\"value\":\"base\",\"when\":["
                + rule7("false()", "\"value\":\"false-branch\"") + ","
                + rule7("true()", "\"value\":\"first\"") + ","
                + rule7("true()", "\"value\":\"second\"") + ","
                + rule7("unknown_amendment_seven()", "\"value\":\"bad\"") + "]");
        assertEquals("first", value(result, "answer"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    static Stream<Arguments> booleans() {
        return Stream.of(Arguments.of("true()", true), Arguments.of("false()", false),
                Arguments.of("1", true), Arguments.of("-2", true), Arguments.of("0", false),
                Arguments.of("''", false), Arguments.of("'false'", true), Arguments.of("'0'", true),
                Arguments.of("number('not-a-number')", false), Arguments.of("/data/answer", true),
                Arguments.of("/data/answer[false()]", false));
    }

    @ParameterizedTest @MethodSource("booleans")
    void conditionUsesXpathBooleanCoercion(String condition, boolean expected) throws Exception {
        Submission result = answer7(single("string", ""), "\"value\":\"base\",\"when\":["
                + rule7(condition, "\"value\":\"matched\"") + "]");
        assertEquals(expected ? "matched" : "base", value(result, "answer"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void unmatchedWhenOnlyOverrideStillReplacesAnExistingDefault() throws Exception {
        LoadedForm form = default7("int", "999", "");
        SimulationConfig cfg = settings7("\"integerRange\":{\"min\":17,\"max\":17},"
                + field7("\"when\":[" + rule7("false()", "\"value\":23") + "]"));
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals("17", value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void rulesAloneCanProvideAnAnswer() throws Exception {
        Submission result = answer7(single("string", ""), "\"when\":["
                + rule7("true()", "\"value\":\"only-rule\"") + "]");
        assertEquals("only-rule", value(result, "answer"));
    }

    static Stream<Arguments> merges() {
        String normal = "\"distribution\":{\"normal\":{\"mean\":17,\"sd\":0}}";
        String otherNormal = "\"distribution\":{\"normal\":{\"mean\":23,\"sd\":0}}";
        return Stream.of(
                Arguments.of("int", "\"min\":1,\"max\":2", "\"min\":17,\"max\":17", "17"),
                Arguments.of("date", "\"min\":\"1999-01-01\",\"max\":\"1999-01-02\"",
                        "\"min\":\"2024-02-29\",\"max\":\"2024-02-29\"", "2024-02-29"),
                Arguments.of("string", "\"texts\":[\"base\"]", "\"texts\":[\"rule\"]", "rule"),
                Arguments.of("string", "\"value\":\"base\"", "\"value\":\"rule\"", "rule"),
                Arguments.of("int", normal, otherNormal, "23"),
                Arguments.of("int", normal + ",\"min\":0,\"max\":100", "\"min\":30,\"max\":40", "30"),
                Arguments.of("int", "\"min\":30,\"max\":40", normal, "30"),
                Arguments.of("int", "\"min\":17,\"max\":17", "\"missingRate\":0", "17"),
                Arguments.of("string", "\"texts\":[\"retained\"]", "\"missingRate\":0", "retained"),
                Arguments.of("string", "\"value\":\"retained\"", "\"missingRate\":0", "retained"),
                Arguments.of("int", normal, "\"missingRate\":0", "17"),
                Arguments.of("string", "\"expr\":\"'base-expression'\"", "\"expr\":\"'rule-expression'\"", "rule-expression"),
                Arguments.of("string", "\"expr\":\"'retained-expression'\"", "\"missingRate\":0", "retained-expression"));
    }

    @ParameterizedTest @MethodSource("merges")
    void matchingRuleReplacesItsKeysAndRetainsTheOtherParentSettings(
            String type, String parent, String rule, String expected) throws Exception {
        Submission result = answer7(single(type, ""), parent + ",\"when\":[" + rule7("true()", rule) + "]");
        assertEquals(expected, value(result, "answer"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectWeightsAreReplacedAsOneSettingOrRetainedWhenOmitted(boolean replace) throws Exception {
        String settings = "\"weights\":{\"10\":1,\"20\":1000000},\"when\":["
                + rule7("true()", replace ? "\"weights\":{\"10\":1}" : "\"missingRate\":0") + "]";
        if (!replace) settings = "\"weights\":{\"10\":1},\"when\":[" + rule7("true()", "\"missingRate\":0") + "]";
        FormWalker walker = new FormWalker(select("select1", "string", ""), settings7(field7(settings)));
        for (int n = 1; n <= 20; n++) assertEquals("10", value(walker.walk(n), "answer"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void areaIsReplacedAsOneSettingOrRetainedWhenOmitted(boolean replace) throws Exception {
        String area = "\"area\":{\"minLatitude\":8,\"maxLatitude\":8,\"minLongitude\":38,\"maxLongitude\":38}";
        String replacement = "\"area\":{\"minLatitude\":9,\"maxLatitude\":9,\"minLongitude\":39,\"maxLongitude\":39}";
        Submission result = answer7(single("geopoint", ""), area + ",\"when\":["
                + rule7("true()", replace ? replacement : "\"missingRate\":0") + "]");
        String[] point = value(result, "answer").split("\\s+");
        assertEquals(replace ? 9 : 8, Double.parseDouble(point[0]));
        assertEquals(replace ? 39 : 38, Double.parseDouble(point[1]));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inheritedOrRuleMissingRateClearsAnOverriddenDefault(boolean rateInRule) throws Exception {
        String parent = rateInRule ? "\"value\":\"base\",\"missingRate\":0" : "\"value\":\"base\",\"missingRate\":1";
        String rule = rateInRule ? "\"missingRate\":1" : "\"value\":\"replacement\"";
        Submission result = answer7(default7("string", "old", ""), parent + ",\"when\":[" + rule7("true()", rule) + "]");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 1);
    }

    @Test void ruleMissingRateZeroOverridesParentOne() throws Exception {
        Submission result = answer7(single("string", ""), "\"value\":\"kept-setting\",\"missingRate\":1,\"when\":["
                + rule7("true()", "\"missingRate\":0") + "]");
        assertEquals("kept-setting", value(result, "answer"));
    }

    @Test void aRequiredQuestionIgnoresTheEffectiveMissingRate() throws Exception {
        Submission result = answer7(single("string", "required=\"true()\""), "\"value\":\"base\",\"when\":["
                + rule7("true()", "\"value\":\"required-answer\",\"missingRate\":1") + "]");
        assertEquals("required-answer", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @Test void aTrueRuleWithOnlyMissingRateDoesNotFallThroughToALaterValueRule() throws Exception {
        Submission result = answer7(single("string", ""), "\"value\":\"base\",\"when\":["
                + rule7("true()", "\"missingRate\":0") + "," + rule7("true()", "\"value\":\"later\"") + "]");
        assertEquals("base", value(result, "answer"));
    }
}
