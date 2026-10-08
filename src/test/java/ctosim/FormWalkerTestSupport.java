package ctosim;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.*;

/** All expectations come from specs 07 and its explicitly referenced contracts 01/04/05/06. */
final class FormWalkerTestSupport {
    static final String METADATA = "<started/><finished/><meta><instanceID/></meta>";
    static final String METADATA_BINDS = """
        <bind nodeset="/data/started" type="dateTime" jr:preload="timestamp" jr:preloadParams="start"/>
        <bind nodeset="/data/finished" type="dateTime" jr:preload="timestamp" jr:preloadParams="end"/>
        <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
        """;

    private FormWalkerTestSupport() { }

    static String resource(String name) throws Exception {
        try (InputStream in = FormWalkerTestSupport.class.getResourceAsStream("/ctosim/form-walker/" + name)) {
            assertNotNull(in, "Missing fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static LoadedForm loadXml(String xml) throws Exception {
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "walker-fixture.xml");
        }
    }

    static LoadedForm form(String instance, String binds, String body) throws Exception {
        return form(instance, binds, body, "", "");
    }

    static LoadedForm form(String instance, String binds, String body, String actions, String secondary) throws Exception {
        return loadXml(resource("form-template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body)
                .replace("__ACTIONS__", actions).replace("__SECONDARY__", secondary));
    }

    static String bind(String path, String type, String attributes) {
        return "<bind nodeset=\"/data/" + path + "\" type=\"" + type + "\" " + attributes + "/>";
    }

    static String input(String path) {
        return "<input ref=\"/data/" + path + "\"><label>" + path + "</label></input>";
    }

    static LoadedForm single(String type, String attrs) throws Exception {
        return form("<answer/>", bind("answer", type, attrs), input("answer"));
    }

    static LoadedForm select(String control, String type, String attrs) throws Exception {
        return form("<answer/>", bind("answer", type, attrs),
                "<" + control + " ref=\"/data/answer\"><label>Choice</label>"
                + "<item><label>Twenty</label><value>20</value></item>"
                + "<item><label>Ten</label><value>10</value></item>"
                + "<item><label>Thirty</label><value>30</value></item></" + control + ">");
    }

    static SimulationConfig config(String members) throws ConfigException {
        return SimulationConfig.parse("{" + members + "}", "walker-config.json");
    }

    static SimulationConfig completeConfig() throws ConfigException {
        return config("\"seed\":731991,\"missingRate\":0,\"specialRate\":0");
    }

    static Submission walk(LoadedForm form, String configMembers) throws Exception {
        return new FormWalker(form, config(configMembers)).walk(1);
    }

    static Submission fixed(LoadedForm form, String value) throws Exception {
        // Test callers pass JSON-safe literals; Unicode is deliberately preserved.
        return walk(form, "\"missingRate\":0,\"fields\":{\"answer\":{\"value\":\"" + value + "\"}}");
    }

    static Document xml(Submission submission) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(
                submission.instanceXml().getBytes(StandardCharsets.UTF_8)));
    }

    // Match by local name because the engine may choose different namespace prefixes.
    static String xpath(String path) {
        StringBuilder expression = new StringBuilder();
        for (String part : path.split("/")) {
            int bracket = part.indexOf('[');
            String name = bracket < 0 ? part : part.substring(0, bracket);
            expression.append("/*[local-name()='").append(name).append("']");
            if (bracket >= 0) expression.append(part.substring(bracket));
        }
        return expression.toString();
    }

    static String value(Submission submission, String path) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(xpath("data/" + path), xml(submission));
    }

    static int count(Submission submission, String path) throws Exception {
        return ((NodeList) XPathFactory.newInstance().newXPath().evaluate(
                xpath("data/" + path), xml(submission), XPathConstants.NODESET)).getLength();
    }

    static long seconds(Submission submission) {
        Duration duration = Duration.between(submission.start(), submission.end());
        assertEquals(0, duration.getNano(), "Clock advances in whole seconds");
        return duration.getSeconds();
    }

    static void assertElapsed(Submission submission, int handled) {
        long seconds = seconds(submission);
        assertTrue(seconds >= 5L * handled && seconds <= 60L * handled,
                () -> handled + " handled questions must take 5..60 seconds each, got " + seconds);
    }

    static void assertStart(Submission submission, SimulationConfig config) {
        Duration offset = Duration.between(config.start(), submission.start());
        assertFalse(offset.isNegative());
        assertEquals(0, offset.getNano());
        assertEquals(0, offset.getSeconds() % 60);
        long minutes = offset.toMinutes();
        assertTrue(minutes / 1440 < config.fieldworkDays(), "Day offset is inside fieldwork");
        assertTrue(minutes % 1440 <= 540, "Minute offset is in 0..540 inclusive");
    }

    static void assertSameSubmission(Submission expected, Submission actual) {
        assertAll(() -> assertEquals(expected.number(), actual.number()),
                () -> assertEquals(expected.instanceXml(), actual.instanceXml()),
                () -> assertEquals(expected.start(), actual.start()),
                () -> assertEquals(expected.end(), actual.end()),
                () -> assertEquals(expected.answered(), actual.answered()),
                () -> assertEquals(expected.warnings(), actual.warnings()));
    }

    static void assertWarning(Submission submission, String... fragments) {
        assertTrue(submission.warnings().stream().anyMatch(w -> {
            for (String fragment : fragments) if (!w.toLowerCase(java.util.Locale.ROOT)
                    .contains(fragment.toLowerCase(java.util.Locale.ROOT))) return false;
            return true;
        }), () -> "Expected warning containing " + List.of(fragments) + ": " + submission.warnings());
    }

    static void assertUniformBin(int observed, int draws, double probability, String label) {
        double tolerance = 7 * Math.sqrt(draws * probability * (1 - probability)) + 2;
        assertEquals(draws * probability, observed, tolerance, label);
    }

    static LoadedForm repeat(boolean counted, boolean nested) throws Exception {
        String row = nested ? "<answer/><child jr:template=\"\"><leaf/></child>" : "<answer/>";
        String nestedBody = nested ? "<repeat nodeset=\"/data/row/child\">" + input("row/child/leaf") + "</repeat>" : "";
        return form("<size/><row jr:template=\"\">" + row + "</row><after/>",
                bind("size", "int", "") + bind("row/answer", "string", "")
                + (nested ? bind("row/child/leaf", "string", "") : "") + bind("after", "string", ""),
                input("size") + "<repeat nodeset=\"/data/row\" " + (counted ? "jr:count=\"/data/size\"" : "")
                + ">" + input("row/answer") + nestedBody + "</repeat>" + input("after"));
    }
}
