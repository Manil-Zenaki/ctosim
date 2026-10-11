package ctosim;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 05, Amendment 1: decimal appearance on TEXT. */
class AnswerGeneratorDecimalAppearanceTest {
    private static Question text(String appearance) {
        return new Question("answer", QuestionKind.TEXT, List.of(), appearance, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"numbers_decimal", "numbers_decimal compact", "compact numbers_decimal",
            "compact numbers_decimal minimal", "  numbers_decimal  ", "compact   numbers_decimal   minimal"})
    void completeAppearanceWordUsesDecimalRangeInsteadOfIntegerRange(String appearance) throws Exception {
        AnswerGenerator generator = generator("\"integerRange\":{\"min\":91,\"max\":91},"
                + "\"decimalRange\":{\"min\":-12.5,\"max\":-12.5}");
        for (String answer : draws(generator, text(appearance), 20)) assertEquals("-12.5", answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my_numbers_decimal", "numbers_decimal_extra", "numbers_decimal-phone",
            "numbers_decimalx", "compact my_numbers_decimal minimal"})
    void substringsDoNotActivateDecimalAppearance(String appearance) throws Exception {
        for (String answer : draws(generator(""), text(appearance), 100)) {
            assertNotNull(answer);
            assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
        }
    }

    @Test
    void numericFieldRangeReplacesGlobalDecimalRange() throws Exception {
        AnswerGenerator generator = generator("\"decimalRange\":{\"min\":100,\"max\":100},"
                + "\"fields\":{\"answer\":{\"min\":-0.25,\"max\":-0.25}}");
        for (String answer : draws(generator, text("numbers_decimal"), 30)) assertEquals("-0.25", answer);
    }

    @ParameterizedTest
    @CsvSource({"2.345,2.35", "-2.345,-2.35", "1.125,1.13", "-1.125,-1.13",
            "12.50,12.5", "3.00,3", "1.999,2", "-1.999,-2", "0.004,0", "-0.004,0",
            "0.005,0.01", "-0.005,-0.01", "0,0", "-0.0,0", "1e12,1000000000000", "1e-8,0"})
    void singletonRangeUsesDecimalHalfUpAndCanonicalFormatting(String value, String expected) throws Exception {
        for (String answer : draws(override("\"min\":" + value + ",\"max\":" + value),
                text("numbers_decimal"), 20)) assertEquals(expected, answer);
    }

    @ParameterizedTest
    @CsvSource({"0.001,0.004", "-0.004,-0.001"})
    void roundingCanProduceZeroOutsideTheUnroundedRange(String min, String max) throws Exception {
        for (String answer : draws(override("\"min\":" + min + ",\"max\":" + max),
                text("numbers_decimal"), 100)) assertEquals("0", answer);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drawsAreUniformBeforeRoundingForGlobalAndFieldRanges(boolean fieldRange) throws Exception {
        AnswerGenerator generator = fieldRange ? override("\"min\":-0.02,\"max\":0.02")
                : generator("\"decimalRange\":{\"min\":-0.02,\"max\":0.02}");
        List<String> answers = draws(generator, text("numbers_decimal"), 60_000);
        var counts = counts(answers);
        assertEquals(Set.of("-0.02", "-0.01", "0", "0.01", "0.02"), counts.keySet());
        // Endpoint rounding intervals are half as wide as interior intervals.
        for (String value : counts.keySet()) {
            assertRate(counts.get(value), answers.size(), value.endsWith("02") ? 0.125 : 0.25, value);
        }
    }

    @Test
    void defaultDecimalRangeAndFormattingApply() throws Exception {
        int[] bins = new int[10];
        for (String answer : draws(generator(""), text("numbers_decimal"), DRAWS)) {
            assertNotNull(answer);
            assertTrue(answer.matches("-?(0|[1-9][0-9]*)(\\.[0-9]{1,2})?"), answer);
            BigDecimal value = new BigDecimal(answer);
            assertEquals(value.stripTrailingZeros().toPlainString(), answer);
            assertTrue(value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(1000)) <= 0, answer);
            bins[Math.min(9, value.intValue() / 100)]++;
        }
        // Rounded bucket boundaries differ from exact tenths by at most .005/1000.
        for (int count : bins) assertRate(count, DRAWS, 0.1, "default decimal range bucket");
    }

    @Test
    void textsOverrideTakesPrecedenceAndPreservesAllTextVerbatim() throws Exception {
        AnswerGenerator generator = override("\"min\":7.5,\"max\":7.5,"
                + "\"texts\":[\"not numeric\",\"\",\"  café 東京  \",\"1.2300\"]");
        assertUniform(draws(generator, text("numbers_decimal"), DRAWS),
                List.of("not numeric", "", "  café 東京  ", "1.2300"));
    }

    @Test
    void missingRulePrecedesTextsOverrideOnOptionalDecimalText() throws Exception {
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), "numbers_decimal", false);
        for (String answer : draws(override("\"texts\":[\"retained text\"],\"missingRate\":1"),
                question, 30)) assertNull(answer);
    }

    @Test
    void labelWordDoesNotSuppressDecimalText() throws Exception {
        for (String answer : draws(override("\"min\":7.25,\"max\":7.25"),
                text("label numbers_decimal"), 20)) assertEquals("7.25", answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"min\":\"2001-01-01\",\"max\":\"2001-01-02\"",
            "\"weights\":{\"invalid\":1}"})
    void irrelevantOverrideDoesNotDisplaceGlobalDecimalRange(String field) throws Exception {
        AnswerGenerator generator = generator("\"decimalRange\":{\"min\":7.25,\"max\":7.25},"
                + "\"fields\":{\"answer\":{" + field + "}}");
        for (String answer : draws(generator, text("numbers_decimal"), 20)) assertEquals("7.25", answer);
    }

    @Test
    void decimalAppearanceDoesNotChangeIntegerQuestions() throws Exception {
        Question question = new Question("answer", QuestionKind.INTEGER, List.of(), "numbers_decimal", true);
        for (String answer : draws(override("\"min\":2.1,\"max\":3.9"), question, 30)) assertEquals("3", answer);
    }

    @Test
    void decimalAppearanceDoesNotChangeDateQuestions() throws Exception {
        Question question = new Question("answer", QuestionKind.DATE, List.of(), "numbers_decimal", true);
        for (String answer : draws(override("\"min\":\"2001-05-17\",\"max\":\"2001-05-17\""),
                question, 30)) assertEquals("2001-05-17", answer);
    }
}
