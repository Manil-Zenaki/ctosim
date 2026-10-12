package ctosim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
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

        // Use only type and constraint bindings. In particular, never rerun calculations,
        // actions, preloads, repeat creation, or relevance on the submitted snapshot.
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
        String frozenXml = "<h:html xmlns:h=\"http://www.w3.org/1999/xhtml\" xmlns=\"http://www.w3.org/2002/xforms\""
                + " xmlns:jr=\"http://openrosa.org/javarosa\"><h:head><h:title>Constraint oracle</h:title><model><instance>"
                + submittedXml.replaceFirst("^\\s*<\\?xml[^?]*\\?>", "") + "</instance>" + frozenBinds
                + "</model></h:head><h:body/></h:html>";
        FormDef frozen = loadXml(frozenXml).newSimulatedSession(new SimulatedClock(end), 81731).getModel().getForm();
        List<String> violations = new ArrayList<>();
        int evaluated = inspect(frozen, frozen.getMainInstance().getRoot(), "", questions, violations);
        assertTrue(violations.isEmpty(), () -> "Final-instance constraint violations: " + violations);
        return evaluated;
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
