package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment7Support.*;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment7Test {
    @Test
    void defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder() {
        ProblemRates defaults = ProblemRates.defaults();
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, defaults);
        assertEquals(.02, defaults.outliers());
        assertEquals(DEFAULT_FAULTS, defaults.faults());
    }

    @Test
    void sevenRateConstructorPreservesItsArgumentsAndDisablesBothNewProblemKinds() {
        double[] values = {.11, .22, .33, .44, .55, .66, .77};
        ProblemRates legacy = SimulationConfigProblemsSupport.rates(values);
        SimulationConfigProblemsSupport.assertRates(values, legacy);
        assertEquals(0.0, legacy.outliers(), 0.0);
        assertEquals(List.of(), legacy.faults());
        assertThrows(UnsupportedOperationException.class, () -> legacy.faults().add("tooFast"));
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#faultLists")
    void canonicalConstructorKeepsEveryOrderedSubsetIncludingEmpty(List<String> faults) {
        ProblemRates result = rates(.875, faults);
        assertEquals(faults, result.faults());
        assertEquals(.875, result.outliers());
        SimulationConfigProblemsSupport.assertRates(new double[]{.11, .22, .33, .44, .55, .66, .77}, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.0, 1, .375, Double.MIN_VALUE, Double.MIN_NORMAL, 0.9999999999999999})
    void canonicalConstructorAcceptsInclusiveOutlierBoundsAndInteriorDoubles(double value) {
        assertEquals(value, rates(value, List.of()).outliers(), 0.0);
    }

    static Stream<Arguments> invalidCanonicalRates() {
        return IntStream.range(0, 8).boxed().flatMap(index -> Stream.of(
                -Double.MIN_VALUE, -.1, Math.nextUp(1.0), 2.0, Double.NaN,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)
                .map(value -> Arguments.of(index, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidCanonicalRates")
    void canonicalConstructorValidatesEveryRateIncludingOutliers(int index, double value) {
        double[] r = {.1, .2, .3, .4, .5, .6, .7, .8};
        r[index] = value;
        assertThrows(IllegalArgumentException.class, () -> new ProblemRates(
                r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], LEGACY_FAULTS));
    }

    @Test
    void allRatesMayBeOneWithoutSumOrFaultFeasibilityConstraints() {
        ProblemRates result = new ProblemRates(1, 1, 1, 1, 1, 1, 1, 1, LEGACY_FAULTS);
        SimulationConfigProblemsSupport.assertRates(new double[]{1, 1, 1, 1, 1, 1, 1}, result);
        assertEquals(1, result.outliers());
        assertEquals(LEGACY_FAULTS, result.faults());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown", "TooFast", "skipmodules", "dontknow", " tooFast", "tooFast ", "tooFast\t", "故障"})
    void constructorRejectsUnknownFaultNamesWithoutTrimmingOrCaseFolding(String invalid) {
        assertThrows(IllegalArgumentException.class, () -> rates(.2, List.of(invalid)));
        assertThrows(IllegalArgumentException.class, () -> rates(.2, List.of("dontKnow", invalid)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tooFast", "skipModules", "dontKnow"})
    void constructorRejectsAdjacentAndSeparatedDuplicates(String fault) {
        String other = LEGACY_FAULTS.stream().filter(name -> !name.equals(fault)).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> rates(.2, List.of(fault, fault)));
        assertThrows(IllegalArgumentException.class, () -> rates(.2, List.of(fault, other, fault)));
    }

    @Test
    void nullFaultListThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> rates(.2, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void nullFaultElementThrowsNullPointerException(int index) {
        List<String> faults = new ArrayList<>(LEGACY_FAULTS);
        faults.set(index, null);
        assertThrows(NullPointerException.class, () -> rates(.2, faults));
    }

    @Test
    void constructorTakesADefensiveSnapshotOfTheSuppliedList() {
        List<String> input = new ArrayList<>(List.of("dontKnow", "tooFast"));
        ProblemRates result = rates(.625, input);
        input.set(0, "skipModules");
        input.clear();
        input.add(null);
        assertEquals(List.of("dontKnow", "tooFast"), result.faults());
        assertEquals(.625, result.outliers());
    }

    static Stream<Consumer<List<String>>> mutations() {
        return Stream.of(
                list -> list.add("dontKnow"),
                list -> list.set(0, "dontKnow"),
                list -> list.remove(0),
                List::clear,
                list -> { var iterator = list.iterator(); iterator.next(); iterator.remove(); },
                list -> { var iterator = list.listIterator(); iterator.next(); iterator.set("dontKnow"); },
                list -> list.subList(0, 1).clear(),
                list -> list.replaceAll(value -> "dontKnow"),
                list -> list.removeIf(value -> true));
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void faultsCannotBeMutatedThroughTheAccessorOrItsViews(Consumer<List<String>> mutation) {
        ProblemRates result = rates(.2, Arrays.asList("skipModules", "tooFast"));
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(result.faults()));
        assertEquals(List.of("skipModules", "tooFast"), result.faults());
    }

    @Test
    void defaultFaultsAreUnmodifiableAndUnchangedByCustomInstances() {
        ProblemRates defaults = ProblemRates.defaults();
        assertThrows(UnsupportedOperationException.class, () -> defaults.faults().remove(0));
        rates(0, List.of());
        assertEquals(DEFAULT_FAULTS, defaults.faults());
        assertEquals(DEFAULT_FAULTS, ProblemRates.defaults().faults());
    }
}
