package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoTestSupport.*;

class SurveyCtoListFunctionsTest {
    static Stream<Arguments> splittingCases() {
        return Stream.of(
                Arguments.of(",", "", List.of(), ""),
                Arguments.of(",", "a", List.of("a"), "a"),
                Arguments.of(",", "a,b,c", List.of("a", "b", "c"), "a,b,c"),
                Arguments.of(",", "a,,b", List.of("a", "", "b"), "a,,b"),
                Arguments.of(",", ",a,", List.of("", "a", ""), ",a"),
                Arguments.of(",", ",", List.of("", ""), ""),
                Arguments.of(",", ",,,", List.of("", "", "", ""), ""),
                Arguments.of(",", "b,a,b,c,a", List.of("b", "a", "b", "c", "a"), "b,a,c"),
                Arguments.of(",", "a, a,A,a ", List.of("a", " a", "A", "a "), "a, a,A,a "),
                Arguments.of("", "", List.of(), ""),
                Arguments.of("", "a,b", List.of("a,b"), "a,b"),
                Arguments.of("||", "a||||b||", List.of("a", "", "b", ""), "a||||b"),
                Arguments.of("::", "::x::x::", List.of("", "x", "x", ""), "::x"),
                Arguments.of("xyz", "xy", List.of("xy"), "xy"),
                Arguments.of(".", "a.b.a", List.of("a", "b", "a"), "a.b"),
                Arguments.of("|", "a|b|a", List.of("a", "b", "a"), "a|b"),
                Arguments.of("*", "a*b*a", List.of("a", "b", "a"), "a*b"),
                Arguments.of("[", "a[b[a", List.of("a", "b", "a"), "a[b"),
                Arguments.of("\\", "a\\b\\a", List.of("a", "b", "a"), "a\\b"),
                Arguments.of("💠", "東京💠café💠東京", List.of("東京", "café", "東京"), "東京💠café"),
                Arguments.of(" ", " a  b ", List.of("", "a", "", "b", ""), " a b"));
    }

    static String args(String sep, String list) { return "'" + sep + "', '" + list + "'"; }

    @ParameterizedTest(name = "count-items with separator [{0}] and list [{1}]") @MethodSource("splittingCases")
    void countIncludesEmptyItemsButEmptyListHasNoItems(String sep, String list, List<String> items, String deduped) throws Exception {
        String call = "count-items(" + args(sep, list) + ")";
        assertValue((double) items.size(), evaluate(expressionForm(call).newEntryController(), call));
    }

    static Stream<Arguments> itemCases() {
        return splittingCases().flatMap(row -> {
            Object[] data = row.get();
            @SuppressWarnings("unchecked") List<String> items = (List<String>) data[2];
            List<Arguments> cases = new ArrayList<>();
            for (int i = -1; i <= items.size(); i++) {
                cases.add(Arguments.of(data[0], data[1], Integer.toString(i),
                        i >= 0 && i < items.size() ? items.get(i) : ""));
            }
            return cases.stream();
        });
    }

    @ParameterizedTest(name = "item-at([{0}], [{1}], {2})") @MethodSource("itemCases")
    void itemAtUsesZeroBasedPositionsAndChecksBothBounds(String sep, String list, String position, String expected) throws Exception {
        String call = "item-at(" + args(sep, list) + ", " + position + ")";
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }

    static Stream<Arguments> invalidPositions() {
        return Stream.of("-0.5", "0.5", "1.5", "1000000000000", "number('bad')", "'bad'", "''")
                .map(n -> Arguments.of("item-at(',', 'a,b', " + n + ")"));
    }

    @ParameterizedTest @MethodSource("invalidPositions")
    void itemAtRejectsNonIntegerAndNonNumericPositions(String call) throws Exception {
        assertValue("", evaluate(expressionForm(call).newEntryController(), call));
    }

    static Stream<Arguments> searchCases() {
        return Stream.of(
                Arguments.of(",", "a,b,a", "a", 0.0), Arguments.of(",", "a,b,a", "b", 1.0),
                Arguments.of(",", "a,b,a", "c", -1.0), Arguments.of(",", "aa,b", "a", -1.0),
                Arguments.of(",", "A,a", "a", 1.0), Arguments.of(",", " a,a ", "a", -1.0),
                Arguments.of(",", "a,,b", "", 1.0), Arguments.of(",", "a,b,", "", 2.0),
                Arguments.of(",", ",,", "", 0.0), Arguments.of(",", "", "", -1.0),
                Arguments.of("", "", "", -1.0), Arguments.of("", "abc", "abc", 0.0),
                Arguments.of("", "abc", "a", -1.0), Arguments.of("::", "東京::café::東京", "東京", 0.0),
                Arguments.of(".", "a.b", "b", 1.0));
    }

    @ParameterizedTest @MethodSource("searchCases")
    void itemIndexReturnsFirstExactMatchOrMinusOne(String sep, String list, String item, double expected) throws Exception {
        String call = "item-index(" + args(sep, list) + ", '" + item + "')";
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }

    @ParameterizedTest @MethodSource("searchCases")
    void itemPresentUsesExactWholeItemsIncludingEmptyItems(String sep, String list, String item, double index) throws Exception {
        String call = "item-present(" + args(sep, list) + ", '" + item + "')";
        assertValue(index >= 0, evaluate(expressionForm(call).newEntryController(), call));
    }

    @ParameterizedTest @MethodSource("splittingCases")
    void deduplicationPreservesFirstOccurrenceOrderAndEmptyItems(String sep, String list, List<String> items, String expected) throws Exception {
        String call = "de-duplicate(" + args(sep, list) + ")";
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }

    static Stream<Arguments> expressionArguments() {
        return Stream.of(Arguments.of("count-items(' ', /data/many)", 2.0),
                Arguments.of("item-at(' ', /data/many, 1 - 1)", "a"),
                Arguments.of("item-index(' ', /data/many, /data/choice)", 0.0),
                Arguments.of("item-present(' ', /data/many, upper('b'))", false),
                Arguments.of("de-duplicate(concat(':', ':'), concat('a::', 'b::a'))", "a::b"),
                Arguments.of("count-items(',', /data/blank)", 0.0));
    }

    @ParameterizedTest @MethodSource("expressionArguments")
    void listFunctionsAcceptFieldsAndComputedArguments(String call, Object expected) throws Exception {
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }
}
