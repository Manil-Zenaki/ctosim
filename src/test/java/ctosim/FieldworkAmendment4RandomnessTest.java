package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Locale, time zone and form engine state are JVM-wide")
class FieldworkAmendment4RandomnessTest extends FieldworkAmendment4Support {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eachNewDayDrawsItsPlaceAgainWithTheViableChoiceWeights(boolean weighted) throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        var form = cascade(VILLAGE_SEARCH, ID_SEARCH, false, "");
        var config = config4(2, 1, 1, LOCATED + (weighted
                ? ",\"fields\":{\"village\":{\"weights\":{\"A\":1,\"B\":3}}}" : ""));
        int draws = 320, firstA = 0, secondA = 0, changes = 0;
        var seeds = new Random(134044781L);
        for (int i = 0; i < draws; i++) {
            var plan = new Fieldwork(form, config.withSeed(seeds.nextLong()));
            var rows = plan.walkAll();
            String first = value(rows.get(0), "village"), second = value(rows.get(1), "village");
            if (first.equals("A")) firstA++;
            if (second.equals("A")) secondA++;
            if (!first.equals(second)) changes++;
            assertEquals(1, day(config, plan.visits().get(1)));
        }
        double probability = weighted ? 0.25 : 0.5;
        assertUniformBin(firstA, draws, probability, "first day's village A");
        assertUniformBin(secondA, draws, probability, "second day's village A");
        assertTrue(changes > 0, "a new day must not keep yesterday's still-viable village automatically");
    }

    @Test void aDayReachedBecauseOfLongInterviewsAlsoStartsAFreshPlace() throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        var form = cascade(VILLAGE_SEARCH, ID_SEARCH, false, "ct:minimum-seconds=\"21600\"");
        var config = config4(3, 1, 100, "");
        int changes = 0;
        var seeds = new Random(13400194L);
        for (int i = 0; i < 40; i++) {
            var plan = new Fieldwork(form, config.withSeed(seeds.nextLong()));
            var rows = plan.walkAll();
            assertEquals(0, day(config, plan.visits().get(1)));
            assertEquals(1, day(config, plan.visits().get(2)));
            assertEquals(value(rows.get(0), "village"), value(rows.get(1), "village"));
            if (!value(rows.get(0), "village").equals(value(rows.get(2), "village"))) changes++;
        }
        assertTrue(changes > 0, "the day must reset its place even though the quota was not reached");
    }

    @Test void repeatedWalkAllCallsResetPlacesAndReproduceMovesTimesGpsAndWarnings() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var plan = new Fieldwork(cascade(), config4(10, 2, 3, LOCATED + "," + FORCE_A));
        var expected = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        for (int run = 0; run < 2; run++) {
            sameRun(expected, plan.walkAll());
            assertEquals(visits, plan.visits());
            assertEquals(warnings, plan.warnings());
        }
    }

    @Test void independentPlansDoNotShareRememberedPlacesOrReceivedIds() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var form = cascade();
        var config = config4(6, 1, 10, LOCATED + "," + FORCE_A);
        var first = new Fieldwork(form, config);
        var second = new Fieldwork(form, config);
        var expected = List.copyOf(first.walkAll());
        sameRun(expected, second.walkAll());
        assertEquals(first.visits(), second.visits());
    }

    @Test void increasingTheSubmissionCountPreservesTheExistingLocatedPlanPrefix() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var form = cascade();
        var config = config4(3, 1, 10, LOCATED + "," + FORCE_A);
        var shortPlan = new Fieldwork(form, config);
        var longPlan = new Fieldwork(form, config.withSubmissions(6));
        sameRun(shortPlan.walkAll(), longPlan.walkAll().subList(0, 3));
        assertEquals(shortPlan.visits(), longPlan.visits().subList(0, 3));
    }

    @Test void freshLoadsReproduceTheLocatedPlanUnderDifferentLocalesAndTimeZones() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var config = config4(8, 2, 3, LOCATED);
        var plan = new Fieldwork(cascade(), config);
        var expected = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        Locale locale = Locale.getDefault(), display = Locale.getDefault(Locale.Category.DISPLAY),
                format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var fresh = new Fieldwork(FormLoader.load(xform, media), config);
            sameRun(expected, fresh.walkAll());
            assertEquals(visits, fresh.visits());
            assertEquals(warnings, fresh.warnings());
        } finally {
            Locale.setDefault(locale);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }

    @Test void locatedPointDrawsDependOnSubmissionNumberRatherThanEnumeratorOrDay() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var form = cascade();
        var single = new Fieldwork(form, config4(8, 1, 4, LOCATED));
        var spread = new Fieldwork(form, config4(8, 3, 1, LOCATED));
        single.walkAll();
        spread.walkAll();
        for (int i = 0; i < 8; i++) {
            assertEquals(single.visits().get(i).latitude(), spread.visits().get(i).latitude());
            assertEquals(single.visits().get(i).longitude(), spread.visits().get(i).longitude());
        }
    }

    @Test void differentSeedsAndSubmissionNumbersVaryHouseholdPointsWithinTheLocatedVillage() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var form = cascade();
        var firstPoints = new HashSet<List<Double>>();
        for (long seed : List.of(1L, 2L, -1L, 731991L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var plan = new Fieldwork(form, config4(4, 1, 4, LOCATED).withSeed(seed));
            plan.walkAll();
            var points = new HashSet<List<Double>>();
            for (var visit : plan.visits()) {
                near(visit, 10, 20);
                points.add(List.of(visit.latitude(), visit.longitude()));
            }
            assertTrue(points.size() > 1, "separate households must draw separate points");
            var first = plan.visits().get(0);
            firstPoints.add(List.of(first.latitude(), first.longitude()));
        }
        assertTrue(firstPoints.size() > 1, "point draws respond to the seed");
    }

    @Test void standaloneWalksStillUseTheSuppliedVisitRatherThanCsvVillageCoordinates() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var form = cascade();
        var config = config4(2, 1, 4, LOCATED);
        var walker = new FormWalker(form, config);
        var visit = new Visit(1, START, -30, -80, null);
        var expected = walker.walk(1, visit);
        gpsFollowsVisit(expected, visit, "before", "after", "trace_before", "shape_after");
        new Fieldwork(form, config).walkAll();
        same(expected, walker.walk(1, visit));
    }
}
