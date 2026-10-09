package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsTextAndPrecedenceTest {
    @ParameterizedTest @CsvSource({"2,5,2,5", "4,,4,10", "12,,12,12", ",4,1,4", "0,0,0,0", "0,3,0,3", "1,1,1,1"})
    void lengthIsUniformWithSpecifiedDefaultsAndAllowsEmptyString(Integer min, Integer max, int effectiveMin, int effectiveMax) throws Exception {
        var q = question(QuestionKind.TEXT, text(min, max, null));
        var samples = AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 20000);
        List<String> lengths = samples.stream().map(s -> Integer.toString(s.length())).toList();
        AnswerGeneratorTestSupport.assertUniform(lengths, java.util.stream.IntStream.rangeClosed(effectiveMin, effectiveMax)
                .mapToObj(Integer::toString).toList());
        for (String value : samples) assertTrue(value.matches("[a-z]*"), value);
    }

    @ParameterizedTest @ValueSource(strings = {"numbers", "numbers_phone", "compact numbers", "numbers_phone compact", ""})
    void eachCharacterIsUniformInTheSpecifiedAlphabet(String appearance) throws Exception {
        var q = question(QuestionKind.TEXT, appearance, true, text(8, 8, null));
        var samples = AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 12000);
        String alphabet = appearance.isEmpty() ? "abcdefghijklmnopqrstuvwxyz" : "0123456789";
        List<String> allowed = alphabet.chars().mapToObj(c -> Character.toString((char) c)).toList();
        for (int position = 0; position < 8; position++) {
            int index = position;
            for (String s : samples) assertEquals(8, s.length());
            AnswerGeneratorTestSupport.assertUniform(samples.stream().map(s -> s.substring(index, index + 1)).toList(), allowed);
        }
        assertTrue(new HashSet<>(samples).size() > 100, "Fresh character draws must not just repeat a fixed word");
    }

    @ParameterizedTest @ValueSource(strings = {"", "numbers", "numbers_phone"})
    void invertedLengthBoundsFallBackToOriginalTextRules(String appearance) throws Exception {
        var generator = generator("");
        var plain = question(QuestionKind.TEXT, appearance, true, ConstraintHints.none());
        for (var h : List.of(text(8, 2, null), text(null, 0, null), text(0, -1, null))) {
            var q = question(QuestionKind.TEXT, appearance, true, h);
            assertEquals(AnswerGeneratorTestSupport.draws(generator, plain, 100),
                    AnswerGeneratorTestSupport.draws(generator, q, 100));
        }
    }

    @Test void phonePatternAlwaysMatchesInFullAndOverridesAppearanceAndLength() throws Exception {
        var q = question(QuestionKind.TEXT, "", true, text(1, 2, "^09[0-9]{8}$"));
        var values = AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 1000);
        for (String s : values) assertTrue(s.matches("^09[0-9]{8}$"), s);
        assertTrue(new HashSet<>(values).size() > 1, "Pattern generation must make fresh random draws");
    }

    @ParameterizedTest @ValueSource(strings = {"[", "(?!)"})
    void invalidAndUnsatisfiablePatternsFallBackToLengths(String pattern) throws Exception {
        var values = AnswerGeneratorTestSupport.draws(generator(""), question(QuestionKind.TEXT,
                "numbers_phone", true, text(3, 3, pattern)), 100);
        for (String s : values) assertTrue(s.matches("[0-9]{3}"), s);
    }

    @ParameterizedTest @ValueSource(strings = {"[", "(?!)"})
    void unusablePatternWithoutLengthsFallsBackToOriginalTextRule(String pattern) throws Exception {
        var values = AnswerGeneratorTestSupport.draws(generator(""), question(QuestionKind.TEXT, text(null, null, pattern)), 100);
        for (String s : values) assertTrue(s.matches("answer [1-9][0-9]{0,3}"), s);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "", "café", "(?:a|b){2,4}", "[A-Z]\\d{2}", "a?b+", "[^0-9]{2}", "(?=ab)ab"})
    void otherJavaPatternsEitherMatchInFullOrUseDocumentedUnsupportedFallback(String pattern) throws Exception {
        // The spec deliberately permits unsupported patterns; only the phone acceptance pattern is mandatory.
        var values = AnswerGeneratorTestSupport.draws(generator(""), question(QuestionKind.TEXT, text(null, null, pattern)), 50);
        for (String s : values) assertTrue(s.matches(pattern) || s.matches("answer [1-9][0-9]{0,3}"),
                "Must match the entire pattern or use the specified unsupported fallback: " + s);
    }

    @Test void textsOverrideIgnoresPatternAndLengthsAndKeepsUniformUnicodeAndEmptyChoices() throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"texts\":[\"\",\"café\",\"東京\"]}}");
        var values = AnswerGeneratorTestSupport.draws(generator,
                question(QuestionKind.TEXT, "numbers", true, text(10, 10, "^09[0-9]{8}$")),
                TODAY, new Random(SEED), 20000);
        AnswerGeneratorTestSupport.assertUniform(values, List.of("", "café", "東京"));
    }

    static ConstraintHints allHints() {
        return new ConstraintHints(new Bound(900000000, true), new Bound(999999999, true),
                new DateBound(TODAY.plusYears(10), true), new DateBound(TODAY.plusYears(11), true),
                2, 4, "^09[0-9]{8}$");
    }

    @ParameterizedTest @EnumSource(QuestionKind.class)
    void blankRuleStillPrecedesFixedValuesAndHints(QuestionKind kind) throws Exception {
        var generator = generator("\"missingRate\":1,\"fields\":{\"answer\":{\"value\":\"fixed\"}}");
        assertNull(generator.generate(question(kind, "", false, allHints()), TODAY, new Random(SEED)));
        assertEquals("fixed", generator.generate(question(kind, "", true, allHints()), TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(QuestionKind.class)
    void fieldMissingRateStillOverridesGlobalRateBeforeHints(QuestionKind kind) throws Exception {
        var optional = question(kind, "", false, allHints());
        assertNull(generator("\"missingRate\":0,\"fields\":{\"answer\":{\"missingRate\":1}}")
                .generate(optional, TODAY, new Random(SEED)));
        assertNotNull(generator("\"missingRate\":1,\"fields\":{\"answer\":{\"missingRate\":0}}")
                .generate(optional, TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void emptySelectStillBlanksEvenWithFixedValueAndHints(QuestionKind kind) throws Exception {
        var generator = generator("\"fields\":{\"answer\":{\"value\":\"fixed\"}}");
        assertNull(generator.generate(new Question("answer", kind, List.of(), "", true, allHints()), TODAY, new Random(SEED)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "TEXT", "DATE", "DATETIME"}, mode = EnumSource.Mode.EXCLUDE)
    void unlistedKindsIgnoreEveryHint(QuestionKind kind) throws Exception {
        var generator = generator("");
        assertEquals(AnswerGeneratorTestSupport.draws(generator, question(kind, ConstraintHints.none()), 200),
                AnswerGeneratorTestSupport.draws(generator, question(kind, allHints()), 200));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "DATETIME", "TEXT"})
    void listedKindsIgnoreHintsForOtherKinds(QuestionKind kind) throws Exception {
        ConstraintHints irrelevant = switch (kind) {
            case INTEGER, DECIMAL -> new ConstraintHints(null, null, allHints().earliest(), allHints().latest(), 2, 4, "x");
            case DATE, DATETIME -> new ConstraintHints(allHints().lower(), allHints().upper(), null, null, 2, 4, "x");
            default -> new ConstraintHints(allHints().lower(), allHints().upper(), allHints().earliest(), allHints().latest(), null, null, null);
        };
        var generator = generator("");
        assertEquals(AnswerGeneratorTestSupport.draws(generator, question(kind, ConstraintHints.none()), 200),
                AnswerGeneratorTestSupport.draws(generator, question(kind, irrelevant), 200));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "DATETIME", "TEXT"})
    @ResourceLock("java.util.Locale.default")
    void hintedDrawsAreReproducibleIndependentOfLocaleAndUseCallerRandomState(QuestionKind kind) throws Exception {
        var q = question(kind, allHints());
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            var first = AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 200);
            Locale.setDefault(Locale.FRANCE);
            var second = AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED), 200);
            assertEquals(first, second);
            assertNotEquals(first, AnswerGeneratorTestSupport.draws(generator(""), q, TODAY, new Random(SEED + 1), 200));
            assertTrue(new HashSet<>(first).size() > 1);
        } finally { Locale.setDefault(previous); }
    }

    @Test void lengthOnlyDrawsAreReproducibleAndOptionalMissingRateIsUnchanged() throws Exception {
        var generator = generator("\"missingRate\":0.25");
        var q = question(QuestionKind.TEXT, "", false, text(2, 7, null));
        var first = AnswerGeneratorTestSupport.draws(generator, q, TODAY, new Random(SEED), 20000);
        var second = AnswerGeneratorTestSupport.draws(generator("\"missingRate\":0.25"), q, TODAY, new Random(SEED), 20000);
        assertEquals(first, second);
        AnswerGeneratorTestSupport.assertRate((int) first.stream().filter(java.util.Objects::isNull).count(), 20000, .25, "missing rate");
    }
}
