package ctosim;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.xpath.XPathParseTool;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Evaluates source-form constraints against a frozen copy of the submitted XML.
 * No walker internals, reparsing of calculated values, or answering/navigation is used. */
final class FormWalkerAmendment5ConstraintOracle {
    private static final Set<String> CONTROLS = Set.of("input", "select", "select1", "trigger", "upload", "range");
    private static final Set<String> INPUT_TYPES = Set.of("string", "int", "integer", "long", "decimal", "date",
            "time", "dateTime", "geopoint", "geotrace", "geoshape", "barcode");

    private FormWalkerAmendment5ConstraintOracle() { }

    static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    static String attribute(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    static int assertConstraints(Document definition, String submittedXml, java.time.LocalDateTime end) throws Exception {
        Map<String, Element> binds = new LinkedHashMap<>();
        Map<String, String> ids = new LinkedHashMap<>();
        var allBinds = definition.getElementsByTagNameNS("*", "bind");
        for (int i = 0; i < allBinds.getLength(); i++) {
            Element bind = (Element) allBinds.item(i);
            String path = bind.getAttribute("nodeset");
            assertTrue(path.startsWith("/"), "Oracle requires absolute bind paths: " + path);
            binds.put(path, bind);
            if (bind.hasAttribute("id")) ids.put(bind.getAttribute("id"), path);
        }
        Map<String, Element> questions = new LinkedHashMap<>();
        var controls = definition.getElementsByTagNameNS("*", "body").item(0).getChildNodes();
        collectControls(controls, binds, ids, questions);

        // Use only type and constraint bindings. Keep repeat declarations for index(),
        // but never rerun calculations, actions, preloads, counts, or relevance.
        Document snapshot = parse(submittedXml);
        StringBuilder frozenBinds = new StringBuilder();
        for (var entry : binds.entrySet()) {
            var present = (org.w3c.dom.NodeList) javax.xml.xpath.XPathFactory.newInstance().newXPath().evaluate(
                    xpath(entry.getKey().substring(1)), snapshot, javax.xml.xpath.XPathConstants.NODESET);
            if (present.getLength() == 0) continue;
            String type = entry.getValue().getAttribute("type");
            if (type.equals("select") || type.equals("select1")) type = "string";
            frozenBinds.append("<bind nodeset=\"").append(attribute(entry.getKey())).append("\"");
            if (!type.isEmpty()) frozenBinds.append(" type=\"").append(attribute(type)).append("\"");
            String rule = entry.getValue().getAttribute("constraint");
            if (!rule.isEmpty()) frozenBinds.append(" constraint=\"").append(attribute(rule)).append("\"");
            frozenBinds.append("/>");
        }
        Element primary = (Element) definition.getElementsByTagNameNS("*", "instance").item(0);
        String frozenBody = freezeRepeats(controls, ids, primary, snapshot, false);
        StringWriter instance = new StringWriter();
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(new DOMSource(snapshot), new StreamResult(instance));
        String frozenXml = "<h:html xmlns:h=\"http://www.w3.org/1999/xhtml\" xmlns=\"http://www.w3.org/2002/xforms\""
                + " xmlns:jr=\"http://openrosa.org/javarosa\"><h:head><h:title>Constraint oracle</h:title><model><instance>"
                + instance + "</instance>" + frozenBinds
                + "</model></h:head><h:body>" + frozenBody + "</h:body></h:html>";
        FormDef frozen = loadXml(frozenXml).newSimulatedSession(new SimulatedClock(end), 81731).getModel().getForm();
        List<String> violations = new ArrayList<>();
        int evaluated = inspect(frozen, frozen.getMainInstance().getRoot(), "", questions, violations);
        assertTrue(violations.isEmpty(), () -> "Final-instance constraint violations: " + violations);
        return evaluated;
    }

    private static String freezeRepeats(org.w3c.dom.NodeList controls, Map<String, String> ids,
            Element primary, Document snapshot, boolean insideRepeat) throws Exception {
        StringBuilder body = new StringBuilder();
        var selector = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        for (int i = 0; i < controls.getLength(); i++) {
            if (!(controls.item(i) instanceof Element control)) continue;
            if (!control.getLocalName().equals("repeat")) {
                body.append(freezeRepeats(control.getChildNodes(), ids, primary, snapshot, insideRepeat));
                continue;
            }
            String path = control.hasAttribute("bind") ? ids.get(control.getAttribute("bind")) : control.getAttribute("nodeset");
            assertNotNull(path, "Unknown repeat binding");
            assertTrue(path.startsWith("/"), "Oracle requires absolute repeat paths: " + path);
            var present = (org.w3c.dom.NodeList) selector.evaluate(xpath(path.substring(1)), snapshot,
                    javax.xml.xpath.XPathConstants.NODESET);
            if (present.getLength() == 0) continue; // Do not recreate an omitted repeat or its parent groups.
            Element source = (Element) selector.evaluate("." + xpath(path.substring(1)), primary,
                    javax.xml.xpath.XPathConstants.NODE);
            assertNotNull(source, "Missing source repeat structure: " + path);
            String parentPath = path.substring(1, path.lastIndexOf('/'));
            var parents = (org.w3c.dom.NodeList) selector.evaluate(xpath(parentPath), snapshot,
                    javax.xml.xpath.XPathConstants.NODESET);
            // Nested templates belong inside the outer template, already copied with it,
            // never inside the submitted (non-template) parent instances.
            for (int p = 0; !insideRepeat && p < parents.getLength(); p++) {
                Element parent = (Element) parents.item(p);
                boolean hasTemplate = false;
                for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element element && element.getLocalName().equals(source.getLocalName())
                            && element.hasAttributeNS("http://openrosa.org/javarosa", "template")) hasTemplate = true;
                }
                if (!hasTemplate) {
                    Element template = (Element) snapshot.importNode(source, true);
                    clearTemplateValues(template);
                    template.setAttributeNS("http://openrosa.org/javarosa", "jr:template", "");
                    parent.insertBefore(template, parent.getFirstChild());
                }
            }
            // Only the structure is copied: jr:count and other dynamic attributes are deliberately absent.
            body.append("<repeat nodeset=\"").append(attribute(path)).append("\">")
                    .append(freezeRepeats(control.getChildNodes(), ids, primary, snapshot, true)).append("</repeat>");
        }
        return body.toString();
    }

    private static void clearTemplateValues(Element element) {
        for (var child = element.getFirstChild(); child != null;) {
            var next = child.getNextSibling();
            if (child instanceof Element nested) clearTemplateValues(nested);
            else element.removeChild(child);
            child = next;
        }
    }

    private static void collectControls(org.w3c.dom.NodeList nodes, Map<String, Element> binds,
            Map<String, String> ids, Map<String, Element> questions) {
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element control)) continue;
            if (CONTROLS.contains(control.getLocalName())) {
                String path = control.hasAttribute("bind") ? ids.get(control.getAttribute("bind")) : control.getAttribute("ref");
                assertNotNull(path, "Unknown control binding");
                assertTrue(path.startsWith("/"), "Oracle requires absolute control paths: " + path);
                Element bind = binds.get(path);
                if (bind != null && bind.hasAttribute("constraint")
                        && !bind.hasAttributeNS("urn:ctosim:xforms", "calculate-here")
                        && ((!control.getLocalName().equals("input") && !control.getLocalName().equals("range"))
                        || INPUT_TYPES.contains(bind.getAttribute("type")))) questions.put(path, bind);
            }
            collectControls(control.getChildNodes(), binds, ids, questions);
        }
    }

    private static int inspect(FormDef frozen, TreeElement node, String parent,
            Map<String, Element> questions, List<String> violations) throws Exception {
        if (node.getMult() < 0) return 0;
        String path = parent + "/" + node.getName();
        int evaluated = 0;
        Element bind = questions.get(path);
        if (bind != null && node.getValue() != null) {
            EvaluationContext context = new EvaluationContext(frozen.getEvaluationContext(), node.getRef());
            String readonly = bind.getAttribute("readonly");
            if (readonly.isEmpty() || !evaluate(frozen, context, readonly)) {
                evaluated++;
                if (!evaluate(frozen, context, bind.getAttribute("constraint")))
                    violations.add(node.getRef() + " = " + node.getValue().getValue() + " violates " + bind.getAttribute("constraint"));
            }
        }
        for (int i = 0; i < node.getNumChildren(); i++)
            evaluated += inspect(frozen, node.getChildAt(i), path, questions, violations);
        return evaluated;
    }

    private static boolean evaluate(FormDef form, EvaluationContext context, String expression) throws Exception {
        return (Boolean) XPathParseTool.parseXPath("boolean(" + expression + ")").eval(form.getMainInstance(), context);
    }
}
