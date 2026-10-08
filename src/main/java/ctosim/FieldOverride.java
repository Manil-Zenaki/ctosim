package ctosim;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/** How one field is answered, from the "fields" section of a configuration. See specs/04-simulation-config.md. */
public final class FieldOverride {

    FieldOverride() {
    }

    public Optional<String> value() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Optional<FieldRange> range() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Map<String, Double> weights() {
        throw new UnsupportedOperationException("not implemented");
    }

    public List<String> texts() {
        throw new UnsupportedOperationException("not implemented");
    }

    public OptionalDouble missingRate() {
        throw new UnsupportedOperationException("not implemented");
    }
}
