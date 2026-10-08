package ctosim;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;

/** Settings of a simulation run, read from an optional JSON file. See specs/04-simulation-config.md. */
public final class SimulationConfig {

    SimulationConfig() {
    }

    public static SimulationConfig defaults() {
        throw new UnsupportedOperationException("not implemented");
    }

    public static SimulationConfig parse(String json, String sourceName) throws ConfigException {
        throw new UnsupportedOperationException("not implemented");
    }

    public static SimulationConfig load(Path path) throws ConfigException {
        throw new UnsupportedOperationException("not implemented");
    }

    public long seed() {
        throw new UnsupportedOperationException("not implemented");
    }

    public int submissions() {
        throw new UnsupportedOperationException("not implemented");
    }

    public LocalDateTime start() {
        throw new UnsupportedOperationException("not implemented");
    }

    public int fieldworkDays() {
        throw new UnsupportedOperationException("not implemented");
    }

    public double missingRate() {
        throw new UnsupportedOperationException("not implemented");
    }

    public double specialRate() {
        throw new UnsupportedOperationException("not implemented");
    }

    public List<String> specialValues() {
        throw new UnsupportedOperationException("not implemented");
    }

    public int repeatMin() {
        throw new UnsupportedOperationException("not implemented");
    }

    public int repeatMax() {
        throw new UnsupportedOperationException("not implemented");
    }

    public NumericRange integerRange() {
        throw new UnsupportedOperationException("not implemented");
    }

    public NumericRange decimalRange() {
        throw new UnsupportedOperationException("not implemented");
    }

    public Optional<FieldOverride> field(String name) {
        throw new UnsupportedOperationException("not implemented");
    }

    public SortedSet<String> overriddenFields() {
        throw new UnsupportedOperationException("not implemented");
    }

    public SimulationConfig withSeed(long seed) {
        throw new UnsupportedOperationException("not implemented");
    }

    public SimulationConfig withSubmissions(int submissions) {
        throw new UnsupportedOperationException("not implemented");
    }
}
