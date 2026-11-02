package ctosim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static ctosim.AnswerGeneratorOtherChoiceTest.orderedValues;
import static org.junit.jupiter.api.Assertions.*;

/** Statistical oracles use category probabilities and small, enumerated subsets. */
class AnswerGeneratorOtherDistributionTest {
    private static final int N = 60_000;
    private static final String CODES = "\"otherCodes\":[\"o2\",\"o1\",\"not_offered\"]";

    @Test
    void selectOneOtherRateIsConditionalOnNotDrawingSpecial() throws Exception {
        List<String> choices = List.of("o1", "b", "s2", "a", "o2", "s1");
        List<String> answers = draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":0.25,"
                + "\"specialValues\":[\"s1\",\"s2\"],\"fields\":{\"answer\":{\"weights\":{\"a\":1,\"b\":3,\"absent\":1000000}}}"),
                select(QuestionKind.SELECT_ONE, choices), N);
        assertProbabilities(answers, Map.of("s1", 0.125, "s2", 0.125,
                "o1", 0.15, "o2", 0.15, "a", 0.1125, "b", 0.3375));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void defaultOtherRateIsThreePercentAmongOrdinaryAnswers(QuestionKind kind) throws Exception {
        List<String> answers = draws(generator(CODES + ",\"specialRate\":0"),
                select(kind, List.of("o1", "a", "o2")), N);
        if (kind == QuestionKind.SELECT_ONE) {
            assertProbabilities(answers, Map.of("a", 0.97, "o1", 0.015, "o2", 0.015));
        } else {
            assertProbabilities(answers, Map.of("a", 0.97, "o1 a", 0.015, "a o2", 0.015));
        }
    }

    @Test
    void selectOneRetainsUniformOrdinaryDrawWhenNoWeightsApply() throws Exception {
        assertProbabilities(draws(generator(CODES + ",\"otherRate\":0.2,\"specialRate\":0"),
                        select(QuestionKind.SELECT_ONE, List.of("b", "o1", "a", "o2")), N),
                Map.of("a", 0.4, "b", 0.4, "o1", 0.1, "o2", 0.1));
    }

    @Test
    void selectOneFallsBackToUniformOrdinaryWeightsWithoutChangingOtherProbability() throws Exception {
        assertProbabilities(draws(generator(CODES + ",\"otherRate\":0.2,\"specialRate\":0,"
                        + "\"fields\":{\"answer\":{\"weights\":{\"a\":0,\"absent\":1000}}}"),
                        select(QuestionKind.SELECT_ONE, List.of("b", "o1", "a", "o2")), N),
                Map.of("a", 0.4, "b", 0.4, "o1", 0.1, "o2", 0.1));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void optionalBlankRatePrecedesBothSpecialAndOtherBranches(QuestionKind kind) throws Exception {
        Question q = new Question("answer", kind, List.of("o1", "a", "s"), "", false);
        List<String> answers = draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":0.25,"
                + "\"missingRate\":0.8,\"specialValues\":[\"s\"],\"fields\":{\"answer\":{\"missingRate\":0.2}}"), q, N);
        Map<String, Double> expected = new HashMap<>();
        expected.put(null, 0.2);
        expected.put("s", 0.2);
        expected.put("a", 0.36);
        expected.put(kind == QuestionKind.SELECT_ONE ? "o1" : "o1 a", 0.24);
        assertProbabilities(answers, expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void multipleSelectKeepsEveryOrdinarySubsetProbabilityAndAddsOtherIndependently(int ordinaryCount) throws Exception {
        List<String> ordinary = List.of("z", "a", "m", "b").subList(0, ordinaryCount);
        List<String> offered = new ArrayList<>(ordinary);
        offered.add(0, "o1");
        offered.add(2, "o2");
        offered.add("s");
        Map<String, Double> expected = new HashMap<>();
        expected.put("s", 0.2);
        int cap = Math.min(3, ordinaryCount);
        for (int mask = 1; mask < (1 << ordinaryCount); mask++) {
            int k = Integer.bitCount(mask);
            if (k > cap) continue;
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < ordinaryCount; i++) if ((mask & (1 << i)) != 0) subset.add(ordinary.get(i));
            double p = 0.8 / cap / combinations(ordinaryCount, k);
            expected.put(render(offered, subset), p * 0.6);
            for (String other : List.of("o1", "o2")) {
                List<String> withOther = new ArrayList<>(subset);
                withOther.add(other);
                expected.put(render(offered, withOther), p * 0.2);
            }
        }
        List<String> answers = draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":0.2,\"specialValues\":[\"s\"]"),
                select(QuestionKind.SELECT_MULTIPLE, offered), N);
        for (String answer : answers) orderedValues(answer, offered);
        assertProbabilities(answers, expected);
    }

    @Test
    void multipleSelectCanContainThreeOrdinaryChoicesPlusOneOther() throws Exception {
        List<String> choices = List.of("o1", "z", "a", "o2", "b");
        List<String> answers = draws(generator(CODES + ",\"otherRate\":1"),
                select(QuestionKind.SELECT_MULTIPLE, choices), N);
        Map<Integer, Integer> sizes = new HashMap<>();
        for (String answer : answers) {
            List<String> selected = orderedValues(answer, choices);
            assertEquals(1, selected.stream().filter(v -> v.equals("o1") || v.equals("o2")).count());
            sizes.merge(selected.size(), 1, Integer::sum);
        }
        assertEquals(Set.of(2, 3, 4), sizes.keySet());
        for (int size = 2; size <= 4; size++) assertRate(sizes.get(size), N, 1.0 / 3, "total size " + size);
    }

    @Test
    void weightedMultipleSelectPreservesWithoutReplacementProbabilitiesWithOtherAdded() throws Exception {
        List<String> choices = List.of("o2", "z", "a", "o1", "b");
        // With weights 1,2,7, P({i,j}|k=2) = wi/10*wj/(10-wi) + wj/10*wi/(10-wj).
        Map<String, Double> ordinary = Map.of("z", 1.0 / 30, "a", 2.0 / 30, "b", 7.0 / 30,
                "z a", (1.0 / 10 * 2 / 9 + 2.0 / 10 * 1 / 8) / 3,
                "z b", (1.0 / 10 * 7 / 9 + 7.0 / 10 * 1 / 3) / 3,
                "a b", (2.0 / 10 * 7 / 8 + 7.0 / 10 * 2 / 3) / 3,
                "z a b", 1.0 / 3);
        Map<String, Double> expected = new HashMap<>();
        ordinary.forEach((subset, p) -> {
            expected.put(subset, p * 0.6);
            for (String other : List.of("o1", "o2")) {
                List<String> selected = new ArrayList<>(List.of(subset.split(" ")));
                selected.add(other);
                expected.put(render(choices, selected), p * 0.2);
            }
        });
        List<String> answers = draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":0,"
                + "\"fields\":{\"answer\":{\"weights\":{\"z\":1,\"a\":2,\"b\":7,\"absent\":1000000}}}"),
                select(QuestionKind.SELECT_MULTIPLE, choices), N);
        assertProbabilities(answers, expected);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void aNamedOtherBecomesOrdinaryButUnmentionedOthersRemainOther(QuestionKind kind) throws Exception {
        List<String> choices = List.of("o1", "a", "o2");
        List<String> answers = draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":1,"
                + "\"specialValues\":[\"o1\"],\"fields\":{\"answer\":{\"weights\":{\"o1\":3,\"a\":1}}}"),
                select(kind, choices), N);
        if (kind == QuestionKind.SELECT_ONE) {
            assertProbabilities(answers, Map.of("o1", 0.45, "a", 0.15, "o2", 0.4));
        } else {
            assertProbabilities(answers, Map.of("o1", 0.225, "a", 0.075, "o1 a", 0.3,
                    "o1 o2", 0.15, "a o2", 0.05, "o1 a o2", 0.2));
        }
    }

    @Test
    void zeroWeightNamedOtherCanFillMultipleSelectAfterPositiveWeightsRunOut() throws Exception {
        List<String> answers = draws(generator(CODES + ",\"otherRate\":1,\"specialRate\":0,"
                        + "\"fields\":{\"answer\":{\"weights\":{\"o1\":0,\"a\":1}}}"),
                select(QuestionKind.SELECT_MULTIPLE, List.of("o1", "a", "o2")), N);
        assertProbabilities(answers, Map.of("a o2", 0.5, "o1 a o2", 0.5));
    }

    @Test
    void allZeroOrdinaryWeightsIncludeNamedOtherInSelectOneFallback() throws Exception {
        assertProbabilities(draws(generator(CODES + ",\"otherRate\":0.4,\"specialRate\":0,"
                        + "\"fields\":{\"answer\":{\"weights\":{\"o1\":0,\"absent\":1}}}"),
                        select(QuestionKind.SELECT_ONE, List.of("o1", "a", "o2")), N),
                Map.of("o1", 0.3, "a", 0.3, "o2", 0.4));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void absentOtherCodesKeepOldOrdinaryAndSpecialDistributionsDespiteOtherRateOne(QuestionKind kind) throws Exception {
        List<String> answers = draws(generator("\"otherRate\":1,\"specialRate\":0.2,\"specialValues\":[\"s\"]"),
                select(kind, List.of("96", "a", "s")), N);
        if (kind == QuestionKind.SELECT_ONE) {
            assertProbabilities(answers, Map.of("96", 0.4, "a", 0.4, "s", 0.2));
        } else {
            assertProbabilities(answers, Map.of("96", 0.2, "a", 0.2, "96 a", 0.4, "s", 0.2));
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void configuredButUnofferedOthersDoNotChangeOrdinaryDistribution(QuestionKind kind) throws Exception {
        List<String> answers = draws(generator(CODES + ",\"otherRate\":1,\"specialRate\":1"),
                select(kind, List.of("b", "a")), N);
        if (kind == QuestionKind.SELECT_ONE) assertUniform(answers, List.of("b", "a"));
        else assertProbabilities(answers, Map.of("b", 0.25, "a", 0.25, "b a", 0.5));
    }

    private static String render(List<String> offered, List<String> selected) {
        return String.join(" ", offered.stream().filter(selected::contains).toList());
    }

    private static int combinations(int n, int k) {
        int result = 1;
        for (int i = 1; i <= k; i++) result = result * (n - i + 1) / i;
        return result;
    }

    private static void assertProbabilities(List<String> answers, Map<String, Double> expected) {
        assertEquals(1, expected.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12,
                "The test oracle must account for all probability mass");
        Map<String, Integer> observed = counts(answers);
        assertTrue(expected.keySet().containsAll(observed.keySet()), "Unexpected answers: " + observed.keySet());
        expected.forEach((answer, probability) ->
                assertRate(observed.getOrDefault(answer, 0), answers.size(), probability, String.valueOf(answer)));
    }
}
