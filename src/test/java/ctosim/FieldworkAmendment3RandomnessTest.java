package ctosim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Locale/time zone defaults and form engine state are JVM-wide")
class FieldworkAmendment3RandomnessTest extends FieldworkAmendment3Support {
    @Test void unweightedViableChoicesAreUniformDespiteUnequalNumbersOfFreeDescendants() throws Exception {
        assertFirstVillageDistribution("", 0.5);
    }

    @Test void upperWeightsAreRestrictedToViableOfferedChoicesAndRetainTheirRelativeRatio() throws Exception {
        assertFirstVillageDistribution("\"weights\":{\"A\":1,\"B\":3,\"X\":1000000,\"GHOST\":1000000}", 0.25);
    }

    @Test void zeroTotalWeightOnTheViableSubsetUsesTheExistingUniformFallback() throws Exception {
        assertFirstVillageDistribution("\"weights\":{\"X\":1,\"A\":0,\"B\":0}", 0.5);
    }

    private void assertFirstVillageDistribution(String override, double probabilityA) throws Exception {
        // A has one ID, B has nineteen. First choices must not be proportional to capacity.
        var form = simple("search('a3-villages')", "search('a3-rates', 'matches', 'village', /data/village)", "", "code");
        var config = idConfig(1, override.isEmpty() ? "" : "\"fields\":{\"village\":{" + override + "}}");
        var seeds = new Random(30317839L);
        var counts = new HashMap<String, Integer>();
        int draws = 640;
        for (int i = 0; i < draws; i++) {
            var submission = new Fieldwork(form, config.withSeed(seeds.nextLong())).walkAll().get(0);
            String village = value(submission, "village");
            assertTrue(Set.of("A", "B").contains(village), "only villages with ordinary IDs are viable");
            counts.merge(village, 1, Integer::sum);
        }
        assertUniformBin(counts.getOrDefault("A", 0), draws, probabilityA, "first viable village A");
        assertUniformBin(counts.getOrDefault("B", 0), draws, 1 - probabilityA, "first viable village B");
    }

    @Test void weightsAreReevaluatedAsBranchesRunOutOfIds() throws Exception {
        var config = idConfig(5, "\"fields\":{\"village\":{\"weights\":{\"A\":100,\"B\":1,\"X\":999999}}}");
        var form = simple("search('a3-villages', 'matches', 'district', 'D1')", HOUSEHOLDS, "", "code");
        var plan = new Fieldwork(form, config);
        assertSteered(plan.walkAll(), Map.of("A", POOLS.get("A"), "B", POOLS.get("B")), false);
        assertNoPlanIdWarning(plan, 5);
    }

    @Test void higherUpperQuestionsAlsoRestrictWeightsToTheirViableChoices() throws Exception {
        var config = idConfig(8, """
                "fields":{"region":{"weights":{"R1":1,"R2":0,"R0":1000000}},
                          "district":{"weights":{"D1":1,"D2":0,"D3":1,"D0":1000000}}}
                """ );
        var plan = new Fieldwork(deep(false), config);
        var submissions = plan.walkAll();
        assertSteered(submissions, POOLS, true);
        assertEquals(List.of("R1", "R1", "R1", "R1", "R1", "R1", "R1", "R2"),
                column(submissions, "region"));
        assertEquals(List.of("D1", "D1", "D1", "D1", "D1", "D2", "D2", "D3"),
                column(submissions, "district"));
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void eachWalkAllStartsWithFreshViabilityAndReproducesCompleteSubmissions() throws Exception {
        var plan = new Fieldwork(deep(false), idConfig(8, ""));
        var first = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        assertSteered(first, POOLS, true);
        for (int i = 0; i < 2; i++) {
            sameRun(first, plan.walkAll());
            assertEquals(visits, plan.visits());
            assertEquals(warnings, plan.warnings());
        }
    }

    @Test void independentPlansSharingTheSameLoadedFormDoNotShareViabilityHistory() throws Exception {
        var form = deep(false);
        var config = idConfig(8, "");
        var first = new Fieldwork(form, config);
        var second = new Fieldwork(form, config);
        var expected = List.copyOf(first.walkAll());
        assertSteered(expected, POOLS, true);
        sameRun(expected, second.walkAll());
        sameRun(expected, first.walkAll());
        assertNoPlanIdWarning(second, 8);
    }

    @Test void freshLoadsReproduceSteeredAnswersUnderOtherLocalesAndTimeZones() throws Exception {
        var plan = new Fieldwork(deep(true), idConfig(8, ""));
        var expected = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        Locale locale = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var fresh = new Fieldwork(FormLoader.load(xform, media), idConfig(8, ""));
            sameRun(expected, fresh.walkAll());
            assertEquals(visits, fresh.visits());
            assertEquals(plan.warnings(), fresh.warnings());
        } finally {
            TimeZone.setDefault(zone);
            Locale.setDefault(locale);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    @Test void increasingSubmissionCountKeepsEarlierSteeredChoices() throws Exception {
        var form = deep(false);
        var config = idConfig(4, "");
        var shortPlan = new Fieldwork(form, config);
        var longPlan = new Fieldwork(form, config.withSubmissions(8));
        sameRun(shortPlan.walkAll(), longPlan.walkAll().subList(0, 4));
    }

    @Test void differentSeedsCanChangeTheSteeredRouteWhilePreservingUniqueness() throws Exception {
        var form = deep(false);
        var sequences = new HashSet<List<String>>();
        for (long seed : List.of(1L, 2L, 3L, 7L, 193L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var submissions = new Fieldwork(form, idConfig(8, "").withSeed(seed)).walkAll();
            assertSteered(submissions, POOLS, true);
            sequences.add(column(submissions, "village"));
        }
        assertTrue(sequences.size() > 1, "route draws respond to the configured seed");
    }

    @Test void unrelatedAnswerDrawsDoNotChangeSteeringWithTheSameSeedAndNumber() throws Exception {
        var plain = simple();
        var noisy = attached("<noise/><village/><another/><answer/>", bind("noise", "int", "")
                        + bind("village", "string", "") + bind("another", "string", "") + bind("answer", "string", ""),
                input("noise") + select("village", "search('a3-villages')", "code")
                        + input("another") + control(HOUSEHOLDS, "code"));
        var config = idConfig(8, "");
        var expected = new Fieldwork(plain, config).walkAll();
        var actual = new Fieldwork(noisy, config).walkAll();
        assertEquals(column(expected, "village"), column(actual, "village"));
        assertEquals(column(expected, "answer"), column(actual, "answer"));
    }
}
