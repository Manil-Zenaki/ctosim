package ctosim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VillagesAmendment10Test {
    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment10Support#acceptedNames")
    void constructorPreservesExactDistinctColumnNames(String latitude, String longitude) {
        Villages villages = new Villages(latitude, longitude);
        assertEquals(latitude, villages.latitude());
        assertEquals(longitude, villages.longitude());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment10Support#invalidNames")
    void constructorRejectsEmptyOrEdgeWhitespaceLatitude(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Villages(name, "longitude"));
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment10Support#invalidNames")
    void constructorRejectsEmptyOrEdgeWhitespaceLongitude(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Villages("latitude", name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "Latitude", "緯度", "a b", "é"})
    void constructorRejectsEqualNamesEvenWhenTheyAreDifferentStringInstances(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Villages(name, new String(name)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"longitude"})
    void constructorRejectsNullLatitudeIncludingWhenBothComponentsAreNull(String longitude) {
        assertThrows(NullPointerException.class, () -> new Villages(null, longitude));
    }

    @ParameterizedTest
    @ValueSource(strings = {"latitude", "緯度"})
    void constructorRejectsNullLongitude(String latitude) {
        assertThrows(NullPointerException.class, () -> new Villages(latitude, null));
    }
}
