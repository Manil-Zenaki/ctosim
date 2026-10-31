package ctosim;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment4MoveTest extends FieldworkAmendment4Support {
    @Test void theNearestViableLocatedCandidateCanBeInAnotherDistrictDespiteWeights() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var plan = new Fieldwork(cascade(), config4(2, 1, 4, LOCATED + "," + FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of("A", "C"), column(rows, "village"),
                "X is nearer but has only special IDs; C beats viable B despite D1's weight");
        assertEquals(List.of("D1", "D2"), column(rows, "district"));
    }

    @ParameterizedTest @CsvSource({"80,0,79.9,0,80,0.2", "0,179.99,0,179,0,-179.99"})
    void nearestCandidatesUseGreatCircleDistanceAtHighLatitudesAndAcrossTheAntimeridian(
            double aLat, double aLon, double bLat, double bLon, double cLat, double cLon) throws Exception {
        tables("route-villages.csv", "route-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1," + aLat + "," + aLon);
        replaceTable("villages", "B,D1,10,20.2", "B,D1," + bLat + "," + bLon);
        replaceTable("villages", "C,D2,10,20.01", "C,D2," + cLat + "," + cLon);
        assertTrue(distance(aLat, aLon, cLat, cLon) < distance(aLat, aLon, bLat, bLon));
        var rows = new Fieldwork(cascade(), config4(2, 1, 4, LOCATED + "," + FORCE_A)).walkAll();
        assertEquals(List.of("A", "C"), column(rows, "village"));
    }

    @Test void equalDistanceCandidatesAreOrderedByUpperChoicesBeforeVillageFileOrder() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        replaceTable("villages", "C,D2,10,20.01", "C,D2,10,20.2");
        // C precedes B in the CSV, but D1 precedes D2 in the offered districts.
        replaceTable("villages", "A,D1,10,20\nB,D1,10,20.2\nX,D1,10,20.00001\nC,D2,10,20.2",
                "A,D1,10,20\nC,D2,10,20.2\nB,D1,10,20.2\nX,D1,10,20.00001");
        var rows = new Fieldwork(cascade(), config4(2, 1, 4, LOCATED + "," + FORCE_A)).walkAll();
        assertEquals(List.of("A", "B"), column(rows, "village"));
    }

    @Test void equalDistanceCandidatesWithinOneDistrictKeepOfferedVillageOrder() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        replaceTable("villages", "C,D2,10,20.01", "C,D1,10,20.2");
        replaceTable("villages", "A,D1,10,20\nB,D1,10,20.2\nX,D1,10,20.00001\nC,D1,10,20.2",
                "A,D1,10,20\nC,D1,10,20.2\nB,D1,10,20.2\nX,D1,10,20.00001");
        var rows = new Fieldwork(cascade(), config4(2, 1, 4, LOCATED + "," + FORCE_A)).walkAll();
        assertEquals(List.of("A", "C"), column(rows, "village"));
    }

    @Test void unavailableNearerVillagesAreNotCandidates() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var form = cascade("search('a4-villages', 'matches', 'district', 'D1')", ID_SEARCH, false, "");
        var rows = new Fieldwork(form, config4(2, 1, 4, LOCATED + "," + FORCE_A)).walkAll();
        assertEquals(List.of("A", "B"), column(rows, "village"), "C is located and near but is not offered");
    }

    @ParameterizedTest @ValueSource(strings = {"origin", "candidates", "configuration"})
    void absentLocationsRestoreAmendmentThreeChoiceWeights(String missing) throws Exception {
        tables("route-villages.csv", "route-households.csv");
        if (missing.equals("origin")) replaceTable("villages", "A,D1,10,20", "A,D1,bad,20");
        if (missing.equals("candidates")) {
            replaceTable("villages", "B,D1,10,20.2", "B,D1,bad,20.2");
            replaceTable("villages", "C,D2,10,20.01", "C,D2,bad,20.01");
        }
        String located = missing.equals("configuration") ? "" : LOCATED + ",";
        var rows = new Fieldwork(cascade(), config4(2, 1, 4, located + FORCE_A)).walkAll();
        assertEquals(List.of("A", "B"), column(rows, "village"), "D1 remains viable and has all the weight");
    }

    @ParameterizedTest @CsvSource({"10,20,10,20.2", "0,0,0,0.000001", "0,0,0,0",
            "80,179.9,80,-179.9", "89.9,40,89.9,-140", "-35,-71.4,-35.05,-71.45"})
    void travelAddsExactlyTwoMinutesPerGreatCircleKilometreRoundedUp(
            double lat1, double lon1, double lat2, double lon2) throws Exception {
        tables("two-villages.csv", "two-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1," + lat1 + "," + lon1);
        replaceTable("villages", "B,D1,10,20.2", "B,D1," + lat2 + "," + lon2);
        var form = cascade();
        var ordinary = new Fieldwork(form, config4(2, 1, 4, FORCE_A));
        var located = new Fieldwork(form, config4(2, 1, 4, LOCATED + "," + FORCE_A));
        var baseline = ordinary.walkAll();
        var actual = located.walkAll();
        assertEquals(List.of("A", "B"), column(actual, "village"));
        assertEquals(baseline.get(0).start(), actual.get(0).start(), "no travel charge for the first interview");
        long ordinaryGap = wholeMinutes(baseline.get(0).end(), baseline.get(1).start());
        assertEquals(ordinaryGap + travelMinutes(lat1, lon1, lat2, lon2),
                wholeMinutes(actual.get(0).end(), actual.get(1).start()), "charge uses village coordinates, not jittered visits");
        assertEquals(actual.get(1).start(), located.visits().get(1).start());
    }

    @Test void keepingAVillageDoesNotChargeItsCoordinatesAsTravelAgain() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var form = cascade();
        var plain = new Fieldwork(form, config4(3, 1, 4, "")).walkAll();
        var located = new Fieldwork(form, config4(3, 1, 4, LOCATED)).walkAll();
        for (int i = 0; i < 3; i++) {
            assertEquals(plain.get(i).start(), located.get(i).start());
            assertEquals(plain.get(i).end(), located.get(i).end());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aMoveWithOneUnlocatedEndAddsNoTravelTime(boolean originUnlocated) throws Exception {
        tables("two-villages.csv", "two-households.csv");
        replaceTable("villages", originUnlocated ? "A,D1,10,20" : "B,D1,10,20.2",
                originUnlocated ? "A,D1,bad,20" : "B,D1,bad,20.2");
        var form = cascade();
        var plain = new Fieldwork(form, config4(2, 1, 4, FORCE_A)).walkAll();
        var located = new Fieldwork(form, config4(2, 1, 4, LOCATED + "," + FORCE_A)).walkAll();
        assertEquals(List.of("A", "B"), column(located, "village"));
        assertEquals(plain.get(1).start(), located.get(1).start());
    }

    @Test void travelPastClosingStartsANewDayAndRedrawsThatDaysPlace() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1,0,0");
        // Nearest candidate C is still over 1000 km away. D1/B wins the fresh
        // day's ordinary viable weighted draw, proving C was not carried over.
        var config = config4(6, 1, 10, LOCATED + "," + FORCE_A + ",\"fieldworkDays\":1");
        var plan = new Fieldwork(cascade(), config);
        var rows = plan.walkAll();
        assertEquals(List.of("A", "B", "B", "C", "C", "C"), column(rows, "village"),
                "the deferred candidate must not reserve or receive an ID before the fresh day's draw");
        assertEquals(1, day(config, plan.visits().get(1)));
        long offset = wholeMinutes(config.start().plusDays(1), rows.get(1).start());
        assertTrue(offset >= 0 && offset <= 60, "new day uses the first interview's offset");
        assertTrue(Duration.between(rows.get(0).end(), rows.get(1).start()).toMinutes() >= 10);
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 2, 1);
        near(plan.visits().get(1), 10, 20.2);
    }

    @Test void aQuotaDayBoundaryDoesNotChargeTravelBetweenDistantVillages() throws Exception {
        tables("two-villages.csv", "two-households.csv");
        replaceTable("villages", "B,D1,10,20.2", "B,D1,-40,-100");
        var config = config4(2, 1, 1, LOCATED + "," + FORCE_A);
        var plan = new Fieldwork(cascade(), config);
        var rows = plan.walkAll();
        assertEquals(List.of("A", "B"), column(rows, "village"));
        assertEquals(1, day(config, plan.visits().get(1)));
        long offset = wholeMinutes(config.start().plusDays(1), rows.get(1).start());
        assertTrue(offset >= 0 && offset <= 60);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void onlyAMoveStrictlyAfterClosingRollsToTheNextDay(int minutesAfterClosing) throws Exception {
        tables("two-villages.csv", "two-households.csv");
        String minimum = "ct:minimum-seconds=\"600\"";
        var form = attached("<district/><village/><answer/>", bind("district", "string", minimum)
                        + bind("village", "string", minimum) + bind("answer", "string", minimum),
                select("district", "search('a4-districts')", "code")
                        + select("village", VILLAGE_SEARCH, "code") + control(ID_SEARCH, "code"));
        var config = config4(2, 1, 4, FORCE_A);
        var baseline = new Fieldwork(form, config).walkAll();
        long remaining = wholeMinutes(baseline.get(1).start(), config.start().plusHours(9));
        long charge = remaining + minutesAfterClosing;
        assertTrue(charge > 0, "fixture leaves time for an in-day move");
        // Equatorial great-circle distance; a quarter-minute margin avoids
        // relying on floating-point equality at ceil's integer boundary.
        double longitude = Math.toDegrees(((charge - 0.25) / 2) / 6371.0);
        replaceTable("villages", "A,D1,10,20", "A,D1,0,0");
        replaceTable("villages", "B,D1,10,20.2", "B,D1,0," + longitude);
        var plan = new Fieldwork(FormLoader.load(xform, media), config4(2, 1, 4, LOCATED + "," + FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of("A", "B"), column(rows, "village"));
        if (minutesAfterClosing <= 0) {
            assertEquals(config.start().plusHours(9).plusMinutes(minutesAfterClosing), rows.get(1).start());
            assertEquals(0, day(config, plan.visits().get(1)));
        } else {
            assertEquals(1, day(config, plan.visits().get(1)));
            long offset = wholeMinutes(config.start().plusDays(1), rows.get(1).start());
            assertTrue(offset >= 0 && offset <= 60);
        }
    }
}
