package ctosim;

import java.util.stream.Stream;
import org.javarosa.core.model.data.StringData;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoPulldataLookupTest extends SurveyCtoPulldataTestSupport {
    static Stream<Arguments> lookups() {
        return Stream.of(
                Arguments.of("name", "id", "a", "Alice"),
                Arguments.of("name", "id", "b", "Bob"),
                Arguments.of("other", "id", "a", "alternate-a"),
                Arguments.of("id", "name", "Bob", "b"),
                Arguments.of("id", "id", "a", "a"),
                Arguments.of("name", "id", "empty", ""),
                Arguments.of("name", "id", "missing", ""),
                Arguments.of("name", "id", " a ", "spaced key"),
                Arguments.of("name", "id", " a", ""),
                Arguments.of("name", "id", "A", "uppercase key"),
                Arguments.of("name", "id", "", "empty key"),
                Arguments.of("name", "id", "unicode", "Élodie 東京 😀"),
                Arguments.of("name", "id", "leading", "005"),
                Arguments.of(" spaced header ", "id", "a", " spaced value "),
                Arguments.of("spaced header", "id", "a", ""),
                Arguments.of("Name", "id", "a", ""),
                Arguments.of("name", "ID", "a", ""),
                Arguments.of("absent", "id", "a", ""),
                Arguments.of("name", "absent", "", ""),
                Arguments.of("", "id", "a", ""),
                Arguments.of("name", "", "a", ""));
    }

    @ParameterizedTest(name = "column={0}, key column={1}, key=[{2}]") @MethodSource("lookups")
    void returnsTheFirstExactMatchAsAStringOrEmpty(String column, String keyColumn, String key, String expected)
            throws Exception {
        attach("people", "people.csv");
        assertLookup(loadMedia().newEntryController(), call("people", column, keyColumn, key), expected);
    }

    static Stream<Arguments> xpathKeys() {
        return Stream.of(Arguments.of("5", "whole number"), Arguments.of("2 + 3", "whole number"),
                Arguments.of("/data/number", "whole number"), Arguments.of("5.5", "fraction"),
                Arguments.of("0", "zero"), Arguments.of("-5", "negative"),
                Arguments.of("true()", "boolean true"), Arguments.of("false()", "boolean false"),
                Arguments.of("/data/blank", "empty key"), Arguments.of("concat('a', '')", "Alice"));
    }

    @ParameterizedTest @MethodSource("xpathKeys")
    void convertsKeyExpressionsUsingXpathStringSpelling(String key, String expected) throws Exception {
        attach("people", "people.csv");
        assertLookup(loadMedia().newEntryController(), "pulldata('people', 'name', 'id', " + key + ")", expected);
    }

    @Test
    void convertsAllFourNumericArgumentsWithoutDecimalSuffixes() throws Exception {
        attach("5", "numeric.csv");
        assertLookup(loadMedia().newEntryController(), "pulldata(2 + 3, 2, 3, 5)", "numeric arguments");
    }

    @Test
    void sourceAndColumnExpressionsCanBeComputedAndReadFromFields() throws Exception {
        attach("people", "people.csv");
        assertLookup(loadMedia().newEntryController(),
                "pulldata(concat('peo', 'ple'), /data/column, /data/keyColumn, /data/key)", "Alice");
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "simulated", "properties"})
    void calculatesInEverySessionAndRecalculatesForEveryReferencedArgument(String kind) throws Exception {
        attach("people", "people.csv");
        attach("second", "people.csv");
        calculate("pulldata(/data/source, /data/column, /data/keyColumn, /data/key)");
        var loaded = loadMedia();
        var first = session(loaded, kind);
        var second = session(loaded, kind);
        assertEquals("[Alice]", value(child(root(first), "result")));
        answer(first, child(root(first), "key"), new StringData("b"));
        assertEquals("[Bob]", value(child(root(first), "result")));
        answer(first, child(root(first), "column"), new StringData("other"));
        assertEquals("[alternate-b]", value(child(root(first), "result")));
        answer(first, child(root(first), "keyColumn"), new StringData("name"));
        assertEquals("[]", value(child(root(first), "result")));
        answer(first, child(root(first), "key"), new StringData("Bob"));
        assertEquals("[alternate-b]", value(child(root(first), "result")));
        answer(first, child(root(first), "source"), new StringData("missing"));
        assertEquals("[]", value(child(root(first), "result")));
        answer(first, child(root(first), "source"), new StringData("second"));
        assertEquals("[alternate-b]", value(child(root(first), "result")));
        assertEquals("[Alice]", value(child(root(second), "result")));
        assertEquals("[Alice]", value(child(root(session(loaded, kind)), "result")));
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "simulated", "properties"})
    void firstLoadSetvalueCanReadAttachedData(String kind) throws Exception {
        attach("people", "people.csv");
        writeForm("", "", "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" "
                + "value=\"pulldata('people', 'name', 'id', 'a')\"/>", "");
        var loaded = loadMedia();
        for (int i = 0; i < 2; i++) assertEquals("Alice", value(child(root(session(loaded, kind)), "result")));
    }

    @ParameterizedTest @ValueSource(strings = {"relevant", "required", "readonly"})
    void booleanBindingsReadAttachedDataAndFollowKeyChanges(String site) throws Exception {
        attach("people", "people.csv");
        writeForm("", site + "=\"pulldata('people', 'name', 'id', /data/key) = 'Alice'\"", "", "");
        var session = loadMedia().newEntryController();
        var target = child(root(session), "target");
        assertTrue(switch (site) {
            case "relevant" -> target.isRelevant();
            case "required" -> target.isRequired();
            default -> !target.isEnabled();
        });
        answer(session, child(root(session), "key"), new StringData("b"));
        assertFalse(switch (site) {
            case "relevant" -> target.isRelevant();
            case "required" -> target.isRequired();
            default -> !target.isEnabled();
        });
    }

    @Test
    void constraintLooksUpTheCandidateInTheBoundNodeContext() throws Exception {
        attach("people", "people.csv");
        writeForm("", "constraint=\"pulldata('people', 'name', 'id', .) = 'Alice'\"", "", "");
        var session = loadMedia().newEntryController();
        var target = child(root(session), "target");
        var form = session.getModel().getForm();
        assertTrue(form.evaluateConstraint(target.getRef(), new StringData("a")));
        assertFalse(form.evaluateConstraint(target.getRef(), new StringData("b")));
    }

    @ParameterizedTest @ValueSource(strings = {"../key", "/data/row/key"})
    void repeatFieldArgumentsUseTheirOwnInstanceAndRecalculateIndependently(String reference) throws Exception {
        attach("people", "people.csv");
        java.nio.file.Files.writeString(xform, resource("repeats.xml").replace("__KEY__", reference));
        var session = loadMedia().newEntryController();
        var first = root(session).getChild("row", 0);
        var second = root(session).getChild("row", 1);
        assertEquals("[Alice]", value(child(first, "result")));
        assertEquals("[Bob]", value(child(second, "result")));
        answer(session, child(first, "key"), new StringData("unicode"));
        assertEquals("[Élodie 東京 😀]", value(child(first, "result")));
        assertEquals("[Bob]", value(child(second, "result")));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 5, 8})
    void rejectsEverySmallerArityAndExcessArgumentsAtEvaluation(int count) throws Exception {
        attach("people", "people.csv");
        String[] valid = {"'people'", "'name'", "'id'", "'a'"};
        var arguments = new java.util.ArrayList<String>();
        for (int i = 0; i < count; i++) arguments.add(i < 4 ? valid[i] : "'extra'");
        var expression = XPathParseTool.parseXPath("pulldata(" + String.join(",", arguments) + ")");
        var form = loadMedia().newEntryController().getModel().getForm();
        assertThrows(Exception.class, () -> expression.eval(form.getMainInstance(), form.getEvaluationContext()));
    }

    @Test
    void pulldataIsNotReportedAsStubbedEvenWhenItsFileIsMissing() throws Exception {
        calculate("concat(pulldata('absent', 'name', 'id', 'a'), username(), plug-in-metadata('x'))");
        assertEquals(new java.util.TreeSet<>(java.util.List.of("plug-in-metadata", "username")),
                loadMedia().stubbedFunctions());
    }
}
