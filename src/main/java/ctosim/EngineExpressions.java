package ctosim;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *   <li><b>Conditional repeat functions.</b> The engine evaluates every argument of a function
 *   before calling it, so the condition of {@code count-if(${rows}, ${kind} = 'odd')} would be
 *   evaluated once, for all instances together. Each call becomes the native function on the
 *   nodes selected by a predicate, {@code count(/data/rows[boolean(/data/rows/kind = 'odd')])},
 *   which the engine evaluates for each instance; relative references in the condition are
 *   first made absolute from the expression's own node. {@code rank-index-if} becomes
 *   {@code ctosim-rank-index-if(position, all nodes, included nodes)}.</li>
 *   <li><b>Built-ins with SurveyCTO behaviour.</b> The engine matches its own functions before
 *   custom ones, so {@code format-date-time} and {@code decimal-time} are renamed to internal
 *   functions (English names; times written as text).</li>
 *   <li><b>Counted repeats.</b> The engine creates the instances of a repeat with a count
 *   ({@code jr:count}) as it needs them, but keeps the instance written in the form's primary
 *   instance, so a count of 0 still left one instance. That initial instance is removed when the
 *   repeat has a template ({@code jr:template}) to create instances from.</li>
 *   <li><b>Dates stored by calculations.</b> Every {@code calculate} (except on time fields) is
 *   wrapped in {@code ctosim-store(...)}, which stores a date or date-time result as its date
 *   only, as SurveyCTO does.</li>
 * </ul>
 */
final class EngineExpressions {

    static final String INTERNAL_INDEX = "ctosim-index";
    static final String JAVAROSA_NS = "http://openrosa.org/javarosa";
    static final String STORE = "ctosim-store";
    static final String RANK_INDEX_IF = "ctosim-rank-index-if";
    static final String FORMAT_DATE_TIME = "ctosim-format-date-time";
    static final String DECIMAL_TIME = "ctosim-decimal-time";

    /** SurveyCTO's conditional repeat functions, with the function each one becomes. */
    private static final Map<String, String> CONDITIONAL = Map.of(
            "count-if", "count", "sum-if", "sum", "min-if", "min", "max-if", "max",
            "join-if", "join", "rank-index-if", RANK_INDEX_IF);
    /** Arguments of each conditional function: the repeated nodes, then the condition, come last. */
    private static final Map<String, Integer> CONDITIONAL_ARITY = Map.of(
            "count-if", 2, "sum-if", 2, "min-if", 2, "max-if", 2, "join-if", 3, "rank-index-if", 3);
    private static final Map<String, String> RENAMED = Map.of(
            "format-date-time", FORMAT_DATE_TIME, "decimal-time", DECIMAL_TIME);
    private static final Set<String> OPERATORS = Set.of("and", "or", "div", "mod");
    private static final Pattern NUMERIC_REFERENCE = Pattern.compile("&#(\\d{5,7});");

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
                BIND_EXPRESSIONS, "nodeset", indexCounter);
        changed |= rewriteAttributes(doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "setvalue"),
                new String[] {"value"}, "ref", indexCounter);
        NodeList binds = doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "bind");
        for (int i = 0; i < binds.getLength(); i++) {
            Element bind = (Element) binds.item(i);
            if (bind.hasAttributeNS(FormLoader.CTOSIM_NS, FormLoader.CALCULATE_HERE)) {
                String before = bind.getAttributeNS(FormLoader.CTOSIM_NS, FormLoader.CALCULATE_HERE);
                String after = store(bind, rewriteExpression(before, indexCounter, contextPath(bind, "nodeset")));
                if (!after.equals(before)) {
                    bind.setAttributeNS(FormLoader.CTOSIM_NS,
                            bind.getAttributeNodeNS(FormLoader.CTOSIM_NS, FormLoader.CALCULATE_HERE).getName(), after);
                    changed = true;
                }
            }
        }
        changed |= dropCountedRepeatInstances(doc);
        if (!changed) {
            return original;
        }
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        ByteArrayOutputStream out = new ByteArrayOutputStream(original.length + 256);
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return supplementaryCharacters(out.toString(StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The serialiser writes characters outside the Basic Multilingual Plane (emoji, rare CJK) as
     * numeric references, which the engine's XML parser decodes wrongly; they are written as
     * themselves instead.
     */
    private static String supplementaryCharacters(String xml) {
        Matcher m = NUMERIC_REFERENCE.matcher(xml);
        StringBuilder out = new StringBuilder(xml.length());
        while (m.find()) {
            int codePoint = Integer.parseInt(m.group(1));
            m.appendReplacement(out, codePoint > 0xFFFF && Character.isValidCodePoint(codePoint)
                    ? Matcher.quoteReplacement(new String(Character.toChars(codePoint))) : "$0");
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean rewriteAttributes(NodeList elements, String[] attributes, String contextAttribute,
                                             int[] indexCounter) {
        boolean changed = false;
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            String context = contextPath(e, contextAttribute);
            for (String attribute : attributes) {
                if (e.hasAttribute(attribute)) {
                    String before = e.getAttribute(attribute);
                    String after = rewriteExpression(before, indexCounter, context);
                    if (attribute.equals("calculate")) {
                        after = store(e, after);
                    }
                    if (!after.equals(before)) {
                        e.setAttribute(attribute, after);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** Removes the initial instances of counted repeats that have a template; true when any was removed. */
    private static boolean dropCountedRepeatInstances(Document doc) {
        Element primary = primaryInstance(doc);
        if (primary == null) {
            return false;
        }
        boolean changed = false;
        NodeList repeats = doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "repeat");
        for (int i = 0; i < repeats.getLength(); i++) {
            Element repeat = (Element) repeats.item(i);
            String path = repeat.getAttribute("nodeset").strip();
            if (repeat.getAttributeNS(JAVAROSA_NS, "count").isBlank() || !path.startsWith("/")) {
                continue;
            }
            String[] steps = path.substring(1).split("/");
            if (!steps[0].equals(primary.getLocalName())) {
                continue;
            }
            List<Element> parents = List.of(primary);
            for (int k = 1; k < steps.length - 1; k++) {
                parents = children(parents, steps[k]);
            }
            for (Element parent : parents) {
                List<Element> instances = children(List.of(parent), steps[steps.length - 1]);
                if (instances.stream().anyMatch(e -> e.hasAttributeNS(JAVAROSA_NS, "template"))) {
                    for (Element instance : instances) {
                        if (!instance.hasAttributeNS(JAVAROSA_NS, "template")) {
                            parent.removeChild(instance);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static Element primaryInstance(Document doc) {
        NodeList instances = doc.getElementsByTagNameNS(FormLoader.XFORMS_NS, "instance");
        for (int i = 0; i < instances.getLength(); i++) {
            Element instance = (Element) instances.item(i);
            if (!instance.hasAttribute("id")) {
                for (org.w3c.dom.Node n = instance.getFirstChild(); n != null; n = n.getNextSibling()) {
                    if (n instanceof Element e) {
                        return e;
                    }
                }
            }
        }
        return null;
    }

    /** The child elements named {@code name} of every element of {@code parents}, in order. */
    private static List<Element> children(List<Element> parents, String name) {
        List<Element> result = new ArrayList<>();
        for (Element parent : parents) {
            for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e && name.equals(e.getLocalName())) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    /** The absolute path an element's expressions are evaluated for, or null when it has none. */
    private static String contextPath(Element e, String attribute) {
        String path = e.getAttribute(attribute).strip();
        return path.startsWith("/") ? path : null;
    }

    /** A calculation wrapped so that a date result is stored as a date only; time fields are left alone. */
    private static String store(Element bind, String calculation) {
        String type = bind.getAttribute("type");
        return calculation.isBlank() || type.substring(type.indexOf(':') + 1).equals("time")
                ? calculation : STORE + "(" + calculation + ")";
    }

    /**
     * Applies the rewrites to one expression evaluated for the node at {@code context} (an
     * absolute path, or null when unknown); text inside string literals is never touched.
     */
    static String rewriteExpression(String expr, int[] indexCounter, String context) {
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
                    Integer arity = CONDITIONAL_ARITY.get(name);
                    if (arity != null && args.size() == arity) {
                        List<String> rewritten = new ArrayList<>();
                        for (String arg : args) {
                            rewritten.add(rewriteExpression(arg, indexCounter, context).strip());
                        }
                        String nodes = rewritten.get(arity - 2);
                        String included = nodes + "[boolean(" + absolute(rewritten.get(arity - 1), context) + ")]";
                        List<String> call = new ArrayList<>(rewritten.subList(0, arity - 2));
                        if (name.equals("rank-index-if")) {
                            call.add(nodes);
                        }
                        call.add(included);
                        out.append(CONDITIONAL.get(name)).append('(').append(String.join(", ", call)).append(')');
                        i = close + 1;
                        continue;
                    }
                    String renamed = RENAMED.get(name);
                    if (renamed != null) {
                        out.append(renamed).append('(');
                        for (int k = 0; k < args.size(); k++) {
                            out.append(k > 0 ? "," : "").append(rewriteExpression(args.get(k), indexCounter, context));
                        }
                        out.append(')');
                        i = close + 1;
                        continue;
                    }
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
                            String arg = rewriteExpression(args.get(k), indexCounter, context);
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
        if (!ZERO_ARGUMENT_REWRITES.containsKey(name) && !SELECT_FUNCTIONS.contains(name)
                && !CONDITIONAL.containsKey(name) && !RENAMED.containsKey(name)) {
            return null;
        }
        int next = skipSpaces(expr, end);
        return next < expr.length() && expr.charAt(next) == '(' ? name : null;
    }

    /**
     * The condition with its relative location paths made absolute from {@code context}, so that
     * inside a predicate they still designate what they designate from the expression's node.
     * Paths starting with {@code .} or a name are relative; function names, operators and
     * numbers are not paths. Without a context the condition is returned unchanged.
     */
    private static String absolute(String condition, String context) {
        if (context == null) {
            return condition;
        }
        StringBuilder out = new StringBuilder(condition.length() + 32);
        int i = 0;
        while (i < condition.length()) {
            char c = condition.charAt(i);
            if (c == '\'' || c == '"') {
                int end = stringEnd(condition, i);
                out.append(condition, i, end);
                i = end;
                continue;
            }
            if (c == '[') { // a predicate's paths are relative to the node it filters
                int end = closingParen(condition, i);
                end = end < 0 ? condition.length() : end + 1;
                out.append(condition, i, end);
                i = end;
                continue;
            }
            boolean starts = (c == '.' || Character.isLetter(c) || c == '_')
                    && (i == 0 || !isPathChar(condition.charAt(i - 1)) && condition.charAt(i - 1) != '$');
            if (!starts) {
                out.append(c);
                i++;
                continue;
            }
            int end = i;
            while (end < condition.length() && isPathChar(condition.charAt(end))) {
                end++;
            }
            String path = condition.substring(i, end);
            int next = skipSpaces(condition, end);
            boolean function = next < condition.length() && condition.charAt(next) == '(';
            boolean number = c == '.' && path.length() > 1 && Character.isDigit(path.charAt(1));
            out.append(function || number || OPERATORS.contains(path) || path.contains("::")
                    ? path : resolve(context, path));
            i = end;
        }
        return out.toString();
    }

    /** {@code relative} resolved from the absolute path {@code context}. */
    private static String resolve(String context, String relative) {
        List<String> steps = new ArrayList<>();
        for (String step : context.split("/")) {
            if (!step.isEmpty()) {
                steps.add(step);
            }
        }
        for (String step : relative.split("/")) {
            if (step.equals("..")) {
                if (!steps.isEmpty()) {
                    steps.remove(steps.size() - 1);
                }
            } else if (!step.isEmpty() && !step.equals(".")) {
                steps.add(step);
            }
        }
        return "/" + String.join("/", steps);
    }

    private static boolean isPathChar(char c) {
        return isNameChar(c) || c == '/';
    }

    /** Index of the ')' matching the '(' at {@code open}, or -1 when unbalanced. */
    static int closingParen(String expr, int open) {
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
    static List<String> splitArguments(String inside) {
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
