package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** General rules that Amendment 1 explicitly preserves for its answerable appearances. */
class AnswerGeneratorAppearanceContractTest {
    private static Question appeared(QuestionKind kind, boolean required) {
        return new Question("answer", kind, List.of(),
                kind == QuestionKind.TEXT ? "numbers_decimal" : "month-year", required);
    }

    static Stream<Arguments> blankCases() {
        return Stream.of(QuestionKind.TEXT, QuestionKind.DATE).flatMap(kind ->
                Stream.of(false, true).flatMap(required ->
                        Stream.of(
                                Arguments.of(kind, required, "\"missingRate\":0", false),
                                Arguments.of(kind, required, "\"missingRate\":1", true),
                                Arguments.of(kind, required, "\"missingRate\":1,\"fields\":{\"answer\":{\"missingRate\":0}}", false),
                                Arguments.of(kind, required, "\"missingRate\":0,\"fields\":{\"answer\":{\"missingRate\":1}}", true))));
    }

    @ParameterizedTest
    @MethodSource("blankCases")
    void missingRateBoundariesFieldPrecedenceAndRequiredExemptionArePreserved(
            QuestionKind kind, boolean required, String config, boolean missing) throws Exception {
        for (String answer : draws(generator(config), appeared(kind, required), 30)) {
            if (missing && !required) assertNull(answer);
            else assertNotNull(answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void fieldMissingRateKeepsItsProbabilityWithNewAppearance(QuestionKind kind) throws Exception {
        List<String> answers = draws(generator("\"missingRate\":0.9,"
                + "\"fields\":{\"answer\":{\"missingRate\":0.3}}"), appeared(kind, false), DRAWS);
        assertRate(counts(answers).getOrDefault(null, 0), DRAWS, 0.3, "field missing rate");
    }

    static Stream<Arguments> fixedCases() {
        return Stream.of(QuestionKind.TEXT, QuestionKind.DATE).flatMap(kind ->
                Stream.of("verbatim café", "", "1.2300", "2025-03-17")
                        .map(value -> Arguments.of(kind, value)));
    }

    @ParameterizedTest
    @MethodSource("fixedCases")
    void fixedValueBypassesDecimalFormattingAndMonthNormalization(QuestionKind kind, String fixed) throws Exception {
        for (String answer : draws(override("\"value\":\"" + fixed + "\",\"missingRate\":1"),
                appeared(kind, true), 30)) assertEquals(fixed, answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void missingRuleStillPrecedesFixedValue(QuestionKind kind) throws Exception {
        for (String answer : draws(override("\"value\":\"fixed\",\"missingRate\":1"),
                appeared(kind, false), 30)) assertNull(answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void equivalentRandomStatesGiveIdenticalSequencesAfterPriorCalls(QuestionKind kind) throws Exception {
        AnswerGenerator used = generator("\"missingRate\":0.3");
        draws(used, appeared(kind, true), 50);
        Random left = new Random(SEED);
        Random right = new Random(SEED);
        for (int i = 0; i < 17; i++) { left.nextLong(); right.nextLong(); }
        assertEquals(draws(generator("\"missingRate\":0.3"), appeared(kind, false), TODAY, left, 300),
                draws(used, appeared(kind, false), TODAY, right, 300));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void retriesCanReturnFreshAnswers(QuestionKind kind) throws Exception {
        assertTrue(new HashSet<>(draws(generator(""), appeared(kind, true), 200)).size() > 1);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void differentSuppliedSeedsCanChangeAnswerSequences(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("");
        assertNotEquals(draws(generator, appeared(kind, true), TODAY, new Random(101), 100),
                draws(generator, appeared(kind, true), TODAY, new Random(202), 100));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    void acceptsRandomGeneratorImplementationsOtherThanRandom(QuestionKind kind) throws Exception {
        assertEquals(draws(generator(""), appeared(kind, true), TODAY, longOnlyRandom(SEED), 100),
                draws(generator(""), appeared(kind, true), TODAY, longOnlyRandom(SEED), 100));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "DATE"})
    @ResourceLock("java.util.Locale.default")
    void newAppearancesDoNotDependOnDefaultLocale(QuestionKind kind) throws Exception {
        Locale saved = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.US);
            List<String> expected = draws(generator(""), appeared(kind, true), 200);
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                assertEquals(expected, draws(generator(""), appeared(kind, true), 200));
            }
        } finally {
            Locale.setDefault(saved);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    private static RandomGenerator longOnlyRandom(long seed) {
        return new RandomGenerator() {
            private final Random delegate = new Random(seed);
            @Override public long nextLong() { return delegate.nextLong(); }
        };
    }
}
