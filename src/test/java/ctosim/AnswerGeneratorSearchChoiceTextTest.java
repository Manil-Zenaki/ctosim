package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorSearchSupport.*;
import static ctosim.ConstraintHintsAmendment2Support.hints;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorSearchChoiceTextTest {
    @Test
    void selectOneListsAllOfferedChoicesInDisplayOrderIncludingSpecialAndZeroWeightChoices() throws Exception {
        var q = new Question("answer", QuestionKind.SELECT_ONE, List.of("z", "99", "é", "a", "-99"), "", true);
        assertLevel(q.choices(), search(generator("\"specialRate\":1,"
                + "\"fields\":{\"answer\":{\"weights\":{\"a\":1,\"z\":0,\"not_offered\":10}}}"), q), 0, 1);
    }

    @Test
    void fourChoiceSearchIncludesEveryNonemptySubsetInSizeThenDisplayOrder() throws Exception {
        var q = new Question("answer", QuestionKind.SELECT_MULTIPLE, List.of("z", "a", "é", "99"), "", true);
        assertLevel(List.of("z", "a", "é", "99", "z a", "z é", "z 99", "a é", "a 99", "é 99",
                "z a é", "z a 99", "z é 99", "a é 99", "z a é 99"), search(generator(""), q), 0, 1);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void singletonSelectHasExactlyOneSearchAnswer(QuestionKind kind) throws Exception {
        assertLevel(List.of("唯一"), search(generator(""), new Question("answer", kind, List.of("唯一"), "", false)), 0, 1);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void emptySelectHasNoSearchValues(QuestionKind kind) throws Exception {
        var levels = search(generator(""), new Question("answer", kind, List.of(), "", true));
        assertNotNull(levels);
        assertTrue(levels.stream().allMatch(List::isEmpty), "No answer can be made from zero choices");
    }

    @ParameterizedTest @ValueSource(ints = {5, 26, 27})
    void combinationLimitKeepsTheFirst20000AndNeverSelectsMoreThanFour(int count) throws Exception {
        List<String> choices = IntStream.range(0, count).mapToObj(i -> "v" + (count - i)).toList();
        List<String> expected = new ArrayList<>();
        // Independent exhaustive oracle: enumerate every subset up to size four before applying the cap.
        expected.addAll(choices);
        for (int a = 0; a < count; a++) for (int b = a + 1; b < count; b++) {
            expected.add(choices.get(a) + " " + choices.get(b));
        }
        for (int a = 0; a < count; a++) for (int b = a + 1; b < count; b++) for (int c = b + 1; c < count; c++) {
            expected.add(choices.get(a) + " " + choices.get(b) + " " + choices.get(c));
        }
        for (int a = 0; a < count; a++) for (int b = a + 1; b < count; b++) for (int c = b + 1; c < count; c++) {
            for (int d = c + 1; d < count; d++) {
                expected.add(choices.get(a) + " " + choices.get(b) + " " + choices.get(c) + " " + choices.get(d));
            }
        }
        assertEquals(count == 5 ? 30 : count == 26 ? 17901 : 20853, expected.size());
        assertLevel(expected.subList(0, Math.min(20000, expected.size())), search(generator(""),
                new Question("answer", QuestionKind.SELECT_MULTIPLE, choices, "", true)), 0, 1);
    }

    @Test
    void selectMultipleSearchDoesNotUseWeightsOrSpecialChoiceDrawingRules() throws Exception {
        assertLevel(List.of("99", "z", "a", "99 z", "99 a", "z a", "99 z a"), search(generator(
                "\"specialRate\":1,\"fields\":{\"answer\":{\"weights\":{\"z\":0,\"a\":1}}}"),
                new Question("answer", QuestionKind.SELECT_MULTIPLE, List.of("99", "z", "a"), "", true)), 0, 1);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void textLikeCandidatesAreVerbatimInFirstOccurrenceOrder(QuestionKind kind) throws Exception {
        var h = hints().candidates("Z", "é雪", "", "a b", "Z", "001", "é雪").build();
        assertLevel(List.of("Z", "é雪", "", "a b", "001"), search(generator(""), question(kind, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void prefixSearchAddsOneThroughTwelveZerosAfterCandidatesAndDropsDuplicates(QuestionKind kind) throws Exception {
        var h = hints().candidates("candidate", "pré00", "candidate").prefix("pré").build();
        List<String> expected = new ArrayList<>(List.of("candidate", "pré00"));
        for (int n = 1; n <= 12; n++) if (n != 2) expected.add("pré" + "0".repeat(n));
        assertLevel(expected, search(generator(""), question(kind, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void prefixExpansionAlsoWorksWithoutCandidates(QuestionKind kind) throws Exception {
        List<String> expected = IntStream.rangeClosed(1, 12).mapToObj(n -> "09" + "0".repeat(n)).toList();
        assertLevel(expected, search(generator(""), question(kind, hints().prefix("09").build())), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void textSearchDoesNotTruncatePrefixesToLengthHintsOrReplaceThemWithPatternDraws(QuestionKind kind) throws Exception {
        var h = hints().prefix("X").length(2, 2).pattern("^Y$").candidates("candidate").build();
        List<String> expected = new ArrayList<>(List.of("candidate"));
        for (int n = 1; n <= 12; n++) expected.add("X" + "0".repeat(n));
        assertLevel(expected, search(generator(""), question(kind, h)), 0, 2);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void broadTextLevelListsIntegersThenRepeatedCharactersWithCrossSectionDuplicatesRemoved(QuestionKind kind)
            throws Exception {
        var levels = search(generator(""), question(kind, hints().prefix("X").candidates("z").build()));
        assertLevel(textFallback(), levels, 1, 2);
        assertEquals(20296, levels.get(1).size());
        assertEquals("1".repeat(100), levels.get(1).get(20295));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void noHintsStillProvideTheBroadTextSearch(QuestionKind kind) throws Exception {
        var levels = search(generator(""), question(kind));
        // Empty first levels may be retained or omitted; the spec does not settle that representation.
        assertFalse(levels.isEmpty());
        assertEquals(textFallback(), levels.get(levels.size() - 1));
        assertTrue(levels.subList(0, levels.size() - 1).stream().allMatch(List::isEmpty));
    }
}
