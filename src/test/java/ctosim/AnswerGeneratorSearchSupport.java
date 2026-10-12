package ctosim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/** Independent finite-list oracles for spec 05, Amendment 2. */
final class AnswerGeneratorSearchSupport {
    static final LocalDate TODAY = LocalDate.of(2040, 3, 1);

    private AnswerGeneratorSearchSupport() { }

    static AnswerGenerator generator(String jsonMembers) throws ConfigException {
        return new AnswerGenerator(SimulationConfig.parse("{" + jsonMembers + "}", "search-values-test.json"));
    }

    static Question question(QuestionKind kind) {
        return question(kind, ConstraintHints.none());
    }

    static Question question(QuestionKind kind, ConstraintHints hints) {
        List<String> choices = kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE
                ? List.of("z", "a", "99", "é") : List.of();
        return new Question("answer", kind, choices, "", true, hints);
    }

    static List<List<String>> search(AnswerGenerator generator, Question question) {
        return generator.searchValues(question, TODAY);
    }

    static List<String> integers(long first, long last) {
        return LongStream.rangeClosed(first, last).mapToObj(Long::toString).toList();
    }

    static List<String> dates(LocalDate first, LocalDate last, QuestionKind kind) {
        return first.datesUntil(last.plusDays(1))
                .map(d -> d.toString() + (kind == QuestionKind.DATETIME ? "T12:00:00" : "")).toList();
    }

    static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    static List<String> decimalFallback() {
        List<String> values = new ArrayList<>(integers(-10_000, 10_000));
        for (int cents = -10_000; cents <= 10_000; cents++) {
            values.add(BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString());
        }
        return distinct(values);
    }

    static List<String> textFallback() {
        List<String> values = new ArrayList<>(integers(-10_000, 10_000));
        for (int n = 1; n <= 100; n++) {
            values.add("a".repeat(n));
            values.add("0".repeat(n));
            values.add("1".repeat(n));
        }
        return distinct(values);
    }

    static void assertLevel(List<String> expected, List<List<String>> levels, int index, int count) {
        assertNotNull(levels);
        assertEquals(count, levels.size(), "Number of search levels");
        assertEquals(expected, levels.get(index), "Complete ordered level " + (index + 1));
    }
}
