package ctosim;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 05, Amendment 1: sample a day, then store the first of its month. */
class AnswerGeneratorMonthYearAppearanceTest {
    private static Question date(String appearance) {
        return new Question("answer", QuestionKind.DATE, List.of(), appearance, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"month-year", "month-year compact", "compact month-year",
            "compact month-year minimal", "  month-year  ", "compact   month-year   minimal"})
    void completeAppearanceWordReturnsFirstOfMonthEvenOutsideRange(String appearance) throws Exception {
        for (String answer : draws(override("\"min\":\"2025-03-17\",\"max\":\"2025-03-17\""),
                date(appearance), 20)) assertEquals("2025-03-01", answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"my_month-year", "month-year_extra", "month-years", "month-yearly",
            "compact my_month-year minimal"})
    void substringsDoNotActivateMonthYearAppearance(String appearance) throws Exception {
        for (String answer : draws(override("\"min\":\"2025-03-17\",\"max\":\"2025-03-17\""),
                date(appearance), 20)) assertEquals("2025-03-17", answer);
    }

    @ParameterizedTest
    @CsvSource({"2024-02-29,2024-02-01", "2025-02-28,2025-02-01", "2025-01-01,2025-01-01",
            "1999-12-31,1999-12-01", "2000-01-31,2000-01-01"})
    void singletonDateRangesNormalizeLeapDaysAndYearBoundaries(String day, String expected) throws Exception {
        for (String answer : draws(override("\"min\":\"" + day + "\",\"max\":\"" + day + "\""),
                date("month-year"), 20)) assertEquals(expected, answer);
    }

    @Test
    void rangeContainedWithinOneMonthAlwaysProducesTheSameFirstDay() throws Exception {
        for (String answer : draws(override("\"min\":\"2024-02-10\",\"max\":\"2024-02-29\""),
                date("month-year"), 200)) assertEquals("2024-02-01", answer);
    }

    @Test
    void appearancesForOtherKindsDoNotDisplaceMonthNormalization() throws Exception {
        for (String answer : draws(override("\"min\":\"2025-03-17\",\"max\":\"2025-03-17\""),
                date("label numbers_decimal month-year"), 20)) assertEquals("2025-03-01", answer);
    }

    @ParameterizedTest
    @CsvSource({"2025-01-31,2025-02-02", "2024-01-01,2024-03-31",
            "2025-01-01,2025-03-31", "1999-12-31,2000-01-02"})
    void monthsAreWeightedByIncludedDaysRatherThanDrawnUniformly(String min, String max) throws Exception {
        AnswerGenerator generator = override("\"min\":\"" + min + "\",\"max\":\"" + max + "\"");
        assertMonthDistribution(draws(generator, date("month-year"), 60_000),
                LocalDate.parse(min), LocalDate.parse(max));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2040-03-01", "2001-01-15", "2025-12-31"})
    void defaultRangeUsesExactly365DaysEndingOnSuppliedToday(String todayText) throws Exception {
        LocalDate today = LocalDate.parse(todayText);
        assertMonthDistribution(draws(generator(""), date("month-year"), today, new Random(SEED), 90_000),
                today.minusDays(364), today);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"min\":1,\"max\":1", "\"texts\":[\"invalid\"]",
            "\"weights\":{\"invalid\":1}"})
    void irrelevantOverridesPreserveDefaultDateRange(String field) throws Exception {
        assertMonthDistribution(draws(override(field), date("month-year"), 60_000),
                TODAY.minusDays(364), TODAY);
    }

    @Test
    void monthYearDoesNotNormalizeDatetimeQuestions() throws Exception {
        Question question = new Question("answer", QuestionKind.DATETIME, List.of(), "month-year", true);
        for (String answer : draws(override("\"min\":\"2025-03-17\",\"max\":\"2025-03-17\""),
                question, 100)) {
            assertNotNull(answer);
            assertTrue(answer.matches("2025-03-17T[0-9]{2}:[0-9]{2}:00"), answer);
        }
    }

    @Test
    void monthYearDoesNotReplaceDefaultTextGeneration() throws Exception {
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), "month-year", true);
        for (String answer : draws(generator(""), question, 100)) {
            assertNotNull(answer);
            assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
        }
    }

    private static void assertMonthDistribution(List<String> answers, LocalDate min, LocalDate max) {
        // Independent oracle: enumerate the contract's inclusive day range, then group by month.
        Map<String, Integer> daysPerMonth = new HashMap<>();
        for (LocalDate day = min; !day.isAfter(max); day = day.plusDays(1)) {
            daysPerMonth.merge(day.withDayOfMonth(1).toString(), 1, Integer::sum);
        }
        var actual = counts(answers);
        assertEquals(daysPerMonth.keySet(), actual.keySet(), "Only normalized dates from the range are allowed");
        int totalDays = daysPerMonth.values().stream().mapToInt(Integer::intValue).sum();
        daysPerMonth.forEach((month, days) ->
                assertRate(actual.get(month), answers.size(), (double) days / totalDays, month));
    }
}
