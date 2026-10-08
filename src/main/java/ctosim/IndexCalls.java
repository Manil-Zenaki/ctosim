package ctosim;

import java.io.ByteArrayOutputStream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Gives every {@code index()} call in the form's logic its own, distinct expression.
 *
 * <p>The engine merges binds whose expressions are identical into one computation and
 * evaluates it once, in the common ancestor of their nodes. For an expression that
 * references no field, such as {@code index()}, that ancestor is the form root, so every
 * repeat instance would get position 1. Rewriting each call as {@code ctosim-index(N)}, with
 * N unique in the form, keeps the expressions apart, so each is evaluated on its own node.
 * {@code ctosim-index} behaves exactly like {@code index()} and ignores its argument.
 */
final class IndexCalls {

    static final String INTERNAL_NAME = "ctosim-index";

    private static final String[] BIND_EXPRESSIONS = {"calculate", "relevant", "constraint", "required", "readonly"};

    private IndexCalls() {
    }

    /** The form with its index() calls rewritten, or {@code original} when it has none. */
    static byte[] rewrite(Document doc, byte[] original) throws TransformerException {
        int[] counter = {0};
        boolean changed = false;
        changed |= rewriteAttributes(doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "bind"), BIND_EXPRESSIONS, counter);
        changed |= rewriteAttributes(doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "setvalue"), new String[] {"value"}, counter);
        if (!changed) {
            return original;
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream(original.length + 256);
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    private static boolean rewriteAttributes(NodeList elements, String[] attributes, int[] counter) {
        boolean changed = false;
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            for (String attribute : attributes) {
                if (e.hasAttribute(attribute)) {
                    String before = e.getAttribute(attribute);
                    String after = rewriteExpression(before, counter);
                    if (!after.equals(before)) {
                        e.setAttribute(attribute, after);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** Replaces each call {@code index()} (outside string literals) with a numbered internal call. */
    static String rewriteExpression(String expr, int[] counter) {
        StringBuilder out = new StringBuilder(expr.length() + 16);
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'' || c == '"') {
                int end = expr.indexOf(c, i + 1);
                end = end < 0 ? expr.length() : end + 1;
                out.append(expr, i, end);
                i = end;
                continue;
            }
            int callEnd = indexCallEnd(expr, i);
            if (callEnd > 0) {
                out.append(INTERNAL_NAME).append('(').append(++counter[0]).append(')');
                i = callEnd;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** End of an {@code index ( )} call starting at {@code at}, or -1 when there is none. */
    private static int indexCallEnd(String expr, int at) {
        if (!expr.startsWith("index", at) || (at > 0 && isNameChar(expr.charAt(at - 1)))) {
            return -1;
        }
        int j = skipSpaces(expr, at + "index".length());
        if (j >= expr.length() || expr.charAt(j) != '(') {
            return -1;
        }
        j = skipSpaces(expr, j + 1);
        return j < expr.length() && expr.charAt(j) == ')' ? j + 1 : -1;
    }

    private static int skipSpaces(String s, int from) {
        while (from < s.length() && Character.isWhitespace(s.charAt(from))) {
            from++;
        }
        return from;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }
}
