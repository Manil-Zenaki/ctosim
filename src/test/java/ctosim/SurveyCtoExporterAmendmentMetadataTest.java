package ctosim;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Metadata behavior introduced by spec 08 Amendment 1, as updated by Amendment 2. */
class SurveyCtoExporterAmendmentMetadataTest {
    @TempDir Path temp;

    private SurveyCtoExporter exporter(String primaryName, String extraPrimary, String secondary) throws Exception {
        String definition = resource("form-template.xml")
                .replace("__INSTANCE__", "<value/>" + extraPrimary)
                .replace("<meta><instanceID/></meta>", "<meta><instanceID/>" + primaryName + "</meta>")
                .replace("__BINDS__", "<bind nodeset=\"/data/value\" type=\"string\"/>")
                .replace("__BODY__", "<input ref=\"/data/value\"><label>Value</label></input>")
                .replace("__SECONDARY__", secondary);
        return new SurveyCtoExporter(catalog(temp, "Export", "v08", field("value", "text", "value", 2)),
                load(definition), "example");
    }

    private SurveyCtoExporter namedExporter() throws Exception {
        return exporter("<instanceName>definition default</instanceName>", "", "");
    }

    private Submission namedSubmission(int number, String id, String nameElement, LocalDateTime submitted) {
        return Submission.of(number, instance(id, nameElement), START, END, submitted);
    }

    private Submission namedSubmission(int number, String id, String nameElement) {
        return Submission.of(number, instance(id, nameElement), START, END);
    }

    private String instance(String id, String nameElement) {
        return "<data id=\"export_test\"><value>kept</value><meta><instanceID>"
                + xmlText(id) + "</instanceID>" + nameElement + "</meta></data>";
    }

    private List<String> mainTexts(SurveyCtoExporter e, List<Submission> submissions) {
        return List.of(e.wide(submissions), e.longFiles(submissions).get("Export.csv"));
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
            "2027-01-01T00:05:00.987654321|Jan 1, 2027 12:05:00 AM",
            "2027-02-03T12:00:01.000000001|Feb 3, 2027 12:00:01 PM",
            "2027-09-09T13:04:05|Sep 9, 2027 1:04:05 PM",
            "2027-12-31T23:59:59.999999999|Dec 31, 2027 11:59:59 PM"})
    void submissionDateUsesSubmittedWithTheSpecifiedDateTimeFormatting(String submitted, String expected) throws Exception {
        var s = namedSubmission(1, "uuid:abc", "", LocalDateTime.parse(submitted));
        for (String text : mainTexts(namedExporter(), List.of(s))) {
            assertEquals(expected, row(text, 1).get("SubmissionDate"));
        }
    }

    @Test void storedEndFieldStillUsesXmlWhileSubmissionDateUsesSubmitted() throws Exception {
        var e = single(temp, "end");
        var s = Submission.of(1, "<data id=\"export_test\"><value>2025-02-03T04:05:06.789+03:00</value>"
                + "<meta><instanceID>uuid:abc</instanceID></meta></data>", START, END,
                LocalDateTime.of(2027, 1, 2, 13, 4, 5));
        for (String text : mainTexts(e, List.of(s))) {
            var values = row(text, 1);
            assertEquals("Jan 2, 2027 1:04:05 PM", values.get("SubmissionDate"));
            assertEquals("Feb 3, 2025 4:05:06 AM", values.get("value"));
        }
    }

    @Test void submittedDateFormattingIsEnglishAndIndependentOfDefaultTimeZone() throws Exception {
        Locale oldLocale = Locale.getDefault();
        Locale oldFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var s = namedSubmission(1, "uuid:abc", "", LocalDateTime.of(2027, 9, 2, 0, 5, 6));
            for (String text : mainTexts(namedExporter(), List.of(s))) {
                assertEquals("Sep 2, 2027 12:05:06 AM", row(text, 1).get("SubmissionDate"));
            }
        } finally {
            Locale.setDefault(oldLocale);
            Locale.setDefault(Locale.Category.FORMAT, oldFormat);
            Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
            TimeZone.setDefault(oldZone);
        }
    }

    @Test void changingOnlySubmittedChangesOnlySubmissionDate() throws Exception {
        var e = namedExporter();
        var first = namedSubmission(1, "uuid:abc", "<instanceName>House</instanceName>", END.plusDays(1));
        var second = namedSubmission(1, "uuid:abc", "<instanceName>House</instanceName>", END.plusDays(2));
        var texts1 = mainTexts(e, List.of(first));
        var texts2 = mainTexts(e, List.of(second));
        for (int i = 0; i < 2; i++) {
            Map<String, String> r1 = row(texts1.get(i), 1), r2 = row(texts2.get(i), 1);
            assertNotEquals(r1.remove("SubmissionDate"), r2.remove("SubmissionDate"));
            assertEquals(r1, r2);
        }
        assertEquals(texts1, mainTexts(e, List.of(first)), "Earlier calls must not affect the result");
    }

    @Test void submittedDatesDoNotSortOrMergeSubmissionRows() throws Exception {
        var later = namedSubmission(90, "uuid:later", "<instanceName>Later</instanceName>", END.plusDays(2));
        var earlier = namedSubmission(3, "uuid:earlier", "<instanceName>Earlier</instanceName>", END.plusDays(1));
        for (String text : mainTexts(namedExporter(), List.of(later, earlier))) {
            assertEquals("uuid:later", row(text, 1).get("KEY"));
            assertEquals("Oct 17, 2026 8:30:27 AM", row(text, 1).get("SubmissionDate"));
            assertEquals("uuid:earlier", row(text, 2).get("KEY"));
            assertEquals("Oct 16, 2026 8:30:27 AM", row(text, 2).get("SubmissionDate"));
        }
    }

    @Test void fourArgumentSubmissionsStillExportEndAsSubmissionDate() throws Exception {
        for (String text : mainTexts(namedExporter(), List.of(value("answer")))) {
            assertEquals(DATE, row(text, 1).get("SubmissionDate"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"<instanceName/>", "<instanceName></instanceName>", "<instanceName>default</instanceName>"})
    void instanceNameHeaderIsBetweenInstanceIdAndVersionEvenWithNoSubmissions(String definitionName) throws Exception {
        for (String text : mainTexts(exporter(definitionName, "", ""), List.of())) {
            assertEquals(List.of(List.of("SubmissionDate", "value", "instanceID", "instanceName", "formdef_version", "KEY")), csv(text));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "House 1", "  leading and trailing  ", "\u00e9\u6c34, \"quoted\"\r\nsecond line"})
    void instanceNameCellRemovesTrailingSpacesAndPreservesOtherTextAndCsvEscaping(String name) throws Exception {
        var s = namedSubmission(1, "uuid:abc", "<instanceName>" + xmlText(name) + "</instanceName>");
        String expected = name.equals("  leading and trailing  ") ? "  leading and trailing" : name;
        for (String text : mainTexts(namedExporter(), List.of(s))) {
            assertEquals(expected, row(text, 1).get("instanceName"));
        }
    }

    @Test void instanceNameCsvUsesOnlyRequiredQuotesAndDoublesEmbeddedQuotes() throws Exception {
        var s = namedSubmission(1, "uuid:abc", "<instanceName>A, &quot;B&quot;</instanceName>");
        String expected = "SubmissionDate,value,instanceID,instanceName,formdef_version,KEY\n"
                + "\"Oct 15, 2026 8:30:27 AM\",kept,uuid:abc,\"A, \"\"B\"\"\",v08,uuid:abc";
        for (String text : mainTexts(namedExporter(), List.of(s))) assertEquals(expected, text);
    }

    @Test void missingInstanceNameGivesEmptyCellWithoutUsingTheDefinitionDefault() throws Exception {
        var s = namedSubmission(1, "uuid:abc", "");
        for (String text : mainTexts(namedExporter(), List.of(s))) assertEquals("", row(text, 1).get("instanceName"));
    }

    @Test void namesAreResolvedIndependentlyForEverySubmission() throws Exception {
        var input = List.of(namedSubmission(1, "uuid:a", "<instanceName>Alpha</instanceName>"),
                namedSubmission(2, "uuid:b", ""),
                namedSubmission(3, "uuid:c", "<instanceName>Gamma</instanceName>"));
        for (String text : mainTexts(namedExporter(), input)) {
            assertEquals("Alpha", row(text, 1).get("instanceName"));
            assertEquals("", row(text, 2).get("instanceName"));
            assertEquals("Gamma", row(text, 3).get("instanceName"));
        }
    }

    @Test void nameInSubmissionDoesNotAddAColumnWhenDefinitionHasNone() throws Exception {
        var s = namedSubmission(1, "uuid:abc", "<instanceName>Unexpected</instanceName>");
        for (String text : mainTexts(exporter("", "", ""), List.of(s))) {
            assertEquals(List.of("SubmissionDate", "value", "instanceID", "formdef_version", "KEY"), csv(text).get(0));
        }
    }

    @Test void instanceNameOutsidePrimaryMetaDoesNotEnableTheColumn() throws Exception {
        var e = exporter("", "<instanceName>root decoy</instanceName><other><meta><instanceName>nested</instanceName></meta></other>",
                "<instance id=\"secondary\"><lookup><meta><instanceName>secondary</instanceName></meta></lookup></instance>");
        for (String text : mainTexts(e, List.of())) assertFalse(csv(text).get(0).contains("instanceName"));
    }

    @Test void instanceNameNestedBelowMetaIsNotADirectChildAndDoesNotEnableColumn() throws Exception {
        var e = exporter("<wrapper><instanceName>decoy</instanceName></wrapper>", "", "");
        for (String text : mainTexts(e, List.of())) assertFalse(csv(text).get(0).contains("instanceName"));
    }

    @Test void nameCellReadsOnlyTheDirectPrimaryMetaChild() throws Exception {
        String xml = "<data id=\"export_test\"><value>kept</value><instanceName>root decoy</instanceName>"
                + "<other><meta><instanceName>nested decoy</instanceName></meta></other>"
                + "<meta><instanceID>uuid:abc</instanceID><instanceName>correct</instanceName></meta></data>";
        var s = Submission.of(1, xml, START, END);
        for (String text : mainTexts(namedExporter(), List.of(s))) assertEquals("correct", row(text, 1).get("instanceName"));
    }

    @Test void decoyNameDoesNotFillAnAbsentDirectMetaName() throws Exception {
        String xml = "<data id=\"export_test\"><instanceName>root decoy</instanceName>"
                + "<meta><instanceID>uuid:abc</instanceID><wrapper><instanceName>nested decoy</instanceName></wrapper></meta></data>";
        var s = Submission.of(1, xml, START, END);
        for (String text : mainTexts(namedExporter(), List.of(s))) assertEquals("", row(text, 1).get("instanceName"));
    }
}
