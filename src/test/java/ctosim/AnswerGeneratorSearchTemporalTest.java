package ctosim;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.AnswerGeneratorSearchSupport.*;
import static ctosim.ConstraintHintsAmendment2Support.hints;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorSearchTemporalTest {
    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void defaultDrawingDatesCoverExactly365DaysEndingOnTheSuppliedToday(QuestionKind kind) throws Exception {
        assertLevel(dates(TODAY.minusDays(364), TODAY, kind), search(generator(""), question(kind)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void dateOverrideIncludesLeapDayAndBothEnds(QuestionKind kind) throws Exception {
        assertLevel(dates(LocalDate.of(2000, 2, 28), LocalDate.of(2000, 3, 1), kind), search(generator(
                "\"fields\":{\"answer\":{\"min\":\"2000-02-28\",\"max\":\"2000-03-01\"}}"), question(kind)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void numericOverrideIsIgnoredForDateDrawingRanges(QuestionKind kind) throws Exception {
        assertLevel(dates(TODAY.minusDays(364), TODAY, kind), search(generator(
                "\"fields\":{\"answer\":{\"min\":2,\"max\":4}}"), question(kind)), 0, 2);
    }

    static Stream<Arguments> hintCases() {
        LocalDate start = LocalDate.of(2000, 1, 1);
        return Stream.of(
                Arguments.of("intersection", 2, true, 5, true, 2, 5),
                Arguments.of("exclusive ends", 2, false, 5, false, 3, 4),
                Arguments.of("disjoint bounded", 20, true, 22, true, 20, 22),
                Arguments.of("singleton", 20, true, 20, true, 20, 20),
                Arguments.of("lower intersection", 8, true, null, true, 8, 10),
                Arguments.of("upper intersection", null, true, 2, true, 0, 2),
                Arguments.of("lower disjoint", 20, false, null, true, 21, 385),
                Arguments.of("upper disjoint", null, true, -20, false, -385, -21),
                Arguments.of("empty ignored", 8, true, 2, true, 0, 10),
                Arguments.of("exclusive empty ignored", 4, false, 5, false, 0, 10))
                .flatMap(row -> Stream.of(QuestionKind.DATE, QuestionKind.DATETIME).map(kind -> {
                    Object[] a = row.get();
                    ConstraintHints h = new ConstraintHints(null, null,
                            a[1] == null ? null : new DateBound(start.plusDays((int) a[1]), (boolean) a[2]),
                            a[3] == null ? null : new DateBound(start.plusDays((int) a[3]), (boolean) a[4]),
                            null, null, null);
                    return Arguments.of(a[0], kind, h, start.plusDays((int) a[5]), start.plusDays((int) a[6]));
                }));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("hintCases")
    void temporalDrawingRangesApplyDateHints(String label, QuestionKind kind, ConstraintHints h,
                                             LocalDate first, LocalDate last) throws Exception {
        assertLevel(dates(first, last, kind), search(generator(
                "\"fields\":{\"answer\":{\"min\":\"2000-01-01\",\"max\":\"2000-01-11\"}}"),
                question(kind, h)), 0, 2);
    }

    static Stream<Arguments> cappedDates() {
        return Stream.of(QuestionKind.DATE, QuestionKind.DATETIME).flatMap(kind ->
                Stream.of(9999, 10000, 10001, 20000).map(span -> Arguments.of(kind, span)));
    }

    @ParameterizedTest @MethodSource("cappedDates")
    void dateCapEnumeratesSmallRangesAndSpreadsLargeRangesIncludingBothEnds(QuestionKind kind, int span)
            throws Exception {
        LocalDate first = LocalDate.of(1980, 1, 1), last = first.plusDays(span);
        var values = search(generator("\"fields\":{\"answer\":{\"min\":\"" + first
                + "\",\"max\":\"" + last + "\"}}"), question(kind)).get(0);
        if (span <= 10000) {
            assertEquals(dates(first, last, kind), values);
        } else {
            assertEquals(10001, values.size());
            assertEquals(dates(first, first, kind).get(0), values.get(0));
            assertEquals(dates(last, last, kind).get(0), values.get(10000));
            long previous = Long.MIN_VALUE;
            for (int i = 0; i <= 10000; i++) {
                String value = values.get(i);
                if (kind == QuestionKind.DATETIME) assertTrue(value.endsWith("T12:00:00"));
                long day = LocalDate.parse(value.substring(0, 10)).toEpochDay();
                assertEquals(first.toEpochDay() + i * span / 10000d, day, .50000001, "date grid point " + i);
                assertTrue(day > previous);
                previous = day;
            }
        }
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void dateAlternativesPreserveOrderDeduplicateOverlapAndSkipContradictions(QuestionKind kind) throws Exception {
        LocalDate d = TODAY.minusDays(10);
        var h = hints().alternatives(hints().dates(d.plusDays(4), d.plusDays(6)).build(),
                hints().dates(d.plusDays(9), d.plusDays(7)).build(),
                hints().dates(d, d.plusDays(1)).build(), hints().dates(d.plusDays(5), d.plusDays(7)).build()).build();
        var expected = new ArrayList<>(dates(d.plusDays(4), d.plusDays(6), kind));
        expected.addAll(dates(d, d.plusDays(1), kind));
        expected.addAll(dates(d.plusDays(7), d.plusDays(7), kind));
        assertLevel(expected, search(generator(""), question(kind, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void largeDateAlternativesEachReceiveTheirOwn10001ValueBudget(QuestionKind kind) throws Exception {
        LocalDate first = LocalDate.of(1800, 1, 1), second = LocalDate.of(1900, 1, 1);
        var h = hints().alternatives(hints().dates(second, second.plusDays(20000)).build(),
                hints().dates(first, first.plusDays(20000)).build()).build();
        List<String> expected = new ArrayList<>();
        for (LocalDate start : List.of(second, first)) for (int i = 0; i <= 10000; i++) {
            LocalDate day = start.plusDays(2L * i);
            expected.add(day + (kind == QuestionKind.DATETIME ? "T12:00:00" : ""));
        }
        assertLevel(expected, search(generator(""), question(kind, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void broadDateLevelUsesExactDayOffsetsAndIgnoresNarrowDrawingHints(QuestionKind kind) throws Exception {
        var g = generator("\"fields\":{\"answer\":{\"min\":\"2000-01-01\",\"max\":\"2000-01-01\"}}");
        var q = question(kind, hints().dates(TODAY, TODAY).build());
        for (LocalDate today : List.of(TODAY, LocalDate.of(1999, 12, 31))) {
            assertLevel(dates(today.minusDays(36524), today.plusDays(3652), kind), g.searchValues(q, today), 1, 2);
        }
    }

    @Test
    void timeFirstLevelIncludesAll1440MinutesOfTheDay() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int h = 0; h < 24; h++) for (int m = 0; m < 60; m++) expected.add(time(h, m, 0));
        assertLevel(expected, search(generator(""), question(QuestionKind.TIME)), 0, 2);
    }

    @Test
    void timeSecondLevelIncludesAll86400SecondsOfTheDay() throws Exception {
        List<String> expected = new ArrayList<>();
        for (int h = 0; h < 24; h++) for (int m = 0; m < 60; m++) for (int s = 0; s < 60; s++) {
            expected.add(time(h, m, s));
        }
        assertLevel(expected, search(generator(""), question(QuestionKind.TIME)), 1, 2);
    }

    private static String time(int h, int m, int s) {
        return twoDigits(h) + ":" + twoDigits(m) + ":" + twoDigits(s);
    }

    private static String twoDigits(int n) { return n < 10 ? "0" + n : Integer.toString(n); }
}
