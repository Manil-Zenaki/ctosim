package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static ctosim.SimulationConfigRealisticSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigRealisticRulesTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "1", "'literal'", "${never_declared} + 1", "${a} + ${b} + ${a}",
            "profile() = 'not_declared'", "(${age} >= 18) and (profile() = 'rural')",
            "concat('Élodie', ${surname})", "  ${income} * 12  ", "${a}\n +\t${b}"
    })
    void expressionsAndConditionsParseWithoutFieldOrProfileLookupAndRetainTheirText(String expression) throws Exception {
        FieldOverride answer = override("\"expr\":" + quote(expression));
        assertEquals(expression, answer.expression().orElseThrow());
        assertTrue(answer.value().isEmpty());
        assertTrue(answer.range().isEmpty());
        assertTrue(answer.distribution().isEmpty());
        assertTrue(answer.weights().isEmpty());
        assertTrue(answer.texts().isEmpty());
        FieldOverride rules = override("\"when\":[{\"if\":" + quote(expression) + ",\"expr\":" + quote(expression) + "}]");
        assertEquals(expression, rules.rules().get(0).condition());
        assertEquals(expression, rules.rules().get(0).override().expression().orElseThrow());
        assertTrue(rules.rules().get(0).override().rules().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expressionCanHaveAnIndependentMissingRate(boolean inRule) throws Exception {
        FieldOverride answer = settings("\"expr\":\"${unknown} + 1\",\"missingRate\":0.375", inRule);
        assertEquals("${unknown} + 1", answer.expression().orElseThrow());
        assertEquals(0.375, answer.missingRate().orElseThrow());
    }

    @Test
    void whenAloneIsAValidOverrideAndDoesNotPromoteRuleSettingsToTheParent() throws Exception {
        FieldOverride parent = override("\"when\":[{\"if\":\"1 = 1\",\"value\":\"yes\"}]");
        assertTrue(parent.value().isEmpty());
        assertTrue(parent.range().isEmpty());
        assertTrue(parent.weights().isEmpty());
        assertTrue(parent.texts().isEmpty());
        assertTrue(parent.missingRate().isEmpty());
        assertTrue(parent.distribution().isEmpty());
        assertTrue(parent.expression().isEmpty());
        assertTrue(parent.area().isEmpty());
        assertEquals(1, parent.rules().size());
        assertEquals("yes", parent.rules().get(0).override().value().orElseThrow());
    }

    @Test
    void rulesRetainFileOrderAndDuplicateConditionsWithoutEvaluatingThem() throws Exception {
        FieldOverride parent = override("\"value\":\"fallback\",\"when\":["
                + "{\"if\":\"1 = 0\",\"value\":\"first\"},"
                + "{\"if\":\"1 = 1\",\"value\":\"second\"},"
                + "{\"if\":\"1 = 1\",\"value\":\"third\"}]");
        assertEquals("fallback", parent.value().orElseThrow());
        assertEquals(List.of("1 = 0", "1 = 1", "1 = 1"), parent.rules().stream().map(OverrideRule::condition).toList());
        assertEquals(List.of("first", "second", "third"), parent.rules().stream().map(r -> r.override().value().orElseThrow()).toList());
        parent.rules().forEach(r -> assertTrue(r.override().rules().isEmpty()));
    }

    @Test
    void ruleAndParentAnswerKindsAreValidatedSeparately() throws Exception {
        FieldOverride parent = override("\"value\":7,\"when\":["
                + "{\"if\":\"1 = 1\",\"distribution\":" + NORMAL + "},"
                + "{\"if\":\"1 = 0\",\"expr\":\"${unknown}\"}]");
        assertEquals("7", parent.value().orElseThrow());
        assertEquals(new Distribution("normal", -2.5, 3.25), parent.rules().get(0).override().distribution().orElseThrow());
        assertEquals("${unknown}", parent.rules().get(1).override().expression().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"value\":1.0", "\"expr\":\"1 + 2\"", "\"min\":-5,\"max\":8",
            "\"min\":\"2020-02-29\",\"max\":\"2020-02-29\"", "\"weights\":{\"z\":0,\"a\":2}",
            "\"texts\":[\"\",\"東京\",\"東京\"]", "\"missingRate\":0",
            "\"distribution\":{\"lognormal\":{\"mean\":10,\"sd\":0}}",
            "\"area\":{\"minLatitude\":0,\"maxLatitude\":0,\"minLongitude\":0,\"maxLongitude\":0}"})
    void eachPermittedRuleSettingHasTheSameAccessorSemanticsAsAFieldSetting(String members) throws Exception {
        assertOverrideEquals(settings(members, false), settings(members, true));
    }

    @Test
    void ruleDateBoundsRemainDates() throws Exception {
        FieldOverride rule = settings("\"min\":\"2020-02-29\",\"max\":\"2021-01-01\"", true);
        assertEquals(new DateRange(LocalDate.of(2020, 2, 29), LocalDate.of(2021, 1, 1)), rule.range().orElseThrow());
    }

    static Stream<Arguments> malformedExpressions() {
        return Stream.of("1 +", "(", "1 =", "'unterminated", "${age} +", "profile() =", "${a} + (${b} * )")
                .flatMap(expression -> Stream.of(
                        Arguments.of(fieldJson("\"expr\":" + quote(expression)), "fields.answer.expr"),
                        Arguments.of(ruleJson("\"expr\":" + quote(expression)), "fields.answer.when[0].expr"),
                        Arguments.of(fieldJson("\"when\":[{\"if\":\"1 = 1\",\"value\":1},{\"if\":"
                                + quote(expression) + ",\"value\":2}]"), "fields.answer.when[1].if")));
    }

    @ParameterizedTest(name = "invalid XPath at {1}: {0}")
    @MethodSource("malformedExpressions")
    void malformedXpathReportsTheActualExpressionKeyIncludingTheRuleIndex(String json, String path) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, path);
    }
}
