package ctosim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorNumberTest {
    @Test
    void integerRangeRoundsBoundsInwardAndIncludesBothEndpointsUniformly() throws Exception {
        assertUniform(draws(override("\"min\":-2.8,\"max\":2.8"), question(QuestionKind.INTEGER), DRAWS),
                List.of("-2", "-1", "0", "1", "2"));
    }

    @Test
    void integerUsesConfiguredGlobalRange() throws Exception {
        assertUniform(draws(generator("\"integerRange\":{\"min\":41,\"max\":43}"),
                question(QuestionKind.INTEGER), DRAWS), List.of("41", "42", "43"));
    }

    @Test
    void integerOverrideReplacesGlobalRange() throws Exception {
        AnswerGenerator generator = generator("\"integerRange\":{\"min\":100,\"max\":100},"
                + "\"fields\":{\"answer\":{\"min\":-1,\"max\":1}}");
        assertUniform(draws(generator, question(QuestionKind.INTEGER), DRAWS), List.of("-1", "0", "1"));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "-3,-3,-3", "2.1,3.9,3", "-3.9,-2.1,-3", "0.2,0.8,0",
            "0.6,0.8,1", "0.5,0.5,1", "-0.8,-0.2,-1", "-0.5,-0.5,0", "-0.4,-0.1,0"})
    void singletonAndIntegerFreeRangesUseSpecifiedIntegerOrMathRoundMinimum(String min, String max,
                                                                           String expected) throws Exception {
        for (String answer : draws(override("\"min\":" + min + ",\"max\":" + max),
                question(QuestionKind.INTEGER), 50)) assertEquals(expected, answer);
    }

    @Test
    void integerSupportsBoundsBeyondThe32BitIntegerRange() throws Exception {
        assertUniform(draws(override("\"min\":2147483648,\"max\":2147483650"),
                question(QuestionKind.INTEGER), DRAWS), List.of("2147483648", "2147483649", "2147483650"));
    }

    @Test
    void defaultIntegerRangeIsUsedWhenThereIsNoOverride() throws Exception {
        List<String> allowed = java.util.stream.IntStream.rangeClosed(0, 100).mapToObj(Integer::toString).toList();
        assertUniform(draws(generator(""), question(QuestionKind.INTEGER), DRAWS), allowed);
    }

    @ParameterizedTest
    @CsvSource({"12.5,12.5", "3,3", "-0.25,-0.25", "1.125,1.13", "-1.125,-1.13",
            "2.345,2.35", "-2.345,-2.35", "1.999,2", "-1.999,-2", "0.004,0", "-0.004,0",
            "0.005,0.01", "-0.005,-0.01", "0,0", "-0.0,0", "1000000000000,1000000000000",
            "0.00000001,0"})
    void equalDecimalBoundsUseHalfUpPlainFormatting(String value, String expected) throws Exception {
        for (String answer : draws(override("\"min\":" + value + ",\"max\":" + value),
                question(QuestionKind.DECIMAL), 20)) assertEquals(expected, answer);
    }

    @Test
    void decimalRoundsAfterDrawingSoRoundedAnswerCanLieOutsideRawBounds() throws Exception {
        for (String answer : draws(override("\"min\":0.001,\"max\":0.004"),
                question(QuestionKind.DECIMAL), 200)) assertEquals("0", answer);
    }

    @Test
    void decimalUsesGlobalRangeInsteadOfIntegerRange() throws Exception {
        AnswerGenerator generator = generator("\"integerRange\":{\"min\":500,\"max\":500},"
                + "\"decimalRange\":{\"min\":-2.5,\"max\":-2.5}");
        assertEquals("-2.5", generator.generate(question(QuestionKind.DECIMAL), TODAY, new Random(SEED)));
    }

    @Test
    void decimalOverrideReplacesGlobalRange() throws Exception {
        AnswerGenerator generator = generator("\"decimalRange\":{\"min\":100,\"max\":100},"
                + "\"fields\":{\"answer\":{\"min\":-0.25,\"max\":-0.25}}");
        for (String answer : draws(generator, question(QuestionKind.DECIMAL), 20)) assertEquals("-0.25", answer);
    }

    @Test
    void defaultDecimalRangeAndFormattingAreRespected() throws Exception {
        for (String answer : draws(generator(""), question(QuestionKind.DECIMAL), 2000)) {
            BigDecimal value = assertCanonicalDecimal(answer);
            assertTrue(value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(1000)) <= 0, answer);
        }
    }

    @Test
    void decimalIsUniformBeforeRoundingIncludingHalfWidthEndpointBins() throws Exception {
        // [-.02,.02] rounds to five possible hundredths. The endpoints each
        // collect half a hundredth, while each interior value collects one.
        List<String> answers = draws(override("\"min\":-0.02,\"max\":0.02"),
                question(QuestionKind.DECIMAL), 60_000);
        var counts = counts(answers);
        assertEquals(java.util.Set.of("-0.02", "-0.01", "0", "0.01", "0.02"), counts.keySet());
        for (String answer : answers) assertCanonicalDecimal(answer);
        for (String value : List.of("-0.02", "-0.01", "0", "0.01", "0.02")) {
            assertRate(counts.get(value), answers.size(), value.endsWith("02") ? 0.125 : 0.25, value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"texts\":[\"not a number\"]", "\"weights\":{\"bad\":1}",
            "\"min\":\"2000-01-01\",\"max\":\"2000-01-02\""})
    void irrelevantOverridePartsLeaveIntegerGlobalRangeApplicable(String field) throws Exception {
        AnswerGenerator generator = generator("\"integerRange\":{\"min\":7,\"max\":7},"
                + "\"fields\":{\"answer\":{" + field + "}}");
        assertEquals("7", generator.generate(question(QuestionKind.INTEGER), TODAY, new Random(SEED)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"texts\":[\"not a number\"]", "\"weights\":{\"bad\":1}",
            "\"min\":\"2000-01-01\",\"max\":\"2000-01-02\""})
    void irrelevantOverridePartsLeaveDecimalGlobalRangeApplicable(String field) throws Exception {
        AnswerGenerator generator = generator("\"decimalRange\":{\"min\":7.25,\"max\":7.25},"
                + "\"fields\":{\"answer\":{" + field + "}}");
        assertEquals("7.25", generator.generate(question(QuestionKind.DECIMAL), TODAY, new Random(SEED)));
    }

    @Test
    void applicableNumericRangeStillWorksAlongsideIrrelevantTextsAndWeights() throws Exception {
        AnswerGenerator generator = override("\"min\":4,\"max\":4,\"texts\":[\"bad\"],\"weights\":{\"bad\":1}");
        assertEquals("4", generator.generate(question(QuestionKind.INTEGER), TODAY, new Random(SEED)));
        assertEquals("4", generator.generate(question(QuestionKind.DECIMAL), TODAY, new Random(SEED)));
    }

    private static BigDecimal assertCanonicalDecimal(String answer) {
        assertNotNull(answer);
        assertTrue(answer.matches("-?(0|[1-9][0-9]*)(\\.[0-9]{1,2})?"), answer);
        BigDecimal value = new BigDecimal(answer);
        assertEquals(value.setScale(2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString(), answer);
        return value;
    }
}
