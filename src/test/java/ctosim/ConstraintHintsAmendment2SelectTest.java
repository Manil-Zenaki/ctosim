package ctosim;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment2Support.*;
import static ctosim.ConstraintHintsTestSupport.*;
import static ctosim.FormWalkerTestSupport.assertUniformBin;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment2SelectTest {
    @Test void selectOneNeverOffersExcludedChoicesAndUsesRemainingWeights() throws Exception {
        var q = select(QuestionKind.SELECT_ONE, hints().exclude("a", "-99").build(), "a", "b", "c", "-99");
        var generator = generator("\"specialRate\":1,\"fields\":{\"answer\":{\"weights\":{\"a\":100,\"b\":1,\"c\":3}}}");
        var random = new Random(SEED); int b = 0; int n = 16000;
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertTrue(answer.equals("b") || answer.equals("c"), answer);
            if (answer.equals("b")) b++;
        }
        assertUniformBin(b, n, .25, "weights after exclusion");
    }

    @ParameterizedTest @ValueSource(strings = {"b", "-99"})
    void selectOneInclusionWinsOverSpecialRateAndZeroWeight(String included) throws Exception {
        var q = select(QuestionKind.SELECT_ONE, hints().include(included).build(), "a", "b", "-99");
        for (int rate : List.of(0, 1)) {
            var generator = generator("\"specialRate\":" + rate + ",\"fields\":{\"answer\":{\"weights\":{\"a\":1}}}");
            var random = new Random(SEED);
            for (int i = 0; i < 50; i++) assertEquals(included, generator.generate(q, TODAY, random));
        }
    }

    static Stream<Arguments> impossibleSelections() {
        return Stream.of(Arguments.of(QuestionKind.SELECT_ONE, hints().exclude("a", "b", "c").build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().exclude("a", "b", "c").build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().include("missing").build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().include("a").exclude("a").build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().count(4, 5).build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().count(2, 1).build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().count(0, 0).build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().include("a", "b").count(1, 1).build()),
                Arguments.of(QuestionKind.SELECT_MULTIPLE, hints().exclude("a", "b").count(2, 3).build()));
    }

    @ParameterizedTest @MethodSource("impossibleSelections")
    void impossibleSelectionHintsAreIgnoredWithTheSameRandomStream(QuestionKind kind, ConstraintHints h) throws Exception {
        var generator = generator(""); var actual = new Random(SEED); var expected = new Random(SEED);
        for (int i = 0; i < 200; i++) assertEquals(generator.generate(question(kind, ConstraintHints.none()), TODAY, expected),
                generator.generate(question(kind, h), TODAY, actual));
    }

    @Test void selectMultipleIncludesEveryRequiredChoiceAndPreservesOfferedOrderWithoutDuplicates() throws Exception {
        var choices = List.of("c", "-99", "b", "a", "d", "e");
        var h = hints().include("a", "c").exclude("-99", "e").count(3, 4).build();
        var q = select(QuestionKind.SELECT_MULTIPLE, h, choices.toArray(String[]::new));
        var generator = generator("\"specialRate\":1"); var random = new Random(SEED);
        int n = 16000; int three = 0;
        for (int i = 0; i < n; i++) {
            var answer = Arrays.asList(generator.generate(q, TODAY, random).split(" "));
            assertTrue(answer.containsAll(List.of("a", "c")));
            assertFalse(answer.contains("-99")); assertFalse(answer.contains("e"));
            assertEquals(answer.size(), new HashSet<>(answer).size());
            assertEquals(choices.stream().filter(answer::contains).toList(), answer);
            assertTrue(answer.size() == 3 || answer.size() == 4);
            if (answer.size() == 3) three++;
        }
        assertUniformBin(three, n, .5, "selection count");
    }

    static Stream<Arguments> countDefaults() {
        return Stream.of(Arguments.of(hints().include("a").build(), 1, 3),
                Arguments.of(hints().include("a", "b").count(0, 4).build(), 2, 4),
                Arguments.of(hints().count(4, null).build(), 4, 4),
                Arguments.of(hints().include("a", "b", "c", "d").build(), 4, 4),
                Arguments.of(hints().exclude("e").count(2, 99).build(), 2, 4),
                Arguments.of(hints().exclude("c", "d", "e").build(), 1, 2));
    }

    @ParameterizedTest @MethodSource("countDefaults")
    void countsRespectDefaultsIncludedMinimumAndOfferedMaximum(ConstraintHints h, int min, int max) throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, h, "a", "b", "c", "d", "e");
        var generator = generator(""); var random = new Random(SEED); int n = 12000;
        int[] bins = new int[max - min + 1];
        for (int i = 0; i < n; i++) {
            var answer = Arrays.asList(generator.generate(q, TODAY, random).split(" "));
            assertTrue(answer.size() >= min && answer.size() <= max, answer.toString());
            assertTrue(answer.containsAll(h.included()));
            assertTrue(answer.stream().noneMatch(h.excluded()::contains));
            bins[answer.size() - min]++;
        }
        for (int i = 0; i < bins.length; i++) assertUniformBin(bins[i], n, 1d / bins.length, "count " + (min + i));
    }

    @Test void includedSpecialChoiceCanAppearWithOrdinaryChoicesWhenHintsRequireIt() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, hints().include("-99", "b").count(2, 2).build(), "a", "-99", "b");
        var generator = generator("\"specialRate\":1"); var random = new Random(SEED);
        for (int i = 0; i < 100; i++) assertEquals("-99 b", generator.generate(q, TODAY, random));
    }

    @Test void countHintsSuppressSpecialSingletonDraws() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, hints().count(2, 2).build(), "a", "b", "-99");
        var generator = generator("\"specialRate\":1"); var random = new Random(SEED);
        for (int i = 0; i < 100; i++) assertEquals(2, generator.generate(q, TODAY, random).split(" ").length);
    }

    @Test void absentSelectionHintsKeepOriginalSpecialSingletonRule() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, ConstraintHints.none(), "a", "b", "-99");
        var generator = generator("\"specialRate\":1"); var random = new Random(SEED);
        for (int i = 0; i < 100; i++) assertEquals("-99", generator.generate(q, TODAY, random));
    }

    @Test void unweightedRemainingChoicesAreUniformGivenAnIncludedChoice() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, hints().include("a").count(2, 2).build(), "a", "b", "c", "d");
        var generator = generator(""); var random = new Random(SEED); int n = 16000; int[] bins = new int[3];
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            int index = List.of("a b", "a c", "a d").indexOf(answer);
            assertTrue(index >= 0, answer); bins[index]++;
        }
        for (int i = 0; i < 3; i++) assertUniformBin(bins[i], n, 1d / 3, "remaining choice " + i);
    }

    @Test void weightedRemainingChoicesDoNotDropAZeroWeightIncludedChoice() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, hints().include("a").count(2, 2).build(), "a", "b", "c");
        var generator = generator("\"fields\":{\"answer\":{\"weights\":{\"b\":1,\"c\":3}}}");
        var random = new Random(SEED); int b = 0; int n = 16000;
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertTrue(answer.equals("a b") || answer.equals("a c"), answer);
            if (answer.equals("a b")) b++;
        }
        assertUniformBin(b, n, .25, "remaining weighted choice");
    }

    @Test void noOfferedChoicesStillReturnsBlankDespiteInclusions() throws Exception {
        var generator = generator("");
        for (var kind : List.of(QuestionKind.SELECT_ONE, QuestionKind.SELECT_MULTIPLE))
            assertNull(generator.generate(select(kind, hints().include("a").build()), TODAY, new Random(SEED)));
    }

    @Test void sameSeedsReplaySelectionsWithNewHints() throws Exception {
        var q = select(QuestionKind.SELECT_MULTIPLE, hints().include("b").exclude("d").count(2, 3).build(), "c", "b", "a", "d");
        var a = generator(""); var b = generator(""); var ra = new Random(SEED); var rb = new Random(SEED);
        for (int i = 0; i < 500; i++) assertEquals(a.generate(q, TODAY, ra), b.generate(q, TODAY, rb));
    }
}
