package ctosim;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class VisitPlanAmendment16Test {
    static Stream<Arguments> finalLists() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of()),
                Arguments.of(List.of("z", "a")),
                Arguments.of(List.of("1")),
                Arguments.of(List.of("moved", "moved")),
                Arguments.of(List.of("", " ")),
                Arguments.of(Arrays.asList("moved", null)));
    }

    @ParameterizedTest
    @MethodSource("finalLists")
    void canonicalConstructorDoesNotValidateFinalValues(List<String> values) {
        VisitPlan plan = assertDoesNotThrow(() -> new VisitPlan("a", "1", "n", 0.25, 0.125, 4, values));
        assertEquals(values, plan.finalValues());
        assertEquals("a", plan.field());
        assertEquals("1", plan.available());
        assertEquals("n", plan.number());
        assertEquals(0.25, plan.revisitRate());
        assertEquals(0.125, plan.unreachedRate());
        assertEquals(4, plan.maxVisits());
    }

    static Stream<Arguments> uncheckedComponents() {
        return Stream.of(
                Arguments.of("availability", "1", null, 0.1, 0.025, 3),
                Arguments.of(null, null, null, Double.NaN, Double.POSITIVE_INFINITY, Integer.MIN_VALUE),
                Arguments.of("", "", "", -1.0, 2.0, 11),
                Arguments.of(" same ", " Y ", " same ", 0.75, 0.75, 1));
    }

    @ParameterizedTest
    @MethodSource("uncheckedComponents")
    void sixArgumentConstructorKeepsComponentsAndAddsAnEmptyFinalList(String field, String available,
            String number, double revisit, double unreached, int maximum) {
        VisitPlan legacy = assertDoesNotThrow(() -> new VisitPlan(field, available, number, revisit, unreached, maximum));
        assertEquals(List.of(), legacy.finalValues());
        assertEquals(field, legacy.field());
        assertEquals(available, legacy.available());
        assertEquals(number, legacy.number());
        assertEquals(revisit, legacy.revisitRate());
        assertEquals(unreached, legacy.unreachedRate());
        assertEquals(maximum, legacy.maxVisits());
    }

    @ParameterizedTest
    @MethodSource("uncheckedComponents")
    void addingFinalValuesDoesNotIntroduceValidationOfExistingComponents(String field, String available,
            String number, double revisit, double unreached, int maximum) {
        VisitPlan plan = assertDoesNotThrow(() -> new VisitPlan(field, available, number, revisit, unreached,
                maximum, List.of("moved")));
        assertEquals(field, plan.field());
        assertEquals(available, plan.available());
        assertEquals(number, plan.number());
        assertEquals(revisit, plan.revisitRate());
        assertEquals(unreached, plan.unreachedRate());
        assertEquals(maximum, plan.maxVisits());
        assertEquals(List.of("moved"), plan.finalValues());
    }
}
