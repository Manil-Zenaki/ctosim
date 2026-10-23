package ctosim;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change JVM time zone")
class InjectedProblemsTravelTest {
    @Test void travelPointsAreSpreadAcrossTheAllowedAreaInsteadOfClusteringAtItsEdge() throws Exception {
        int draws = 600;
        var r = run(ordinary(0, false, false, true), config(draws * 2, draws,
                "\"area\":{\"minLatitude\":-20,\"maxLatitude\":20,\"minLongitude\":-20,\"maxLongitude\":20}")
                .withProblems(only("travel", .5)));
        assertEquals(draws, targets(r, "travel").size()); int[] cells = new int[4]; int central = 0;
        for (int n : targets(r, "travel")) {
            var v = r.visit(n); assertTrue(v.latitude() >= -20 && v.latitude() <= 20);
            assertTrue(v.longitude() >= -20 && v.longitude() <= 20);
            int row = Math.min(1, (int) ((v.latitude() + 20) / 20));
            int col = Math.min(1, (int) ((v.longitude() + 20) / 20)); cells[row * 2 + col]++;
            if (Math.abs(v.latitude()) <= 10 && Math.abs(v.longitude()) <= 10) central++;
        }
        // The excluded radius is at most 80 km, under .2% of this rectangle's area;
        // add two counts beyond the seven-sigma bound to allow that conditioning.
        double expected = draws / 4.0, tolerance = 7 * Math.sqrt(draws * .25 * .75) + 4;
        for (int cell : cells) assertEquals(expected, cell, tolerance);
        assertEquals(expected, central, tolerance, "the central quarter has the same area as a quadrant");
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void travelUsesSameDayTenToFortyMinuteGapAndRequiredGreatCircleDistance(boolean explicitArea) throws Exception {
        String extra = "\"interviewsPerDay\":1" + (explicitArea
                ? ",\"area\":{\"minLatitude\":8,\"maxLatitude\":12,\"minLongitude\":38,\"maxLongitude\":42}" : "");
        var r = run(ordinary(0, false, false, true), config(80, 40, extra).withProblems(only("travel", .5)));
        assertEquals(40, targets(r, "travel").size());
        for (var p : representatives(r)) {
            var previous = r.related(p); var target = r.target(p);
            assertEquals(target.number() - 40, previous.number()); assertEquals(day(r, previous.number()), day(r, target.number()));
            long minutes = gap(previous, target); assertTrue(minutes >= 10 && minutes <= 40);
            assertTrue(distance(r.visit(previous.number()), r.visit(target.number())) + 1e-6 >= Math.max(20000, 2000 * minutes));
            var visit = r.visit(target.number());
            assertTrue(visit.latitude() >= (explicitArea ? 8 : -1) && visit.latitude() <= (explicitArea ? 12 : 1));
            assertTrue(visit.longitude() >= (explicitArea ? 38 : -1) && visit.longitude() <= (explicitArea ? 42 : 1));
            assertEquals(r.visit(previous.number()).enumerator(), visit.enumerator());
            assertFalse(target.start().isAfter(r.config().start().plusDays(day(r, previous.number())).plusHours(9)));
        }
        assertMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"geopoint", "geotrace", "geoshape"})
    void anyLocationQuestionEnablesTravelAndOverridesItsMissingRate(String type) throws Exception {
        var f = fixture("<location/>", bind("location", type, ""), input("location"));
        var c = config(2, 1, "\"fields\":{\"location\":{\"missingRate\":1}}").withProblems(only("travel", .5));
        var r = run(f, c);
        assertEquals("", value(r.submissions().get(0), "location")); assertEquals(Set.of(2), targets(r, "travel"));
        assertEquals(1, r.plan().problems().size()); var p = r.plan().problems().get(0);
        assertEquals("location", p.field()); assertFalse(value(r.target(p), "location").isEmpty());
        if (!type.equals("geopoint")) assertEquals(value(r.target(p), "location"), p.value());
        assertEquals(key(r.submissions().get(0)), p.relatedKey());
        double[] first = FieldworkTestSupport.point(value(r.target(p), "location").split(";\\s*")[0]);
        assertTrue(FieldworkTestSupport.distance(r.visit(2).latitude(), r.visit(2).longitude(), first[0], first[1]) <= 30.16);
        assertMetadata(r);
    }
    @Test void travelRecordsAllAnsweredLocationsInFormOrderButNotIrrelevantLocations() throws Exception {
        var f = fixture("<shape_z/><hidden/><trace_a/><gps/>", bind("gps", "geopoint", "")
                + bind("trace_a", "geotrace", "") + bind("hidden", "geopoint", "relevant=\"false()\"")
                + bind("shape_z", "geoshape", ""), input("shape_z") + input("hidden") + input("trace_a") + input("gps"));
        var r = run(f, config(2, 1, "").withProblems(only("travel", .5)));
        assertEquals(List.of("shape_z", "trace_a", "gps"), r.plan().problems().stream().map(InjectedProblem::field).toList());
        for (var p : r.plan().problems()) {
            if (!p.field().equals("gps")) assertEquals(value(r.target(p), p.field()), p.value());
            assertEquals(key(r.submissions().get(0)), p.relatedKey());
        }
    }
    @Test void noLocationQuestionSkipsAllTravelAndGivesOneWarning() throws Exception {
        var f = singleTimed(0); var c = config(20, 10, ""); var clean = run(f, c);
        var r = run(f, c.withProblems(only("travel", .25)));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, "travel");
        String warning = r.plan().warnings().get(0).toLowerCase(Locale.ROOT);
        assertTrue(warning.contains("location") || warning.contains("geopoint") || warning.contains("gps")
                || warning.contains("geotrace") || warning.contains("geoshape"), "warning explains missing location questions");
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void areaTooSmallForTwentyKmSkipsTravelAndPreservesOrdinaryPlan() throws Exception {
        var f = ordinary(0, false, false, true);
        var c = config(2, 1, "\"area\":{\"minLatitude\":0,\"maxLatitude\":0.001,\"minLongitude\":0,\"maxLongitude\":0.001}");
        var clean = run(f, c); var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void travelCandidateAfterClosingIsSkippedAndWarnsAboutItsSubmission() throws Exception {
        var f = ordinary(9 * 3600, false, false, true); var c = config(2, 1, "");
        var clean = run(f, c); var r = run(f, c.withProblems(only("travel", .5)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void travelAtExactlyClosingIsAllowedButOneSecondLaterIsSkipped() throws Exception {
        var c = config(2, 1, "\"interviewsPerDay\":10");
        var probe = run(timedLocation(60), c);
        long gap = gap(probe.submissions().get(0), probe.submissions().get(1));
        int duration = (int) Duration.between(probe.submissions().get(0).start(), c.start().plusHours(9)).getSeconds()
                - (int) gap * 60;
        var atClosing = run(timedLocation(duration), c.withProblems(only("travel", .5)));
        assertEquals(Set.of(2), targets(atClosing, "travel"));
        assertEquals(c.start().plusHours(9), atClosing.submissions().get(1).start());
        var afterClosing = run(timedLocation(duration + 1), c.withProblems(only("travel", .5)));
        assertTrue(afterClosing.plan().problems().isEmpty()); assertWarning(afterClosing, null, 2);
        ordinary(afterClosing, 2);
    }
    private static LoadedForm timedLocation(int seconds) throws Exception {
        return fixture("<gps/>", bind("gps", "geopoint", "ct:minimum-seconds=\"" + seconds + "\""), input("gps"));
    }
    @Test void theInterviewAfterTravelBeginsTheNextDayEvenWithQuotaRemaining() throws Exception {
        var f = ordinary(0, false, false, true); var c = config(3, 1, "\"interviewsPerDay\":10"); int checked = 0;
        for (int seed = 50; seed < 75; seed++) {
            var r = run(f, c.withSeed(seed).withProblems(only("travel", 1.0 / 3)));
            if (!targets(r, "travel").equals(Set.of(2))) continue;
            checked++; assertEquals(0, day(r, 2)); assertEquals(1, day(r, 3));
            long delay = FieldworkTestSupport.wholeMinutes(c.start().plusDays(1), r.submissions().get(2).start());
            assertTrue(delay >= 0 && delay <= 60); ordinary(r, 3);
        }
        assertTrue(checked > 0);
    }
    @Test void travelAndOrdinaryPlanUseTheSameNumberBasedGapDraw() throws Exception {
        var f = ordinary(0, false, false, true); var c = config(80, 40, "\"interviewsPerDay\":2");
        var clean = run(f, c); var r = run(f, c.withProblems(only("travel", .5)));
        assertEquals(40, targets(r, "travel").size());
        for (int n : targets(r, "travel")) {
            assertEquals(gap(clean.submissions().get(n - 41), clean.submissions().get(n - 1)),
                    gap(r.submissions().get(n - 41), r.submissions().get(n - 1)));
        }
    }
}
