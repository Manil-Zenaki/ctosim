package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment3SearchSupport.items;
import static ctosim.FormWalkerAmendment3SearchSupport.selectControl;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment2CascadeTest extends FieldworkAmendment2Support {
    @ParameterizedTest @ValueSource(strings = {"", "2"})
    void idsComeFromTheAnsweredVillageRatherThanTheOpeningValue(String initial) throws Exception {
        var plan = new Fieldwork(cascade(DYNAMIC, initial, "", "code"), fixedVillage(3, ""));
        var submissions = plan.walkAll();
        assertEquals(FIRST, new HashSet<>(answers(submissions)));
        deferredVisits(plan, 3);
        reuseWarning(plan, 0);
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 11})
    void anIdIsReusedOnlyAfterAllCurrentlyOfferedIdsHaveBeenReceived(int n) throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(n, ""));
        int reused = checkAllocation(plan.walkAll(), POOLS);
        assertEquals(Math.max(0, n - FIRST.size()), reused);
        deferredVisits(plan, n);
        reuseWarning(plan, reused);
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 9})
    void aSingletonFilteredListCountsEverySubmissionAfterTheFirstAsAReuse(int n) throws Exception {
        var form = cascade("search('cascade-overlap', 'matches', 'village', /data/village)", "", "", "code");
        var plan = new Fieldwork(form, idConfig(n, "\"fields\":{\"village\":{\"value\":\"3\"}}"));
        assertEquals(java.util.Collections.nCopies(n, "SOLO"), answers(plan.walkAll()));
        deferredVisits(plan, n);
        reuseWarning(plan, n - 1);
    }

    @ParameterizedTest @ValueSource(strings = {
            "search('cascade-households', 'matches', 'village', /data/village)",
            "search('cascade-households', 'matches', 'village', concat('', /data/village))",
            "search('cascade-households', 'matches', 'village', /data/village, 'active', 'yes')",
            "search('cascade-households', 'matches', 'district', 'north', 'village', /data/village)",
            "search('cascade-households', 'matches', 'village', /data/village, 'village', /data/village)",
            "minimal search(\"cascade-households\", \"matches\", \"village\", /data/village) quick"
    })
    void eitherNonliteralFilterArgumentActivatesAllocationAtTheQuestion(String appearance) throws Exception {
        var plan = new Fieldwork(cascade(appearance, "", "", "code"), fixedVillage(3, ""));
        assertEquals(FIRST, new HashSet<>(answers(plan.walkAll())));
        deferredVisits(plan, 3);
        reuseWarning(plan, 0);
    }

    @Test void aDistrictVillageHouseholdCascadeUsesBothEarlierAnswers() throws Exception {
        var form = attached("<district/><village/><answer/>",
                bind("district", "string", "required=\"true()\"")
                        + bind("village", "string", "required=\"true()\"") + bind("answer", "string", ""),
                selectControl("select1", "district", "", items("north", "south"))
                        + selectControl("select1", "village",
                                "search('cascade-villages', 'matches', 'district', /data/district)", items("code"))
                        + control("search('cascade-households', 'matches', 'village', /data/village, 'district', /data/district)", "code"));
        var plan = new Fieldwork(form, idConfig(80, ""));
        var submissions = plan.walkAll();
        var districts = new HashSet<String>();
        for (var submission : submissions) {
            String district = value(submission, "district");
            districts.add(district);
            assertTrue((district.equals("north") ? Set.of("1", "2") : Set.of("3", "4"))
                    .contains(value(submission, "village")));
        }
        assertEquals(Set.of("north", "south"), districts, "fixed-seed run exercises both cascade branches");
        int reused = checkAllocation(submissions, POOLS);
        deferredVisits(plan, 80);
        reuseWarning(plan, reused);
    }

    @Test void changingListsExcludeIdsUsedInAnyEarlierVillage() throws Exception {
        var form = cascade("search('cascade-overlap', 'matches', 'village', /data/village)", "", "", "code");
        var config = idConfig(60, "\"fields\":{\"village\":{\"weights\":{\"1\":1,\"2\":1,\"3\":0,\"4\":0}}}");
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        var villages = new HashSet<String>();
        for (var submission : submissions) villages.add(value(submission, "village"));
        assertEquals(Set.of("1", "2"), villages);
        int reused = checkAllocation(submissions, OVERLAP);
        assertEquals(57, reused, "three globally distinct IDs, even though SHARED is offered in both villages");
        reuseWarning(plan, reused);
    }

    @Test void staticChoicesJoinLoadedChoicesAndAreTrackedAcrossAllVillages() throws Exception {
        var form = cascade(DYNAMIC, "", "", "7", "code", "8");
        var plan = new Fieldwork(form, idConfig(70,
                "\"fields\":{\"village\":{\"weights\":{\"1\":1,\"2\":1,\"3\":0,\"4\":0}}}"));
        var pools = Map.of("1", Set.of("7", "8", "0007", "É02", "A03"),
                "2", Set.of("7", "8", "B01", "B02", "B03"));
        var submissions = plan.walkAll();
        int reused = checkAllocation(submissions, pools);
        assertEquals(8, new HashSet<>(answers(submissions)).size());
        reuseWarning(plan, reused);
    }

    @Test void repeatedCsvRowsEmptyCodesAndThePlaceholderDoNotBecomeExtraIds() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(4, ""));
        var answers = answers(plan.walkAll());
        assertEquals(FIRST, new HashSet<>(answers.subList(0, 3)));
        assertTrue(FIRST.contains(answers.get(3)));
        reuseWarning(plan, 1);
    }

    @Test void specialValuesAreExcludedFromLoadedAndStaticChoicesEvenAtSpecialRateOne() throws Exception {
        var config = SimulationConfig.parse("""
                {"seed":731991,"submissions":5,"id":"answer","missingRate":0,"specialRate":1,
                 "specialValues":["0007","99","-99","7"],"fields":{"village":{"value":"1"}}}
                """, "cascade-special.json");
        var plan = new Fieldwork(cascade(DYNAMIC, "", "", "7", "code", "8"), config);
        int reused = checkAllocation(plan.walkAll(), Map.of("1", Set.of("É02", "A03", "8")));
        assertEquals(2, reused);
        reuseWarning(plan, reused);
    }

    @Test void anEmptySpecialValueListRestoresThoseCodesAsEligibleIds() throws Exception {
        var plan = new Fieldwork(cascade(DYNAMIC, "", "", "7", "code", "99"),
                idConfig(6, "\"specialValues\":[],\"fields\":{\"village\":{\"value\":\"1\"}}"));
        assertEquals(Set.of("0007", "É02", "A03", "99", "-99", "7"), new HashSet<>(answers(plan.walkAll())));
        deferredVisits(plan, 6);
        reuseWarning(plan, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"", "required=\"true()\""})
    void globalMissingRateOneCannotBlankAnOptionalOrRequiredCascadeId(String required) throws Exception {
        var config = SimulationConfig.parse("""
                {"seed":731991,"submissions":3,"id":"answer","missingRate":1,"specialRate":0,
                 "fields":{"village":{"value":"1"}}}
                """, "cascade-missing.json");
        var plan = new Fieldwork(cascade(DYNAMIC, "", required, "code"), config);
        assertEquals(FIRST, new HashSet<>(answers(plan.walkAll())));
        reuseWarning(plan, 0);
    }

    @Test void aFieldMissingRateOfOneAlsoCannotBlankTheCascadeId() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(3, "\"missingRate\":1"));
        assertEquals(FIRST, new HashSet<>(answers(plan.walkAll())));
        reuseWarning(plan, 0);
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"weights\":{\"0007\":1,\"É02\":0,\"A03\":0}",
            "\"min\":10,\"max\":20"
    })
    void weightsAndNumericRangesDoNotDisableDeferredSelectIdAllocation(String override) throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(3, override));
        assertEquals(FIRST, new HashSet<>(answers(plan.walkAll())));
        deferredVisits(plan, 3);
        reuseWarning(plan, 0);
    }

    @Test void uniquenessContinuesAcrossEnumeratorsAndWorkdays() throws Exception {
        var config = idConfig(12, "\"enumerators\":4,\"interviewsPerDay\":1,"
                + "\"fields\":{\"village\":{\"value\":\"1\"}}");
        var plan = new Fieldwork(cascade(), config);
        int reused = checkAllocation(plan.walkAll(), POOLS);
        assertEquals(9, reused);
        assertEquals(4, plan.visits().stream().map(Visit::enumerator).distinct().count());
        assertTrue(plan.visits().stream().map(v -> day(config, v)).distinct().count() > 1);
        reuseWarning(plan, reused);
    }
}
