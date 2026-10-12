package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorSearchSupport.*;
import static ctosim.ConstraintHintsTestSupport.numeric;
import static ctosim.ConstraintHintsAmendment2Support.hints;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorSearchNumberTest {
    @Test
    void defaultIntegerDrawingRangeIsEnumeratedInclusively() throws Exception {
        assertLevel(integers(0, 100), search(generator(""), question(QuestionKind.INTEGER)), 0, 2);
    }

    static Stream<Arguments> integerRanges() {
        return Stream.of(
                Arguments.of("-2.8", "3.9", integers(-2, 3)),
                Arguments.of("-4", "-4", List.of("-4")),
                Arguments.of("0", "0", List.of("0")),
                Arguments.of("2147483648", "2147483650", integers(2147483648L, 2147483650L)),
                Arguments.of("-0.9", "0.9", List.of("0")));
    }

    @ParameterizedTest @MethodSource("integerRanges")
    void integersUseInwardRoundedBoundsAndPlainFormatting(String min, String max, List<String> expected)
            throws Exception {
        assertLevel(expected, search(generator("\"integerRange\":{\"min\":" + min + ",\"max\":" + max + "}"),
                question(QuestionKind.INTEGER)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void applicableFieldRangeReplacesGlobalRange(QuestionKind kind) throws Exception {
        assertLevel(List.of("-3", "-2", "-1"), search(generator(
                "\"fields\":{\"answer\":{\"min\":-3,\"max\":-1}}"), question(kind)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void dateOverrideDoesNotReplaceNumericRange(QuestionKind kind) throws Exception {
        assertLevel(List.of("2", "3"), search(generator(
                "\"integerRange\":{\"min\":2,\"max\":3},\"decimalRange\":{\"min\":2,\"max\":3},"
                + "\"fields\":{\"answer\":{\"min\":\"2001-01-01\",\"max\":\"2001-01-03\"}}"), question(kind)), 0, 2);
    }

    static Stream<Arguments> hintedIntegers() {
        return Stream.of(
                Arguments.of("intersection", numeric(3d, true, 7d, true), 3, 7),
                Arguments.of("disjoint bounded", numeric(20d, true, 22d, true), 20, 22),
                Arguments.of("lower translated", numeric(20d, true, null, true), 20, 30),
                Arguments.of("upper translated", numeric(null, true, -20d, true), -30, -20),
                Arguments.of("lower intersected", numeric(7d, true, null, true), 7, 10),
                Arguments.of("upper intersected", numeric(null, true, 2d, true), 0, 2),
                Arguments.of("exclusive integers", numeric(3d, false, 7d, false), 4, 6),
                Arguments.of("inclusive fractions", numeric(3.2, true, 7.8, true), 4, 7),
                Arguments.of("negative exclusive fractions", numeric(-7.8, false, -3.2, false), -7, -4),
                Arguments.of("singleton", numeric(20d, true, 20d, true), 20, 20),
                Arguments.of("empty hints ignored", numeric(7d, true, 3d, true), 0, 10),
                Arguments.of("integer-free hints ignored", numeric(20.2, true, 20.8, true), 0, 10));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("hintedIntegers")
    void integerLevelUsesTheHintAdjustedDrawingRange(String label, ConstraintHints h, int first, int last)
            throws Exception {
        assertLevel(integers(first, last), search(generator("\"integerRange\":{\"min\":0,\"max\":10}"),
                question(QuestionKind.INTEGER, h)), 0, 2);
    }

    @Test
    void oneSidedHintTranslationUsesTheFieldRangesUnroundedWidth() throws Exception {
        assertLevel(integers(20, 22), search(generator("\"fields\":{\"answer\":{\"min\":0.2,\"max\":2.8}}"),
                question(QuestionKind.INTEGER, numeric(20d, true, null, true))), 0, 2);
    }

    @ParameterizedTest @ValueSource(ints = {9999, 10000})
    void integerRangesAtOrBelowTheCapKeepEveryValue(int last) throws Exception {
        assertLevel(integers(0, last), search(generator("\"integerRange\":{\"min\":0,\"max\":" + last + "}"),
                question(QuestionKind.INTEGER)), 0, 2);
    }

    @ParameterizedTest @ValueSource(ints = {10001, 20000, 30000})
    void oversizedIntegerRangesAreSpreadAcrossBothEnds(int last) throws Exception {
        var values = search(generator("\"integerRange\":{\"min\":0,\"max\":" + last + "}"),
                question(QuestionKind.INTEGER)).get(0);
        assertEquals(10001, values.size());
        assertEquals("0", values.get(0));
        assertEquals(Integer.toString(last), values.get(10000));
        assertEquals(values.size(), new HashSet<>(values).size());
        for (int i = 0; i <= 10000; i++) {
            // Half ties are unspecified for integer interpolation; allow either nearest integer.
            assertEquals(i * last / 10000d, Long.parseLong(values.get(i)), .50000001, "grid point " + i);
            if (i > 0) assertTrue(Long.parseLong(values.get(i)) > Long.parseLong(values.get(i - 1)));
        }
    }

    @Test
    void decimalDefaultRangeContainsIntegersNotEveryCent() throws Exception {
        assertLevel(integers(0, 1000), search(generator(""), question(QuestionKind.DECIMAL)), 0, 2);
    }

    static Stream<Arguments> decimalRanges() {
        return Stream.of(
                Arguments.of("2.345", "2.345", List.of("2.35")),
                Arguments.of("-2.345", "-2.345", List.of("-2.35")),
                Arguments.of("-0.004", "0.004", List.of("0")),
                Arguments.of("0.121", "0.124", List.of("0.12")),
                Arguments.of("0.12", "0.24", List.of("0.12", "0.24")),
                Arguments.of("-2", "2", integers(-2, 2)));
    }

    @ParameterizedTest @MethodSource("decimalRanges")
    void decimalEndsAreHalfUpFormattedAndDeduplicated(String min, String max, List<String> expected) throws Exception {
        assertLevel(expected, search(generator("\"decimalRange\":{\"min\":" + min + ",\"max\":" + max + "}"),
                question(QuestionKind.DECIMAL)), 0, 2);
    }

    @Test
    void fractionalDecimalEndsAndEveryInteriorIntegerArePresentExactlyOnce() throws Exception {
        var values = search(generator("\"decimalRange\":{\"min\":-2.25,\"max\":3.75}"),
                question(QuestionKind.DECIMAL)).get(0);
        assertEquals(8, values.size());
        assertEquals(new HashSet<>(List.of("-2.25", "-2", "-1", "0", "1", "2", "3", "3.75")),
                new HashSet<>(values));
        assertEquals(integers(-2, 3), values.stream().filter(v -> !v.contains(".")).toList());
    }

    @Test
    void exclusiveDecimalHintsMoveEndsByOneCent() throws Exception {
        assertLevel(List.of("0.01", "0.03"), search(generator(""), question(QuestionKind.DECIMAL,
                numeric(0d, false, .04, false))), 0, 2);
    }

    static Stream<Arguments> hintedDecimals() {
        return Stream.of(
                Arguments.of("intersection", numeric(3d, true, 5d, true), integers(3, 5)),
                Arguments.of("bounded disjoint", numeric(20d, true, 22d, true), integers(20, 22)),
                Arguments.of("lower translated", numeric(20d, true, null, true), integers(20, 30)),
                Arguments.of("upper translated", numeric(null, true, -20d, true), integers(-30, -20)),
                Arguments.of("lower intersected", numeric(8d, true, null, true), integers(8, 10)),
                Arguments.of("upper intersected", numeric(null, true, 2d, true), integers(0, 2)),
                Arguments.of("empty ignored", numeric(8d, true, 2d, true), integers(0, 10)),
                Arguments.of("empty after cent adjustment", numeric(20d, false, 20.01, false), integers(0, 10)),
                Arguments.of("fractional singleton", numeric(20.125, true, 20.125, true), List.of("20.13")));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("hintedDecimals")
    void decimalLevelUsesTheHintAdjustedDrawingRange(String label, ConstraintHints h, List<String> expected)
            throws Exception {
        assertLevel(expected, search(generator("\"decimalRange\":{\"min\":0,\"max\":10}"),
                question(QuestionKind.DECIMAL, h)), 0, 2);
    }

    @ParameterizedTest @ValueSource(ints = {10000, 20000})
    void integralDecimalRangesRespectTheCapAndEvenSpacing(int last) throws Exception {
        var expected = new ArrayList<String>();
        for (int i = 0; i <= 10000; i++) expected.add(Integer.toString(i * (last / 10000)));
        assertLevel(expected, search(generator("\"decimalRange\":{\"min\":0,\"max\":" + last + "}"),
                question(QuestionKind.DECIMAL)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void alternativesKeepTheirOrderDropOverlapsAndSkipUnsatisfiableRanges(QuestionKind kind) throws Exception {
        var h = hints().alternatives(hints().range(8, 10).build(), hints().range(7, 3).build(),
                hints().range(2, 4).build(), hints().range(9, 11).build(), hints().range(99, 99).build()).build();
        assertLevel(List.of("8", "9", "10", "2", "3", "4", "11", "99"),
                search(generator("\"specialRate\":0"), question(kind, h)), 0, 2);
    }

    @Test
    void valueCapIsPerDrawingRangeRatherThanPerLevel() throws Exception {
        var h = hints().alternatives(hints().range(-30000, -10000).build(), hints().range(20000, 40000).build()).build();
        List<String> expected = new ArrayList<>();
        for (int start : new int[] {-30000, 20000}) {
            for (int i = 0; i <= 10000; i++) expected.add(Integer.toString(start + 2 * i));
        }
        assertLevel(expected, search(generator("\"integerRange\":{\"min\":0,\"max\":1}"),
                question(QuestionKind.INTEGER, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL"})
    void broadNumericLevelIsCompleteRegardlessOfNarrowHintsOrConfiguration(QuestionKind kind) throws Exception {
        var values = search(generator("\"fields\":{\"answer\":{\"min\":0,\"max\":0}}"),
                question(kind, hints().range(7, 7).build()));
        assertLevel(kind == QuestionKind.INTEGER ? integers(-10000, 10000) : decimalFallback(), values, 1, 2);
    }
}
