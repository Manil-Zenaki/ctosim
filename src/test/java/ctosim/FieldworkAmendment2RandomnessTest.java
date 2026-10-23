package ctosim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment3SearchSupport.items;
import static ctosim.FormWalkerAmendment3SearchSupport.selectControl;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Locale/time zone defaults and form engine state are JVM-wide")
class FieldworkAmendment2RandomnessTest extends FieldworkAmendment2Support {
    @Test void everyWalkAllStartsWithFreshUsedIdsAndReplacesItsWarningCount() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(10, ""));
        assertTrue(plan.visits().isEmpty());
        assertTrue(plan.warnings().isEmpty());
        var first = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        assertEquals(7, checkAllocation(first, POOLS));
        reuseWarning(plan, 7);
        for (int replay = 0; replay < 2; replay++) {
            sameRun(first, plan.walkAll());
            assertEquals(visits, plan.visits());
            assertEquals(warnings, plan.warnings());
        }
    }

    @Test void separatePlansSharingALoadedFormDoNotShareReceivedIds() throws Exception {
        var form = cascade();
        var config = fixedVillage(3, "");
        var first = new Fieldwork(form, config);
        var second = new Fieldwork(form, config);
        var expected = List.copyOf(first.walkAll());
        assertEquals(FIRST, new HashSet<>(answers(expected)));
        sameRun(expected, second.walkAll());
        reuseWarning(first, 0);
        reuseWarning(second, 0);
        sameRun(expected, first.walkAll());
    }

    @Test void freshLoadsReproduceCascadeResultsAcrossLocalesAndTimeZones() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(12, ""));
        var first = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        Locale oldLocale = Locale.getDefault();
        Locale oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale oldFormat = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var reloaded = new Fieldwork(FormLoader.load(xform, media), fixedVillage(12, ""));
            sameRun(first, reloaded.walkAll());
            assertEquals(visits, reloaded.visits());
            assertEquals(warnings, reloaded.warnings());
        } finally {
            TimeZone.setDefault(oldZone);
            Locale.setDefault(oldLocale);
            Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
            Locale.setDefault(Locale.Category.FORMAT, oldFormat);
        }
    }

    @Test void extendingThePlanKeepsTheEarlierIdDrawsIncludingDrawsAfterExhaustion() throws Exception {
        var form = cascade();
        var config = fixedVillage(8, "");
        var shortPlan = new Fieldwork(form, config);
        var longPlan = new Fieldwork(form, config.withSubmissions(19));
        assertEquals(answers(shortPlan.walkAll()), answers(longPlan.walkAll()).subList(0, 8));
        reuseWarning(shortPlan, 5);
        reuseWarning(longPlan, 16);
    }

    @Test void unrelatedAnswerDrawsDoNotChangeIdsWhenTheOfferedSetsAreTheSame() throws Exception {
        var plain = cascade();
        var noisy = attached("<noise/><village/><noise2/><answer/>",
                bind("noise", "int", "") + bind("village", "string", "required=\"true()\"")
                        + bind("noise2", "string", "") + bind("answer", "string", ""),
                input("noise") + selectControl("select1", "village", "", items("1", "2", "3", "4"))
                        + input("noise2") + control(DYNAMIC, "code"));
        var config = fixedVillage(20, "");
        assertEquals(answers(new Fieldwork(plain, config).walkAll()),
                answers(new Fieldwork(noisy, config).walkAll()), "ID draws depend on seed and submission number");
    }

    @Test void unusedChoicesAreDrawnUniformlyAndDifferentSeedsCanChangeTheirOrder() throws Exception {
        var form = cascade();
        var config = fixedVillage(2, "");
        var seeds = new Random(139702L);
        var firstCounts = new HashMap<String, Integer>();
        var pairCounts = new HashMap<List<String>, Integer>();
        int draws = 360;
        for (int i = 0; i < draws; i++) {
            var plan = new Fieldwork(form, config.withSeed(seeds.nextLong()));
            var ids = answers(plan.walkAll());
            assertTrue(FIRST.containsAll(ids));
            assertNotEquals(ids.get(0), ids.get(1));
            firstCounts.merge(ids.get(0), 1, Integer::sum);
            pairCounts.merge(ids, 1, Integer::sum);
        }
        assertTrue(pairCounts.size() > 1, "different seeds must be able to change the allocation");
        for (String first : FIRST) {
            assertUniformBin(firstCounts.getOrDefault(first, 0), draws, 1.0 / 3, "first unused ID " + first);
            for (String second : FIRST) if (!first.equals(second)) {
                assertUniformBin(pairCounts.getOrDefault(List.of(first, second), 0), draws, 1.0 / 6,
                        "ordered unused pair " + first + ", " + second);
            }
        }
    }

    @Test void exhaustedListsDrawUniformlyFromAllEligibleChoicesDespiteOrdinaryWeights() throws Exception {
        // The same three choices are offered throughout. After the first three draws all are used.
        var config = idConfig(903, "\"fieldworkDays\":3650,\"fields\":{\"village\":{\"value\":\"1\"},"
                + "\"answer\":{\"weights\":{\"0007\":1,\"É02\":0,\"A03\":0}}}");
        var plan = new Fieldwork(cascade(), config);
        var ids = answers(plan.walkAll());
        assertEquals(FIRST, new HashSet<>(ids.subList(0, 3)));
        var counts = new HashMap<String, Integer>();
        for (String id : ids.subList(3, ids.size())) {
            assertTrue(FIRST.contains(id));
            counts.merge(id, 1, Integer::sum);
        }
        for (String id : FIRST) assertUniformBin(counts.getOrDefault(id, 0), 900, 1.0 / 3, "reused ID " + id);
        reuseWarning(plan, 900);
    }
}
