package ctosim;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Rewrites the form's own expressions where the engine's behaviour differs from SurveyCTO's.
 * The engine parses the rewritten copy; the user's form is never changed.
 *
 * <ul>
 *   <li><b>{@code index()}.</b> The engine merges binds whose expressions are identical and
 *   evaluates them once, in the common ancestor of their nodes. For an expression that
 *   references no field, that ancestor is the form root, so every repeat instance would get
 *   position 1. Each call becomes {@code ctosim-index(N)}, with N unique in the form, which
 *   keeps the expressions apart. {@code ctosim-index} behaves like {@code index()}.</li>
 *   <li><b>Select functions on numbers.</b> SurveyCTO stores every value as text, so
 *   {@code selected(${arm}, '1')} works when {@code arm} was calculated as a number; the
 *   engine stores such results as numbers and its select functions then fail. The first
 *   argument of {@code selected}, {@code selected-at} and {@code count-selected} is wrapped in
 *   {@code string()}, which leaves text values unchanged.</li>
 *   <li><b>Time and randomness.</b> {@code today()}, {@code now()}, {@code random()} and
 *   {@code uuid()} become internal functions whose meaning depends on the session (native, or
 *   simulated clock and seed; see {@link SessionEnvironment}). {@code random()} and
 *   {@code uuid()} are numbered like {@code index()}, so that two identical
 *   {@code once(random())} binds are not merged into a single draw.</li>
 * </ul>
 */
final class EngineExpressions {

    static final String INTERNAL_INDEX = "ctosim-index";

    private static final Set<String> SELECT_FUNCTIONS = Set.of("selected", "selected-at", "count-selected");

    /** Zero-argument calls replaced by internal functions; see the class comment and SessionEnvironment. */
    private static final Map<String, String> ZERO_ARGUMENT_REWRITES = Map.of(
            "index", INTERNAL_INDEX,
            "today", SessionEnvironment.TODAY,
            "now", SessionEnvironment.NOW,
            "random", SessionEnvironment.RANDOM,
            "uuid", SessionEnvironment.UUID);

    /** Calls whose identical copies the engine must not merge into one evaluation. */
    private static final Set<String> NUMBERED = Set.of("index", "random", "uuid");
    private static final String[] BIND_EXPRESSIONS = {"calculate", "relevant", "constraint", "required", "readonly"};

    private EngineExpressions() {
    }

    /** The form with its expressions rewritten, or {@code original} when nothing changes. */
    static byte[] rewrite(Document doc, byte[] original) throws TransformerException {
        int[] indexCounter = {0};
        boolean changed = rewriteAttributes(doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "bind"),
                BIND_EXPRESSIONS, indexCounter);
        changed |= rewriteAttributes(doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "setvalue"),
                new String[] {"value"}, indexCounter);
        if (!changed) {
            return original;
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream(original.length + 256);
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    private static boolean rewriteAttributes(NodeList elements, String[] attributes, int[] indexCounter) {
        boolean changed = false;
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            for (String attribute : attributes) {
                if (e.hasAttribute(attribute)) {
                    String before = e.getAttribute(attribute);
                    String after = rewriteExpression(before, indexCounter);
                    if (!after.equals(before)) {
                        e.setAttribute(attribute, after);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** Applies both rewrites to one expression; text inside string literals is never touched. */
    static String rewriteExpression(String expr, int[] indexCounter) {
        StringBuilder out = new StringBuilder(expr.length() + 16);
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '\'' || c == '"') {
                int end = stringEnd(expr, i);
                out.append(expr, i, end);
                i = end;
                continue;
            }
            String name = functionNameAt(expr, i);
            if (name != null) {
                int open = skipSpaces(expr, i + name.length());
                int close = closingParen(expr, open);
                if (close > 0) {
                    List<String> args = splitArguments(expr.substring(open + 1, close));
                    String internal = ZERO_ARGUMENT_REWRITES.get(name);
                    if (internal != null && args.size() == 1 && args.get(0).isBlank()) {
                        out.append(internal).append('(');
                        if (NUMBERED.contains(name)) {
                            out.append(++indexCounter[0]);
                        }
                        out.append(')');
                        i = close + 1;
                        continue;
                    }
                    if (SELECT_FUNCTIONS.contains(name)) {
                        out.append(expr, i, open + 1);
                        for (int k = 0; k < args.size(); k++) {
                            String arg = rewriteExpression(args.get(k), indexCounter);
                            if (k > 0) {
                                out.append(',');
                            }
                            out.append(k == 0 && !arg.isBlank() ? "string(" + arg + ")" : arg);
                        }
                        out.append(')');
                        i = close + 1;
                        continue;
                    }
                }
                out.append(name);
                i += name.length();
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** The name of a function call starting at {@code at} (index or a select function), or null. */
    private static String functionNameAt(String expr, int at) {
        if (at > 0 && isNameChar(expr.charAt(at - 1))) {
            return null;
        }
        int end = at;
        while (end < expr.length() && isNameChar(expr.charAt(end))) {
            end++;
        }
        if (end == at) {
            return null;
        }
        String name = expr.substring(at, end);
        if (!ZERO_ARGUMENT_REWRITES.containsKey(name) && !SELECT_FUNCTIONS.contains(name)) {
            return null;
        }
        int next = skipSpaces(expr, end);
        return next < expr.length() && expr.charAt(next) == '(' ? name : null;
    }

    /** Index of the ')' matching the '(' at {@code open}, or -1 when unbalanced. */
    private static int closingParen(String expr, int open) {
        int depth = 0;
        for (int i = open; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '\'' || c == '"') {
                i = stringEnd(expr, i) - 1;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Top-level arguments of a call, exactly as written (white space kept). */
    private static List<String> splitArguments(String inside) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (c == '\'' || c == '"') {
                i = stringEnd(inside, i) - 1;
            } else if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(inside.substring(start, i));
                start = i + 1;
            }
        }
        args.add(inside.substring(start));
        return args;
    }

    private static int stringEnd(String s, int quote) {
        int end = s.indexOf(s.charAt(quote), quote + 1);
        return end < 0 ? s.length() : end + 1;
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
