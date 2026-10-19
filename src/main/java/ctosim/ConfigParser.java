package ctosim;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads and validates the JSON of a simulation configuration (spec 04). */
final class ConfigParser {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm[:ss]")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Set<String> TOP_KEYS = Set.of("seed", "submissions", "start", "fieldworkDays",
            "missingRate", "specialRate", "specialValues", "repeats", "integerRange", "decimalRange", "fields", "timeZone",
            "enumerators", "speedViolationRate", "profiles", "area", "textPools", "id", "interviewsPerDay");
    private static final Set<String> MIN_MAX = Set.of("min", "max");
    private static final Set<String> OVERRIDE_KEYS = Set.of("value", "min", "max", "weights", "texts", "missingRate",
            "distribution", "expr", "area", "when");
    private static final Set<String> RULE_KEYS = Set.of("if", "value", "min", "max", "weights", "texts", "missingRate",
            "distribution", "expr", "area");
    private static final Set<String> AREA_KEYS = Set.of("minLatitude", "maxLatitude", "minLongitude", "maxLongitude");
    private static final Set<String> MEAN_SD = Set.of("mean", "sd");
    private static final Set<String> POOLS = Set.of("firstName", "lastName", "place", "comment");

    private final String source;

    private ConfigParser(String source) {
        this.source = source;
    }

    static SimulationConfig parse(String json, String source) throws ConfigException {
        return new ConfigParser(source).read(json.startsWith("﻿") ? json.substring(1) : json);
    }

    private SimulationConfig read(String json) throws ConfigException {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            JsonLocation where = e.getLocation();
            String line = where == null ? "" : " at line " + where.getLineNr();
            throw new ConfigException(source + ": invalid JSON" + line + " (" + e.getOriginalMessage() + ")", e);
        }
        if (root == null || root.isMissingNode() || !root.isObject()) {
            throw new ConfigException(source + ": the configuration must be a single JSON object");
        }
        checkKeys(root, "", TOP_KEYS);

        SimulationConfig d = SimulationConfig.defaults();
        long seed = root.has("seed") ? integer(root.get("seed"), "seed").longValueExact() : d.seed();
        int submissions = root.has("submissions")
                ? boundedInt(root.get("submissions"), "submissions", 1, SimulationConfig.MAX_SUBMISSIONS) : d.submissions();
        LocalDateTime start = root.has("start") ? dateTime(root.get("start"), "start") : d.start();
        int days = root.has("fieldworkDays") ? boundedInt(root.get("fieldworkDays"), "fieldworkDays", 1, 3650) : d.fieldworkDays();
        double missing = root.has("missingRate") ? rate(root.get("missingRate"), "missingRate") : d.missingRate();
        double special = root.has("specialRate") ? rate(root.get("specialRate"), "specialRate") : d.specialRate();
        List<String> specialValues = root.has("specialValues")
                ? specialValues(root.get("specialValues")) : d.specialValues();

        int repeatMin = d.repeatMin();
        int repeatMax = d.repeatMax();
        if (root.has("repeats")) {
            JsonNode repeats = object(root.get("repeats"), "repeats");
            checkKeys(repeats, "repeats", MIN_MAX);
            requireBoth(repeats, "repeats");
            repeatMin = boundedInt(repeats.get("min"), "repeats.min", 0, 1000);
            repeatMax = boundedInt(repeats.get("max"), "repeats.max", 0, 1000);
            if (repeatMin > repeatMax) {
                throw error("repeats", "min (" + repeatMin + ") must not be greater than max (" + repeatMax + ")");
            }
        }
        NumericRange integerRange = root.has("integerRange") ? numericRange(root.get("integerRange"), "integerRange") : d.integerRange();
        NumericRange decimalRange = root.has("decimalRange") ? numericRange(root.get("decimalRange"), "decimalRange") : d.decimalRange();
        Map<String, FieldOverride> fields = root.has("fields") ? fields(root.get("fields")) : Map.of();

        ZoneId timeZone = root.has("timeZone") ? zone(root.get("timeZone")) : d.timeZone();
        int enumerators = root.has("enumerators")
                ? boundedInt(root.get("enumerators"), "enumerators", 1, SimulationConfig.MAX_ENUMERATORS) : d.enumerators();

        double speedViolations = root.has("speedViolationRate")
                ? rate(root.get("speedViolationRate"), "speedViolationRate") : d.speedViolationRate();

        Map<String, Double> profiles = root.has("profiles") ? profiles(root.get("profiles")) : Map.of();
        Area area = root.has("area") ? area(root.get("area"), "area") : null;
        Map<String, List<String>> pools = root.has("textPools") ? textPools(root.get("textPools")) : Map.of();
        String id = root.has("id") ? surveyId(root.get("id")) : null;
        int interviewsPerDay = root.has("interviewsPerDay")
                ? boundedInt(root.get("interviewsPerDay"), "interviewsPerDay", 1, SimulationConfig.MAX_INTERVIEWS_PER_DAY)
                : d.interviewsPerDay();

        return new SimulationConfig(seed, submissions, start, days, missing, special, specialValues,
                repeatMin, repeatMax, integerRange, decimalRange, fields, timeZone, enumerators, speedViolations,
                profiles, area, pools, id, interviewsPerDay);
    }

    // --- Sections -------------------------------------------------------------------

    /** The survey id question (spec 04, Amendment 4): a name without white space at its edges. */
    private String surveyId(JsonNode node) throws ConfigException {
        String name = string(node, "id");
        if (name.isEmpty()) {
            throw error("id", "must not be empty");
        }
        if (EDGE_SPACE.matcher(name).find()) {
            throw error("id", "must not start or end with white space: \"" + name + "\"");
        }
        return name;
    }

    private static final java.util.regex.Pattern EDGE_SPACE = java.util.regex.Pattern.compile("^[ \t\r\n\f]|[ \t\r\n\f]$");

    private ZoneId zone(JsonNode node) throws ConfigException {
        String id = string(node, "timeZone");
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            throw error("timeZone", "\"" + id + "\" is not a time-zone ID (" + e.getMessage() + ")");
        }
    }

    private List<String> specialValues(JsonNode node) throws ConfigException {
        if (!node.isArray()) {
            throw error("specialValues", "must be an array of strings" + found(node));
        }
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < node.size(); i++) {
            String path = "specialValues[" + i + "]";
            String value = string(node.get(i), path);
            if (value.isEmpty()) {
                throw error(path, "must not be an empty string");
            }
            if (!seen.add(value)) {
                throw error(path, "repeats the value \"" + value + "\"");
            }
            values.add(value);
        }
        return values;
    }

    private NumericRange numericRange(JsonNode node, String path) throws ConfigException {
        JsonNode range = object(node, path);
        checkKeys(range, path, MIN_MAX);
        requireBoth(range, path);
        double min = number(range.get("min"), path + ".min");
        double max = number(range.get("max"), path + ".max");
        if (min > max) {
            throw error(path, "min (" + min + ") must not be greater than max (" + max + ")");
        }
        return new NumericRange(min, max);
    }

    private Map<String, FieldOverride> fields(JsonNode node) throws ConfigException {
        JsonNode fields = object(node, "fields");
        Map<String, FieldOverride> result = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = fields.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            if (name.isEmpty()) {
                throw error("fields", "a field name must not be empty");
            }
            if (!name.equals(name.strip()) || !name.equals(name.trim())) {
                throw error("fields", "the field name \"" + name + "\" must not start or end with white space");
            }
            result.put(name, override(entry.getValue(), "fields." + name));
        }
        return result;
    }

    private FieldOverride override(JsonNode node, String path) throws ConfigException {
        return override(node, path, false);
    }

    /** An override, or a rule's settings when {@code rule} (then "if" is required and "when" not allowed). */
    private FieldOverride override(JsonNode node, String path, boolean rule) throws ConfigException {
        JsonNode o = object(node, path);
        checkKeys(o, path, rule ? RULE_KEYS : OVERRIDE_KEYS);
        if (o.size() == 0 || rule && o.size() == 1 && o.has("if")) {
            throw error(path, "must set at least one of value, expr, min/max, weights, texts, distribution, area"
                    + (rule ? "" : ", when") + " or missingRate");
        }
        boolean hasValue = o.has("value");
        if (hasValue && (o.has("min") || o.has("max") || o.has("weights") || o.has("texts") || o.has("expr")
                || o.has("distribution"))) {
            throw error(path, "value cannot be combined with expr, min/max, weights, texts or distribution");
        }
        if (o.has("expr") && (o.has("min") || o.has("max") || o.has("weights") || o.has("texts") || o.has("distribution"))) {
            throw error(path, "expr cannot be combined with value, min/max, weights, texts or distribution");
        }
        if (o.has("distribution") && (o.has("weights") || o.has("texts"))) {
            throw error(path, "distribution cannot be combined with value, weights or texts");
        }
        String value = hasValue ? fixedValue(o.get("value"), path + ".value") : null;
        FieldRange range = (o.has("min") || o.has("max")) ? fieldRange(o, path) : null;
        Map<String, Double> weights = o.has("weights") ? weights(o.get("weights"), path + ".weights") : Map.of();
        List<String> texts = o.has("texts") ? texts(o.get("texts"), path + ".texts") : List.of();
        Double missingRate = o.has("missingRate") ? rate(o.get("missingRate"), path + ".missingRate") : null;
        Distribution distribution = o.has("distribution") ? distribution(o.get("distribution"), path + ".distribution") : null;
        if (distribution != null && range instanceof DateRange) {
            throw error(path, "distribution can only be combined with numeric min/max");
        }
        String expr = o.has("expr") ? expression(o.get("expr"), path + ".expr") : null;
        Area area = o.has("area") ? area(o.get("area"), path + ".area") : null;
        List<OverrideRule> rules = new ArrayList<>();
        if (rule) {
            expression(o.get("if"), path + ".if");
        } else if (o.has("when")) {
            JsonNode when = o.get("when");
            if (!when.isArray() || when.size() == 0) {
                throw error(path + ".when", "must be a non-empty array of rules" + found(when));
            }
            for (int i = 0; i < when.size(); i++) {
                String rulePath = path + ".when[" + i + "]";
                JsonNode r = object(when.get(i), rulePath);
                if (!r.has("if")) {
                    throw error(rulePath, "must have an \"if\" condition");
                }
                rules.add(new OverrideRule(r.get("if").textValue(), override(r, rulePath, true)));
            }
        }
        return new FieldOverride(value, range, weights, texts, missingRate, distribution, expr, area, rules);
    }

    private Distribution distribution(JsonNode node, String path) throws ConfigException {
        JsonNode o = object(node, path);
        checkKeys(o, path, Set.of("normal", "lognormal"));
        if (o.size() != 1) {
            throw error(path, "must have exactly one of normal or lognormal");
        }
        String kind = o.has("normal") ? "normal" : "lognormal";
        String p = path + "." + kind;
        JsonNode params = object(o.get(kind), p);
        checkKeys(params, p, MEAN_SD);
        if (!params.has("mean") || !params.has("sd")) {
            throw error(p, "must have both mean and sd");
        }
        double mean = number(params.get("mean"), p + ".mean");
        double sd = number(params.get("sd"), p + ".sd");
        if (sd < 0) {
            throw error(p + ".sd", "must not be negative (" + sd + ")");
        }
        if (kind.equals("lognormal") && !(mean > 0)) {
            throw error(p + ".mean", "must be greater than 0 for a lognormal distribution (" + mean + ")");
        }
        return new Distribution(kind, mean, sd);
    }

    private Area area(JsonNode node, String path) throws ConfigException {
        JsonNode o = object(node, path);
        checkKeys(o, path, AREA_KEYS);
        for (String key : List.of("minLatitude", "maxLatitude", "minLongitude", "maxLongitude")) {
            if (!o.has(key)) {
                throw error(path, "must have " + key);
            }
        }
        double minLat = coordinate(o.get("minLatitude"), path + ".minLatitude", 90);
        double maxLat = coordinate(o.get("maxLatitude"), path + ".maxLatitude", 90);
        double minLon = coordinate(o.get("minLongitude"), path + ".minLongitude", 180);
        double maxLon = coordinate(o.get("maxLongitude"), path + ".maxLongitude", 180);
        if (minLat > maxLat) {
            throw error(path, "minLatitude (" + minLat + ") must not be greater than maxLatitude (" + maxLat + ")");
        }
        if (minLon > maxLon) {
            throw error(path, "minLongitude (" + minLon + ") must not be greater than maxLongitude (" + maxLon + ")");
        }
        return new Area(minLat, maxLat, minLon, maxLon);
    }

    private double coordinate(JsonNode node, String path, double limit) throws ConfigException {
        double value = number(node, path);
        if (!(value >= -limit && value <= limit)) {
            throw error(path, "must be between " + (int) -limit + " and " + (int) limit + ", not " + value);
        }
        return value;
    }

    private Map<String, Double> profiles(JsonNode node) throws ConfigException {
        JsonNode o = object(node, "profiles");
        if (o.size() == 0) {
            throw error("profiles", "must list at least one profile");
        }
        Map<String, Double> profiles = new LinkedHashMap<>();
        boolean positive = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            if (name.isEmpty() || !name.equals(name.strip())) {
                throw error("profiles", "the profile name \"" + name + "\" must be non-empty, without leading or trailing white space");
            }
            double weight = number(entry.getValue(), "profiles." + name);
            if (weight < 0) {
                throw error("profiles." + name, "a weight must not be negative (" + weight + ")");
            }
            positive |= weight > 0;
            profiles.put(name, weight);
        }
        if (!positive) {
            throw error("profiles", "at least one weight must be greater than 0");
        }
        return profiles;
    }

    private Map<String, List<String>> textPools(JsonNode node) throws ConfigException {
        JsonNode o = object(node, "textPools");
        checkKeys(o, "textPools", POOLS);
        Map<String, List<String>> pools = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String path = "textPools." + entry.getKey();
            List<String> texts = texts(entry.getValue(), path);
            for (int i = 0; i < texts.size(); i++) {
                if (texts.get(i).isEmpty()) {
                    throw error(path + "[" + i + "]", "must not be an empty string");
                }
            }
            pools.put(entry.getKey(), texts);
        }
        return pools;
    }

    /** An XPath expression of the configuration: a non-empty string that parses once ${x} are paths. */
    private String expression(JsonNode node, String path) throws ConfigException {
        String text = string(node, path);
        if (text.isBlank()) {
            throw error(path, "must not be empty");
        }
        try {
            org.javarosa.xpath.XPathParseTool.parseXPath(text.replaceAll("\\$\\{([^}]*)\\}", "/data/$1"));
        } catch (org.javarosa.xpath.parser.XPathSyntaxException | RuntimeException e) {
            throw error(path, "is not a valid expression (" + e.getMessage() + ")");
        }
        return text;
    }

    private FieldRange fieldRange(JsonNode o, String path) throws ConfigException {
        requireBoth(o, path);
        JsonNode min = o.get("min");
        JsonNode max = o.get("max");
        for (String bound : List.of("min", "max")) {
            JsonNode b = o.get(bound);
            if (!b.isNumber() && !b.isTextual()) {
                throw error(path + "." + bound, "must be a number or a date (YYYY-MM-DD)" + found(b));
            }
        }
        if (min.isNumber() && max.isNumber()) {
            double lo = number(min, path + ".min");
            double hi = number(max, path + ".max");
            if (lo > hi) {
                throw error(path, "min (" + lo + ") must not be greater than max (" + hi + ")");
            }
            return new NumericRange(lo, hi);
        }
        if (min.isTextual() && max.isTextual()) {
            LocalDate lo = date(min, path + ".min");
            LocalDate hi = date(max, path + ".max");
            if (lo.isAfter(hi)) {
                throw error(path, "min (" + lo + ") must not be after max (" + hi + ")");
            }
            return new DateRange(lo, hi);
        }
        throw error(path, "min and max must both be numbers or both be dates (YYYY-MM-DD)");
    }

    private Map<String, Double> weights(JsonNode node, String path) throws ConfigException {
        JsonNode o = object(node, path);
        if (o.size() == 0) {
            throw error(path, "must list at least one choice");
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        boolean positive = false;
        for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String entryPath = path + "." + entry.getKey();
            double weight = number(entry.getValue(), entryPath);
            if (weight < 0) {
                throw error(entryPath, "a weight must not be negative (" + weight + ")");
            }
            positive |= weight > 0;
            weights.put(entry.getKey(), weight);
        }
        if (!positive) {
            throw error(path, "at least one weight must be greater than 0");
        }
        return weights;
    }

    private List<String> texts(JsonNode node, String path) throws ConfigException {
        if (!node.isArray()) {
            throw error(path, "must be an array of strings" + found(node));
        }
        if (node.size() == 0) {
            throw error(path, "must contain at least one text");
        }
        List<String> texts = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            texts.add(string(node.get(i), path + "[" + i + "]"));
        }
        return texts;
    }

    // --- Values ---------------------------------------------------------------------

    private String fixedValue(JsonNode node, String path) throws ConfigException {
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            BigDecimal exact = node.decimalValue();
            if (isWhole(exact)) {
                return exact.toBigIntegerExact().toString();
            }
            return Double.toString(exact.doubleValue());
        }
        throw error(path, "must be a string or a number" + found(node));
    }

    private BigDecimal integer(JsonNode node, String path) throws ConfigException {
        if (node == null || !node.isNumber()) {
            throw error(path, "must be an integer" + found(node));
        }
        // Jackson reads a number too large for a BigDecimal-free parse (1e400) as an infinite double.
        if (node.isDouble() && !Double.isFinite(node.doubleValue())) {
            throw error(path, "is out of range (" + node.asText() + ")");
        }
        BigDecimal exact = node.decimalValue();
        if (!isWhole(exact)) {
            throw error(path, "must be an integer, not " + exact.toPlainString());
        }
        try {
            BigDecimal whole = exact.setScale(0);
            whole.longValueExact();
            return whole;
        } catch (ArithmeticException e) {
            throw error(path, "is out of range (" + exact.toPlainString() + ")");
        }
    }

    private int boundedInt(JsonNode node, String path, int min, int max) throws ConfigException {
        BigDecimal value = integer(node, path);
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw error(path, "must be between " + min + " and " + max + ", not " + value.toPlainString());
        }
        return value.intValueExact();
    }

    private double number(JsonNode node, String path) throws ConfigException {
        if (node == null || !node.isNumber()) {
            throw error(path, "must be a number" + found(node));
        }
        return node.decimalValue().doubleValue();
    }

    private double rate(JsonNode node, String path) throws ConfigException {
        double rate = number(node, path);
        if (!(rate >= 0 && rate <= 1)) {
            throw error(path, "must be between 0 and 1, not " + rate);
        }
        return rate;
    }

    private String string(JsonNode node, String path) throws ConfigException {
        if (node == null || !node.isTextual()) {
            throw error(path, "must be a string" + found(node));
        }
        return node.textValue();
    }

    private LocalDateTime dateTime(JsonNode node, String path) throws ConfigException {
        String text = string(node, path);
        try {
            return LocalDateTime.parse(text, START);
        } catch (DateTimeParseException e) {
            throw error(path, "must be a local date-time YYYY-MM-DDTHH:MM[:SS], not \"" + text + "\"");
        }
    }

    private LocalDate date(JsonNode node, String path) throws ConfigException {
        String text = string(node, path);
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException e) {
            throw error(path, "must be a date YYYY-MM-DD, not \"" + text + "\"");
        }
    }

    private JsonNode object(JsonNode node, String path) throws ConfigException {
        if (node == null || !node.isObject()) {
            throw error(path, "must be a JSON object" + found(node));
        }
        return node;
    }

    // --- Helpers --------------------------------------------------------------------

    private void checkKeys(JsonNode object, String path, Set<String> allowed) throws ConfigException {
        for (Iterator<String> it = object.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (!allowed.contains(key)) {
                throw error(path.isEmpty() ? key : path + "." + key, "is not a known setting");
            }
        }
    }

    private void requireBoth(JsonNode object, String path) throws ConfigException {
        for (String key : List.of("min", "max")) {
            if (!object.has(key)) {
                throw error(path + "." + key, "is missing (min and max must be given together)");
            }
        }
    }

    private static boolean isWhole(BigDecimal value) {
        return value.signum() == 0 || value.stripTrailingZeros().scale() <= 0;
    }

    private static String found(JsonNode node) {
        if (node == null) {
            return "";
        }
        return node.isNull() ? ", not null" : ", not " + node.getNodeType().toString().toLowerCase();
    }

    private ConfigException error(String path, String reason) {
        return new ConfigException(source + ": " + path + " " + reason);
    }
}
