package ctosim;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoMissingAttachmentsTest extends SurveyCtoPulldataTestSupport {
    @Test
    void missingLiteralSourcesAreUniqueAndNaturallySortedWithoutCsvSuffixes() throws Exception {
        attach("present", "people.csv");
        calculate("concat(" + call("zebra", "name", "id", "a") + ","
                + call("alpha", "name", "id", "a") + "," + call("Zebra", "name", "id", "a") + ","
                + call("école", "name", "id", "a") + "," + call("alpha", "other", "id", "b") + ","
                + call("present", "name", "id", "a") + ")");
        assertEquals(List.of("Zebra", "alpha", "zebra", "école"), new ArrayList<>(loadMedia().missingAttachments()));
    }

    @ParameterizedTest @ValueSource(strings = {"'literal'", "\"literal\"", "  'literal'  "})
    void eitherXpathQuoteStyleAndWhitespaceAroundALiteralAreRecognized(String source) throws Exception {
        calculate("pulldata (" + source + ", 'name', 'id', 'a')");
        assertEquals(Set.of("literal"), loadMedia().missingAttachments());
    }

    @ParameterizedTest @ValueSource(strings = {"", " name with spaces ", "a,b(c)", "source.csv", "farmer's data"})
    void inventoryRetainsTheExactLiteralValueIncludingPunctuationAndEmptySource(String source) throws Exception {
        calculate("pulldata(\"" + source + "\", 'name', 'id', 'a')");
        assertEquals(Set.of(source), loadMedia().missingAttachments());
    }

    @ParameterizedTest @ValueSource(strings = {"/data/source", "concat('peo', 'ple')", "5", "string('people')"})
    void nonliteralSourcesAreNotListedEvenWhenTheirValueCanBeDetermined(String source) throws Exception {
        calculate("pulldata(" + source + ", 'name', 'id', 'a')");
        assertTrue(loadMedia().missingAttachments().isEmpty());
    }

    @Test
    void nestedLiteralSourceIsFoundEvenInsideAnOuterNonliteralSource() throws Exception {
        calculate("pulldata(pulldata('source-list', 'name', 'id', 'a'), 'name', 'id', 'a')");
        assertEquals(Set.of("source-list"), loadMedia().missingAttachments());
    }

    @Test
    void literalSourcesInUnchosenBranchesAreStillReported() throws Exception {
        calculate("if(true(), 'unused file', pulldata('not-evaluated', 'name', 'id', 'a'))");
        assertEquals(Set.of("not-evaluated"), loadMedia().missingAttachments());
    }

    @ParameterizedTest @ValueSource(strings = {"calculate", "relevant", "constraint", "required", "readonly"})
    void everyBindExpressionSiteContributesItsLiteralSources(String site) throws Exception {
        writeForm("", site + "=\"pulldata('bind-source', 'name', 'id', 'a') = ''\"", "", "");
        assertEquals(Set.of("bind-source"), loadMedia().missingAttachments());
    }

    @Test
    void modelAndBodySetvalueActionsContributeLiteralSources() throws Exception {
        writeForm("", "", "<setvalue event=\"odk-instance-first-load\" ref=\"/data/result\" "
                        + "value=\"pulldata('model-action', 'name', 'id', 'a')\"/>",
                "<input ref=\"/data/blank\"><label>Trigger</label>"
                        + "<setvalue event=\"xforms-value-changed\" ref=\"/data/target\" "
                        + "value=\"pulldata('body-action', 'name', 'id', 'a')\"/></input>");
        assertEquals(Set.of("model-action", "body-action"), loadMedia().missingAttachments());
    }

    @ParameterizedTest @ValueSource(strings = {"nodeset", "label", "value"})
    void itemsetNodesetAndLabelAndValueReferencesContributeLiteralSources(String site) throws Exception {
        String condition = "[pulldata('itemset-source', 'name', 'id', 'a') = '']";
        String nodeset = "instance('options')/items/item" + (site.equals("nodeset") ? condition : "");
        String label = "label" + (site.equals("label") ? condition : "");
        String value = "name" + (site.equals("value") ? condition : "");
        writeForm("", "", "<instance id=\"options\"><items><item><name>a</name><label>Alpha</label></item></items></instance>",
                "<select1 ref=\"/data/blank\"><label>Choose</label><itemset nodeset=\"" + nodeset
                        + "\"><label ref=\"" + label + "\"/><value ref=\"" + value + "\"/></itemset></select1>");
        assertEquals(Set.of("itemset-source"), loadMedia().missingAttachments());
    }

    @Test
    void textInsideXPathStringsLabelsInstanceDataAndCommentsDoesNotDeclareAttachments() throws Exception {
        calculate("\"pulldata('string-decoy', 'name', 'id', 'a')\"");
        String xml = Files.readString(xform)
                .replace("<source>people</source>", "<source>pulldata('data-decoy', 'name', 'id', 'a')</source>")
                .replace("<label>Key</label>", "<label>pulldata('label-decoy', 'name', 'id', 'a')</label>")
                .replace("</model>", "<!-- pulldata('comment-decoy', 'name', 'id', 'a') --></model>");
        Files.writeString(xform, xml);
        assertTrue(loadMedia().missingAttachments().isEmpty());
    }

    @Test
    void formWithoutPulldataHasAnEmptyInventory() throws Exception {
        assertTrue(loadMedia().missingAttachments().isEmpty());
    }

    @Test
    void streamLoadsReportAllLiteralSourcesButDoNotReportComputedSources() throws Exception {
        attach("people", "people.csv");
        calculate("concat(" + call("people", "name", "id", "a") + ","
                + call("absent", "name", "id", "a") + ",pulldata(/data/source, 'name', 'id', 'a'))");
        try (var in = Files.newInputStream(xform)) {
            assertEquals(Set.of("people", "absent"), FormLoader.load(in, xform.toString()).missingAttachments());
        }
    }

    @Test
    void addingFilesBeforeFirstInventoryReadDoesNotChangeTheLoadTimeSnapshot() throws Exception {
        calculate(call("people", "name", "id", "a"));
        var loaded = loadMedia();
        attach("people", "people.csv");
        assertEquals(Set.of("people"), loaded.missingAttachments());
        assertEquals("[Alice]", value(child(root(loaded.newEntryController()), "result")));
        assertEquals(Set.of("people"), loaded.missingAttachments());
        assertTrue(loadMedia().missingAttachments().isEmpty());
    }

    @Test
    void deletingFilesBeforeFirstInventoryReadDoesNotChangeTheLoadTimeSnapshot() throws Exception {
        var csv = attach("people", "people.csv");
        calculate(call("people", "name", "id", "a"));
        var loaded = loadMedia();
        Files.delete(csv);
        assertTrue(loaded.missingAttachments().isEmpty());
        assertEquals("[]", value(child(root(loaded.newEntryController()), "result")));
        assertTrue(loaded.missingAttachments().isEmpty());
        assertEquals(Set.of("people"), loadMedia().missingAttachments());
    }

    @Test
    void inventoriesAndPreviouslyReturnedSetsDoNotLeakBetweenLoadedForms() throws Exception {
        calculate(call("alpha", "name", "id", "a"));
        var first = loadMedia();
        var original = first.missingAttachments();
        calculate(call("beta", "name", "id", "a"));
        var second = loadMedia();
        second.newEntryController();
        first.newEntryController();
        assertEquals(Set.of("alpha"), original);
        assertEquals(Set.of("alpha"), first.missingAttachments());
        assertEquals(Set.of("beta"), second.missingAttachments());
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                Arguments.of("add", (Consumer<SortedSet<String>>) set -> set.add("new")),
                Arguments.of("remove", (Consumer<SortedSet<String>>) set -> set.remove("alpha")),
                Arguments.of("clear", (Consumer<SortedSet<String>>) SortedSet::clear),
                Arguments.of("iterator.remove", (Consumer<SortedSet<String>>) set -> {
                    var it = set.iterator(); it.next(); it.remove();
                }),
                Arguments.of("headSet.clear", (Consumer<SortedSet<String>>) set -> set.headSet("beta").clear()),
                Arguments.of("tailSet.clear", (Consumer<SortedSet<String>>) set -> set.tailSet("beta").clear()),
                Arguments.of("subSet.clear", (Consumer<SortedSet<String>>) set -> set.subSet("alpha", "beta").clear()),
                Arguments.of("removeIf", (Consumer<SortedSet<String>>) set -> set.removeIf(s -> true)));
    }

    @ParameterizedTest @MethodSource("mutations")
    void missingSetAndItsViewsCannotBeModified(String operation, Consumer<SortedSet<String>> mutation) throws Exception {
        calculate("concat(" + call("alpha", "name", "id", "a") + "," + call("beta", "name", "id", "a") + ")");
        var loaded = loadMedia();
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(loaded.missingAttachments()));
        assertEquals(Set.of("alpha", "beta"), loaded.missingAttachments());
    }

    @Test
    void emptyMissingSetCannotBePopulated() throws Exception {
        var loaded = loadMedia();
        assertThrows(UnsupportedOperationException.class, () -> loaded.missingAttachments().add("new"));
        assertTrue(loaded.missingAttachments().isEmpty());
    }
}
