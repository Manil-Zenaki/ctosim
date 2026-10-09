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
import org.javarosa.xpath.parser.XPathSyntaxException;

/** Fills one form from beginning to end. See specs/07-form-walker.md. */
public final class FormWalker {

    static final int MAX_ATTEMPTS = 50;
    static final int MAX_STEPS = 100_000;
    private static final int START_MINUTES = 540;
    private static final int MIN_SECONDS = 5;
    private static final int MAX_SECONDS = 60;

    private final LoadedForm form;
    private final SimulationConfig config;
    private final AnswerGenerator generator;
    private final List<String> configWarnings;
    private final String[] deviceIds;

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
     * One 15-digit device id per enumerator, drawn in enumerator order from the seed alone, so
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
                for (int k = 0; k < 15; k++) {
                    digits.append(random.nextInt(10));
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
            FormDef def = session.getModel().getForm();
            refreshDurations(def, def.getMainInstance().getRoot());
            def.postProcessInstance();
            String xml;
            try {
                xml = new String(new XFormSerializingVisitor().serializeInstance(def.getInstance()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("submission " + number + ": the instance could not be serialised", e);
            }
            return new Submission(number, xml, start, clock.now(), answered, warnings);
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
            FormDef def = session.getModel().getForm();
            TreeElement node = def.getMainInstance().resolveReference(ref);
            String here = node == null ? null : node.getBindAttributeValue(FormLoader.CTOSIM_NS, FormLoader.CALCULATE_HERE);
            if (here != null) {
                try {
                    calculate(def, node, ref, here);
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
            if (prompt.getAnswerValue() != null && !overridden) {
                return; // keep a default or a value set by a calculation or an action
            }
            QuestionKind kind = kind(prompt);
            if (kind == null) {
                warn("field '" + name + "' (" + path + ") uses a control that cannot be simulated; left unanswered");
                return;
            }
            List<String> choices = new ArrayList<>();
            if (prompt.getSelectChoices() != null && (kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE)) {
                for (SelectChoice choice : prompt.getSelectChoices()) {
                    choices.add(choice.getValue());
                }
            }
            String appearance = prompt.getQuestion().getAppearanceAttr();
            boolean required = prompt.isRequired();
            Question question = new Question(name, kind, choices, appearance == null ? "" : appearance, required,
                    ConstraintHints.forCurrentQuestion(session));

            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                String text = generator.generate(question, clock.now().toLocalDate(), answers);
                if (text == null) {
                    clear(ref);
                    advanceClock();
                    return;
                }
                IAnswerData data = convert(prompt, text);
                if (data == null) {
                    clear(ref);
                    warn("field '" + name + "' (" + path + "): the value '" + text + "' cannot be given to this question; left blank");
                    warnIfRequired(required, name, path);
                    advanceClock();
                    return;
                }
                if (session.answerQuestion(data, true) == FormEntryController.ANSWER_OK) {
                    answered++;
                    advanceClock();
                    return;
                }
            }
            clear(ref);
            warn("field '" + name + "' (" + path + "): no answer satisfied its constraint after "
                    + MAX_ATTEMPTS + " attempts; left blank");
            warnIfRequired(required, name, path);
            advanceClock();
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

        private void advanceClock() {
            clock.advance(Duration.ofSeconds(MIN_SECONDS + timing.nextInt(MAX_SECONDS - MIN_SECONDS + 1)));
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
