package ctosim;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/** Settings of a simulation run, read from an optional JSON file. See specs/04-simulation-config.md. */
public final class SimulationConfig {

    static final int MAX_SUBMISSIONS = 1_000_000;
    static final int MAX_ENUMERATORS = 10_000;

    static final List<String> DEFAULT_SPECIAL_VALUES = List.of(
            "-99", "-98", "-97", "-96", "-88", "-77", "-999", "-888", "-777", "97", "98", "99", "999");

    private final long seed;
    private final int submissions;
    private final LocalDateTime start;
    private final int fieldworkDays;
    private final double missingRate;
    private final double specialRate;
    private final List<String> specialValues;
    private final int repeatMin;
    private final int repeatMax;
    private final NumericRange integerRange;
    private final NumericRange decimalRange;
    private final SortedSet<String> overriddenFields;
    private final Map<String, FieldOverride> fields;
    private final ZoneId timeZone;
    private final int enumerators;
    private final double speedViolationRate;
    private final Map<String, Double> profiles;
    private final Area area;
    private final Map<String, List<String>> textPools;

    SimulationConfig(long seed, int submissions, LocalDateTime start, int fieldworkDays, double missingRate,
                     double specialRate, List<String> specialValues, int repeatMin, int repeatMax,
                     NumericRange integerRange, NumericRange decimalRange, Map<String, FieldOverride> fields,
                     ZoneId timeZone, int enumerators, double speedViolationRate) {
        this(seed, submissions, start, fieldworkDays, missingRate, specialRate, specialValues, repeatMin, repeatMax,
                integerRange, decimalRange, fields, timeZone, enumerators, speedViolationRate, Map.of(), null, Map.of());
    }

    SimulationConfig(long seed, int submissions, LocalDateTime start, int fieldworkDays, double missingRate,
                     double specialRate, List<String> specialValues, int repeatMin, int repeatMax,
                     NumericRange integerRange, NumericRange decimalRange, Map<String, FieldOverride> fields,
                     ZoneId timeZone, int enumerators, double speedViolationRate,
                     Map<String, Double> profiles, Area area, Map<String, List<String>> textPools) {
        this.profiles = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(profiles));
        this.area = area;
        java.util.Map<String, List<String>> pools = new java.util.LinkedHashMap<>();
        textPools.forEach((k, v) -> pools.put(k, List.copyOf(v)));
        this.textPools = Collections.unmodifiableMap(pools);
        this.seed = seed;
        this.submissions = submissions;
        this.start = start;
        this.fieldworkDays = fieldworkDays;
        this.missingRate = missingRate;
        this.specialRate = specialRate;
        this.specialValues = List.copyOf(specialValues);
        this.repeatMin = repeatMin;
        this.repeatMax = repeatMax;
        this.integerRange = integerRange;
        this.decimalRange = decimalRange;
        this.fields = Collections.unmodifiableMap(new TreeMap<>(fields));
        this.overriddenFields = Collections.unmodifiableSortedSet(new TreeSet<>(fields.keySet()));
        this.timeZone = timeZone;
        this.enumerators = enumerators;
        this.speedViolationRate = speedViolationRate;
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(1, 100, LocalDateTime.of(2026, 1, 5, 8, 0, 0), 30, 0.05, 0.02,
                DEFAULT_SPECIAL_VALUES, 1, 3, new NumericRange(0, 100), new NumericRange(0, 1000), Map.of(),
                ZoneId.of("UTC"), 5, 0.1);
    }

    public static SimulationConfig parse(String json, String sourceName) throws ConfigException {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(sourceName, "sourceName");
        return ConfigParser.parse(json, sourceName);
    }

    public static SimulationConfig load(Path path) throws ConfigException {
        Objects.requireNonNull(path, "path");
        String source = path.toString();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ConfigException(source + ": the file cannot be read (" + e + ")", e);
        }
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ConfigException(source + ": the file is not valid UTF-8 text", e);
        }
        return ConfigParser.parse(json, source);
    }

    public long seed() {
        return seed;
    }

    public int submissions() {
        return submissions;
    }

    public LocalDateTime start() {
        return start;
    }

    public int fieldworkDays() {
        return fieldworkDays;
    }

    public double missingRate() {
        return missingRate;
    }

    public double specialRate() {
        return specialRate;
    }

    public List<String> specialValues() {
        return specialValues;
    }

    public int repeatMin() {
        return repeatMin;
    }

    public int repeatMax() {
        return repeatMax;
    }

    public NumericRange integerRange() {
        return integerRange;
    }

    public NumericRange decimalRange() {
        return decimalRange;
    }

    public Optional<FieldOverride> field(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public SortedSet<String> overriddenFields() {
        return overriddenFields;
    }

    /** The simulated devices' time zone. See specs/04-simulation-config.md, Amendment 1. */
    public ZoneId timeZone() {
        return timeZone;
    }

    /** The number of simulated enumerators. See specs/04-simulation-config.md, Amendment 1. */
    public int enumerators() {
        return enumerators;
    }

    /** Probability of a speed violation on a field with a minimum time. See specs/04, Amendment 2. */
    /** Profile weights in file order; empty when not set (spec 04, Amendment 3). */
    public java.util.Map<String, Double> profiles() {
        return profiles;
    }

    public java.util.Optional<Area> area() {
        return java.util.Optional.ofNullable(area);
    }

    /** The question holding the survey id. See specs/04-simulation-config.md, Amendment 4. */
    public java.util.Optional<String> id() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Text pools in file order; empty when not set. */
    public java.util.Map<String, java.util.List<String>> textPools() {
        return textPools;
    }

    public double speedViolationRate() {
        return speedViolationRate;
    }

    /** This configuration with {@code override} for a field it does not name (spec 10, Amendment 2). */
    SimulationConfig withFieldIfAbsent(String name, FieldOverride override) {
        if (fields.containsKey(name)) {
            return this;
        }
        Map<String, FieldOverride> more = new java.util.LinkedHashMap<>(fields);
        more.put(name, override);
        return new SimulationConfig(seed, submissions, start, fieldworkDays, missingRate, specialRate,
                specialValues, repeatMin, repeatMax, integerRange, decimalRange, more,
                timeZone, enumerators, speedViolationRate, profiles, area, textPools);
    }

    public SimulationConfig withSeed(long seed) {
        return new SimulationConfig(seed, submissions, start, fieldworkDays, missingRate, specialRate,
                specialValues, repeatMin, repeatMax, integerRange, decimalRange, fields,
                timeZone, enumerators, speedViolationRate, profiles, area, textPools);
    }

    public SimulationConfig withSubmissions(int submissions) {
        if (submissions < 1 || submissions > MAX_SUBMISSIONS) {
            throw new IllegalArgumentException("submissions must be between 1 and " + MAX_SUBMISSIONS + ": " + submissions);
        }
        return new SimulationConfig(seed, submissions, start, fieldworkDays, missingRate, specialRate,
                specialValues, repeatMin, repeatMax, integerRange, decimalRange, fields,
                timeZone, enumerators, speedViolationRate, profiles, area, textPools);
    }
}
