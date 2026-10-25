package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SimulationConfigAmendment9Support.*;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment9Test {
    @Test
    void defaultsEnableLeftOpenWithoutChangingAnyEarlierDefault() {
        ProblemRates p = ProblemRates.defaults();
        assertEquals(.02, p.leftOpen());
        assertEquals(new ProblemRates(.01, .01, .02, .02, .02, .02, .02, .02, .02, DEFAULT_FAULTS), p);
    }

    @Test
    void canonicalConstructorPlacesLeftOpenBetweenTravelAndOutliers() {
        ProblemRates p = rates(.99);
        assertAll(
                () -> assertEquals(.11, p.resent()), () -> assertEquals(.22, p.reinterview()),
                () -> assertEquals(.33, p.shortDuration()), () -> assertEquals(.44, p.longDuration()),
                () -> assertEquals(.55, p.offHours()), () -> assertEquals(.66, p.overlap()),
                () -> assertEquals(.77, p.travel()), () -> assertEquals(.99, p.leftOpen()),
                () -> assertEquals(.88, p.outliers()),
                () -> assertEquals(List.of("fakeRefusal", "tooFast"), p.faults()));
    }

    static Stream<Double> validDoubles() {
        return Stream.of(0.0, -0.0, Double.MIN_VALUE, Double.MIN_NORMAL, .02, .375, Math.nextDown(1.0), 1.0);
    }

    @ParameterizedTest
    @MethodSource("validDoubles")
    void canonicalConstructorAcceptsTheEntireInclusiveRateInterval(double value) {
        assertEquals(value, rates(value).leftOpen(), 0.0);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment9Support#invalidDoubles")
    void canonicalConstructorRejectsInvalidLeftOpenIncludingNonfiniteValues(double value) {
        assertThrows(IllegalArgumentException.class, () -> rates(value));
    }

    @Test
    void amendment7ConstructorInsertsZeroWithoutShiftingTravelOutliersOrFaults() {
        ProblemRates p = new ProblemRates(.11, .22, .33, .44, .55, .66, .77, .88,
                List.of("fakeRefusal", "tooFast"));
        assertEquals(0.0, p.leftOpen());
        assertEquals(rates(0), p);
    }

    @Test
    void amendment6ConstructorKeepsAllSevenRatesAndDisablesLeftOpenOutliersAndFaults() {
        ProblemRates p = new ProblemRates(.11, .22, .33, .44, .55, .66, .77);
        assertEquals(0.0, p.leftOpen());
        assertEquals(new ProblemRates(.11, .22, .33, .44, .55, .66, .77, 0, 0, List.of()), p);
    }

    static Stream<Arguments> invalidEarlierRates() {
        return IntStream.range(0, 8).boxed().flatMap(index -> invalidDoubles()
                .map(value -> Arguments.of(index, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidEarlierRates")
    void addingLeftOpenDoesNotBypassValidationOfAnyEarlierRate(int index, double value) {
        double[] r = new double[8];
        Arrays.fill(r, .25);
        r[index] = value;
        assertThrows(IllegalArgumentException.class, () -> new ProblemRates(
                r[0], r[1], r[2], r[3], r[4], r[5], r[6], .5, r[7], List.of()));
    }

    @Test
    void allNineRatesMayBeOneWithoutASumConstraint() {
        ProblemRates p = new ProblemRates(1, 1, 1, 1, 1, 1, 1, 1, 1, List.of());
        assertEquals(1.0, p.leftOpen());
    }

    @Test
    void newCanonicalConstructorDefensivelyCopiesTheFaultListAndKeepsItsOrder() {
        List<String> caller = new ArrayList<>(List.of("fakeRefusal", "dontKnow", "tooFast"));
        ProblemRates p = new ProblemRates(0, 0, 0, 0, 0, 0, 0, .75, 0, caller);
        caller.clear();
        assertEquals(List.of("fakeRefusal", "dontKnow", "tooFast"), p.faults());
        assertThrows(UnsupportedOperationException.class, () -> p.faults().set(0, "skipModules"));
        assertEquals(.75, p.leftOpen());
    }

    @ParameterizedTest
    @MethodSource("invalidFaultLists")
    void newCanonicalConstructorStillRejectsInvalidFaultNamesAndDuplicates(List<String> faults) {
        assertThrows(IllegalArgumentException.class,
                () -> new ProblemRates(0, 0, 0, 0, 0, 0, 0, .5, 0, faults));
    }

    static Stream<List<String>> invalidFaultLists() {
        return Stream.of(List.of("leftOpen"), List.of("unknown"), List.of("TooFast"),
                List.of("fakeRefusal", "fakeRefusal"), List.of("tooFast", "dontKnow", "tooFast"));
    }

    @Test
    void newCanonicalConstructorStillRejectsNullFaultListOrElement() {
        assertThrows(NullPointerException.class,
                () -> new ProblemRates(0, 0, 0, 0, 0, 0, 0, .5, 0, null));
        assertThrows(NullPointerException.class,
                () -> new ProblemRates(0, 0, 0, 0, 0, 0, 0, .5, 0, Arrays.asList("tooFast", null)));
    }
}
