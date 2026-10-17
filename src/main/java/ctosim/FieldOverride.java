package ctosim;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** How one field is answered, from the "fields" section of a configuration. See specs/04-simulation-config.md. */
public final class FieldOverride {

    private final String value;
    private final FieldRange range;
    private final Map<String, Double> weights;
    private final List<String> texts;
    private final Double missingRate;
    private final Distribution distribution;
    private final String expression;
    private final Area area;
    private final List<OverrideRule> rules;

    FieldOverride(String value, FieldRange range, Map<String, Double> weights, List<String> texts, Double missingRate) {
        this(value, range, weights, texts, missingRate, null, null, null, List.of());
    }

    FieldOverride(String value, FieldRange range, Map<String, Double> weights, List<String> texts, Double missingRate,
                  Distribution distribution, String expression, Area area, List<OverrideRule> rules) {
        this.value = value;
        this.range = range;
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
        this.texts = List.copyOf(texts);
        this.missingRate = missingRate;
        this.distribution = distribution;
        this.expression = expression;
        this.area = area;
        this.rules = List.copyOf(rules);
    }

    /**
     * This override with {@code rule}'s settings in place of its own settings of the same keys
     * (min and max together), and without rules (spec 07, Amendment 7).
     */
    FieldOverride with(FieldOverride rule) {
        boolean fixes = rule.value != null || rule.expression != null;
        String v = rule.value != null ? rule.value : rule.expression != null ? null : value;
        String e = rule.expression != null ? rule.expression : rule.value != null ? null : expression;
        return new FieldOverride(v,
                rule.range != null ? rule.range : fixes ? null : range,
                !rule.weights.isEmpty() ? rule.weights : fixes ? Map.of() : weights,
                !rule.texts.isEmpty() ? rule.texts : fixes ? List.of() : texts,
                rule.missingRate != null ? rule.missingRate : missingRate,
                rule.distribution != null ? rule.distribution : fixes ? null : distribution,
                e,
                rule.area != null ? rule.area : area,
                List.of());
    }

    /** This override with {@code range} as its range (spec 07, Amendment 9); a new one when null. */
    static FieldOverride withRange(FieldOverride override, FieldRange range) {
        if (override == null) {
            return new FieldOverride(null, range, Map.of(), List.of(), null);
        }
        return new FieldOverride(override.value, range, override.weights, override.texts, override.missingRate,
                override.distribution, override.expression, override.area, override.rules);
    }

    /** An override that answers with one of {@code texts} (spec 10, Amendment 2). */
    static FieldOverride ofTexts(List<String> texts) {
        return new FieldOverride(null, null, Map.of(), texts, null);
    }

    /** This override without its rules. */
    FieldOverride withoutRules() {
        return new FieldOverride(value, range, weights, texts, missingRate, distribution, expression, area, List.of());
    }

    /** This override without its expression (an expression that could not be evaluated is absent). */
    FieldOverride withoutExpression() {
        return new FieldOverride(value, range, weights, texts, missingRate, distribution, null, area, List.of());
    }

    /** This override with a fixed value and no expression or rules. */
    FieldOverride fixedTo(String fixed) {
        return new FieldOverride(fixed, null, Map.of(), List.of(), missingRate, null, null, area, List.of());
    }

    public Optional<String> value() {
        return Optional.ofNullable(value);
    }

    public Optional<FieldRange> range() {
        return Optional.ofNullable(range);
    }

    /** Choice weights in file order; empty when not set. */
    public Map<String, Double> weights() {
        return weights;
    }

    /** Texts in file order; empty when not set. */
    public List<String> texts() {
        return texts;
    }

    public OptionalDouble missingRate() {
        return missingRate == null ? OptionalDouble.empty() : OptionalDouble.of(missingRate);
    }

    public Optional<Distribution> distribution() {
        return Optional.ofNullable(distribution);
    }

    /** The "expr" key. */
    public Optional<String> expression() {
        return Optional.ofNullable(expression);
    }

    public Optional<Area> area() {
        return Optional.ofNullable(area);
    }

    /** The "when" rules, in file order; empty when not set. */
    public List<OverrideRule> rules() {
        return rules;
    }
}
