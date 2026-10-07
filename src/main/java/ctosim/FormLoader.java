package ctosim;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.javarosa.core.model.FormDef;
import org.javarosa.model.xform.XFormsModule;
import org.javarosa.xform.util.XFormUtils;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Reads a SurveyCTO form definition (XForm). See specs/01-form-loader.md. */
public final class FormLoader {

    static final String XHTML_NS = "http://www.w3.org/1999/xhtml";
    static final String XFORMS_NS = "http://www.w3.org/2002/xforms";

    private static final String[] BIND_EXPRESSIONS = {"calculate", "relevant", "constraint", "required", "readonly"};
    private static final String[] BODY_EXPRESSIONS = {"ref", "nodeset"};

    static {
        // Engine-wide setup required by some native functions, e.g. jr:choice-name.
        new XFormsModule().registerModule();
    }

    private FormLoader() {
    }

    public static LoadedForm load(Path xform) throws FormLoadException {
        Objects.requireNonNull(xform, "xform");
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(xform);
        } catch (NoSuchFileException e) {
            throw new FormLoadException(xform + ": the file does not exist", e);
        } catch (IOException e) {
            throw new FormLoadException(xform + ": the file cannot be read (" + e + ")", e);
        }
        Path fileName = xform.getFileName();
        return parse(bytes, fileName == null ? xform.toString() : fileName.toString());
    }

    public static LoadedForm load(InputStream in, String sourceName) throws FormLoadException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(sourceName, "sourceName");
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new FormLoadException(sourceName + ": the input cannot be read (" + e.getMessage() + ")", e);
        }
        return parse(bytes, sourceName);
    }

    private static LoadedForm parse(byte[] raw, String source) throws FormLoadException {
        byte[] xml = stripBom(raw);
        if (new String(xml, StandardCharsets.UTF_8).isBlank()) {
            throw new FormLoadException(source + ": the input is empty");
        }

        Document doc = readXml(xml, source);
        Element root = doc.getDocumentElement();
        if (!isElement(root, XHTML_NS, "html")) {
            throw new FormLoadException(source + ": this is not an XForm (the root element is not an XHTML html element)");
        }
        Element head = child(root, XHTML_NS, "head");
        Element model = head == null ? null : child(head, XFORMS_NS, "model");
        if (model == null) {
            throw new FormLoadException(source + ": this is not an XForm (no model element in the head)");
        }
        Element instance = child(model, XFORMS_NS, "instance");
        Element primary = instance == null ? null : firstElement(instance);
        if (primary == null) {
            throw new FormLoadException(source + ": the form has no primary instance");
        }
        if (!primary.hasAttribute("id")) {
            throw new FormLoadException(source + ": the primary instance root <" + primary.getLocalName()
                    + "> has no id attribute");
        }
        String formId = primary.getAttribute("id");
        String version = primary.hasAttribute("version") ? primary.getAttribute("version") : null;
        Element titleElement = child(head, XHTML_NS, "title");
        String title = titleElement == null ? "" : titleElement.getTextContent().strip();

        FormDef probe = parseWithEngine(xml, source);
        SortedSet<String> stubbed = stubbedFunctions(doc, probe, source);

        LoadedForm form = new LoadedForm(xml, source, formId, version, title,
                Collections.unmodifiableSortedSet(stubbed));
        try {
            form.newEntryController();
        } catch (RuntimeException e) {
            throw new FormLoadException(source + ": the form engine could not initialise the form ("
                    + e.getMessage() + ")", e);
        }
        return form;
    }

    /** Parses the form with JavaRosa and registers the SurveyCTO stubs on its evaluation context. */
    static FormDef parseWithEngine(byte[] xml, String source) throws FormLoadException {
        try {
            FormDef def = XFormUtils.getFormFromInputStream(new ByteArrayInputStream(xml));
            if (def == null) {
                throw new FormLoadException(source + ": the form engine could not parse the form");
            }
            SurveyCtoFunctions.register(def.getEvaluationContext());
            return def;
        } catch (FormLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new FormLoadException(source + ": the form engine rejected the form (" + e.getMessage() + ")", e);
        }
    }

    private static SortedSet<String> stubbedFunctions(Document doc, FormDef parsed, String source)
            throws FormLoadException {
        FunctionReferences refs = new FunctionReferences();
        try {
            for (Element bind : descendants(doc.getDocumentElement(), XFORMS_NS, "bind")) {
                addAttributes(refs, bind, BIND_EXPRESSIONS);
            }
            for (Element setvalue : descendants(doc.getDocumentElement(), XFORMS_NS, "setvalue")) {
                addAttributes(refs, setvalue, "value");
            }
            Element body = child(doc.getDocumentElement(), XHTML_NS, "body");
            if (body != null) {
                addAttributes(refs, body, BODY_EXPRESSIONS);
                for (Element e : descendants(body, null, null)) {
                    addAttributes(refs, e, BODY_EXPRESSIONS);
                }
            }
        } catch (XPathSyntaxException e) {
            throw new FormLoadException(source + ": an XPath expression does not parse (" + e.getMessage() + ")", e);
        }

        TreeSet<String> stubbed = new TreeSet<>(refs.names());
        stubbed.removeAll(SurveyCtoFunctions.ENGINE_BUILT_INS);
        for (String handler : parsed.getEvaluationContext().getFunctionHandlers().keySet()) {
            if (!SurveyCtoFunctions.isNamedStub(handler)) {
                stubbed.remove(handler); // a handler the engine itself registered
            }
        }
        return stubbed;
    }

    private static void addAttributes(FunctionReferences refs, Element e, String... names) throws XPathSyntaxException {
        for (String name : names) {
            if (e.hasAttribute(name)) {
                refs.add(e.getAttribute(name));
            }
        }
    }

    private static Document readXml(byte[] xml, String source) throws FormLoadException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXException e) {
            throw new FormLoadException(source + ": the input is not well-formed XML (" + e.getMessage() + ")", e);
        } catch (IOException | ParserConfigurationException e) {
            throw new FormLoadException(source + ": the XML cannot be read (" + e.getMessage() + ")", e);
        }
    }

    private static final ErrorHandler SILENT = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private static byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            return Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return bytes;
    }

    private static boolean isElement(Node node, String ns, String localName) {
        return node instanceof Element && localName.equals(node.getLocalName()) && ns.equals(node.getNamespaceURI());
    }

    private static Element child(Element parent, String ns, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (isElement(n, ns, localName)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static Element firstElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }

    /** Descendant elements with the given name, or all descendants when {@code localName} is null. */
    private static java.util.List<Element> descendants(Element root, String ns, String localName) {
        NodeList list = localName == null
                ? root.getElementsByTagNameNS("*", "*")
                : root.getElementsByTagNameNS(ns, localName);
        java.util.List<Element> result = new java.util.ArrayList<>(list.getLength());
        for (int i = 0; i < list.getLength(); i++) {
            result.add((Element) list.item(i));
        }
        return result;
    }
}
