package ctosim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigValidationTest {
    private static void top(List<Arguments> cases, String key, String... invalidValues) {
        for (String value : invalidValues) {
            cases.add(Arguments.of("{" + quote(key) + ":" + value + "}", key));
        }
    }

    private static void fieldCase(List<Arguments> cases, String body, String location) {
        cases.add(Arguments.of("{\"fields\":{\"age\":{" + body + "}}}", "fields.age" + location));
    }

    static Stream<Arguments> invalidTopLevelValues() {
        List<Arguments> cases = new ArrayList<>();
        top(cases, "seed", "42.5", "-0.5", "9223372036854775808", "-9223372036854775809",
                "9223372036854775808.0", "-9223372036854775809.0", "1e30", "-1e30",
                "1.0000000000000001");
        top(cases, "submissions", "0", "-1", "1000001", "1.5", "1e20", "4294967396",
                "100.0000000000000001");
        top(cases, "fieldworkDays", "0", "-1", "3651", "1.5", "1e20", "4294967326");
        top(cases, "missingRate", "-0.000001", "1.000001", "-1", "2");
        top(cases, "specialRate", "-0.000001", "1.000001", "-1", "2");
        top(cases, "start", "\"\"", "\"not-a-date\"", "\"2026-01-05\"",
                "\"2026-01-05 08:00:00\"", "\"2026-02-29T08:00\"", "\"2024-02-30T08:00\"",
                "\"2026-13-01T08:00\"", "\"2026-01-00T08:00\"", "\"2026-01-05T24:00\"",
                "\"2026-01-05T08:60\"", "\"2026-01-05T08:00:60\"",
                "\"2026-01-05T08:00Z\"", "\"2026-01-05T08:00:00+01:00\"");
        top(cases, "specialValues", "[\"\"]", "[\"ok\",\"\"]", "[\"99\",\"99\"]",
                "[\"é\",\"x\",\"é\"]", "[\"99\",\"\\u0039\\u0039\"]");
        return cases.stream();
    }

    @ParameterizedTest(name = "reject invalid {1}: {0}")
    @MethodSource("invalidTopLevelValues")
    void rejectsOutOfRangeOrInvalidTopLevelValuesWithTheirLocation(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<Arguments> wrongTypesAndNulls() {
        List<Arguments> cases = new ArrayList<>();
        for (String key : List.of("seed", "submissions", "fieldworkDays", "missingRate", "specialRate")) {
            top(cases, key, "null", "\"42\"", "true", "false", "[]", "{}");
        }
        top(cases, "start", "null", "42", "true", "[]", "{}");
        top(cases, "specialValues", "null", "\"99\"", "42", "true", "{}");
        for (String value : List.of("null", "42", "true", "[]", "{}")) {
            top(cases, "specialValues", "[\"valid\"," + value + "]");
        }
        for (String key : List.of("repeats", "integerRange", "decimalRange", "fields")) {
            top(cases, key, "null", "\"object\"", "42", "true", "[]");
        }
        for (String key : List.of("repeats", "integerRange", "decimalRange")) {
            for (String member : List.of("min", "max")) {
                for (String value : List.of("null", "\"1\"", "true", "[]", "{}")) {
                    String other = member.equals("min") ? "\"max\":10" : "\"min\":0";
                    cases.add(Arguments.of("{" + quote(key) + ":{" + quote(member) + ":"
                            + value + "," + other + "}}", key + "." + member));
                }
            }
        }
        for (String value : List.of("null", "\"override\"", "42", "true", "[]")) {
            cases.add(Arguments.of("{\"fields\":{\"age\":" + value + "}}", "fields.age"));
        }
        for (String value : List.of("null", "true", "false", "[]", "{}")) {
            fieldCase(cases, "\"value\":" + value, ".value");
        }
        for (String value : List.of("null", "\"0.5\"", "true", "[]", "{}")) {
            fieldCase(cases, "\"missingRate\":" + value, ".missingRate");
        }
        for (String member : List.of("min", "max")) {
            for (String value : List.of("null", "true", "[]", "{}")) {
                String other = member.equals("min") ? "\"max\":10" : "\"min\":0";
                fieldCase(cases, quote(member) + ":" + value + "," + other, "." + member);
            }
        }
        for (String value : List.of("null", "\"weights\"", "42", "true", "[]")) {
            fieldCase(cases, "\"weights\":" + value, ".weights");
        }
        for (String value : List.of("null", "\"1\"", "true", "[]", "{}")) {
            fieldCase(cases, "\"weights\":{\"good\":1,\"2\":" + value + "}", ".weights.2");
        }
        for (String value : List.of("null", "\"text\"", "42", "true", "{}")) {
            fieldCase(cases, "\"texts\":" + value, ".texts");
        }
        for (String value : List.of("null", "42", "true", "[]", "{}")) {
            fieldCase(cases, "\"texts\":[\"valid\"," + value + "]", ".texts");
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "wrong type or null at {1}: {0}")
    @MethodSource("wrongTypesAndNulls")
    void rejectsWrongJsonTypesAndExplicitNullAtEveryValuePosition(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<Arguments> invalidRanges() {
        List<Arguments> cases = new ArrayList<>();
        for (String key : List.of("repeats", "integerRange", "decimalRange")) {
            top(cases, key, "{}", "{\"min\":0}", "{\"max\":10}", "{\"min\":2,\"max\":1}");
        }
        for (String[] bounds : List.of(
                new String[]{"-1", "3", ".min"}, new String[]{"0", "-1", ""},
                new String[]{"0", "1001", ".max"}, new String[]{"1001", "1001", ""},
                new String[]{"0.5", "3", ".min"}, new String[]{"0", "3.5", ".max"},
                new String[]{"4294967296", "4294967297", ""},
                new String[]{"0", "4294967299", ".max"})) {
            cases.add(Arguments.of("{\"repeats\":{\"min\":" + bounds[0] + ",\"max\":"
                    + bounds[1] + "}}", "repeats" + bounds[2]));
        }
        fieldCase(cases, "\"min\":1", "");
        fieldCase(cases, "\"max\":2", "");
        fieldCase(cases, "\"min\":2,\"max\":1", "");
        fieldCase(cases, "\"min\":\"2026-02-01\",\"max\":\"2026-01-31\"", "");
        fieldCase(cases, "\"min\":1,\"max\":\"2026-01-01\"", "");
        fieldCase(cases, "\"min\":\"2026-01-01\",\"max\":1", "");
        fieldCase(cases, "\"min\":\"1\",\"max\":\"2\"", "");
        for (String badDate : List.of("", "yesterday", "2026-02-29", "2024-02-30", "2026-00-01",
                "2026-13-01", "2026-01-00", "2026-01-32", "2026-1-01", "2026-01-1",
                "2026-01-01T00:00:00")) {
            fieldCase(cases, "\"min\":" + quote(badDate) + ",\"max\":\"2030-12-31\"", ".min");
            fieldCase(cases, "\"min\":\"1900-01-01\",\"max\":" + quote(badDate), ".max");
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "invalid range at {1}: {0}")
    @MethodSource("invalidRanges")
    void rejectsMissingReversedFractionalOrMismatchedRangeBounds(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<Arguments> invalidOverrides() {
        List<Arguments> cases = new ArrayList<>();
        fieldCase(cases, "", "");
        fieldCase(cases, "\"weights\":{}", ".weights");
        fieldCase(cases, "\"weights\":{\"1\":0}", ".weights");
        fieldCase(cases, "\"weights\":{\"1\":0,\"2\":0.0}", ".weights");
        fieldCase(cases, "\"weights\":{\"1\":1,\"2\":-0.000001}", ".weights.2");
        fieldCase(cases, "\"texts\":[]", ".texts");
        fieldCase(cases, "\"missingRate\":-0.000001", ".missingRate");
        fieldCase(cases, "\"missingRate\":1.000001", ".missingRate");
        for (String other : List.of("\"min\":1,\"max\":2",
                "\"min\":\"2024-01-01\",\"max\":\"2024-12-31\"",
                "\"weights\":{\"x\":1}", "\"texts\":[\"x\"]", "\"min\":1", "\"max\":2")) {
            // Exercise both traversal orders and false-like, but present, fixed values.
            for (String value : List.of("\"x\"", "\"\"", "0")) {
                fieldCase(cases, "\"value\":" + value + "," + other, "");
                fieldCase(cases, other + ",\"value\":" + value, "");
            }
        }
        for (String name : List.of("", " ", " age", "age ", "\tage", "age\t", "\nage", "age\r")) {
            cases.add(Arguments.of("{\"fields\":{" + quote(name) + ":{\"value\":\"x\"}}}", "fields"));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "invalid override at {1}: {0}")
    @MethodSource("invalidOverrides")
    void rejectsEmptyConflictingOrInvalidFieldOverrides(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<Arguments> unknownKeys() {
        return Stream.of(
                Arguments.of("{\"submisions\":100}", "submisions"),
                Arguments.of("{\"Seed\":1}", "Seed"),
                Arguments.of("{\" seed\":1}", " seed"),
                Arguments.of("{\"repeats\":{\"min\":0,\"max\":3,\"step\":1}}", "repeats.step"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":3,\"step\":1}}", "integerRange.step"),
                Arguments.of("{\"decimalRange\":{\"min\":0,\"max\":3,\"step\":1}}", "decimalRange.step"),
                Arguments.of("{\"fields\":{\"age\":{\"value\":1,\"typo\":2}}}", "fields.age.typo"),
                Arguments.of("{\"fields\":{\"age\":{\"Value\":1}}}", "fields.age.Value"));
    }

    @ParameterizedTest(name = "unknown key {1}")
    @MethodSource("unknownKeys")
    void rejectsUnknownKeysAtEverySchemaObjectLevel(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    static Stream<Arguments> duplicateKeys() {
        return Stream.of(
                Arguments.of("{\"seed\":1,\"seed\":1}", "seed"),
                Arguments.of("{\"seed\":1,\"seed\":2}", "seed"),
                Arguments.of("{\"seed\":1,\"\\u0073eed\":2}", "seed"),
                Arguments.of("{\"repeats\":{\"min\":1,\"min\":2,\"max\":3}}", "repeats"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"max\":3}}", "repeats"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"max\":2}}", "integerRange"),
                Arguments.of("{\"decimalRange\":{\"min\":0,\"min\":1,\"max\":2}}", "decimalRange"),
                Arguments.of("{\"fields\":{\"age\":{\"value\":1},\"age\":{\"value\":2}}}", "fields"),
                Arguments.of("{\"fields\":{\"age\":{\"value\":1,\"value\":2}}}", "fields.age"),
                Arguments.of("{\"fields\":{\"age\":{\"min\":0,\"min\":1,\"max\":2}}}", "fields.age"),
                Arguments.of("{\"fields\":{\"age\":{\"texts\":[\"a\"],\"texts\":[\"b\"]}}}", "fields.age"),
                Arguments.of("{\"fields\":{\"age\":{\"missingRate\":0,\"missingRate\":1}}}", "fields.age"),
                Arguments.of("{\"fields\":{\"age\":{\"weights\":{\"2\":1,\"2\":2}}}}", "fields.age.weights"));
    }

    @ParameterizedTest(name = "duplicate key under {1}: {0}")
    @MethodSource("duplicateKeys")
    void rejectsDuplicateObjectKeysIncludingIdenticalValuesAndEscapedNames(String json, String location) {
        // Duplicate names may be detected as JSON syntax or as a schema rule; either
        // route must reject them and identify the source. See the questions file.
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[{}]", "42", "\"text\"", "true", "false", "null",
            "{} {}", "{} []", "{} 42", "{} null", "{} true", "{} trailing"})
    void requiresExactlyOneRootJsonObject(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\n", "{", "{\"seed\":}", "{seed:1}", "{'seed':1}",
            "{\"seed\":01}", "{\"seed\":+1}", "{\"seed\":NaN}", "{\"seed\":Infinity}",
            "{\"seed\":1,}", "{\"seed\":1 //comment\n}", "{/*comment*/}",
            "{\"specialValues\":[\"x\",]}", "{\"start\":\"unclosed}",
            "{\"start\":\"invalid\\q\"}", "{\"start\":\"raw\nnewline\"}"})
    void malformedJsonAlwaysUsesConfigException(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }
}
