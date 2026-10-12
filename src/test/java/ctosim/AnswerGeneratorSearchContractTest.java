package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.AnswerGeneratorSearchSupport.*;
import static ctosim.ConstraintHintsAmendment2Support.hints;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorSearchContractTest {
    private static Question searchableQuestion(QuestionKind kind, boolean required) {
        var base = question(kind, hints().candidates("candidate", "candidate").prefix("pré").build());
        return new Question(base.name(), base.kind(), base.choices(), base.appearance(), required, base.hints());
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class,
            names = {"GEOPOINT", "GEOTRACE", "GEOSHAPE", "IMAGE", "AUDIO", "VIDEO", "FILE", "ACKNOWLEDGE"})
    void unlistedKindsHaveNoSearchLevelEvenWithCandidatesAndFixedOverrides(QuestionKind kind) throws Exception {
        assertEquals(List.of(), search(generator("\"fields\":{\"answer\":{\"value\":\"fixed\"}}"),
                searchableQuestion(kind, true)));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "DATETIME",
            "TIME", "SELECT_ONE", "SELECT_MULTIPLE", "TEXT", "BARCODE"})
    void everySearchLevelIsUnmodifiableAndContainsOnlyDistinctNonNullStrings(QuestionKind kind) throws Exception {
        var levels = search(generator(""), searchableQuestion(kind, true));
        assertFalse(levels.isEmpty());
        for (var level : levels) {
            assertFalse(level.isEmpty(), "This fixture has values for every advertised level");
            assertTrue(level.stream().allMatch(java.util.Objects::nonNull));
            assertEquals(level.size(), new HashSet<>(level).size());
            assertThrows(UnsupportedOperationException.class, () -> level.add("changed"));
            assertThrows(UnsupportedOperationException.class, () -> level.set(0, "changed"));
            assertThrows(UnsupportedOperationException.class, () -> level.remove(0));
        }
    }

    @ParameterizedTest @EnumSource
    void fixedValueGlobalMissingRateAndFieldMissingRateDoNotChangeSearch(QuestionKind kind) throws Exception {
        var expected = search(generator(""), searchableQuestion(kind, true));
        var q = searchableQuestion(kind, false);
        assertEquals(expected, search(generator("\"missingRate\":1"), q), "global missing rate");
        assertEquals(expected, search(generator("\"fields\":{\"answer\":{\"missingRate\":1}}"), q), "field missing rate");
        assertEquals(expected, search(generator("\"fields\":{\"answer\":{\"value\":\"fixed\"}}"), q), "fixed value");
        assertEquals(expected, search(generator("\"missingRate\":1,\"fields\":{\"answer\":{\"value\":\"\",\"missingRate\":1}}"), q),
                "fixed empty string and missing rates");
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"TEXT", "BARCODE"})
    void textsOverrideIsIgnoredWithoutSuppressingHintCandidatesAndPrefixes(QuestionKind kind) throws Exception {
        var q = searchableQuestion(kind, false);
        assertEquals(search(generator(""), q), search(generator(
                "\"fields\":{\"answer\":{\"texts\":[\"override\",\"\"],\"missingRate\":1}}"), q));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "DATETIME",
            "TIME", "SELECT_ONE", "SELECT_MULTIPLE", "TEXT", "BARCODE"})
    void searchIsDeterministicAcrossInstancesCallsAndInterveningGeneration(QuestionKind kind) throws Exception {
        var first = generator("");
        var second = generator("");
        var q = searchableQuestion(kind, true);
        var expected = search(first, q);
        assertEquals(expected, search(second, q));
        assertEquals(expected, search(first, q));
        for (int i = 0; i < 5; i++) first.generate(question(QuestionKind.INTEGER), TODAY, new Random(i));
        search(first, question(QuestionKind.DECIMAL));
        assertEquals(expected, search(first, q));
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"INTEGER", "DECIMAL", "DATE", "DATETIME",
            "TIME", "SELECT_ONE", "SELECT_MULTIPLE", "TEXT", "BARCODE"})
    @ResourceLock("java.util.Locale.default")
    void searchFormattingDoesNotDependOnDefaultLocale(QuestionKind kind) throws Exception {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.US);
            var expected = search(generator("\"decimalRange\":{\"min\":-2.25,\"max\":3.75}"), searchableQuestion(kind, true));
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                assertEquals(expected, search(generator("\"decimalRange\":{\"min\":-2.25,\"max\":3.75}"),
                        searchableQuestion(kind, true)));
            }
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }
}
