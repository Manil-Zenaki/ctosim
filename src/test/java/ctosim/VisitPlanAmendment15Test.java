package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class VisitPlanAmendment15Test {
    static Stream<Arguments> components() {
        return Stream.of(
                Arguments.of("availability", "1", null, 0.1, 0.025, 3),
                Arguments.of(null, null, null, 0.1, 0.025, 3),
                Arguments.of("", "", "", 0.1, 0.025, 3),
                Arguments.of(" edge ", " Y ", " edge ", 0.1, 0.025, 3),
                Arguments.of("a", "1", "a", 0.1, 0.025, 3),
                Arguments.of("a", "1", "n", -1.0, 2.0, 1),
                Arguments.of("a", "1", "n", 0.75, 0.75, 11),
                Arguments.of("a", "1", "n", Double.NaN, Double.POSITIVE_INFINITY, Integer.MAX_VALUE),
                Arguments.of("a", "1", "n", Double.NEGATIVE_INFINITY, Double.NaN, Integer.MIN_VALUE));
    }

    @ParameterizedTest
    @MethodSource("components")
    void recordEnforcesNothingAndPreservesEveryComponent(String field, String available, String number,
            double revisit, double unreached, int maxVisits) {
        VisitPlan plan = assertDoesNotThrow(() -> new VisitPlan(field, available, number, revisit, unreached, maxVisits));
        assertAll(
                () -> assertEquals(field, plan.field()),
                () -> assertEquals(available, plan.available()),
                () -> assertEquals(number, plan.number()),
                () -> assertEquals(revisit, plan.revisitRate()),
                () -> assertEquals(unreached, plan.unreachedRate()),
                () -> assertEquals(maxVisits, plan.maxVisits()));
    }
}
