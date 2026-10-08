package ctosim;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorOverrideTest {
    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void selectIgnoresNumericRangeAndTextsButHonorsApplicableWeights(QuestionKind kind) throws Exception {
        AnswerGenerator generator = override("\"min\":123,\"max\":123,\"texts\":[\"wrong\"],"
                + "\"weights\":{\"kept\":1}");
        for (String answer : draws(generator, select(kind, List.of("kept")), 100)) assertEquals("kept", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void selectIgnoresDateRange(QuestionKind kind) throws Exception {
        for (String answer : draws(override("\"min\":\"2000-01-01\",\"max\":\"2000-01-01\""),
                select(kind, List.of("kept")), 100)) assertEquals("kept", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"BARCODE", "IMAGE", "AUDIO", "VIDEO", "FILE", "ACKNOWLEDGE",
            "GEOPOINT", "GEOTRACE", "GEOSHAPE"})
    void otherKindsIgnoreTextsWeightsAndEitherRangeType(QuestionKind kind) throws Exception {
        for (String range : List.of("\"min\":123,\"max\":123",
                "\"min\":\"2000-01-01\",\"max\":\"2000-01-01\"")) {
            AnswerGenerator generator = override(range + ",\"texts\":[\"wrong\"],\"weights\":{\"wrong\":1}");
            for (String answer : draws(generator, question(kind), 100)) assertKindFormat(kind, answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "TIME", "DATETIME", "SELECT_ONE",
            "SELECT_MULTIPLE", "GEOPOINT", "GEOTRACE", "GEOSHAPE", "BARCODE", "IMAGE", "AUDIO", "VIDEO", "FILE", "ACKNOWLEDGE"})
    void numericTextAppearanceOnlyAppliesToTextQuestions(QuestionKind kind) throws Exception {
        Question question = new Question("answer", kind, question(kind).choices(), "numbers_phone", true);
        AnswerGenerator generator = generator("");
        for (String answer : draws(generator, question, 100)) {
            switch (kind) {
                case INTEGER -> assertTrue(answer.matches("[0-9]{1,3}"), answer);
                case DECIMAL -> assertTrue(answer.matches("[0-9]{1,4}(\\.[0-9]{1,2})?"), answer);
                case DATE -> assertTrue(answer.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), answer);
                case TIME -> assertTrue(answer.matches("[0-9]{2}:[0-9]{2}:00"), answer);
                case DATETIME -> assertTrue(answer.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:00"), answer);
                case SELECT_ONE, SELECT_MULTIPLE -> {
                    for (String token : answer.split(" ")) assertTrue(question.choices().contains(token), answer);
                }
                default -> assertKindFormat(kind, answer);
            }
        }
    }

    private static void assertKindFormat(QuestionKind kind, String answer) {
        assertNotNull(answer);
        switch (kind) {
            case ACKNOWLEDGE -> assertEquals("OK", answer);
            case BARCODE -> assertTrue(answer.matches("[0-9]{10}"), answer);
            case IMAGE -> assertTrue(answer.matches("answer_[1-9][0-9]{0,5}\\.jpg"), answer);
            case AUDIO -> assertTrue(answer.matches("answer_[1-9][0-9]{0,5}\\.m4a"), answer);
            case VIDEO -> assertTrue(answer.matches("answer_[1-9][0-9]{0,5}\\.mp4"), answer);
            case FILE -> assertTrue(answer.matches("answer_[1-9][0-9]{0,5}\\.bin"), answer);
            case GEOPOINT, GEOTRACE, GEOSHAPE -> {
                String[] points = answer.split("; ", -1);
                assertEquals(kind == QuestionKind.GEOPOINT ? 1 : kind == QuestionKind.GEOTRACE ? 4 : 5, points.length);
                for (String point : points) assertTrue(point.matches("-?[0-9]+\\.[0-9]{6} -?[0-9]+\\.[0-9]{6} 0 5"), point);
                String[] first = points[0].split(" ");
                assertTrue(Math.abs(Double.parseDouble(first[0])) <= 1);
                assertTrue(Math.abs(Double.parseDouble(first[1])) <= 1);
            }
            default -> fail("No format assertion for " + kind);
        }
    }
}
