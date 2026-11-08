package ctosim;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment6Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment6ReasonsTest {
    @ParameterizedTest @ValueSource(strings = {
            "\"value\":\"moved\"", "\"texts\":[\"moved\"]", "\"expr\":\"'moved'\"",
            "\"when\":[{\"if\":\"true()\",\"value\":\"moved\"}]"})
    void finalRestrictionsStillReplaceTheOrdinaryOverride(String override) throws Exception {
        var plan = new Fieldwork(reasonForm("absent", "moved"),
                config6(4, 1, 1, 1, 0, 2, "[\"moved\"]",
                        "\"fields\":{\"status\":{" + override + ",\"missingRate\":1}}"));
        assertEquals(List.of("absent", "yes", "absent", "yes"), reasons(plan.walkAll()));
    }

    @ParameterizedTest @ValueSource(ints = {2, 5, 10})
    void everyFailureOfARevisitedHouseholdUsesANonfinalReasonWhenOffered(int max) throws Exception {
        var plan = new Fieldwork(reasonForm("absent", "moved", "deceased"),
                config6(100, 2, 2, 1, 0, max, "[\"moved\",\"deceased\"]", ""));
        var rows = plan.walkAll();
        boolean laterFailure = false;
        for (int i = 0; i < rows.size(); i++) {
            Visit visit = plan.visits().get(i);
            assertEquals(visit.found() ? "yes" : "absent", value(rows.get(i), "status"));
            if (!visit.found() && visit.attempt() > 1) laterFailure = true;
        }
        assertTrue(plan.visits().stream().anyMatch(Visit::found), "exercise successful completion");
        if (max > 2) assertTrue(laterFailure, "exercise the restriction after the first failure too");
    }

    @Test void restrictedNonfinalReasonsRemainUniformDespiteOverrideWeightsAndMissingRate() throws Exception {
        var plan = new Fieldwork(reasonForm("absent", "busy", "later", "moved"),
                config6(1200, 1, 1, 1, 0, 2, "[\"moved\"]",
                        "\"fields\":{\"status\":{\"missingRate\":1,\"weights\":{"
                        + "\"absent\":1,\"busy\":0,\"later\":0,\"moved\":100}}}"));
        var rows = plan.walkAll();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < rows.size(); i += 2) {
            assertFalse(plan.visits().get(i).found());
            String reason = value(rows.get(i), "status");
            assertTrue(List.of("absent", "busy", "later").contains(reason), reason);
            counts.merge(reason, 1, Integer::sum);
        }
        for (String reason : List.of("absent", "busy", "later"))
            assertUniformBin(counts.getOrDefault(reason, 0), 600, 1.0 / 3, reason);
    }

    @ParameterizedTest @ValueSource(strings = {"moved", "décédé", "0007"})
    void allFinalFallbackDoesNotCancelARevisitedHouseholdsPlannedSuccess(String code) throws Exception {
        var plan = new Fieldwork(reasonForm(code),
                config6(12, 1, 1, 1, 0, 2, "[\"" + code + "\"]", ""));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i += 2) {
            assertEquals(code, value(rows.get(i), "status"));
            assertFalse(plan.visits().get(i).found());
            assertEquals(plan.visits().get(i).id(), plan.visits().get(i + 1).id());
            assertEquals(2, plan.visits().get(i + 1).attempt());
            assertTrue(plan.visits().get(i + 1).found());
            assertEquals("yes", value(rows.get(i + 1), "status"));
        }
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void allFinalFallbackDrawsUniformlyAmongEveryEligibleReason() throws Exception {
        var plan = new Fieldwork(reasonForm("moved", "deceased", "closed"),
                config6(1200, 1, 1, 1, 0, 2, "[\"moved\",\"deceased\",\"closed\"]", ""));
        var rows = plan.walkAll();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < rows.size(); i += 2) {
            String reason = value(rows.get(i), "status");
            assertTrue(List.of("moved", "deceased", "closed").contains(reason));
            counts.merge(reason, 1, Integer::sum);
        }
        for (String reason : List.of("moved", "deceased", "closed"))
            assertUniformBin(counts.getOrDefault(reason, 0), 600, 1.0 / 3, reason);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalReasonsNeverRestoreAnyOfTheFourExcludedSpecialCodeFamilies(boolean revisited) throws Exception {
        // The only non-special failure is final: revisited households must use fallback;
        // unreached households terminate. Specials must not count as nonfinal alternatives.
        var plan = new Fieldwork(reasonForm("moved", "S", "D", "R", "O"),
                config6(20, 1, 1, revisited ? 1 : 0, revisited ? 0 : 1, 2,
                        "[\"moved\",\"S\",\"D\",\"R\",\"O\"]",
                        "\"specialValues\":[\"S\"],\"dontKnowCodes\":[\"D\"],"
                        + "\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"]"));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++)
            assertEquals(plan.visits().get(i).found() ? "yes" : "moved", value(rows.get(i), "status"));
    }

    @Test void aNonfinalSpecialChoiceDoesNotPreventTheAllFinalFallback() throws Exception {
        var plan = new Fieldwork(reasonForm("moved", "DK"),
                config6(2, 1, 1, 1, 0, 2, "[\"moved\"]", "\"dontKnowCodes\":[\"DK\"]"));
        var rows = plan.walkAll();
        assertEquals("moved", value(rows.get(0), "status"));
        assertTrue(plan.visits().get(1).found());
    }

    @Test void finalCodesAreComparedAsExactStringsIncludingCaseLeadingZerosAndUnicode() throws Exception {
        var plan = new Fieldwork(reasonForm("Moved", "007", "décédé"),
                config6(240, 1, 1, 1, 0, 2, "[\"moved\",\"7\",\"décédé\"]", ""));
        var rows = plan.walkAll();
        var seen = new java.util.HashSet<String>();
        for (int i = 0; i < rows.size(); i += 2) {
            String reason = value(rows.get(i), "status");
            assertTrue(List.of("Moved", "007").contains(reason), reason);
            seen.add(reason);
        }
        assertEquals(java.util.Set.of("Moved", "007"), seen);
    }

    @Test void filteringUsesChoicesOfferedAtEachAttemptRatherThanAllChoicesInTheForm() throws Exception {
        var plan = new Fieldwork(finalOnlyAfterFirstAttempt(),
                config6(90, 1, 1, 1, 0, 3, "[\"moved\"]", ""));
        var rows = plan.walkAll();
        boolean secondFailure = false;
        for (int i = 0; i < rows.size(); i++) {
            Visit visit = plan.visits().get(i);
            if (visit.found()) assertEquals("yes", value(rows.get(i), "status"));
            else {
                assertEquals(visit.attempt() == 1 ? "absent" : "moved", value(rows.get(i), "status"));
                if (visit.attempt() == 2) secondFailure = true;
            }
        }
        assertTrue(secondFailure, "an unoffered nonfinal choice must not defeat fallback");
    }

    @Test void noEligibleFailureChoiceKeepsTheExistingWarningAndOrdinaryAnswerFallback() throws Exception {
        var plan = new Fieldwork(reasonForm(), config6(2, 1, 1, 1, 0, 2,
                "[\"unoffered-final\"]", "\"fields\":{\"status\":{\"value\":\"yes\"}}"));
        var rows = plan.walkAll();
        assertFalse(plan.visits().get(0).found());
        assertEquals("yes", value(rows.get(0), "status"));
        assertWarning(rows.get(0), "status");
        assertTrue(plan.visits().get(1).found());
        assertEquals(plan.visits().get(0).id(), plan.visits().get(1).id());
    }
}
