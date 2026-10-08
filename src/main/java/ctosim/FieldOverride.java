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

    FieldOverride(String value, FieldRange range, Map<String, Double> weights, List<String> texts, Double missingRate) {
        this.value = value;
        this.range = range;
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
        this.texts = List.copyOf(texts);
        this.missingRate = missingRate;
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
}
