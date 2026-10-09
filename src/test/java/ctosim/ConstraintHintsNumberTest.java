package ctosim;

import java.util.List;
import java.util.Random;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsNumberTest {
    static Stream<Arguments> integerIntervals() {
        return Stream.of(
            Arguments.of("none", numeric(null, true, null, true), 0, 10),
            Arguments.of("intersect both", numeric(3d, true, 7d, true), 3, 7),
            Arguments.of("intersection at one point", numeric(10d, true, 20d, true), 10, 10),
            Arguments.of("H encloses R", numeric(-10d, true, 20d, true), 0, 10),
            Arguments.of("bounded disjoint high", numeric(20d, true, 23d, true), 20, 23),
            Arguments.of("bounded disjoint low", numeric(-23d, true, -20d, true), -23, -20),
            Arguments.of("lower intersect", numeric(7d, true, null, true), 7, 10),
            Arguments.of("upper intersect", numeric(null, true, 2d, true), 0, 2),
            Arguments.of("lower disjoint preserves width", numeric(20d, true, null, true), 20, 30),
            Arguments.of("upper disjoint preserves width", numeric(null, true, -20d, true), -30, -20),
            Arguments.of("exclusive whole bounds", numeric(3d, false, 7d, false), 4, 6),
            Arguments.of("inclusive fractions", numeric(3.2, true, 7.8, true), 4, 7),
            Arguments.of("exclusive fractions", numeric(3.2, false, 7.8, false), 4, 7),
            Arguments.of("negative inclusive fractions", numeric(-7.8, true, -3.2, true), -7, -4),
            Arguments.of("negative exclusive integers", numeric(-7d, false, -3d, false), -6, -4),
            Arguments.of("equality", numeric(30d, true, 30d, true), 30, 30),
            Arguments.of("inverted H ignored", numeric(8d, true, 2d, true), 0, 10),
            Arguments.of("fractional H contains no integer", numeric(20.2, true, 20.8, true), 0, 10),
            Arguments.of("exclusive empty H", numeric(20d, false, 21d, false), 0, 10),
            Arguments.of("lower exclusion removes touching point", numeric(10d, false, null, true), 11, 21),
            Arguments.of("upper exclusion removes touching point", numeric(null, true, 0d, false), -11, -1));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("integerIntervals")
    void integersAreUniformOverTheSpecifiedEffectiveInterval(String label, ConstraintHints h, int min, int max) throws Exception {
        var generator = generator("\"integerRange\":{\"min\":0,\"max\":10}");
        List<String> samples = AnswerGeneratorTestSupport.draws(generator, question(QuestionKind.INTEGER, h),
                TODAY, new Random(SEED), 12000);
        AnswerGeneratorTestSupport.assertUniform(samples,
                LongStream.rangeClosed(min, max).mapToObj(Long::toString).toList());
    }

    static Stream<Arguments> decimalIntervals() {
        return Stream.of(
            Arguments.of("intersect", numeric(3d, true, 7d, true), 3d, 7d),
            Arguments.of("H encloses R", numeric(-10d, true, 20d, true), 0d, 10d),
            Arguments.of("touching", numeric(10d, true, 20d, true), 10d, 10d),
            Arguments.of("bounded disjoint high", numeric(20d, true, 25d, true), 20d, 25d),
            Arguments.of("bounded disjoint low", numeric(-25d, true, -20d, true), -25d, -20d),
            Arguments.of("lower intersection", numeric(3d, true, null, true), 3d, 10d),
            Arguments.of("upper intersection", numeric(null, true, 3d, true), 0d, 3d),
            Arguments.of("lower disjoint", numeric(20d, true, null, true), 20d, 30d),
            Arguments.of("upper disjoint", numeric(null, true, -20d, true), -30d, -20d),
            Arguments.of("exclusive cents", numeric(3d, false, 7d, false), 3.01, 6.99),
            Arguments.of("negative exclusive cents", numeric(-7d, false, -3d, false), -6.99, -3.01),
            Arguments.of("equality", numeric(42.5, true, 42.5, true), 42.5, 42.5),
            Arguments.of("inverted H", numeric(8d, true, 2d, true), 0d, 10d),
            Arguments.of("empty after cent adjustment", numeric(20d, false, 20.01, false), 0d, 10d),
            Arguments.of("touching lower excluded", numeric(10d, false, null, true), 10.01, 20.01),
            Arguments.of("touching upper excluded", numeric(null, true, 0d, false), -10.01, -0.01));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("decimalIntervals")
    void decimalsUseEffectiveIntervalAndRemainUniformBeforeRounding(String label, ConstraintHints h, double min, double max) throws Exception {
        var generator = generator("\"decimalRange\":{\"min\":0,\"max\":10}");
        var q = question(QuestionKind.DECIMAL, h);
        var random = new Random(SEED);
        double sum = 0;
        boolean nearMin = false, nearMax = false;
        int n = 12000;
        int[] bins = new int[4];
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertTrue(answer.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]?[1-9])?"), answer);
            assertNotEquals("-0", answer);
            double value = Double.parseDouble(answer);
            assertTrue(value >= min - 1e-9 && value <= max + 1e-9, answer + " outside " + min + ".." + max);
            sum += value;
            if (max > min) bins[Math.min(3, Math.max(0, (int) (4 * (value - min) / (max - min))))]++;
            nearMin |= value <= min + (max - min) * .05;
            nearMax |= value >= max - (max - min) * .05;
        }
        // Seven standard errors of a continuous uniform draw, plus half a cent for rounding.
        assertEquals((min + max) / 2, sum / n, 7 * (max - min) / Math.sqrt(12d * n) + .005);
        assertTrue(nearMin && nearMax, "Draws must explore both ends, not a clipped/default singleton");
        if (max > min) for (int count : bins) {
            double tolerance = 7 * Math.sqrt(n * .25 * .75) + n * .005 / (max - min) + 2;
            assertEquals(n / 4d, count, tolerance, "Equal-width quartiles must be uniform, allowing cent rounding");
        }
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void fieldRangeControlsIntersectionAndTranslationWidth(QuestionKind kind) throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"min\":200,\"max\":204}}");
        var random = new Random(SEED);
        for (int i = 0; i < 1000; i++) {
            double intersected = Double.parseDouble(generator.generate(question(kind, numeric(202d, true, 210d, true)), TODAY, random));
            assertTrue(intersected >= 202 && intersected <= 204);
            double translated = Double.parseDouble(generator.generate(question(kind, numeric(300d, true, null, true)), TODAY, random));
            assertTrue(translated >= 300 && translated <= 304);
            double upper = Double.parseDouble(generator.generate(question(kind, numeric(null, true, 100d, true)), TODAY, random));
            assertTrue(upper >= 96 && upper <= 100);
        }
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void zeroWidthConfiguredRangeTranslatesToOneSidedHint(QuestionKind kind) throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"min\":5,\"max\":5}}");
        assertEquals("20", generator.generate(question(kind, numeric(20d, true, null, true)), TODAY, new Random(SEED)));
        assertEquals("-20", generator.generate(question(kind, numeric(null, true, -20d, true)), TODAY, new Random(SEED)));
    }

    @Test void phoneAndBeyondInt32BoundsAreNotTruncated() throws Exception {
        var generator = generator("");
        var random = new Random(SEED);
        for (double base : new double[]{900000000d, 4000000000d, -4000000000d}) {
            var q = question(QuestionKind.INTEGER, numeric(base, true, base + 9, true));
            var values = AnswerGeneratorTestSupport.draws(generator, q, TODAY, random, 12000);
            AnswerGeneratorTestSupport.assertUniform(values,
                    LongStream.rangeClosed((long) base, (long) base + 9).mapToObj(Long::toString).toList());
        }
    }

    @Test void decimalSingletonUsesOriginalHalfUpFormatting() throws Exception {
        var generator = generator("");
        assertEquals("2000.35", generator.generate(question(QuestionKind.DECIMAL,
                numeric(2000.345, true, 2000.345, true)), TODAY, new Random(SEED)));
        assertEquals("-2000.35", generator.generate(question(QuestionKind.DECIMAL,
                numeric(-2000.345, true, -2000.345, true)), TODAY, new Random(SEED)));
    }

    @Test void translatedIntegerRangeUsesTheUnroundedConfiguredWidth() throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"min\":200.2,\"max\":204.8}}");
        var values = AnswerGeneratorTestSupport.draws(generator,
                question(QuestionKind.INTEGER, numeric(300d, true, null, true)), TODAY, new Random(SEED), 12000);
        AnswerGeneratorTestSupport.assertUniform(values, List.of("300", "301", "302", "303", "304"));
    }
}
