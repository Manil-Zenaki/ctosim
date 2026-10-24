package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static ctosim.SimulationConfigTestSupport.quote;

final class SimulationConfigAmendment7Support {
    static final List<String> DEFAULT_FAULTS = List.of("tooFast", "skipModules", "dontKnow");

    private SimulationConfigAmendment7Support() { }

    static ProblemRates rates(double outliers, List<String> faults) {
        return new ProblemRates(.11, .22, .33, .44, .55, .66, .77, outliers, faults);
    }

    static String json(String key, String token) {
        return "{\"problems\":{\"" + key + "\":" + token + "}}";
    }

    static String array(List<String> values) {
        return "[" + String.join(",", values.stream().map(SimulationConfigTestSupport::quote).toList()) + "]";
    }

    // All sixteen ordered subsets: empty, every singleton, every pair, every permutation.
    static Stream<List<String>> faultLists() {
        List<List<String>> result = new ArrayList<>();
        result.add(List.of());
        for (String first : DEFAULT_FAULTS) {
            result.add(List.of(first));
            for (String second : DEFAULT_FAULTS) {
                if (first.equals(second)) continue;
                result.add(List.of(first, second));
                for (String third : DEFAULT_FAULTS) {
                    if (!third.equals(first) && !third.equals(second)) result.add(List.of(first, second, third));
                }
            }
        }
        return result.stream();
    }

    static Stream<String> acceptedOutliers() {
        return Stream.of("0", "-0.0", "1", "1.0", "1e0", "0.375", "2.5e-1", "1E-12",
                "0.9999999999999999", "2.2250738585072014e-308");
    }

    static Stream<Arguments> invalidValues() {
        Stream<Arguments> outliers = Stream.of("-1", "-0.000000000001", "1.0000000000000002", "2", "1e20",
                        "null", "true", "false", "\"0.2\"", "\"\"", "[]", "[0.2]", "{}")
                .map(token -> Arguments.of(json("outliers", token), "problems.outliers"));
        Stream<Arguments> wrongArrays = Stream.of("null", "true", "false", "0", "0.2", "\"tooFast\"", "{}")
                .map(token -> Arguments.of(json("faults", token), "problems.faults"));
        Stream<Arguments> badElements = Stream.of("null", "0", "0.2", "true", "false", "[]", "{}",
                        "\"\"", "\"unknown\"", "\"TooFast\"", "\"skipmodules\"", "\"dontknow\"",
                        "\" tooFast\"", "\"tooFast \"", "\"tooFast\\t\"", "\"故障\"")
                .flatMap(token -> Stream.of(
                        Arguments.of(json("faults", "[" + token + "]"), "problems.faults[0]"),
                        Arguments.of(json("faults", "[\"dontKnow\"," + token + "]"), "problems.faults[1]")));
        Stream<Arguments> duplicates = DEFAULT_FAULTS.stream().flatMap(fault -> Stream.of(
                Arguments.of(json("faults", array(List.of(fault, fault))), "problems.faults[1]"),
                Arguments.of(json("faults", array(List.of(fault,
                        DEFAULT_FAULTS.stream().filter(other -> !other.equals(fault)).findFirst().orElseThrow(), fault))),
                        "problems.faults[2]")));
        return Stream.of(outliers, wrongArrays, badElements, duplicates,
                Stream.of(Arguments.of(json("faults", "[\"tooFast\",\"tooF\\u0061st\"]"), "problems.faults[1]")))
                .flatMap(stream -> stream);
    }

    static Stream<Arguments> unknownKeys() {
        Stream<Arguments> misspelled = Stream.of("Outliers", "outlier", "outliers ", "Faults", "fault", "faults ")
                .map(key -> Arguments.of(json(key, "0"), "problems." + key));
        Stream<Arguments> misplaced = Stream.of("outliers", "faults").flatMap(key -> {
            String member = quote(key) + ":" + (key.equals("faults") ? "[]" : "0.2");
            return Stream.of(
                    Arguments.of("{" + member + "}", key),
                    Arguments.of("{\"repeats\":{\"min\":1,\"max\":2," + member + "}}", "repeats." + key),
                    Arguments.of("{\"fields\":{\"age\":{\"value\":1," + member + "}}}", "fields.age." + key),
                    Arguments.of("{\"fields\":{\"age\":{\"when\":[{\"if\":\"1=1\",\"value\":1," + member + "}]}}}",
                            "fields.age.when[0]." + key));
        });
        return Stream.concat(misspelled, misplaced);
    }

    static Stream<String> duplicateKeys() {
        return Stream.of(
                "{\"problems\":{\"outliers\":0,\"outliers\":0}}",
                "{\"problems\":{\"outliers\":0,\"outliers\":1}}",
                "{\"problems\":{\"outliers\":0,\"outli\\u0065rs\":1}}",
                "{\"problems\":{\"faults\":[],\"faults\":[]}}",
                "{\"problems\":{\"faults\":[],\"faults\":[\"tooFast\"]}}",
                "{\"problems\":{\"faults\":[],\"fa\\u0075lts\":[\"dontKnow\"]}}");
    }
}
