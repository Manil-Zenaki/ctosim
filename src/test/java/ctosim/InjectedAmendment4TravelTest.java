package ctosim;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form walks share JVM state")
class InjectedAmendment4TravelTest extends InjectedAmendment4Support {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void locatedVillagesEnableTravelWithOrWithoutGpsAndIgnoreTheDailyQuota(boolean gps) throws Exception {
        villageTables();
        var r = run(villageForm(gps), cfg(2, 1, 1, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertEquals(Set.of(2), targets(r, "travel"));
        assertEquals(1, villageRecords(r).size());
        var p = villageRecords(r).get(0);
        travelSchedule(r, p);
        distanceFence(r, p);
        assertTrue(Set.of("B", "C").contains(value(r.target(p), "village")),
                "near, unlocated, empty and special-only villages are not candidates");
        assertEquals(value(r.target(p), "village").equals("C") ? "D2" : "D1", value(r.target(p), "district"));
        assertNotEquals(value(r.related(p), "answer"), value(r.target(p), "answer"));
        assertMetadata(r);
    }

    @Test void distantVillageAndItsGpsAnswersOverrideAnUnrelatedConfiguredArea() throws Exception {
        villageTables();
        var r = run(villageForm(true), cfg(2, 1, 4, LOCATED + "," + FORCE_A + "," + AREA)
                .withProblems(only("travel", .5)));
        assertEquals(1, villageRecords(r).size());
        for (int n = 1; n <= 2; n++) {
            nearVillage(r, n);
            gpsFollowsVisit(r.submissions().get(n - 1), r.visit(n), "trace_z", "gps_a", "shape");
        }
        assertTrue(InjectedProblemsTestSupport.distance(r.visit(1), r.visit(2)) > 20000);
    }

    @Test void recordsUseFormOrderActualValuesAndThePreviousVillagesCleanValue() throws Exception {
        villageTables();
        var r = run(villageForm(true), cfg(2, 1, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertEquals(List.of("trace_z", "village", "gps_a", "shape"),
                r.plan().problems().stream().map(InjectedProblem::field).toList());
        for (var p : r.plan().problems()) {
            assertEquals(value(r.target(p), p.field()), p.value());
            assertEquals(p.field().equals("village") ? value(r.submissions().get(0), "village") : "", p.cleanValue());
            assertEquals(key(r.submissions().get(0)), p.relatedKey());
        }
        assertMetadata(r);
    }

    @Test void travelAnswersOptionalLocationsDespiteMissingRateButOmitsIrrelevantOnes() throws Exception {
        villageTables();
        String fields = "\"fields\":{\"district\":{\"weights\":{\"D1\":1}},"
                + "\"village\":{\"weights\":{\"A\":1}},\"trace_z\":{\"missingRate\":1},"
                + "\"gps_a\":{\"missingRate\":1},\"shape\":{\"missingRate\":1}}";
        var r = run(villageForm(true), cfg(2, 1, 4, LOCATED + "," + fields).withProblems(only("travel", .5)));
        for (String name : List.of("trace_z", "gps_a", "shape")) {
            assertEquals("", value(r.submissions().get(0), name));
            assertFalse(value(r.submissions().get(1), name).isEmpty());
        }
        assertEquals("", value(r.submissions().get(1), "hidden"));
        assertEquals(4, r.plan().problems().size());
    }

    @Test void travelRelatesToItsOwnEnumeratorsPreviousInterviewAndRecordsAreSorted() throws Exception {
        villageTables();
        var r = run(villageForm(false), cfg(12, 6, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertEquals(6, villageRecords(r).size());
        for (var p : villageRecords(r)) { travelSchedule(r, p); distanceFence(r, p); }
        assertMetadata(r);
    }

    @Test void consecutiveTravelTargetsMeasureFromTheActualPreviousVillage() throws Exception {
        villageTables();
        var r = run(villageForm(false), cfg(3, 1, 10, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", 2.0 / 3)));
        assertEquals(Set.of(2, 3), targets(r, "travel"));
        assertEquals(2, villageRecords(r).size());
        for (var p : villageRecords(r)) {
            travelSchedule(r, p); distanceFence(r, p);
            assertEquals(value(r.related(p), "village"), p.cleanValue());
        }
    }

    @ParameterizedTest @CsvSource({"89,0,89,2,87,0", "0,179.99,0,-179.99,0,170"})
    void candidateDistanceUsesGreatCircleGeometryAtThePoleAndAcrossTheAntimeridian(
            double aLat, double aLon, double bLat, double bLon, double cLat, double cLon) throws Exception {
        villageTables();
        replaceTable("villages", "A,D1,0,0", "A,D1," + aLat + "," + aLon);
        replaceTable("villages", "B,D1,0,2", "B,D1," + bLat + "," + bLon);
        replaceTable("villages", "C,D2,0,4", "C,D2," + cLat + "," + cLon);
        replaceTable("villages", "N,D1,0,0.1", "N,D1,bad,0.1");
        assertTrue(FieldworkTestSupport.distance(aLat, aLon, bLat, bLon) < 20000);
        assertTrue(FieldworkTestSupport.distance(aLat, aLon, cLat, cLon) > 80000);
        var r = run(villageForm(false), cfg(2, 1, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertEquals(Set.of(2), targets(r, "travel"));
        assertEquals("C", value(r.submissions().get(1), "village"));
        near(r.visit(2), cLat, cLon);
    }

    @Test void aDistantVillageWhoseHouseholdsHaveAlreadyBeenReceivedIsNotACandidate() throws Exception {
        villageTables();
        java.nio.file.Files.writeString(media.resolve("a4-villages.csv"),
                "code,district,lat_col,lon_col\nA,D1,0,0\nB,D1,0,2\nC,D2,0,4\n");
        java.nio.file.Files.writeString(media.resolve("a4-households.csv"),
                "code,village\nA01,A\nB01,B\nC01,C\nC02,C\nC03,C\n");
        var r = find(villageForm(false), cfg(3, 1, 10, LOCATED + "," + FORCE_A)
                        .withProblems(only("travel", 1.0 / 3)),
                candidate -> targets(candidate, "travel").equals(Set.of(3)));
        assertEquals(List.of("A", "B", "C"), column(r.submissions(), "village"));
        assertEquals(List.of("A01", "B01"), column(r.submissions().subList(0, 2), "answer"));
        assertTrue(value(r.submissions().get(2), "answer").startsWith("C"));
        travelSchedule(r, villageRecords(r).get(0));
    }

    @Test void villageRecordsPreserveUnicodeChoiceCodes() throws Exception {
        villageTables();
        replaceTable("villages", "B,D1,0,2", "村-é,D1,0,2");
        replaceTable("villages", "C,D2,0,4", "C,D2,bad,4");
        var path = media.resolve("a4-households.csv");
        java.nio.file.Files.writeString(path, java.nio.file.Files.readString(path).replace(",B\n", ",村-é\n"));
        var r = run(villageForm(false), cfg(2, 1, 4, LOCATED + "," + FORCE_A)
                .withProblems(only("travel", .5)));
        assertEquals(1, villageRecords(r).size());
        assertEquals("村-é", villageRecords(r).get(0).value());
        assertEquals("村-é", value(r.submissions().get(1), "village"));
        assertEquals("A", villageRecords(r).get(0).cleanValue());
    }

    @Test void theNextInterviewStartsTheNextDayWithANewOrdinaryPlace() throws Exception {
        villageTables();
        var r = find(villageForm(false), cfg(3, 1, 10, LOCATED + "," + FORCE_A)
                        .withProblems(only("travel", 1.0 / 3)),
                candidate -> targets(candidate, "travel").equals(Set.of(2)));
        firstOfNextDay(r, 2, 3);
        assertEquals("A", value(r.submissions().get(2), "village"),
                "the next day's ordinary weighted draw must not inherit the travel destination");
    }

    @Test void distanceThresholdUsesTwiceTheActualGapRatherThanOnlyTwentyKilometres() throws Exception {
        villageTables();
        // B is 30 km away; C is always far enough. B is allowed only for gaps <= 15 minutes.
        double longitude = Math.toDegrees(30.0 / 6371);
        replaceTable("villages", "B,D1,0,2", "B,D1,0," + longitude);
        var form = villageForm(false);
        int longGaps = 0, shortGaps = 0, bDraws = 0;
        for (int seed = 500; seed < 660; seed++) {
            var r = run(form, cfg(2, 1, 4, LOCATED + "," + FORCE_A).withSeed(seed).withProblems(only("travel", .5)));
            assertEquals(1, villageRecords(r).size());
            var p = villageRecords(r).get(0);
            long g = gap(r.related(p), r.target(p));
            String village = value(r.target(p), "village");
            if (g > 15) { longGaps++; assertEquals("C", village); }
            // Avoid floating-point equality exactly at 30 km.
            if (g < 15) { shortGaps++; if (village.equals("B")) bDraws++; }
        }
        assertTrue(longGaps > 40 && shortGaps > 5, "exercise both sides of the gap-dependent fence");
        assertTrue(bDraws > 0, "a nearer village is eligible when the actual gap allows it");
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void theVillageCoordinateDistanceFenceIsInclusive(int metresFromFence) throws Exception {
        villageTables();
        var config = cfg(2, 1, 4, LOCATED + "," + FORCE_A).withProblems(only("travel", .5));
        var probe = run(villageForm(false), config);
        assertEquals(1, villageRecords(probe).size());
        long g = gap(probe.submissions().get(0), probe.submissions().get(1));
        double threshold = Math.max(20000, 2000 * g);
        double lon = Math.toDegrees((threshold + metresFromFence) / 6371000);
        replaceTable("villages", "B,D1,0,2", "B,D1,0," + lon);
        replaceTable("villages", "C,D2,0,4", "C,D2,bad,4");
        // Coordinate roundtrip can put mathematical equality one ulp below the fence.
        if (metresFromFence == 0) {
            while (FieldworkTestSupport.distance(0, 0, 0, lon) < threshold) lon = Math.nextUp(lon);
            var path = media.resolve("a4-villages.csv");
            String csv = java.nio.file.Files.readString(path)
                    .replaceAll("(?m)^B,D1,0,[^\\r\\n]+", "B,D1,0," + lon);
            java.nio.file.Files.writeString(path, csv);
        }
        var r = run(villageForm(false), config);
        if (metresFromFence < 0) {
            assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        } else {
            assertEquals(Set.of(2), targets(r, "travel"));
            assertEquals("B", value(r.submissions().get(1), "village"));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void aTravelStartAtClosingIsAllowedButOneSecondLaterFallsBack(int afterClosing) throws Exception {
        villageTables();
        var config = cfg(2, 1, 10, LOCATED + "," + FORCE_A).withProblems(only("travel", .5));
        var probe = run(villageForm(false, 600), config);
        assertEquals(Set.of(2), targets(probe, "travel"));
        long delta = Duration.between(probe.submissions().get(1).start(), config.start().plusHours(9)).getSeconds();
        var form = villageForm(false, Math.toIntExact(600 + delta + afterClosing));
        var r = run(form, config);
        if (afterClosing == 0) {
            assertEquals(Set.of(2), targets(r, "travel"));
            assertEquals(config.start().plusHours(9), r.submissions().get(1).start());
        } else {
            assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
            unchanged(run(form, config.withProblems(null)), r);
        }
    }

}
