package ctosim;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment3SearchTest extends FormWalkerAmendment3SearchSupport {
    @ParameterizedTest @ValueSource(strings = {"search('search')", "search('search.csv')", "minimal search('search') quick"})
    void oneArgumentReplacesPlaceholderInPlaceAndPreservesFileOrder(String appearance) throws Exception {
        assertGeneratedChoices(search("select", appearance, "7", "code", "8"),
                List.of("7", "V03", "V01", "V02", "V04", "V05", "8"));
    }

    static Stream<Arguments> matchingCalls() {
        return Stream.of(
                Arguments.of("'contains', 'name', 'Alpha'", List.of("V03", "V01", "V05")),
                Arguments.of("'startswith', 'name', 'Alpha'", List.of("V03", "V01")),
                Arguments.of("'endswith', 'name', 'Alpha'", List.of("V03", "V05")),
                Arguments.of("'matches', 'name', 'Alpha'", List.of("V03")),
                Arguments.of("'matches', 'name', 'alpha'", List.of("V04")),
                Arguments.of("'matches', 'name', 'ALPHA'", List.of()),
                Arguments.of("'matches', 'name', 'Al.*'", List.of()),
                Arguments.of("'matches', 'name,alias', 'Alpha'", List.of("V03", "V02")),
                Arguments.of("'contains', 'name,alias', 'Alpha', 'region', '2'", List.of("V02", "V05")),
                Arguments.of("'contains', 'name', 'Alpha', 'region', '1'", List.of("V03", "V01")),
                Arguments.of("'matches', 'name', 'Alpha', 'region', ' 1'", List.of()),
                Arguments.of("'matches', 'region', '1', 'name', 'Alpha'", List.of("V03")),
                Arguments.of("'matches', 'region', '1', 'name', 'alpha'", List.of("V04")),
                Arguments.of("'contains', 'name', ''", List.of("V03", "V01", "V02", "V04", "V05")),
                Arguments.of("'matches', 'name', ' Alpha'", List.of()));
    }

    @ParameterizedTest @MethodSource("matchingCalls")
    void matchingIsExactCaseSensitiveAndMultipleColumnsUseAnyMatch(String args, List<String> expected) throws Exception {
        assertGeneratedChoices(search("select", "search('search', " + args + ")", "code"), expected);
    }

    @Test void sortbyIsNumericWithInvalidRowsLastAndDeduplicationAfterSorting() throws Exception {
        assertGeneratedChoices(search("select", "search('sorted', 'matches', 'region', '1')", "7", "code", "8"),
                List.of("7", "EARLY", "DUP", "MIDDLE", "LATE", "BAD_FIRST", "BAD_LAST", "8"));
    }

    @Test void loadedDuplicatesDoNotBiasSelectOneDraws() throws Exception {
        FormWalker walker = new FormWalker(search("select1", "search('search')", "code"), searchConfig(""));
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (int n = 1; n <= 2500; n++) {
            Submission result = walker.walk(n);
            assertTrue(result.warnings().isEmpty());
            counts.merge(value(result, "answer"), 1, Integer::sum);
        }
        assertEquals(java.util.Set.of("V03", "V01", "V02", "V04", "V05"), counts.keySet());
        counts.forEach((choice, count) -> assertUniformBin(count, 2500, .2, choice));
    }

    @ParameterizedTest @ValueSource(strings = {"select1", "select"})
    void fixedLoadedValueConvertsEvenThoughAbsentFromEngineChoices(String control) throws Exception {
        assertAccepted(search(control, "search('search')", "code"), "V03");
    }

    @Test void multipleAnswerCanMixLoadedAndStaticChoices() throws Exception {
        assertAccepted(search("select", "search('search')", "7", "code", "8"), "7 V03 V02 8");
    }

    @ParameterizedTest @ValueSource(strings = {"code", "Label for code", "v03", "UNKNOWN", "V02"})
    void placeholderLabelsWrongCaseAndFilteredOutValuesCannotBeStored(String invalid) throws Exception {
        assertRejected(search("select1", "search('search', 'matches', 'region', '1')", "code"), invalid);
    }

    @Test void oneUnknownItemRejectsAnEntireMultipleAnswer() throws Exception {
        assertRejected(search("select", "search('search')", "code"), "V03 UNKNOWN");
    }

    @Test void weightsCanSelectLoadedValues() throws Exception {
        FormWalker walker = new FormWalker(search("select1", "search('search')", "7", "code"),
                searchConfig("\"answer\":{\"weights\":{\"V02\":1,\"not-offered\":100}}"));
        for (int n = 1; n <= 20; n++) assertEquals("V02", value(walker.walk(n), "answer"));
    }

    @Test void noPlaceholderKeepsOrdinaryNumericChoicesAndDoesNotTryMissingFile() throws Exception {
        assertGeneratedChoices(search("select", "search('absent')", "-2", "0", "2.5"), List.of("-2", "0", "2.5"));
    }

    @Test void negativeAndDecimalStaticValuesAreNotMistakenForPlaceholder() throws Exception {
        assertGeneratedChoices(search("select", "search('search', 'matches', 'name', 'Alpha')", "-2", "2.5", "code"),
                List.of("-2", "2.5", "V03"));
    }

    @Test void numericLoadedValuesAreStoredWithoutConvertingThemToIndexes() throws Exception {
        assertGeneratedChoices(search("select", "search('search')", "region"), List.of("1", "2"));
    }

    @Test void loadedChoicesStillParticipateInSpecialValueSelection() throws Exception {
        LoadedForm loaded = search("select1", "search('search')", "code");
        SimulationConfig cfg = config("\"missingRate\":0,\"specialRate\":1,\"specialValues\":[\"V02\"]");
        for (int n = 1; n <= 10; n++) assertEquals("V02", value(new FormWalker(loaded, cfg).walk(n), "answer"));
    }

    @Test void noSearchAppearanceKeepsNonnumericChoiceAsAnOrdinaryChoice() throws Exception {
        assertAccepted(search("select1", "minimal", "code"), "code");
    }

    @Test void fieldArgumentsAreEvaluatedAfterEarlierQuestionsAreAnswered() throws Exception {
        LoadedForm loaded = attached("<source/><method/><columns/><needle/><filter/><region/><answer/>",
                bind("source", "string", "") + bind("method", "string", "") + bind("columns", "string", "")
                + bind("needle", "string", "") + bind("filter", "string", "") + bind("region", "int", "")
                + bind("answer", "string", ""),
                input("source") + input("method") + input("columns") + input("needle") + input("filter") + input("region")
                + selectControl("select1", "answer", "search(../source, ../method, ../columns, ../needle, ../filter, ../region)", items("code")), "");
        Submission result = new FormWalker(loaded, searchConfig("""
                "source":{"value":"search.csv"},"method":{"value":"matches"},
                "columns":{"value":"name,alias"},"needle":{"value":"Alpha"},
                "filter":{"value":"region"},"region":{"value":2}
                """)).walk(1);
        assertEquals("V02", value(result, "answer"));
        assertEquals(7, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void parserHandlesNestedXPathCallsCommasAndParenthesesInsideLiterals() throws Exception {
        assertAccepted(search("select1", "search(concat('quo', 'ted'), 'matches', concat('na', 'me'), concat('Alpha', ', ', 'Beta'))", "code"), "É03");
        assertGeneratedChoices(search("select", "search('quoted', 'matches', 'name', 'unused(a,b)')", "code"), List.of());
    }

    @Test void fieldReferencesAreResolvedSeparatelyForEachRepeatParent() throws Exception {
        LoadedForm loaded = attached("<size>2</size><row jr:template=\"\"><region/><answer/></row>",
                bind("size", "int", "") + bind("row/region", "int", "calculate=\"position(..)\"") + bind("row/answer", "string", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">"
                + selectControl("select1", "row/answer", "search('search', 'matches', 'name', 'Alpha', 'region', ../region)", items("7", "code")) + "</repeat>", "");
        FormWalker walker = new FormWalker(loaded, searchConfig("\"answer\":{\"weights\":{\"V03\":1}}"));
        for (int n = 1; n <= 12; n++) {
            Submission result = walker.walk(n);
            assertEquals("V03", value(result, "row[1]/answer"));
            assertEquals("7", value(result, "row[2]/answer"));
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test void searchBypassesChoiceFiltersForBothPlaceholderAndStaticChoices() throws Exception {
        String secondary = "<instance id=\"choices\"><root>"
                + "<item><name>7</name><label>Static before</label><keep>no</keep></item>"
                + "<item><name>code</name><label>Placeholder</label><keep>no</keep></item>"
                + "<item><name>8</name><label>Static after</label><keep>yes</keep></item>"
                + "</root></instance>";
        LoadedForm loaded = attached("<answer/>", bind("answer", "string", ""),
                selectControl("select", "answer", "search('search')",
                        "<itemset nodeset=\"instance('choices')/root/item[keep = 'yes']\"><value ref=\"name\"/><label ref=\"label\"/></itemset>"), secondary);
        assertGeneratedChoices(loaded, List.of("7", "V03", "V01", "V02", "V04", "V05", "8"));
    }

    static Stream<Arguments> quotedFields() {
        return Stream.of(Arguments.of("'matches', 'name', 'Alpha, Beta'", "É03"),
                Arguments.of("'matches', 'name', 'say \"Hello\"'", "V02"),
                Arguments.of("'contains', 'name', 'second'", "V03"),
                Arguments.of("'matches', 'name', ' padded '", "V04"),
                Arguments.of("'matches', 'region', ''", "V05"),
                Arguments.of("'matches', 'name', 'extra'", "V06"));
    }

    @ParameterizedTest @MethodSource("quotedFields")
    void attachedCsvUsesPulldataQuotingUnicodeAndRaggedRowRules(String args, String expected) throws Exception {
        assertAccepted(search("select1", "search('quoted', " + args + ")", "code"), expected);
    }

    @Test void utf8BomAndCrLfAreAccepted() throws Exception {
        Files.writeString(media.resolve("quoted.csv"), "\ufeff" + resource("amendment3/quoted.csv").replace("\r\n", "\n").replace("\n", "\r\n"));
        assertAccepted(search("select1", "search('quoted', 'matches', 'name', 'Alpha, Beta')", "code"), "É03");
    }

    @Test void headerOnlyFileProducesAnEmptyLoadedListWithoutWarning() throws Exception {
        assertGeneratedChoices(search("select", "search('header-only')", "code"), List.of());
    }

    @Test void anEmptyMatchRetainsStaticChoicesOnBothSides() throws Exception {
        assertGeneratedChoices(search("select", "search('search', 'matches', 'name', 'absent')", "7", "code", "8"), List.of("7", "8"));
    }

    @Test void replayAcrossLoadsAndOutOfOrderWalksIsIdentical() throws Exception {
        LoadedForm loaded = search("select", "search('sorted')", "7", "code", "8");
        FormWalker first = new FormWalker(loaded, searchConfig(""));
        Submission expected = first.walk(23);
        first.walk(1);
        assertSameSubmission(expected, first.walk(23));
        FormWalker second = new FormWalker(FormLoader.load(xform, media), searchConfig(""));
        second.walk(39);
        Submission actual = second.walk(23);
        assertSameSubmission(expected, actual);
        assertEquals(expected.submitted(), actual.submitted());
    }

    @Test void attachedFileIsCachedForLoadedFormButFreshLoadsSeeChanges() throws Exception {
        LoadedForm loaded = search("select1", "search('search', 'matches', 'name', 'Alpha')", "code");
        assertAccepted(loaded, "V03");
        Files.writeString(media.resolve("search.csv"), resource("amendment3/header-only.csv"));
        assertAccepted(loaded, "V03");
        assertGeneratedChoices(FormLoader.load(xform, media), List.of());
    }

    @Test void fileIsFirstReadWhenTheQuestionNeedsIt() throws Exception {
        LoadedForm loaded = search("select1", "search('search', 'matches', 'name', 'Alpha')", "code");
        Files.writeString(media.resolve("search.csv"), resource("amendment3/header-only.csv"));
        assertGeneratedChoices(loaded, List.of());
    }

    @Test void attachmentCacheIsNotSharedBetweenFormsWithDifferentMediaDirectories() throws Exception {
        LoadedForm first = search("select1", "search('search', 'matches', 'name', 'Alpha')", "code");
        assertAccepted(first, "V03");
        var otherMedia = Files.createDirectory(area.resolve("other-media"));
        Files.writeString(otherMedia.resolve("search.csv"), resource("amendment3/header-only.csv"));
        assertGeneratedChoices(FormLoader.load(xform, otherMedia), List.of());
        assertAccepted(first, "V03");
    }

    @Test void differentSeedsCanProduceDifferentSequencesOfLoadedAnswers() throws Exception {
        LoadedForm loaded = search("select1", "search('search')", "code");
        FormWalker first = new FormWalker(loaded, searchConfig("").withSeed(19));
        FormWalker second = new FormWalker(loaded, searchConfig("").withSeed(912));
        var a = new java.util.ArrayList<String>();
        var b = new java.util.ArrayList<String>();
        for (int n = 1; n <= 32; n++) {
            a.add(value(first.walk(n), "answer"));
            b.add(value(second.walk(n), "answer"));
        }
        assertNotEquals(a, b);
    }
}
