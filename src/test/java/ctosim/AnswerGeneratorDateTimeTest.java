package ctosim;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorDateTimeTest {
    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void defaultDateRangeIsExactly365DaysEndingOnProvidedToday(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("\"start\":\"2026-01-05T08:00:00\",\"fieldworkDays\":1");
        // A leap year, a year boundary, and dates far from the machine clock.
        for (LocalDate today : List.of(TODAY, LocalDate.of(1901, 1, 1), LocalDate.of(2024, 2, 29))) {
            List<String> dates = draws(generator, question(kind), today, new Random(SEED), DRAWS)
                    .stream().map(a -> extractDate(kind, a).toString()).toList();
            List<String> allowed = IntStream.rangeClosed(0, 364).mapToObj(i -> today.minusDays(i).toString()).toList();
            assertUniform(dates, allowed);
            assertTrue(dates.contains(today.toString()), "today must be included");
            assertTrue(dates.contains(today.minusDays(364).toString()), "oldest day must be included");
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void dateOverrideIsUniformInclusiveAndCanBeOutsideDefaultWindow(QuestionKind kind) throws Exception {
        AnswerGenerator generator = override("\"min\":\"2000-02-28\",\"max\":\"2000-03-01\"");
        List<String> dates = draws(generator, question(kind), DRAWS).stream()
                .map(a -> extractDate(kind, a).toString()).toList();
        assertUniform(dates, List.of("2000-02-28", "2000-02-29", "2000-03-01"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void equalDateBoundsAlwaysUseThatDate(QuestionKind kind) throws Exception {
        for (String answer : draws(override("\"min\":\"2099-12-31\",\"max\":\"2099-12-31\""),
                question(kind), 300)) assertEquals(LocalDate.of(2099, 12, 31), extractDate(kind, answer));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TIME", "DATETIME"})
    void timeIsUniformAcross841WholeMinutesIncluding0600And2000(QuestionKind kind) throws Exception {
        List<String> times = draws(generator(""), question(kind), 60_000).stream()
                .map(a -> extractTime(kind, a).toString()).toList();
        List<String> allowed = IntStream.rangeClosed(0, 840)
                .mapToObj(i -> LocalTime.of(6, 0).plusMinutes(i).toString()).toList();
        assertUniform(times, allowed);
        assertTrue(times.contains("06:00"));
        assertTrue(times.contains("20:00"));
    }

    @Test
    void datetimeCombinesUniformDatesAndTimesWithoutTimeZone() throws Exception {
        AnswerGenerator generator = override("\"min\":\"2020-12-31\",\"max\":\"2021-01-01\"");
        int[][] jointCounts = new int[2][3];
        for (String answer : draws(generator, question(QuestionKind.DATETIME), DRAWS)) {
            LocalDate date = extractDate(QuestionKind.DATETIME, answer);
            LocalTime time = extractTime(QuestionKind.DATETIME, answer);
            int day = Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.of(2020, 12, 31), date));
            int minute = Math.toIntExact(ChronoUnit.MINUTES.between(LocalTime.of(6, 0), time));
            assertTrue(day >= 0 && day <= 1, answer);
            assertTrue(minute >= 0 && minute <= 840, answer);
            jointCounts[day][minute / 281]++;
        }
        for (int day = 0; day < 2; day++) {
            for (int bin = 0; bin < 3; bin++) {
                int width = bin == 2 ? 279 : 281;
                assertRate(jointCounts[day][bin], DRAWS, 0.5 * width / 841, "date/time bin " + day + "/" + bin);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DATE", "DATETIME"})
    void numericRangeTextsAndWeightsAreIgnoredForDates(QuestionKind kind) throws Exception {
        AnswerGenerator generator = override("\"min\":0,\"max\":0,\"texts\":[\"wrong\"],\"weights\":{\"wrong\":1}");
        List<String> dates = draws(generator, question(kind), DRAWS).stream()
                .map(a -> extractDate(kind, a).toString()).toList();
        assertUniform(dates, IntStream.rangeClosed(0, 364).mapToObj(i -> TODAY.minusDays(i).toString()).toList());
    }

    @Test
    void applicableDateRangeStillWorksWithIrrelevantTextsAndWeights() throws Exception {
        AnswerGenerator generator = override("\"min\":\"2000-01-01\",\"max\":\"2000-01-01\","
                + "\"texts\":[\"wrong\"],\"weights\":{\"wrong\":1}");
        for (String answer : draws(generator, question(QuestionKind.DATE), 100)) assertEquals("2000-01-01", answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"min\":0,\"max\":0", "\"min\":\"2000-01-01\",\"max\":\"2000-01-01\""})
    void timeIgnoresBothRangeTypes(String range) throws Exception {
        List<String> times = draws(override(range + ",\"texts\":[\"wrong\"],\"weights\":{\"wrong\":1}"),
                question(QuestionKind.TIME), DRAWS).stream().map(a -> extractTime(QuestionKind.TIME, a).toString()).toList();
        assertUniform(times, IntStream.rangeClosed(0, 840)
                .mapToObj(i -> LocalTime.of(6, 0).plusMinutes(i).toString()).toList());
    }

    private static LocalDate extractDate(QuestionKind kind, String answer) {
        assertNotNull(answer);
        if (kind == QuestionKind.DATE) {
            assertTrue(answer.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), answer);
            return LocalDate.parse(answer);
        }
        assertDatetimeFormat(answer);
        return LocalDateTime.parse(answer).toLocalDate();
    }

    private static LocalTime extractTime(QuestionKind kind, String answer) {
        assertNotNull(answer);
        if (kind == QuestionKind.TIME) {
            assertTrue(answer.matches("[0-9]{2}:[0-9]{2}:00"), answer);
            return LocalTime.parse(answer);
        }
        assertDatetimeFormat(answer);
        return LocalDateTime.parse(answer).toLocalTime();
    }

    private static void assertDatetimeFormat(String answer) {
        assertTrue(answer.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:00"), answer);
    }
}
