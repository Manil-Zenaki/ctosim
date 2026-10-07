package ctosim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.FormLoaderTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderFunctionTest {
    private static final List<String> STUBS = List.of("pulldata", "plug-in-metadata", "username", "duration",
            "phone-call-log", "phone-call-duration", "unimplemented-survey-function", "another-unknown-function");

    static Stream<Arguments> stubCalls() {
        List<String> arguments = List.of("", "'café 東京'", "42.5", "true()", "/data/answer",
                "/data/nonexistent", "date('2020-02-29')",
                "'text', -2.5, false(), /data/answer, /data/nonexistent, date('2020-02-29')",
                String.join(", ", java.util.Collections.nCopies(32, "'argument'")));
        return STUBS.stream().flatMap(name -> arguments.stream().map(args -> Arguments.of(name, args)));
    }

    @ParameterizedTest(name = "{0}({1}) has its specified value and type in every session")
    @MethodSource("stubCalls")
    void stubsAcceptZeroOneManyAndMixedTypeArguments(String function, String arguments) throws Exception {
        String call = function + "(" + arguments + ")";
        LoadedForm form = expressionForm("concat('[', " + call + ", ']')");
        Object expected = switch (function) {
            case "username" -> "simulated";
            case "duration", "phone-call-duration" -> 0.0;
            default -> "";
        };
        for (int i = 0; i < 2; i++) {
            FormEntryController session = form.newEntryController();
            Object actual = evaluate(session, call);
            if (expected instanceof Number) {
                assertInstanceOf(Number.class, actual, "The stub must return a number, not the string '0'");
                assertEquals(0.0, ((Number) actual).doubleValue());
            } else {
                assertInstanceOf(String.class, actual);
                assertEquals(expected, actual);
            }
            String formatted = expected instanceof Number ? "0" : expected.toString();
            assertEquals("[" + formatted + "]", value(session, "result"), "The form's calculate must also run");
        }
        assertEquals(Set.of(function), form.stubbedFunctions());
    }

    @Test void nestedStubsAreDiscoveredAndEvaluated() throws Exception {
        LoadedForm form = expressionForm("concat(username(pulldata('file', 'column')), ':', "
                + "duration(phone-call-log()), ':', unknown-outer(plug-in-metadata(), phone-call-duration()))");
        assertEquals("simulated:0:", value(form.newEntryController(), "result"));
        assertEquals(Set.of("username", "pulldata", "duration", "phone-call-log", "unknown-outer",
                "plug-in-metadata", "phone-call-duration"), form.stubbedFunctions());
    }

    @Test void onlyReferencedStubsAreReportedOnceInNaturalOrder() throws Exception {
        LoadedForm form = expressionForm("concat(zebra-probe(), username(), alpha-probe(), pulldata(), "
                + "username(), A-probe(), alpha-probe())");
        assertEquals(List.of("A-probe", "alpha-probe", "pulldata", "username", "zebra-probe"),
                new ArrayList<>(form.stubbedFunctions()));
    }

    @Test void functionsInUnchosenBranchesAreStillReferences() throws Exception {
        LoadedForm form = expressionForm("if(true(), 'chosen', hidden-probe(username()))");
        assertEquals(Set.of("hidden-probe", "username"), form.stubbedFunctions());
        assertEquals("chosen", value(form.newEntryController(), "result"));
    }

    @Test void whitespaceBeforeAnOpeningParenthesisDoesNotHideAFunction() throws Exception {
        LoadedForm form = expressionForm("concat(username (), white-space-probe ('x'))");
        assertEquals(Set.of("username", "white-space-probe"), form.stubbedFunctions());
        assertEquals("simulated", value(form.newEntryController(), "result"));
    }

    @Test void functionNamesInsideStringsLabelsDataAndCommentsAreNotReferences() throws Exception {
        LoadedForm form = loadFixture("lexical-decoys.xml");
        assertTrue(form.stubbedFunctions().isEmpty());
        assertEquals("pulldata()username()literal-only-function()", value(form.newEntryController(), "result"));
    }

    @Test void formWithoutFunctionReferencesHasAnEmptySet() throws Exception {
        assertTrue(loadFixture("minimal.xml").stubbedFunctions().isEmpty());
    }

    @Test void stubInventoriesDoNotLeakBetweenLoadedFormsOrChangeWhenSessionsOpen() throws Exception {
        LoadedForm a = expressionForm("username()");
        SortedSet<String> original = a.stubbedFunctions();
        LoadedForm b = expressionForm("other-form-probe()");
        LoadedForm plain = loadFixture("minimal.xml");
        b.newEntryController();
        a.newEntryController();
        assertEquals(Set.of("username"), original);
        assertEquals(Set.of("username"), a.stubbedFunctions());
        assertEquals(Set.of("other-form-probe"), b.stubbedFunctions());
        assertTrue(plain.stubbedFunctions().isEmpty());
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                Arguments.of("add", (Consumer<SortedSet<String>>) set -> set.add("new-probe")),
                Arguments.of("remove", (Consumer<SortedSet<String>>) set -> set.remove("username")),
                Arguments.of("clear", (Consumer<SortedSet<String>>) SortedSet::clear),
                Arguments.of("iterator.remove", (Consumer<SortedSet<String>>) set -> {
                    var iterator = set.iterator(); iterator.next(); iterator.remove();
                }),
                Arguments.of("headSet.clear", (Consumer<SortedSet<String>>) set -> set.headSet("username").clear()),
                Arguments.of("tailSet.clear", (Consumer<SortedSet<String>>) set -> set.tailSet("username").clear()),
                Arguments.of("subSet.clear", (Consumer<SortedSet<String>>) set -> set.subSet("pulldata", "z").clear()),
                Arguments.of("removeIf", (Consumer<SortedSet<String>>) set -> set.removeIf(s -> true)));
    }

    @ParameterizedTest(name = "stubbedFunctions rejects {0}") @MethodSource("mutations")
    void stubbedFunctionSetAndItsViewsCannotBeModified(String operation, Consumer<SortedSet<String>> mutation) throws Exception {
        LoadedForm form = expressionForm("concat(username(), pulldata())");
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(form.stubbedFunctions()));
        assertEquals(Set.of("pulldata", "username"), form.stubbedFunctions());
    }

    @Test void anEmptyStubbedFunctionSetCannotBePopulated() throws Exception {
        SortedSet<String> functions = loadFixture("minimal.xml").stubbedFunctions();
        assertThrows(UnsupportedOperationException.class, () -> functions.add("username"));
        assertTrue(functions.isEmpty());
    }

    private static final String INPUT = "<input ref=\"/data/answer\"><label>Answer</label></input>";

    static Stream<Arguments> expressionLocations() {
        List<Arguments> cases = new ArrayList<>();
        for (String attr : List.of("calculate", "relevant", "constraint", "required", "readonly")) {
            String name = "probe-" + attr;
            String expression = attr.equals("calculate") ? "concat('value:', " + name + "())" : name + "() = ''";
            cases.add(Arguments.of(attr, name, attr + "=\"" + expression + "\"", "", INPUT));
        }
        cases.add(Arguments.of("setvalue", "probe-action", "",
                "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" value=\"concat('set:', probe-action())\"/>", INPUT));
        cases.add(Arguments.of("body ref", "probe-ref", "", "",
                "<input ref=\"/data/answer[probe-ref() = '']\"><label>Answer</label></input>"));
        cases.add(Arguments.of("choice-filter nodeset", "probe-filter", "", "",
                "<select1 ref=\"/data/choice\"><label>Choice</label>"
                        + "<itemset nodeset=\"instance('options')/items/item[probe-filter() = '']\">"
                        + "<label ref=\"label\"/><value ref=\"name\"/></itemset></select1>"));
        cases.add(Arguments.of("itemset label ref", "probe-label-ref", "", "",
                "<select1 ref=\"/data/choice\"><label>Choice</label>"
                        + "<itemset nodeset=\"instance('options')/items/item\">"
                        + "<label ref=\"label[probe-label-ref() = '']\"/><value ref=\"name\"/></itemset></select1>"));
        return cases.stream();
    }

    static LoadedForm locationForm(String attributes, String actions, String body) throws Exception {
        return loadXml(fixtureText("location-template.xml").replace("__BIND_ATTRIBUTES__", attributes)
                .replace("__ACTIONS__", actions).replace("__BODY__", body), "locations.xml");
    }

    @ParameterizedTest(name = "Discovers a stub referenced only in {0}") @MethodSource("expressionLocations")
    void everySpecifiedExpressionLocationContributesToTheStubInventory(String location, String name,
            String attributes, String actions, String body) throws Exception {
        LoadedForm form = locationForm(attributes, actions, body);
        assertEquals(Set.of(name), form.stubbedFunctions());
        assertEquals("", evaluate(form.newEntryController(), name + "()"));
    }

    @Test void functionsInChoiceFiltersCanBeEvaluatedWhenChoicesAreRequested() throws Exception {
        LoadedForm form = locationForm("", "", "<select1 ref=\"/data/choice\"><label>Choice</label>"
                + "<itemset nodeset=\"instance('options')/items/item[filter-probe(name, true()) = '']\">"
                + "<label ref=\"label\"/><value ref=\"name\"/></itemset></select1>");
        FormEntryController session = form.newEntryController();
        moveToQuestion(session, "choice");
        var choices = session.getModel().getQuestionPrompt().getSelectChoices();
        assertEquals(1, choices.size());
        assertEquals("Alpha", session.getModel().getQuestionPrompt().getSelectChoiceText(choices.get(0)));
    }

    @Test void stubbedConstraintRunsWhenAnAnswerIsSubmitted() throws Exception {
        LoadedForm form = locationForm("constraint=\"constraint-probe(.) = ''\"", "", INPUT);
        FormEntryController session = form.newEntryController();
        moveToQuestion(session, "answer");
        assertEquals(ANSWER_OK, session.answerQuestion(new org.javarosa.core.model.data.StringData("accepted"), true));
    }

    @Test void setvalueActionsCanCallStubsInEachInitializedSession() throws Exception {
        LoadedForm form = locationForm("", "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" "
                + "value=\"concat('set:', username(), ':', action-probe())\"/>", INPUT);
        assertEquals("set:simulated:", value(form.newEntryController(), "result"));
        assertEquals("set:simulated:", value(form.newEntryController(), "result"));
        assertEquals(Set.of("username", "action-probe"), form.stubbedFunctions());
    }

    @Test void nativeFunctionsAreNotListedAsStubs() throws Exception {
        assertTrue(loadFixture("native-functions.xml").stubbedFunctions().isEmpty());
    }

    static Stream<Arguments> nativeCalls() {
        return Stream.of(Arguments.of("selected('a b', 'a')", true),
                Arguments.of("selected('a b', 'ab')", false),
                Arguments.of("count-selected('a b')", 2.0),
                Arguments.of("count-selected('')", 0.0),
                Arguments.of("if(true(), 'yes', 'no')", "yes"),
                Arguments.of("if(false(), 'yes', 'no')", "no"),
                Arguments.of("concat('café', ':', '東京')", "café:東京"),
                Arguments.of("jr:choice-name(/data/choice, '/data/choice')", "Alpha"));
    }

    @ParameterizedTest @MethodSource("nativeCalls")
    void nativeFunctionsKeepTheirStandardValues(String expression, Object expected) throws Exception {
        FormEntryController session = loadFixture("native-functions.xml").newEntryController();
        assertEquals(expected, evaluate(session, expression));
    }

    @Test void todayRemainsANativeDateAndIsNotReplacedWithAnEmptyString() throws Exception {
        LocalDate before = LocalDate.now();
        FormEntryController session = loadFixture("native-functions.xml").newEntryController();
        Object result = evaluate(session, "today()");
        LocalDate after = LocalDate.now();
        Date date = assertInstanceOf(Date.class, result);
        LocalDate actual = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertTrue(actual.equals(before) || actual.equals(after), "today() must remain the current local date");
    }

    @Test void nativeAndStubbedFunctionsWorkTogetherInACalculate() throws Exception {
        LoadedForm form = expressionForm("concat(if(selected('red blue', 'blue'), username(), 'wrong'), ':', "
                + "count-selected('red blue'), ':', duration() + 3, ':', pulldata('x'))");
        assertEquals("simulated:2:3:", value(form.newEntryController(), "result"));
        assertEquals(Set.of("username", "duration", "pulldata"), form.stubbedFunctions());
    }
}
