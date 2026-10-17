package ctosim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 12 fixtures and an independent CSV oracle; no production implementation is inspected. */
abstract class FakeAttachmentsTestSupport {
    static final long SEED = 831731L;
    private Path scratch;

    Path scratch() throws Exception {
        if (scratch == null) {
            scratch = Files.createTempDirectory(
                    Files.createDirectories(Path.of(".codex-runs")).toAbsolutePath(), "fake-attachments-");
        }
        return scratch;
    }

    @AfterEach void removeScratch() throws Exception {
        if (scratch == null) return;
        assertTrue(scratch.normalize().startsWith(Path.of(".codex-runs").toAbsolutePath().normalize()));
        try (var paths = Files.walk(scratch)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    static String xml(String instance, String binds, String body, String extra, String actions) throws Exception {
        try (var in = FakeAttachmentsTestSupport.class.getResourceAsStream(
                "/ctosim/fake-attachments/form-template.xml")) {
            assertNotNull(in, "Missing form template");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__INSTANCE__", instance).replace("__BINDS__", binds)
                    .replace("__BODY__", body).replace("__EXTRA__", extra).replace("__ACTIONS__", actions);
        }
    }

    static LoadedForm load(String xml) throws Exception {
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "fake-attachments.xml");
        }
    }

    static LoadedForm form(String instance, String binds, String body) throws Exception {
        return load(xml(instance, binds, body, "", ""));
    }

    static String attr(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
                .replace("\r", "&#13;").replace("\n", "&#10;").replace("\t", "&#9;");
    }

    static String literal(String text) {
        assertFalse(text.contains("'") && text.contains("\""), "Fixture requires a simple XPath literal");
        return text.contains("'") ? "\"" + text + "\"" : "'" + text + "'";
    }

    static String bind(String name, String type, String attrs) {
        return "<bind nodeset=\"/data/" + name + "\" type=\"" + type + "\" " + attrs + "/>";
    }

    static String calc(String name, String expression) {
        return bind(name, "string", "calculate=\"" + attr(expression) + "\"");
    }

    static String input(String name) {
        return "<input ref=\"/data/" + name + "\"><label>" + name + "</label></input>";
    }

    static String pull(String file, String value, String key, String expression) {
        return "pulldata(" + literal(file) + "," + literal(value) + "," + literal(key) + "," + expression + ")";
    }

    static String select(String name, String appearance, String column, String label) {
        return "<select1 ref=\"/data/" + name + "\" appearance=\"" + attr(appearance)
                + "\"><label>" + name + "</label><item><label>" + attr(label)
                + "</label><value>" + attr(column) + "</value></item></select1>";
    }

    static LoadedForm pulls(String keyType, String... columns) throws Exception {
        StringBuilder instance = new StringBuilder("<key/>");
        StringBuilder binds = new StringBuilder(bind("key", keyType, ""));
        for (int i = 0; i < columns.length; i++) {
            instance.append("<v").append(i).append("/>");
            binds.append(calc("v" + i, pull("people", columns[i], "id", "/data/key")));
        }
        return form(instance.toString(), binds.toString(), input("key"));
    }

    static LoadedForm levels(int count) throws Exception {
        StringBuilder instance = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < count; i++) {
            instance.append("<q").append(i).append("/>");
            binds.append(bind("q" + i, "string", ""));
            body.append(select("q" + i, "search('places')", "code" + i, "LABEL" + i));
        }
        return form(instance.toString(), binds.toString(), body.toString());
    }

    static Table table(FakeAttachments fake, String name) {
        String csv = fake.files().get(name);
        assertNotNull(csv, "Expected file " + name);
        assertFalse(csv.startsWith("\ufeff"), "CSV must not have a BOM");
        List<List<String>> records = parseCsv(csv);
        assertTrue(records.size() > 1, "A generated file has a header and data");
        List<String> header = records.get(0);
        assertEquals(header.size(), header.stream().distinct().count(), "No duplicate columns");
        List<List<String>> rows = records.subList(1, records.size());
        for (List<String> row : rows) {
            assertEquals(header.size(), row.size(), "Every row has every column");
            row.forEach(cell -> assertFalse(cell.isEmpty(), "Every column has a value"));
        }
        // Re-encode independently to enforce LF records, minimal quoting, and doubled quotes.
        String canonical = records.stream().map(row -> row.stream().map(FakeAttachmentsTestSupport::csvCell)
                .collect(Collectors.joining(","))).collect(Collectors.joining("\n")) + "\n";
        assertEquals(canonical, csv, "CSV serialization prescribed by spec 12");
        return new Table(header, rows);
    }

    private static String csvCell(String value) {
        return value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < csv.length() && csv.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '\n')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c == '\n') { records.add(List.copyOf(row)); row.clear(); }
            } else cell.append(c);
        }
        assertFalse(quoted, "CSV quoting must close");
        if (!row.isEmpty() || !cell.isEmpty()) { row.add(cell.toString()); records.add(List.copyOf(row)); }
        return records;
    }

    record Table(List<String> header, List<List<String>> rows) {
        List<String> column(String name) {
            int index = header.indexOf(name);
            assertTrue(index >= 0, "Missing column " + name + " in " + header);
            return rows.stream().map(row -> row.get(index)).toList();
        }
    }
}
