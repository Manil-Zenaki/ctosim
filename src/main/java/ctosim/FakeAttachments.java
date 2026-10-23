package ctosim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.model.xform.XPathReference;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathBinaryOpExpr;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFilterExpr;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.expr.XPathNumericLiteral;
import org.javarosa.xpath.expr.XPathPathExpr;
import org.javarosa.xpath.expr.XPathStep;
import org.javarosa.xpath.expr.XPathStringLiteral;
import org.javarosa.xpath.expr.XPathUnaryOpExpr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Fictitious attached files built from what the form reads from them. See specs/12-fake-attachments.md. */
public final class FakeAttachments {

    private static final int MAX_ROWS = 500;
    private static final int ROWS_WITHOUT_LEVELS = 50;

    private final SortedMap<String, String> files;
    private final SortedMap<String, List<String>> keyAnswers;

    private FakeAttachments(SortedMap<String, String> files, SortedMap<String, List<String>> keyAnswers) {
        this.files = Collections.unmodifiableSortedMap(files);
        this.keyAnswers = Collections.unmodifiableSortedMap(keyAnswers);
    }

    /** The fictitious files for the form's missing attachments, drawn with {@code seed}. */
    public static FakeAttachments of(LoadedForm form, long seed) {
        return of(form, seed, null, 0);
    }

    /**
     * The fictitious files, with enough codes in the survey id's level for {@code submissions}
     * distinct ids. See specs/12-fake-attachments.md, Amendment 1.
     */
    public static FakeAttachments of(LoadedForm form, long seed, String idField, int submissions) {
        Objects.requireNonNull(form, "form");
        if (submissions < 0) {
            throw new IllegalArgumentException("submissions must not be negative: " + submissions);
        }
        Reading reading = new Reading(form);
        SortedMap<String, String> files = new TreeMap<>();
        SortedMap<String, List<String>> keys = new TreeMap<>();
        for (String name : form.attachments().missing(reading.files.keySet())) {
            FileUse use = reading.files.get(name);
            Table table = build(use, reading, seed, name, idLevel(use, idField), submissions);
            files.put(name, table.csv());
            for (Map.Entry<String, String> key : use.keyQuestions.entrySet()) {
                String question = key.getKey();
                if (!keys.containsKey(question)) {
                    keys.put(question, List.copyOf(table.column(key.getValue())));
                }
            }
        }
        return new FakeAttachments(files, keys);
    }

    /** Each fictitious file's name (without .csv) and its CSV content. */
    public SortedMap<String, String> files() {
        return files;
    }

    /** The values each key question should be answered with, so that the form finds its rows. */
    public SortedMap<String, List<String>> keyAnswers() {
        return keyAnswers;
    }

    // --- what the form reads ---------------------------------------------------------

    /** What the form reads from one attached file. */
    private static final class FileUse {
        final Set<String> columns = new LinkedHashSet<>();
        final List<String> levels = new ArrayList<>();             // value columns of search() selects
        final Map<String, String> levelOfSelect = new HashMap<>();  // select path -> its level column
        final Set<String> selectOnes = new HashSet<>();               // paths of the select_one search() selects
        final Map<String, String> levelOfLabel = new HashMap<>();   // label column -> its level column
        final Map<String, String> comparedSelect = new HashMap<>(); // match/filter column -> select path
        final Set<String> matchOrFilter = new HashSet<>();           // match and filter columns
        final Map<String, String> keySelect = new HashMap<>();      // key column -> select path
        final Map<String, Boolean> keyIsInteger = new HashMap<>();  // key column -> looked up with an int field
        final Map<String, String> keyQuestions = new LinkedHashMap<>(); // question name -> key column
        final Map<String, Set<String>> pulledBy = new HashMap<>();  // value column -> calculate paths
    }

    /** One pulldata() call: file, value column, key column, key expression, in the context of a node. */
    private record Pull(String file, String value, String key, XPathExpression keyExpr, TreeReference context) { }

    private static final class Reading {
        final Map<String, FileUse> files = new LinkedHashMap<>();
        final Map<String, String> bindType = new HashMap<>();       // path -> bind type
        final Map<String, String> calculate = new HashMap<>();      // path -> calculate expression
        final Map<String, String> control = new HashMap<>();        // path -> body control element
        final List<XPathExpression> expressions = new ArrayList<>();
        final List<TreeReference> contexts = new ArrayList<>();
        final Map<String, List<String>> literalsCompared = new HashMap<>(); // path -> literals it is compared with

        Reading(LoadedForm form) {
            Document doc;
            try {
                doc = readEngineXml(form);
            } catch (Exception e) {
                return; // the form's content never makes this module throw
            }
            List<Pull> pulls = new ArrayList<>();
            for (Element bind : elements(doc, XFORMS, "bind")) {
                String path = bind.getAttribute("nodeset").strip();
                TreeReference context = reference(path);
                bindType.put(path, bind.getAttribute("type").strip());
                if (bind.hasAttribute("calculate")) {
                    calculate.put(path, bind.getAttribute("calculate"));
                }
                for (String attribute : new String[] {"calculate", "relevant", "constraint", "required", "readonly"}) {
                    if (bind.hasAttribute(attribute)) {
                        read(bind.getAttribute(attribute), context, pulls);
                    }
                }
            }
            for (Element setvalue : elements(doc, XFORMS, "setvalue")) {
                if (setvalue.hasAttribute("value")) {
                    read(setvalue.getAttribute("value"), reference(setvalue.getAttribute("ref").strip()), pulls);
                }
            }
            for (Pull pull : pulls) {
                FileUse use = files.computeIfAbsent(pull.file(), f -> new FileUse());
                use.columns.add(pull.value());
                use.columns.add(pull.key());
                TreeReference keyRef = singleReference(pull.keyExpr(), pull.context());
                boolean integer = keyRef != null
                        && "int".equals(stripPrefix(bindType.get(keyRef.genericize().toString(false))));
                use.keyIsInteger.merge(pull.key(), integer, Boolean::logicalOr);
            }
            Element body = body(doc);
            if (body != null) {
                Map<String, Element> instances = secondaryInstances(doc);
                for (Element select : descendantsInOrder(body)) {
                    String local = select.getLocalName();
                    if (select.hasAttribute("ref")) {
                        control.put(select.getAttribute("ref").strip(), local);
                    }
                    if (!("select1".equals(local) || "select".equals(local)) || !select.hasAttribute("appearance")) {
                        continue;
                    }
                    readSearch(select, doc, instances);
                }
            }
            for (Pull pull : pulls) {
                FileUse use = files.get(pull.file());
                TreeReference keyRef = singleReference(pull.keyExpr(), pull.context());
                if (keyRef == null) {
                    continue;
                }
                String keyPath = keyRef.genericize().toString(false);
                if (use.levelOfSelect.containsKey(keyPath)) {
                    use.keySelect.putIfAbsent(pull.key(), keyPath);
                } else if ("input".equals(control.get(keyPath)) && !calculate.containsKey(keyPath)
                        && Set.of("", "string", "int", "long").contains(stripPrefix(bindType.get(keyPath)))) {
                    use.keyQuestions.putIfAbsent(keyRef.getNameLast(), pull.key());
                }
            }
            for (Map.Entry<String, String> c : calculate.entrySet()) {
                try {
                    XPathExpression e = unstored(XPathParseTool.parseXPath(c.getValue()));
                    if (e instanceof XPathFuncExpr f && "pulldata".equals(f.id.toString()) && f.args.length >= 2
                            && f.args[0] instanceof XPathStringLiteral file && f.args[1] instanceof XPathStringLiteral column) {
                        FileUse use = files.get(file.s);
                        if (use != null) {
                            use.pulledBy.computeIfAbsent(column.s, k -> new LinkedHashSet<>()).add(c.getKey());
                        }
                    }
                } catch (Exception e) {
                    // an expression the engine reports itself
                }
            }
            for (int i = 0; i < expressions.size(); i++) {
                compared(expressions.get(i), contexts.get(i));
            }
        }

        private void read(String text, TreeReference context, List<Pull> pulls) {
            XPathExpression e;
            try {
                e = XPathParseTool.parseXPath(text);
            } catch (Exception ex) {
                return;
            }
            expressions.add(e);
            contexts.add(context);
            walk(e, expr -> {
                if (expr instanceof XPathFuncExpr f && "pulldata".equals(f.id.toString()) && f.args.length == 4
                        && f.args[0] instanceof XPathStringLiteral file) {
                    if (f.args[1] instanceof XPathStringLiteral value && f.args[2] instanceof XPathStringLiteral key) {
                        pulls.add(new Pull(file.s, value.s, key.s, f.args[3], context));
                    } else {
                        // a computed column name adds nothing for it; the literal one is still read
                        FileUse use = files.computeIfAbsent(file.s, k -> new FileUse());
                        for (int i = 1; i <= 2; i++) {
                            if (f.args[i] instanceof XPathStringLiteral literal) {
                                use.columns.add(literal.s);
                            }
                        }
                    }
                }
            });
        }

        private void readSearch(Element select, Document doc, Map<String, Element> instances) {
            List<String> args = SearchChoices.searchArguments(select.getAttribute("appearance"));
            if (args == null || args.isEmpty()) {
                return;
            }
            String file = literal(args.get(0));
            if (file == null || !(args.size() == 1 || args.size() == 4 || args.size() == 6)) {
                return; // a call spec 07 cannot read loads nothing
            }
            if (args.size() > 1 && !Set.of("contains", "startswith", "endswith", "matches").contains(literal(args.get(1)))) {
                return;
            }
            if (file.endsWith(".csv")) {
                file = file.substring(0, file.length() - 4);
            }
            FileUse use = files.computeIfAbsent(file, f -> new FileUse());
            String path = select.getAttribute("ref").strip();
            String[] placeholder = placeholder(select, doc, instances);
            if (placeholder != null) {
                String valueColumn = placeholder[0];
                use.columns.add(valueColumn);
                if (!use.levels.contains(valueColumn)) {
                    use.levels.add(valueColumn);
                }
                use.levelOfSelect.put(path, valueColumn);
                if ("select1".equals(select.getLocalName())) {
                    use.selectOnes.add(path);
                }
                if (placeholder[1] != null && !placeholder[1].isBlank()) {
                    use.columns.add(placeholder[1]);
                    use.levelOfLabel.putIfAbsent(placeholder[1], valueColumn);
                }
            }
            if (args.size() == 4 || args.size() == 6) {
                String compared = selectPath(args.get(3), path);
                String columns = literal(args.get(2));
                if (columns != null) {
                    for (String column : columns.split(",")) {
                        String c = column.strip();
                        if (!c.isEmpty()) {
                            use.columns.add(c);
                            use.matchOrFilter.add(c);
                            if (compared != null) {
                                use.comparedSelect.putIfAbsent(c, compared);
                            }
                        }
                    }
                }
                if (args.size() == 6) {
                    String filter = literal(args.get(4));
                    if (filter != null) {
                        use.columns.add(filter);
                        use.matchOrFilter.add(filter);
                        String filterSelect = selectPath(args.get(5), path);
                        if (filterSelect != null) {
                            use.comparedSelect.putIfAbsent(filter, filterSelect);
                        }
                    }
                }
            }
        }

        /** The path of the field a search() argument refers to, when it is a single reference. */
        private String selectPath(String arg, String context) {
            try {
                TreeReference ref = singleReference(XPathParseTool.parseXPath(arg), reference(context));
                return ref == null ? null : ref.genericize().toString(false);
            } catch (Exception e) {
                return null;
            }
        }

        /** Records, for each field, the literals it is compared with by = or !=. */
        private void compared(XPathExpression e, TreeReference context) {
            walk(e, expr -> {
                if (expr instanceof XPathEqExpr eq) {
                    side(eq.a, eq.b, context);
                    side(eq.b, eq.a, context);
                }
            });
        }

        private void side(XPathExpression field, XPathExpression literal, TreeReference context) {
            TreeReference ref = singleReference(field, context);
            String text = literal instanceof XPathStringLiteral s ? s.s
                    : literal instanceof XPathNumericLiteral n ? number(n.d) : null;
            if (ref != null && text != null) {
                List<String> list = literalsCompared.computeIfAbsent(ref.genericize().toString(false), k -> new ArrayList<>());
                if (!list.contains(text)) {
                    list.add(text);
                }
            }
        }
    }

    /** The expression a calculate stores: the engine form wraps calculates in ctosim-store(). */
    private static XPathExpression unstored(XPathExpression e) {
        return e instanceof XPathFuncExpr f && EngineExpressions.STORE.equals(f.id.toString()) && f.args.length == 1
                ? f.args[0] : e;
    }

    /** Calls {@code visit} on every node of an expression. */
    private static void walk(XPathExpression e, java.util.function.Consumer<XPathExpression> visit) {
        if (e == null) {
            return;
        }
        visit.accept(e);
        if (e instanceof XPathBinaryOpExpr op) {
            walk(op.a, visit);
            walk(op.b, visit);
        } else if (e instanceof XPathUnaryOpExpr op) {
            walk(op.a, visit);
        } else if (e instanceof XPathFuncExpr f) {
            for (XPathExpression arg : f.args) {
                walk(arg, visit);
            }
        } else if (e instanceof XPathFilterExpr f) {
            walk(f.x, visit);
            for (XPathExpression p : f.predicates) {
                walk(p, visit);
            }
        } else if (e instanceof XPathPathExpr p) {
            walk(p.filtExpr, visit);
            for (XPathStep step : p.steps) {
                for (XPathExpression pred : step.predicates) {
                    walk(pred, visit);
                }
            }
        }
    }

    /** The absolute reference an expression consists of, or null when it is not a single field reference. */
    private static TreeReference singleReference(XPathExpression e, TreeReference context) {
        if (!(e instanceof XPathPathExpr path) || path.init_context == XPathPathExpr.INIT_CONTEXT_EXPR) {
            return null;
        }
        try {
            TreeReference ref = path.getReference();
            TreeReference absolute = ref.isAbsolute() || context == null ? ref : ref.anchor(context);
            return absolute == null || absolute.getInstanceName() != null ? null : absolute;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static TreeReference reference(String path) {
        try {
            return (TreeReference) new XPathReference(path).getReference();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String literal(String arg) {
        String a = arg.strip();
        return a.length() >= 2 && (a.charAt(0) == '\'' || a.charAt(0) == '"') && a.charAt(a.length() - 1) == a.charAt(0)
                ? a.substring(1, a.length() - 1) : null;
    }

    private static String stripPrefix(String type) {
        if (type == null) {
            return "";
        }
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }

    private static String number(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
    }

    // --- building a file ---------------------------------------------------------------

    private record Table(List<String> header, List<List<String>> rows) {
        List<String> column(String name) {
            int i = header.indexOf(name);
            List<String> values = new ArrayList<>(rows.size());
            for (List<String> row : rows) {
                values.add(row.get(i));
            }
            return values;
        }

        String csv() {
            StringBuilder out = new StringBuilder();
            line(out, header);
            for (List<String> row : rows) {
                line(out, row);
            }
            return out.toString();
        }

        private static void line(StringBuilder out, List<String> cells) {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                String c = cells.get(i);
                if (c.indexOf(',') >= 0 || c.indexOf('"') >= 0 || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) {
                    out.append('"').append(c.replace("\"", "\"\"")).append('"');
                } else {
                    out.append(c);
                }
            }
            out.append('\n');
        }
    }

    /** The level of the survey id's select_one in this file, or -1 (spec 12, Amendment 1). */
    private static int idLevel(FileUse use, String idField) {
        if (idField == null) {
            return -1;
        }
        for (String path : use.selectOnes) {
            if (path.substring(path.lastIndexOf('/') + 1).equals(idField)) {
                return use.levels.indexOf(use.levelOfSelect.get(path));
            }
        }
        return -1;
    }

    private static Table build(FileUse use, Reading reading, long seed, String name, int idLevel, int submissions) {
        SplittableRandom random = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + name.hashCode());
        List<List<String>> paths = codePaths(use.levels.size(), idLevel, submissions);
        int n = paths.isEmpty() ? ROWS_WITHOUT_LEVELS : paths.size();
        List<String> header = new ArrayList<>(use.columns);
        List<List<String>> rows = new ArrayList<>(n);
        for (int r = 0; r < n; r++) {
            List<String> path = paths.isEmpty() ? List.of() : paths.get(r);
            List<String> row = new ArrayList<>(header.size());
            for (String column : header) {
                row.add(value(column, use, reading, path, r, random));
            }
            rows.add(row);
        }
        return new Table(header, rows);
    }

    /**
     * The code of each level on each row: a tree of codes with at most MAX_ROWS leaves, except
     * that the survey id's level gets at least {@code submissions} codes (spec 12, Amendment 1).
     */
    private static List<List<String>> codePaths(int levels, int idLevel, int submissions) {
        if (levels == 0) {
            return List.of();
        }
        boolean expand = idLevel >= 0 && submissions > normalCodes(idLevel);
        List<List<String>> paths = new ArrayList<>();
        int first = expand && idLevel == 0 ? Math.max(3, submissions) : 3;
        for (int c = 1; c <= first; c++) {
            paths.add(List.of(index(c, first)));
        }
        for (int level = 1; level < levels; level++) {
            int b = paths.size() * 3 <= MAX_ROWS ? 3 : paths.size() * 2 <= MAX_ROWS ? 2 : 1;
            if (expand && level == idLevel) {
                b = Math.max(b, (submissions + paths.size() - 1) / paths.size());
            } else if (expand && level > idLevel) {
                b = 1;
            }
            List<List<String>> next = new ArrayList<>(paths.size() * b);
            for (List<String> parent : paths) {
                String p = parent.get(parent.size() - 1);
                for (int c = 1; c <= b; c++) {
                    List<String> child = new ArrayList<>(parent);
                    child.add(p + index(c, b));
                    next.add(child);
                }
            }
            paths = next;
        }
        return paths;
    }

    /** The number of codes "Rows" gives a level. */
    private static long normalCodes(int level) {
        long codes = 3;
        for (int l = 1; l <= level; l++) {
            codes *= codes * 3 <= MAX_ROWS ? 3 : codes * 2 <= MAX_ROWS ? 2 : 1;
        }
        return codes;
    }

    /** A child's index, padded with zeros to the width of {@code b} when b is larger than 9. */
    private static String index(int c, int b) {
        String text = Integer.toString(c);
        if (b <= 9) {
            return text;
        }
        return "0".repeat(Integer.toString(b).length() - text.length()) + text;
    }

    private static String value(String column, FileUse use, Reading reading, List<String> path, int row,
                                SplittableRandom random) {
        int level = use.levels.indexOf(column);
        if (level >= 0) {
            return path.get(level);
        }
        boolean ruleSix = false;
        if (use.matchOrFilter.contains(column)) {
            String compared = use.comparedSelect.get(column);
            String selectLevel = compared == null ? null : use.levelOfSelect.get(compared);
            if (selectLevel != null) {
                return path.get(use.levels.indexOf(selectLevel));
            }
            ruleSix = true; // rule 2: otherwise as rule 6
        }
        if (!ruleSix) {
            String other = otherRules(column, use, path, row, random);
            if (other != null) {
                return other;
            }
        }
        for (String calculatePath : use.pulledBy.getOrDefault(column, Set.of())) {
            List<String> literals = reading.literalsCompared.get(calculatePath);
            if (literals != null && !literals.isEmpty()) {
                return literals.get(random.nextInt(literals.size()));
            }
        }
        return Integer.toString(1 + random.nextInt(3));
    }

    /** Rules 3 to 5, or null when none applies. */
    private static String otherRules(String column, FileUse use, List<String> path, int row, SplittableRandom random) {
        String labelled = use.levelOfLabel.get(column);
        if (labelled != null) {
            return column + " " + path.get(use.levels.indexOf(labelled));
        }
        if (use.keySelect.containsKey(column)) {
            return path.get(use.levels.indexOf(use.levelOfSelect.get(use.keySelect.get(column))));
        }
        if (use.keyIsInteger.containsKey(column)) {
            return use.keyIsInteger.get(column) ? Integer.toString(row + 1) : String.format(Locale.ROOT, "K%04d", row + 1);
        }
        return byName(column, random);
    }

    private static final Pattern WORD_BOUNDARY = Pattern.compile("[_\\-0-9]+|(?<=[a-z])(?=[A-Z])");

    /** Values by the column's name (rule 5), or null when no line applies. */
    private static String byName(String column, SplittableRandom random) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : WORD_BOUNDARY.split(column)) {
            if (!w.isEmpty()) {
                words.add(w.toLowerCase(Locale.ROOT));
            }
        }
        if (any(words, "phone", "tel", "telephone", "mobile", "cell")) {
            StringBuilder s = new StringBuilder("0");
            for (int i = 0; i < 9; i++) {
                s.append((char) ('0' + random.nextInt(10)));
            }
            return s.toString();
        }
        if (any(words, "lat", "latitude", "lon", "lng", "long", "longitude")) {
            return String.format(Locale.ROOT, "%.6f", -1 + 2 * random.nextDouble());
        }
        if (any(words, "name", "nom", "nm", "prenom", "firstname", "surname", "lastname", "fullname")) {
            List<String> first = AnswerGenerator.BUILT_IN_POOLS.get("firstName");
            List<String> last = AnswerGenerator.BUILT_IN_POOLS.get("lastName");
            return first.get(random.nextInt(first.size())) + " " + last.get(random.nextInt(last.size()));
        }
        if (any(words, "age")) {
            return Integer.toString(1 + random.nextInt(80));
        }
        if (any(words, "area", "surface", "superficie")) {
            return String.format(Locale.ROOT, "%.2f", (25 + random.nextInt(476)) / 100.0);
        }
        if (any(words, "nb", "num", "nbr", "number", "count", "total")) {
            return Integer.toString(1 + random.nextInt(4));
        }
        return null;
    }

    private static boolean any(Set<String> words, String... candidates) {
        for (String c : candidates) {
            if (words.contains(c)) {
                return true;
            }
        }
        return false;
    }

    // --- reading the XForm ---------------------------------------------------------------

    private static final String XFORMS = FormLoader.XFORMS_NS;

    private static Document readEngineXml(LoadedForm form) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(form.engineXml()));
    }

    /** The elements of that name, in document order. */
    private static List<Element> elements(Node root, String ns, String localName) {
        List<Element> out = new ArrayList<>();
        org.w3c.dom.NodeList live = root instanceof Document d ? d.getElementsByTagNameNS(ns, localName)
                : ((Element) root).getElementsByTagNameNS(ns, localName);
        for (int i = 0; i < live.getLength(); i++) {
            out.add((Element) live.item(i));
        }
        return out;
    }

    private static Element body(Document doc) {
        List<Element> bodies = elements(doc, "http://www.w3.org/1999/xhtml", "body");
        return bodies.isEmpty() ? null : bodies.get(0);
    }

    /** The secondary instances, by id. */
    private static Map<String, Element> secondaryInstances(Document doc) {
        Map<String, Element> out = new HashMap<>();
        for (Element instance : elements(doc, XFORMS, "instance")) {
            if (instance.hasAttribute("id")) {
                out.put(instance.getAttribute("id"), instance);
            }
        }
        return out;
    }

    /** Every element below root, in document order. */
    private static List<Element> descendantsInOrder(Element root) {
        List<Element> out = new ArrayList<>();
        org.w3c.dom.NodeList all = root.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            out.add((Element) all.item(i));
        }
        return out;
    }

    private static Element child(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static final Pattern ITEXT = Pattern.compile("jr:itext\\(\\s*'?([^')]+?)'?\\s*\\)");
    private static final Pattern INSTANCE = Pattern.compile("instance\\(\\s*'([^']+)'\\s*\\)");

    /** The text of an itext id in the default translation (the one marked default, else the first). */
    private static String itext(Document doc, String id) {
        List<Element> translations = elements(doc, XFORMS, "translation");
        Element chosen = null;
        for (Element t : translations) {
            if (t.hasAttribute("default")) {
                chosen = t;
                break;
            }
        }
        if (chosen == null && !translations.isEmpty()) {
            chosen = translations.get(0);
        }
        if (chosen == null) {
            return null;
        }
        for (Element text : elements(chosen, XFORMS, "text")) {
            if (id.equals(text.getAttribute("id"))) {
                for (Element value : elements(text, XFORMS, "value")) {
                    if (!value.hasAttribute("form")) {
                        return value.getTextContent().strip();
                    }
                }
            }
        }
        return null;
    }

    /** The label of a choice: its label element's text, or its itext in the default language. */
    private static String label(Element labelElement, Element item, Document doc) {
        if (labelElement == null) {
            return null;
        }
        String ref = labelElement.getAttribute("ref");
        if (ref.isEmpty()) {
            return labelElement.getTextContent().strip();
        }
        Matcher m = ITEXT.matcher(ref);
        if (m.find()) {
            String id = m.group(1);
            if (item != null) { // an itemset: the item's child of that name holds the id
                Element idChild = child(item, id);
                if (idChild != null) {
                    id = idChild.getTextContent().strip();
                }
            }
            return itext(doc, id);
        }
        Element named = item == null ? null : child(item, ref.strip());
        return named == null ? null : named.getTextContent().strip();
    }

    /** The placeholder of a search() select: {value column, label column or null}, or null. */
    private static String[] placeholder(Element select, Document doc, Map<String, Element> instances) {
        Element itemset = child(select, "itemset");
        if (itemset != null) {
            Matcher m = INSTANCE.matcher(itemset.getAttribute("nodeset"));
            Element valueRef = child(itemset, "value");
            if (!m.find() || valueRef == null || !instances.containsKey(m.group(1))) {
                return null;
            }
            String valueName = valueRef.getAttribute("ref").strip();
            for (Element item : elements(instances.get(m.group(1)), "*", "item")) {
                Element v = child(item, valueName);
                String value = v == null ? "" : v.getTextContent().strip();
                if (!value.isEmpty() && isNotNumber(value)) {
                    return new String[] {value, label(child(itemset, "label"), item, doc)};
                }
            }
            return null;
        }
        for (Element item : elements(select, XFORMS, "item")) {
            Element v = child(item, "value");
            String value = v == null ? "" : v.getTextContent().strip();
            if (!value.isEmpty() && isNotNumber(value)) {
                return new String[] {value, label(child(item, "label"), null, doc)};
            }
        }
        return null;
    }

    private static boolean isNotNumber(String s) {
        try {
            Double.parseDouble(s);
            return false;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
