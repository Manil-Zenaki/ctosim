package ctosim;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterValueTest {
    @TempDir Path temp;

    static Stream<Arguments> dateTimes() {
        return Stream.of(
                Arguments.of("date", "2026-10-07", "Oct 7, 2026"),
                Arguments.of("today", "2024-02-29", "Feb 29, 2024"),
                Arguments.of("DATE", "2026-01-01", "Jan 1, 2026"),
                Arguments.of("time", "00:05:00", "12:05:00 AM"),
                Arguments.of("time", "12:05:09", "12:05:09 PM"),
                Arguments.of("time", "23:59:59.999", "11:59:59 PM"),
                Arguments.of("time", "08:30:20.987+03:00", "8:30:20 AM"),
                Arguments.of("time", "00:05:00-11:00", "12:05:00 AM"),
                Arguments.of("time", "13:02:03Z", "1:02:03 PM"),
                Arguments.of("datetime", "2026-10-15T08:30:27.000+03:00", "Oct 15, 2026 8:30:27 AM"),
                Arguments.of("datetime", "2026-12-31T23:59:59.999-11:00", "Dec 31, 2026 11:59:59 PM"),
                Arguments.of("datetime", "2026-01-01T00:05:00+14:00", "Jan 1, 2026 12:05:00 AM"),
                Arguments.of("datetime", "2026-09-02T12:00:00Z", "Sep 2, 2026 12:00:00 PM"),
                Arguments.of("start", "2026-10-15T08:30:27", DATE),
                Arguments.of("end", "2026-10-15T08:30:27.123456789", DATE));
    }

    @ParameterizedTest(name = "{0}: {1}") @MethodSource("dateTimes")
    void datesAndTimesUseEnglishWallClockAndDiscardOffsetsAndFractions(String type, String stored, String expected) throws Exception {
        assertValueBoth(single(temp, type), value(stored), expected);
    }

    static Stream<Arguments> months() {
        String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return IntStream.rangeClosed(1, 12).mapToObj(m -> Arguments.of(String.format(Locale.ROOT, "2026-%02d-07", m), names[m - 1] + " 7, 2026"));
    }

    @ParameterizedTest @MethodSource("months")
    void allMonthAbbreviationsAreExactlyThreeEnglishLetters(String stored, String expected) throws Exception {
        assertValueBoth(single(temp, "date"), value(stored), expected);
    }

    static Stream<Arguments> invalidTemporalValues() {
        return Stream.of("date", "today", "time", "datetime", "start", "end")
                .flatMap(type -> Stream.of("", "not-a-date", "2026-99-99", "99:99:99", "2026-10-15T99:99:99", "  garbage  ")
                        .map(stored -> Arguments.of(type, stored)));
    }

    @ParameterizedTest @MethodSource("invalidTemporalValues")
    void unreadableAndEmptyTemporalValuesArePreserved(String type, String stored) throws Exception {
        assertValueBoth(single(temp, type), value(stored), stored);
    }

    @ParameterizedTest @ValueSource(strings = {"text", "integer", "decimal", "select_one codes", "barcode", "geotrace", "geoshape", "calculate", "calculate_here", "comments", "audio audit", "speed violations limit=5", "sensor_statistic accelerometer", "deviceid", "unknown_extension", "TEXT", "AuDiO audit"})
    void otherTypesAreStoredVerbatimIncludingSpecialMultiwordTypes(String type) throws Exception {
        String stored = " 2026-10-15T08:30:27+03:00, é & \"家庭\" \r\n ";
        assertValueBoth(single(temp, type), value(stored), stored);
    }

    @ParameterizedTest @ValueSource(strings = {"2026-10-07", "08:30:20", "2026-10-15T08:30:27.000+03:00", "0001.2300", "a.jpg"})
    void calculateNeverCoercesValuesThatLookLikeOtherTypes(String stored) throws Exception {
        assertValueBoth(single(temp, "calculate"), value(stored), stored);
    }

    @Test void formattingDoesNotDependOnDefaultLocaleOrTimeZone() throws Exception {
        Locale old = Locale.getDefault();
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var e = single(temp, "DATETIME");
            assertValueBoth(e, value("2026-09-01T00:05:00+14:00"), "Sep 1, 2026 12:05:00 AM");
            assertEquals(DATE, row(e.wide(List.of(value(""))), 1).get("SubmissionDate"));
        } finally {
            Locale.setDefault(old); Locale.setDefault(Locale.Category.FORMAT, format);
            Locale.setDefault(Locale.Category.DISPLAY, display); TimeZone.setDefault(zone);
        }
    }

    static Stream<Arguments> csvCells() {
        return Stream.of(Arguments.of("", ""), Arguments.of("plain", "plain"),
                Arguments.of(" leading and trailing ", " leading and trailing "),
                Arguments.of("a\tb", "a\tb"), Arguments.of("家庭 é 😀", "家庭 é 😀"),
                Arguments.of("a,b", "\"a,b\""), Arguments.of("a\"b", "\"a\"\"b\""),
                Arguments.of("a\rb", "\"a\rb\""), Arguments.of("a\nb", "\"a\nb\""),
                Arguments.of("a,\"b\"\r\nc", "\"a,\"\"b\"\"\r\nc\""));
    }

    @ParameterizedTest @MethodSource("csvCells")
    void csvQuotesOnlyRequiredCellsDoublesQuotesAndUsesSingleLfBetweenRows(String stored, String encoded) throws Exception {
        var e = single(temp, "text");
        var submissions = List.of(value(stored));
        String expected = "SubmissionDate,value,instanceID,formdef_version,KEY\n\"" + DATE + "\"," + encoded + ",uuid:abc,v08,uuid:abc";
        assertEquals(expected, e.wide(submissions));
        assertEquals(expected, e.longFiles(submissions).get("Export.csv"));
    }

    @Test void xmlTextContentDecodesEntitiesAndCdataWithoutTrimming() throws Exception {
        var s = submission(1, "uuid:abc", "<value>  &amp;&lt;<![CDATA[a,b\"é]]>&gt;  </value>");
        assertValueBoth(single(temp, "text"), s, "  &<a,b\"é>  ");
    }

    static Stream<Arguments> points() {
        return Stream.of(Arguments.of("", List.of("", "", "", "")),
                Arguments.of("1", List.of("1", "", "", "")),
                Arguments.of("1 -2", List.of("1", "-2", "", "")),
                Arguments.of("1 -2 3.00", List.of("1", "-2", "3.00", "")),
                Arguments.of("1 -2 3.00 0.50", List.of("1", "-2", "3.00", "0.50")),
                Arguments.of("1 -2 3 4 ignored", List.of("1", "-2", "3", "4")));
    }

    @ParameterizedTest @MethodSource("points")
    void geopointHasOnlyFourComponentColumnsWithMissingItemsEmpty(String stored, List<String> components) throws Exception {
        var e = single(temp, "geopoint");
        for (String text : List.of(e.wide(List.of(value(stored))), e.longFiles(List.of(value(stored))).get("Export.csv"))) {
            var rows = csv(text);
            assertEquals(List.of("SubmissionDate", "value-Latitude", "value-Longitude", "value-Altitude", "value-Accuracy", "instanceID", "formdef_version", "KEY"), rows.get(0));
            assertEquals(components, rows.get(1).subList(1, 5));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"image", "audio", "video", "file", "IMAGE", "image options"})
    void attachmentUrlsUseUtf8FormEncodingOfBothIdentifierAndFilename(String type) throws Exception {
        var s = submission(1, "uuid:é +/&?", "<value>café +/&amp;?.jpg</value>");
        assertValueBoth(single(temp, type), s,
                BASE + "uuid%3A%C3%A9+%2B%2F%26%3F&file=caf%C3%A9+%2B%2F%26%3F.jpg");
    }

    @ParameterizedTest @ValueSource(strings = {"image", "audio", "video", "file"})
    void absentOrEmptyMediaNeverProducesAnAttachmentUrl(String type) throws Exception {
        var e = single(temp, type);
        assertValueBoth(e, value(""), "");
        assertValueBoth(e, submission(1, "uuid:abc", ""), "");
    }

    @ParameterizedTest @ValueSource(strings = {"", "ignored-file.csv"})
    void textAuditUsesSynthesizedFilenameWheneverElementIsPresent(String stored) throws Exception {
        assertValueBoth(single(temp, "text audit"), value(stored), BASE + "uuid%3Aabc&file=TA_abc.csv");
    }

    @Test void absentTextAuditHasEmptyCell() throws Exception {
        assertValueBoth(single(temp, "text audit"), submission(1, "uuid:abc", ""), "");
    }

    @Test void synthesizedTextAuditFilenameIsAlsoUtf8FormEncoded() throws Exception {
        assertValueBoth(single(temp, "text audit"), submission(1, "uuid:é +", "<value/>"),
                BASE + "uuid%3A%C3%A9+%2B&file=TA_%C3%A9+%2B.csv");
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "uuid:uuid:abc", "prefix-uuid:abc"})
    void textAuditStripsOnlyALeadingUuidPrefix(String id) throws Exception {
        String filenameId = id.startsWith("uuid:") ? id.substring(5) : id;
        String expected = BASE + id.replace(":", "%3A") + "&file=TA_" + filenameId.replace(":", "%3A") + ".csv";
        assertValueBoth(single(temp, "text audit"), submission(1, id, "<value/>"), expected);
    }

    private SurveyCtoExporter select(boolean filtered) throws Exception {
        String secondary = "";
        String choices;
        if (filtered) {
            secondary = "<instance id=\"choices\"><root>"
                    + "<item><name>2</name><label>Two</label><enabled>yes</enabled></item>"
                    + "<item><name>-99</name><label>Unknown</label><enabled>no</enabled></item>"
                    + "<item><name>1</name><label>One</label><enabled>yes</enabled></item>"
                    + "<item><name>2</name><label>Two again</label><enabled>no</enabled></item>"
                    + "<item><name>a-b-c</name><label>Letters</label><enabled>no</enabled></item>"
                    + "</root></instance>";
            choices = "<itemset nodeset=\"instance('choices')/root/item[enabled='yes']\"><value ref=\"name\"/><label ref=\"label\"/></itemset>";
        } else {
            choices = "<item><label>Two</label><value>2</value></item>"
                    + "<item><label>Unknown</label><value>-99</value></item>"
                    + "<item><label>One</label><value>1</value></item>"
                    + "<item><label>Two again</label><value>2</value></item>"
                    + "<item><label>Letters</label><value>a-b-c</value></item>";
        }
        var form = form("<value/>", "<bind nodeset=\"/data/value\" type=\"string\"/>",
                "<select ref=\"/data/value\"><label>Choices</label>" + choices + "</select>", secondary);
        return new SurveyCtoExporter(catalog(temp, "Export", null, field("value", "SeLeCt_MuLtIpLe choices", "value", 2)), form, "example");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void dummyColumnsUseAllChoicesInFirstOccurrenceOrderWithoutChoiceFilter(boolean filtered) throws Exception {
        var e = select(filtered);
        var s = value("-99 1 a-b-c");
        for (String text : List.of(e.wide(List.of(s)), e.longFiles(List.of(s)).get("Export.csv"))) {
            assertEquals(List.of("SubmissionDate", "value", "value_2", "value__99", "value_1", "value_a_b_c", "instanceID", "formdef_version", "KEY"), csv(text).get(0));
            assertEquals(List.of("-99 1 a-b-c", "0", "1", "1", "1"), csv(text).get(1).subList(1, 6));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"12 -990 unknown", "2 2", ""})
    void dummySelectionUsesWholeItemsAndEmptySelectionsHaveEmptyDummies(String stored) throws Exception {
        var e = select(false);
        List<String> expected = stored.isEmpty() ? List.of("", "", "", "")
                : stored.equals("2 2") ? List.of("1", "0", "0", "0") : List.of("0", "0", "0", "0");
        for (String text : List.of(e.wide(List.of(value(stored))), e.longFiles(List.of(value(stored))).get("Export.csv"))) {
            assertEquals(stored, csv(text).get(1).get(1));
            assertEquals(expected, csv(text).get(1).subList(2, 6));
        }
    }

    @Test void absentSelectElementHasEmptyDummyCellsAndStillContributesColumns() throws Exception {
        var e = select(true);
        String text = e.wide(List.of(submission(1, "uuid:abc", "")));
        assertEquals(List.of("", "", "", "", ""), csv(text).get(1).subList(1, 6));
        assertEquals(csv(text).get(0), csv(e.wide(List.of())).get(0));
    }

    @Test void csvEscapingAlsoAppliesToHeadersExpandedFromChoiceValues() throws Exception {
        var form = form("<value/>", "", "<select ref=\"/data/value\"><label>Choice</label>"
                + "<item><label>Comma</label><value>a,b</value></item>"
                + "<item><label>Quote</label><value>q&quot;x</value></item></select>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Export", null,
                field("value", "select_multiple codes", "value", 2)), form, "example");
        String header = "SubmissionDate,value,\"value_a,b\",\"value_q\"\"x\",instanceID,formdef_version,KEY";
        assertEquals(header, e.wide(List.of()));
        assertEquals(header, e.longFiles(List.of()).get("Export.csv"));
    }
}
