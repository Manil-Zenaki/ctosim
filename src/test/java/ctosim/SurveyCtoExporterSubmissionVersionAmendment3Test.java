package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 08 Amendment 3: formdef_version belongs to each submitted instance. */
class SurveyCtoExporterSubmissionVersionAmendment3Test {
    @TempDir Path temp;

    enum Layout {
        WIDE, LONG_MAIN;

        String export(SurveyCtoExporter exporter, List<Submission> submissions) {
            return this == WIDE ? exporter.wide(submissions)
                    : exporter.longFiles(submissions).get("Export.csv");
        }
    }

    private SurveyCtoExporter exporter(String catalogVersion) throws Exception {
        return new SurveyCtoExporter(catalog(temp, "Export", catalogVersion,
                field("value", "text", "value", 2)), simpleForm(), "example");
    }

    private SurveyCtoExporter exporterWithMissingCatalogVersion() throws Exception {
        return new SurveyCtoExporter(catalogJson(temp, """
                {"form_id":"export_test","title":"Export","fields":[
                  {"name":"value","type":"text","xpath":"/data/value","row":2}]}
                """), simpleForm(), "example");
    }

    private static String attribute(String value) {
        // Character references preserve XML attribute whitespace, unlike literal CR/LF/TAB.
        return xmlText(value).replace("\"", "&quot;")
                .replace("\n", "&#10;").replace("\t", "&#9;");
    }

    private static Submission submissionWithVersion(int number, String id, String version, String content) {
        String versionAttribute = version == null ? "" : " version=\"" + attribute(version) + "\"";
        return Submission.of(number, "<data id=\"export_test\"" + versionAttribute + ">" + content
                + "<meta><instanceID>" + xmlText(id) + "</instanceID></meta></data>", START, END);
    }

    private static Submission version(String version) {
        return submissionWithVersion(1, "uuid:abc", version, "<value>kept</value>");
    }

    private static List<String> versions(String text) {
        var rows = csv(text);
        int column = rows.get(0).indexOf("formdef_version");
        assertTrue(column >= 0, "The version metadata column must remain present");
        return rows.subList(1, rows.size()).stream().map(r -> r.get(column)).toList();
    }

    static Stream<Arguments> literalVersions() {
        return Stream.of(Layout.values()).flatMap(layout -> Stream.of(
                "2026110701", "0", "00000042", "92233720368547758081234567890",
                "release-3.01", "\u00e9\u6c34\ud83d\ude80", "", "   ", "  v3  ",
                "v,3", "v\"3", "v&<3>", "v\r3", "v\n3", "v\t3")
                .map(value -> Arguments.of(layout, value)));
    }

    @ParameterizedTest @MethodSource("literalVersions")
    void rootAttributeOverridesCatalogAndPreservesItsStringValue(Layout layout, String stored) throws Exception {
        assertEquals(List.of(stored), versions(layout.export(exporter("catalog-version"), List.of(version(stored)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentRootAttributeFallsBackToCatalogRatherThanFormDefinition(Layout layout) throws Exception {
        // simpleForm's definition has version="definition-version".
        assertEquals(List.of("catalog-version"),
                versions(layout.export(exporter("catalog-version"), List.of(version(null)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentRootAttributeAndNullCatalogVersionGiveEmptyCell(Layout layout) throws Exception {
        assertEquals(List.of(""), versions(layout.export(exporter(null), List.of(version(null)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentRootAttributeAndMissingCatalogVersionGiveEmptyCell(Layout layout) throws Exception {
        assertEquals(List.of(""), versions(layout.export(exporterWithMissingCatalogVersion(), List.of(version(null)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentRootAttributePreservesEmptyCatalogVersion(Layout layout) throws Exception {
        assertEquals(List.of(""), versions(layout.export(exporter(""), List.of(version(null)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void presentRootAttributeWorksWhenCatalogVersionIsNull(Layout layout) throws Exception {
        assertEquals(List.of("instance-version"),
                versions(layout.export(exporter(null), List.of(version("instance-version")))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void presentRootAttributeWorksWhenCatalogVersionIsMissing(Layout layout) throws Exception {
        assertEquals(List.of("instance-version"), versions(layout.export(exporterWithMissingCatalogVersion(),
                List.of(version("instance-version")))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void presentRootAttributeWorksWhenCatalogVersionIsEmpty(Layout layout) throws Exception {
        assertEquals(List.of("instance-version"),
                versions(layout.export(exporter(""), List.of(version("instance-version")))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void fallbackPreservesCatalogVersionWithoutNumericParsingOrTrimming(Layout layout) throws Exception {
        String fallback = "  00042, \"\u00e9\u6c34\"  ";
        assertEquals(List.of(fallback), versions(layout.export(exporter(fallback), List.of(version(null)))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void versionElementsAndDescendantAttributesDoNotSupplyTheRootVersion(Layout layout) throws Exception {
        String content = "<version>child-version</version><formdef_version>decoy</formdef_version>"
                + "<value version=\"field-version\">kept</value>"
                + "<other version=\"nested-version\"><data version=\"nested-root\"/></other>";
        var s = submissionWithVersion(1, "uuid:abc", null, content);
        assertEquals(List.of("catalog-version"), versions(layout.export(exporter("catalog-version"), List.of(s))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void rootAttributeWinsOverConflictingVersionElementsAndDescendantAttributes(Layout layout) throws Exception {
        var s = submissionWithVersion(1, "uuid:abc", "root-version",
                "<value version=\"field-version\">kept</value><version>child-version</version>");
        assertEquals(List.of("root-version"), versions(layout.export(exporter("catalog-version"), List.of(s))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void eachRowResolvesItsOwnVersionInSubmissionListOrder(Layout layout) throws Exception {
        var input = List.of(
                submissionWithVersion(90, "uuid:new", "20261107", "<value>new</value>"),
                submissionWithVersion(2, "uuid:missing", null, "<value>fallback</value>"),
                submissionWithVersion(71, "uuid:old", "20200101", "<value>old</value>"),
                submissionWithVersion(4, "uuid:empty", "", "<value>empty</value>"),
                submissionWithVersion(5, "uuid:again", null, "<value>fallback again</value>"),
                submissionWithVersion(1, "uuid:same-version", "20261107", "<value>new again</value>"));
        String text = layout.export(exporter("catalog-version"), input);
        assertEquals(List.of("20261107", "catalog-version", "20200101", "", "catalog-version", "20261107"),
                versions(text));
        assertEquals(List.of("uuid:new", "uuid:missing", "uuid:old", "uuid:empty", "uuid:again", "uuid:same-version"),
                csv(text).subList(1, 7).stream().map(r -> r.get(r.size() - 1)).toList());
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void changingOnlyRootVersionChangesOnlyTheVersionCell(Layout layout) throws Exception {
        var e = exporter("catalog-version");
        Map<String, String> before = row(layout.export(e, List.of(version("first"))), 1);
        Map<String, String> after = row(layout.export(e, List.of(version("second"))), 1);
        assertEquals("first", before.remove("formdef_version"));
        assertEquals("second", after.remove("formdef_version"));
        assertEquals(before, after);
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void earlierExportsCannotLeakTheirVersionIntoLaterCalls(Layout layout) throws Exception {
        var e = exporter("catalog-version");
        String first = layout.export(e, List.of(version("first")));
        assertEquals(List.of("first"), versions(first));
        assertEquals(List.of("catalog-version"), versions(layout.export(e, List.of(version(null)))));
        assertEquals(List.of(""), versions(layout.export(e, List.of(version("")))));
        assertEquals(List.of("second"), versions(layout.export(e, List.of(version("second")))));
        assertEquals(first, layout.export(e, List.of(version("first"))));
        assertEquals(first, layout.export(exporter("catalog-version"), List.of(version("first"))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void emptyBatchKeepsTheVersionHeaderWithoutInventingARow(Layout layout) throws Exception {
        var e = exporter(null);
        layout.export(e, List.of(version("previous")));
        assertEquals("SubmissionDate,value,instanceID,formdef_version,KEY", layout.export(e, List.of()));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void submissionVersionIsStillMetadataWhenTheCatalogHasNoFields(Layout layout) throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", "catalog-version"), simpleForm(), "example");
        String expected = "SubmissionDate,instanceID,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",uuid:abc,instance-version,uuid:abc";
        assertEquals(expected, layout.export(e, List.of(version("instance-version"))));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void versionStaysBetweenInstanceNameAndKeyWhenTheFormEnablesInstanceName(Layout layout) throws Exception {
        String definition = resource("form-template.xml")
                .replace("__INSTANCE__", "<value/>")
                .replace("<meta><instanceID/></meta>", "<meta><instanceID/><instanceName/></meta>")
                .replace("__BINDS__", "<bind nodeset=\"/data/value\" type=\"string\"/>")
                .replace("__BODY__", "<input ref=\"/data/value\"><label>Value</label></input>")
                .replace("__SECONDARY__", "");
        var e = new SurveyCtoExporter(catalog(temp, "Export", "catalog-version",
                field("value", "text", "value", 2)), load(definition), "example");
        var s = Submission.of(1, "<data id=\"export_test\" version=\"instance-version\"><value>kept</value>"
                + "<meta><instanceID>uuid:abc</instanceID><instanceName>House</instanceName></meta></data>", START, END);
        assertEquals("SubmissionDate,value,instanceID,instanceName,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:abc,House,instance-version,uuid:abc",
                layout.export(e, List.of(s)));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void rootVersionUsesNormalCsvQuotingAndXmlEntityDecoding(Layout layout) throws Exception {
        String expected = "SubmissionDate,value,instanceID,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:abc,\"\u00e9, \"\"v3\"\" & <x>\r\nnext\",uuid:abc";
        assertEquals(expected, layout.export(exporter("catalog-version"),
                List.of(version("\u00e9, \"v3\" & <x>\r\nnext"))));
    }

    @Test
    void mixedRootVersionsDoNotAlterRepeatHeadersValuesOrKeys() throws Exception {
        var e = repeats(temp);
        var first = submissionWithVersion(8, "uuid:a", "old",
                "<main><member version=\"repeat-decoy\"><member_name>A</member_name><info>"
                + "<job version=\"job-decoy\"><job_title>Job A</job_title></job></info></member></main>");
        var second = submissionWithVersion(2, "uuid:b", "new",
                "<main><member><member_name>B</member_name><info/></member></main>");
        var submissions = List.of(first, second);
        var files = e.longFiles(submissions);
        assertEquals("member_name,job_count,SET-OF-job,member_tail,PARENT_KEY,KEY,SET-OF-member\n"
                + "A,1,uuid:a/main-member[1]/info-job,,uuid:a,uuid:a/main-member[1],uuid:a/main-member\n"
                + "B,0,,,uuid:b,uuid:b/main-member[1],uuid:b/main-member",
                files.get("Repeats-main-member.csv"));
        assertEquals("job_title,tags,tags_1,tags__97,gps-Latitude,gps-Longitude,gps-Altitude,gps-Accuracy,PARENT_KEY,KEY,SET-OF-job\n"
                + "Job A,,,,,,,,uuid:a/main-member[1],uuid:a/main-member[1]/info-job[1],uuid:a/main-member[1]/info-job",
                files.get("Repeats-info-job.csv"));
        assertEquals(List.of("old", "new"), versions(e.wide(submissions)));
        assertEquals(List.of("old", "new"), versions(files.get("Repeats.csv")));
    }

    @Test
    void writeAllWritesPerSubmissionVersionsAsExactUtf8AndReplacesThemOnNextExport() throws Exception {
        var e = exporter("catalog-version");
        Path directory = temp.resolve("export");
        var input = List.of(
                submissionWithVersion(7, "uuid:a", "\u00e9,\"3\"", "<value>kept</value>"),
                submissionWithVersion(1, "uuid:b", null, "<value>kept</value>"),
                submissionWithVersion(9, "uuid:c", "", "<value>kept</value>"));
        String expected = "SubmissionDate,value,instanceID,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:a,\"\u00e9,\"\"3\"\"\",uuid:a\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:b,catalog-version,uuid:b\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:c,,uuid:c";
        e.writeAll(input, directory);
        for (String filename : List.of("Export_WIDE.csv", "Export.csv")) {
            assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(directory.resolve(filename)), filename);
        }
        e.writeAll(List.of(version("0")), directory);
        String replacement = "SubmissionDate,value,instanceID,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:abc,0,uuid:abc";
        for (String filename : List.of("Export_WIDE.csv", "Export.csv")) {
            assertArrayEquals(replacement.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(directory.resolve(filename)), filename);
        }
    }
}
