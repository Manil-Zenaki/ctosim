package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorContractTest {
    @Test
    void constructorRejectsNullConfiguration() {
        assertThrows(NullPointerException.class, () -> new AnswerGenerator(null));
    }

    @Test
    void generateRejectsNullQuestion() throws Exception {
        AnswerGenerator generator = generator("");
        assertThrows(NullPointerException.class, () -> generator.generate(null, TODAY, new Random(SEED)));
    }

    @Test
    void generateRejectsNullTodayEvenForFixedAnswers() throws Exception {
        AnswerGenerator generator = override("\"value\":\"fixed\"");
        assertThrows(NullPointerException.class,
                () -> generator.generate(question(QuestionKind.TEXT), null, new Random(SEED)));
    }

    @Test
    void generateRejectsNullRandomEvenForAcknowledge() throws Exception {
        AnswerGenerator generator = generator("");
        assertThrows(NullPointerException.class,
                () -> generator.generate(question(QuestionKind.ACKNOWLEDGE), TODAY, null));
    }

    @Test
    void questionRejectsNullName() {
        assertThrows(NullPointerException.class,
                () -> new Question(null, QuestionKind.TEXT, List.of(), "", false));
    }

    @Test
    void questionRejectsNullKind() {
        assertThrows(NullPointerException.class, () -> new Question("q", null, List.of(), "", false));
    }

    @Test
    void questionRejectsNullChoices() {
        assertThrows(NullPointerException.class,
                () -> new Question("q", QuestionKind.TEXT, null, "", false));
    }

    @Test
    void questionRejectsNullAppearance() {
        assertThrows(NullPointerException.class,
                () -> new Question("q", QuestionKind.TEXT, List.of(), null, false));
    }

    @Test
    void questionRejectsNullChoiceElement() {
        assertThrows(NullPointerException.class,
                () -> new Question("q", QuestionKind.SELECT_ONE, Arrays.asList("a", null), "", false));
    }

    @Test
    void questionCopiesChoicesInTheirOriginalOrder() {
        List<String> source = new ArrayList<>(List.of("z", "é", "a"));
        Question question = new Question("村", QuestionKind.SELECT_MULTIPLE, source, "minimal", true);
        source.set(0, "changed");
        source.clear();
        assertEquals(List.of("z", "é", "a"), question.choices());
        assertEquals("村", question.name());
        assertEquals(QuestionKind.SELECT_MULTIPLE, question.kind());
        assertEquals("minimal", question.appearance());
        assertTrue(question.required());
    }

    @Test
    void questionChoicesCannotBeAddedRemovedOrReplaced() {
        List<String> choices = select(QuestionKind.SELECT_ONE, List.of("a", "b")).choices();
        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> choices.add("c")),
                () -> assertThrows(UnsupportedOperationException.class, () -> choices.remove(0)),
                () -> assertThrows(UnsupportedOperationException.class, () -> choices.set(0, "c")));
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void missingRateZeroNeverBlanksOptionalQuestions(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, question(kind).choices(), "", false);
        for (String answer : draws(generator("\"missingRate\":0"), question, 100)) assertNotNull(answer);
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void missingRateOneAlwaysBlanksOptionalQuestions(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, question(kind).choices(), "", false);
        for (String answer : draws(generator("\"missingRate\":1"), question, 20)) assertNull(answer);
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void requiredQuestionsIgnoreGlobalAndFieldMissingRates(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("\"missingRate\":1,\"fields\":{\"answer\":{\"missingRate\":1}}");
        for (String answer : draws(generator, question(kind), 20)) assertNotNull(answer);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.23, 1.0})
    void fieldMissingRateReplacesGlobalRate(double rate) throws Exception {
        AnswerGenerator generator = generator("\"missingRate\":" + (rate == 1 ? 0 : 1)
                + ",\"fields\":{\"answer\":{\"missingRate\":" + rate + "}}");
        Question question = new Question("answer", QuestionKind.ACKNOWLEDGE, List.of(), "", false);
        List<String> answers = draws(generator, question, DRAWS);
        assertRate((int) answers.stream().filter(a -> a == null).count(), DRAWS, rate, "field missing rate");
        answers.stream().filter(a -> a != null).forEach(a -> assertEquals("OK", a));
    }

    @Test
    void globalMissingRateAppliesWhenOverrideDoesNotSetIt() throws Exception {
        AnswerGenerator generator = generator("\"missingRate\":0.31,\"fields\":{\"answer\":{\"value\":\"fixed\"}}");
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), "", false);
        List<String> answers = draws(generator, question, DRAWS);
        assertRate((int) answers.stream().filter(a -> a == null).count(), DRAWS, 0.31, "global missing rate");
        answers.stream().filter(a -> a != null).forEach(a -> assertEquals("fixed", a));
    }

    @Test
    void globalMissingRateAlsoAppliesWithoutAnyFieldOverride() throws Exception {
        Question question = new Question("answer", QuestionKind.ACKNOWLEDGE, List.of(), "", false);
        List<String> answers = draws(generator("\"missingRate\":0.42"), question, DRAWS);
        assertRate((int) answers.stream().filter(a -> a == null).count(), DRAWS, 0.42, "global missing rate");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void emptySelectIsBlankEvenWhenRequiredAndFixed(QuestionKind kind) throws Exception {
        for (boolean required : List.of(false, true)) {
            for (double rate : List.of(0.0, 1.0)) {
                Question question = new Question("answer", kind, List.of(), "", required);
                for (String members : List.of("\"missingRate\":" + rate,
                        "\"missingRate\":" + rate + ",\"fields\":{\"answer\":{\"value\":\"fixed\"}}")) {
                    assertNull(generator(members).generate(question, TODAY, new Random(SEED)));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void fixedValueIsReturnedVerbatimForEveryKindAndRetry(QuestionKind kind) throws Exception {
        AnswerGenerator generator = override("\"value\":\"  自由 é 🙂  \"");
        for (String answer : draws(generator, question(kind), 50)) assertEquals("  自由 é 🙂  ", answer);
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void blankRulePrecedesFixedValueForEveryKind(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, question(kind).choices(), "", false);
        assertNull(override("\"value\":\"fixed\",\"missingRate\":1")
                .generate(question, TODAY, new Random(SEED)));
    }

    @Test
    void fixedEmptyStringIsAnAnswerAndNotNull() throws Exception {
        assertEquals("", override("\"value\":\"\"")
                .generate(question(QuestionKind.TEXT), TODAY, new Random(SEED)));
    }

    @Test
    void fieldOverridesMatchExactQuestionName() throws Exception {
        AnswerGenerator generator = generator("\"fields\":{\"Answer\":{\"value\":\"wrong\"},"
                + "\"answers\":{\"value\":\"wrong\"},\"réponse\":{\"value\":\"oui\"}}");
        assertEquals("OK", generator.generate(question(QuestionKind.ACKNOWLEDGE), TODAY, new Random(SEED)));
        assertEquals("oui", generator.generate(new Question("réponse", QuestionKind.TEXT, List.of(), "", true),
                TODAY, new Random(SEED)));
    }

    @ParameterizedTest
    @EnumSource(QuestionKind.class)
    void equalConfigurationArgumentsAndRandomStateGiveIdenticalSequences(QuestionKind kind) throws Exception {
        SimulationConfig first = mixedOverrideFixture();
        SimulationConfig second = mixedOverrideFixture();
        Random left = new Random(SEED);
        Random right = new Random(SEED);
        for (int i = 0; i < 17; i++) { left.nextLong(); right.nextLong(); }
        Question a = new Question("answer", kind, question(kind).choices(), "", false);
        Question b = new Question("answer", kind, new ArrayList<>(a.choices()), "", false);
        assertEquals(draws(new AnswerGenerator(first), a, TODAY, left, 200),
                draws(new AnswerGenerator(second), b, TODAY, right, 200));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"TEXT", "INTEGER", "DECIMAL", "DATE", "DATETIME",
            "SELECT_ONE", "SELECT_MULTIPLE"})
    void randomizedFieldOverridesAlsoUseOnlyTheSuppliedRandomState(QuestionKind kind) throws Exception {
        String field = switch (kind) {
            case TEXT -> "\"texts\":[\"first\",\"second\",\"third\"]";
            case INTEGER, DECIMAL -> "\"min\":-2.4,\"max\":4.7";
            case DATE, DATETIME -> "\"min\":\"2000-01-01\",\"max\":\"2000-01-31\"";
            case SELECT_ONE, SELECT_MULTIPLE -> "\"weights\":{\"z\":0.25,\"a\":0.75,\"m\":2}";
            default -> throw new AssertionError("Unexpected kind " + kind);
        };
        assertEquals(draws(override(field), question(kind), 200), draws(override(field), question(kind), 200));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ACKNOWLEDGE")
    void retryDrawsCanProduceFreshAnswers(QuestionKind kind) throws Exception {
        assertTrue(new HashSet<>(draws(generator(""), question(kind), 150)).size() > 1);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ACKNOWLEDGE")
    void differentRandomSeedsCanChangeAnswers(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("");
        assertNotEquals(draws(generator, question(kind), TODAY, new Random(101), 100),
                draws(generator, question(kind), TODAY, new Random(202), 100));
    }

    @Test
    void acceptsRandomGeneratorImplementationsOtherThanRandom() throws Exception {
        RandomGenerator left = new LongOnlyRandom(SEED);
        RandomGenerator right = new LongOnlyRandom(SEED);
        AnswerGenerator a = generator("");
        AnswerGenerator b = generator("");
        for (QuestionKind kind : QuestionKind.values()) {
            assertEquals(draws(a, question(kind), TODAY, left, 30), draws(b, question(kind), TODAY, right, 30));
        }
    }

    @Test
    void unrelatedPriorCallsDoNotCreateHiddenGeneratorState() throws Exception {
        AnswerGenerator used = generator("");
        for (QuestionKind kind : QuestionKind.values()) draws(used, question(kind), 7);
        AnswerGenerator fresh = generator("");
        for (QuestionKind kind : QuestionKind.values()) {
            assertEquals(draws(fresh, question(kind), 50), draws(used, question(kind), 50));
        }
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void outputsDoNotDependOnDefaultLocale() throws Exception {
        Locale saved = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.US);
            List<List<String>> expected = new ArrayList<>();
            AnswerGenerator first = generator("");
            for (QuestionKind kind : QuestionKind.values()) expected.add(draws(first, question(kind), 50));
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                AnswerGenerator second = generator("");
                for (QuestionKind kind : QuestionKind.values()) {
                    assertEquals(expected.get(kind.ordinal()), draws(second, question(kind), 50), kind.name());
                }
            }
        } finally {
            Locale.setDefault(saved);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    private static final class LongOnlyRandom implements RandomGenerator {
        private final Random delegate;
        LongOnlyRandom(long seed) { delegate = new Random(seed); }
        @Override public long nextLong() { return delegate.nextLong(); }
    }
}
