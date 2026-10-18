package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("FormWalker temporarily changes the JVM time zone")
class FieldworkLocationTest {
    // Spec 05 prints coordinates to six decimals: two-axis rounding is below 0.08 m.
    private static final double PRINTED_POINT_TOLERANCE_METRES = 0.16;

    @ParameterizedTest @CsvSource({"9.03,38.74", "-35.2,-71.4", "85,150", "0,179.99999", "0,-179.99999"})
    void everyGpsAndFirstTraceOrShapeVertexIsWithinThirtyMetresOfTheVisit(double lat, double lon) throws Exception {
        var walker = new FormWalker(locations(), settings(""));
        Visit visit = new Visit(1, START, lat, lon, null);
        for (int n = 1; n <= 40; n++) {
            Submission submission = walker.walk(n, visit);
            for (String name : List.of("gps", "gps2", "trace", "shape")) {
                double[] first = point(value(submission, name).split(";\\s*")[0]);
                assertTrue(distance(lat, lon, first[0], first[1]) <= 30 + PRINTED_POINT_TOLERANCE_METRES,
                        name + " must use a great-circle distance to the visit");
            }
        }
    }

    @Test void remainingTraceVerticesKeepTheSpecifiedStepRuleAndShapesClose() throws Exception {
        var walker = new FormWalker(locations(), settings(""));
        for (int n = 1; n <= 40; n++) {
            Submission submission = walker.walk(n, visit(1));
            for (String name : List.of("trace", "shape")) {
                String[] vertices = value(submission, name).split(";\\s*");
                assertEquals(name.equals("trace") ? 4 : 5, vertices.length);
                for (int j = 1; j < 4; j++) {
                    double[] previous = point(vertices[j - 1]);
                    double[] current = point(vertices[j]);
                    assertTrue(Math.abs(previous[0] - current[0]) <= 0.001001001);
                    assertTrue(Math.abs(previous[1] - current[1]) <= 0.001001001);
                }
                if (name.equals("shape")) assertArrayEquals(point(vertices[0]), point(vertices[4]));
            }
        }
    }

    @Test void aGlobalAreaDoesNotReplaceTheVisitLocationForAnswers() throws Exception {
        var config = settings("\"area\":{\"minLatitude\":-10,\"maxLatitude\":-9,\"minLongitude\":-20,\"maxLongitude\":-19}");
        var walker = new FormWalker(locations(), config);
        var result = walker.walk(1, visit(1));
        double[] gps = point(value(result, "gps"));
        assertTrue(distance(visit(1).latitude(), visit(1).longitude(), gps[0], gps[1]) <= 30.16);
    }

    @ParameterizedTest @ValueSource(strings = {"gps", "trace", "shape"})
    void aFieldsOwnAreaTakesPrecedenceOverTheVisitLocation(String name) throws Exception {
        var config = settings("\"fields\":{\"" + name + "\":{\"area\":{\"minLatitude\":-22,"
                + "\"maxLatitude\":-21,\"minLongitude\":120,\"maxLongitude\":121}}}");
        var walker = new FormWalker(locations(), config);
        for (int n = 1; n <= 30; n++) {
            var result = walker.walk(n, visit(1));
            double[] own = point(value(result, name).split(";\\s*")[0]);
            assertTrue(own[0] >= -22 && own[0] <= -21);
            assertTrue(own[1] >= 120 && own[1] <= 121);
            double[] other = point(value(result, "gps2"));
            assertTrue(distance(visit(1).latitude(), visit(1).longitude(), other[0], other[1]) <= 30.16);
        }
    }

    @Test void locationOverridesAlsoApplyInsideRepeatInstances() throws Exception {
        var form = fixture("<row jr:template=\"\"><gps/></row>", bind("row/gps", "geopoint", ""),
                "<repeat nodeset=\"/data/row\">" + input("row/gps") + "</repeat>");
        var config = settings("\"repeats\":{\"min\":4,\"max\":4}");
        var result = new FormWalker(form, config).walk(1, visit(1));
        assertEquals(4, count(result, "row"));
        for (int i = 1; i <= 4; i++) {
            double[] gps = point(value(result, "row[" + i + "]/gps"));
            assertTrue(distance(visit(1).latitude(), visit(1).longitude(), gps[0], gps[1]) <= 30.16);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void plannedHouseholdsStayInsideTheAreaAndSameEnumeratorDayClustersStayWithinThreeKm(boolean configured) throws Exception {
        String area = configured ? ",\"area\":{\"minLatitude\":8,\"maxLatitude\":10,\"minLongitude\":38,\"maxLongitude\":40}" : "";
        var config = settings("\"submissions\":120,\"enumerators\":8,\"fieldworkDays\":3" + area);
        var plan = new Fieldwork(timed(60), config);
        plan.walkAll();
        for (Visit visit : plan.visits()) {
            assertInside(visit, configured ? 8 : -1, configured ? 10 : 1, configured ? 38 : -1, configured ? 40 : 1);
        }
        int compared = 0;
        for (int i = 0; i < plan.visits().size(); i++) {
            Visit a = plan.visits().get(i);
            for (int j = 0; j < i; j++) {
                Visit b = plan.visits().get(j);
                if (a.enumerator() == b.enumerator() && day(config, a) == day(config, b)) {
                    assertTrue(distance(a.latitude(), a.longitude(), b.latitude(), b.longitude()) <= 3000.000001);
                    compared++;
                }
            }
        }
        assertTrue(compared > 100, "exercise multi-interview villages");
    }

    @ParameterizedTest @CsvSource({"9,9,39,39", "9,9,39,40", "9,10,39,39", "89.99,90,179.99,180",
            "-90,-89.99,-180,-179.99", "9,9.000001,39,39.000001"})
    void narrowDegenerateAndPolarAreasStillContainEveryHousehold(double minLat, double maxLat, double minLon, double maxLon) throws Exception {
        var config = settings("\"submissions\":30,\"enumerators\":3,\"fieldworkDays\":2,"
                + "\"area\":{\"minLatitude\":" + minLat + ",\"maxLatitude\":" + maxLat
                + ",\"minLongitude\":" + minLon + ",\"maxLongitude\":" + maxLon + "}");
        var plan = new Fieldwork(timed(60), config);
        plan.walkAll();
        for (Visit visit : plan.visits()) assertInside(visit, minLat, maxLat, minLon, maxLon);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void independentVillagesCoverTheRectangleUniformlyWithinTheHouseholdDisplacementBound(boolean configured) throws Exception {
        int draws = 1600;
        String area = configured ? ",\"area\":{\"minLatitude\":8,\"maxLatitude\":10,\"minLongitude\":38,\"maxLongitude\":40}" : "";
        // q = 1: every sampled visit belongs to a distinct enumerator/day village.
        var plan = new Fieldwork(timed(60), settings("\"submissions\":" + draws + ",\"enumerators\":400,\"fieldworkDays\":4" + area));
        plan.walkAll();
        int[] latitude = new int[4];
        int[] longitude = new int[4];
        int[][] joint = new int[4][4];
        for (Visit visit : plan.visits()) {
            assertInside(visit, configured ? 8 : -1, configured ? 10 : 1, configured ? 38 : -1, configured ? 40 : 1);
            int lat = Math.min(3, (int) ((visit.latitude() - (configured ? 8 : -1)) * 2));
            int lon = Math.min(3, (int) ((visit.longitude() - (configured ? 38 : -1)) * 2));
            latitude[lat]++;
            longitude[lon]++;
            joint[lat][lon]++;
        }
        // A household may be at most 1.5 km from an unexposed village. Allow the full
        // probability mass in 1.5-km bands on each bin edge, plus seven binomial SDs.
        for (int i = 0; i < 4; i++) {
            assertEquals(draws / 4.0, latitude[i], 7 * Math.sqrt(draws * .25 * .75) + draws * .015);
            assertEquals(draws / 4.0, longitude[i], 7 * Math.sqrt(draws * .25 * .75) + draws * .015);
            for (int j = 0; j < 4; j++) {
                assertEquals(draws / 16.0, joint[i][j], 7 * Math.sqrt(draws / 16.0 * 15 / 16) + draws * .03);
            }
        }
    }

    @Test void answerRandomnessAndInterviewDurationDoNotChangePlannedLocationsWhenTheDayIsUnchanged() throws Exception {
        var config = settings("\"submissions\":18,\"enumerators\":3,\"fieldworkDays\":2");
        var shortPlan = new Fieldwork(timed(60), config);
        var longPlan = new Fieldwork(timed(120), config);
        shortPlan.walkAll();
        longPlan.walkAll();
        for (int i = 0; i < 18; i++) {
            Visit a = shortPlan.visits().get(i);
            Visit b = longPlan.visits().get(i);
            assertEquals(day(config, a), day(config, b));
            assertEquals(a.latitude(), b.latitude());
            assertEquals(a.longitude(), b.longitude());
        }
    }

    private static void assertInside(Visit visit, double minLat, double maxLat, double minLon, double maxLon) {
        assertTrue(Double.isFinite(visit.latitude()) && Double.isFinite(visit.longitude()));
        assertTrue(visit.latitude() >= minLat && visit.latitude() <= maxLat, visit.toString());
        assertTrue(visit.longitude() >= minLon && visit.longitude() <= maxLon, visit.toString());
    }
}
