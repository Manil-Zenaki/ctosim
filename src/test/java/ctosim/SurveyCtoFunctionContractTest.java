package ctosim;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoFunctionContractTest {
    record Function(String name, String arguments, int arity) {
        String call() { return name + "(" + arguments + ")"; }
    }

    static final List<Function> FUNCTIONS = List.of(
            new Function("index", "", 0), new Function("choice-label", "/data/choice, 'a'", 2),
            new Function("relevant", "/data/text", 1), new Function("empty", "''", 1),
            new Function("rank-value", "2, '1 2 3'", 2), new Function("rank-index", "1, /data/row/score", 2),
            new Function("count-items", "',', 'a,b'", 2), new Function("item-at", "',', 'a,b', 0", 3),
            new Function("item-index", "',', 'a,b', 'a'", 3), new Function("item-present", "',', 'a,b', 'a'", 3),
            new Function("de-duplicate", "',', 'a,b,a'", 2), new Function("linebreak", "", 0),
            new Function("lower", "'ABC'", 1), new Function("upper", "'abc'", 1),
            new Function("format-number", "1234.5", 1), new Function("hash", "'a'", -1),
            new Function("distance-between", "'0 0', '0 1'", 2), new Function("short-geopoint", "'0 0 1'", 1),
            new Function("geo-scatter", "'0 0', 10", 2), new Function("enumerator-name", "", 0),
            new Function("enumerator-id", "", 0), new Function("device-info", "", 0),
            new Function("collect-is-phone-app", "", 0));

    static Stream<Arguments> invalidArities() {
        return FUNCTIONS.stream().flatMap(fn -> {
            IntStream counts = fn.arity() == -1 ? IntStream.of(0)
                    : IntStream.concat(IntStream.range(0, fn.arity()), IntStream.of(fn.arity() + 1, fn.arity() + 3));
            List<String> validArguments = fn.arguments().isEmpty() ? List.of() : List.of(fn.arguments().split(", "));
            return counts.mapToObj(n -> {
                // Keep the accepted prefix well-typed, so an implementation that ignores excess
                // arguments cannot accidentally pass because an unrelated argument is invalid.
                List<String> arguments = new java.util.ArrayList<>(validArguments.subList(0, Math.min(n, validArguments.size())));
                while (arguments.size() < n) arguments.add("'extra'");
                return Arguments.of(fn.name(), fn.call(), fn.name() + "(" + String.join(",", arguments) + ")");
            });
        });
    }

    @ParameterizedTest(name = "{0} rejects {2}") @MethodSource("invalidArities")
    void everyFunctionRejectsUnsupportedArgumentCountsAtEvaluation(String name, String validCall, String invalidCall) throws Exception {
        // Reference the function without evaluating it during initialization. Parse outside assertThrows
        // so a syntax error or a failure to initialize cannot masquerade as correct arity validation.
        var loaded = name.equals("rank-index")
                ? load(fixture("ranking.xml").replace("__ROWS__", "<row><score>10</score></row>"))
                : expressionForm("if(false(), " + validCall + ", '')");
        var session = loaded.newEntryController();
        var parsed = XPathParseTool.parseXPath(invalidCall);
        var form = session.getModel().getForm();
        assertThrows(Exception.class, () -> parsed.eval(form.getMainInstance(), form.getEvaluationContext()));
    }

    static Stream<Arguments> implementedNames() {
        return FUNCTIONS.stream().map(fn -> Arguments.of(fn.name(), fn.call()));
    }

    @ParameterizedTest @MethodSource("implementedNames")
    void implementedFunctionsAreAbsentFromStubInventoryEvenInUnchosenBranches(String name, String call) throws Exception {
        var loaded = expressionForm("if(false(), " + call + ", 'safe')");
        assertTrue(loaded.stubbedFunctions().isEmpty(), () -> "Implemented function listed as a stub: " + name);
    }

    static Stream<Arguments> unchangedStubs() {
        return Stream.of(Arguments.of("pulldata", ""), Arguments.of("plug-in-metadata", ""),
                Arguments.of("username", "simulated"), Arguments.of("duration", 0.0),
                Arguments.of("phone-call-log", ""), Arguments.of("phone-call-duration", 0.0),
                Arguments.of("count-if", ""), Arguments.of("sum-if", ""), Arguments.of("join-if", ""),
                Arguments.of("min-if", ""), Arguments.of("max-if", ""), Arguments.of("rank-index-if", ""),
                Arguments.of("unknown-spec03-probe", ""));
    }

    @ParameterizedTest @MethodSource("unchangedStubs")
    void originalFixedStubsAndUnimplementedConditionalFunctionsKeepTheirValuesAndInventory(String name, Object expected) throws Exception {
        String call = name + "('data', 1, true(), /data/text)";
        var loaded = expressionForm("concat(lower('ABC'), " + call + ")");
        assertEquals(Set.of(name), loaded.stubbedFunctions());
        assertValue(expected, evaluate(loaded.newEntryController(), call));
    }

    @Test void nativeFunctionsStillComposeWithSurveyCtoFunctions() throws Exception {
        String expression = "concat(if(selected('a b', 'b'), upper('yes'), lower('NO')), ':', "
                + "count-selected('a b'), ':', jr:choice-name(/data/choice, '/data/choice'))";
        var loaded = expressionForm(expression);
        assertTrue(loaded.stubbedFunctions().isEmpty());
        assertEquals("YES:2:Alpha", evaluate(loaded.newEntryController(), expression));
    }
}
