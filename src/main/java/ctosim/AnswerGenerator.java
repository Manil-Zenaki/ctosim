package ctosim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import com.github.curiousoddman.rgxgen.RgxGen;

/** Proposes an answer to one question. See specs/05-answer-generator.md. */
public final class AnswerGenerator {

    private static final int FIRST_MINUTE = 6 * 60;
    private static final int LAST_MINUTE = 20 * 60;

    private final SimulationConfig config;
    private final Set<String> specialValues;

    public AnswerGenerator(SimulationConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.specialValues = Set.copyOf(config.specialValues());
    }

    /** The answer in XForm value format, or {@code null} to leave the question blank. */
    public String generate(Question question, LocalDate today, RandomGenerator random) {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(random, "random");

        FieldOverride override = config.field(question.name()).orElse(null);
        boolean select = question.kind() == QuestionKind.SELECT_ONE || question.kind() == QuestionKind.SELECT_MULTIPLE;
        List<String> words = Arrays.asList(question.appearance().split(" "));
        if (select && words.contains("label")) {
            return null; // the header row of a table: it shows the labels and cannot be answered
        }
        if (select && question.choices().isEmpty()) {
            return null;
        }
        double missingRate = override != null && override.missingRate().isPresent()
                ? override.missingRate().getAsDouble() : config.missingRate();
        if (!question.required() && random.nextDouble() < missingRate) {
            return null;
        }
        if (override != null && override.value().isPresent()) {
            return override.value().get();
        }

        Map<String, Double> weights = override == null ? Map.of() : override.weights();
        FieldRange range = override == null ? null : override.range().orElse(null);
        List<String> texts = override == null ? List.of() : override.texts();
        ConstraintHints chosen = chosenHints(question, random);
        if (chosen != question.hints()) {
            question = new Question(question.name(), question.kind(), question.choices(), question.appearance(),
                    question.required(), chosen);
        }
        Question q = question;
        ConstraintHints hints = chosen;

        return switch (question.kind()) {
            case SELECT_ONE -> selectOne(question.choices(), weights, hints, random);
            case SELECT_MULTIPLE -> selectMultiple(question.choices(), weights, hints, random);
            case INTEGER -> notExcluded(hints, random, () -> integer(hinted(range instanceof NumericRange r ? r : config.integerRange(),
                    hints, true), random));
            case DECIMAL -> notExcluded(hints, random, () -> decimal(hinted(range instanceof NumericRange r ? r : config.decimalRange(),
                    hints, false), random));
            case TEXT -> words.contains("numbers_decimal") && texts.isEmpty()
                    ? notExcluded(hints, random, () -> decimal(hinted(range instanceof NumericRange r ? r : config.decimalRange(), hints, false), random))
                    : texts.isEmpty() ? notExcluded(hints, random, () -> text(q, texts, random)) : text(q, texts, random);
            case DATE -> monthYear(date(hinted(dateRange(range, today), question.hints()), random), words).toString();
            case TIME -> time(random);
            case DATETIME -> date(hinted(dateRange(range, today), question.hints()), random) + "T" + time(random);
            case GEOPOINT -> point(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1);
            case GEOTRACE -> String.join("; ", trace(random));
            case GEOSHAPE -> shape(random);
            case BARCODE -> digits(10, random);
            case IMAGE -> media(question, ".jpg", random);
            case AUDIO -> media(question, ".m4a", random);
            case VIDEO -> media(question, ".mp4", random);
            case FILE -> media(question, ".bin", random);
            case ACKNOWLEDGE -> "OK";
        };
    }

    // --- Alternatives of a constraint (spec 09, Amendment 2) ---------------------------

    /** Draws again, up to 20 times, while the answer is one the constraint excludes. */
    private static String notExcluded(ConstraintHints hints, RandomGenerator random, java.util.function.Supplier<String> draw) {
        String answer = draw.get();
        for (int i = 0; i < 20 && answer != null && hints.excluded().contains(answer); i++) {
            answer = draw.get();
        }
        return answer;
    }

    /**
     * The hints to draw with: the question's own, or one of its satisfiable alternatives, a
     * special one (a single allowed value, such as a "don't know" code) with probability specialRate.
     */
    private ConstraintHints chosenHints(Question question, RandomGenerator random) {
        List<ConstraintHints> alternatives = question.hints().alternatives();
        if (alternatives.isEmpty()) {
            return question.hints();
        }
        List<ConstraintHints> special = new ArrayList<>();
        List<ConstraintHints> other = new ArrayList<>();
        for (ConstraintHints alternative : alternatives) {
            if (satisfiable(alternative, question.kind())) {
                (isSpecial(alternative, question.kind()) ? special : other).add(alternative);
            }
        }
        if (special.isEmpty() && other.isEmpty()) {
            return ConstraintHints.none();
        }
        List<ConstraintHints> group = special.isEmpty() ? other : other.isEmpty() ? special
                : random.nextDouble() < config.specialRate() ? special : other;
        return group.get(random.nextInt(group.size()));
    }

    private static boolean satisfiable(ConstraintHints h, QuestionKind kind) {
        boolean integer = kind == QuestionKind.INTEGER;
        if (h.lower() != null && h.upper() != null && closedLower(h.lower(), integer) > closedUpper(h.upper(), integer)) {
            return false;
        }
        if (h.earliest() != null && h.latest() != null && closedEarliest(h.earliest()).isAfter(closedLatest(h.latest()))) {
            return false;
        }
        if (h.minLength() != null && h.maxLength() != null && h.minLength() > h.maxLength()) {
            return false;
        }
        if (h.minSelected() != null && h.maxSelected() != null && h.minSelected() > h.maxSelected()) {
            return false;
        }
        for (String value : h.included()) {
            if (h.excluded().contains(value)) {
                return false;
            }
        }
        if (h.maxSelected() != null && h.included().size() > h.maxSelected()) {
            return false;
        }
        return kind != QuestionKind.SELECT_ONE || h.included().size() <= 1;
    }

    private static boolean isSpecial(ConstraintHints h, QuestionKind kind) {
        boolean integer = kind == QuestionKind.INTEGER;
        if (h.lower() != null && h.upper() != null && closedLower(h.lower(), integer) == closedUpper(h.upper(), integer)) {
            return true;
        }
        if (h.earliest() != null && h.latest() != null && closedEarliest(h.earliest()).equals(closedLatest(h.latest()))) {
            return true;
        }
        return !h.included().isEmpty() && h.maxSelected() != null && h.included().size() == h.maxSelected();
    }

    private static LocalDate closedEarliest(DateBound bound) {
        return bound.inclusive() ? bound.value() : bound.value().plusDays(1);
    }

    private static LocalDate closedLatest(DateBound bound) {
        return bound.inclusive() ? bound.value() : bound.value().minusDays(1);
    }

    // --- Selects --------------------------------------------------------------------

    /** Select one within the constraint's excluded and included choices; ignored when nothing is left. */
    private String selectOne(List<String> choices, Map<String, Double> weights, ConstraintHints hints, RandomGenerator random) {
        if (hints.excluded().isEmpty() && hints.included().isEmpty()) {
            return selectOne(choices, weights, random);
        }
        List<String> offered = new ArrayList<>(choices);
        offered.removeAll(hints.excluded());
        if (!hints.included().isEmpty()) {
            String value = hints.included().iterator().next();
            return offered.contains(value) ? value : selectOne(choices, weights, random);
        }
        return offered.isEmpty() ? selectOne(choices, weights, random) : selectOne(offered, weights, random);
    }

    /** Select multiple within the constraint's choices and selection count; ignored when it cannot be met. */
    private String selectMultiple(List<String> choices, Map<String, Double> weights, ConstraintHints hints,
                                  RandomGenerator random) {
        if (hints.excluded().isEmpty() && hints.included().isEmpty() && hints.minSelected() == null
                && hints.maxSelected() == null) {
            return selectMultiple(choices, weights, random);
        }
        List<String> offered = new ArrayList<>(choices);
        offered.removeAll(hints.excluded());
        if (!offered.containsAll(hints.included())) {
            return selectMultiple(choices, weights, random);
        }
        int min = Math.max(1, Math.max(hints.minSelected() == null ? 1 : hints.minSelected(), hints.included().size()));
        int max = Math.min(hints.maxSelected() != null ? hints.maxSelected() : Math.max(3, min), offered.size());
        if (min > max) {
            return selectMultiple(choices, weights, random);
        }
        int size = min + random.nextInt(max - min + 1);
        Set<String> chosen = new HashSet<>(hints.included());
        List<String> remaining = new ArrayList<>(offered);
        remaining.removeAll(chosen);
        while (chosen.size() < size) {
            chosen.add(remaining.remove(weightedIndex(remaining, weights, random)));
        }
        List<String> answer = new ArrayList<>();
        for (String choice : choices) {
            if (chosen.contains(choice) && !answer.contains(choice)) {
                answer.add(choice);
            }
        }
        return String.join(" ", answer);
    }

    private String selectOne(List<String> choices, Map<String, Double> weights, RandomGenerator random) {
        List<String> special = new ArrayList<>();
        List<String> ordinary = new ArrayList<>();
        split(choices, special, ordinary);
        if (!special.isEmpty() && (ordinary.isEmpty() || random.nextDouble() < config.specialRate())) {
            return special.get(random.nextInt(special.size()));
        }
        return ordinary.get(weightedIndex(ordinary, weights, random));
    }

    private String selectMultiple(List<String> choices, Map<String, Double> weights, RandomGenerator random) {
        List<String> special = new ArrayList<>();
        List<String> ordinary = new ArrayList<>();
        split(choices, special, ordinary);
        if (!special.isEmpty() && (ordinary.isEmpty() || random.nextDouble() < config.specialRate())) {
            return special.get(random.nextInt(special.size()));
        }
        int size = 1 + random.nextInt(Math.min(3, ordinary.size()));
        List<String> remaining = new ArrayList<>(ordinary);
        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < size; i++) {
            chosen.add(remaining.remove(weightedIndex(remaining, weights, random)));
        }
        List<String> answer = new ArrayList<>(size);
        for (String choice : choices) {
            if (chosen.contains(choice)) {
                answer.add(choice);
            }
        }
        return String.join(" ", answer);
    }

    private void split(List<String> choices, List<String> special, List<String> ordinary) {
        for (String choice : choices) {
            (specialValues.contains(choice) ? special : ordinary).add(choice);
        }
    }

    /**
     * Index drawn in proportion to the weights (a choice absent from {@code weights} weighs 0),
     * or uniformly when there are no weights or all candidates weigh 0.
     */
    private static int weightedIndex(List<String> candidates, Map<String, Double> weights, RandomGenerator random) {
        double total = 0;
        double[] w = new double[candidates.size()];
        if (!weights.isEmpty()) {
            for (int i = 0; i < w.length; i++) {
                w[i] = weights.getOrDefault(candidates.get(i), 0.0);
                total += w[i];
            }
        }
        if (total <= 0) {
            return random.nextInt(candidates.size());
        }
        double target = random.nextDouble() * total;
        int last = -1;
        for (int i = 0; i < w.length; i++) {
            if (w[i] > 0) {
                last = i;
                target -= w[i];
                if (target < 0) {
                    return i;
                }
            }
        }
        return last; // rounding left a tiny remainder: the last positive weight
    }

    // --- Numbers --------------------------------------------------------------------

    private static String integer(NumericRange range, RandomGenerator random) {
        double lo = Math.ceil(range.min());
        double hi = Math.floor(range.max());
        if (lo > hi) {
            return Long.toString(Math.round(range.min()));
        }
        long low = (long) lo;
        long span = (long) hi - low;
        return Long.toString(low + (span == 0 ? 0 : random.nextLong(span + 1)));
    }

    private static String decimal(NumericRange range, RandomGenerator random) {
        double value = range.min() == range.max() ? range.min() : random.nextDouble(range.min(), range.max());
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
        return rounded.signum() == 0 ? "0" : rounded.stripTrailingZeros().toPlainString();
    }

    /** SurveyCTO Collect stores the first day of the month for a month-year question. */
    private static LocalDate monthYear(LocalDate date, List<String> words) {
        return words.contains("month-year") ? date.withDayOfMonth(1) : date;
    }

    // --- Text -----------------------------------------------------------------------

    private static String text(Question question, List<String> texts, RandomGenerator random) {
        if (!texts.isEmpty()) {
            return texts.get(random.nextInt(texts.size()));
        }
        List<String> words = Arrays.asList(question.appearance().split(" "));
        boolean numeric = words.contains("numbers") || words.contains("numbers_phone");
        ConstraintHints hints = question.hints();
        if (hints.pattern() != null) {
            String matching = matching(hints.pattern(), random);
            if (matching != null) {
                return matching;
            }
        }
        if (hints.prefix() != null) {
            String prefix = hints.prefix();
            boolean digitsOnly = numeric || !prefix.isEmpty() && prefix.chars().allMatch(c -> c >= '0' && c <= '9');
            int rest = 8;
            boolean fits = true;
            if (hints.minLength() != null || hints.maxLength() != null) {
                int lo = Math.max(hints.minLength() != null ? hints.minLength() : prefix.length(), prefix.length());
                int hi = hints.maxLength() != null ? hints.maxLength() : Math.max(lo, 10); // the length rule's default
                fits = hi >= lo;
                rest = fits ? lo + random.nextInt(hi - lo + 1) - prefix.length() : 0;
            }
            if (fits) {
                StringBuilder s = new StringBuilder(prefix);
                for (int i = 0; i < rest; i++) {
                    s.append(digitsOnly ? (char) ('0' + random.nextInt(10)) : (char) ('a' + random.nextInt(26)));
                }
                return s.toString();
            }
        }
        if (hints.minLength() != null || hints.maxLength() != null) {
            int min = hints.minLength() != null ? hints.minLength() : 1;
            int max = hints.maxLength() != null ? hints.maxLength() : Math.max(min, 10);
            if (max >= min) {
                int length = min + random.nextInt(max - min + 1);
                StringBuilder s = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    s.append(numeric ? (char) ('0' + random.nextInt(10)) : (char) ('a' + random.nextInt(26)));
                }
                return s.toString();
            }
        }
        if (numeric) {
            return digits(8, random);
        }
        return question.name() + " " + (1 + random.nextInt(9999));
    }

    /** A random string matching the whole pattern, or null when none can be produced. */
    private static String matching(String pattern, RandomGenerator random) {
        try {
            String s = RgxGen.parse(pattern).generate(random);
            return s != null && Pattern.compile(pattern).matcher(s).matches() ? s : null;
        } catch (RuntimeException | StackOverflowError e) {
            return null; // unsupported or unsatisfiable pattern
        }
    }

    // --- Constraint hints (spec 09) -------------------------------------------------

    /** The numeric range to draw from once the constraint's bounds are taken into account. */
    private static NumericRange hinted(NumericRange range, ConstraintHints hints, boolean integer) {
        Bound lower = hints.lower();
        Bound upper = hints.upper();
        if (lower == null && upper == null) {
            return range;
        }
        Double lo = lower == null ? null : closedLower(lower, integer);
        Double hi = upper == null ? null : closedUpper(upper, integer);
        if (lo != null && hi != null && lo > hi) {
            return range; // contradictory hints are ignored
        }
        double a = Math.max(range.min(), lo == null ? Double.NEGATIVE_INFINITY : lo);
        double b = Math.min(range.max(), hi == null ? Double.POSITIVE_INFINITY : hi);
        if (a <= b) {
            return new NumericRange(a, b);
        }
        if (lo != null && hi != null) {
            return new NumericRange(lo, hi);
        }
        double width = range.max() - range.min();
        return lo != null ? new NumericRange(lo, lo + width) : new NumericRange(hi - width, hi);
    }

    private static double closedLower(Bound bound, boolean integer) {
        double v = bound.value();
        if (integer) {
            return bound.inclusive() ? Math.ceil(v) : Math.floor(v) + 1;
        }
        return bound.inclusive() ? v : v + 0.01;
    }

    private static double closedUpper(Bound bound, boolean integer) {
        double v = bound.value();
        if (integer) {
            return bound.inclusive() ? Math.floor(v) : Math.ceil(v) - 1;
        }
        return bound.inclusive() ? v : v - 0.01;
    }

    /** The date range to draw from once the constraint's date bounds are taken into account. */
    private static DateRange hinted(DateRange range, ConstraintHints hints) {
        DateBound earliest = hints.earliest();
        DateBound latest = hints.latest();
        if (earliest == null && latest == null) {
            return range;
        }
        LocalDate lo = earliest == null ? null
                : earliest.inclusive() ? earliest.value() : earliest.value().plusDays(1);
        LocalDate hi = latest == null ? null
                : latest.inclusive() ? latest.value() : latest.value().minusDays(1);
        if (lo != null && hi != null && lo.isAfter(hi)) {
            return range;
        }
        LocalDate a = lo == null || lo.isBefore(range.min()) ? range.min() : lo;
        LocalDate b = hi == null || hi.isAfter(range.max()) ? range.max() : hi;
        if (!a.isAfter(b)) {
            return new DateRange(a, b);
        }
        if (lo != null && hi != null) {
            return new DateRange(lo, hi);
        }
        return lo != null ? new DateRange(lo, lo.plusDays(364)) : new DateRange(hi.minusDays(364), hi);
    }

    private static String digits(int count, RandomGenerator random) {
        StringBuilder s = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            s.append((char) ('0' + random.nextInt(10)));
        }
        return s.toString();
    }

    // --- Dates and times ------------------------------------------------------------

    private static DateRange dateRange(FieldRange range, LocalDate today) {
        return range instanceof DateRange r ? r : new DateRange(today.minusDays(364), today);
    }

    private static LocalDate date(DateRange range, RandomGenerator random) {
        long days = ChronoUnit.DAYS.between(range.min(), range.max());
        return range.min().plusDays(days == 0 ? 0 : random.nextLong(days + 1));
    }

    private static String time(RandomGenerator random) {
        int minute = FIRST_MINUTE + random.nextInt(LAST_MINUTE - FIRST_MINUTE + 1);
        return String.format(Locale.ROOT, "%02d:%02d:00", minute / 60, minute % 60);
    }

    // --- Locations and media --------------------------------------------------------

    private static String point(double lat, double lon) {
        return String.format(Locale.ROOT, "%.6f %.6f 0 5", lat, lon);
    }

    private static List<String> trace(RandomGenerator random) {
        double lat = random.nextDouble() * 2 - 1;
        double lon = random.nextDouble() * 2 - 1;
        List<String> points = new ArrayList<>(4);
        points.add(point(lat, lon));
        for (int i = 1; i < 4; i++) {
            lat += random.nextDouble() * 0.002 - 0.001;
            lon += random.nextDouble() * 0.002 - 0.001;
            points.add(point(lat, lon));
        }
        return points;
    }

    private static String shape(RandomGenerator random) {
        List<String> points = trace(random);
        points.add(points.get(0));
        return String.join("; ", points);
    }

    private static String media(Question question, String extension, RandomGenerator random) {
        return question.name() + "_" + (1 + random.nextInt(999999)) + extension;
    }
}
