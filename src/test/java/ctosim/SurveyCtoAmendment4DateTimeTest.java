package ctosim;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static ctosim.SurveyCtoAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Tests change the default locale and time zone and restore them in finally")
class SurveyCtoAmendment4DateTimeTest {
    static Stream<Arguments> abbreviations() {
        List<Arguments> cases = new ArrayList<>();
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String locale : List.of("fr-FR", "de-DE", "tr-TR", "ja-JP")) {
            for (int m = 1; m <= 12; m++) cases.add(Arguments.of(locale, LocalDate.of(2026, m, 5).toString(), "%b", months[m - 1]));
            for (int d = 0; d < 7; d++) cases.add(Arguments.of(locale, LocalDate.of(2026, 1, 5).plusDays(d).toString(), "%a", days[d]));
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("abbreviations")
    void dayAndMonthAbbreviationsAreEnglishInEveryDefaultLocale(String language, String date, String pattern, String expected) throws Exception {
        Locale old = Locale.getDefault();
        Locale oldFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        try {
            Locale.setDefault(Locale.forLanguageTag(language));
            var session = expressionForm("format-date-time(date('" + date + "'), '" + pattern + "')").newEntryController();
            assertEquals("[" + expected + "]", value(child(root(session), "result")));
        } finally {
            Locale.setDefault(old);
            Locale.setDefault(Locale.Category.FORMAT, oldFormat);
            Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
        }
    }

    @Test void numericDateFormattingKeepsThePublishedExample() throws Exception {
        var session = expressionForm("format-date-time(date('2026-01-05'), '%Y/%m/%d %a %b %e %n')").newEntryController();
        assertEquals("[2026/01/05 Mon Jan 5 1]", value(child(root(session), "result")));
    }

    static Stream<Arguments> decimalTimes() {
        return Stream.of(Arguments.of("06:00:00", 0.25), Arguments.of("2013-01-31T18:00:00", 0.75),
                Arguments.of("00:00:00", 0.0), Arguments.of("12:00:00", 0.5),
                Arguments.of("23:59:59", 86399.0 / 86400),
                Arguments.of("01:02:03.125", 3723.125 / 86400),
                Arguments.of("23:59:59.999", 86399.999 / 86400),
                Arguments.of("06:00:00Z", 0.25), Arguments.of("06:00:00+05:45", 0.25),
                Arguments.of("06:00:00-12:00", 0.25),
                Arguments.of("00:15:00+14:00", 900.0 / 86400),
                Arguments.of("23:45:00-12:00", 85500.0 / 86400),
                Arguments.of("2024-02-29T01:02:03.125+05:45", 3723.125 / 86400),
                Arguments.of("2013-01-31T18:00:00Z", 0.75),
                Arguments.of("2013-01-31T18:00:00-04:00", 0.75));
    }

    @ParameterizedTest @MethodSource("decimalTimes")
    void decimalTimeUsesWrittenClockTimeIncludingFractionsAndIgnoresOffsets(String input, double expected) throws Exception {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            String call = "decimal-time('" + input + "')";
            var session = calculation(call).newEntryController();
            assertEquals(expected, assertInstanceOf(Number.class, value(child(root(session), "result"))).doubleValue(), 1e-12);
        } finally { TimeZone.setDefault(old); }
    }

    static Stream<String> invalidDecimalTimes() {
        return Stream.of("", "garbage", "2026-01-05", "06:00", "25:00:00", "12:60:00", "-1:00:00");
    }

    @ParameterizedTest @MethodSource("invalidDecimalTimes")
    void decimalTimeReturnsNaNForValuesThatAreNeitherTimesNorDateTimes(String input) throws Exception {
        var session = calculation("string(decimal-time('" + input + "'))").newEntryController();
        assertValue("NaN", value(child(root(session), "result")));
    }

    @ParameterizedTest @MethodSource("invalidDecimalTimes")
    void decimalTimeNaNCalculationsStoreNoValue(String input) throws Exception {
        var session = calculation("decimal-time('" + input + "')").newEntryController();
        assertNull(child(root(session), "result").getValue());
    }

    @Test void decimalTimeAcceptsAStringFieldAndKeepsNativeTimeFieldSupport() throws Exception {
        var form = FormWalkerTestSupport.form("<text>06:00:00</text><clock>18:00:00</clock><a/><b/>",
                FormWalkerTestSupport.bind("text", "string", "") + FormWalkerTestSupport.bind("clock", "time", "")
                + FormWalkerTestSupport.bind("a", "decimal", "calculate=\"decimal-time(/data/text)\"")
                + FormWalkerTestSupport.bind("b", "decimal", "calculate=\"decimal-time(/data/clock)\""),
                FormWalkerTestSupport.input("text") + FormWalkerTestSupport.input("clock"));
        var session = form.newEntryController();
        assertEquals(0.25, value(child(root(session), "a")));
        assertEquals(0.75, value(child(root(session), "b")));
    }

    @ParameterizedTest @CsvSource({"UTC,2026-01-01", "GMT-08:00,2025-12-31", "GMT+14:00,2026-01-01"})
    void dateTimeCalculationsStoreTheCalendarDateOfTheInstantInTheDefaultZone(String zone, String expected) throws Exception {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var session = calculation("date-time('2026-01-01T00:30:00Z')").newEntryController();
            assertValue(expected, evaluate(session, "string(/data/result)"));
            assertEquals("[" + expected + "]", value(child(root(session), "observed")));
        } finally { TimeZone.setDefault(old); }
    }

    @ParameterizedTest @CsvSource({"date(20000),2024-10-04", "date('2024-02-29'),2024-02-29",
            "date-time('2026-10-11T23:59:59'),2026-10-11"})
    void dateValuedCalculationsStoreOnlyAnIsoDateAndConsumersReadThatValue(String expression, String expected) throws Exception {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var session = calculation(expression).newEntryController();
            assertValue(expected, evaluate(session, "string(/data/result)"));
            assertEquals("[" + expected + "]", value(child(root(session), "observed")));
        } finally { TimeZone.setDefault(old); }
    }

    @ParameterizedTest @ValueSource(strings = {"today()", "now()"})
    void ordinaryTodayAndNowStoreOnlyTheCurrentCalendarDate(String function) throws Exception {
        LocalDate before = LocalDate.now();
        var session = calculation(function).newEntryController();
        LocalDate after = LocalDate.now();
        String stored = assertInstanceOf(String.class, evaluate(session, "string(/data/result)"));
        assertTrue(stored.equals(before.toString()) || stored.equals(after.toString()),
                "Stored value must be a date only, allowing a midnight crossed during initialization: " + stored);
        assertEquals("[" + stored + "]", value(child(root(session), "observed")));
    }

    @ParameterizedTest @ValueSource(strings = {"string", "date", "dateTime"})
    void dateOnlyCalculationStorageDoesNotDependOnTheDeclaredFieldType(String type) throws Exception {
        var form = FormWalkerTestSupport.form("<tick/><result/><observed/>",
                FormWalkerTestSupport.bind("tick", "int", "")
                + FormWalkerTestSupport.bind("result", type, "calculate=\"date-time('2026-10-11T18:07:09')\"")
                + FormWalkerTestSupport.bind("observed", "string", "calculate=\"format-date-time(/data/result, '%Y-%m-%d %H:%M:%S')\""),
                FormWalkerTestSupport.input("tick"));
        var session = form.newEntryController();
        assertValue("2026-10-11", evaluate(session, "string(/data/result)"));
        assertEquals("2026-10-11 00:00:00", value(child(root(session), "observed")),
                "A consumer must read the stored date, with the original time discarded");
    }

    @ParameterizedTest @ValueSource(strings = {"today()", "now()"})
    void simulatedTodayAndNowStoreTheClockDateAndRecalculateAfterMidnight(String function) throws Exception {
        var clock = new SimulatedClock(LocalDateTime.of(2026, 10, 11, 23, 59, 59));
        var session = calculation("if(/data/tick >= 0, " + function + ", date('2000-01-01'))").newSimulatedSession(clock, 93);
        assertValue("2026-10-11", evaluate(session, "string(/data/result)"));
        assertEquals("[2026-10-11]", value(child(root(session), "observed")));
        clock.advance(Duration.ofSeconds(2));
        answer(session, child(root(session), "tick"), new IntegerData(1));
        assertValue("2026-10-12", evaluate(session, "string(/data/result)"));
        assertEquals("[2026-10-12]", value(child(root(session), "observed")));
    }

    @Test void formattedNowRetainsItsTimeOfDay() throws Exception {
        var session = calculation("format-date-time(now(), '%Y-%m-%d %H:%M:%S')")
                .newSimulatedSession(new SimulatedClock(LocalDateTime.of(2026, 10, 11, 18, 7, 9)), 42);
        assertEquals("2026-10-11 18:07:09", value(child(root(session), "result")));
    }

    @Test void decimalDateTimeWrappedAroundNowRetainsItsFractionOfADay() throws Exception {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var session = calculation("decimal-date-time(now())").newSimulatedSession(
                    new SimulatedClock(LocalDateTime.of(2026, 10, 11, 18, 0)), 42);
            double expected = LocalDate.of(2026, 10, 11).toEpochDay() + 0.75;
            assertEquals(expected, assertInstanceOf(Number.class, value(child(root(session), "result"))).doubleValue(), 1e-9);
        } finally { TimeZone.setDefault(old); }
    }

    @ParameterizedTest @CsvSource({"+14:00,2026-01-01", "-08:00,2025-12-31"})
    void walkedDateCalculationUsesTheSessionZoneInsteadOfTheMachinesZone(String zone, String expected) throws Exception {
        TimeZone old = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            var form = calculation("date-time('2026-01-01T00:30:00Z')");
            var config = FormWalkerTestSupport.config("\"timeZone\":\"" + zone + "\"");
            var submission = new FormWalker(form, config).walk(1);
            assertEquals(expected, FormWalkerTestSupport.value(submission, "result"));
            assertEquals("[" + expected + "]", FormWalkerTestSupport.value(submission, "observed"));
        } finally { TimeZone.setDefault(old); }
    }
}
