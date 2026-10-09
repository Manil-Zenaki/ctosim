package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterContractTest {
    @TempDir Path temp;

    @Test void constructorRequiresEveryArgument() throws Exception {
        var catalog = catalog(temp, "T", null);
        var form = simpleForm();
        assertAll(() -> assertThrows(NullPointerException.class, () -> new SurveyCtoExporter(null, form, "example")),
                () -> assertThrows(NullPointerException.class, () -> new SurveyCtoExporter(catalog, null, "example")),
                () -> assertThrows(NullPointerException.class, () -> new SurveyCtoExporter(catalog, form, null)));
    }

    @ParameterizedTest @ValueSource(strings = {"", "a.b", "a_b", "a b", "a/b", "a:b", "a?b", "é", "服务器", "a\n", "\ta", "a@b"})
    void serverNameRejectsAnythingOutsideAsciiLettersDigitsAndHyphens(String server) throws Exception {
        var catalog = catalog(temp, "T", null);
        var form = simpleForm();
        assertThrows(IllegalArgumentException.class, () -> new SurveyCtoExporter(catalog, form, server));
    }

    @ParameterizedTest @ValueSource(strings = {"example", "A9-z", "-", "-edge-", "123"})
    void serverNamesUsingOnlyAllowedCharactersAreAcceptedAndUsedVerbatim(String server) throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", null, field("value", "image", "value", 2)), simpleForm(), server);
        assertValueBoth(e, value("a.jpg"), "https://" + server + ".surveycto.com/view/submission-attachment/?uuid=uuid%3Aabc&file=a.jpg");
    }

    @Test void catalogAndFormIdsMustMatchExactly() throws Exception {
        var c = catalogJson(temp, "{\"form_id\":\"EXPORT_TEST\",\"fields\":[]}");
        var form = simpleForm();
        assertThrows(IllegalArgumentException.class, () -> new SurveyCtoExporter(c, form, "example"));
    }

    @Test void catalogTitleAndVersionWinOverDefinitionAndInstanceMetadata() throws Exception {
        var e = single(temp, "text");
        var s = Submission.of(1, "<data id=\"export_test\" version=\"instance-version\"><value>x</value>"
                + "<meta><instanceID>uuid:x</instanceID><instanceName>ignore</instanceName></meta>"
                + "<review_status>ignore</review_status></data>", START, END);
        assertEquals("Export_WIDE.csv", e.wideFileName());
        assertEquals(List.of("SubmissionDate", "value", "instanceID", "formdef_version", "KEY"), csv(e.wide(List.of(s))).get(0));
        assertEquals(List.of(DATE, "x", "uuid:x", "v08", "uuid:x"), csv(e.wide(List.of(s))).get(1));
    }

    @Test void absentCatalogVersionGivesAnEmptyCell() throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", null), simpleForm(), "example");
        assertEquals(List.of(DATE, "uuid:x", "", "uuid:x"), csv(e.wide(List.of(submission(1, "uuid:x", "")))).get(1));
    }

    @Test void submissionDateComesFromEndAccessorWhileEndFieldComesFromXml() throws Exception {
        var e = single(temp, "end");
        var s = Submission.of(83, value("2026-01-02T00:05:00+14:00").instanceXml(), START.minusYears(10), END);
        for (String text : List.of(e.wide(List.of(s)), e.longFiles(List.of(s)).get("Export.csv"))) {
            assertEquals(DATE, row(text, 1).get("SubmissionDate"));
            assertEquals("Jan 2, 2026 12:05:00 AM", row(text, 1).get("value"));
        }
    }

    @Test void nestedOrdinaryGroupsAreFlattenedButFieldsAreResolvedByFullXpath() throws Exception {
        var form = form("<outer><inner><value/></inner></outer><value/>", "", "", "");
        var c = catalog(temp, "Export", null, field("outer", "begin group", "outer", 2),
                field("inner", "begin group", "outer/inner", 3), field("value", "text", "outer/inner/value", 4));
        var e = new SurveyCtoExporter(c, form, "example");
        var s = submission(1, "uuid:x", "<value>wrong root value</value><outer><inner><value>right nested value</value></inner></outer>");
        assertValueBoth(e, s, "right nested value");
        assertValueBoth(e, submission(2, "uuid:y", "<value>wrong root value</value>"), "");
        assertEquals(List.of("SubmissionDate", "value", "instanceID", "formdef_version", "KEY"), csv(e.wide(List.of())).get(0));
    }

    @Test void rootValidationAndFieldPathsUseTheFormsActualRootRatherThanHardcodedData() throws Exception {
        String definition = resource("form-template.xml").replace("<data id=", "<survey id=")
                .replace("</data>", "</survey>").replace("__INSTANCE__", "<value/>")
                .replace("__BINDS__", "").replace("__BODY__", "").replace("__SECONDARY__", "");
        var form = load(definition);
        var c = catalog(temp, "Export", null, new FieldInfo("value", "text", "/survey/value", 2));
        var e = new SurveyCtoExporter(c, form, "example");
        var s = Submission.of(1, "<survey id=\"export_test\"><value>right root</value><meta><instanceID>uuid:x</instanceID></meta></survey>", START, END);
        assertValueBoth(e, s, "right root");
        var wrongRoot = value("wrong root");
        assertThrows(IllegalArgumentException.class, () -> e.wide(List.of(wrongRoot)));
    }

    @Test void everyForbiddenTitleCharacterIsReplacedInEveryFileName() throws Exception {
        var c = catalog(temp, "é\\/:*?\"<>| end", null, field("main", "begin group", "main", 2),
                field("member", "begin repeat", "main/member", 3),
                field("job", "begin repeat", "main/member/info/job", 4),
                field("plot", "begin repeat", "main/plot", 5));
        var e = new SurveyCtoExporter(c, load(resource("repeats.xml")), "example");
        String title = "é_________ end";
        assertEquals(title + "_WIDE.csv", e.wideFileName());
        assertEquals(List.of(title + ".csv", title + "-main-member.csv", title + "-info-job.csv", title + "-main-plot.csv"),
                new ArrayList<>(e.longFiles(List.of()).keySet()));
    }

    @Test void headerIsPresentWithoutSubmissionsAndEmptyCatalogHasOnlyMetadata() throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", null), simpleForm(), "example");
        String expected = "SubmissionDate,instanceID,formdef_version,KEY";
        assertEquals(expected, e.wide(List.of()));
        assertEquals(Map.of("Export.csv", expected), e.longFiles(List.of()));
    }

    @Test void groupsAndNotesDoNotProduceColumnsAndCatalogOrderOverridesXmlAndRowNumbers() throws Exception {
        var form = form("<group><a/><b/><note/></group><uncatalogued/>", "", "", "");
        var c = catalog(temp, "Export", null,
                field("group", "begin group", "group", 10), field("b", "text", "group/b", 9),
                field("note", "note", "group/note", 2), field("a", "text", "group/a", 1));
        var e = new SurveyCtoExporter(c, form, "example");
        String text = e.wide(List.of(submission(1, "uuid:a", "<group><a>A</a><note>hidden</note><b>B</b></group><uncatalogued>ignored</uncatalogued>")));
        assertEquals(List.of("SubmissionDate", "b", "a", "instanceID", "formdef_version", "KEY"), csv(text).get(0));
        assertEquals(List.of(DATE, "B", "A", "uuid:a", "", "uuid:a"), csv(text).get(1));
        assertEquals(text, e.longFiles(List.of(submission(1, "uuid:a", "<group><a>A</a><b>B</b></group>"))).get("Export.csv"));
    }

    @Test void absentGroupAndAbsentOrEmptyFieldsStillHaveEmptyCatalogCells() throws Exception {
        var e = single(temp, "text");
        String text = e.wide(List.of(submission(1, "uuid:1", ""), submission(2, "uuid:2", "<value/>")));
        assertEquals("", row(text, 1).get("value"));
        assertEquals("", row(text, 2).get("value"));
        assertEquals(csv(e.wide(List.of())).get(0), csv(text).get(0));
    }

    static Stream<Arguments> invalidXmlAndMethods() {
        List<String> invalid = List.of("", "not XML", "<data>",
                "<wrong id=\"export_test\"><meta><instanceID>uuid:x</instanceID></meta></wrong>",
                "<data><meta><instanceID>uuid:x</instanceID></meta></data>",
                "<data id=\"wrong\"><meta><instanceID>uuid:x</instanceID></meta></data>",
                "<data id=\"export_test\"/>",
                "<data id=\"export_test\"><meta/></data>",
                "<data id=\"export_test\"><instanceID>uuid:x</instanceID></data>",
                "<data id=\"export_test\"><g><meta><instanceID>uuid:x</instanceID></meta></g></data>",
                "<data id=\"export_test\"><meta><g><instanceID>uuid:x</instanceID></g></meta></data>");
        return invalid.stream().flatMap(xml -> Stream.of("wide", "long", "write").map(method -> Arguments.of(method, xml)));
    }

    private void export(String method, SurveyCtoExporter e, List<Submission> submissions) throws IOException {
        switch (method) {
            case "wide" -> e.wide(submissions);
            case "long" -> e.longFiles(submissions);
            case "write" -> e.writeAll(submissions, temp.resolve("output"));
            default -> throw new AssertionError(method);
        }
    }

    @ParameterizedTest(name = "{0}: invalid XML case {index}") @MethodSource("invalidXmlAndMethods")
    void invalidInstanceIsRejectedByEveryExportMethodAndNamesSubmissionNumber(String method, String xml) throws Exception {
        var e = single(temp, "text");
        var s = Submission.of(731, xml, START, END);
        var error = assertThrows(IllegalArgumentException.class, () -> export(method, e, List.of(value("valid first"), s)));
        assertTrue(error.getMessage().contains("731"), "Diagnostic must identify number, not list position");
    }

    @ParameterizedTest @ValueSource(strings = {"wide", "long", "write"})
    void nullSubmissionListAndNullElementsAreRejected(String method) throws Exception {
        var e = single(temp, "text");
        assertAll(() -> assertThrows(NullPointerException.class, () -> export(method, e, null)),
                () -> assertThrows(NullPointerException.class, () -> export(method, e, Arrays.asList((Submission) null))),
                () -> assertThrows(NullPointerException.class, () -> export(method, e, Arrays.asList(value("valid"), null))));
    }

    @Test void nullOutputDirectoryIsRejected() throws Exception {
        var e = single(temp, "text");
        assertThrows(NullPointerException.class, () -> e.writeAll(List.of(), null));
    }

    @Test void longMapCannotBeChangedThroughMapEntriesOrViews() throws Exception {
        var result = single(temp, "text").longFiles(List.of());
        assertAll(() -> assertThrows(UnsupportedOperationException.class, () -> result.put("new", "x")),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.remove("Export.csv")),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.entrySet().iterator().next().setValue("x")),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.keySet().remove("Export.csv")),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.values().clear()));
    }

    @Test void writingCreatesParentsAndMatchesEveryReturnedUtf8Byte() throws Exception {
        var e = repeats(temp);
        var submissions = List.of(submission(1, "uuid:é", "<before>家庭,\"é\"</before><main><plot><crop>teff</crop></plot></main>"));
        Path directory = temp.resolve("missing/parents/output");
        e.writeAll(submissions, directory);
        Map<String, String> expected = new LinkedHashMap<>(e.longFiles(submissions));
        expected.put(e.wideFileName(), e.wide(submissions));
        try (var files = Files.list(directory)) { assertEquals(expected.size(), files.count()); }
        for (var entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(directory.resolve(entry.getKey())), entry.getKey());
        }
    }

    @Test void writingReplacesAndTruncatesEveryExportButPreservesOtherFiles() throws Exception {
        var e = repeats(temp);
        Path directory = Files.createDirectory(temp.resolve("existing"));
        var names = new ArrayList<>(e.longFiles(List.of()).keySet());
        names.add(e.wideFileName());
        for (String name : names) Files.writeString(directory.resolve(name), "obsolete".repeat(1000));
        byte[] sentinel = {0, 1, 13, 10, (byte) 255};
        Files.write(directory.resolve("keep.bin"), sentinel);
        e.writeAll(List.of(), directory);
        assertArrayEquals(sentinel, Files.readAllBytes(directory.resolve("keep.bin")));
        assertEquals(e.wide(List.of()), Files.readString(directory.resolve(e.wideFileName())));
        for (var file : e.longFiles(List.of()).entrySet()) assertEquals(file.getValue(), Files.readString(directory.resolve(file.getKey())));
    }

    @Test void filesystemErrorsPropagateWhenDirectoryIsARegularFile() throws Exception {
        var e = single(temp, "text");
        Path file = Files.writeString(temp.resolve("occupied"), "keep");
        assertThrows(IOException.class, () -> e.writeAll(List.of(), file));
        assertEquals("keep", Files.readString(file));
    }

    @Test void filesystemErrorsPropagateWhenAnExportTargetIsADirectory() throws Exception {
        var e = single(temp, "text");
        Path directory = Files.createDirectory(temp.resolve("output"));
        Files.createDirectory(directory.resolve(e.wideFileName()));
        assertThrows(IOException.class, () -> e.writeAll(List.of(), directory));
    }

    @Test void repeatedCallsSeparateLoadsAndPriorExportsCannotChangeResults() throws Exception {
        var e = repeats(temp);
        var submissions = repeatSubmissions();
        String wide = e.wide(submissions);
        var longFiles = e.longFiles(submissions);
        var retained = new LinkedHashMap<>(longFiles);
        e.wide(List.of()); e.longFiles(List.of());
        assertEquals(wide, e.wide(submissions));
        assertEquals(retained, e.longFiles(submissions));
        assertEquals(retained, longFiles);
        var fresh = repeats(temp);
        assertEquals(wide, fresh.wide(submissions));
        assertEquals(retained, fresh.longFiles(submissions));
    }
}
