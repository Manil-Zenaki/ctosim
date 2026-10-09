package ctosim;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsDateTest {
    static final LocalDate START = LocalDate.of(2040, 2, 27);
    static final LocalDate END = LocalDate.of(2040, 3, 2);

    static Stream<Arguments> intervals() {
        var cases = List.of(
            Arguments.of("intersection", dates(START.plusDays(1), true, END.minusDays(1), true), START.plusDays(1), END.minusDays(1)),
            Arguments.of("encloses D", dates(START.minusDays(5), true, END.plusDays(5), true), START, END),
            Arguments.of("touches D", dates(END, true, END.plusDays(3), true), END, END),
            Arguments.of("exclusive leap-day singleton", dates(START.plusDays(1), false, END.minusDays(1), false), LocalDate.of(2040, 2, 29), LocalDate.of(2040, 2, 29)),
            Arguments.of("disjoint bounded future", dates(END.plusDays(10), true, END.plusDays(12), true), END.plusDays(10), END.plusDays(12)),
            Arguments.of("disjoint bounded past", dates(START.minusDays(12), true, START.minusDays(10), true), START.minusDays(12), START.minusDays(10)),
            Arguments.of("lower overlaps D", dates(END.minusDays(1), true, null, true), END.minusDays(1), END),
            Arguments.of("upper overlaps D", dates(null, true, START.plusDays(1), true), START, START.plusDays(1)),
            Arguments.of("lower fallback 365 days", dates(END.plusDays(1), true, null, true), END.plusDays(1), END.plusDays(365)),
            Arguments.of("upper fallback 365 days", dates(null, true, START.minusDays(1), true), START.minusDays(365), START.minusDays(1)),
            Arguments.of("excluded touching lower", dates(END, false, null, true), END.plusDays(1), END.plusDays(365)),
            Arguments.of("excluded touching upper", dates(null, true, START, false), START.minusDays(365), START.minusDays(1)),
            Arguments.of("inverted H ignored", dates(END, true, START, true), START, END),
            Arguments.of("empty after exclusivity ignored", dates(END, false, END.plusDays(1), false), START, END),
            Arguments.of("equality outside D", dates(END.plusYears(5), true, END.plusYears(5), true), END.plusYears(5), END.plusYears(5)));
        return Stream.of(QuestionKind.DATE, QuestionKind.DATETIME).flatMap(kind -> cases.stream().map(a -> {
            Object[] v = a.get();
            return Arguments.of(kind, v[0], v[1], v[2], v[3]);
        }));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("intervals")
    void dateHintsSelectTheSpecifiedUniformClosedInterval(QuestionKind kind, String label,
            ConstraintHints hints, LocalDate min, LocalDate max) throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"min\":\"2040-02-27\",\"max\":\"2040-03-02\"}}");
        var q = question(kind, hints);
        var random = new Random(SEED);
        var dates = new java.util.ArrayList<String>();
        for (int i = 0; i < 20000; i++) {
            String answer = generator.generate(q, TODAY, random);
            if (kind == QuestionKind.DATETIME) {
                assertTrue(answer.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:00"), answer);
                LocalDateTime dt = LocalDateTime.parse(answer);
                assertFalse(dt.toLocalTime().isBefore(LocalTime.of(6, 0)));
                assertFalse(dt.toLocalTime().isAfter(LocalTime.of(20, 0)));
                dates.add(dt.toLocalDate().toString());
            } else {
                assertEquals(LocalDate.parse(answer).toString(), answer);
                dates.add(answer);
            }
        }
        AnswerGeneratorTestSupport.assertUniform(dates, min.datesUntil(max.plusDays(1)).map(LocalDate::toString).toList());
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void defaultRangeEndsOnSuppliedTodayAndIntersectsHints(QuestionKind kind) throws Exception {
        var generator = generator("");
        var h = dates(TODAY.minusDays(2), true, TODAY.plusYears(1), true);
        var dates = AnswerGeneratorTestSupport.draws(generator, question(kind, h), TODAY, new Random(SEED), 12000)
                .stream().map(v -> v.substring(0, 10)).toList();
        AnswerGeneratorTestSupport.assertUniform(dates, List.of(TODAY.minusDays(2).toString(), TODAY.minusDays(1).toString(), TODAY.toString()));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void defaultRangeStillHasExactly365DaysWhenHintsAreEmpty(QuestionKind kind) throws Exception {
        var generator = generator("");
        var h = dates(TODAY.plusDays(2), true, TODAY.plusDays(1), true);
        var dates = AnswerGeneratorTestSupport.draws(generator, question(kind, h), TODAY, new Random(SEED), 20000)
                .stream().map(v -> v.substring(0, 10)).toList();
        AnswerGeneratorTestSupport.assertUniform(dates, TODAY.minusDays(364).datesUntil(TODAY.plusDays(1)).map(LocalDate::toString).toList());
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATETIME"})
    void datetimeTimePartStillSamplesEveryWholeMinuteUniformly(QuestionKind kind) throws Exception {
        var generator = generator("");
        var q = question(kind, dates(TODAY.plusYears(10), true, TODAY.plusYears(10), true));
        var times = AnswerGeneratorTestSupport.draws(generator, q, TODAY, new Random(SEED), 50000)
                .stream().map(v -> v.substring(11)).toList();
        var allowed = java.util.stream.IntStream.rangeClosed(360, 1200)
                .mapToObj(m -> String.format(java.util.Locale.ROOT, "%02d:%02d:00", m / 60, m % 60)).toList();
        AnswerGeneratorTestSupport.assertUniform(times, allowed);
    }
}
