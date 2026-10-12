package ctosim;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TimeZone;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.AnswerDataFactory;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.UncastData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

/** Fills one form from beginning to end. See specs/07-form-walker.md. */
public final class FormWalker {

    static final int MAX_ATTEMPTS = 50;
    /** Converter bind attributes (spec 02, Amendment 3), in {@link FormLoader#CTOSIM_NS}. */
    static final String MINIMUM_SECONDS = "minimum-seconds";
    static final String SPEED_VIOLATIONS = "speed-violations";
    static final int MAX_STEPS = 100_000;
    /** Passes of the end-of-form constraint check that may answer questions again (Amendment 5). */
    static final int MAX_CONSTRAINT_PASSES = 5;
    private static final int START_MINUTES = 540;
    private static final int MIN_SECONDS = 5;
    private static final int MAX_SECONDS = 60;

    private final LoadedForm form;
    private final SimulationConfig config;
    private final AnswerGenerator generator;
    private final List<String> configWarnings;
    private final String[] deviceIds;
    /** Generic path of each field name of the form (its first occurrence), for ${name} in expressions. */
    private final Map<String, String> paths = new HashMap<>();

    public FormWalker(LoadedForm form, SimulationConfig config) {
        this.form = Objects.requireNonNull(form, "form");
        this.config = Objects.requireNonNull(config, "config");
        this.generator = new AnswerGenerator(config);
        this.deviceIds = deviceIds(config);

        Set<String> names = new HashSet<>();
        collectNames(form.newEntryController().getModel().getForm().getMainInstance().getRoot(), names);
        List<String> warnings = new ArrayList<>();
        for (String name : config.overriddenFields()) {
            if (!names.contains(name)) {
                warnings.add("configuration: field '" + name + "' does not name any element of form '"
                        + form.formId() + "'");
            }
        }
        collectPaths(form.newEntryController().getModel().getForm().getMainInstance().getRoot(), "", paths);
        Set<String> referenced = new java.util.TreeSet<>();
        for (String name : config.overriddenFields()) {
            FieldOverride override = config.field(name).orElseThrow();
            override.expression().ifPresent(e -> referenced.addAll(references(e)));
            for (OverrideRule rule : override.rules()) {
                referenced.addAll(references(rule.condition()));
                rule.override().expression().ifPresent(e -> referenced.addAll(references(e)));
            }
        }
        for (String name : referenced) {
            if (!names.contains(name) && !config.overriddenFields().contains(name)) {
                warnings.add("configuration: field '" + name + "' (in an expression) does not name any element of form '"
                        + form.formId() + "'");
            }
        }
        warnings.sort(null);
        this.configWarnings = List.copyOf(warnings);
    }

    public List<String> configWarnings() {
        return configWarnings;
    }

    public Submission walk(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("submission numbers start at 1: " + number);
        }
        // Stored date-times carry the offset of the JVM's default zone (spec 07, Amendment 1).
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(config.timeZone()));
        try {
            return new Walk(number).run();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    /**
     * One 16-character hexadecimal device id (like an Android id) per enumerator, drawn in enumerator order from the seed alone, so
     * an enumerator keeps its device whatever the number of enumerators.
     */
    private static String[] deviceIds(SimulationConfig config) {
        SplittableRandom random = new SplittableRandom(seed(config.seed(), 0, 6));
        String[] ids = new String[config.enumerators()];
        Set<String> seen = new HashSet<>();
        for (int e = 0; e < ids.length; e++) {
            String id;
            do {
                StringBuilder digits = new StringBuilder(15);
                for (int k = 0; k < 16; k++) {
                    digits.append(Character.forDigit(random.nextInt(16), 16));
                }
                id = digits.toString();
            } while (!seen.add(id));
            ids[e] = id;
        }
        return ids;
    }

    private static String username(int enumerator, int enumerators) {
        int width = Math.max(2, String.valueOf(enumerators).length());
        return "enumerator" + String.format(Locale.ROOT, "%0" + width + "d", enumerator);
    }

    private static final java.util.regex.Pattern REFERENCE = java.util.regex.Pattern.compile("\\$\\{([^}]*)\\}");

    /** The field names written as ${name} in an expression of the configuration. */
    private static Set<String> references(String expression) {
        Set<String> names = new HashSet<>();
        java.util.regex.Matcher m = REFERENCE.matcher(expression);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    private static void collectPaths(TreeElement element, String parent, Map<String, String> paths) {
        String path = parent + "/" + element.getName();
        paths.putIfAbsent(element.getName(), path);
        for (int i = 0; i < element.getNumChildren(); i++) {
            collectPaths(element.getChildAt(i), path, paths);
        }
    }

    private static void collectNames(TreeElement element, Set<String> names) {
        names.add(element.getName());
        for (int i = 0; i < element.getNumChildren(); i++) {
            collectNames(element.getChildAt(i), names);
        }
    }

    /** Mixes the configuration seed, the submission number and a stream id into one seed. */
    private static long seed(long base, long number, long stream) {
        long h = base * 0x9E3779B97F4A7C15L + number;
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L + stream;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** The state of one submission's walk. */
    private final class Walk {
        private final int number;
        private final SplittableRandom answers;
        private final SplittableRandom timing;
        private final SplittableRandom repeats;
        private final SimulatedClock clock;
        private final LocalDateTime start;
        private final FormEntryController session;
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Integer> repeatTargets = new HashMap<>();
        private final SplittableRandom violationDraws;
        /** The submission's profile (spec 07, Amendment 7); "" without profiles. */
        private final String profile;
        private final List<String> violations = new ArrayList<>();
        private int answered;

        Walk(int number) {
            this.number = number;
            long base = config.seed();
            this.answers = new SplittableRandom(seed(base, number, 2));
            this.timing = new SplittableRandom(seed(base, number, 3));
            this.repeats = new SplittableRandom(seed(base, number, 4));
            this.start = config.start()
                    .plusDays(timing.nextInt(config.fieldworkDays()))
                    .plusMinutes(timing.nextInt(START_MINUTES + 1));
            this.violationDraws = new SplittableRandom(seed(base, number, 8));
            this.profile = drawProfile(new SplittableRandom(seed(base, number, 9)));
            this.clock = new SimulatedClock(start);
            int enumerator = 1 + new SplittableRandom(seed(base, number, 5)).nextInt(config.enumerators());
            this.session = form.newSimulatedSession(clock, seed(base, number, 1), Map.of(
                    "username", username(enumerator, config.enumerators()),
                    "deviceid", deviceIds[enumerator - 1]));
        }

        Submission run() {
            int steps = 0;
            for (int event = step(++steps); event != FormEntryController.EVENT_END_OF_FORM; event = step(++steps)) {
                if (event == FormEntryController.EVENT_QUESTION) {
                    question(session.getModel().getQuestionPrompt());
                } else if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                    newRepeatPrompt();
                }
            }
            enforceConstraints();
            FormDef def = session.getModel().getForm();
            reportSpeedViolations(def, def.getMainInstance().getRoot());
            refreshDurations(def, def.getMainInstance().getRoot());
            def.postProcessInstance();
            String xml;
            try {
                xml = new String(new XFormSerializingVisitor().serializeInstance(def.getInstance()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("submission " + number + ": the instance could not be serialised", e);
            }
            LocalDateTime end = clock.now();
            LocalDateTime submitted = end.plusMinutes(1 + new SplittableRandom(seed(config.seed(), number, 7)).nextInt(240));
            return new Submission(number, xml, start, end, submitted, answered, warnings);
        }

        private int step(int steps) {
            if (steps > MAX_STEPS) {
                TreeReference at = session.getModel().getFormIndex().getReference();
                throw new IllegalStateException("submission " + number + ": stopped after " + MAX_STEPS
                        + " navigation steps at " + (at == null ? "the beginning of the form" : at.toString(true)));
            }
            return session.stepToNextEvent();
        }

        // --- Questions ----------------------------------------------------------------

        private void question(FormEntryPrompt prompt) {
            TreeReference ref = session.getModel().getFormIndex().getReference();
            String name = ref.getNameLast();
            String path = ref.toString(true);
            // The prompt's own copy of the node's bind attributes: resolving the node again would
            // scan its siblings, which is slow in large repeats.
            TreeElement here = TreeElement.getAttribute(prompt.getBindAttributes(),
                    FormLoader.CTOSIM_NS, FormLoader.CALCULATE_HERE);
            if (here != null) {
                FormDef def = session.getModel().getForm();
                try {
                    calculate(def, def.getMainInstance().resolveReference(ref), ref, here.getAttributeValue());
                } catch (RuntimeException e) {
                    warn("field '" + name + "' (" + path + "): its calculate_here expression could not be evaluated ("
                            + e.getMessage() + "); value kept");
                }
                return;
            }
            if (prompt.isReadOnly()) {
                return;
            }
            boolean overridden = config.field(name).isPresent();
            if (prompt.getAnswerValue() != null && !overridden && satisfiesConstraint(ref, prompt)) {
                return; // keep a default or a value set by a calculation or an action
            }
            QuestionKind kind = kind(prompt);
            if (kind == null) {
                warn("field '" + name + "' (" + path + ") uses a control that cannot be simulated; left unanswered");
                return;
            }
            Prepared prepared = prepare(prompt, ref, name, path, kind, true);
            Question question = prepared.question();
            List<String> loaded = prepared.loaded();
            boolean required = question.required();
            double minimum = minimumSeconds(prompt);
            Effective effective = effectiveOverride(name, path, ref);
            FieldOverride override = effective.override();
            if (effective.blank()) {
                clear(ref); // an expression whose value is empty leaves the question blank
                handled(ref, minimum);
                return;
            }

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String text = generator.generate(question, clock.now().toLocalDate(), answers, override);
                if (text == null) {
                    clear(ref);
                    handled(ref, minimum);
                    return;
                }
                IAnswerData data = loaded == null ? convert(prompt, text) : convertLoaded(kind, text, loaded);
                if (data == null) {
                    clear(ref);
                    warn("field '" + name + "' (" + path + "): the value '" + text + "' cannot be given to this question; left blank");
                    warnIfRequired(required, name, path);
                    handled(ref, minimum);
                    return;
                }
                if (session.answerQuestion(data, true) == FormEntryController.ANSWER_OK) {
                    answered++;
                    handled(ref, minimum);
                    return;
                }
            }
            if (tryCandidates(prompt, question, kind, loaded) || search(prompt, question, kind, loaded, ref)) {
                answered++;
                handled(ref, minimum);
                return;
            }
            clear(ref);
            warn("field '" + name + "' (" + path + "): no answer satisfied its constraint after "
                    + MAX_ATTEMPTS + " attempts; left blank");
            warnIfRequired(required, name, path);
            handled(ref, minimum);
        }

        /** The generator's question for the current prompt, with its search() choices when it has any. */
        private record Prepared(Question question, List<String> loaded) { }

        private Prepared prepare(FormEntryPrompt prompt, TreeReference ref, String name, String path, QuestionKind kind,
                                 boolean report) {
            List<String> choices = new ArrayList<>();
            if (prompt.getSelectChoices() != null && (kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE)) {
                for (SelectChoice choice : prompt.getSelectChoices()) {
                    choices.add(choice.getValue());
                }
            }
            String appearance = prompt.getQuestion().getAppearanceAttr();
            // Choices loaded from an attached file by search() (spec 07, Amendment 3).
            List<String> loaded = null;
            if (appearance != null && (kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE)) {
                // A choice filter does not apply: the placeholder and static choices come from the whole list.
                List<String> formChoices = SurveyCtoExporter.choiceValues(session.getModel().getForm(), prompt.getQuestion());
                SearchChoices.Result search = SearchChoices.load(appearance, formChoices,
                        expression -> evaluate(expression, ref), form.attachments());
                if (search != null) {
                    if (search.warning() != null && report) {
                        warn("field '" + name + "' (" + path + "): " + search.warning() + "; only its static choices are offered");
                    }
                    choices = search.choices();
                    loaded = choices;
                }
            }
            Question question = new Question(name, kind, choices, appearance == null ? "" : appearance,
                    prompt.isRequired(), ConstraintHints.forCurrentQuestion(session));
            return new Prepared(question, loaded);
        }

        private boolean satisfiesConstraint(TreeReference ref, FormEntryPrompt prompt) {
            return session.getModel().getForm().evaluateConstraint(ref, prompt.getAnswerValue());
        }

        // --- Constraints at the end of the form (Amendment 5) ---------------------------

        /**
         * Like SurveyCTO Collect when a form is finalised: every question that holds a value must
         * satisfy its constraint on the final instance. A question that does not is answered again;
         * after a few passes, or when no answer is accepted, it is cleared.
         */
        private void enforceConstraints() {
            for (int pass = 1; pass <= MAX_CONSTRAINT_PASSES; pass++) {
                if (!constraintPass(false)) {
                    return;
                }
            }
            while (constraintPass(true)) {
                // each pass clears at least one value, so this ends
            }
        }

        /** One pass over the questions in form order; true when it changed a value. */
        private boolean constraintPass(boolean clearOnly) {
            session.jumpToIndex(FormIndex.createBeginningOfFormIndex());
            boolean changed = false;
            int steps = 0;
            for (int event = step(++steps); event != FormEntryController.EVENT_END_OF_FORM; event = step(++steps)) {
                if (event != FormEntryController.EVENT_QUESTION) {
                    continue;
                }
                FormEntryPrompt prompt = session.getModel().getQuestionPrompt();
                TreeReference ref = session.getModel().getFormIndex().getReference();
                // The value is read from the instance: the prompt drops a select value that its
                // filtered choice list does not hold, such as a value loaded by search().
                TreeElement node = session.getModel().getForm().getMainInstance().resolveReference(ref);
                IAnswerData value = node == null ? null : node.getValue();
                QuestionKind kind = kind(prompt);
                if (prompt.isReadOnly() || value == null || kind == null
                        || TreeElement.getAttribute(prompt.getBindAttributes(), FormLoader.CTOSIM_NS,
                        FormLoader.CALCULATE_HERE) != null) {
                    continue;
                }
                if (session.getModel().getForm().evaluateConstraint(ref, value)) {
                    continue;
                }
                changed = true;
                String name = ref.getNameLast();
                String path = ref.toString(true);
                if (clearOnly || !answerAgain(prompt, ref, name, path, kind)) {
                    clear(ref);
                    warn("field '" + name + "' (" + path + "): its constraint does not hold at the end of the form; left blank");
                    warnIfRequired(prompt.isRequired(), name, path);
                }
            }
            return changed;
        }

        /** Asks the generator again for the current question; true when the engine accepted an answer. */
        private boolean answerAgain(FormEntryPrompt prompt, TreeReference ref, String name, String path, QuestionKind kind) {
            Prepared prepared = prepare(prompt, ref, name, path, kind, false);
            FieldOverride override = effectiveOverride(name, path, ref).override();
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String text = generator.generate(prepared.question(), clock.now().toLocalDate(), answers, override);
                IAnswerData data = text == null ? null
                        : prepared.loaded() == null ? convert(prompt, text) : convertLoaded(kind, text, prepared.loaded());
                if (data != null && session.answerQuestion(data, true) == FormEntryController.ANSWER_OK) {
                    return true;
                }
            }
            return tryCandidates(prompt, prepared.question(), kind, prepared.loaded())
                    || search(prompt, prepared.question(), kind, prepared.loaded(), ref);
        }

        /**
         * Tries, level by level, the constraint's string literals (text questions) and the
         * generator's search values; answers one the constraint accepts, drawn uniformly among the
         * accepted values of the first level that has any (spec 07, Amendment 6).
         */
        private boolean search(FormEntryPrompt prompt, Question question, QuestionKind kind, List<String> loaded,
                               TreeReference ref) {
            List<List<String>> levels = new ArrayList<>();
            if (kind == QuestionKind.TEXT || kind == QuestionKind.BARCODE) {
                levels.add(ConstraintHints.stringLiterals(session));
            }
            levels.addAll(generator.searchValues(question, clock.now().toLocalDate()));
            FormDef def = session.getModel().getForm();
            for (List<String> level : levels) {
                List<IAnswerData> accepted = new ArrayList<>();
                for (String value : level) {
                    IAnswerData data = loaded == null ? convert(prompt, value) : convertLoaded(kind, value, loaded);
                    if (data != null && def.evaluateConstraint(ref, data)) {
                        accepted.add(data);
                    }
                }
                if (!accepted.isEmpty()) {
                    IAnswerData chosen = accepted.get(answers.nextInt(accepted.size()));
                    if (session.answerQuestion(chosen, true) == FormEntryController.ANSWER_OK) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The constraint's candidate values, in order, until the engine accepts one (spec 09, Amendment 2). */
        private boolean tryCandidates(FormEntryPrompt prompt, Question question, QuestionKind kind, List<String> loaded) {
            for (String candidate : question.hints().candidates()) {
                IAnswerData data = loaded == null ? convert(prompt, candidate) : convertLoaded(kind, candidate, loaded);
                if (data != null && session.answerQuestion(data, true) == FormEntryController.ANSWER_OK) {
                    return true;
                }
            }
            return false;
        }

        // --- Rules, expressions and profiles (spec 07, Amendment 7) ---------------------

        private String drawProfile(SplittableRandom random) {
            Map<String, Double> profiles = config.profiles();
            if (profiles.isEmpty()) {
                return "";
            }
            double total = profiles.values().stream().mapToDouble(Double::doubleValue).sum();
            double target = random.nextDouble() * total;
            String last = "";
            for (Map.Entry<String, Double> entry : profiles.entrySet()) {
                if (entry.getValue() > 0) {
                    last = entry.getKey();
                    target -= entry.getValue();
                    if (target < 0) {
                        return entry.getKey();
                    }
                }
            }
            return last;
        }

        /** A question's override as it applies, and whether its expression asks for a blank answer. */
        private record Effective(FieldOverride override, boolean blank) { }

        /**
         * The question's override once its first applicable rule is merged in and its expression
         * evaluated (an expression's value becomes a fixed value; an empty value, a blank answer).
         */
        private Effective effectiveOverride(String name, String path, TreeReference ref) {
            FieldOverride override = config.field(name).orElse(null);
            if (override == null) {
                return new Effective(null, false);
            }
            FieldOverride base = override;
            OverrideRule applied = null;
            for (OverrideRule rule : base.rules()) {
                Object value = configExpression(rule.condition(), name, path, ref);
                if (value != null && truth(value)) {
                    applied = rule;
                    break;
                }
            }
            override = applied != null ? base.with(applied.override()) : base.withoutRules();
            if (override.expression().isPresent()) {
                Object value = configExpression(override.expression().get(), name, path, ref);
                if (value == null) { // the expression is absent: the other settings apply
                    return new Effective(applied != null ? base.with(applied.override().withoutExpression())
                            : base.withoutRules().withoutExpression(), false);
                }
                String text = XPathFuncExpr.toString(XPathFuncExpr.unpack(value));
                return new Effective(override.fixedTo(text), text.isEmpty());
            }
            return new Effective(override, false);
        }

        /** XPath boolean(): a node-set is true when it is not empty. */
        private static boolean truth(Object value) {
            return value instanceof org.javarosa.xpath.XPathNodeset nodes ? nodes.size() > 0
                    : XPathFuncExpr.toBoolean(XPathFuncExpr.unpack(value));
        }

        /** An XPath string literal for {@code s}, whatever quotes it holds. */
        private static String literal(String s) {
            if (!s.contains("'")) {
                return "'" + s + "'";
            }
            if (!s.contains("\"")) {
                return "\"" + s + "\"";
            }
            StringBuilder concat = new StringBuilder("concat(''");
            for (String part : s.split("'", -1)) {
                concat.append(", '").append(part).append("'");
                concat.append(", \"'\"");
            }
            concat.setLength(concat.length() - ", \"'\"".length());
            return concat.append(")").toString();
        }

        /** Function names of an expression that neither the engine nor the form's session provides. */
        private void unknownFunctions(org.javarosa.xpath.expr.XPathExpression e, EvaluationContext ec, Set<String> out) {
            if (e instanceof org.javarosa.xpath.expr.XPathFuncExpr f) {
                String fn = f.id.toString();
                if (!SurveyCtoFunctions.ENGINE_BUILT_INS.contains(fn) && !ec.getFunctionHandlers().containsKey(fn)) {
                    out.add(fn);
                }
                for (org.javarosa.xpath.expr.XPathExpression a : f.args) {
                    unknownFunctions(a, ec, out);
                }
            } else if (e instanceof org.javarosa.xpath.expr.XPathBinaryOpExpr b) {
                unknownFunctions(b.a, ec, out);
                unknownFunctions(b.b, ec, out);
            } else if (e instanceof org.javarosa.xpath.expr.XPathUnaryOpExpr u) {
                unknownFunctions(u.a, ec, out);
            } else if (e instanceof org.javarosa.xpath.expr.XPathPathExpr p) {
                if (p.filtExpr != null) {
                    unknownFunctions(p.filtExpr, ec, out);
                }
                for (org.javarosa.xpath.expr.XPathStep step : p.steps) {
                    if (step.predicates != null) {
                        for (org.javarosa.xpath.expr.XPathExpression pred : step.predicates) {
                            unknownFunctions(pred, ec, out);
                        }
                    }
                }
            } else if (e instanceof org.javarosa.xpath.expr.XPathFilterExpr f) {
                unknownFunctions(f.x, ec, out);
            }
        }

        /** A configuration expression evaluated for the question; null (with a warning) when it cannot be. */
        private Object configExpression(String expression, String name, String path, TreeReference ref) {
            StringBuilder resolved = new StringBuilder();
            java.util.regex.Matcher m = REFERENCE.matcher(expression);
            while (m.find()) {
                String field = paths.get(m.group(1));
                if (field == null) {
                    warn("field '" + name + "' (" + path + "): the configuration expression '" + expression
                            + "' names an unknown field '" + m.group(1) + "'");
                    return null;
                }
                m.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(" " + field + " "));
            }
            m.appendTail(resolved);
            String text = resolved.toString().replaceAll("\\bprofile\\(\\s*\\)",
                    java.util.regex.Matcher.quoteReplacement(literal(profile)));
            text = EngineExpressions.rewriteExpression(text, new int[] {1_000_000}, genericPath(ref));
            try {
                FormDef def = session.getModel().getForm();
                EvaluationContext ec = new EvaluationContext(def.getEvaluationContext(), ref);
                org.javarosa.xpath.expr.XPathExpression parsed = XPathParseTool.parseXPath(text);
                Set<String> unknown = new java.util.TreeSet<>();
                unknownFunctions(parsed, ec, unknown);
                if (!unknown.isEmpty()) {
                    throw new IllegalArgumentException("unknown function " + String.join(", ", unknown));
                }
                Object value = parsed.eval(def.getMainInstance(), ec);
                return value instanceof org.javarosa.xpath.XPathNodeset ? value : XPathFuncExpr.unpack(value);
            } catch (XPathSyntaxException | RuntimeException e) {
                warn("field '" + name + "' (" + path + "): the configuration expression '" + expression
                        + "' cannot be evaluated (" + e.getMessage() + ")");
                return null;
            }
        }

        /** The string value of {@code expression} evaluated for the node at {@code ref}. */
        private String evaluate(String expression, TreeReference ref) {
            FormDef def = session.getModel().getForm();
            XPathExpression parsed;
            try {
                parsed = XPathParseTool.parseXPath(expression);
            } catch (XPathSyntaxException e) {
                throw new IllegalArgumentException("'" + expression + "' does not parse", e);
            }
            Object value = parsed.eval(def.getMainInstance(), new EvaluationContext(def.getEvaluationContext(), ref));
            return XPathFuncExpr.toString(XPathFuncExpr.unpack(value));
        }

        /** Recomputes, in document order, every relevant field whose calculation calls duration(). */
        private void refreshDurations(FormDef def, TreeElement element) {
            Map<String, String> calculations = form.durationCalculations();
            if (calculations.isEmpty() || element.getMult() == TreeReference.INDEX_TEMPLATE) {
                return;
            }
            String expression = calculations.get(genericPath(element.getRef()));
            if (expression != null && element.isRelevant()) {
                calculate(def, element, element.getRef(), expression);
            }
            for (int i = 0; i < element.getNumChildren(); i++) {
                refreshDurations(def, element.getChildAt(i));
            }
        }

        private void warnIfRequired(boolean required, String name, String path) {
            if (required) {
                warn("field '" + name + "' (" + path + ") is required but has no value");
            }
        }

        private void clear(TreeReference ref) {
            session.getModel().getForm().setValue(null, ref, true);
        }

        /**
         * After a question was answered or left blank: a speed violation may be recorded when it has a
         * minimum time, and the clock advances, by at least that minimum.
         */
        private void handled(TreeReference ref, double minimum) {
            if (minimum > 0 && violationDraws.nextDouble() < config.speedViolationRate()) {
                violations.add(violationPath(ref));
            }
            long seconds = MIN_SECONDS + timing.nextInt(MAX_SECONDS - MIN_SECONDS + 1);
            if (minimum > 0) {
                seconds = Math.max(seconds, (long) Math.ceil(minimum));
            }
            clock.advance(Duration.ofSeconds(seconds));
        }

        /** Sets the speed-violation count and list fields from the violations recorded. */
        private void reportSpeedViolations(FormDef def, TreeElement element) {
            if (element.getMult() == TreeReference.INDEX_TEMPLATE) {
                return;
            }
            String report = element.getBindAttributeValue(FormLoader.CTOSIM_NS, SPEED_VIOLATIONS);
            if (("count".equals(report) || "list".equals(report)) && element.isRelevant()) {
                String value = violations.isEmpty() ? null
                        : "count".equals(report) ? String.valueOf(violations.size()) : String.join(",", violations);
                def.setValue(value == null ? null : new StringData(value), element.getRef(), true);
            }
            for (int i = 0; i < element.getNumChildren(); i++) {
                reportSpeedViolations(def, element.getChildAt(i));
            }
        }

        // --- Repeats ------------------------------------------------------------------

        private void newRepeatPrompt() {
            TreeReference ref = session.getModel().getFormIndex().getReference();
            int existing = ref.getMultiplicity(ref.size() - 1);
            String name = ref.getNameLast();
            String key = ref.getParentRef().toString(true) + "/" + name;
            int target = repeatTargets.computeIfAbsent(key, k -> drawRepeatCount(name));
            if (existing < target) {
                session.newRepeat();
            }
        }

        private int drawRepeatCount(String repeatName) {
            int min = config.repeatMin();
            int max = config.repeatMax();
            FieldRange range = config.field(repeatName).flatMap(FieldOverride::range).orElse(null);
            if (range instanceof NumericRange r) {
                min = (int) Math.ceil(r.min());
                max = (int) Math.floor(r.max());
            }
            return max <= min ? Math.max(min, 0) : min + repeats.nextInt(max - min + 1);
        }

        private void warn(String message) {
            warnings.add("submission " + number + ": " + message);
        }
    }

    // --- Engine types -----------------------------------------------------------------

    /** Evaluates {@code expression} at {@code ref} as the engine evaluates a calculate, and stores the result. */
    static void calculate(FormDef def, TreeElement node, TreeReference ref, String expression) {
        XPathExpression parsed;
        try {
            parsed = XPathParseTool.parseXPath(expression);
        } catch (XPathSyntaxException e) {
            throw new IllegalStateException("the expression does not parse: " + e.getMessage(), e);
        }
        EvaluationContext context = new EvaluationContext(def.getEvaluationContext(), ref);
        Object result = parsed.eval(def.getMainInstance(), context);
        def.setValue(IAnswerData.wrapData(result, node.getDataType()), ref, true);
    }

    /** The minimum time of a question, from its bind; NaN when it has none or it cannot be read. */
    static double minimumSeconds(FormEntryPrompt prompt) {
        TreeElement attribute = TreeElement.getAttribute(prompt.getBindAttributes(), FormLoader.CTOSIM_NS, MINIMUM_SECONDS);
        if (attribute == null) {
            return Double.NaN;
        }
        try {
            double value = Double.parseDouble(attribute.getAttributeValue());
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** A question as SurveyCTO lists it among speed violations: main[1]/member[2]/member_name. */
    static String violationPath(TreeReference ref) {
        StringBuilder path = new StringBuilder();
        for (int i = 1; i < ref.size(); i++) {
            if (i > 1) {
                path.append('/');
            }
            path.append(ref.getName(i));
            if (i < ref.size() - 1) {
                path.append('[').append(Math.max(ref.getMultiplicity(i), 0) + 1).append(']');
            }
        }
        return path.toString();
    }

    /** A reference as a path without positions: /data/member/age. */
    static String genericPath(TreeReference ref) {
        return ref.toString(false).replaceAll("\\[[^\\]]*\\]", "");
    }

    /** The generator's kind for a question, or null when its control cannot be simulated. */
    static QuestionKind kind(FormEntryPrompt prompt) {
        switch (prompt.getControlType()) {
            case Constants.CONTROL_SELECT_ONE:
                return QuestionKind.SELECT_ONE;
            case Constants.CONTROL_SELECT_MULTI:
                return QuestionKind.SELECT_MULTIPLE;
            case Constants.CONTROL_TRIGGER:
                return QuestionKind.ACKNOWLEDGE;
            case Constants.CONTROL_IMAGE_CHOOSE:
                return QuestionKind.IMAGE;
            case Constants.CONTROL_AUDIO_CAPTURE:
                return QuestionKind.AUDIO;
            case Constants.CONTROL_VIDEO_CAPTURE:
                return QuestionKind.VIDEO;
            case Constants.CONTROL_FILE_CAPTURE:
            case Constants.CONTROL_UPLOAD:
                return QuestionKind.FILE;
            case Constants.CONTROL_RANK:
            case Constants.CONTROL_OSM_CAPTURE:
                return null;
            default:
                break;
        }
        switch (prompt.getDataType()) {
            case Constants.DATATYPE_TEXT:
                return QuestionKind.TEXT;
            case Constants.DATATYPE_INTEGER:
            case Constants.DATATYPE_LONG:
                return QuestionKind.INTEGER;
            case Constants.DATATYPE_DECIMAL:
                return QuestionKind.DECIMAL;
            case Constants.DATATYPE_DATE:
                return QuestionKind.DATE;
            case Constants.DATATYPE_TIME:
                return QuestionKind.TIME;
            case Constants.DATATYPE_DATE_TIME:
                return QuestionKind.DATETIME;
            case Constants.DATATYPE_GEOPOINT:
                return QuestionKind.GEOPOINT;
            case Constants.DATATYPE_GEOTRACE:
                return QuestionKind.GEOTRACE;
            case Constants.DATATYPE_GEOSHAPE:
                return QuestionKind.GEOSHAPE;
            case Constants.DATATYPE_BARCODE:
                return QuestionKind.BARCODE;
            default:
                return null;
        }
    }

    /** The engine answer for the generator's text, or null when it cannot be converted. */
    static IAnswerData convert(FormEntryPrompt prompt, String text) {
        int control = prompt.getControlType();
        if (control == Constants.CONTROL_SELECT_ONE) {
            SelectChoice choice = offered(prompt, text);
            return choice == null ? null : new SelectOneData(choice.selection());
        }
        if (control == Constants.CONTROL_SELECT_MULTI) {
            List<Selection> selections = new ArrayList<>();
            for (String value : text.split(" ")) {
                if (value.isEmpty()) {
                    continue;
                }
                SelectChoice choice = offered(prompt, value);
                if (choice == null) {
                    return null;
                }
                selections.add(choice.selection());
            }
            return selections.isEmpty() ? null : new SelectMultiData(selections);
        }
        IAnswerData template = AnswerDataFactory.templateByDataType(prompt.getDataType());
        if (template == null) {
            return new StringData(text);
        }
        try {
            return template.cast(new UncastData(text));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The answer to a question whose choices were loaded by search(): the engine's own choice list
     * holds only the placeholder, so the chosen values are stored as they are.
     */
    static IAnswerData convertLoaded(QuestionKind kind, String text, List<String> choices) {
        List<Selection> selections = new ArrayList<>();
        for (String value : text.split(" ")) {
            if (value.isEmpty()) {
                continue;
            }
            if (!choices.contains(value)) {
                return null;
            }
            selections.add(new Selection(value));
        }
        if (selections.isEmpty() || kind == QuestionKind.SELECT_ONE && selections.size() > 1) {
            return null;
        }
        return kind == QuestionKind.SELECT_ONE ? new SelectOneData(selections.get(0)) : new SelectMultiData(selections);
    }

    private static SelectChoice offered(FormEntryPrompt prompt, String value) {
        List<SelectChoice> choices = prompt.getSelectChoices();
        if (choices != null) {
            for (SelectChoice choice : choices) {
                if (choice.getValue().equals(value)) {
                    return choice;
                }
            }
        }
        return null;
    }
}
