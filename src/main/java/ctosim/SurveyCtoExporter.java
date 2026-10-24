package ctosim;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.GroupDef;
import org.javarosa.core.model.IFormElement;
import org.javarosa.core.model.ItemsetBinding;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Writes submissions as SurveyCTO wide and long exports. See specs/08-surveycto-exporter.md. */
public final class SurveyCtoExporter {

    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9-]+");
    private static final String DATE = "(\\d{4})-(\\d{2})-(\\d{2})";
    private static final String TIME = "(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}(?::?\\d{2})?)?";
    private static final Pattern DATE_VALUE = Pattern.compile(DATE);
    private static final Pattern TIME_VALUE = Pattern.compile(TIME);
    private static final Pattern DATETIME_VALUE = Pattern.compile(DATE + "T" + TIME);
    private static final String[] MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] GEOPOINT_PARTS = {"-Latitude", "-Longitude", "-Altitude", "-Accuracy"};

    private enum Kind { GROUP, NOTE, REPEAT, DATE, TIME, DATETIME, SELECT_MULTIPLE, GEOPOINT, MEDIA, TEXT_AUDIT, AUDIO_AUDIT, OTHER }

    /** A field giving columns, or a repeat group, placed in its container's item list. */
    private interface Item { }

    private record Column(String name, Kind kind, List<String> rel, List<String> choices) implements Item { }

    private static final class Repeat implements Item {
        final String name;
        final List<String> rel;
        final String path;
        final boolean counted;
        final List<Item> items = new ArrayList<>();

        Repeat(String name, List<String> rel, boolean counted) {
            this.name = name;
            this.rel = rel;
            this.path = String.join("-", rel);
            this.counted = counted;
        }
    }

    private record Parsed(Element root, String id, String instanceName) { }

    private final String formId;
    private final String title;
    private final String version;
    private final String serverName;
    private final String rootName;
    /** Whether the form has meta/instanceName (an instance_name setting): it then gets a column. */
    private final boolean hasInstanceName;
    private final List<Item> rootItems = new ArrayList<>();
    private final List<Repeat> repeats = new ArrayList<>();

    public SurveyCtoExporter(FieldCatalog catalog, LoadedForm form, String serverName) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(serverName, "serverName");
        if (!SERVER_NAME.matcher(serverName).matches()) {
            throw new IllegalArgumentException("invalid server name: " + serverName);
        }
        if (!catalog.formId().equals(form.formId())) {
            throw new IllegalArgumentException("catalog form id " + catalog.formId()
                    + " differs from the form's id " + form.formId());
        }
        this.formId = catalog.formId();
        this.title = catalog.title();
        this.version = catalog.version();
        this.serverName = serverName;

        FormDef def = form.newEntryController().getModel().getForm();
        this.rootName = def.getMainInstance().getRoot().getName();
        TreeElement meta = def.getMainInstance().getRoot().getChild("meta", 0);
        this.hasInstanceName = meta != null && meta.getChild("instanceName", 0) != null;
        Set<String> counted = new HashSet<>();
        Map<String, List<String>> choices = new HashMap<>();
        scanForm(def, def.getChildren(), counted, choices);
        buildLayout(catalog.fields(), counted, choices);
    }

    public String wideFileName() {
        return fileTitle() + "_WIDE.csv";
    }

    public String wide(List<Submission> submissions) {
        List<Parsed> parsed = parseAll(submissions);
        Map<Repeat, Integer> maxima = new HashMap<>();
        for (Parsed p : parsed) {
            findMaxima(rootItems, p.root(), maxima);
        }
        List<String> header = new ArrayList<>();
        header.add("SubmissionDate");
        wideHeader(rootItems, "", maxima, header);
        header.addAll(metadataHeader());

        List<List<String>> rows = new ArrayList<>();
        rows.add(header);
        for (int i = 0; i < parsed.size(); i++) {
            Parsed p = parsed.get(i);
            List<String> row = new ArrayList<>();
            row.add(formatDateTime(submissions.get(i).submitted()));
            wideCells(rootItems, p.root(), "", p, maxima, row);
            row.addAll(metadataCells(p));
            rows.add(row);
        }
        return csv(rows);
    }

    public Map<String, String> longFiles(List<Submission> submissions) {
        List<Parsed> parsed = parseAll(submissions);
        List<String> mainHeader = new ArrayList<>();
        mainHeader.add("SubmissionDate");
        longHeader(rootItems, mainHeader);
        mainHeader.addAll(metadataHeader());
        List<List<String>> mainRows = new ArrayList<>();
        mainRows.add(mainHeader);

        Map<Repeat, List<List<String>>> repeatRows = new LinkedHashMap<>();
        for (Repeat r : repeats) {
            List<String> header = new ArrayList<>();
            longHeader(r.items, header);
            header.addAll(List.of("PARENT_KEY", "KEY", "SET-OF-" + r.name));
            List<List<String>> rows = new ArrayList<>();
            rows.add(header);
            repeatRows.put(r, rows);
        }

        for (int i = 0; i < parsed.size(); i++) {
            Parsed p = parsed.get(i);
            List<String> row = new ArrayList<>();
            row.add(formatDateTime(submissions.get(i).submitted()));
            longCells(rootItems, p.root(), p.id(), p, row);
            row.addAll(metadataCells(p));
            mainRows.add(row);
            collectInstances(rootItems, p.root(), p.id(), p, repeatRows);
        }

        Map<String, String> files = new LinkedHashMap<>();
        files.put(fileTitle() + ".csv", csv(mainRows));
        for (Map.Entry<Repeat, List<List<String>>> e : repeatRows.entrySet()) {
            files.put(fileTitle() + "-" + e.getKey().path + ".csv", csv(e.getValue()));
        }
        return Collections.unmodifiableMap(files);
    }

    public void writeAll(List<Submission> submissions, Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        String wide = wide(submissions);
        Map<String, String> longFiles = longFiles(submissions);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(wideFileName()), wide, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> e : longFiles.entrySet()) {
            Files.writeString(directory.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
    }

    // ---- form and catalog ----

    private static void scanForm(FormDef def, List<IFormElement> elements, Set<String> counted,
                                 Map<String, List<String>> choices) {
        if (elements == null) {
            return;
        }
        for (IFormElement element : elements) {
            if (element instanceof GroupDef group) {
                if (group.getRepeat() && group.getCountReference() != null && group.getBind() != null) {
                    counted.add(path(group.getBind().getReference()));
                }
                scanForm(def, group.getChildren(), counted, choices);
            } else if (element instanceof QuestionDef question
                    && question.getControlType() == Constants.CONTROL_SELECT_MULTI
                    && question.getBind() != null) {
                choices.put(path(question.getBind().getReference()), choiceValues(def, question));
            }
        }
    }

    private static String path(Object reference) {
        String text = reference instanceof TreeReference ref ? ref.toString(false) : String.valueOf(reference);
        return text.replaceAll("\\[[^\\]]*\\]", "");
    }

    /** All choice values of a select, in form order, without choice filters, duplicates removed. */
    static List<String> choiceValues(FormDef def, QuestionDef question) {
        Set<String> values = new LinkedHashSet<>();
        ItemsetBinding itemset = question.getDynamicChoices();
        if (itemset == null) {
            List<SelectChoice> items = question.getChoices();
            if (items != null) {
                for (SelectChoice choice : items) {
                    values.add(choice.getValue());
                }
            }
        } else if (itemset.nodesetRef != null && itemset.valueRef != null) {
            EvaluationContext context = def.getEvaluationContext();
            // The value reference carries the choice filter's predicate: follow its steps below
            // each item instead of resolving it.
            int depth = itemset.nodesetRef.size();
            for (TreeReference item : context.expandReference(itemset.nodesetRef.removePredicates())) {
                AbstractTreeElement<?> node = context.resolveReference(item);
                for (int i = depth; node != null && i < itemset.valueRef.size(); i++) {
                    node = node.getChild(itemset.valueRef.getName(i), 0);
                }
                values.add(node == null || node.getValue() == null ? "" : node.getValue().uncast().getString());
            }
        }
        return List.copyOf(values);
    }

    private void buildLayout(List<FieldInfo> fields, Set<String> counted, Map<String, List<String>> choices) {
        Map<String, Repeat> repeatsByXpath = new LinkedHashMap<>();
        List<Map.Entry<String, Item>> placed = new ArrayList<>();
        for (FieldInfo field : fields) {
            if (kind(field.type()) == Kind.REPEAT) {
                repeatsByXpath.put(field.xpath(), null);
            }
        }
        for (FieldInfo field : fields) {
            Kind kind = kind(field.type());
            if (kind == Kind.GROUP || kind == Kind.NOTE) {
                continue;
            }
            String container = container(field.xpath(), repeatsByXpath.keySet());
            List<String> rel = relativeSteps(field.xpath(), container);
            Item item;
            if (kind == Kind.REPEAT) {
                Repeat repeat = new Repeat(field.name(), rel, counted.contains(field.xpath()));
                repeatsByXpath.put(field.xpath(), repeat);
                repeats.add(repeat);
                item = repeat;
            } else {
                List<String> values = kind == Kind.SELECT_MULTIPLE
                        ? choices.getOrDefault(field.xpath(), List.of()) : List.of();
                item = new Column(field.name(), kind, rel, values);
            }
            placed.add(new java.util.AbstractMap.SimpleEntry<>(container, item));
        }
        // Repeats are created in the first pass over the catalog, so items are placed afterwards.
        for (Map.Entry<String, Item> entry : placed) {
            if (entry.getKey() == null) {
                rootItems.add(entry.getValue());
            } else {
                repeatsByXpath.get(entry.getKey()).items.add(entry.getValue());
            }
        }
    }

    /** The xpath of the innermost catalog repeat strictly containing {@code xpath}, or null. */
    private static String container(String xpath, Set<String> repeatXpaths) {
        String best = null;
        for (String candidate : repeatXpaths) {
            if (xpath.startsWith(candidate + "/") && (best == null || candidate.length() > best.length())) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<String> relativeSteps(String xpath, String container) {
        List<String> steps = steps(xpath);
        int skip = container == null ? 1 : steps(container).size();
        return List.copyOf(steps.subList(Math.min(skip, steps.size()), steps.size()));
    }

    private static List<String> steps(String xpath) {
        List<String> steps = new ArrayList<>();
        for (String step : xpath.split("/")) {
            if (!step.isEmpty()) {
                steps.add(step);
            }
        }
        return steps;
    }

    private static Kind kind(String type) {
        String[] words = type.trim().toLowerCase(Locale.ROOT).split("\\s+");
        String first = words[0];
        String second = words.length > 1 ? words[1] : "";
        if (first.equals("begin_group") || first.equals("begin") && second.equals("group")) {
            return Kind.GROUP;
        }
        if (first.equals("begin_repeat") || first.equals("begin") && second.equals("repeat")) {
            return Kind.REPEAT;
        }
        if (first.equals("text") && second.equals("audit")) {
            return Kind.TEXT_AUDIT;
        }
        if (first.equals("audio") && second.equals("audit")) {
            return Kind.AUDIO_AUDIT;
        }
        return switch (first) {
            case "note" -> Kind.NOTE;
            case "date", "today" -> Kind.DATE;
            case "time" -> Kind.TIME;
            case "datetime", "start", "end" -> Kind.DATETIME;
            case "select_multiple" -> Kind.SELECT_MULTIPLE;
            case "geopoint" -> Kind.GEOPOINT;
            case "image", "audio", "video", "file", "comments" -> Kind.MEDIA;
            default -> Kind.OTHER;
        };
    }

    // ---- submissions ----

    private List<Parsed> parseAll(List<Submission> submissions) {
        Objects.requireNonNull(submissions, "submissions");
        List<Parsed> parsed = new ArrayList<>();
        for (Submission submission : submissions) {
            Objects.requireNonNull(submission, "submission");
            parsed.add(parse(submission));
        }
        return parsed;
    }

    private Parsed parse(Submission submission) {
        Element root;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            root = builder.parse(new InputSource(new StringReader(submission.instanceXml()))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw invalid(submission, "the instance is not well-formed XML (" + e.getMessage() + ")");
        }
        if (!localName(root).equals(rootName)) {
            throw invalid(submission, "the root element is " + localName(root) + ", not " + rootName);
        }
        if (!root.hasAttribute("id") || !root.getAttribute("id").equals(formId)) {
            throw invalid(submission, "the root element's id is not " + formId);
        }
        Element meta = child(root, "meta");
        Element instanceId = meta == null ? null : child(meta, "instanceID");
        if (instanceId == null) {
            throw invalid(submission, "the instance has no meta/instanceID element");
        }
        Element instanceName = child(meta, "instanceName");
        // SurveyCTO drops the trailing spaces of instance names.
        return new Parsed(root, instanceId.getTextContent(),
                instanceName == null ? "" : instanceName.getTextContent().replaceAll(" +$", ""));
    }

    private static IllegalArgumentException invalid(Submission submission, String reason) {
        return new IllegalArgumentException("submission " + submission.number() + ": " + reason);
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName(e).equals(name)) {
                return e;
            }
        }
        return null;
    }

    private static Element resolve(Element context, List<String> rel) {
        Element current = context;
        for (int i = 0; current != null && i < rel.size(); i++) {
            current = child(current, rel.get(i));
        }
        return current;
    }

    /** The instances of {@code repeat} in {@code context}, or null when their containing element is absent. */
    private static List<Element> instances(Element context, Repeat repeat) {
        if (context == null) {
            return null;
        }
        Element parent = resolve(context, repeat.rel.subList(0, repeat.rel.size() - 1));
        if (parent == null) {
            return null;
        }
        String name = repeat.rel.get(repeat.rel.size() - 1);
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName(e).equals(name)) {
                result.add(e);
            }
        }
        return result;
    }

    // ---- layouts ----

    private static void findMaxima(List<Item> items, Element context, Map<Repeat, Integer> maxima) {
        for (Item item : items) {
            if (item instanceof Repeat r) {
                List<Element> list = instances(context, r);
                int size = list == null ? 0 : list.size();
                maxima.merge(r, size, Math::max);
                if (list != null) {
                    for (Element instance : list) {
                        findMaxima(r.items, instance, maxima);
                    }
                }
            }
        }
    }

    private static void wideHeader(List<Item> items, String suffix, Map<Repeat, Integer> maxima, List<String> out) {
        for (Item item : items) {
            if (item instanceof Column c) {
                for (String name : columnNames(c)) {
                    out.add(name + suffix);
                }
            } else if (item instanceof Repeat r) {
                if (r.counted) {
                    out.add(r.name + "_count" + suffix);
                }
                for (int k = 1; k <= maxima.getOrDefault(r, 0); k++) {
                    wideHeader(r.items, suffix + "_" + k, maxima, out);
                }
            }
        }
    }

    private void wideCells(List<Item> items, Element context, String suffix, Parsed p,
                           Map<Repeat, Integer> maxima, List<String> out) {
        for (Item item : items) {
            if (item instanceof Column c) {
                out.addAll(cells(c, context, p));
            } else if (item instanceof Repeat r) {
                List<Element> list = instances(context, r);
                if (r.counted) {
                    out.add(list == null ? "" : String.valueOf(list.size()));
                }
                for (int k = 1; k <= maxima.getOrDefault(r, 0); k++) {
                    Element instance = list != null && k <= list.size() ? list.get(k - 1) : null;
                    wideCells(r.items, instance, suffix + "_" + k, p, maxima, out);
                }
            }
        }
    }

    private static void longHeader(List<Item> items, List<String> out) {
        for (Item item : items) {
            if (item instanceof Column c) {
                out.addAll(columnNames(c));
            } else if (item instanceof Repeat r) {
                if (r.counted) {
                    out.add(r.name + "_count");
                }
                out.add("SET-OF-" + r.name);
            }
        }
    }

    private void longCells(List<Item> items, Element context, String key, Parsed p, List<String> out) {
        for (Item item : items) {
            if (item instanceof Column c) {
                out.addAll(cells(c, context, p));
            } else if (item instanceof Repeat r) {
                List<Element> list = instances(context, r);
                if (r.counted) {
                    out.add(list == null ? "" : String.valueOf(list.size()));
                }
                out.add(list == null || list.isEmpty() ? "" : key + "/" + r.path);
            }
        }
    }

    private void collectInstances(List<Item> items, Element context, String key, Parsed p,
                                  Map<Repeat, List<List<String>>> rows) {
        for (Item item : items) {
            if (item instanceof Repeat r) {
                List<Element> list = instances(context, r);
                if (list == null) {
                    continue;
                }
                for (int k = 1; k <= list.size(); k++) {
                    Element instance = list.get(k - 1);
                    String instanceKey = key + "/" + r.path + "[" + k + "]";
                    List<String> row = new ArrayList<>();
                    longCells(r.items, instance, instanceKey, p, row);
                    row.addAll(List.of(key, instanceKey, key + "/" + r.path));
                    rows.get(r).add(row);
                    collectInstances(r.items, instance, instanceKey, p, rows);
                }
            }
        }
    }

    private static List<String> columnNames(Column c) {
        List<String> names = new ArrayList<>();
        switch (c.kind()) {
            case SELECT_MULTIPLE -> {
                names.add(c.name());
                for (String value : c.choices()) {
                    names.add(c.name() + "_" + value.replace('-', '_'));
                }
            }
            case GEOPOINT -> {
                for (String part : GEOPOINT_PARTS) {
                    names.add(c.name() + part);
                }
            }
            default -> names.add(c.name());
        }
        return names;
    }

    // ---- cell values ----

    private List<String> cells(Column c, Element context, Parsed p) {
        Element element = context == null ? null : resolve(context, c.rel());
        String stored = element == null ? "" : element.getTextContent();
        switch (c.kind()) {
            case SELECT_MULTIPLE -> {
                List<String> cells = new ArrayList<>();
                cells.add(stored);
                Set<String> selected = new HashSet<>(items(stored));
                // A relevant question left blank keeps its element: its dummies are 0, not empty.
                for (String value : c.choices()) {
                    cells.add(element == null ? "" : selected.contains(value) ? "1" : "0");
                }
                return cells;
            }
            case GEOPOINT -> {
                List<String> parts = items(stored);
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < GEOPOINT_PARTS.length; i++) {
                    cells.add(i < parts.size() ? parts.get(i) : "");
                }
                return cells;
            }
            case DATE -> {
                return List.of(formatDate(stored));
            }
            case TIME -> {
                return List.of(formatTime(stored));
            }
            case DATETIME -> {
                return List.of(formatDateTimeValue(stored));
            }
            case MEDIA -> {
                return List.of(stored.isEmpty() ? "" : attachmentUrl(p.id(), stored));
            }
            case TEXT_AUDIT -> {
                return List.of(element == null ? "" : attachmentUrl(p.id(), "TA_" + uuid(p) + ".csv"));
            }
            case AUDIO_AUDIT -> {
                return List.of(element == null ? "" : attachmentUrl(p.id(), "AA_" + uuid(p) + "_AFTER_0S.m4a"));
            }
            default -> {
                return List.of(stored);
            }
        }
    }

    private static String uuid(Parsed p) {
        return p.id().startsWith("uuid:") ? p.id().substring(5) : p.id();
    }

    /** The metadata columns that end every row of the wide and main long files. */
    private List<String> metadataHeader() {
        return hasInstanceName ? List.of("instanceID", "instanceName", "formdef_version", "KEY")
                : List.of("instanceID", "formdef_version", "KEY");
    }

    private List<String> metadataCells(Parsed p) {
        String formdef = version == null ? "" : version;
        return hasInstanceName ? List.of(p.id(), p.instanceName(), formdef, p.id()) : List.of(p.id(), formdef, p.id());
    }

    private static List<String> items(String value) {
        List<String> items = new ArrayList<>();
        for (String item : value.split(" ")) {
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private String attachmentUrl(String instanceId, String file) {
        return "https://" + serverName + ".surveycto.com/view/submission-attachment/?uuid="
                + URLEncoder.encode(instanceId, StandardCharsets.UTF_8)
                + "&file=" + URLEncoder.encode(file, StandardCharsets.UTF_8);
    }

    private static String formatDate(String stored) {
        Matcher m = DATE_VALUE.matcher(stored);
        if (!m.matches()) {
            return stored;
        }
        LocalDate date = date(m, 1);
        return date == null ? stored : formatDate(date);
    }

    private static String formatTime(String stored) {
        Matcher m = TIME_VALUE.matcher(stored);
        if (!m.matches()) {
            return stored;
        }
        LocalTime time = time(m, 1);
        return time == null ? stored : formatTime(time);
    }

    static String formatDateTimeValue(String stored) {
        Matcher m = DATETIME_VALUE.matcher(stored);
        if (!m.matches()) {
            return stored;
        }
        LocalDate date = date(m, 1);
        LocalTime time = time(m, 4);
        return date == null || time == null ? stored : formatDate(date) + " " + formatTime(time);
    }

    private static LocalDate date(Matcher m, int group) {
        try {
            return LocalDate.of(Integer.parseInt(m.group(group)), Integer.parseInt(m.group(group + 1)),
                    Integer.parseInt(m.group(group + 2)));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static LocalTime time(Matcher m, int group) {
        try {
            String seconds = m.group(group + 2);
            return LocalTime.of(Integer.parseInt(m.group(group)), Integer.parseInt(m.group(group + 1)),
                    seconds == null ? 0 : Integer.parseInt(seconds));
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String formatDateTime(LocalDateTime value) {
        return formatDate(value.toLocalDate()) + " " + formatTime(value.toLocalTime());
    }

    private static String formatDate(LocalDate date) {
        return MONTHS[date.getMonthValue() - 1] + " " + date.getDayOfMonth() + ", " + date.getYear();
    }

    private static String formatTime(LocalTime time) {
        int hour = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
        return String.format(Locale.ROOT, "%d:%02d:%02d %s", hour, time.getMinute(), time.getSecond(),
                time.getHour() < 12 ? "AM" : "PM");
    }

    // ---- CSV ----

    private String fileTitle() {
        return title.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    static String csv(List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            List<String> row = rows.get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) {
                    out.append(',');
                }
                out.append(quote(row.get(j)));
            }
        }
        return out.toString();
    }

    private static String quote(String cell) {
        if (cell.indexOf(',') < 0 && cell.indexOf('"') < 0 && cell.indexOf('\r') < 0 && cell.indexOf('\n') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}
