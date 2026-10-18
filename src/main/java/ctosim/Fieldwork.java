package ctosim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.IFormElement;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;

/** Simulates all submissions as planned fieldwork. See specs/13-fieldwork-plan.md. */
public final class Fieldwork {

    /** Length of a working day, from the time of day of the configuration's start. */
    static final int WORK_MINUTES = 540;
    static final int FIRST_START_MINUTES = 60;
    static final int MIN_GAP_MINUTES = 10;
    static final int MAX_GAP_MINUTES = 40;
    /** Distance from the village within which interviews take place; a little under the spec's 1.5 km. */
    static final double VILLAGE_METRES = 1_450;
    /** Above this number of possible ids, distinct ids are drawn one by one instead of shuffling them all. */
    private static final long SHUFFLE_LIMIT = 1_000_000;

    private final SimulationConfig config;
    private final FormWalker walker;
    private final List<String> configWarnings;
    /** The id question, or null when the configuration names none or the form has no element of that name. */
    private final String idField;
    private final QuestionDef idQuestion;
    private final int idType;
    private List<Visit> visits = List.of();
    private List<String> warnings = List.of();

    public Fieldwork(LoadedForm form, SimulationConfig config) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.walker = new FormWalker(java.util.Objects.requireNonNull(form, "form"), config);
        List<String> configured = new ArrayList<>(walker.configWarnings());
        FormDef def = form.newEntryController().getModel().getForm();
        Set<String> names = new HashSet<>();
        collectNames(def.getMainInstance().getRoot(), names);
        String name = config.id().orElse(null);
        if (name != null && !names.contains(name)) {
            configured.add("configuration: the survey id '" + name + "' does not name any element of form '"
                    + form.formId() + "'");
            name = null;
        }
        this.idField = name;
        this.idQuestion = name == null ? null : question(def, name);
        this.idType = idQuestion == null ? Constants.DATATYPE_NULL : dataType(def, idQuestion);
        this.configWarnings = List.copyOf(configured);
        this.definition = def;
    }

    private final FormDef definition;

    public List<String> configWarnings() {
        return configWarnings;
    }

    public List<Submission> walkAll() {
        int n = config.submissions();
        int e = config.enumerators();
        int days = config.fieldworkDays();
        long quota = Math.max(1, ((long) n + (long) e * days - 1) / ((long) e * days));
        List<String> planWarnings = new ArrayList<>();
        List<String> ids = surveyIds(n, planWarnings);

        LocalDateTime[] lastEnd = new LocalDateTime[e + 1];
        int[] day = new int[e + 1];
        long[] today = new long[e + 1];
        int lastDay = 0;
        List<Submission> submissions = new ArrayList<>(n);
        List<Visit> planned = new ArrayList<>(n);
        for (int number = 1; number <= n; number++) {
            int enumerator = (number - 1) % e + 1;
            SplittableRandom minutes = new SplittableRandom(FormWalker.seed(config.seed(), number, 10));
            int first = minutes.nextInt(FIRST_START_MINUTES + 1);
            int gap = MIN_GAP_MINUTES + minutes.nextInt(MAX_GAP_MINUTES - MIN_GAP_MINUTES + 1);
            LocalDateTime start;
            if (lastEnd[enumerator] == null) {
                day[enumerator] = 0;
                start = dayStart(0).plusMinutes(first);
            } else {
                LocalDateTime candidate = lastEnd[enumerator].plusMinutes(gap);
                if (today[enumerator] < quota && !candidate.isAfter(dayStart(day[enumerator]).plusMinutes(WORK_MINUTES))) {
                    start = candidate;
                } else {
                    int next = day[enumerator] + 1;
                    LocalDateTime earliest = lastEnd[enumerator].plusMinutes(MIN_GAP_MINUTES);
                    while (dayStart(next).plusMinutes(first).isBefore(earliest)) {
                        next++; // an interview so long that it runs into the next day's work
                    }
                    day[enumerator] = next;
                    today[enumerator] = 0;
                    start = dayStart(next).plusMinutes(first);
                }
            }
            double[] village = village(enumerator, day[enumerator]);
            double[] point = household(number, village);
            Visit visit = new Visit(enumerator, start, point[0], point[1], ids == null ? null : ids.get(number - 1));
            Submission s = walker.walk(number, visit);
            submissions.add(s);
            planned.add(visit);
            lastEnd[enumerator] = s.end();
            today[enumerator]++;
            lastDay = Math.max(lastDay, day[enumerator]);
        }
        if (lastDay + 1 > days) {
            planWarnings.add(0, "fieldwork: the interviews took " + (lastDay + 1) + " days, more than fieldworkDays ("
                    + days + "); the enumerators could not do them in time");
        }
        this.visits = Collections.unmodifiableList(planned);
        this.warnings = List.copyOf(planWarnings);
        return Collections.unmodifiableList(submissions);
    }

    public List<Visit> visits() {
        return visits;
    }

    public List<String> warnings() {
        return warnings;
    }

    // --- When and where -------------------------------------------------------------

    private LocalDateTime dayStart(int day) {
        return config.start().plusDays(day);
    }

    /** The village of an enumerator on a day: uniform in the area, or in spec 05's rectangle. */
    private double[] village(int enumerator, int day) {
        SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), (long) enumerator << 32 | day, 11));
        Area area = area();
        return new double[] {
            area.minLatitude() + random.nextDouble() * (area.maxLatitude() - area.minLatitude()),
            area.minLongitude() + random.nextDouble() * (area.maxLongitude() - area.minLongitude())};
    }

    /** A point within {@link #VILLAGE_METRES} of the village and within the area. */
    private double[] household(int number, double[] village) {
        SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), number, 12));
        double distance = VILLAGE_METRES * Math.sqrt(random.nextDouble());
        double bearing = random.nextDouble() * 2 * Math.PI;
        double dLat = Math.toDegrees(distance * Math.cos(bearing) / FormWalker.EARTH_RADIUS_METRES);
        double cos = Math.max(Math.cos(Math.toRadians(village[0])), 1e-6);
        double dLon = Math.toDegrees(distance * Math.sin(bearing) / FormWalker.EARTH_RADIUS_METRES) / cos;
        Area area = area();
        // Moving a coordinate into the area brings the point closer to the village, which is inside it.
        double lat = clamp(village[0] + dLat, area.minLatitude(), area.maxLatitude());
        double lon = clamp(village[1] + dLon, area.minLongitude(), area.maxLongitude());
        return new double[] {lat, lon};
    }

    private Area area() {
        return config.area().orElse(new Area(-1, 1, -1, 1));
    }

    private static double clamp(double x, double min, double max) {
        return Math.max(min, Math.min(max, x));
    }

    // --- Survey ids -----------------------------------------------------------------

    /** The id of each submission, in number order; null when there is no id question or no possible value. */
    private List<String> surveyIds(int n, List<String> planWarnings) {
        if (idField == null) {
            return null;
        }
        List<String> values = possibleValues();
        long count = values == null ? integerCount() : values.size();
        if (count == 0) {
            planWarnings.add("fieldwork: the survey id '" + idField + "' has no possible values; its answers are"
                    + " drawn as usual and may repeat");
            return null;
        }
        SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), 0, 13));
        List<String> order;
        if (values != null) {
            order = shuffled(values, random);
        } else if (count <= SHUFFLE_LIMIT) {
            List<String> all = new ArrayList<>((int) count);
            for (long k = integerMin(); k <= integerMax(); k++) {
                all.add(Long.toString(k));
            }
            order = shuffled(all, random);
        } else {
            Set<Long> seen = new HashSet<>();
            order = new ArrayList<>(n);
            while (order.size() < n) {
                long k = integerMin() + random.nextLong(count);
                if (seen.add(k)) {
                    order.add(Long.toString(k));
                }
            }
        }
        if (count < n) {
            planWarnings.add("fieldwork: the survey id '" + idField + "' has only " + count + " possible values for "
                    + n + " submissions; some ids are used twice");
        }
        List<String> ids = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            ids.add(order.get(k % order.size()));
        }
        return ids;
    }

    private static List<String> shuffled(List<String> values, SplittableRandom random) {
        List<String> order = new ArrayList<>(values);
        for (int i = order.size() - 1; i > 0; i--) {
            Collections.swap(order, i, random.nextInt(i + 1));
        }
        return order;
    }

    /**
     * The id's possible values when they are listed (value, texts, choices), in their order and
     * without repeats; null when they are the whole numbers of a range (see {@link #integerMin()}).
     */
    private List<String> possibleValues() {
        FieldOverride override = config.field(idField).orElse(null);
        if (override != null && override.value().isPresent()) {
            return distinct(List.of(override.value().get()));
        }
        if (override != null && !override.texts().isEmpty()) {
            return distinct(override.texts());
        }
        boolean integer = idType == Constants.DATATYPE_INTEGER || idType == Constants.DATATYPE_LONG;
        if (integer && override != null && override.range().orElse(null) instanceof NumericRange) {
            return null;
        }
        if (idQuestion != null && idQuestion.getControlType() == Constants.CONTROL_SELECT_ONE) {
            List<String> choices = new ArrayList<>(SurveyCtoExporter.choiceValues(definition, idQuestion));
            choices.removeAll(config.specialValues());
            return distinct(choices);
        }
        if (integer) {
            return null;
        }
        return List.of();
    }

    private NumericRange integerRange() {
        FieldOverride override = config.field(idField).orElse(null);
        if (override != null && override.range().orElse(null) instanceof NumericRange r) {
            return r;
        }
        return config.integerRange();
    }

    private long integerMin() {
        return (long) Math.ceil(integerRange().min());
    }

    private long integerMax() {
        return (long) Math.floor(integerRange().max());
    }

    private long integerCount() {
        return Math.max(0, integerMax() - integerMin() + 1);
    }

    private static List<String> distinct(List<String> values) {
        Set<String> kept = new LinkedHashSet<>();
        for (String v : values) {
            if (!v.isEmpty()) {
                kept.add(v);
            }
        }
        return new ArrayList<>(kept);
    }

    // --- The form -------------------------------------------------------------------

    private static void collectNames(TreeElement element, Set<String> names) {
        names.add(element.getName());
        for (int i = 0; i < element.getNumChildren(); i++) {
            collectNames(element.getChildAt(i), names);
        }
    }

    /** The first question of the form, in body order, whose field has this name. */
    private static QuestionDef question(IFormElement element, String name) {
        if (element instanceof QuestionDef q && q.getBind() != null
                && name.equals(((TreeReference) q.getBind().getReference()).getNameLast())) {
            return q;
        }
        if (element.getChildren() != null) {
            for (IFormElement child : element.getChildren()) {
                QuestionDef found = question(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int dataType(FormDef def, QuestionDef question) {
        TreeReference ref = ((TreeReference) question.getBind().getReference()).genericize();
        TreeElement template = def.getMainInstance().getTemplatePath(ref);
        return template == null ? Constants.DATATYPE_NULL : template.getDataType();
    }
}
