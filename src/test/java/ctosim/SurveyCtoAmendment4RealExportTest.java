package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("FormWalker temporarily uses the configured default time zone")
class SurveyCtoAmendment4RealExportTest {
    private static final Path FORM = Path.of("samples/xforms/ctosim_coverage3.xml");
    private static final Path MEDIA = Path.of("samples/coverage3");
    private static final Path EXPORT = MEDIA.resolve("wide/ctosim coverage test 3_WIDE.csv");
    private static final Set<String> EXCLUDED = Set.of("f_today", "f_now", "f_date_before_today",
            "f_decimal_date_time", "f_decimal_time", "f_version", "f_uuid", "f_random", "f_format_number");

    @Test void thirdCoverageFormLoadsWithItsMediaAndInitializesAnOrdinarySession() throws Exception {
        assertNotNull(FormLoader.load(FORM, MEDIA).newEntryController());
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 17}) @Timeout(120)
    void everyStableOracleFieldMatchesAllFourRealExportRowsWithDefaultConfiguration(int number) throws Exception {
        Map<String, String> expected = oracle();
        var submission = new FormWalker(FormLoader.load(FORM, MEDIA), SimulationConfig.defaults()).walk(number);
        Document instance = FormWalkerTestSupport.xml(submission);
        var xpath = XPathFactory.newInstance().newXPath();
        assertEquals(1, ((NodeList) xpath.evaluate(FormWalkerTestSupport.xpath("data/oracle"),
                instance, XPathConstants.NODESET)).getLength(), "The oracle group must exist");
        List<Executable> checks = new ArrayList<>();
        for (var entry : expected.entrySet()) checks.add(() -> assertEquals(entry.getValue(),
                xpath.evaluate(FormWalkerTestSupport.xpath("data/oracle/" + entry.getKey()), instance),
                "submission " + number + ", oracle/" + entry.getKey()));
        assertAll("All stable fields in /data/oracle", checks);
    }

    private static Map<String, String> oracle() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document source;
        try (var in = Files.newInputStream(FORM)) { source = factory.newDocumentBuilder().parse(in); }
        var xpath = XPathFactory.newInstance().newXPath();
        // Derive the field inventory from the published form, not from generated output:
        // this catches missing output fields, and excludes repeat groups and their children.
        NodeList fields = (NodeList) xpath.evaluate(
                "//*[local-name()='instance']/*[local-name()='data']/*[local-name()='oracle']/*[not(*)]",
                source, XPathConstants.NODESET);
        List<List<String>> csv = csv(Files.readString(EXPORT, StandardCharsets.UTF_8));
        assertEquals(5, csv.size(), "Header and the four published submissions");
        List<String> header = csv.get(0);
        Map<String, String> expected = new LinkedHashMap<>();
        for (int i = 0; i < fields.getLength(); i++) {
            String name = fields.item(i).getLocalName();
            if (EXCLUDED.contains(name)) continue;
            int column = header.indexOf(name);
            assertTrue(column >= 0, "Export column missing for " + name);
            assertEquals(column, header.lastIndexOf(name), "Ambiguous export column " + name);
            String value = csv.get(1).get(column);
            for (int row = 2; row < csv.size(); row++) assertEquals(value, csv.get(row).get(column),
                    "Spec says stable values agree in all four rows: " + name);
            expected.put(name, value);
        }
        assertTrue(expected.size() > 50, "Inventory must cover the whole oracle, not a handful of functions");
        assertTrue(expected.keySet().containsAll(Set.of("f_hash", "f_format_date", "f_date_from_number",
                "agg_count_if", "agg_sum_if", "agg_join_if", "agg_min_if", "agg_max_if", "agg_rank_if", "never_shown")));
        return expected;
    }

    /** Small test-only CSV reader: preserve quoted newlines, commas, doubled quotes and blank cells. */
    private static List<List<String>> csv(String text) {
        if (text.startsWith("\ufeff")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '\r' || c == '\n')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c != ',') {
                    rows.add(row); row = new ArrayList<>();
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                }
            } else cell.append(c);
        }
        assertFalse(quoted, "Published CSV has an unterminated quote");
        if (!row.isEmpty() || cell.length() != 0) { row.add(cell.toString()); rows.add(row); }
        assertFalse(rows.isEmpty());
        for (var r : rows) assertEquals(rows.get(0).size(), r.size(), "Published CSV row width");
        return rows;
    }
}
