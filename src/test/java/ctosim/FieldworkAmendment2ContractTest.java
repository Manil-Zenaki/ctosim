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
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment2ContractTest extends FieldworkAmendment2Support {
    @ParameterizedTest @ValueSource(strings = {
            "search('cascade-households', 'matches', 'village', '1')",
            "search('cascade-households', 'matches', 'village', '1', 'active', 'yes')",
            "search(\"cascade-households\", \"matches\", \"village\", \"1\")"
    })
    void literalFiltersStillAllocateAndCycleVisitIdsUnderAmendmentOne(String appearance) throws Exception {
        assertPool(cascade(appearance, "", "", "code"), fixedVillage(8, ""), FIRST, true);
    }

    @Test void aFixedValueOverrideKeepsVisitAllocationEvenForAnAnswerDependentSearch() throws Exception {
        assertPool(cascade(), fixedVillage(4, "\"value\":\"0007\""), Set.of("0007"), true);
    }

    @Test void explicitTextsKeepVisitAllocationIncludingExplicitSpecialCodes() throws Exception {
        assertPool(cascade(), fixedVillage(5, "\"texts\":[\"0007\",\"99\",\"0007\",\"\"]"),
                Set.of("0007", "99"), true);
    }

    @Test void aFixedSpecialCodeStillOverridesTheDeferredChoiceRule() throws Exception {
        assertPool(cascade(), fixedVillage(3, "\"value\":\"99\""), Set.of("99"), true);
    }

    @Test void aDynamicSearchThatIsNotTheConfiguredIdKeepsOrdinaryMissingBehavior() throws Exception {
        var form = cascade();
        var config = settings("\"submissions\":3,\"fields\":{\"village\":{\"value\":\"1\"},"
                + "\"answer\":{\"missingRate\":1}}");
        var plan = new Fieldwork(form, config);
        assertEquals(List.of("", "", ""), answers(plan.walkAll()));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void walkingAVisitAloneUsesOrdinaryWeightsAndDoesNotRememberEarlierIds() throws Exception {
        var walker = new FormWalker(cascade(), fixedVillage(3,
                "\"weights\":{\"0007\":1,\"É02\":0,\"A03\":0}"));
        var visit = visit(1);
        var first = walker.walk(1, visit);
        assertEquals("0007", value(first, "answer"));
        assertEquals("0007", value(walker.walk(2, visit), "answer"));
        assertEquals("0007", value(walker.walk(3, visit), "answer"));
        same(first, walker.walk(1, visit));
    }

    @Test void walkingAVisitAloneStillAppliesTheOrdinaryMissingRate() throws Exception {
        var walker = new FormWalker(cascade(), fixedVillage(3, "\"missingRate\":1"));
        assertEquals("", value(walker.walk(1, visit(1)), "answer"));
    }

    @Test void walkingByNumberAloneRemainsOrdinaryAfterAPlanUsedTheSameLoadedForm() throws Exception {
        var form = cascade();
        var config = fixedVillage(3, "\"missingRate\":1");
        var walker = new FormWalker(form, config);
        var before = walker.walk(2);
        assertEquals("", value(before, "answer"));
        var plan = new Fieldwork(form, config);
        assertEquals(FIRST, new HashSet<>(answers(plan.walkAll())));
        same(before, walker.walk(2));
        assertEquals("", value(new FormWalker(form, config).walk(2, plan.visits().get(1)), "answer"));
    }

    @Test void oneAggregateWarningCountsReusedSubmissionsRatherThanDistinctRepeatedIds() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(17, ""));
        checkAllocation(plan.walkAll(), POOLS);
        reuseWarning(plan, 14);
        assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add("changed"));
    }

    @Test void theReuseWarningFollowsTheFieldworkOverflowWarning() throws Exception {
        var config = idConfig(8, "\"enumerators\":1,\"fieldworkDays\":1,\"interviewsPerDay\":2,"
                + "\"fields\":{\"village\":{\"value\":\"1\"}}");
        var plan = new Fieldwork(cascade(), config);
        checkAllocation(plan.walkAll(), POOLS);
        assertEquals(2, plan.warnings().size());
        warning(plan.warnings().subList(0, 1), null, 4, 1);
        warning(plan.warnings().subList(1, 2), "answer", 5);
    }

    @Test void reuseWarningsBelongToThePlanRatherThanIndividualSubmissions() throws Exception {
        var plan = new Fieldwork(cascade(), fixedVillage(8, ""));
        for (var submission : plan.walkAll()) {
            assertTrue(submission.warnings().isEmpty(), submission.warnings().toString());
        }
        reuseWarning(plan, 5);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "wrong-column"})
    void anUnreadableFilteredFileStillAllocatesUniquelyFromItsStaticChoices(String source) throws Exception {
        var form = cascade("search('" + source + "', 'matches', 'village', /data/village)",
                "", "", "7", "code", "8");
        var plan = new Fieldwork(form, fixedVillage(5, ""));
        var submissions = plan.walkAll();
        int reused = checkAllocation(submissions, Map.of("1", Set.of("7", "8")));
        assertEquals(3, reused);
        deferredVisits(plan, 5);
        reuseWarning(plan, reused);
        for (var submission : submissions) assertWarning(submission, "answer");
    }

    @Test void aFilterWithNoMatchingRowsStillAllocatesItsStaticChoicesAtTheQuestion() throws Exception {
        var form = cascade("search('cascade-overlap', 'matches', 'village', /data/village)",
                "", "", "7", "code", "8");
        var plan = new Fieldwork(form, idConfig(5, "\"fields\":{\"village\":{\"value\":\"4\"}}"));
        int reused = checkAllocation(plan.walkAll(), Map.of("4", Set.of("7", "8")));
        assertEquals(3, reused);
        deferredVisits(plan, 5);
        reuseWarning(plan, reused);
    }

    @Test void evenAnEmptyDynamicListIsNotAssignedAnOpeningTimeVisitId() throws Exception {
        var form = cascade("search('cascade-overlap', 'matches', 'village', /data/village)", "", "", "code");
        var plan = new Fieldwork(form, idConfig(1, "\"fields\":{\"village\":{\"value\":\"4\"}}"));
        plan.walkAll();
        deferredVisits(plan, 1);
        // Amendment 2 does not define a draw or reuse count for zero eligible choices.
        // Do not impose an answer/fallback/warning oracle for that ambiguous case.
    }
}
