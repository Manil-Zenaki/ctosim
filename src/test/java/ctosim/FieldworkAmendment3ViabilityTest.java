package ctosim;

import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment3ViabilityTest extends FieldworkAmendment3Support {
    @ParameterizedTest @ValueSource(ints = {1, 7, 8})
    void everyReachableIdCanBeUsedBeforeAnyVillageIsRevisitedAfterExhaustion(int n) throws Exception {
        var plan = new Fieldwork(simple(), idConfig(n, ""));
        var submissions = plan.walkAll();
        assertSteered(submissions, POOLS, false);
        assertEquals(n, new HashSet<>(column(submissions, "answer")).size());
        if (n == 8) assertEquals(allIds(POOLS), new HashSet<>(column(submissions, "answer")));
        assertNoPlanIdWarning(plan, n);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void viabilityPropagatesThroughThreeUpperQuestionsForEitherFilterPosition(boolean filterText) throws Exception {
        var plan = new Fieldwork(deep(filterText), idConfig(8, ""));
        var submissions = plan.walkAll();
        assertSteered(submissions, POOLS, true);
        assertEquals(allIds(POOLS), new HashSet<>(column(submissions, "answer")));
        assertNoPlanIdWarning(plan, 8);
    }

    @ParameterizedTest @ValueSource(strings = {
            "search('a3-households', 'matches', 'village', /data/village, 'cohort', 'main')",
            "search('a3-households', 'matches', 'cohort', 'main', 'village', /data/village)",
            "minimal search(\"a3-households\", \"matches\", \"village\", /data/village, \"cohort\", \"main\") quick",
            "search('a3-households', 'matches', 'village', ../village, 'cohort', 'main')"
    })
    void aSingleQuestionReferenceRecognizesTheUpperQuestion(String appearance) throws Exception {
        var plan = new Fieldwork(simple("search('a3-villages')", appearance, "", "code"),
                idConfig(8, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertSteered(plan.walkAll(), POOLS, false);
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void aVillageWithOnlySpecialIdsIsNotViableEvenWhenSpecialRateIsOne() throws Exception {
        var config = SimulationConfig.parse("""
                {"seed":731991,"submissions":8,"id":"answer","missingRate":0,"specialRate":1,
                 "fields":{"village":{"weights":{"Z":1000000,"A":1,"B":1,"C":1,"E":1,"X":1000000}}}}
                """, "steered-specials.json");
        var plan = new Fieldwork(simple(), config);
        assertSteered(plan.walkAll(), POOLS, false);
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void changingSpecialValuesChangesWhichBranchesAreViable() throws Exception {
        var pools = new HashMap<>(POOLS);
        pools.put("Z", Set.of("99", "-99"));
        var plan = new Fieldwork(simple(), idConfig(10, "\"specialValues\":[]"));
        assertSteered(plan.walkAll(), pools, false);
        assertNoPlanIdWarning(plan, 10);
    }

    @Test void anIdReceivedInOneVillageAlsoReducesViabilityInAnotherVillage() throws Exception {
        // Both branches offer static ID 8: capacity is four distinct IDs, not five.
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                select("village", "search('a3-villages', 'matches', 'code', 'C')", "code", "7")
                        + control(HOUSEHOLDS, "code", "8"));
        var pools = Map.of("C", Set.of("SHARED", "C1", "8"), "7", Set.of("STATIC", "8"));
        var plan = new Fieldwork(form, idConfig(4, ""));
        assertSteered(plan.walkAll(), pools, false);
        assertNoPlanIdWarning(plan, 4);
    }

    @Test void staticUpperChoicesAndStaticIdChoicesParticipateInViability() throws Exception {
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                select("village", "search('a3-villages', 'matches', 'code', 'X')", "code", "7")
                        + control(HOUSEHOLDS, "8", "code"));
        var pools = Map.of("X", Set.of("8"), "7", Set.of("8", "STATIC"));
        var plan = new Fieldwork(form, idConfig(2, "\"fields\":{\"village\":{\"weights\":{\"X\":1,\"7\":0}}}"));
        var submissions = plan.walkAll();
        assertEquals(List.of("X", "7"), column(submissions, "village"));
        assertEquals(List.of("8", "STATIC"), column(submissions, "answer"));
        assertSteered(submissions, pools, false);
    }

    @Test void onlyChoicesCurrentlyOfferedToTheUpperQuestionCanBeSelected() throws Exception {
        var form = simple("search('a3-villages', 'matches', 'district', 'D1')", HOUSEHOLDS, "", "code");
        var pools = Map.of("A", POOLS.get("A"), "B", POOLS.get("B"));
        var plan = new Fieldwork(form, idConfig(5,
                "\"fields\":{\"village\":{\"weights\":{\"GHOST\":1000000,\"C\":1000000,\"A\":1,\"B\":1}}}"));
        assertSteered(plan.walkAll(), pools, false);
        assertNoPlanIdWarning(plan, 5);
    }

    @ParameterizedTest @ValueSource(strings = {"main", "other"})
    void viabilityEvaluatesOtherSearchArgumentsWithTheirCurrentAnswers(String cohort) throws Exception {
        var form = attached("<cohort/><village/><answer/>", bind("cohort", "string", "")
                        + bind("village", "string", "") + bind("answer", "string", ""),
                input("cohort") + select("village", "search('a3-villages')", "code")
                        + control("search('a3-households', 'matches', 'village', /data/village, 'cohort', /data/cohort)", "code"));
        var pools = cohort.equals("main") ? POOLS : Map.of("A", Set.of("U1"), "Z", Set.of("U2"));
        int n = allIds(pools).size();
        var plan = new Fieldwork(form, idConfig(n, "\"fields\":{\"cohort\":{\"value\":\"" + cohort + "\"}}"));
        var submissions = plan.walkAll();
        assertSteered(submissions, pools, false);
        for (var submission : submissions) assertEquals(cohort, value(submission, "cohort"));
        assertNoPlanIdWarning(plan, n);
    }

    @ParameterizedTest @ValueSource(strings = {"", "required=\"true()\""})
    void globalMissingRateCannotBlankAnUpperQuestionWithViableChoices(String required) throws Exception {
        var form = simple("search('a3-villages')", HOUSEHOLDS, required, "code");
        var config = SimulationConfig.parse("""
                {"seed":731991,"submissions":8,"id":"answer","missingRate":1,"specialRate":0}
                """, "steered-missing.json");
        var plan = new Fieldwork(form, config);
        assertSteered(plan.walkAll(), POOLS, false);
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void fieldMissingRatesAreSuppressedAtEveryLevelOfTheCascade() throws Exception {
        var plan = new Fieldwork(deep(false), idConfig(8, """
                "fields":{"region":{"missingRate":1},"district":{"missingRate":1},
                          "village":{"missingRate":1},"answer":{"missingRate":1}}
                """));
        assertSteered(plan.walkAll(), POOLS, true);
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void aRangeOnlyUpperOverrideDoesNotDisableSteering() throws Exception {
        var plan = new Fieldwork(simple(), idConfig(8,
                "\"fields\":{\"village\":{\"min\":0,\"max\":1,\"missingRate\":1}}"));
        assertSteered(plan.walkAll(), POOLS, false);
    }

    @Test void steeringSharesIdHistoryAcrossEnumeratorsAndDays() throws Exception {
        var config = idConfig(8, "\"enumerators\":2,\"interviewsPerDay\":1");
        var plan = new Fieldwork(deep(false), config);
        assertSteered(plan.walkAll(), POOLS, true);
        assertEquals(2, plan.visits().stream().map(Visit::enumerator).distinct().count());
        assertEquals(4, plan.visits().stream().map(v -> day(config, v)).distinct().count());
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void aSingletonViableChoiceIsAnsweredEvenWithAMissingRateOfOne() throws Exception {
        var form = simple("search('a3-villages', 'matches', 'code', 'C')",
                "search('a3-households', 'matches', 'village', /data/village, 'code', 'C1')", "", "code");
        var plan = new Fieldwork(form, idConfig(1, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        var submission = plan.walkAll().get(0);
        assertEquals("C", value(submission, "village"));
        assertEquals("C1", value(submission, "answer"));
        assertNoPlanIdWarning(plan, 1);
    }

    @Test void hypotheticalViabilityChecksDoNotReceiveOrReserveIds() throws Exception {
        // Both villages offer precisely the same one static ID. Looking ahead through either
        // branch must not consume it before the actual ID question is answered.
        var form = simple("search('a3-villages', 'matches', 'district', 'D1')",
                "search('a3-households', 'matches', 'village', /data/village, 'cohort', 'absent')", "", "code", "8");
        var plan = new Fieldwork(form, idConfig(1, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        var submission = plan.walkAll().get(0);
        assertTrue(Set.of("A", "B").contains(value(submission, "village")));
        assertEquals("8", value(submission, "answer"));
        assertNoPlanIdWarning(plan, 1);
    }
}
