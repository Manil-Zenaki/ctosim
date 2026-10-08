package ctosim;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoGeographyTest {
    private static final double RADIUS = 6_371_000.0;

    static Stream<Arguments> distances() {
        return Stream.of(Arguments.of("0 0", "0 0", 0.0),
                Arguments.of("12.5 -44.75", "12.5 -44.75", 0.0),
                Arguments.of("0 0", "0 1", RADIUS * Math.PI / 180),
                Arguments.of("0 0", "1 0", RADIUS * Math.PI / 180),
                Arguments.of("0 0", "0 90", RADIUS * Math.PI / 2),
                Arguments.of("0 0", "0 180", RADIUS * Math.PI),
                Arguments.of("-90 0", "90 0", RADIUS * Math.PI),
                Arguments.of("0 180", "0 -180", 0.0),
                Arguments.of("0 179", "0 -179", RADIUS * Math.PI / 90),
                Arguments.of("60 0", "60 90", RADIUS * Math.acos(0.75)),
                Arguments.of("-30 -20", "30 -20", RADIUS * Math.PI / 3),
                Arguments.of("0 0 100 2", "0 1 -100 99", RADIUS * Math.PI / 180),
                Arguments.of("0 0", "0 0.000001", RADIUS * Math.PI / 180_000_000));
    }

    @ParameterizedTest @MethodSource("distances")
    void distanceUsesSpecifiedSphereAndIgnoresAltitudeAndAccuracy(String p1, String p2, double expected) throws Exception {
        String call = "distance-between('" + p1 + "', '" + p2 + "')";
        var session = expressionForm(call).newEntryController();
        double actual = assertInstanceOf(Double.class, evaluate(session, call));
        assertEquals(expected, actual, 0.001, "Distance in metres (one millimetre tolerance)");
        assertEquals(actual, assertInstanceOf(Double.class,
                evaluate(session, "distance-between('" + p2 + "', '" + p1 + "')")), 0.001);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "bad 0", "0 bad", "91 0", "-91 0",
            "0 181", "0 -181", "90.0001 0", "-90.0001 0", "0 180.0001", "0 -180.0001",
            "NaN 0", "0 NaN", "Infinity 0", "0 Infinity"})
    void invalidPointInEitherArgumentReturnsNumericNaN(String point) throws Exception {
        String call = "distance-between('" + point + "', '0 0')";
        var session = expressionForm(call).newEntryController();
        assertTrue(assertInstanceOf(Double.class, evaluate(session, call)).isNaN());
        assertTrue(assertInstanceOf(Double.class,
                evaluate(session, "distance-between('0 0', '" + point + "')")).isNaN());
    }

    static Stream<Arguments> shortPoints() {
        return Stream.of(Arguments.of("", ""), Arguments.of("12.3", ""),
                Arguments.of("12.30 -045.600", "12.30 -045.600"),
                Arguments.of("12.3 -45.6 100", "12.3 -45.6"),
                Arguments.of("12.3 -45.6 100 5", "12.3 -45.6"),
                Arguments.of("north east", "north east"), Arguments.of("91 181 0", "91 181"));
    }

    @ParameterizedTest @MethodSource("shortPoints")
    void shortGeopointKeepsTheFirstTwoItemsWithoutNumericValidation(String point, String expected) throws Exception {
        String call = "short-geopoint('" + point + "')";
        assertValue(expected, evaluate(expressionForm(call).newEntryController(), call));
    }

    static Stream<Arguments> scatterCases() {
        return Stream.of(Arguments.of("", "0"), Arguments.of("12.30 -45.60 100 5", "1000"),
                Arguments.of("  12.30   -45.60  ", "-1"), Arguments.of("not a geopoint", "'bad'"),
                Arguments.of("東京", "1000000"));
    }

    @ParameterizedTest @MethodSource("scatterCases")
    void geoScatterReturnsItsFirstArgumentExactlyWithoutRandomness(String point, String range) throws Exception {
        String call = "geo-scatter('" + point + "', " + range + ")";
        var loaded = expressionForm(call);
        for (int s = 0; s < 2; s++) {
            var session = loaded.newEntryController();
            for (int i = 0; i < 5; i++) assertValue(point, evaluate(session, call));
        }
    }

    @org.junit.jupiter.api.Test void geographyAcceptsComputedAndFieldArguments() throws Exception {
        var session = expressionForm("concat(short-geopoint(/data/text), geo-scatter(/data/text, /data/number), "
                + "distance-between(concat('0', ' 0'), concat('0', ' 0')))").newEntryController();
        assertValue("AbC café", evaluate(session, "short-geopoint(/data/text)"));
        assertValue("AbC café 東京", evaluate(session, "geo-scatter(/data/text, /data/number)"));
        assertValue(0.0, evaluate(session, "distance-between(concat('0', ' 0'), concat('0', ' 0'))"));
    }
}
