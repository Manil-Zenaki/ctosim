package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorRealisticSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorRealisticOverrideTest {
    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void explicitOverrideReplacesConfiguredFixedValueForEveryKind(QuestionKind kind) throws Exception {
        SimulationConfig config = config("\"fields\":{\"answer\":{\"value\":\"configured\"}}");
        FieldOverride explicit = field("\"value\":\"explicit\"");
        for (String value : draws(config, question(kind), explicit, 8)) assertEquals("explicit", value);
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void nullOverrideMeansNoFieldOverrideForEveryKind(QuestionKind kind) throws Exception {
        SimulationConfig config = config("\"missingRate\":0,\"fields\":{\"answer\":{\"value\":\"configured\"}}");
        List<String> actual = draws(config, question(kind), null, 30);
        assertTrue(actual.stream().noneMatch("configured"::equals));
        assertEquals(draws(config("\"missingRate\":0"), question(kind), null, 30), actual);
    }

    static Stream<Arguments> delegateCases() {
        return Stream.of(Arguments.of(question(QuestionKind.INTEGER), distribution("normal", 30, 8)),
                Arguments.of(question(QuestionKind.DECIMAL), distribution("lognormal", 30, 8)),
                Arguments.of(question(QuestionKind.TEXT), distribution("normal", 30, 8)),
                Arguments.of(question(QuestionKind.GEOSHAPE), area(8, 9, 38, 39)),
                Arguments.of(text("answer"), "\"texts\":[\"one\",\"two\"]"),
                Arguments.of(question(QuestionKind.SELECT_ONE), "\"weights\":{\"a\":1,\"b\":9}"));
    }

    @ParameterizedTest
    @MethodSource("delegateCases")
    void threeArgumentMethodDelegatesToTheConfiguredOverrideWithIdenticalRandomState(
            Question question, String settings) throws Exception {
        SimulationConfig config = config("\"fields\":{\"answer\":{" + settings + "}}");
        AnswerGenerator three = new AnswerGenerator(config);
        AnswerGenerator four = new AnswerGenerator(config);
        Random r3 = new Random(SEED), r4 = new Random(SEED);
        for (int i = 0; i < 100; i++) {
            assertEquals(three.generate(question, TODAY, r3),
                    four.generate(question, TODAY, r4, config.field(question.name()).orElse(null)));
        }
        assertEquals(r3.nextLong(), r4.nextLong(), "Delegation leaves the same supplied random state");
    }

    @Test
    void explicitOverrideDoesNotMergeAnOmittedRangeFromTheConfiguredField() throws Exception {
        SimulationConfig config = config("\"integerRange\":{\"min\":7,\"max\":7},"
                + "\"fields\":{\"answer\":{\"min\":90,\"max\":90}}");
        assertEquals("7", draws(config, question(QuestionKind.INTEGER), field("\"missingRate\":0"), 1).get(0));
    }

    @Test
    void explicitOverrideDoesNotMergeConfiguredDistribution() throws Exception {
        SimulationConfig config = config("\"fields\":{\"answer\":{" + distribution("normal", 50, 0) + "}}");
        List<String> values = draws(config, question(QuestionKind.INTEGER), field("\"min\":0,\"max\":1"), DRAWS);
        assertEquals(java.util.Set.of("0", "1"), new HashSet<>(values));
        rate(values.stream().filter("0"::equals).count(), DRAWS, 0.5);
    }

    @Test
    void explicitOverrideDoesNotMergeConfiguredAreaAndFallsBackToGlobalArea() throws Exception {
        SimulationConfig config = config(area(8, 8, 38, 38) + ",\"fields\":{\"answer\":{" + area(9, 9, 39, 39) + "}}");
        assertEquals("8.000000 38.000000 0 5", draws(config, question(QuestionKind.GEOPOINT),
                field("\"missingRate\":0"), 1).get(0));
    }

    static Stream<Arguments> explicitOrdinaryParts() {
        return Stream.of(Arguments.of(question(QuestionKind.INTEGER), "\"min\":17,\"max\":17", "17"),
                Arguments.of(question(QuestionKind.DATE), "\"min\":\"2001-02-03\",\"max\":\"2001-02-03\"", "2001-02-03"),
                Arguments.of(text("answer"), "\"texts\":[\"replacement\"]", "replacement"),
                Arguments.of(question(QuestionKind.SELECT_ONE), "\"weights\":{\"b\":1}", "b"));
    }

    @ParameterizedTest
    @MethodSource("explicitOrdinaryParts")
    void explicitOverrideUsesExistingRangeTextAndWeightRules(Question question, String settings, String expected)
            throws Exception {
        SimulationConfig config = config("\"fields\":{\"answer\":{\"value\":\"configured\"}}");
        for (String value : draws(config, question, field(settings), 20)) assertEquals(expected, value);
    }

    static Stream<Arguments> newFeatures() {
        return Stream.of(Arguments.of(question(QuestionKind.INTEGER), distribution("normal", 50, 10)),
                Arguments.of(question(QuestionKind.DECIMAL), distribution("lognormal", 20, 10)),
                Arguments.of(question(QuestionKind.TEXT), distribution("normal", 50, 10)),
                Arguments.of(question(QuestionKind.GEOPOINT), area(8, 9, 38, 40)),
                Arguments.of(question(QuestionKind.GEOTRACE), area(8, 9, 38, 40)),
                Arguments.of(question(QuestionKind.GEOSHAPE), area(8, 9, 38, 40)),
                Arguments.of(text("phone"), "\"missingRate\":0"),
                Arguments.of(text("email"), "\"missingRate\":0"),
                Arguments.of(text("name"), "\"missingRate\":0"),
                Arguments.of(text("comment"), "\"missingRate\":0"),
                Arguments.of(text("city"), "\"missingRate\":0"));
    }

    private static SimulationConfig variedConfig() throws Exception {
        return config("\"textPools\":{\"firstName\":[\"Ida\",\"\u00c9lodie\"],"
                + "\"lastName\":[\"Silva\",\"O'Neil\"],\"place\":[\"Kumasi\",\"Accra\"],"
                + "\"comment\":[\"Recorded\",\"Unknown\"]}");
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    void realisticFeaturesAreDeterministicDespiteUnrelatedPriorCalls(Question question, String settings)
            throws Exception {
        SimulationConfig config = variedConfig();
        FieldOverride override = field(settings);
        AnswerGenerator used = new AnswerGenerator(config);
        draws(used, question, override, new Random(9), 17);
        Random left = new Random(SEED), right = new Random(SEED);
        for (int i = 0; i < 7; i++) { left.nextLong(); right.nextLong(); }
        assertEquals(draws(new AnswerGenerator(config), question, override, left, 100),
                draws(used, question, override, right, 100));
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    void realisticFeaturesProduceFreshDrawsAndDifferentSeedSequences(Question question, String settings)
            throws Exception {
        AnswerGenerator generator = new AnswerGenerator(variedConfig());
        FieldOverride override = field(settings);
        List<String> left = draws(generator, question, override, new Random(101), 100);
        assertTrue(new HashSet<>(left).size() > 1);
        assertNotEquals(left, draws(generator, question, override, new Random(202), 100));
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    void realisticFeaturesAcceptAnyRandomGeneratorImplementation(Question question, String settings)
            throws Exception {
        FieldOverride override = field(settings);
        List<String> first = draws(new AnswerGenerator(variedConfig()), question, override, longOnly(SEED), 100);
        List<String> second = draws(new AnswerGenerator(variedConfig()), question, override, longOnly(SEED), 100);
        assertEquals(first, second);
    }

    private static RandomGenerator longOnly(long seed) {
        return new RandomGenerator() {
            private final Random delegate = new Random(seed);
            @Override public long nextLong() { return delegate.nextLong(); }
        };
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    @ResourceLock("java.util.Locale.default")
    void realisticFeaturesDoNotDependOnTheDefaultLocale(Question question, String settings) throws Exception {
        Locale saved = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.US);
            List<String> expected = draws(variedConfig(), question, field(settings), 100);
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                assertEquals(expected, draws(variedConfig(), question, field(settings), 100));
            }
        } finally {
            Locale.setDefault(saved);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    void blankRuleAndRequiredExemptionStillApplyToRealisticFeatures(Question question, String settings)
            throws Exception {
        // The text-only scenarios already contain missingRate, so replace rather than duplicate that key.
        String feature = settings.equals("\"missingRate\":0") ? "\"missingRate\":1" : settings + ",\"missingRate\":1";
        Question optional = new Question(question.name(), question.kind(), question.choices(), question.appearance(), false);
        for (String value : draws(variedConfig(), optional, field(feature), 10)) assertNull(value);
        for (String value : draws(variedConfig(), question, field(feature), 10)) assertNotNull(value);
    }

    @ParameterizedTest
    @MethodSource("newFeatures")
    void fixedValuesBypassRealisticGeneration(Question question, String ignoredSettings) throws Exception {
        for (String fixed : List.of("verbatim", "", "1.2300")) {
            for (String value : draws(variedConfig(), question, field("\"value\":\"" + fixed + "\""), 10)) {
                assertEquals(fixed, value);
            }
        }
    }

    @Test
    void explicitMissingRateReplacesConfiguredFieldRateAndDoesNotMergeIt() throws Exception {
        SimulationConfig config = config("\"missingRate\":0,\"fields\":{\"answer\":{\"missingRate\":1}}");
        Question optional = new Question("answer", QuestionKind.INTEGER, List.of(), "", false);
        List<String> values = draws(config, optional, field(distribution("normal", 50, 10) + ",\"missingRate\":0.3"), DRAWS);
        rate(values.stream().filter(v -> v == null).count(), DRAWS, 0.3);
        assertNotNull(draws(config, optional, field(distribution("normal", 50, 0)), 1).get(0));
    }

    @Test
    void globalMissingRateAppliesWhenExplicitOverrideOmitsItAndPrecedesFixedValues() throws Exception {
        SimulationConfig config = config("\"missingRate\":1,\"fields\":{\"phone\":{\"missingRate\":0}}");
        Question optional = new Question("phone", QuestionKind.TEXT, List.of(), "", false);
        for (FieldOverride override : java.util.Arrays.asList(null, field("\"value\":\"fixed\""),
                field(distribution("normal", 7, 0)))) {
            assertNull(draws(config, optional, override, 1).get(0));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"expr\":\"'should not appear'\"",
            "\"when\":[{\"if\":\"true()\",\"value\":\"should not appear\"}]",
            "\"when\":[{\"if\":\"${unavailable} = 1\",\"missingRate\":1}]"})
    void generatorIgnoresExpressionsAndRulesInsteadOfEvaluatingThem(String settings) throws Exception {
        SimulationConfig config = config("\"integerRange\":{\"min\":7,\"max\":7}");
        for (String value : draws(config, question(QuestionKind.INTEGER), field(settings), 20)) assertEquals("7", value);
    }

    @Test
    void rulesDoNotReplaceApplicableBaseDistributionOrArea() throws Exception {
        FieldOverride distribution = field(distribution("normal", 7, 0)
                + ",\"when\":[{\"if\":\"true()\",\"value\":\"999\"}]");
        assertEquals("7", draws(config(""), question(QuestionKind.INTEGER), distribution, 1).get(0));
        FieldOverride area = field(area(8, 8, 38, 38) + ",\"when\":[{\"if\":\"true()\"," + area(9, 9, 39, 39) + "}]");
        assertEquals("8.000000 38.000000 0 5", draws(config(""), question(QuestionKind.GEOPOINT), area, 1).get(0));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void explicitFixedOverrideCannotAnswerEmptyOrLabelSelects(QuestionKind kind) throws Exception {
        FieldOverride fixed = field("\"value\":\"a\"");
        assertNull(draws(config(""), new Question("answer", kind, List.of(), "", true), fixed, 1).get(0));
        assertNull(draws(config(""), new Question("answer", kind, List.of("a"), "label", true), fixed, 1).get(0));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT", "BARCODE", "DATE", "ACKNOWLEDGE"})
    void areaSettingsDoNotChangeUnrelatedKinds(QuestionKind kind) throws Exception {
        FieldOverride override = field(area(8, 8, 38, 38));
        Question question = kind == QuestionKind.TEXT ? text("firstname") : question(kind);
        for (String value : draws(config(POOLS), question, override, 30)) {
            assertNotNull(value);
            switch (kind) {
                case INTEGER, DECIMAL -> assertTrue(number(value, kind) >= 0);
                case TEXT -> assertEquals("Ada", value);
                case BARCODE -> assertTrue(value.matches("[0-9]{10}"));
                case DATE -> assertTrue(!java.time.LocalDate.parse(value).isAfter(TODAY));
                case ACKNOWLEDGE -> assertEquals("OK", value);
                default -> fail("Uncovered fixture");
            }
        }
    }

    @Test
    void distributionDoesNotDisplaceOrdinaryTextPlausibleTextOrEightDigitAppearances() throws Exception {
        FieldOverride override = field(distribution("normal", 7, 0));
        assertEquals("Ada", draws(config(POOLS), text("firstname"), override, 1).get(0));
        for (String value : draws(config(""), text("answer"), override, 50)) {
            assertTrue(value.matches("answer [1-9][0-9]{0,3}"));
        }
        for (String appearance : List.of("numbers", "numbers_phone", "xnumbers_decimal", "numbers_decimal_suffix")) {
            Question question = new Question("answer", QuestionKind.TEXT, List.of(), appearance, true);
            for (String value : draws(config(""), question, override, 30)) {
                assertTrue(value.matches(appearance.equals("numbers") || appearance.equals("numbers_phone")
                        ? "[0-9]{8}" : "answer [1-9][0-9]{0,3}"));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"DATE", "TIME", "DATETIME", "SELECT_ONE",
            "SELECT_MULTIPLE", "GEOPOINT", "GEOTRACE", "GEOSHAPE", "BARCODE", "IMAGE", "AUDIO", "VIDEO", "FILE", "ACKNOWLEDGE"})
    void distributionAndPlausibleNameDoNotReplaceOtherKinds(QuestionKind kind) throws Exception {
        Question base = question(kind);
        Question question = new Question("phone", kind, base.choices(), "", true);
        List<String> values = draws(config(""), question, field(distribution("normal", 7, 0)), 30);
        for (String value : values) {
            assertNotNull(value);
            switch (kind) {
                case DATE -> java.time.LocalDate.parse(value);
                case TIME -> java.time.LocalTime.parse(value);
                case DATETIME -> java.time.LocalDateTime.parse(value);
                case SELECT_ONE -> assertTrue(base.choices().contains(value));
                case SELECT_MULTIPLE -> {
                    assertFalse(value.isEmpty());
                    for (String choice : value.split(" ")) assertTrue(base.choices().contains(choice));
                }
                case GEOPOINT, GEOTRACE, GEOSHAPE -> assertTrue(value.contains(" 0 5"));
                case BARCODE -> assertTrue(value.matches("[0-9]{10}"));
                case IMAGE -> assertTrue(value.matches("phone_[1-9][0-9]{0,5}\\.jpg"));
                case AUDIO -> assertTrue(value.matches("phone_[1-9][0-9]{0,5}\\.m4a"));
                case VIDEO -> assertTrue(value.matches("phone_[1-9][0-9]{0,5}\\.mp4"));
                case FILE -> assertTrue(value.matches("phone_[1-9][0-9]{0,5}\\.bin"));
                case ACKNOWLEDGE -> assertEquals("OK", value);
                default -> fail("Uncovered fixture");
            }
        }
    }

    @Test
    void explicitOverloadRetainsNullErrorsForItsThreeRequiredArguments() throws Exception {
        AnswerGenerator generator = new AnswerGenerator(config(""));
        Random random = new Random(SEED);
        assertThrows(NullPointerException.class, () -> generator.generate(null, TODAY, random, null));
        assertThrows(NullPointerException.class, () -> generator.generate(text("name"), null, random, null));
        assertThrows(NullPointerException.class, () -> generator.generate(text("name"), TODAY, null, null));
    }
}
