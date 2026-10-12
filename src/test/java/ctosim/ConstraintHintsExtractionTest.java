package ctosim;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IntegerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsExtractionTest {
    // Amendment 2 adds candidates without changing these original components.
    private static void assertOriginalComponents(ConstraintHints expected, ConstraintHints actual) {
        assertAll(() -> assertEquals(expected.lower(), actual.lower()),
                () -> assertEquals(expected.upper(), actual.upper()),
                () -> assertEquals(expected.earliest(), actual.earliest()),
                () -> assertEquals(expected.latest(), actual.latest()),
                () -> assertEquals(expected.minLength(), actual.minLength()),
                () -> assertEquals(expected.maxLength(), actual.maxLength()),
                () -> assertEquals(expected.pattern(), actual.pattern()),
                () -> assertTrue(actual.alternatives().isEmpty(), "A single conjunction has direct hints"));
    }

    @Test void noneHasSevenNullComponents() {
        ConstraintHints h = ConstraintHints.none();
        assertNotNull(h);
        assertAll(() -> assertNull(h.lower()), () -> assertNull(h.upper()),
                () -> assertNull(h.earliest()), () -> assertNull(h.latest()),
                () -> assertNull(h.minLength()), () -> assertNull(h.maxLength()), () -> assertNull(h.pattern()));
    }

    @Test void fiveArgumentQuestionRetainsCompatibilityAndSuppliesNone() {
        assertEquals(ConstraintHints.none(), new Question("q", QuestionKind.TEXT, List.of(), "", false).hints());
    }

    @Test void sixArgumentQuestionRetainsHintsAndDefensivelyCopiesChoices() {
        var choices = new ArrayList<>(List.of("one", "two"));
        var hints = text(2, 8, "[a-z]+");
        var q = new Question("q", QuestionKind.SELECT_ONE, choices, "minimal", true, hints);
        choices.clear();
        assertAll(() -> assertEquals(hints, q.hints()), () -> assertEquals("q", q.name()),
                () -> assertEquals(QuestionKind.SELECT_ONE, q.kind()), () -> assertTrue(q.required()),
                () -> assertEquals("minimal", q.appearance()),
                () -> assertEquals(List.of("one", "two"), q.choices()),
                () -> assertThrows(UnsupportedOperationException.class, () -> q.choices().add("three")));
    }

    @Test void sixArgumentConstructorKeepsOriginalNullValidation() {
        var h = ConstraintHints.none();
        assertAll(() -> assertThrows(NullPointerException.class, () -> new Question(null, QuestionKind.TEXT, List.of(), "", true, h)),
                () -> assertThrows(NullPointerException.class, () -> new Question("q", null, List.of(), "", true, h)),
                () -> assertThrows(NullPointerException.class, () -> new Question("q", QuestionKind.TEXT, null, "", true, h)),
                () -> assertThrows(NullPointerException.class, () -> new Question("q", QuestionKind.TEXT, List.of(), null, true, h)),
                () -> assertThrows(NullPointerException.class, () -> new Question("q", QuestionKind.TEXT, java.util.Arrays.asList("a", null), "", true, h)));
    }

    @Test void aSuccessfullyConstructedQuestionNeverExposesNullHints() {
        try {
            var q = new Question("q", QuestionKind.TEXT, List.of(), "", true, null);
            assertNotNull(q.hints(), "The sixth component is specified as never null");
        } catch (RuntimeException rejectedInvalidInput) {
            // The spec allows rejecting null; it does not specify a particular null-input policy.
        }
    }

    @Test void beginningEndGroupAndUnconstrainedQuestionGiveNone() throws Exception {
        var s = extractionForm("int", ". > 10").newEntryController();
        assertEquals(EVENT_BEGINNING_OF_FORM, s.getModel().getEvent());
        assertEquals(ConstraintHints.none(), ConstraintHints.forCurrentQuestion(s));
        boolean sawGroup = false;
        for (int i = 0; i < 20; i++) {
            int event = s.stepToNextEvent();
            if (event == EVENT_GROUP) {
                sawGroup = true;
                assertEquals(ConstraintHints.none(), ConstraintHints.forCurrentQuestion(s));
            }
            if (event == EVENT_END_OF_FORM) break;
        }
        assertTrue(sawGroup);
        assertEquals(EVENT_END_OF_FORM, s.getModel().getEvent());
        assertEquals(ConstraintHints.none(), ConstraintHints.forCurrentQuestion(s));
        SimulatedSessionTestSupport.question(s, "plain");
        assertEquals(ConstraintHints.none(), ConstraintHints.forCurrentQuestion(s));
    }

    @Test void repeatCreationPromptGivesNoHints() throws Exception {
        var loaded = FormWalkerTestSupport.form("<row jr:template=\"\"><answer/></row>",
                FormWalkerTestSupport.bind("row/answer", "int", "constraint=\". &gt; 10\""),
                "<repeat nodeset=\"/data/row\">" + FormWalkerTestSupport.input("row/answer") + "</repeat>");
        var s = loaded.newEntryController();
        boolean reached = false;
        for (int i = 0; i < 20; i++) {
            int event = s.stepToNextEvent();
            if (event == EVENT_PROMPT_NEW_REPEAT) {
                reached = true;
                break;
            }
            if (event == EVENT_END_OF_FORM) break;
        }
        assertTrue(reached, "Fixture must reach the engine's add-repeat prompt");
        assertEquals(ConstraintHints.none(), ConstraintHints.forCurrentQuestion(s));
    }

    static Stream<Arguments> comparisons() {
        return Stream.of(
            Arguments.of(". > 3", numeric(3d, false, null, false)),
            Arguments.of(". >= 3", numeric(3d, true, null, false)),
            Arguments.of(". < 3", numeric(null, false, 3d, false)),
            Arguments.of(". <= 3", numeric(null, false, 3d, true)),
            Arguments.of("3 < .", numeric(3d, false, null, false)),
            Arguments.of("3 <= .", numeric(3d, true, null, false)),
            Arguments.of("3 > .", numeric(null, false, 3d, false)),
            Arguments.of("3 >= .", numeric(null, false, 3d, true)),
            Arguments.of(". = -2.5", numeric(-2.5, true, -2.5, true)),
            Arguments.of("-2.5 = .", numeric(-2.5, true, -2.5, true)),
            Arguments.of(". >= '42.5'", numeric(42.5, true, null, false)),
            Arguments.of(". >= (2 + 3 * 4)", numeric(14d, true, null, false)),
            Arguments.of("(. >= 1 and . > 3) and (. <= 9 and . < 8)", numeric(3d, false, 8d, false)),
            Arguments.of(". >= 4 and . > 4 and . < 9 and . <= 9", numeric(4d, false, 9d, false)),
            Arguments.of(". > 4 and . >= 4 and . <= 9 and . < 9", numeric(4d, false, 9d, false)),
            Arguments.of(". > 1 and . >= 2 and . < 9 and . <= 8", numeric(2d, true, 8d, true)),
            Arguments.of(". >= 10 and . <= 2", numeric(10d, true, 2d, true)),
            Arguments.of(". = 5 and . > 5", numeric(5d, false, 5d, true)),
            Arguments.of(".>=900000000 and .<=999999999", numeric(900000000d, true, 999999999d, true)));
    }

    @ParameterizedTest @MethodSource("comparisons")
    void extractsOnlyTheTightestNumericBounds(String expression, ConstraintHints expected) throws Exception {
        assertOriginalComponents(expected, extract(expression));
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        "string-length(.) > 3|4|", "string-length(.) >= 3|3|",
        "string-length(.) < 3||2", "string-length(.) <= 3||3",
        "3 < string-length(.)|4|", "3 <= string-length(.)|3|",
        "3 > string-length(.)||2", "3 >= string-length(.)||3",
        "string-length(.) = 3|3|3", "3 = string-length(.)|3|3",
        "string-length(.) >= 3.5|4|", "string-length(.) > 3.5|4|",
        "string-length(.) <= 9.5||9", "string-length(.) < 9.5||9",
        "string-length(.) = 3.5|4|3", "string-length(.) >= -3|0|",
        "string-length(.) > -0.5|0|", "string-length(.) <= -2||-2",
        "string-length(.) >= 0|0|", "string-length(.) = 0|0|0",
        "string-length(.) > 2 and string-length(.) >= 5 and string-length(.) < 9 and string-length(.) <= 7|5|7",
        "string-length(.) > /data/source|18|"})
    void lengthComparisonsBecomeInclusiveIntegerBounds(String expression, Integer min, Integer max) throws Exception {
        assertEquals(text(min, max, null), extract(expression));
    }

    @ParameterizedTest @ValueSource(strings = {
        ". * 2 < 10", "10 > . * 2", "string(.) = '5'", "string-length(../x) > 3",
        ". > . + 1", ". <= string-length(.)", "string-length(.) > .",
        "regex(., concat('a', 'b'))", "regex(../x, '[0-9]+')", "true()"})
    void unsupportedShapesContributeNoHints(String expression) throws Exception {
        assertEquals(ConstraintHints.none(), extract(expression));
    }

    @Test void disjunctionInsideConjunctionDistributesValidSiblingsToEachBranch() throws Exception {
        var h = extract("(. > 10 or . < 5) and . >= 3 and . <= 20");
        assertOriginalComponents(ConstraintHints.none(), new ConstraintHints(h.lower(), h.upper(),
                h.earliest(), h.latest(), h.minLength(), h.maxLength(), h.pattern()));
        assertEquals(List.of(numeric(10d, false, 20d, true), numeric(3d, true, 5d, false)), h.alternatives());
        assertEquals(List.of("11", "20", "3", "4"), h.candidates());
    }

    @Test void rangeOrEqualityProducesOrderedAlternativesAndCandidates() throws Exception {
        var h = extract("(. > 3 and . < 20) or . = 100");
        assertEquals(List.of(numeric(3d, false, 20d, false), numeric(100d, true, 100d, true)), h.alternatives());
        assertEquals(List.of("4", "19", "100"), h.candidates());
    }

    @Test void disjunctionOfOneSidedBoundsProducesOrderedAlternatives() throws Exception {
        assertEquals(List.of(numeric(5d, false, null, false), numeric(null, false, 2d, false)),
                extract(". > 5 or . < 2").alternatives());
    }

    @Test void negatedComparisonProducesItsComplementaryBound() throws Exception {
        assertEquals(numeric(null, false, 5d, true), extract("not(. > 5)"));
    }

    @Test void selectedLiteralProducesAnIncludedChoice() throws Exception {
        var h = extract("selected(., '1')");
        assertOriginalComponents(ConstraintHints.none(), h);
        assertEquals(List.of("1"), List.copyOf(h.included()));
        assertTrue(h.excluded().isEmpty());
    }

    @Test void inequalityProducesAnExcludedValue() throws Exception {
        var h = extract(". != 5");
        assertOriginalComponents(ConstraintHints.none(), h);
        assertEquals(List.of("5"), List.copyOf(h.excluded()));
        assertTrue(h.included().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {
        "''", "'not numeric'", "/data/empty", "number('bad')", "0 div 0", "1 div 0", "-1 div 0",
        "(1 + regex('x', '['))"})
    void unusableOrFailingExpressionsDoNotThrowOrSuppressOtherTerms(String expression) throws Exception {
        assertEquals(numeric(null, false, 20d, true), extract(". > " + expression + " and . <= 20"));
    }

    @ParameterizedTest @ValueSource(strings = {"^09[0-9]{8}$", "", "[", "^café[0-9]$", "a\\d{2}"})
    void literalPatternIsPreservedWithoutValidationOrNormalization(String pattern) throws Exception {
        assertEquals(text(null, null, pattern), extract("regex(., '" + pattern + "')"));
    }

    @Test void firstRegexWinsAndAllKindsOfHintsCanCoexist() throws Exception {
        assertOriginalComponents(new ConstraintHints(new Bound(1, true), new Bound(9, false),
                new DateBound(TODAY, true), null, 2, 4, "first"),
                extract(". >= 1 and regex(., 'first') and string-length(.) > 1 and . < 9"
                        + " and regex(., 'second') and string-length(.) <= 4 and . >= today()"));
    }

    @Test void nonLiteralRegexBeforeLiteralDoesNotClaimThePatternSlot() throws Exception {
        assertEquals(text(null, null, "second"), extract("regex(., concat('fir', 'st')) and regex(., 'second')"));
    }

    @Test void relativeAndAbsoluteReferencesUseQuestionContextAndCurrentAnswers() throws Exception {
        var s = session("int", ". >= ../x and . <= /data/source + 100");
        assertOriginalComponents(numeric(31d, true, 117d, true), ConstraintHints.forCurrentQuestion(s));
        FormIndex index = SimulatedSessionTestSupport.question(s, "source");
        assertEquals(ANSWER_OK, s.answerQuestion(index, new IntegerData(200), true));
        SimulatedSessionTestSupport.question(s, "group/answer");
        assertOriginalComponents(numeric(31d, true, 300d, true), ConstraintHints.forCurrentQuestion(s));
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
        ". > /data/date|false|true", ". >= /data/date|true|true",
        "/data/date < .|false|true", "/data/date <= .|true|true",
        ". < /data/date|false|false", ". <= /data/date|true|false",
        "/data/date > .|false|false", "/data/date >= .|true|false"})
    void dateValuesProduceDateBoundsInsteadOfEpochNumbers(String expression, boolean inclusive, boolean lower) throws Exception {
        LocalDate leap = LocalDate.of(2040, 2, 29);
        var expected = lower ? dates(leap, inclusive, null, false) : dates(null, false, leap, inclusive);
        assertEquals(expected, extract(expression));
    }

    @ParameterizedTest @ValueSource(strings = {". = /data/date", "/data/date = ."})
    void dateEqualitySetsBothInclusiveBounds(String expression) throws Exception {
        LocalDate leap = LocalDate.of(2040, 2, 29);
        assertOriginalComponents(dates(leap, true, leap, true), extract(expression));
    }

    @Test void dateBoundsUseTightnessAndExclusiveTies() throws Exception {
        assertEquals(dates(LocalDate.of(2040, 2, 29), false, TODAY, false), extract(
                ". >= date('2040-01-01') and . >= /data/date and . > /data/date"
                + " and . < today() and . <= today() and . <= date('2041-01-01')"));
    }

    @Test void todayAndNowAreEvaluatedAtTheTimeOfEachCall() throws Exception {
        var clock = new SimulatedClock(LocalDateTime.of(2040, 2, 28, 23, 59));
        var s = extractionForm("date", ". >= today() and . <= now()").newSimulatedSession(clock, SEED);
        SimulatedSessionTestSupport.question(s, "group/answer");
        LocalDate day = clock.now().toLocalDate();
        assertEquals(dates(day, true, day, true), ConstraintHints.forCurrentQuestion(s));
        clock.advance(Duration.ofMinutes(2));
        day = clock.now().toLocalDate();
        assertEquals(dates(day, true, day, true), ConstraintHints.forCurrentQuestion(s));
    }

    @ParameterizedTest @ValueSource(strings = {"UTC", "GMT+10:00", "GMT-08:00"})
    @ResourceLock("java.util.TimeZone.default")
    void dateTimeBoundsUseTheJvmDefaultZone(String zone) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            LocalDate day = java.time.OffsetDateTime.parse("2040-03-01T00:30:00+02:00")
                    .atZoneSameInstant(TimeZone.getDefault().toZoneId()).toLocalDate();
            assertOriginalComponents(dates(day, true, day, true), extract(". = /data/stamp"));
        } finally { TimeZone.setDefault(previous); }
    }

    @Test void extractingHintsLeavesNavigationAndAnswerUnchanged() throws Exception {
        var s = session("int", ". >= 10");
        var index = s.getModel().getFormIndex();
        var before = SimulatedSessionTestSupport.snapshot(s);
        assertEquals(ConstraintHints.forCurrentQuestion(s), ConstraintHints.forCurrentQuestion(s));
        assertEquals(index, s.getModel().getFormIndex());
        assertEquals(before, SimulatedSessionTestSupport.snapshot(s));
    }
}
