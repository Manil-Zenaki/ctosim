package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoRankingTest {
    static Stream<Arguments> valueRanks() {
        return Stream.of(Arguments.of("5", "", 1.0), Arguments.of("5", "5", 1.0),
                Arguments.of("5", "6", 2.0), Arguments.of("5", "4", 1.0),
                Arguments.of("5", "5 5 6 6 4", 3.0), Arguments.of("5", "100 9 10 2", 4.0),
                Arguments.of("-2", "-3 -2 -1 0 2.5", 4.0),
                Arguments.of("1.5", "1.49 1.5 1.51", 2.0),
                Arguments.of("5", "  9   2  8  ", 3.0),
                Arguments.of("5", "bad 10 NaN 6 nope", 3.0),
                Arguments.of("5", "bad nope", 1.0),
                Arguments.of("0", "-1 0 0.1 bad", 2.0),
                Arguments.of("'bad'", "9 8 7", 999.0), Arguments.of("''", "", 999.0),
                Arguments.of("number('bad')", "1 2", 999.0),
                Arguments.of("'5'", "6 4", 2.0));
    }

    @ParameterizedTest @MethodSource("valueRanks")
    void rankValueCountsOnlyStrictlyGreaterNumericItems(String value, String list, double expected) throws Exception {
        String call = "rank-value(" + value + ", '" + list + "')";
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }

    @Test void rankValueAcceptsNumericFieldsAndComputedLists() throws Exception {
        String call = "rank-value(/data/number, concat('2000 ', '1234.5 12'))";
        assertValue(2.0, evaluate(expressionForm(call).newEntryController(), call));
    }

    static FormEntryController rankingSession(String... scores) throws Exception {
        StringBuilder rows = new StringBuilder();
        for (String score : scores) rows.append("<row><score>").append(attribute(score)).append("</score></row>");
        return load(fixture("ranking.xml").replace("__ROWS__", rows)).newEntryController();
    }

    static Stream<Arguments> indexRanks() {
        return Stream.of(Arguments.of(new String[]{"7"}, new double[]{1}),
                Arguments.of(new String[]{"10", "30", "20"}, new double[]{3, 1, 2}),
                Arguments.of(new String[]{"20", "20", "30", "20"}, new double[]{2, 3, 1, 4}),
                Arguments.of(new String[]{"7", "7", "7"}, new double[]{1, 2, 3}),
                Arguments.of(new String[]{"bad", "10", "", "20", "10"}, new double[]{999, 2, 999, 1, 3}),
                Arguments.of(new String[]{"bad", "", "NaN"}, new double[]{999, 999, 999}),
                Arguments.of(new String[]{"-5", "0", "-1.5", "10"}, new double[]{4, 2, 3, 1}),
                Arguments.of(new String[]{"2", "10", "9", "100"}, new double[]{4, 2, 3, 1}));
    }

    @ParameterizedTest @MethodSource("indexRanks")
    void rankIndexOrdersNumericallyDescendingBreaksTiesByPositionAndOmitsInvalidValues(String[] scores, double[] ranks) throws Exception {
        var session = rankingSession(scores);
        for (int i = 0; i < ranks.length; i++) {
            assertValue(ranks[i], evaluate(session, "rank-index(" + (i + 1) + ", /data/row/score)"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "4", "1000000000000", "1.5", "0.5", "'bad'", "''", "number('bad')"})
    void rankIndexReturns999ForNonIntegerOrOutOfRangePosition(String position) throws Exception {
        assertValue(999.0, evaluate(rankingSession("10", "20", "30"), "rank-index(" + position + ", /data/row/score)"));
    }

    @Test void rankIndexOnZeroRepeatInstancesReturns999() throws Exception {
        assertValue(999.0, evaluate(rankingSession(), "rank-index(1, /data/row/score)"));
    }

    @Test void rankIndexAcceptsComputedPositionAndRelativeRepeatedReference() throws Exception {
        var session = rankingSession("10", "30", "20");
        assertValue(1.0, evaluateAt(session, child(root(session), "rank"), "rank-index(1 + 1, ../row/score)"));
    }

    @Test void rankCalculationRecomputesWhenRepeatedScoreChanges() throws Exception {
        var session = rankingSession("10", "30", "20");
        assertEquals(3, value(child(root(session), "rank")));
        answer(session, child(root(session).getChild("row", 0), "score"), new StringData("40"));
        assertEquals(1, value(child(root(session), "rank")));
        assertValue(2.0, evaluate(session, "rank-index(2, /data/row/score)"));
    }

    @Test void rankCalculationRecomputesWhenPositionChanges() throws Exception {
        var session = rankingSession("10", "30", "20");
        answer(session, child(root(session), "position"), new IntegerData(2));
        assertEquals(1, value(child(root(session), "rank")));
    }
}
