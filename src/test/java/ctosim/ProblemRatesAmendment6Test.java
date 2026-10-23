package ctosim;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SimulationConfigProblemsSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ProblemRatesAmendment6Test {
    @Test
    void defaultsExposeExactlyTheSevenSpecifiedRates() {
        assertRates(DEFAULTS, ProblemRates.defaults());
    }

    @Test
    void constructorStoresEachRateInItsOwnComponentWithoutNormalization() {
        double[] expected = {.11, .22, .33, .44, .55, .66, .77};
        assertRates(expected, rates(expected));
    }

    static Stream<Arguments> validComponents() {
        return IntStream.range(0, 7).boxed().flatMap(index -> Stream.of(
                0.0, -0.0, Double.MIN_VALUE, Math.nextUp(0.0), .125, Math.nextDown(1.0), 1.0)
                .map(value -> Arguments.of(index, value)));
    }

    @ParameterizedTest
    @MethodSource("validComponents")
    void eachComponentAcceptsBothEndpointsAndFiniteInteriorValues(int index, double value) {
        double[] expected = DEFAULTS.clone();
        expected[index] = value;
        assertRates(expected, rates(expected));
    }

    static Stream<Arguments> invalidComponents() {
        return IntStream.range(0, 7).boxed().flatMap(index -> Stream.of(
                -Double.MIN_VALUE, -.1, -Double.MAX_VALUE, Math.nextUp(1.0), 2.0,
                Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN)
                .map(value -> Arguments.of(index, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidComponents")
    void everyComponentRejectsOutOfRangeAndNonfiniteValues(int index, double value) {
        double[] invalid = DEFAULTS.clone();
        invalid[index] = value;
        assertThrows(IllegalArgumentException.class, () -> rates(invalid), KEYS[index]);
    }

    @Test
    void allZeroRatesAreValid() {
        assertRates(new double[7], rates(new double[7]));
    }

    @Test
    void allOneRatesAreValidWithoutASumConstraint() {
        double[] expected = new double[7];
        Arrays.fill(expected, 1);
        assertRates(expected, rates(expected));
    }
}
