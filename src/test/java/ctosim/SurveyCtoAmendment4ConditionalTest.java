package ctosim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.javarosa.core.model.data.StringData;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static ctosim.SurveyCtoAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment4ConditionalTest {
    static Stream<Arguments> oracleCases() {
        return Stream.of(Arguments.of("count", 2.0), Arguments.of("sum", 40.0),
                Arguments.of("min", 10.0), Arguments.of("max", 30.0),
                Arguments.of("join", "10|30"), Arguments.of("rank-index", 2.0));
    }

    @ParameterizedTest @MethodSource("oracleCases")
    void filtersEachInstanceInsteadOfEvaluatingTheConditionOnce(String function, Object expected) throws Exception {
        String call = conditional(function, ODD);
        var session = repeats(call, THREE).newEntryController();
        assertValue(expected, evaluate(session, call));
    }

    @ParameterizedTest @MethodSource("oracleCases")
    void formCalculationsAlsoWorkInSimulatedSessions(String function, Object expected) throws Exception {
        String call = conditional(function, ODD);
        var session = repeats(call, THREE).newSimulatedSession(
                new SimulatedClock(LocalDateTime.of(2026, 10, 11, 18, 0)), 319);
        String text = expected instanceof Double d ? Integer.toString(d.intValue()) : expected.toString();
        assertEquals("[" + text + "]", value(child(root(session), "result")));
    }

    @ParameterizedTest @ValueSource(strings = {"count", "sum", "min", "max", "join", "rank-index"})
    void implementedConditionalFunctionsAreNotReportedAsStubs(String function) throws Exception {
        var form = repeats(conditional(function, "true()"), THREE);
        assertFalse(form.stubbedFunctions().contains(function + "-if"));
    }

    static Stream<Arguments> nativeParityCases() {
        var cases = new ArrayList<Arguments>();
        for (String function : List.of("count", "sum", "min", "max", "join")) {
            for (String[] scores : List.of(new String[]{}, new String[]{"7"},
                    new String[]{"10", "20", "30"}, new String[]{"", "bad", "-2.5", "0", "10"})) {
                cases.add(Arguments.of(function, scores));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("nativeParityCases")
    void includingAllNodesPreservesNativeAggregationIncludingEmptyAndNonnumericValues(
            String function, String[] scores) throws Exception {
        String[][] rows = Stream.of(scores).map(s -> new String[]{s, "odd", s}).toArray(String[][]::new);
        var session = repeats("''", rows).newEntryController();
        Object expected = evaluate(session, nativeCall(function, ""));
        assertValue(expected, evaluate(session, conditional(function, "true()")));
    }

    @ParameterizedTest @ValueSource(strings = {"count", "sum", "min", "max", "join"})
    void noIncludedInstancesHaveTheNativeEmptyNodesetResult(String function) throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(evaluate(session, nativeCall(function, "[false()]")),
                evaluate(session, conditional(function, "false()")));
    }

    @ParameterizedTest @ValueSource(strings = {"sum", "min", "max", "join"})
    void excludedNonnumericNodesDoNotContaminateAggregation(String function) throws Exception {
        var session = repeats("''", new String[]{"bad", "even", "bad"},
                new String[]{"-2.5", "odd", "a"}, new String[]{"", "even", ""},
                new String[]{"7", "odd", "b"}).newEntryController();
        assertValue(evaluate(session, nativeCall(function, "[kind = 'odd']")),
                evaluate(session, conditional(function, ODD)));
    }

    static Stream<Arguments> predicateValues() {
        return Stream.of(Arguments.of("true()", 3.0), Arguments.of("false()", 0.0),
                Arguments.of("0", 0.0), Arguments.of("-2", 3.0), Arguments.of("0.5", 3.0),
                Arguments.of("number('bad')", 0.0), Arguments.of("''", 0.0),
                Arguments.of("'false'", 3.0), Arguments.of("'0'", 3.0), Arguments.of("' '", 3.0));
    }

    @ParameterizedTest @MethodSource("predicateValues")
    void convertsPredicateResultsWithXPathBooleanRules(String predicate, double expected) throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(expected, evaluate(session, conditional("count", predicate)));
    }

    @Test void predicateCanCombineInstanceFieldsAndAnExternalThreshold() throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(1.0, evaluate(session, conditional("count",
                "/data/row/kind = 'odd' and /data/row/score > /data/threshold")));
    }

    @Test void predicateCanReferToTheRepeatNodeItself() throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(2.0, evaluate(session, conditional("count", "boolean(/data/row[kind = 'odd'])")));
    }

    @Test void numericPredicateValuesAreConvertedSeparatelyForEachInstance() throws Exception {
        var session = repeats("''", new String[]{"-2", "odd", "a"}, new String[]{"0", "odd", "b"},
                new String[]{"0.5", "odd", "c"}, new String[]{"bad", "odd", "d"}).newEntryController();
        assertValue(2.0, evaluate(session, conditional("count", "number(/data/row/score)")));
    }

    @Test void stringPredicateValuesAreConvertedSeparatelyForEachInstance() throws Exception {
        var session = repeats("''", new String[]{"1", "", "a"}, new String[]{"2", "false", "b"},
                new String[]{"3", "yes", "c"}).newEntryController();
        assertValue(2.0, evaluate(session, conditional("count", "string(/data/row/kind)")));
    }

    @ParameterizedTest @ValueSource(strings = {"count", "sum", "min", "max", "join"})
    void callsInsideTheTargetRepeatUseTheSameNodesAsTheNativeFunction(String function) throws Exception {
        var session = repeats("''", THREE).newEntryController();
        for (int i = 0; i < 3; i++) {
            var context = child(root(session).getChild("row", i), "label");
            assertValue(evaluateAt(session, context, nativeCall(function, "")),
                    evaluateAt(session, context, conditional(function, "true()")));
        }
    }

    @Test void relativeReferencesResolveFromTheCallContextAndThenSelectEachInstance() throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue(40.0, evaluateAt(session, child(root(session), "result"),
                "sum-if(../row/score, ../row/kind = ../wanted)"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "::", " | ", "\u2603"})
    void joinUsesTheSuppliedSeparatorAndDocumentOrderWithDuplicateUnicodeValues(String separator) throws Exception {
        var session = repeats("''", new String[]{"2", "odd", "caf\u00e9"},
                new String[]{"99", "even", "excluded"}, new String[]{"1", "odd", "\u6771\u4eac"},
                new String[]{"2", "odd", "caf\u00e9"}).newEntryController();
        assertValue(String.join(separator, "caf\u00e9", "\u6771\u4eac", "caf\u00e9"), evaluate(session,
                "join-if('" + separator + "', /data/row/label, " + ODD + ")"));
    }

    @Test void separatorCanBeAnExpression() throws Exception {
        var session = repeats("''", THREE).newEntryController();
        assertValue("10::30", evaluate(session, "join-if(concat(':', ':'), /data/row/score, " + ODD + ")"));
    }

    @Test void predicateReevaluatesAfterAnExternalAnswerChanges() throws Exception {
        var session = repeats(conditional("sum", "/data/row/kind = /data/wanted"), THREE).newEntryController();
        assertEquals("[40]", value(child(root(session), "result")));
        answer(session, child(root(session), "wanted"), new StringData("even"));
        assertEquals("[20]", value(child(root(session), "result")));
    }

    @Test void predicateReevaluatesAfterARepeatedAnswerChanges() throws Exception {
        var session = repeats(conditional("sum", ODD), THREE).newEntryController();
        answer(session, child(root(session).getChild("row", 1), "kind"), new StringData("odd"));
        assertEquals("[60]", value(child(root(session), "result")));
    }

    @Test void aggregateRecalculatesAfterAnIncludedScoreChanges() throws Exception {
        var session = repeats(conditional("sum", ODD), THREE).newEntryController();
        answer(session, child(root(session).getChild("row", 0), "score"), new StringData("-10"));
        assertEquals("[20]", value(child(root(session), "result")));
    }

    @Test void sessionsDoNotShareTheirInclusionState() throws Exception {
        var form = repeats(conditional("count", "/data/row/kind = /data/wanted"), THREE);
        var first = form.newEntryController();
        var second = form.newEntryController();
        answer(first, child(root(first), "wanted"), new StringData("even"));
        assertEquals("[1]", value(child(root(first), "result")));
        assertEquals("[2]", value(child(root(second), "result")));
    }

    static Stream<Arguments> invalidArities() {
        List<Arguments> cases = new ArrayList<>();
        for (String fn : List.of("count", "sum", "min", "max", "join", "rank-index")) {
            List<String> args = switch (fn) {
                case "count" -> List.of("/data/row", "true()");
                case "join" -> List.of("' '", "/data/row/score", "true()");
                case "rank-index" -> List.of("1", "/data/row/score", "true()");
                default -> List.of("/data/row/score", "true()");
            };
            for (int size = 0; size <= args.size() + 2; size++) {
                if (size == args.size()) continue;
                var supplied = new ArrayList<String>();
                for (int i = 0; i < size; i++) supplied.add(i < args.size() ? args.get(i) : "'extra'");
                cases.add(Arguments.of(fn + "-if(" + String.join(", ", supplied) + ")"));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest @MethodSource("invalidArities")
    void wrongArityFailsDuringEvaluation(String call) throws Exception {
        var session = repeats("''", THREE).newEntryController();
        var expression = XPathParseTool.parseXPath(call);
        var form = session.getModel().getForm();
        assertThrows(Exception.class, () -> expression.eval(form.getMainInstance(), form.getEvaluationContext()));
    }
}
