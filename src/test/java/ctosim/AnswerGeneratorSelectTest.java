package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorSelectTest {
    private static final List<String> ORDINARY = List.of("z", "a", "m", "b");

    @Test
    void selectOneIsUniformWithoutWeights() throws Exception {
        assertUniform(draws(generator("\"specialRate\":0"),
                select(QuestionKind.SELECT_ONE, ORDINARY), DRAWS), ORDINARY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"z\":1,\"a\":3", "\"z\":0.125,\"a\":0.375"})
    void selectOneUsesRelativeWeightsAndIgnoresAbsentValues(String weights) throws Exception {
        AnswerGenerator generator = override("\"weights\":{" + weights + ",\"m\":0,\"absent\":1000000}");
        Map<String, Integer> counts = counts(draws(generator, select(QuestionKind.SELECT_ONE, ORDINARY), DRAWS));
        assertEquals(Set.of("z", "a"), counts.keySet(), "Zero and unspecified weights must not be selected");
        assertRate(counts.get("z"), DRAWS, 0.25, "weight 1 of 4");
        assertRate(counts.get("a"), DRAWS, 0.75, "weight 3 of 4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"absent\":10", "\"z\":0,\"a\":0,\"m\":0,\"b\":0,\"absent\":10"})
    void selectOneFallsBackToUniformWhenAllOfferedWeightsAreZero(String weights) throws Exception {
        assertUniform(draws(override("\"weights\":{" + weights + "}"),
                select(QuestionKind.SELECT_ONE, ORDINARY), DRAWS), ORDINARY);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void onlyOfferedChoicesCanBeReturnedAfterFiltering(QuestionKind kind) throws Exception {
        AnswerGenerator generator = override("\"weights\":{\"filtered_out\":99999,\"kept\":1}");
        for (String answer : draws(generator, select(kind, List.of("kept")), 100)) assertEquals("kept", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void specialRateOneReturnsOnlyOneUniformSpecialIgnoringWeights(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("\"specialRate\":1,\"fields\":{\"answer\":{"
                + "\"weights\":{\"99\":999999,\"-99\":0,\"z\":1}}}");
        assertUniform(draws(generator, question(kind), DRAWS), List.of("99", "-99"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void specialRateZeroExcludesSpecialChoicesWhenOrdinaryChoicesExist(QuestionKind kind) throws Exception {
        for (String answer : draws(generator("\"specialRate\":0"), question(kind), 1000)) {
            for (String value : answer.split(" ")) assertTrue(ORDINARY.contains(value), answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void intermediateSpecialRateProducesUniformSingleSpecials(QuestionKind kind) throws Exception {
        List<String> answers = draws(generator("\"specialRate\":0.3"), question(kind), DRAWS);
        Map<String, Integer> counts = counts(answers);
        assertRate(counts.getOrDefault("99", 0), DRAWS, 0.15, "first special");
        assertRate(counts.getOrDefault("-99", 0), DRAWS, 0.15, "second special");
        List<String> ordinary = new ArrayList<>();
        for (String answer : answers) {
            if (!List.of("99", "-99").contains(answer)) {
                for (String value : answer.split(" ")) assertTrue(ORDINARY.contains(value), answer);
                ordinary.add(answer);
            }
        }
        if (kind == QuestionKind.SELECT_ONE) assertUniform(ordinary, ORDINARY);
        else assertSizesUniform(ordinary, 3);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void allSpecialChoicesAreUniformEvenAtSpecialRateZero(QuestionKind kind) throws Exception {
        assertUniform(draws(generator("\"specialRate\":0,\"fields\":{\"answer\":{\"weights\":{\"99\":10}}}"),
                select(kind, List.of("-99", "99", "98")), DRAWS), List.of("-99", "99", "98"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void aSingleSpecialChoiceIsReturnedWhenItIsTheOnlyChoice(QuestionKind kind) throws Exception {
        for (String answer : draws(generator("\"specialRate\":0"), select(kind, List.of("99")), 100)) {
            assertEquals("99", answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void noSpecialChoicesStillProducesOrdinaryAnswersAtSpecialRateOne(QuestionKind kind) throws Exception {
        List<String> values = draws(generator("\"specialRate\":1"), select(kind, ORDINARY), DRAWS);
        if (kind == QuestionKind.SELECT_ONE) assertUniform(values, ORDINARY);
        else {
            for (String value : values) assertOrderedSubset(value, ORDINARY);
            assertSizesUniform(values, 3);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void configuredSpecialValuesReplaceDefaultCodesAndMatchExactly(QuestionKind kind) throws Exception {
        AnswerGenerator generator = generator("\"specialRate\":1,\"specialValues\":[\"REFUSÉ\"]");
        for (String answer : draws(generator, select(kind, List.of("99", "refusé", "REFUSÉ")), 100)) {
            assertEquals("REFUSÉ", answer);
        }
        assertUniform(draws(generator("\"specialRate\":1,\"specialValues\":[\"REFUSÉ\"]"),
                select(QuestionKind.SELECT_ONE, List.of("99", "refusé")), DRAWS), List.of("99", "refusé"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void emptySpecialValuesMakesDefaultCodesOrdinary(QuestionKind kind) throws Exception {
        List<String> values = draws(generator("\"specialRate\":1,\"specialValues\":[]"),
                select(kind, List.of("99", "-99")), DRAWS);
        if (kind == QuestionKind.SELECT_ONE) assertUniform(values, List.of("99", "-99"));
        else assertSizesUniform(values, 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 20})
    void multipleSelectSizeIsUniformFromOneThroughCappedChoiceCount(int choiceCount) throws Exception {
        List<String> choices = new ArrayList<>();
        for (int i = choiceCount; i > 0; i--) choices.add("value_" + i);
        List<String> answers = draws(generator(""), select(QuestionKind.SELECT_MULTIPLE, choices), DRAWS);
        for (String answer : answers) assertOrderedSubset(answer, choices);
        assertSizesUniform(answers, Math.min(3, choiceCount));
    }

    @Test
    void multipleSelectUniformSamplingIsUniformOverEverySubsetOfEachSize() throws Exception {
        List<String> choices = List.of("z", "é", "a", "村");
        List<String> answers = draws(generator(""), select(QuestionKind.SELECT_MULTIPLE, choices), 60_000);
        Map<String, Integer> counts = counts(answers);
        Set<String> expected = new HashSet<>();
        for (int mask = 1; mask < (1 << choices.size()) - 1; mask++) {
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < choices.size(); i++) if ((mask & (1 << i)) != 0) subset.add(choices.get(i));
            String answer = String.join(" ", subset);
            expected.add(answer);
            int combinations = subset.size() == 2 ? 6 : 4;
            assertRate(counts.getOrDefault(answer, 0), answers.size(), 1.0 / (3 * combinations), answer);
        }
        assertEquals(expected, counts.keySet());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"z\":1,\"a\":2,\"m\":7", "\"z\":0.1,\"a\":0.2,\"m\":0.7"})
    void weightedMultipleSelectUsesWeightsWithoutReplacement(String configuredWeights) throws Exception {
        List<String> choices = List.of("z", "a", "m");
        double[] weights = {1, 2, 7};
        List<String> answers = draws(override("\"weights\":{" + configuredWeights + ",\"absent\":1000000}"),
                select(QuestionKind.SELECT_MULTIPLE, choices), 90_000);
        Map<String, Integer> counts = counts(answers);
        for (String answer : answers) assertOrderedSubset(answer, choices);
        assertSizesUniform(answers, 3);
        for (int i = 0; i < 3; i++) {
            assertRate(counts.getOrDefault(choices.get(i), 0), answers.size(), weights[i] / 30,
                    "singleton " + choices.get(i));
            for (int j = i + 1; j < 3; j++) {
                // Either draw order yields this displayed two-element subset.
                double probability = (weights[i] / 10 * weights[j] / (10 - weights[i])
                        + weights[j] / 10 * weights[i] / (10 - weights[j])) / 3;
                String pair = choices.get(i) + " " + choices.get(j);
                assertRate(counts.getOrDefault(pair, 0), answers.size(), probability, pair);
            }
        }
        assertRate(counts.getOrDefault("z a m", 0), answers.size(), 1.0 / 3, "all choices");
    }

    @Test
    void multipleSelectDrawsZeroWeightsOnlyAfterPositiveWeightsAreExhausted() throws Exception {
        List<String> choices = List.of("zero", "positive", "unspecified");
        List<String> answers = draws(override("\"weights\":{\"positive\":7,\"zero\":0,\"absent\":1000}"),
                select(QuestionKind.SELECT_MULTIPLE, choices), DRAWS);
        for (String answer : answers) {
            assertOrderedSubset(answer, choices);
            assertTrue(Arrays.asList(answer.split(" ")).contains("positive"), answer);
        }
        assertSizesUniform(answers, 3);
        assertTrue(answers.contains("zero positive unspecified"), "Must fill requested size even after weights run out");
    }

    @Test
    void multipleSelectWithEnoughPositiveChoicesNeverDrawsZeroWeight() throws Exception {
        List<String> choices = List.of("zero", "z", "a", "m", "unspecified");
        for (String answer : draws(override("\"weights\":{\"z\":1,\"a\":1,\"m\":1,\"zero\":0}"),
                select(QuestionKind.SELECT_MULTIPLE, choices), 3000)) {
            assertOrderedSubset(answer, List.of("z", "a", "m"));
        }
    }

    @Test
    void allZeroOfferedWeightsStillYieldEveryAllowedMultipleSelectSize() throws Exception {
        List<String> answers = draws(override("\"weights\":{\"absent\":1,\"z\":0}"),
                select(QuestionKind.SELECT_MULTIPLE, ORDINARY), DRAWS);
        for (String answer : answers) assertOrderedSubset(answer, ORDINARY);
        assertSizesUniform(answers, 3);
        // The spec does not state the distribution within the all-zero fallback.
    }

    private static void assertOrderedSubset(String answer, List<String> choices) {
        assertNotNull(answer);
        List<String> selected = Arrays.asList(answer.split(" ", -1));
        assertTrue(selected.size() >= 1 && selected.size() <= Math.min(3, choices.size()), answer);
        assertEquals(selected.size(), new HashSet<>(selected).size(), "Selections must be distinct: " + answer);
        assertTrue(choices.containsAll(selected), answer);
        List<String> ordered = choices.stream().filter(selected::contains).toList();
        assertEquals(String.join(" ", ordered), answer, "Use exactly one space and original display order");
    }

    private static void assertSizesUniform(List<String> answers, int maximum) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (String answer : answers) counts.merge(answer.split(" ", -1).length, 1, Integer::sum);
        assertTrue(counts.keySet().stream().allMatch(k -> k >= 1 && k <= maximum));
        for (int k = 1; k <= maximum; k++) {
            assertRate(counts.getOrDefault(k, 0), answers.size(), 1.0 / maximum, "selection size " + k);
        }
    }
}
