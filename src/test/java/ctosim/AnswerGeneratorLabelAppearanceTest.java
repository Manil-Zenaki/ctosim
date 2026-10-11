package ctosim;

import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 05, Amendment 1: table headers cannot be answered. */
class AnswerGeneratorLabelAppearanceTest {
    static Stream<Arguments> labelCases() {
        return Stream.of(QuestionKind.SELECT_ONE, QuestionKind.SELECT_MULTIPLE).flatMap(kind ->
                Stream.of(false, true).flatMap(required ->
                        Stream.of("label", "label list-nolabel", "list-nolabel label",
                                "compact label minimal", "  label  ", "compact   label   minimal")
                                .map(appearance -> Arguments.of(kind, required, appearance))));
    }

    @ParameterizedTest
    @MethodSource("labelCases")
    void labelWordIsAlwaysBlankEvenWhenRequiredAndMissingRateIsZero(
            QuestionKind kind, boolean required, String appearance) throws Exception {
        Question question = new Question("answer", kind, List.of("one", "two", "99"), appearance, required);
        for (String answer : draws(generator("\"missingRate\":0"), question, 20)) assertNull(answer);
    }

    static Stream<Arguments> fixedLabelCases() {
        return Stream.of(QuestionKind.SELECT_ONE, QuestionKind.SELECT_MULTIPLE).flatMap(kind ->
                Stream.of(false, true).flatMap(required ->
                        Stream.of("one", "outside-the-choices", "", "café 東京")
                                .map(value -> Arguments.of(kind, required, value))));
    }

    @ParameterizedTest
    @MethodSource("fixedLabelCases")
    void labelBlankTakesPrecedenceOverEveryFixedValueIncludingEmptyString(
            QuestionKind kind, boolean required, String fixed) throws Exception {
        AnswerGenerator generator = override("\"value\":\"" + fixed + "\",\"missingRate\":0");
        Question question = new Question("answer", kind, List.of("one", "two"), "label", required);
        for (String answer : draws(generator, question, 20)) assertNull(answer);
    }

    static Stream<Arguments> selectionCases() {
        return Stream.of(QuestionKind.SELECT_ONE, QuestionKind.SELECT_MULTIPLE).flatMap(kind ->
                Stream.of(List.<String>of(), List.of("one"), List.of("99", "-99"),
                                List.of("one", "two", "99"))
                        .flatMap(choices -> Stream.of(0, 1).map(rate -> Arguments.of(kind, choices, rate))));
    }

    @ParameterizedTest
    @MethodSource("selectionCases")
    void labelBlankPrecedesSpecialChoiceAndWeightedSelection(
            QuestionKind kind, List<String> choices, int specialRate) throws Exception {
        AnswerGenerator generator = generator("\"missingRate\":0,\"specialRate\":" + specialRate
                + ",\"fields\":{\"answer\":{\"weights\":{\"one\":1,\"two\":0}}}");
        Question question = new Question("answer", kind, choices, "label", true);
        for (String answer : draws(generator, question, 20)) assertNull(answer);
    }

    static Stream<Arguments> nonLabelCases() {
        return Stream.of(QuestionKind.SELECT_ONE, QuestionKind.SELECT_MULTIPLE).flatMap(kind ->
                Stream.of("list-nolabel", "labels", "my_label", "label_extra", "label-list",
                                "compact list-nolabel minimal")
                        .map(appearance -> Arguments.of(kind, appearance)));
    }

    @ParameterizedTest
    @MethodSource("nonLabelCases")
    void substringsIncludingListNolabelRemainAnswerable(QuestionKind kind, String appearance) throws Exception {
        Question question = new Question("answer", kind, List.of("one"), appearance, true);
        for (String answer : draws(generator("\"missingRate\":0"), question, 20)) assertEquals("one", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void labelDoesNotBlankOtherQuestionKinds(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, List.of(), "label", true);
        for (String answer : draws(generator("\"missingRate\":0"), question, 20)) assertNotNull(answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void labelDoesNotSuppressFixedValuesForOtherKinds(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, List.of(), "label", true);
        assertEquals("verbatim", override("\"value\":\"verbatim\"").generate(question, TODAY, new Random(SEED)));
    }
}
