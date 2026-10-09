package ctosim;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only fixtures and CSV reader; expectations are derived from spec 08. */
final class SurveyCtoExporterTestSupport {
    static final LocalDateTime START = LocalDateTime.of(2026, 10, 7, 0, 5);
    static final LocalDateTime END = LocalDateTime.of(2026, 10, 15, 8, 30, 27, 987_000_000);
    static final String DATE = "Oct 15, 2026 8:30:27 AM";
    static final String BASE = "https://example.surveycto.com/view/submission-attachment/?uuid=";

    private SurveyCtoExporterTestSupport() { }

    static String resource(String name) throws Exception {
        try (var in = SurveyCtoExporterTestSupport.class.getResourceAsStream("/ctosim/surveycto-exporter/" + name)) {
            assertNotNull(in, "Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static LoadedForm load(String xml) throws Exception {
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "exporter-test.xml");
        }
    }

    static LoadedForm form(String instance, String binds, String body, String secondary) throws Exception {
        return load(resource("form-template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body).replace("__SECONDARY__", secondary));
    }

    static LoadedForm simpleForm() throws Exception {
        return form("<value/>", "<bind nodeset=\"/data/value\" type=\"string\"/>",
                "<input ref=\"/data/value\"><label>Value</label></input>", "");
    }

    static FieldInfo field(String name, String type, String path, int row) {
        return new FieldInfo(name, type, "/data/" + path, row);
    }

    static FieldCatalog catalog(Path directory, String title, String version, FieldInfo... fields) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("form_id", "export_test");
        data.put("title", title);
        data.put("version", version);
        data.put("fields", List.of(fields));
        return catalogJson(directory, new ObjectMapper().writeValueAsString(data));
    }

    static FieldCatalog catalogJson(Path directory, String json) throws Exception {
        Path path = Files.createTempFile(directory, "catalog-", ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return FieldCatalog.load(path);
    }

    static SurveyCtoExporter single(Path directory, String type) throws Exception {
        LoadedForm form = simpleForm();
        return new SurveyCtoExporter(catalog(directory, "Export", "v08", field("value", type, "value", 2)), form, "example");
    }

    static String xmlText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\r", "&#13;");
    }

    static Submission submission(int number, String id, String content) {
        return Submission.of(number, "<data id=\"export_test\">" + content
                + "<meta><instanceID>" + xmlText(id) + "</instanceID></meta></data>", START, END);
    }

    static Submission value(String value) {
        return submission(1, "uuid:abc", "<value>" + xmlText(value) + "</value>");
    }

    // Deliberately independent of any production CSV library; preserves CR/LF inside quoted cells.
    static List<List<String>> csv(String text) {
        assertFalse(text.startsWith("\ufeff"), "CSV must not have a BOM");
        assertFalse(text.endsWith("\n"), "CSV must not have a final line feed");
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else { quoted = !quoted; }
            } else if (!quoted && (c == ',' || c == '\n')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c == '\n') { rows.add(row); row = new ArrayList<>(); }
            } else {
                if (!quoted) assertNotEquals('\r', c, "Row separator must be LF alone");
                cell.append(c);
            }
        }
        assertFalse(quoted, "Unterminated CSV quote");
        row.add(cell.toString()); rows.add(row);
        int width = rows.get(0).size();
        for (List<String> r : rows) assertEquals(width, r.size(), "CSV row width");
        return rows;
    }

    static Map<String, String> row(String text, int index) {
        var rows = csv(text);
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            String key = rows.get(0).get(i);
            assertFalse(result.containsKey(key), "Unexpected duplicate header " + key);
            result.put(key, rows.get(index).get(i));
        }
        return result;
    }

    static void assertValueBoth(SurveyCtoExporter exporter, Submission submission, String expected) {
        assertAll(() -> assertEquals(expected, row(exporter.wide(List.of(submission)), 1).get("value")),
                () -> assertEquals(expected, row(exporter.longFiles(List.of(submission)).get("Export.csv"), 1).get("value")));
    }

    static SurveyCtoExporter repeats(Path directory) throws Exception {
        LoadedForm form = load(resource("repeats.xml"));
        return new SurveyCtoExporter(catalog(directory, "Repeats", "v08",
                field("before", "text", "before", 20),
                field("main", "begin group", "main", 2),
                field("member", "begin repeat", "main/member", 3),
                field("member_name", "text", "main/member/member_name", 4),
                field("info", "begin group", "main/member/info", 5),
                field("job", "begin repeat", "main/member/info/job", 6),
                field("job_title", "text", "main/member/info/job/job_title", 7),
                field("tags", "select_multiple tags", "main/member/info/job/tags", 8),
                field("gps", "geopoint", "main/member/info/job/gps", 9),
                field("member_tail", "text", "main/member/member_tail", 10),
                field("plot", "begin repeat", "main/plot", 11),
                field("crop", "text", "main/plot/crop", 12),
                field("after", "text", "after", 13)), form, "example");
    }

    static List<Submission> repeatSubmissions() {
        return List.of(
                submission(91, "uuid:B", "<limit>9</limit><before>B</before><main>"
                        + "<member><member_name>Bee</member_name><info><job><job_title>Only</job_title><tags>-97</tags><gps>1 2 3 4</gps></job></info><member_tail>end B</member_tail></member>"
                        + "<plot><crop>maize</crop></plot><plot><crop>rice</crop></plot></main><after>b</after>"),
                submission(2, "uuid:A", "<limit>9</limit><before>A</before><main>"
                        + "<member><member_name>First</member_name><info/><member_tail>end first</member_tail></member>"
                        + "<member><member_name>Second</member_name><info>"
                        + "<job><job_title>J1</job_title><tags>1 -97</tags><gps>5 6</gps></job>"
                        + "<job><job_title>J2</job_title><tags/><gps/></job></info><member_tail>end second</member_tail></member>"
                        + "<plot><crop>teff</crop></plot></main><after>a</after>"),
                submission(17, "uuid:zero", "<main/>"),
                submission(3, "uuid:absent", ""));
    }
}
