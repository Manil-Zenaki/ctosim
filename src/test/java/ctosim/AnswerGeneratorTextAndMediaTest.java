package ctosim;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorTextAndMediaTest {
    @Test
    void textOverrideIsUniformAndPreservesUnicodeWhitespaceAndEmptyStrings() throws Exception {
        assertUniform(draws(override("\"texts\":[\"\",\"  café 村 🙂  \",\"line\\nbreak\"]"),
                question(QuestionKind.TEXT), DRAWS), List.of("", "  café 村 🙂  ", "line\nbreak"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"numbers", "numbers_phone"})
    void textOverrideTakesPrecedenceOverNumericAppearance(String appearance) throws Exception {
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), appearance, true);
        for (String answer : draws(override("\"texts\":[\"fixed text\"]"), question, 100)) {
            assertEquals("fixed text", answer);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"numbers", "numbers_phone", "minimal numbers", "numbers compact",
            "minimal numbers_phone compact", "  numbers   compact  "})
    void numericAppearanceWordsProduceEightAsciiDigits(String appearance) throws Exception {
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), appearance, true);
        List<String> answers = draws(generator(""), question, 2000);
        for (String answer : answers) assertTrue(answer.matches("[0-9]{8}"), answer);
        assertTrue(answers.stream().anyMatch(a -> a.startsWith("0")), "Leading zeros are allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "minimal", "my_numbers", "numbers_phone_extra", "renumbers", "numbersX"})
    void onlyCompleteAppearanceWordsActivateNumericText(String appearance) throws Exception {
        Question question = new Question("village_村", QuestionKind.TEXT, List.of(), appearance, true);
        for (String answer : draws(generator(""), question, 100)) {
            assertTrue(answer.matches("village_村 [1-9][0-9]{0,3}"), answer);
        }
    }

    @Test
    void defaultTextSuffixIsUniformFromOneThrough9999IncludingEndpoints() throws Exception {
        List<String> answers = draws(generator(""), question(QuestionKind.TEXT), 150_000);
        int[] buckets = new int[9];
        boolean first = false;
        boolean last = false;
        for (String answer : answers) {
            assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
            int value = Integer.parseInt(answer.substring("answer ".length()));
            buckets[(value - 1) / 1111]++;
            first |= value == 1;
            last |= value == 9999;
        }
        for (int i = 0; i < buckets.length; i++) assertRate(buckets[i], answers.size(), 1.0 / 9, "text bucket " + i);
        assertTrue(first, "Lower endpoint must be reachable");
        assertTrue(last, "Upper endpoint must be reachable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"numbers", "numbers_phone"})
    void numericTextDigitsAreUniformAtEveryPosition(String appearance) throws Exception {
        Question question = new Question("answer", QuestionKind.TEXT, List.of(), appearance, true);
        List<String> answers = draws(generator(""), question, DRAWS);
        assertDigitsUniform(answers, 8);
        // Adjacent digit pairs exercise separate draws, rejecting repeated-one-digit answers.
        for (int position = 0; position < 7; position++) {
            int[] pairs = new int[100];
            for (String answer : answers) pairs[Integer.parseInt(answer.substring(position, position + 2))]++;
            for (int pair = 0; pair < 100; pair++) assertRate(pairs[pair], DRAWS, 0.01, "digit pair " + pair);
        }
    }

    @Test
    void barcodeHasExactlyTenAsciiDigits() throws Exception {
        List<String> answers = draws(generator(""), question(QuestionKind.BARCODE), 2000);
        for (String answer : answers) assertTrue(answer.matches("[0-9]{10}"), answer);
        assertTrue(new HashSet<>(answers).size() > 1);
        // Uniformity of barcode digits is not specified (unlike numeric text).
    }

    @ParameterizedTest
    @CsvSource({"IMAGE,jpg", "AUDIO,m4a", "VIDEO,mp4", "FILE,bin"})
    void mediaFileNamesPreserveQuestionNameAndUseUniformSuffixAndCorrectExtension(QuestionKind kind,
                                                                                 String extension) throws Exception {
        String name = "photo_hh_村_é";
        Question question = new Question(name, kind, List.of(), "", true);
        List<String> answers = draws(generator(""), question, DRAWS);
        int[] buckets = new int[9];
        for (String answer : answers) {
            assertTrue(answer.startsWith(name + "_"), answer);
            assertTrue(answer.endsWith("." + extension), answer);
            String suffix = answer.substring(name.length() + 1, answer.length() - extension.length() - 1);
            assertTrue(suffix.matches("[1-9][0-9]{0,5}"), answer);
            int value = Integer.parseInt(suffix);
            assertTrue(value >= 1 && value <= 999999, answer);
            buckets[(value - 1) / 111111]++;
        }
        for (int i = 0; i < buckets.length; i++) assertRate(buckets[i], DRAWS, 1.0 / 9, "file suffix bucket " + i);
    }

    @Test
    void acknowledgeAlwaysReturnsUppercaseOK() throws Exception {
        for (String answer : draws(generator(""), question(QuestionKind.ACKNOWLEDGE), 100)) assertEquals("OK", answer);
    }

    @Test
    void textRangeAndWeightsAreIgnoredWhileTextsStillApply() throws Exception {
        AnswerGenerator generator = override("\"min\":9,\"max\":9,\"weights\":{\"wrong\":1},"
                + "\"texts\":[\"right\"]");
        for (String answer : draws(generator, question(QuestionKind.TEXT), 100)) assertEquals("right", answer);
    }

    @Test
    void textIgnoresDateRangeAndFallsBackToNameAndNumber() throws Exception {
        for (String answer : draws(override("\"min\":\"2000-01-01\",\"max\":\"2000-01-01\""),
                question(QuestionKind.TEXT), 100)) assertTrue(answer.matches("answer [1-9][0-9]{0,3}"), answer);
    }

    private static void assertDigitsUniform(List<String> answers, int length) {
        int[][] counts = new int[length][10];
        for (String answer : answers) {
            assertTrue(answer.matches("[0-9]{" + length + "}"), answer);
            for (int i = 0; i < length; i++) counts[i][answer.charAt(i) - '0']++;
        }
        for (int i = 0; i < length; i++) {
            for (int d = 0; d < 10; d++) assertRate(counts[i][d], answers.size(), 0.1, "position " + i + ", digit " + d);
        }
    }
}
