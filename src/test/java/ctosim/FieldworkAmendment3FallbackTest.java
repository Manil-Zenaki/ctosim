package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
class FieldworkAmendment3FallbackTest extends FieldworkAmendment3Support {
    @ParameterizedTest @ValueSource(strings = {"X", "Z"})
    void noViableChoiceInitiallyRestoresTheOrdinaryMissingRule(String village) throws Exception {
        var form = simple("search('a3-villages', 'matches', 'code', '" + village + "')", HOUSEHOLDS, "", "code");
        var plan = new Fieldwork(form, idConfig(2, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals(List.of("", ""), column(plan.walkAll(), "village"),
                "empty and special-only descendant lists must not suppress the ordinary missing rule");
    }

    @Test void anEmptyUpperChoiceListStaysBlankWithoutInventingUnOfferedBranches() throws Exception {
        var form = simple("search('a3-villages', 'matches', 'district', 'absent')", HOUSEHOLDS, "", "code");
        var plan = new Fieldwork(form, idConfig(1, ""));
        assertEquals("", value(plan.walkAll().get(0), "village"));
    }

    @Test void exhaustingAllOfferedBranchesRestoresTheOrdinaryWeightsAndCountsReusedIds() throws Exception {
        var form = simple("search('a3-villages', 'matches', 'district', 'D1')", HOUSEHOLDS, "", "code");
        var config = idConfig(8, "\"fields\":{\"village\":{\"weights\":{\"A\":1,\"B\":0}}}");
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        assertSteered(submissions.subList(0, 5), Map.of("A", POOLS.get("A"), "B", POOLS.get("B")), false);
        assertEquals(List.of("A", "A", "B", "B", "B", "A", "A", "A"), column(submissions, "village"));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer", 3);
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
    }

    @Test void exhaustingAllOfferedBranchesRestoresTheOrdinaryMissingRate() throws Exception {
        var form = simple("search('a3-villages', 'matches', 'code', 'A')", HOUSEHOLDS, "", "code");
        var plan = new Fieldwork(form, idConfig(4, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        var submissions = plan.walkAll();
        assertEquals(List.of("A", "A", "", ""), column(submissions, "village"));
        assertEquals(POOLS.get("A"), new HashSet<>(column(submissions.subList(0, 2), "answer")));
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"value\":\"A\"",
            "\"expr\":\"'A'\"",
            "\"texts\":[\"B\"],\"weights\":{\"A\":1,\"B\":0,\"C\":0,\"E\":0}",
            "\"when\":[{\"if\":\"true()\",\"value\":\"A\"}]",
            "\"weights\":{\"A\":1},\"when\":[{\"if\":\"true()\",\"missingRate\":0}]"
    })
    void explicitOverridesAndApplicableRulesKeepOrdinaryAnswersEvenAfterTheirBranchIsExhausted(String override) throws Exception {
        var plan = new Fieldwork(simple(), idConfig(4, "\"fields\":{\"village\":{" + override + "}}"));
        var submissions = plan.walkAll();
        assertEquals(List.of("A", "A", "A", "A"), column(submissions, "village"));
        assertEquals(POOLS.get("A"), new HashSet<>(column(submissions.subList(0, 2), "answer")));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer", 2);
    }

    @Test void anApplicableMissingRateRuleStillBlanksTheUpperQuestion() throws Exception {
        var config = idConfig(2, "\"fields\":{\"village\":{\"when\":[{\"if\":\"true()\",\"missingRate\":1}]}}");
        assertEquals(List.of("", ""), column(new Fieldwork(simple(), config).walkAll(), "village"));
    }

    @Test void rulesWhoseConditionsAreFalseDoNotDisableSteering() throws Exception {
        var plan = new Fieldwork(simple(), idConfig(8, """
                "fields":{"village":{"missingRate":1,"when":[{"if":"false()","value":"A"}]}}
                """));
        assertSteered(plan.walkAll(), POOLS, false);
        assertNoPlanIdWarning(plan, 8);
    }

    @Test void aHigherFixedAnswerIsRespectedWhileLowerUpperQuestionsStillSteer() throws Exception {
        var plan = new Fieldwork(deep(false), idConfig(8, "\"fields\":{\"region\":{\"value\":\"R1\"}}"));
        var submissions = plan.walkAll();
        var reachable = Map.of("A", POOLS.get("A"), "B", POOLS.get("B"), "C", POOLS.get("C"));
        assertSteered(submissions.subList(0, 7), reachable, true);
        assertEquals(allIds(reachable), new HashSet<>(column(submissions.subList(0, 7), "answer")));
        for (var submission : submissions) assertEquals("R1", value(submission, "region"));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer", 1);
    }

    @ParameterizedTest @ValueSource(strings = {
            "concat('', /data/village)", "string(/data/village)"
    })
    void aFilterExpressionContainingAReferenceIsNotASingleReference(String text) throws Exception {
        var form = simple("search('a3-villages')",
                "search('a3-households', 'matches', 'village', " + text + ", 'cohort', 'main')", "", "code");
        var plan = new Fieldwork(form, idConfig(2, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals(List.of("", ""), column(plan.walkAll(), "village"));
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null), "Amendment 2 still applies");
    }

    @Test void aReferencedSelectWithoutSearchIsNotAnUpperQuestion() throws Exception {
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                select("village", "", "A", "B") + control(HOUSEHOLDS, "code"));
        var plan = new Fieldwork(form, idConfig(1, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals("", value(plan.walkAll().get(0), "village"));
    }

    @Test void aReferencedSearchSelectMultipleIsNotAnUpperQuestion() throws Exception {
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                selectControl("select", "village", "search('a3-villages')", items("code"))
                        + control(HOUSEHOLDS, "code"));
        var plan = new Fieldwork(form, idConfig(1, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals("", value(plan.walkAll().get(0), "village"));
    }

    @Test void aLiteralIdFilterDoesNotSteerAnUnrelatedSearchQuestion() throws Exception {
        var form = simple("search('a3-villages')",
                "search('a3-households', 'matches', 'village', 'A', 'cohort', 'main')", "", "code");
        var plan = new Fieldwork(form, idConfig(2, "\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals(List.of("", ""), column(plan.walkAll(), "village"));
        assertEquals(POOLS.get("A"), new HashSet<>(ids(plan)), "literal filters retain visit allocation");
    }

    @Test void noConfiguredIdLeavesSearchQuestionsUnderOrdinaryMissingRules() throws Exception {
        var plan = new Fieldwork(simple(), settings("\"submissions\":2,\"fields\":{\"village\":{\"missingRate\":1}}"));
        assertEquals(List.of("", ""), column(plan.walkAll(), "village"));
    }

    @ParameterizedTest @ValueSource(strings = {"\"value\":\"8\"", "\"texts\":[\"8\"]"})
    void anExplicitIdPoolDisablesUpperSteering(String override) throws Exception {
        var form = simple("search('a3-villages')", HOUSEHOLDS, "", "code", "8");
        var config = idConfig(2, "\"fields\":{\"village\":{\"missingRate\":1},\"answer\":{" + override + "}}");
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        assertEquals(List.of("", ""), column(submissions, "village"));
        assertEquals(List.of("8", "8"), column(submissions, "answer"));
        assertEquals(List.of("8", "8"), ids(plan));
    }

    @Test void standaloneWalksRetainMissingRatesBeforeAndAfterAPlanUsesTheSameForm() throws Exception {
        var form = deep(false);
        var config = idConfig(8, "\"fields\":{\"region\":{\"missingRate\":1},\"village\":{\"missingRate\":1}}");
        var walker = new FormWalker(form, config);
        var plain = walker.walk(1);
        var withVisit = walker.walk(1, visit(1));
        assertEquals("", value(plain, "region"));
        assertEquals("", value(withVisit, "region"));
        assertEquals("", value(withVisit, "village"));
        new Fieldwork(form, config).walkAll();
        same(plain, walker.walk(1));
        same(withVisit, walker.walk(1, visit(1)));
    }

    @Test void standaloneWalksDoNotSteerAwayFromAPreviouslyUsedBranch() throws Exception {
        var form = simple();
        var config = idConfig(8, "\"fields\":{\"village\":{\"weights\":{\"A\":1}}}");
        var walker = new FormWalker(form, config);
        for (int n = 1; n <= 12; n++) assertEquals("A", value(walker.walk(n, visit(1)), "village"));
    }
}
