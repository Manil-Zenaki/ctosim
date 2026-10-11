package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.IntegerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static ctosim.SurveyCtoAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment4RankTest {
    static Stream<Arguments> rankings() {
        return Stream.of(
                Arguments.of(new String[]{"10", "20", "30"}, new String[]{"odd", "even", "odd"}, new double[]{2, 999, 1}),
                Arguments.of(new String[]{"100", "20", "20", "20"}, new String[]{"even", "odd", "even", "odd"}, new double[]{999, 1, 999, 2}),
                Arguments.of(new String[]{"bad", "10", "", "20", "10", "NaN"}, new String[]{"odd", "odd", "odd", "odd", "odd", "odd"}, new double[]{999, 2, 999, 1, 3, 999}),
                Arguments.of(new String[]{"bad", "", "NaN"}, new String[]{"odd", "odd", "odd"}, new double[]{999, 999, 999}),
                Arguments.of(new String[]{"2", "10", "9", "100"}, new String[]{"odd", "odd", "odd", "even"}, new double[]{3, 1, 2, 999}),
                Arguments.of(new String[]{"-5", "0", "-1.5", "10"}, new String[]{"odd", "odd", "odd", "even"}, new double[]{3, 1, 2, 999}),
                Arguments.of(new String[]{"7"}, new String[]{"odd"}, new double[]{1}),
                Arguments.of(new String[]{"7"}, new String[]{"even"}, new double[]{999}));
    }

    @ParameterizedTest @MethodSource("rankings")
    void ranksOnlyIncludedNumericScoresButPositionsCountEveryInstance(String[] scores, String[] kinds, double[] expected) throws Exception {
        String[][] rows = new String[scores.length][];
        for (int i = 0; i < scores.length; i++) rows[i] = new String[]{scores[i], kinds[i], "row"};
        for (int i = 0; i < expected.length; i++) {
            var session = repeats("rank-index-if(" + (i + 1) + ", /data/row/score, " + ODD + ")", rows)
                    .newEntryController();
            assertEquals("[" + (int) expected[i] + "]", value(child(root(session), "result")),
                    "Rank at original position " + (i + 1));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "4", "999999999999", "1.5", "0.5", "'bad'", "''", "number('bad')"})
    void invalidPositionReturns999(String position) throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(999.0, evaluate(session, "rank-index-if(" + position + ", /data/row/score, true())"));
    }

    @Test void emptyRepeatReturns999() throws Exception {
        assertValue(999.0, evaluate(repeats("''").newEntryController(),
                "rank-index-if(1, /data/row/score, true())"));
    }

    @Test void noIncludedInstanceReturns999ForEveryOriginalPosition() throws Exception {
        var session = repeats("''", THREE).newEntryController();
        for (int i = 1; i <= 3; i++) assertValue(999.0, evaluate(session,
                "rank-index-if(" + i + ", /data/row/score, false())"));
    }

    @Test void positionExpressionRecalculatesWithoutRenumberingIncludedInstances() throws Exception {
        var session = repeats("rank-index-if(/data/position, /data/row/score, " + ODD + ")", THREE).newEntryController();
        assertEquals("[2]", value(child(root(session), "result")));
        answer(session, child(root(session), "position"), new IntegerData(2));
        assertEquals("[999]", value(child(root(session), "result")));
        answer(session, child(root(session), "position"), new IntegerData(3));
        assertEquals("[1]", value(child(root(session), "result")));
    }
}
