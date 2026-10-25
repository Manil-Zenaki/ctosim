package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

final class SimulationConfigAmendment8Support {
    static final List<String> DEFAULT_FAULTS = List.of("tooFast", "skipModules", "dontKnow", "fakeRefusal");

    private SimulationConfigAmendment8Support() { }

    static String quote(String text) {
        return SimulationConfigTestSupport.quote(text).replace("\f", "\\f").replace("\b", "\\b");
    }

    static String array(List<String> values) {
        return "[" + String.join(",", values.stream().map(SimulationConfigAmendment8Support::quote).toList()) + "]";
    }

    static String consentJson(String members) {
        return "{\"consent\":{" + members + "}}";
    }

    static Stream<String> acceptedRates() {
        return Stream.of("0", "-0.0", "1", "1.0", "1e0", "0.375", "9E-1",
                "1e-12", "0.9999999999999999", "2.2250738585072014e-308");
    }

    static Stream<List<String>> acceptedFields() {
        return Stream.of(List.of("consent"), List.of("z_last", "a_first", "middle"),
                List.of("Consent", "consent"), List.of("同意", "ménage_é", "field with space", "a.b", "a/b"),
                List.of("fields", "rate", "yes", "no", "fakeRefusal"));
    }

    static Stream<String> invalidNames() {
        return Stream.of("", " ", " name", "name ", "\tname", "name\t", "\rname", "name\r",
                "\nname", "name\n", "\fname", "name\f");
    }

    static Stream<Arguments> invalidConsent() {
        List<Arguments> cases = new ArrayList<>();
        for (String token : List.of("null", "true", "false", "0", "0.9", "\"x\"", "[]", "[{}]")) {
            cases.add(Arguments.of("{\"consent\":" + token + "}", "consent"));
        }
        // Missing-key and cross-member diagnostics accept the containing object.
        cases.add(Arguments.of(consentJson(""), "consent"));
        cases.add(Arguments.of(consentJson("\"rate\":0.5,\"yes\":\"Y\",\"no\":\"N\""), "consent"));
        for (String token : List.of("null", "true", "false", "0", "\"name\"", "{}", "[]")) {
            cases.add(Arguments.of(consentJson("\"fields\":" + token), "consent.fields"));
        }
        Stream.concat(Stream.of("null", "true", "false", "1", "0.5", "[]", "{}"),
                invalidNames().map(SimulationConfigAmendment8Support::quote)).forEach(token ->
                cases.add(Arguments.of(consentJson("\"fields\":[\"ok\"," + token + "]"), "consent.fields[1]")));
        cases.add(Arguments.of(consentJson("\"fields\":[\"same\",\"same\"]"), "consent.fields[1]"));
        cases.add(Arguments.of(consentJson("\"fields\":[\"same\",\"other\",\"s\\u0061me\"]"), "consent.fields[2]"));
        for (String token : List.of("-1", "-1e-12", "1.0000000000000002", "2", "1e20",
                "null", "true", "false", "\"0.9\"", "\"\"", "[]", "{}")) {
            cases.add(Arguments.of(consentJson("\"fields\":[\"c\"],\"rate\":" + token), "consent.rate"));
        }
        for (String key : List.of("yes", "no")) {
            for (String token : List.of("null", "true", "false", "0", "1", "1.0", "[]", "{}", "\"\"")) {
                cases.add(Arguments.of(consentJson("\"fields\":[\"c\"],\"" + key + "\":" + token), "consent." + key));
            }
        }
        for (String members : List.of("\"yes\":\"same\",\"no\":\"same\"", "\"yes\":\"0\"",
                "\"no\":\"1\"", "\"yes\":\"Y\",\"no\":\"\\u0059\"")) {
            cases.add(Arguments.of(consentJson("\"fields\":[\"c\"]," + members), "consent"));
        }
        return cases.stream();
    }

    static Stream<Arguments> unknownKeys() {
        Stream<Arguments> consentMembers = Stream.of("Fields", "Rate", "Yes", "No", "field", "rates", "unknown",
                "missingRate", "fakeRefusal", "consent", "fields ")
                .map(key -> Arguments.of(consentJson("\"fields\":[\"c\"]," + quote(key) + ":0"), "consent." + key));
        return Stream.concat(consentMembers, Stream.of(
                Arguments.of("{\"Consent\":{\"fields\":[\"c\"]}}", "Consent"),
                Arguments.of("{\"fakeRefusal\":true}", "fakeRefusal"),
                Arguments.of("{\"problems\":{\"fakeRefusal\":0.1}}", "problems.fakeRefusal"),
                Arguments.of("{\"problems\":{\"consent\":{\"fields\":[\"c\"]}}}", "problems.consent"),
                Arguments.of("{\"fields\":{\"c\":{\"consent\":{\"fields\":[\"c\"]}}}}", "fields.c.consent"),
                Arguments.of("{\"repeats\":{\"min\":1,\"max\":2,\"consent\":{}}}", "repeats.consent"),
                Arguments.of("{\"integerRange\":{\"min\":0,\"max\":1,\"consent\":{}}}", "integerRange.consent"),
                Arguments.of("{\"fields\":{\"c\":{\"when\":[{\"if\":\"1=1\",\"value\":1,\"consent\":{}}]}}}",
                        "fields.c.when[0].consent")));
    }

    static Stream<String> duplicateKeys() {
        List<String> cases = new ArrayList<>();
        cases.add("{\"consent\":{\"fields\":[\"a\"]},\"consent\":{\"fields\":[\"b\"]}}");
        cases.add("{\"consent\":{\"fields\":[\"a\"]},\"cons\\u0065nt\":{\"fields\":[\"a\"]}}");
        for (String[] pair : List.of(new String[]{"fields", "[\"c\"]", "[\"d\"]"},
                new String[]{"rate", "0.5", "0.6"}, new String[]{"yes", "\"Y\"", "\"YES\""},
                new String[]{"no", "\"N\"", "\"NO\""})) {
            String prefix = pair[0].equals("fields") ? "" : "\"fields\":[\"c\"],";
            for (String second : List.of(pair[1], pair[2])) {
                cases.add(consentJson(prefix + quote(pair[0]) + ":" + pair[1] + "," + quote(pair[0]) + ":" + second));
            }
            String escaped = "\\u" + String.format("%04x", (int) pair[0].charAt(0)) + pair[0].substring(1);
            cases.add(consentJson(prefix + quote(pair[0]) + ":" + pair[1] + ",\"" + escaped + "\":" + pair[1]));
        }
        return cases.stream();
    }

    static Stream<Arguments> invalidFakeFaults() {
        Stream<Arguments> names = Stream.of("FakeRefusal", "fakerefusal", "fake_refusal", "fakeRefusal ", " fakeRefusal", "fakeRefusals")
                .map(name -> Arguments.of("{\"problems\":{\"faults\":[\"tooFast\"," + quote(name) + "]}}", "problems.faults[1]"));
        return Stream.concat(names, Stream.of(
                Arguments.of("{\"problems\":{\"faults\":[\"fakeRefusal\",\"fakeRefusal\"]}}", "problems.faults[1]"),
                Arguments.of("{\"problems\":{\"faults\":[\"fakeRefusal\",\"tooFast\",\"fakeRefus\\u0061l\"]}}", "problems.faults[2]"),
                Arguments.of("{\"problems\":{\"faults\":[\"fakeRefusal\",null]}}", "problems.faults[1]"),
                Arguments.of("{\"problems\":{\"faults\":\"fakeRefusal\"}}", "problems.faults")));
    }

    static ProblemRates rates(List<String> faults) {
        return new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .88, faults);
    }

    static Stream<List<String>> ordersIncludingFakeRefusal() {
        List<List<String>> result = new ArrayList<>();
        collectOrders(new ArrayList<>(), result);
        return result.stream();
    }

    private static void collectOrders(List<String> prefix, List<List<String>> result) {
        if (prefix.contains("fakeRefusal")) result.add(List.copyOf(prefix));
        for (String fault : DEFAULT_FAULTS) {
            if (!prefix.contains(fault)) {
                prefix.add(fault);
                collectOrders(prefix, result);
                prefix.remove(prefix.size() - 1);
            }
        }
    }
}
