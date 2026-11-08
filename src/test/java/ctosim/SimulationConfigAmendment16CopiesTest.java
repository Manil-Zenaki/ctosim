package ctosim;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment15Support.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment16CopiesTest {
    static Stream<Arguments> copies() {
        return bothReaders(Stream.of("", "\"final\":[]", "\"final\":[\"z\",\"a\",\"0\"]")
                .flatMap(members -> Stream.of("seed", "sameSeed", "count", "sameCount", "addProblems",
                        "replaceProblems", "clearProblems", "sameProblems")
                        .map(operation -> Arguments.of(members, operation))));
    }

    @ParameterizedTest
    @MethodSource("copies")
    void everyCopyKeepsTheWholeVisitPlanAndLeavesTheReceiverUnchanged(String members, String operation,
            boolean load) throws Exception {
        String planMembers = "\"available\":\"Y\",\"number\":\"visit_number\","
                + "\"revisitRate\":0.625,\"unreachedRate\":0.125,\"maxVisits\":10"
                + (members.isEmpty() ? "" : "," + members);
        String json = visits(planMembers);
        if (!operation.equals("addProblems")) {
            json = "{\"problems\":{\"travel\":0.75}," + json.substring(1);
        }
        try (Input input = input(json, load)) {
            SimulationConfig original = input.read();
            VisitPlan expected = new VisitPlan("availability", "Y", "visit_number", 0.625, 0.125, 10,
                    members.contains("\"z\"") ? List.of("z", "a", "0") : List.of());
            var oldProblems = original.problems();
            SimulationConfig copy = switch (operation) {
                case "seed" -> original.withSeed(Long.MIN_VALUE);
                case "sameSeed" -> original.withSeed(original.seed());
                case "count" -> original.withSubmissions(1_000_000);
                case "sameCount" -> original.withSubmissions(original.submissions());
                case "addProblems", "replaceProblems" -> original.withProblems(ProblemRates.defaults());
                case "clearProblems" -> original.withProblems(null);
                case "sameProblems" -> original.withProblems(oldProblems.orElseThrow());
                default -> throw new AssertionError(operation);
            };
            assertNotSame(original, copy);
            assertEquals(expected, copy.visits().orElseThrow());
            assertEquals(expected, original.visits().orElseThrow());
            assertEquals(1, original.seed());
            assertEquals(100, original.submissions());
            assertEquals(oldProblems, original.problems());
            assertThrows(UnsupportedOperationException.class,
                    () -> copy.visits().orElseThrow().finalValues().add("extra"));
            assertEquals(expected, copy.visits().orElseThrow());
        }
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                mutation("add", list -> list.add("extra")),
                mutation("add at index", list -> list.add(1, "extra")),
                mutation("addAll", list -> list.addAll(List.of("extra"))),
                mutation("set", list -> list.set(0, "extra")),
                mutation("remove at index", list -> list.remove(0)),
                mutation("remove value", list -> list.remove("z")),
                mutation("clear", List::clear),
                mutation("removeIf", list -> list.removeIf("z"::equals)),
                mutation("replaceAll", list -> list.replaceAll(value -> value + "!")),
                mutation("sort", list -> list.sort(String::compareTo)),
                mutation("sublist clear", list -> list.subList(0, 1).clear()),
                mutation("iterator remove", list -> { var it = list.iterator(); it.next(); it.remove(); }),
                mutation("list iterator set", list -> { var it = list.listIterator(); it.next(); it.set("extra"); }),
                mutation("list iterator add", list -> list.listIterator().add("extra")));
    }

    private static Arguments mutation(String name, Consumer<List<String>> mutation) {
        return Arguments.of(name, mutation);
    }

    @ParameterizedTest(name = "parser list rejects {0}")
    @MethodSource("mutations")
    void parserFinalListRejectsMutationsThroughEveryView(String description, Consumer<List<String>> mutation)
            throws Exception {
        SimulationConfig config = SimulationConfig.parse(visits("\"final\":[\"z\",\"a\"]"), "final-list.json");
        assertThrows(UnsupportedOperationException.class,
                () -> mutation.accept(config.visits().orElseThrow().finalValues()));
        assertEquals(List.of("z", "a"), config.visits().orElseThrow().finalValues());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"final\":[]"})
    void omittedAndExplicitlyEmptyParserListsAreUnmodifiable(String members) throws Exception {
        SimulationConfig config = SimulationConfig.parse(visits(members), "empty-final.json");
        assertThrows(UnsupportedOperationException.class, () -> config.visits().orElseThrow().finalValues().add("moved"));
        assertEquals(List.of(), config.visits().orElseThrow().finalValues());
    }

    @Test
    void copyChainsKeepFinalValuesAndNullNumber() throws Exception {
        SimulationConfig original = SimulationConfig.parse(visits("\"final\":[\"moved\",\"died\"]"), "chain.json");
        SimulationConfig copy = original.withSeed(17).withSubmissions(1)
                .withProblems(ProblemRates.defaults()).withSeed(Long.MAX_VALUE).withProblems(null);
        assertEquals(new VisitPlan("availability", "1", null, 0.1, 0.025, 3, List.of("moved", "died")),
                copy.visits().orElseThrow());
        assertEquals(original.visits(), copy.visits());
        assertThrows(UnsupportedOperationException.class, () -> copy.visits().orElseThrow().finalValues().set(0, "x"));
    }

    @Test
    void parsingAnotherPlanDoesNotChangePreviouslyReturnedFinalValues() throws Exception {
        SimulationConfig first = SimulationConfig.parse(visits("\"final\":[\"z\",\"a\"]"), "first.json");
        SimulationConfig second = SimulationConfig.parse(visits("\"final\":[\"died\"]"), "second.json");
        SimulationConfig empty = SimulationConfig.parse(visits(""), "empty.json");
        assertEquals(List.of("z", "a"), first.visits().orElseThrow().finalValues());
        assertEquals(List.of("died"), second.visits().orElseThrow().finalValues());
        assertEquals(List.of(), empty.visits().orElseThrow().finalValues());
    }
}
