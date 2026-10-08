package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.DecimalData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoNumericSelectTestSupport.*;
import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoNumericSelectCalculationTest {
    static Stream<Arguments> numericExpressions() {
        return Stream.of(
                Arguments.of("selected(/data/number, '1')", "true"),
                Arguments.of("selected(/data/number, '2')", "false"),
                Arguments.of("selected(/data/arm, '1')", "true"),
                Arguments.of("selected(/data/decimal, '2.5')", "true"),
                Arguments.of("selected(/data/decimal, '2')", "false"),
                Arguments.of("selected(2, '1')", "false"),
                Arguments.of("selected(1, '01')", "false"),
                Arguments.of("selected(1, '1.0')", "false"),
                Arguments.of("selected(12, '2')", "false"),
                Arguments.of("selected(1, '')", "false"),
                Arguments.of("selected(0, '0')", "true"),
                Arguments.of("selected(-3.25, '-3.25')", "true"),
                Arguments.of("selected(2.50, '2.50')", "false"),
                Arguments.of("selected(2.50, '2.5')", "true"),
                Arguments.of("selected(1 + 1, '2')", "true"),
                Arguments.of("selected(if(true(), 1, 2), '1')", "true"),
                Arguments.of("selected(number('1'), '1')", "true"),
                Arguments.of("selected(/data/number, concat('', '1'))", "true"),
                Arguments.of("selected ( /data/number , '1' )", "true"),
                Arguments.of("count-selected(/data/number)", "1"),
                Arguments.of("count-selected(/data/decimal)", "1"),
                Arguments.of("count-selected(/data/arm)", "1"),
                Arguments.of("count-selected(0)", "1"),
                Arguments.of("count-selected(-3.25)", "1"),
                Arguments.of("count-selected(if(false(), 1, 2.5))", "1"),
                Arguments.of("selected-at(/data/number, 0)", "1"),
                Arguments.of("selected-at(/data/decimal, 0)", "2.5"),
                Arguments.of("selected-at(/data/arm, 0)", "1"),
                Arguments.of("selected-at(0, 0)", "0"),
                Arguments.of("selected-at(-3.25, 0)", "-3.25"),
                Arguments.of("selected-at(2.0, 0)", "2"),
                Arguments.of("selected-at(2147483647, 0)", "2147483647"),
                Arguments.of("selected-at(1 div 8, 0)", "0.125"),
                Arguments.of("selected-at(if(true(), 2.5, 1), 1 - 1)", "2.5"),
                Arguments.of("selected-at(1, 1)", ""),
                Arguments.of("selected(/data/blank, '1')", "false"),
                Arguments.of("count-selected(/data/blank)", "0"),
                Arguments.of("selected-at(/data/blank, 0)", ""),
                Arguments.of("selected(count-selected(/data/number), '1')", "true"),
                Arguments.of("count-selected(selected-at(/data/decimal, 0))", "1"),
                Arguments.of("selected-at(count-selected(/data/decimal), 0)", "1"),
                Arguments.of("selected(selected-at(/data/number, 0), '1')", "true"),
                Arguments.of("concat(selected(/data/number, '1'), ':', count-selected(/data/decimal), ':', selected-at(/data/arm, 0))", "true:1:1"));
    }

    @ParameterizedTest(name = "form calculate: {0} -> {1}") @MethodSource("numericExpressions")
    void calculationsUseXpathStringValueOfNumericFirstArguments(String expression, String expected) throws Exception {
        var session = calculation(expression).newEntryController();
        assertEquals("[" + expected + "]", value(child(root(session), "result")));
    }

    static Stream<Arguments> stringExpressions() {
        return Stream.of(
                Arguments.of("selected(/data/single, '1')", "true"),
                Arguments.of("selected(/data/single, '2')", "false"),
                Arguments.of("count-selected(/data/single)", "1"),
                Arguments.of("selected-at(/data/single, 0)", "1"),
                Arguments.of("selected(/data/many, '2.5')", "true"),
                Arguments.of("selected(/data/many, '2')", "false"),
                Arguments.of("count-selected(/data/many)", "2"),
                Arguments.of("selected-at(/data/many, 1)", "2.5"),
                Arguments.of("selected-at(/data/many, 2)", ""),
                Arguments.of("selected(/data/text, '01')", "true"),
                Arguments.of("selected(/data/text, '1')", "false"),
                Arguments.of("selected(/data/text, '東京')", "true"),
                Arguments.of("count-selected(/data/text)", "3"),
                Arguments.of("selected-at(/data/text, 0)", "01"),
                Arguments.of("selected-at(/data/text, 2)", "東京"),
                Arguments.of("selected('', '1')", "false"),
                Arguments.of("count-selected('')", "0"),
                Arguments.of("selected-at('', 0)", ""),
                Arguments.of("selected('11 12', '1')", "false"),
                Arguments.of("selected(string(/data/number), '1')", "true"),
                Arguments.of("count-selected(string(/data/number))", "1"),
                Arguments.of("selected-at(string(/data/decimal), 0)", "2.5"),
                Arguments.of("concat(\"selected(1, '1')\", ':', 'count-selected(2)', ':', 'selected-at(2.5, 0)')",
                        "selected(1, '1'):count-selected(2):selected-at(2.5, 0)"));
    }

    @ParameterizedTest(name = "existing string behavior: {0}") @MethodSource("stringExpressions")
    void stringSelectTextAndLiteralArgumentsKeepTheirBehavior(String expression, String expected) throws Exception {
        var session = calculation(expression).newEntryController();
        assertEquals("[" + expected + "]", value(child(root(session), "result")));
    }

    static Stream<Arguments> updates() {
        return Stream.of(
                Arguments.of("selected(/data/number, '1')", "true", "false", "true"),
                Arguments.of("count-selected(/data/number)", "1", "1", "1"),
                Arguments.of("selected-at(/data/number, 0)", "1", "2", "1"));
    }

    @ParameterizedTest @MethodSource("updates")
    void changingNumericAnswersRecalculatesOnlyTheSessionBeingEdited(String expression,
            String initial, String changed, String restored) throws Exception {
        var loaded = calculation(expression);
        var first = loaded.newEntryController();
        var second = loaded.newEntryController();
        assertEquals("[" + initial + "]", value(child(root(first), "result")));
        answer(first, child(root(first), "number"), new IntegerData(2));
        assertEquals("[" + changed + "]", value(child(root(first), "result")));
        assertEquals("[" + initial + "]", value(child(root(second), "result")));
        assertEquals("[" + initial + "]", value(child(root(loaded.newEntryController()), "result")));
        answer(first, child(root(first), "number"), new IntegerData(1));
        assertEquals("[" + restored + "]", value(child(root(first), "result")));
    }

    @Test void fillingAndClearingNumericAnswersUsesEmptyStringSemantics() throws Exception {
        var session = calculation("concat(selected(/data/blank, '0'), ':', count-selected(/data/blank), ':', selected-at(/data/blank, 0))").newEntryController();
        var blank = child(root(session), "blank");
        assertNull(blank.getValue());
        assertEquals("[false:0:]", value(child(root(session), "result")));
        answer(session, blank, new IntegerData(0));
        assertEquals("[true:1:0]", value(child(root(session), "result")));
        answer(session, blank, null);
        assertEquals("[false:0:]", value(child(root(session), "result")));
    }

    @Test void decimalAnswerChangesAreNotTruncatedToIntegers() throws Exception {
        var session = calculation("concat(selected(/data/decimal, '-3.25'), ':', count-selected(/data/decimal), ':', selected-at(/data/decimal, 0))").newEntryController();
        answer(session, child(root(session), "decimal"), new DecimalData(-3.25));
        assertEquals("[true:1:-3.25]", value(child(root(session), "result")));
    }

    @Test void calculatedStringFieldActuallyHoldsANumber() throws Exception {
        var session = calculation("selected-at(/data/arm, 0)").newEntryController();
        assertInstanceOf(IntegerData.class, child(root(session), "arm").getValue());
        assertEquals("[1]", value(child(root(session), "result")));
    }

    @Test void numericSelectFunctionsAreNotReportedAsStubs() throws Exception {
        var loaded = calculation("concat(selected(1, '1'), count-selected(2.5), selected-at(2, 0))");
        assertTrue(loaded.stubbedFunctions().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"selected(1, '1')", "count-selected(1)", "selected-at(2.5, 0)"})
    void expressionsParsedOutsideTheFormRetainNativeEvaluationBehavior(String expression) throws Exception {
        var session = calculation("'ready'").newEntryController();
        var form = session.getModel().getForm();
        var parsed = XPathParseTool.parseXPath(expression);
        Object nativeResult;
        try {
            nativeResult = parsed.eval(form.getMainInstance(), new EvaluationContext(form.getMainInstance()));
        } catch (Exception nativeFailure) {
            var actual = assertThrows(Exception.class, () -> evaluate(session, expression));
            assertEquals(nativeFailure.getClass(), actual.getClass());
            return;
        }
        assertValue(nativeResult, evaluate(session, expression));
    }
}
