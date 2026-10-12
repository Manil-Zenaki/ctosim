package ctosim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static ctosim.SimulationConfigRealisticSupport.*;
import static ctosim.SimulationConfigTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigRealisticValidationTest {
    private static void add(List<Arguments> cases, String json, String path) {
        cases.add(Arguments.of(json, path));
    }

    private static void overrideCase(List<Arguments> cases, String members, String suffix) {
        add(cases, fieldJson(members), "fields.answer" + suffix);
        add(cases, ruleJson(members), "fields.answer.when[0]" + suffix);
    }

    static Stream<Arguments> invalidProfilesAndPools() {
        List<Arguments> cases = new ArrayList<>();
        for (String wrong : List.of("null", "[]", "true", "1", "\"x\"")) {
            add(cases, "{\"profiles\":" + wrong + "}", "profiles");
            add(cases, "{\"textPools\":" + wrong + "}", "textPools");
        }
        for (String profiles : List.of("{}", "{\"rural\":0}", "{\"rural\":0,\"urban\":0}")) {
            add(cases, "{\"profiles\":" + profiles + "}", "profiles");
        }
        for (String name : List.of("", " rural", "rural ", "\trural", "rural\n", "\rrural")) {
            add(cases, "{\"profiles\":{" + quote(name) + ":1}}", "profiles");
        }
        for (String weight : List.of("-0.000001", "-1", "null", "\"2\"", "true", "[]", "{}")) {
            add(cases, "{\"profiles\":{\"urban\":2,\"rural\":" + weight + "}}", "profiles.rural");
        }
        for (String name : List.of("", "firstname", "FirstName", "lastNames", "village", "place ", " comment")) {
            add(cases, "{\"textPools\":{" + quote(name) + ":[\"X\"]}}", "textPools" + (name.isEmpty() ? "" : "." + name));
        }
        for (String pool : List.of("firstName", "lastName", "place", "comment")) {
            for (String wrong : List.of("[]", "null", "\"X\"", "1", "false", "{}")) {
                add(cases, "{\"textPools\":{\"" + pool + "\":" + wrong + "}}", "textPools." + pool);
            }
            for (String entry : List.of("\"\"", "null", "1", "true", "[]", "{}")) {
                add(cases, "{\"textPools\":{\"" + pool + "\":[\"A\",\"B\"," + entry + "]}}", "textPools." + pool + "[2]");
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> invalidAreas() {
        List<Arguments> cases = new ArrayList<>();
        List<String> keys = List.of("minLatitude", "maxLatitude", "minLongitude", "maxLongitude");
        for (String wrong : List.of("null", "[]", "1", "false", "\"area\"", "{}")) {
            areaCase(cases, wrong, "");
        }
        for (String key : keys) {
            for (String wrong : List.of("null", "\"0\"", "true", "[]", "{}")) {
                String area = areaWith(key, wrong, false);
                areaCase(cases, area, "." + key);
            }
            areaCase(cases, areaWith(key, "", true), "");
            // Every individual coordinate has its own domain, regardless of ordering.
            double limit = key.endsWith("Latitude") ? 90 : 180;
            for (double outside : new double[]{-limit - 0.000001, limit + 0.000001}) {
                // A containing path is allowed here: the same input also reverses some bounds.
                areaCase(cases, areaWith(key, Double.toString(outside), false), "");
            }
        }
        areaCase(cases, "{\"minLatitude\":2,\"maxLatitude\":1,\"minLongitude\":0,\"maxLongitude\":0}", "");
        areaCase(cases, "{\"minLatitude\":0,\"maxLatitude\":0,\"minLongitude\":170,\"maxLongitude\":-170}", "");
        // These violate only one coordinate's domain, so require its full location.
        areaCase(cases, areaWith("minLatitude", "-90.000001", false), ".minLatitude");
        areaCase(cases, areaWith("maxLatitude", "90.000001", false), ".maxLatitude");
        areaCase(cases, areaWith("minLongitude", "-180.000001", false), ".minLongitude");
        areaCase(cases, areaWith("maxLongitude", "180.000001", false), ".maxLongitude");
        areaCase(cases, AREA.substring(0, AREA.length() - 1) + ",\"altitude\":100}", ".altitude");
        return cases.stream();
    }

    private static String areaWith(String changed, String value, boolean omit) {
        List<String> members = new ArrayList<>();
        for (String key : List.of("minLatitude", "maxLatitude", "minLongitude", "maxLongitude")) {
            if (omit && key.equals(changed)) continue;
            members.add(quote(key) + ":" + (key.equals(changed) ? value : "0"));
        }
        return "{" + String.join(",", members) + "}";
    }

    private static void areaCase(List<Arguments> cases, String area, String suffix) {
        add(cases, "{\"area\":" + area + "}", "area" + suffix);
        overrideCase(cases, "\"area\":" + area, ".area" + suffix);
    }

    static Stream<Arguments> invalidDistributionsAndExpressions() {
        List<Arguments> cases = new ArrayList<>();
        for (String wrong : List.of("null", "[]", "1", "true", "\"normal\"", "{}")) {
            overrideCase(cases, "\"distribution\":" + wrong, ".distribution");
        }
        for (String kind : List.of("uniform", "Normal", "LOGNORMAL", "normal ")) {
            overrideCase(cases, "\"distribution\":{" + quote(kind) + ":{\"mean\":1,\"sd\":1}}", ".distribution");
        }
        overrideCase(cases, "\"distribution\":{\"normal\":{\"mean\":1,\"sd\":2},\"lognormal\":{\"mean\":1,\"sd\":2}}", ".distribution");
        overrideCase(cases, "\"distribution\":{\"normal\":{\"mean\":1,\"sd\":2},\"typo\":{}}", ".distribution");
        for (String kind : List.of("normal", "lognormal")) {
            for (String wrong : List.of("null", "[]", "1", "false", "\"x\"", "{}", "{\"mean\":1}", "{\"sd\":1}")) {
                overrideCase(cases, "\"distribution\":{\"" + kind + "\":" + wrong + "}", ".distribution." + kind);
            }
            for (String key : List.of("mean", "sd")) {
                for (String wrong : List.of("null", "\"1\"", "true", "[]", "{}")) {
                    String parameters = key.equals("mean") ? "\"mean\":" + wrong + ",\"sd\":1" : "\"mean\":1,\"sd\":" + wrong;
                    overrideCase(cases, "\"distribution\":{\"" + kind + "\":{" + parameters + "}}", ".distribution." + kind + "." + key);
                }
            }
            for (String sd : List.of("-1", "-0.000001")) {
                overrideCase(cases, "\"distribution\":{\"" + kind + "\":{\"mean\":1,\"sd\":" + sd + "}}", ".distribution." + kind + ".sd");
            }
            overrideCase(cases, "\"distribution\":{\"" + kind + "\":{\"mean\":1,\"sd\":1,\"variance\":2}}", ".distribution." + kind + ".variance");
        }
        for (String mean : List.of("0", "-1", "-0.000001")) {
            overrideCase(cases, "\"distribution\":{\"lognormal\":{\"mean\":" + mean + ",\"sd\":1}}", ".distribution.lognormal.mean");
        }
        for (String wrong : List.of("null", "1", "true", "[]", "{}")) {
            overrideCase(cases, "\"expr\":" + wrong, ".expr");
        }
        return cases.stream();
    }

    static Stream<Arguments> incompatibleOverrideSettings() {
        List<Arguments> cases = new ArrayList<>();
        List<String> exprConflicts = List.of("\"value\":1", "\"min\":0,\"max\":1",
                "\"min\":\"2020-01-01\",\"max\":\"2021-01-01\"", "\"weights\":{\"x\":1}",
                "\"texts\":[\"X\"]", "\"distribution\":" + NORMAL);
        for (String conflict : exprConflicts) {
            overrideCase(cases, "\"expr\":\"1 + 2\"," + conflict, "");
            overrideCase(cases, conflict + ",\"expr\":\"1 + 2\"", "");
        }
        for (String kind : List.of("normal", "lognormal")) {
            for (String conflict : List.of("\"value\":1", "\"weights\":{\"x\":1}", "\"texts\":[\"X\"]",
                    "\"min\":\"2020-01-01\",\"max\":\"2021-01-01\"")) {
                String distribution = "\"distribution\":{\"" + kind + "\":{\"mean\":1,\"sd\":0}}";
                overrideCase(cases, distribution + "," + conflict, "");
                overrideCase(cases, conflict + "," + distribution, "");
            }
        }
        for (String bounds : List.of("\"min\":0", "\"max\":1", "\"min\":2,\"max\":1",
                "\"min\":0,\"max\":\"2020-01-01\"")) {
            overrideCase(cases, "\"distribution\":" + NORMAL + "," + bounds, "");
        }
        return cases.stream();
    }

    static Stream<Arguments> invalidRules() {
        List<Arguments> cases = new ArrayList<>();
        for (String wrong : List.of("null", "1", "true", "\"rule\"", "{}")) {
            add(cases, fieldJson("\"when\":" + wrong), "fields.answer.when");
        }
        for (String wrong : List.of("null", "1", "true", "\"rule\"", "[]", "{}", "{\"value\":1}", "{\"if\":\"1 = 1\"}")) {
            add(cases, fieldJson("\"when\":[{\"if\":\"1 = 1\",\"value\":1}," + wrong + "]"), "fields.answer.when[1]");
        }
        for (String wrong : List.of("null", "1", "true", "[]", "{}", "\"\"")) {
            add(cases, fieldJson("\"when\":[{\"if\":" + wrong + ",\"value\":1}]"), "fields.answer.when[0].if");
        }
        add(cases, ruleJson("\"value\":1,\"when\":[{\"if\":\"1 = 1\",\"value\":2}]"), "fields.answer.when[0].when");
        add(cases, ruleJson("\"value\":1,\"when\":[]"), "fields.answer.when[0].when");
        add(cases, ruleJson("\"value\":1,\"typo\":2"), "fields.answer.when[0].typo");
        // Older override validation also applies inside new rules.
        for (String members : List.of("\"value\":null", "\"value\":true", "\"value\":[]", "\"value\":{}",
                "\"min\":1", "\"max\":2", "\"min\":2,\"max\":1", "\"min\":null,\"max\":1",
                "\"min\":true,\"max\":1", "\"min\":\"2020-02-30\",\"max\":\"2021-01-01\"",
                "\"min\":\"2022-01-01\",\"max\":\"2021-01-01\"", "\"min\":0,\"max\":\"2021-01-01\"",
                "\"weights\":{}", "\"weights\":{\"a\":0}", "\"weights\":{\"a\":-1,\"b\":2}",
                "\"weights\":{\"a\":null}", "\"weights\":{\"a\":\"1\"}", "\"weights\":[]", "\"weights\":null",
                "\"texts\":[]", "\"texts\":null", "\"texts\":\"x\"", "\"texts\":[1]", "\"texts\":[null]",
                "\"missingRate\":-0.01", "\"missingRate\":1.01", "\"missingRate\":null", "\"missingRate\":\"0.5\"",
                "\"value\":1,\"min\":0,\"max\":2", "\"value\":1,\"weights\":{\"a\":1}", "\"value\":1,\"texts\":[\"x\"]")) {
            add(cases, ruleJson(members), "fields.answer.when[0]");
        }
        // A false condition does not exempt its body from configuration validation.
        add(cases, fieldJson("\"when\":[{\"if\":\"1 = 0\",\"distribution\":{\"lognormal\":{\"mean\":0,\"sd\":1}}}]"),
                "fields.answer.when[0].distribution.lognormal.mean");
        return cases.stream();
    }

    static Stream<Arguments> unknownKeys() {
        List<Arguments> cases = new ArrayList<>();
        for (String name : List.of("Profiles", "profile", "Area", "textpools", "textPool", "distribution", "expr", "when", "if")) {
            add(cases, "{" + quote(name) + ":{}}", name);
        }
        for (String name : List.of("profiles", "textPools", "Distribution", "Expr", "When")) {
            overrideCase(cases, "\"value\":1," + quote(name) + ":{}", "." + name);
        }
        // 'if' is valid inside a rule, unlike the containing field override.
        add(cases, fieldJson("\"value\":1,\"if\":\"1 = 1\""), "fields.answer.if");
        for (String name : List.of("profiles", "textPools", "area", "distribution", "expr", "when")) {
            add(cases, "{\"integerRange\":{\"min\":0,\"max\":1," + quote(name) + ":{}}}", "integerRange." + name);
            add(cases, "{\"repeats\":{\"min\":0,\"max\":1," + quote(name) + ":{}}}", "repeats." + name);
        }
        return cases.stream();
    }

    static Stream<Arguments> allInvalidValues() {
        return Stream.of(invalidProfilesAndPools(), invalidAreas(), invalidDistributionsAndExpressions(),
                incompatibleOverrideSettings(), invalidRules(), unknownKeys()).flatMap(s -> s);
    }

    @ParameterizedTest(name = "invalid value at {1}: {0}")
    @MethodSource("allInvalidValues")
    void parseRejectsInvalidValuesWithSourceLocationAndExplanation(String json, String path) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, path);
    }

    static Stream<String> duplicateKeys() {
        List<String> cases = new ArrayList<>();
        for (String members : List.of("\"profiles\":{\"rural\":1},\"profiles\":{\"rural\":1}",
                "\"area\":" + AREA + ",\"area\":" + AREA,
                "\"textPools\":{\"place\":[\"A\"]},\"textPools\":{\"place\":[\"B\"]}",
                "\"profiles\":{\"rural\":1,\"rural\":2}", "\"profiles\":{\"rural\":1,\"r\\u0075ral\":2}",
                "\"textPools\":{\"place\":[\"A\"],\"pla\\u0063e\":[\"B\"]}")) {
            cases.add("{" + members + "}");
        }
        for (String members : List.of("\"expr\":\"1\",\"expr\":\"2\"", "\"expr\":\"1\",\"e\\u0078pr\":\"1\"",
                "\"distribution\":" + NORMAL + ",\"distribution\":" + NORMAL,
                "\"distribution\":{\"normal\":{\"mean\":1,\"sd\":0},\"normal\":{\"mean\":2,\"sd\":1}}",
                "\"distribution\":{\"normal\":{\"mean\":1,\"mean\":2,\"sd\":0}}",
                "\"distribution\":{\"lognormal\":{\"mean\":1,\"sd\":0,\"sd\":1}}",
                "\"area\":" + AREA + ",\"area\":" + AREA,
                "\"area\":{\"minLatitude\":0,\"minLatitude\":0,\"maxLatitude\":0,\"minLongitude\":0,\"maxLongitude\":0}")) {
            cases.add(fieldJson(members));
            cases.add(ruleJson(members));
        }
        cases.add(fieldJson("\"when\":[{\"if\":\"1\",\"value\":1}],\"when\":[{\"if\":\"1\",\"value\":2}]"));
        cases.add(fieldJson("\"when\":[{\"if\":\"1\",\"if\":\"2\",\"value\":1}]"));
        cases.add(ruleJson("\"value\":1,\"value\":2"));
        cases.add("{\"area\":{\"minLatitude\":0,\"maxLatitude\":0,\"minLongitude\":0,\"maxLongitude\":0,\"maxLongitude\":0}}");
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("duplicateKeys")
    void parseRejectsDuplicateKeysAtEveryNewObjectLevel(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }
}
