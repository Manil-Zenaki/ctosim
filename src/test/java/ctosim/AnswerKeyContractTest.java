package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AnswerKeyContractTest {
    private static final String HEADER = "KEY,enumerator,family,problem,check,field,value,clean_value,related_KEY,kind";
    private static InjectedProblem record(int n, String s, boolean deterministic) {
        return new InjectedProblem(n, s, s, s, s, s, s, s, s, s, deterministic);
    }
    @Test void emptyListGivesExactlyTheHeaderWithoutBomOrFinalNewline() {
        assertEquals(HEADER, AnswerKey.csv(List.of()));
    }
    @Test void columnsMapToTheRecordAndSubmissionNumberIsNotExported() {
        var p = new InjectedProblem(93, "uuid:k", "enumerator03", "duplicate", "resent", "ipacheckids",
                "household", "0007", "old", "uuid:m", true);
        assertEquals(HEADER + "\nuuid:k,enumerator03,duplicate,resent,ipacheckids,household,0007,old,uuid:m,deterministic",
                AnswerKey.csv(List.of(p)));
    }
    @Test void preservesSuppliedOrderAndDuplicateRowsAndLabelsStatisticalRecords() {
        var a = record(9, "a", false); var b = record(1, "b", true);
        assertEquals(HEADER + "\na,a,a,a,a,a,a,a,a,statistical\nb,b,b,b,b,b,b,b,b,deterministic"
                + "\na,a,a,a,a,a,a,a,a,statistical", AnswerKey.csv(List.of(a, b, a)));
    }
    @Test void emptyStringsRemainEmptyUnquotedCells() {
        assertEquals(HEADER + "\n,,,,,,,,,deterministic", AnswerKey.csv(List.of(record(1, "", true))));
    }
    @ParameterizedTest @ValueSource(strings = {"plain", " leading and trailing ", "\t", "éthiopie 東京 😀", "=1+1"})
    void ordinaryTextIsPreservedWithoutUnnecessaryQuotes(String text) {
        assertEquals(HEADER + "\n" + String.join(",", Collections.nCopies(9, text)) + ",statistical",
                AnswerKey.csv(List.of(record(1, text, false))));
    }
    @ParameterizedTest @ValueSource(strings = {"a,b", "say \"yes\"", "a\rb", "a\nb", "a\r\nb", "\",\r\n"})
    void everyTextColumnEscapesCommasQuotesAndEitherLineEnding(String text) {
        String escaped = "\"" + text.replace("\"", "\"\"") + "\"";
        assertEquals(HEADER + "\n" + String.join(",", Collections.nCopies(9, escaped)) + ",deterministic",
                AnswerKey.csv(List.of(record(1, text, true))));
    }
    @Test void nullListThrowsNullPointerException() { assertThrows(NullPointerException.class, () -> AnswerKey.csv(null)); }
    @Test void nullRecordAnywhereThrowsNullPointerException() {
        for (var list : List.of(Arrays.asList((InjectedProblem) null), Arrays.asList(record(1, "x", true), null),
                Arrays.asList(null, record(1, "x", true)))) {
            assertThrows(NullPointerException.class, () -> AnswerKey.csv(list));
        }
    }
    @Test void csvDoesNotReorderOrMutateTheInput() {
        var input = new ArrayList<>(List.of(record(2, "b", true), record(1, "a", false)));
        var copy = List.copyOf(input); String first = AnswerKey.csv(input);
        assertEquals(copy, input); assertEquals(first, AnswerKey.csv(input));
    }
}
