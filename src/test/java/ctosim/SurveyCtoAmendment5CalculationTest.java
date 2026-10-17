package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.DecimalData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.StringData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoAmendment5Support.*;
import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment5CalculationTest {
    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of("selected('1 3 4', /data/number)", true),
                Arguments.of("selected('1 2 4', /data/number)", false),
                Arguments.of("selected(/data/many, /data/number)", true),
                Arguments.of("selected(/data/single, /data/number)", true),
                Arguments.of("selected('1 3 4', 3)", true),
                Arguments.of("selected('1 3 4', 2)", false),
                Arguments.of("selected('1 3 4', 4)", true),
                Arguments.of("selected('1 3 4', 1)", true),
                Arguments.of("selected('3', 3.0)", true),
                Arguments.of("selected('3.0', 3)", false),
                Arguments.of("selected('03', 3)", false),
                Arguments.of("selected('13 30 43', 3)", false),
                Arguments.of("selected('0', 0)", true),
                Arguments.of("selected('', 0)", false),
                Arguments.of("selected('00', 0)", false),
                Arguments.of("selected('-3 3', -3)", true),
                Arguments.of("selected('3', -3)", false),
                Arguments.of("selected('1 2.5', /data/decimal)", true),
                Arguments.of("selected('2', /data/decimal)", false),
                Arguments.of("selected('2.50', /data/decimal)", false),
                Arguments.of("selected('2.5', 2.50)", true),
                Arguments.of("selected('-3.25', -3.25)", true),
                Arguments.of("selected('0.125', 1 div 8)", true),
                Arguments.of("selected('2147483647', 2147483647)", true),
                Arguments.of("selected('3', 1 + 2)", true),
                Arguments.of("selected('3', number('03'))", true),
                Arguments.of("selected('3', if(true(), 3, 2))", true),
                Arguments.of("selected('3', if(false(), 3, 2))", false),
                Arguments.of("selected ('3', (1 + 2))", true),
                Arguments.of("selected(3, 3)", true),
                Arguments.of("selected(3, 2)", false),
                Arguments.of("selected(/data/decimal, /data/decimal)", true),
                Arguments.of("selected('1 3 4', /data/blank)", false),
                Arguments.of("selected(/data/blank, 3)", false),
                Arguments.of("selected('true false', true())", true),
                Arguments.of("selected('true', false())", false),
                Arguments.of("selected('3', count-selected(/data/many))", true),
                Arguments.of("selected('3', if(selected('2', 2), 3, 4))", true),
                Arguments.of("selected(if(selected('3', 3), '2 4', '1'), 4)", true),
                Arguments.of("selected('3', '3')", true),
                Arguments.of("selected('3', '03')", false),
                Arguments.of("selected('03', /data/text)", true),
                Arguments.of("selected('3', /data/text)", false),
                Arguments.of("selected('3', string(/data/number))", true),
                Arguments.of("selected('3', concat('', '3'))", true),
                Arguments.of("selected('東京 café', '東京')", true),
                Arguments.of("selected('Café', 'café')", false),
                Arguments.of("selected('1 3 4', '')", false));
    }

    @ParameterizedTest(name = "calculate {0} = {1}") @MethodSource("selections")
    void secondArgumentUsesXpathStringValueWithoutChangingTokenMatching(String expression, boolean expected)
            throws Exception {
        var entry = calculation(expression).newEntryController();
        assertEquals("[" + expected + "]", value(child(root(entry), "result")));
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "simulated", "properties"})
    void secondArgumentConversionWorksInEverySessionApi(String kind) throws Exception {
        var loaded = calculation("selected(/data/many, /data/number)");
        for (int i = 0; i < 2; i++) {
            var entry = session(loaded, kind);
            assertEquals("[true]", value(child(root(entry), "result")));
            answer(entry, child(root(entry), "number"), new IntegerData(2));
            assertEquals("[false]", value(child(root(entry), "result")));
        }
    }

    @Test void changesToNumericSecondArgumentRecalculateWithoutLeakingAcrossSessions() throws Exception {
        var loaded = calculation("selected(/data/many, /data/number)");
        var first = loaded.newEntryController();
        var other = loaded.newEntryController();
        answer(first, child(root(first), "number"), new IntegerData(2));
        assertEquals("[false]", value(child(root(first), "result")));
        assertEquals("[true]", value(child(root(other), "result")));
        assertEquals("[true]", value(child(root(loaded.newEntryController()), "result")));
        answer(first, child(root(first), "number"), new IntegerData(4));
        assertEquals("[true]", value(child(root(first), "result")));
    }

    @Test void fillingAndClearingSecondArgumentDistinguishesZeroFromEmpty() throws Exception {
        var entry = calculation("selected('0 3', /data/blank)").newEntryController();
        var blank = child(root(entry), "blank");
        assertNull(blank.getValue());
        assertEquals("[false]", value(child(root(entry), "result")));
        answer(entry, blank, new IntegerData(0));
        assertEquals("[true]", value(child(root(entry), "result")));
        answer(entry, blank, null);
        assertEquals("[false]", value(child(root(entry), "result")));
    }

    @Test void decimalEditsRetainFractionAndSign() throws Exception {
        var entry = calculation("selected('2.5 -3.25', /data/decimal)").newEntryController();
        answer(entry, child(root(entry), "decimal"), new DecimalData(-3.25));
        assertEquals("[true]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "decimal"), new DecimalData(-3));
        assertEquals("[false]", value(child(root(entry), "result")));
    }

    @Test void firstArgumentChangesStillRecalculateWithANumericSecondArgument() throws Exception {
        var entry = calculation("selected(/data/text, /data/number)").newEntryController();
        assertEquals("[false]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "text"), new StringData("1 3 4"));
        assertEquals("[true]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "text"), null);
        assertEquals("[false]", value(child(root(entry), "result")));
    }

    @Test void numericCalculatedFieldIsConvertedEvenWhenDeclaredAsString() throws Exception {
        var entry = calculation("selected('1 3 4', /data/calculated)").newEntryController();
        assertInstanceOf(IntegerData.class, child(root(entry), "calculated").getValue());
        assertEquals("[true]", value(child(root(entry), "result")));
        answer(entry, child(root(entry), "number"), new IntegerData(2));
        assertEquals("[false]", value(child(root(entry), "result")));
    }

    @Test void functionLookingTextInsideXpathStringsIsNotRewritten() throws Exception {
        var entry = calculation("concat(\"selected('1 3 4', 3)\", ':', selected('3', 3))")
                .newEntryController();
        assertEquals("[selected('1 3 4', 3):true]", value(child(root(entry), "result")));
    }

    @Test void numericSecondArgumentDoesNotMakeSelectedAStub() throws Exception {
        assertTrue(calculation("selected('1 3 4', 3)").stubbedFunctions().isEmpty());
    }
}
