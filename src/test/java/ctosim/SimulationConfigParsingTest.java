package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigParsingTest {
    @Test
    void defaultsMatchEverySpecifiedSetting() {
        SimulationConfig config = SimulationConfig.defaults();
        assertAll(
                () -> assertEquals(1L, config.seed()),
                () -> assertEquals(100, config.submissions()),
                () -> assertEquals(LocalDateTime.of(2026, 1, 5, 8, 0), config.start()),
                () -> assertEquals(30, config.fieldworkDays()),
                () -> assertEquals(0.05, config.missingRate()),
                () -> assertEquals(0.02, config.specialRate()),
                () -> assertEquals(List.of("-99", "-98", "-97", "-96", "-88", "-77",
                        "-999", "-888", "-777", "97", "98", "99", "999"), config.specialValues()),
                () -> assertEquals(1, config.repeatMin()),
                () -> assertEquals(3, config.repeatMax()),
                () -> assertEquals(new NumericRange(0, 100), config.integerRange()),
                () -> assertEquals(new NumericRange(0, 1000), config.decimalRange()),
                () -> assertTrue(config.overriddenFields().isEmpty()),
                () -> assertTrue(config.field("age").isEmpty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", " \r\n\t{ } \r\n", "{\"fields\":{}}"})
    void emptyObjectsAndSurroundingJsonWhitespaceGiveDefaults(String json) throws Exception {
        assertSameSettings(SimulationConfig.defaults(), parse(json));
    }

    @Test
    void oneSettingDoesNotResetOrChangeOtherDefaults() throws Exception {
        SimulationConfig config = parse("{\"seed\":77}");
        assertEquals(77, config.seed());
        assertEquals(100, config.submissions());
        assertSameSettingsExceptSeedAndSubmissions(SimulationConfig.defaults(), config);
    }

    @Test
    void completeFixturePreservesAllTopLevelValues() throws Exception {
        SimulationConfig config = complete();
        assertAll(
                () -> assertEquals(-9007199254740993L, config.seed()),
                () -> assertEquals(321, config.submissions()),
                () -> assertEquals(LocalDateTime.of(2024, 2, 29, 23, 59, 58), config.start()),
                () -> assertEquals(365, config.fieldworkDays()),
                () -> assertEquals(0.125, config.missingRate()),
                () -> assertEquals(0.75, config.specialRate()),
                () -> assertEquals(List.of("99", "-99", "refusé"), config.specialValues()),
                () -> assertEquals(0, config.repeatMin()),
                () -> assertEquals(17, config.repeatMax()),
                () -> assertEquals(new NumericRange(-12.5, 45.75), config.integerRange()),
                () -> assertEquals(new NumericRange(-100.25, 9999.5), config.decimalRange()));
    }

    static Stream<Arguments> exactSeeds() {
        return Stream.of(
                Arguments.of("0", 0L), Arguments.of("-1", -1L),
                Arguments.of("42.0", 42L), Arguments.of("4.2e1", 42L),
                Arguments.of("9007199254740993", 9007199254740993L),
                Arguments.of("-9007199254740993", -9007199254740993L),
                Arguments.of("9223372036854775807", Long.MAX_VALUE),
                Arguments.of("-9223372036854775808", Long.MIN_VALUE),
                Arguments.of("9223372036854775807.0", Long.MAX_VALUE),
                Arguments.of("-9223372036854775808.0", Long.MIN_VALUE));
    }

    @ParameterizedTest(name = "seed {0} is preserved as long {1}")
    @MethodSource("exactSeeds")
    void seedAcceptsIntegralJsonNumbersWithoutDoubleRounding(String jsonNumber, long expected) throws Exception {
        assertEquals(expected, parse("{\"seed\":" + jsonNumber + "}").seed());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1000000,1000000", "42.0,42", "1e3,1000"})
    void submissionsAcceptsInclusiveBoundsAndIntegralNumberForms(String number, int expected) throws Exception {
        assertEquals(expected, parse("{\"submissions\":" + number + "}").submissions());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "3650,3650", "42.0,42", "3e2,300"})
    void fieldworkDaysAcceptsInclusiveBoundsAndIntegralNumberForms(String number, int expected) throws Exception {
        assertEquals(expected, parse("{\"fieldworkDays\":" + number + "}").fieldworkDays());
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,1000", "1000,1000", "2.0,3.0", "1e1,2e1"})
    void repeatBoundsAcceptZeroEqualityUpperLimitAndIntegralNumbers(String min, String max) throws Exception {
        SimulationConfig config = parse("{\"repeats\":{\"min\":" + min + ",\"max\":" + max + "}}");
        assertEquals((int) Double.parseDouble(min), config.repeatMin());
        assertEquals((int) Double.parseDouble(max), config.repeatMax());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, 0.125})
    void ratesAcceptBothEndpointsAndInteriorValues(double rate) throws Exception {
        SimulationConfig config = parse("{\"missingRate\":" + rate + ",\"specialRate\":" + rate
                + ",\"fields\":{\"answer\":{\"missingRate\":" + rate + "}}}");
        assertEquals(rate, config.missingRate());
        assertEquals(rate, config.specialRate());
        assertEquals(OptionalDouble.of(rate), field(config, "answer").missingRate());
    }

    @ParameterizedTest
    @CsvSource({"2026-03-02T08:15,2026-03-02T08:15:00",
            "2024-02-29T23:59:59,2024-02-29T23:59:59", "2026-01-01T00:00,2026-01-01T00:00:00"})
    void startAcceptsMinuteAndSecondPrecisionAndLeapDays(String input, String expected) throws Exception {
        assertEquals(LocalDateTime.parse(expected), parse("{\"start\":" + quote(input) + "}").start());
    }

    @ParameterizedTest
    @ValueSource(strings = {"integerRange", "decimalRange"})
    void defaultNumericRangesAcceptNegativeFractionalAndEqualBounds(String key) throws Exception {
        for (String[] bounds : List.of(new String[]{"-2.75", "-1.5"}, new String[]{"4.5", "4.5"},
                new String[]{"-1e6", "2e6"})) {
            SimulationConfig config = parse("{" + quote(key) + ":{\"min\":" + bounds[0]
                    + ",\"max\":" + bounds[1] + "}}");
            NumericRange actual = key.equals("integerRange") ? config.integerRange() : config.decimalRange();
            assertEquals(new NumericRange(Double.parseDouble(bounds[0]), Double.parseDouble(bounds[1])), actual);
        }
    }

    @Test
    void specialValuesCanBeEmpty() throws Exception {
        assertTrue(parse("{\"specialValues\":[]}").specialValues().isEmpty());
    }

    @Test
    void specialValuesAreExactStringsInFileOrder() throws Exception {
        assertEquals(List.of("99", "099", "A", "a", "  ", "é", "東京", "😀"),
                parse("{\"specialValues\":[\"99\",\"099\",\"A\",\"a\",\"  \",\"é\",\"東京\",\"😀\"]}").specialValues());
    }

    @Test
    void overriddenFieldNamesUseNaturalCaseSensitiveOrderAndExactLookup() throws Exception {
        SimulationConfig config = complete();
        assertEquals(List.of("Consent", "age", "consent", "dob", "phone", "region", "village_name",
                "z_fixed", "école"), new ArrayList<>(config.overriddenFields()));
        assertEquals(Optional.of("1"), field(config, "Consent").value());
        assertEquals(Optional.of("0"), field(config, "consent").value());
        for (String absent : List.of("CONSENT", " consent", "consent ", "unknown", "")) {
            assertTrue(config.field(absent).isEmpty(), absent);
        }
    }

    @Test
    void arbitraryNonemptyNamesAreAcceptedWithoutConsultingAForm() throws Exception {
        SimulationConfig config = parse("{\"fields\":{\"not_in_any_form\":{\"value\":\"x\"},"
                + "\"école\":{\"value\":\"y\"},\"a b\":{\"value\":\"z\"}}}");
        assertEquals(3, config.overriddenFields().size());
        assertEquals(Optional.of("z"), field(config, "a b").value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "001", " café 東京 😀 ", "line one\nline two\t\"quoted\"\\end"})
    void fixedStringsArePreservedExactly(String value) throws Exception {
        assertEquals(Optional.of(value), override("\"value\":" + quote(value)).value());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "1.0,1", "-12.000,-12", "2.5,2.5", "-0.125,-0.125",
            "2.5000,2.5", "1e3,1000", "2.5e-4,2.5E-4"})
    void numericFixedValuesUseWholeNumberOrShortestDoubleText(String input, String expected) throws Exception {
        assertEquals(Optional.of(expected), override("\"value\":" + input).value());
    }

    @Test
    void unspecifiedOverrideMembersUseEmptyOptionalsAndCollections() throws Exception {
        FieldOverride fixed = override("\"value\":\"yes\"");
        assertAll(
                () -> assertTrue(fixed.range().isEmpty()),
                () -> assertTrue(fixed.weights().isEmpty()),
                () -> assertTrue(fixed.texts().isEmpty()),
                () -> assertTrue(fixed.missingRate().isEmpty()));
        FieldOverride missing = override("\"missingRate\":0.5");
        assertAll(
                () -> assertTrue(missing.value().isEmpty()),
                () -> assertTrue(missing.range().isEmpty()),
                () -> assertTrue(missing.weights().isEmpty()),
                () -> assertTrue(missing.texts().isEmpty()));
    }

    @Test
    void numericFieldRangeKeepsFractionalBoundsAndDoesNotCreateAFixedValue() throws Exception {
        FieldOverride age = field(complete(), "age");
        assertEquals(Optional.of(new NumericRange(-15.5, 64.25)), age.range());
        assertTrue(age.value().isEmpty());
    }

    @Test
    void numericFieldRangeAcceptsEqualEndpoints() throws Exception {
        assertEquals(Optional.of(new NumericRange(-3.25, -3.25)),
                override("\"min\":-3.25,\"max\":-3.25").range());
    }

    @Test
    void dateFieldRangeProducesLocalDates() throws Exception {
        assertEquals(Optional.of(new DateRange(LocalDate.of(1950, 1, 1), LocalDate.of(2008, 12, 31))),
                field(complete(), "dob").range());
    }

    @Test
    void dateFieldRangeAcceptsEqualEndpointsAndLeapDay() throws Exception {
        LocalDate date = LocalDate.of(2024, 2, 29);
        assertEquals(Optional.of(new DateRange(date, date)),
                override("\"min\":\"2024-02-29\",\"max\":\"2024-02-29\"").range());
    }

    @Test
    void weightsPreserveChoiceOrderZerosAndUnnormalizedValues() throws Exception {
        Map<String, Double> weights = field(complete(), "region").weights();
        assertEquals(Map.of("z", 0.0, "2", 3.5, "A", 5.0), weights);
        assertEquals(List.of("z", "2", "A"), new ArrayList<>(weights.keySet()));
    }

    @Test
    void onePositiveWeightIsSufficientAndChoiceCodesNeedNotBeKnown() throws Exception {
        assertEquals(Map.of("unknown-choice-é", 0.000001),
                override("\"weights\":{\"unknown-choice-é\":1e-6}").weights());
    }

    @Test
    void textsKeepOrderDuplicatesEmptyStringsWhitespaceAndUnicode() throws Exception {
        assertEquals(List.of("Mojo", "Adama", "Mojo", "Bishoftu"), field(complete(), "village_name").texts());
        assertEquals(List.of("élève", "東京", "😀", "", "  "), field(complete(), "école").texts());
        assertEquals(List.of(""), override("\"texts\":[\"\"]").texts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"value\":\"x\"", "\"min\":1,\"max\":2",
            "\"min\":\"2024-01-01\",\"max\":\"2024-02-01\"",
            "\"weights\":{\"x\":1}", "\"texts\":[\"x\"]"})
    void missingRateCanAccompanyEveryOverrideKind(String body) throws Exception {
        FieldOverride result = override(body + ",\"missingRate\":0.625");
        assertEquals(OptionalDouble.of(0.625), result.missingRate());
        FieldOverride original = override(body);
        assertEquals(original.value(), result.value());
        assertEquals(original.range(), result.range());
        assertEquals(original.weights(), result.weights());
        assertEquals(original.texts(), result.texts());
    }

    @Test
    void nonValueOverrideKindsCanCoexist() throws Exception {
        FieldOverride result = override("\"min\":1,\"max\":2,\"weights\":{\"one\":3},"
                + "\"texts\":[\"text\"],\"missingRate\":0.5");
        assertEquals(Optional.of(new NumericRange(1, 2)), result.range());
        assertEquals(Map.of("one", 3.0), result.weights());
        assertEquals(List.of("text"), result.texts());
        assertEquals(OptionalDouble.of(0.5), result.missingRate());
    }

    @Test
    void repeatedParsingProducesTheSameObservableConfiguration() throws Exception {
        assertSameSettings(complete(), complete());
    }

    @Test
    void objectPropertyOrderDoesNotChangeSettings() throws Exception {
        assertSameSettings(parse("{\"seed\":7,\"repeats\":{\"min\":0,\"max\":4},"
                        + "\"fields\":{\"age\":{\"min\":1,\"max\":8,\"missingRate\":0}}}"),
                parse("{\"fields\":{\"age\":{\"missingRate\":0,\"max\":8,\"min\":1}},"
                        + "\"repeats\":{\"max\":4,\"min\":0},\"seed\":7}"));
    }
}
