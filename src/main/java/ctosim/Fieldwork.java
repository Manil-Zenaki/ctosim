package ctosim;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private List<InjectedProblem> problems = List.of();
    /** The form's start, duration() and location fields, by generic path in document order (spec 14). */
    private final List<String> startPaths;
    private final List<String> durationPaths;
    private final List<String> locationPaths;
    private final boolean hasLocation;

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
        Set<String> order = new LinkedHashSet<>();
        instancePaths(def.getMainInstance().getRoot(), order);
        this.startPaths = startPaths(form, order);
        this.durationPaths = durationPaths(form, order);
        this.locationPaths = locationPaths(def, order);
        this.hasLocation = !locationPaths.isEmpty();
        this.configWarnings = List.copyOf(configured);
        this.definition = def;
        this.form = form;
    }

    private final FormDef definition;
    private final LoadedForm form;

    public List<String> configWarnings() {
        return configWarnings;
    }

    public List<Submission> walkAll() {
        int n = config.submissions();
        int e = config.enumerators();
        int days = config.fieldworkDays();
        long quota = config.interviewsPerDay(); // spec 13, Amendment 1
        List<String> planWarnings = new ArrayList<>();
        boolean atQuestion = idAtQuestion();
        List<String> ids = atQuestion ? null : surveyIds(n, planWarnings);
        FormWalker.IdHistory history = atQuestion ? new FormWalker.IdHistory() : null;
        if (history != null) {
            cascade(history);
        }
        Problems plan = config.problems().map(rates -> new Problems(rates, n, e)).orElse(null);

        LocalDateTime[] lastEnd = new LocalDateTime[e + 1];
        int[] day = new int[e + 1];
        long[] today = new long[e + 1];
        Submission[] last = new Submission[e + 1];
        Visit[] lastVisit = new Visit[e + 1];
        boolean[] dayOver = new boolean[e + 1]; // after an off-hours interview or a trip (spec 14)
        int lastDay = 0;
        List<Submission> submissions = new ArrayList<>(n);
        List<Visit> planned = new ArrayList<>(n);
        List<InjectedProblem> injected = new ArrayList<>();
        Set<Integer> copies = new HashSet<>(); // resent copies, which take no place in the plan
        for (int number = 1; number <= n; number++) {
            int enumerator = (number - 1) % e + 1;
            SplittableRandom minutes = new SplittableRandom(FormWalker.seed(config.seed(), number, 10));
            int first = minutes.nextInt(FIRST_START_MINUTES + 1);
            int gap = MIN_GAP_MINUTES + minutes.nextInt(MAX_GAP_MINUTES - MIN_GAP_MINUTES + 1);
            String problem = plan == null ? null : plan.problemOf(number);

            Integer original = null;
            if (RESENT.equals(problem) || REINTERVIEW.equals(problem)) {
                original = plan.original(number, problem);
                if (original == null) {
                    problem = null;
                }
            }
            if (RESENT.equals(problem)) {
                Submission copy = resend(submissions.get(original - 1), planned.get(original - 1), number);
                copies.add(number);
                submissions.add(copy);
                planned.add(planned.get(original - 1));
                injected.add(resentRecord(copy, planned.get(original - 1), submissions.get(original - 1)));
                continue;
            }
            String originalId = null;
            if (REINTERVIEW.equals(problem)) {
                originalId = idField == null ? null : value(submissions.get(original - 1).instanceXml(), idField);
                if (originalId == null) {
                    plan.warn(problem, "problems: reinterview: the original " + original + " of submission " + number
                            + " has no survey id '" + idField + "'; walked as an ordinary interview");
                    problem = null;
                }
            }

            LocalDateTime start = null;
            double[] point = null;
            if (OFFHOURS.equals(problem) || OVERLAP.equals(problem) || TRAVEL.equals(problem)) {
                Submission previous = last[enumerator];
                String why = previous == null ? "its enumerator has no interview before it" : null;
                if (why == null && OFFHOURS.equals(problem)) {
                    LocalDateTime evening = dayStart(day[enumerator]).plusMinutes(WORK_MINUTES)
                            .plusMinutes(120 + new SplittableRandom(FormWalker.seed(config.seed(), number, 17)).nextInt(121));
                    LocalDateTime earliest = lastEnd[enumerator].plusMinutes(MIN_GAP_MINUTES);
                    LocalDateTime candidate = evening.isBefore(earliest) ? earliest : evening;
                    LocalDateTime nextWork = dayStart(day[enumerator] + 1);
                    if (!candidate.isBefore(nextWork) && !candidate.isAfter(nextWork.plusMinutes(WORK_MINUTES))) {
                        why = "it would start within the next day's working hours";
                    } else {
                        start = candidate;
                        point = household(number, village(enumerator, day[enumerator]));
                    }
                } else if (why == null && OVERLAP.equals(problem)) {
                    long length = java.time.Duration.between(previous.start(), previous.end()).getSeconds();
                    if (length < OVERLAP_SECONDS) {
                        why = "the previous interview lasts less than five minutes";
                    } else {
                        long offset = new SplittableRandom(FormWalker.seed(config.seed(), number, 18))
                                .nextLong(length - OVERLAP_SECONDS + 1);
                        start = previous.start().plusSeconds(offset);
                        point = household(number, village(enumerator, day[enumerator]));
                    }
                } else if (why == null && TRAVEL.equals(problem)) {
                    LocalDateTime candidate = lastEnd[enumerator].plusMinutes(gap);
                    if (candidate.isAfter(dayStart(day[enumerator]).plusMinutes(WORK_MINUTES))) {
                        why = "it would start after the end of work";
                    } else {
                        point = farAway(number, lastVisit[enumerator], Math.max(20_000, 2_000.0 * gap));
                        if (point == null) {
                            why = "no point of the area is far enough from the previous interview";
                        } else {
                            start = candidate;
                        }
                    }
                }
                if (why != null) {
                    plan.warn(problem, "problems: " + problem + ": submission " + number + " was walked as an ordinary"
                            + " interview: " + why);
                    problem = null;
                }
            }
            if (start == null) { // an ordinary visit (spec 13)
                if (lastEnd[enumerator] == null) {
                    day[enumerator] = 0;
                    start = dayStart(0).plusMinutes(first);
                } else {
                    LocalDateTime candidate = lastEnd[enumerator].plusMinutes(gap);
                    if (!dayOver[enumerator] && today[enumerator] < quota
                            && !candidate.isAfter(dayStart(day[enumerator]).plusMinutes(WORK_MINUTES))) {
                        start = candidate;
                    } else {
                        int next = day[enumerator] + 1;
                        LocalDateTime earliest = lastEnd[enumerator].plusMinutes(MIN_GAP_MINUTES);
                        while (dayStart(next).plusMinutes(first).isBefore(earliest)) {
                            next++; // an interview so long that it runs into the next day's work
                        }
                        day[enumerator] = next;
                        today[enumerator] = 0;
                        dayOver[enumerator] = false;
                        start = dayStart(next).plusMinutes(first);
                    }
                }
                point = household(number, village(enumerator, day[enumerator]));
            }

            String id = ids == null ? null : ids.get(number - 1);
            Map<String, String> given = Map.of();
            if (REINTERVIEW.equals(problem)) {
                id = originalId;
                if (history != null) {
                    Map<String, String> uppers = new HashMap<>();
                    for (String upper : history.uppers.keySet()) {
                        String v = value(submissions.get(original - 1).instanceXml(), upper);
                        if (v != null) {
                            uppers.put(upper, v);
                        }
                    }
                    given = uppers;
                }
            }
            double factor = 1;
            if (SHORT.equals(problem) || LONG.equals(problem)) {
                double u = new SplittableRandom(FormWalker.seed(config.seed(), number, 20)).nextDouble();
                factor = SHORT.equals(problem) ? 0.15 + 0.15 * u : 3 + 3 * u;
            }
            Set<String> faults = plan == null ? Set.of() : plan.faultsOf(enumerator);
            boolean fast = faults.contains(TOO_FAST);
            Visit visit = new Visit(enumerator, start, point[0], point[1], id);
            FormWalker.IdHistory before = history == null ? null : history.copy();
            FormWalker.Injection injection = new FormWalker.Injection(factor * (fast ? FAST_FACTOR : 1),
                    TRAVEL.equals(problem), given);
            injection.violationFactor = fast ? FAST_VIOLATIONS : 1;
            injection.skipModules = faults.contains(SKIP_MODULES);
            injection.dontKnow = faults.contains(DONT_KNOW);
            if (OUTLIER.equals(problem)) {
                FormWalker.Injection probe = injection.with(injection.timeFactor());
                probe.collectEligible = true;
                walker.walk(number, visit, copy(before), probe);
                if (probe.eligible.isEmpty()) {
                    plan.warn(OUTLIER, "problems: outlier: submission " + number + " has no question that can get an"
                            + " outlier within its constraint; it carries no outlier");
                } else {
                    injection.outlierField = probe.eligible.get(new SplittableRandom(
                            FormWalker.seed(config.seed(), number, 26)).nextInt(probe.eligible.size()));
                }
            }
            Submission s = walker.walk(number, visit, history, injection);
            submissions.add(s);
            planned.add(visit);
            if (injection.outlierRefused) {
                plan.warn(OUTLIER, "problems: outlier: submission " + number + ": the engine refused "
                        + FormWalker.OUTLIER_ATTEMPTS + " outlier values for '" + injection.outlierField
                        + "'; answered as usual");
            }

            if (REINTERVIEW.equals(problem)) {
                if (originalId.equals(value(s.instanceXml(), idField))) {
                    injected.add(record(s, visit, problem, idField, originalId, "",
                            key(submissions.get(original - 1))));
                } else {
                    plan.warn(problem, "problems: reinterview: submission " + number + " does not accept the survey id '"
                            + originalId + "' of submission " + original + "; it carries no problem");
                }
            } else if (factor != 1) {
                FormWalker.Injection without = injection.with(fast ? FAST_FACTOR : 1);
                Submission clean = walker.walk(number, visit, copy(before), without);
                injected.addAll(durationRecords(s, clean, visit, problem));
            } else if (OFFHOURS.equals(problem)) {
                injected.addAll(startRecords(s, visit, problem, ""));
            } else if (OVERLAP.equals(problem)) {
                injected.addAll(startRecords(s, visit, problem, key(last[enumerator])));
            } else if (TRAVEL.equals(problem)) {
                injected.addAll(travelRecords(s, visit, key(last[enumerator])));
            }
            injected.addAll(changes(s, visit, injection, OUTLIER));
            if (fast) {
                FormWalker.Injection without = injection.with(factor);
                without.violationFactor = 1;
                Submission clean = walker.walk(number, visit, copy(before), without);
                injected.addAll(durationRecords(s, clean, visit, TOO_FAST));
            }
            injected.addAll(changes(s, visit, injection, SKIP_MODULES));
            injected.addAll(changes(s, visit, injection, DONT_KNOW));

            if (OVERLAP.equals(problem)) {
                lastEnd[enumerator] = s.end().isAfter(lastEnd[enumerator]) ? s.end() : lastEnd[enumerator];
            } else {
                lastEnd[enumerator] = s.end();
            }
            if (OFFHOURS.equals(problem) || TRAVEL.equals(problem)) {
                dayOver[enumerator] = true;
            }
            last[enumerator] = s;
            lastVisit[enumerator] = visit;
            today[enumerator]++;
            lastDay = Math.max(lastDay, day[enumerator]);
        }
        if (lastDay + 1 > days) {
            planWarnings.add(0, "fieldwork: the interviews took " + (lastDay + 1) + " days, more than fieldworkDays ("
                    + days + "); the enumerators could not do them in time");
        }
        if (history != null && history.reused > 0) {
            planWarnings.add("fieldwork: " + history.reused + " submissions got a survey id '" + idField
                    + "' that an earlier submission had received; the offered choices ran out");
        }
        if (plan != null) {
            plan.smallSamples(planned, copies);
            planWarnings.addAll(plan.warnings());
        }
        this.visits = Collections.unmodifiableList(planned);
        this.warnings = List.copyOf(planWarnings);
        this.problems = List.copyOf(injected);
        return Collections.unmodifiableList(submissions);
    }

    public List<Visit> visits() {
        return visits;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** The problems injected by the last {@code walkAll()}. See specs/14-injected-problems.md. */
    public List<InjectedProblem> problems() {
        return problems;
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

    /**
     * True when the id is a select_one loaded by search() whose filter depends on the answers, and
     * its override has no value or texts: it is then given at the question (spec 13, Amendment 2).
     */
    private boolean idAtQuestion() {
        if (idQuestion == null || idQuestion.getControlType() != Constants.CONTROL_SELECT_ONE) {
            return false;
        }
        FieldOverride override = config.field(idField).orElse(null);
        if (override != null && (override.value().isPresent() || !override.texts().isEmpty())) {
            return false;
        }
        String appearance = idQuestion.getAppearanceAttr();
        List<String> args = appearance == null ? null : SearchChoices.searchArguments(appearance);
        if (args == null) {
            return false;
        }
        return (args.size() >= 4 && !isLiteral(args.get(3))) || (args.size() >= 6 && !isLiteral(args.get(5)));
    }

    /** Finds the upper questions of the id's cascade (spec 13, Amendment 3). */
    private void cascade(FormWalker.IdHistory history) {
        QuestionDef below = idQuestion;
        Set<String> seen = new HashSet<>();
        while (below != null) {
            String belowName = ((TreeReference) below.getBind().getReference()).getNameLast();
            if (!seen.add(belowName)) {
                return; // a cycle
            }
            List<String> args = SearchChoices.searchArguments(below.getAppearanceAttr() == null ? "" : below.getAppearanceAttr());
            if (args == null) {
                return;
            }
            QuestionDef upper = null;
            String reference = null;
            for (int position : new int[] {3, 5}) {
                if (args.size() > position && upper == null) {
                    upper = searchSelect(args.get(position), below);
                    reference = args.get(position);
                }
            }
            if (upper == null) {
                return;
            }
            String upperName = ((TreeReference) upper.getBind().getReference()).getNameLast();
            history.uppers.put(upperName, new FormWalker.Upper(belowName, below == idQuestion, below.getAppearanceAttr(),
                    SurveyCtoExporter.choiceValues(definition, below), reference));
            below = upper;
        }
    }

    /** The select_one loaded by search() that {@code arg} names by a single reference, or null. */
    private QuestionDef searchSelect(String arg, QuestionDef context) {
        org.javarosa.xpath.expr.XPathExpression parsed;
        try {
            parsed = org.javarosa.xpath.XPathParseTool.parseXPath(arg);
        } catch (org.javarosa.xpath.parser.XPathSyntaxException e) {
            return null;
        }
        if (!(parsed instanceof org.javarosa.xpath.expr.XPathPathExpr path)
                || path.init_context == org.javarosa.xpath.expr.XPathPathExpr.INIT_CONTEXT_EXPR) {
            return null;
        }
        TreeReference ref;
        try {
            ref = path.getReference();
            if (!ref.isAbsolute()) {
                ref = ref.anchor((TreeReference) context.getBind().getReference());
            }
        } catch (RuntimeException e) {
            return null;
        }
        if (ref == null) {
            return null;
        }
        String generic = ref.genericize().toString(false);
        QuestionDef found = questionAt(definition, generic);
        if (found == null || found.getControlType() != Constants.CONTROL_SELECT_ONE || found.getAppearanceAttr() == null
                || SearchChoices.searchArguments(found.getAppearanceAttr()) == null) {
            return null;
        }
        return found;
    }

    private static QuestionDef questionAt(IFormElement element, String generic) {
        if (element instanceof QuestionDef q && q.getBind() != null
                && generic.equals(((TreeReference) q.getBind().getReference()).genericize().toString(false))) {
            return q;
        }
        if (element.getChildren() != null) {
            for (IFormElement child : element.getChildren()) {
                QuestionDef found = questionAt(child, generic);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isLiteral(String arg) {
        String a = arg.strip();
        return a.length() >= 2 && (a.charAt(0) == '\'' || a.charAt(0) == '"') && a.charAt(a.length() - 1) == a.charAt(0)
                && a.indexOf(a.charAt(0), 1) == a.length() - 1;
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
        boolean select = idQuestion != null && idQuestion.getControlType() == Constants.CONTROL_SELECT_ONE;
        List<String> searched = select ? searchChoices() : null;
        if (searched != null) { // a range does not count for a search() list (spec 13, Amendment 1)
            searched.removeAll(config.specialValues());
            return distinct(searched);
        }
        if (integer && override != null && override.range().orElse(null) instanceof NumericRange) {
            return null;
        }
        if (select) {
            List<String> choices = new ArrayList<>(SurveyCtoExporter.choiceValues(definition, idQuestion));
            choices.removeAll(config.specialValues());
            return distinct(choices);
        }
        if (integer) {
            return null;
        }
        return List.of();
    }

    /**
     * The choices the id question offers when the form is opened, when they are loaded by search():
     * the file's and the static ones; null when the question has no search() appearance.
     */
    private List<String> searchChoices() {
        String appearance = idQuestion.getAppearanceAttr();
        if (appearance == null) {
            return null;
        }
        FormDef opened = form.newEntryController().getModel().getForm();
        TreeReference ref = (TreeReference) idQuestion.getBind().getReference();
        List<String> formChoices = SurveyCtoExporter.choiceValues(opened, idQuestion);
        SearchChoices.Result result = SearchChoices.load(appearance, formChoices, expression -> {
            try {
                Object value = org.javarosa.xpath.XPathParseTool.parseXPath(expression).eval(opened.getMainInstance(),
                        new org.javarosa.core.model.condition.EvaluationContext(opened.getEvaluationContext(), ref));
                return org.javarosa.xpath.expr.XPathFuncExpr.toString(org.javarosa.xpath.expr.XPathFuncExpr.unpack(value));
            } catch (org.javarosa.xpath.parser.XPathSyntaxException e) {
                throw new IllegalArgumentException("'" + expression + "' does not parse", e);
            }
        }, form.attachments());
        return result == null ? null : new ArrayList<>(result.choices());
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

    // --- Injected problems (spec 14) --------------------------------------------------

    static final String RESENT = "resent";
    static final String REINTERVIEW = "reinterview";
    static final String SHORT = "short";
    static final String LONG = "long";
    static final String OFFHOURS = "offhours";
    static final String OVERLAP = "overlap";
    static final String TRAVEL = "travel";
    static final String OUTLIER = "outlier";
    static final String TOO_FAST = "tooFast";
    static final String SKIP_MODULES = "skipModules";
    static final String DONT_KNOW = "dontKnow";
    private static final List<String> PROBLEMS = List.of(RESENT, REINTERVIEW, SHORT, LONG, OFFHOURS, OVERLAP, TRAVEL,
            OUTLIER);
    /** Where the warnings of faulty enumerators come, between those of travel and of outliers. */
    private static final String FAULTS = "faults";
    private static final List<String> WARNING_ORDER = List.of(RESENT, REINTERVIEW, SHORT, LONG, OFFHOURS, OVERLAP,
            TRAVEL, FAULTS, OUTLIER);
    private static final double FAST_FACTOR = 0.4;
    private static final double FAST_VIOLATIONS = 3;
    /** Fewer submissions than this and a faulty enumerator cannot be told from the others. */
    private static final int SMALL_SAMPLE = 10;
    private static final long OVERLAP_SECONDS = 300;
    private static final int FAR_ATTEMPTS = 100_000;

    /** The targets of one plan's problems, their originals, and the warnings, problem by problem. */
    private final class Problems {
        private final java.util.Map<Integer, String> targets = new HashMap<>();
        private final Set<Integer> originals = new HashSet<>();
        private final java.util.Map<String, List<String>> warnings = new HashMap<>();
        /** The faults of each faulty enumerator. */
        private final java.util.Map<Integer, Set<String>> faulty = new HashMap<>();

        Problems(ProblemRates rates, int n, int e) {
            double[] shares = {rates.resent(), rates.reinterview(), rates.shortDuration(), rates.longDuration(),
                rates.offHours(), rates.overlap(), rates.travel(), rates.outliers()};
            warnings.put(FAULTS, new ArrayList<>());
            SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), n, 16));
            for (int k = 0; k < PROBLEMS.size(); k++) {
                String problem = PROBLEMS.get(k);
                warnings.put(problem, new ArrayList<>());
                long count = count(shares[k], n);
                if (count == 0) {
                    continue;
                }
                List<Integer> eligible = new ArrayList<>();
                int from = SHORT.equals(problem) || LONG.equals(problem) || OUTLIER.equals(problem) ? 1 : e + 1;
                for (int number = from; number <= n; number++) {
                    if (!targets.containsKey(number)) {
                        eligible.add(number);
                    }
                }
                if (count > eligible.size()) {
                    warn(problem, "problems: " + problem + ": " + count + " asked for, " + eligible.size()
                            + " injected; there are not enough submissions");
                    count = eligible.size();
                }
                for (int i = 0; i < count; i++) {
                    int pick = i + random.nextInt(eligible.size() - i);
                    Collections.swap(eligible, i, pick);
                    targets.put(eligible.get(i), problem);
                }
                if (REINTERVIEW.equals(problem) && idField == null) {
                    warn(problem, "problems: reinterview: no survey id is configured, or the form has none;"
                            + " no reinterview was injected");
                }
                if (TRAVEL.equals(problem) && !hasLocation) {
                    warn(problem, "problems: travel: the form has no geopoint, geotrace or geoshape question;"
                            + " no travel problem was injected");
                }
            }
            faulty(rates, e);
        }

        /** Draws the faulty enumerators and gives each its faults (spec 14, Amendment 1). */
        private void faulty(ProblemRates rates, int e) {
            List<String> faults = new ArrayList<>();
            for (String fault : ProblemRates.FAULTS) {
                if (rates.faults().contains(fault)) {
                    faults.add(fault);
                }
            }
            if (faults.isEmpty()) {
                return;
            }
            int m = Math.min(faults.size(), e / 2);
            if (m == 0) {
                warn(FAULTS, "problems: faults: no fault was injected: with one enumerator, a faulty enumerator could"
                        + " not be compared with others");
                return;
            }
            if (m < faults.size()) {
                warn(FAULTS, "problems: faults: " + faults.size() + " faults are cumulated on " + m
                        + " enumerators, so that at least half of them stay clean; use at least " + 2 * faults.size()
                        + " enumerators for one enumerator per fault");
            }
            List<Integer> all = new ArrayList<>();
            for (int k = 1; k <= e; k++) {
                all.add(k);
            }
            SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), e, 25));
            for (int i = 0; i < m; i++) {
                Collections.swap(all, i, i + random.nextInt(all.size() - i));
            }
            for (int k = 0; k < faults.size(); k++) {
                faulty.computeIfAbsent(all.get(k % m), x -> new java.util.LinkedHashSet<>()).add(faults.get(k));
            }
        }

        Set<String> faultsOf(int enumerator) {
            return faulty.getOrDefault(enumerator, Set.of());
        }

        /** Warns about faulty enumerators with too few submissions to be told from the others. */
        void smallSamples(List<Visit> visits, Set<Integer> copies) {
            java.util.TreeMap<Integer, Integer> counts = new java.util.TreeMap<>();
            for (int enumerator : faulty.keySet()) {
                counts.put(enumerator, 0);
            }
            for (int i = 0; i < visits.size(); i++) {
                if (!copies.contains(i + 1)) {
                    counts.computeIfPresent(visits.get(i).enumerator(), (k, v) -> v + 1);
                }
            }
            counts.forEach((enumerator, count) -> {
                if (count < SMALL_SAMPLE) {
                    warn(FAULTS, "problems: faults: " + FormWalker.username(enumerator, config.enumerators())
                            + " is faulty (" + String.join(", ", faulty.get(enumerator)) + ") but has only " + count
                            + " submissions, too few for checks to tell it from the others");
                }
            });
        }

        String problemOf(int number) {
            String problem = targets.get(number);
            boolean impossible = TRAVEL.equals(problem) && !hasLocation || REINTERVIEW.equals(problem) && idField == null;
            return impossible ? null : problem; // the target stays reserved, walked as an ordinary visit
        }

        /** The original of a duplicate, drawn among earlier submissions that are no target and no original. */
        Integer original(int number, String problem) {
            List<Integer> candidates = new ArrayList<>();
            for (int m = 1; m < number; m++) {
                if (!targets.containsKey(m) && !originals.contains(m)) {
                    candidates.add(m);
                }
            }
            if (candidates.isEmpty()) {
                warn(problem, "problems: " + problem + ": submission " + number + " has no earlier submission to repeat;"
                        + " walked as an ordinary interview");
                return null;
            }
            int m = candidates.get(new SplittableRandom(FormWalker.seed(config.seed(), number, 22)).nextInt(candidates.size()));
            originals.add(m);
            return m;
        }

        void warn(String problem, String warning) {
            warnings.get(problem).add(warning);
        }

        List<String> warnings() {
            List<String> all = new ArrayList<>();
            for (String problem : WARNING_ORDER) {
                all.addAll(warnings.get(problem));
            }
            return all;
        }
    }

    /** A problem's count: its rate times N, rounded half up, at least 1 when the rate is above 0. */
    private static long count(double rate, int n) {
        if (rate <= 0) {
            return 0;
        }
        long count = java.math.BigDecimal.valueOf(rate).multiply(java.math.BigDecimal.valueOf(n))
                .setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return Math.max(1, count);
    }

    /** The original sent again: its instance with the target's own instanceID, uploaded later. */
    private Submission resend(Submission original, Visit originalVisit, int number) {
        String own = value(walker.walk(number, originalVisit).instanceXml(), "instanceID");
        String theirs = value(original.instanceXml(), "instanceID");
        String xml = own == null || theirs == null ? original.instanceXml() : original.instanceXml().replace(theirs, own);
        long later = 1 + new SplittableRandom(FormWalker.seed(config.seed(), number, 21)).nextInt(120);
        return new Submission(number, xml, original.start(), original.end(), original.submitted().plusMinutes(later),
                original.answered(), List.of());
    }

    private InjectedProblem resentRecord(Submission copy, Visit visit, Submission original) {
        String id = config.id().orElse(null);
        String value = id == null ? null : value(copy.instanceXml(), id);
        return new InjectedProblem(copy.number(), key(copy), FormWalker.username(visit.enumerator(), config.enumerators()),
                family(RESENT), RESENT, id == null ? "ipacheckdups" : "ipacheckids", id == null ? "" : id,
                value == null ? "" : value, "", key(original), true);
    }

    private InjectedProblem record(Submission s, Visit visit, String problem, String field, String value,
                                   String cleanValue, String related) {
        return new InjectedProblem(s.number(), key(s), FormWalker.username(visit.enumerator(), config.enumerators()),
                family(problem), problem, check(problem), field, value, cleanValue, related,
                !SHORT.equals(problem) && !LONG.equals(problem) && !OUTLIER.equals(problem) && !TOO_FAST.equals(problem)
                        && !SKIP_MODULES.equals(problem) && !DONT_KNOW.equals(problem));
    }

    private static String family(String problem) {
        return switch (problem) {
            case RESENT, REINTERVIEW -> "duplicate";
            case SHORT, LONG -> "duration";
            case OFFHOURS, OVERLAP -> "schedule";
            case OUTLIER -> "outlier";
            case TOO_FAST, SKIP_MODULES, DONT_KNOW -> "enumerator";
            default -> "travel";
        };
    }

    private static String check(String problem) {
        return switch (problem) {
            case RESENT, REINTERVIEW -> "ipacheckids";
            case SHORT, LONG, OUTLIER -> "ipacheckoutliers";
            case OFFHOURS -> "ipachecktimeuse";
            case TOO_FAST, SKIP_MODULES, DONT_KNOW -> "ipacheckenumdb";
            default -> "custom";
        };
    }

    /** The records of what a walk's faults or outlier changed, for one problem, in walk order. */
    private List<InjectedProblem> changes(Submission s, Visit visit, FormWalker.Injection injection, String problem) {
        List<InjectedProblem> records = new ArrayList<>();
        for (FormWalker.Change change : injection.changes) {
            if (change.problem().equals(problem)) {
                records.add(record(s, visit, problem, change.field(), change.value(), change.clean(), ""));
            }
        }
        return records;
    }

    private static FormWalker.IdHistory copy(FormWalker.IdHistory history) {
        return history == null ? null : history.copy();
    }

    private List<InjectedProblem> durationRecords(Submission s, Submission clean, Visit visit, String problem) {
        List<InjectedProblem> records = new ArrayList<>();
        for (String path : durationPaths) {
            String value = valueAt(s.instanceXml(), path);
            if (value != null) {
                String cleanValue = valueAt(clean.instanceXml(), path);
                records.add(record(s, visit, problem, lastStep(path), value, cleanValue == null ? "" : cleanValue, ""));
            }
        }
        if (records.isEmpty()) {
            records.add(record(s, visit, problem, "", Long.toString(seconds(s)), Long.toString(seconds(clean)), ""));
        }
        return records;
    }

    private static long seconds(Submission s) {
        return java.time.Duration.between(s.start(), s.end()).getSeconds();
    }

    private List<InjectedProblem> startRecords(Submission s, Visit visit, String problem, String related) {
        List<InjectedProblem> records = new ArrayList<>();
        for (String path : startPaths) {
            String value = valueAt(s.instanceXml(), path);
            records.add(record(s, visit, problem, lastStep(path),
                    value == null ? "" : SurveyCtoExporter.formatDateTimeValue(value), "", related));
        }
        if (records.isEmpty()) {
            records.add(record(s, visit, problem, "",
                    s.start().format(java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")), "", related));
        }
        return records;
    }

    private List<InjectedProblem> travelRecords(Submission s, Visit visit, String related) {
        List<InjectedProblem> records = new ArrayList<>();
        for (String path : locationPaths) {
            String value = valueAt(s.instanceXml(), path);
            if (value != null) {
                records.add(record(s, visit, TRAVEL, lastStep(path), value, "", related));
            }
        }
        return records;
    }

    /** A point of the area at least {@code metres} from the previous visit's point, or null when none is found. */
    private double[] farAway(int number, Visit previous, double metres) {
        SplittableRandom random = new SplittableRandom(FormWalker.seed(config.seed(), number, 19));
        Area area = area();
        for (int attempt = 0; attempt < FAR_ATTEMPTS; attempt++) {
            double lat = area.minLatitude() + random.nextDouble() * (area.maxLatitude() - area.minLatitude());
            double lon = area.minLongitude() + random.nextDouble() * (area.maxLongitude() - area.minLongitude());
            if (distance(lat, lon, previous.latitude(), previous.longitude()) >= metres) {
                return new double[] {lat, lon};
            }
        }
        return null;
    }

    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double h = Math.pow(Math.sin((p2 - p1) / 2), 2)
                + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
        return 2 * FormWalker.EARTH_RADIUS_METRES * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    // --- Values of an instance --------------------------------------------------------

    private static String key(Submission s) {
        String id = value(s.instanceXml(), "instanceID");
        return id == null ? "" : id;
    }

    /** The text of the first element named {@code name} in the instance; null when absent or empty. */
    static String value(String instanceXml, String name) {
        org.w3c.dom.NodeList found = parse(instanceXml).getElementsByTagNameNS("*", name);
        String text = found.getLength() == 0 ? null : found.item(0).getTextContent();
        return text == null || text.isEmpty() ? null : text;
    }

    /** The text at an absolute path of the instance (first element of each step); null when absent or empty. */
    private static String valueAt(String instanceXml, String path) {
        org.w3c.dom.Element at = parse(instanceXml).getDocumentElement();
        List<String> steps = new ArrayList<>(List.of(path.replaceFirst("^/", "").split("/")));
        if (steps.isEmpty() || at == null || !steps.remove(0).equals(at.getLocalName())) {
            return null;
        }
        for (String step : steps) {
            org.w3c.dom.Element next = null;
            for (org.w3c.dom.Node c = at.getFirstChild(); c != null && next == null; c = c.getNextSibling()) {
                if (c instanceof org.w3c.dom.Element el && step.equals(el.getLocalName())) {
                    next = el;
                }
            }
            if (next == null) {
                return null;
            }
            at = next;
        }
        String text = at.getTextContent();
        return text == null || text.isEmpty() ? null : text;
    }

    private static String lastStep(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static org.w3c.dom.Document parse(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("a submission's instance cannot be read", e);
        }
    }

    // --- The form's start, duration and location fields ------------------------------------

    /** The generic paths of the main instance's elements, in document order. */
    private static void instancePaths(TreeElement element, Set<String> out) {
        out.add(element.getRef().genericize().toString(false));
        for (int i = 0; i < element.getNumChildren(); i++) {
            instancePaths(element.getChildAt(i), out);
        }
    }

    private static List<String> startPaths(LoadedForm form, Set<String> order) {
        Set<String> starts = new HashSet<>();
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(form.engineXml()));
            org.w3c.dom.NodeList binds = doc.getElementsByTagNameNS("*", "bind");
            for (int i = 0; i < binds.getLength(); i++) {
                org.w3c.dom.Element bind = (org.w3c.dom.Element) binds.item(i);
                if ("timestamp".equals(bind.getAttributeNS(JAVAROSA_NS, "preload"))
                        && "start".equals(bind.getAttributeNS(JAVAROSA_NS, "preloadParams"))) {
                    starts.add(bind.getAttribute("nodeset").strip());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("the form's binds cannot be read", e);
        }
        return inOrder(order, starts);
    }

    private static List<String> durationPaths(LoadedForm form, Set<String> order) {
        Set<String> durations = new HashSet<>();
        form.durationCalculations().forEach((nodeset, calculate) -> {
            String bare = calculate.replaceAll("\\s", "");
            if (bare.equals("duration()") || bare.equals(EngineExpressions.STORE + "(duration())")) {
                durations.add(nodeset);
            }
        });
        return inOrder(order, durations);
    }

    private static List<String> locationPaths(FormDef def, Set<String> order) {
        Set<String> locations = new HashSet<>();
        locations(def.getMainInstance().getRoot(), locations);
        return inOrder(order, locations);
    }

    private static void locations(TreeElement element, Set<String> out) {
        int type = element.getDataType();
        if (type == Constants.DATATYPE_GEOPOINT || type == Constants.DATATYPE_GEOTRACE
                || type == Constants.DATATYPE_GEOSHAPE) {
            out.add(element.getRef().genericize().toString(false));
        }
        for (int i = 0; i < element.getNumChildren(); i++) {
            locations(element.getChildAt(i), out);
        }
    }

    private static List<String> inOrder(Set<String> order, Set<String> wanted) {
        List<String> paths = new ArrayList<>();
        for (String path : order) {
            if (wanted.contains(path)) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    private static final String JAVAROSA_NS = "http://openrosa.org/javarosa";
}
