package ctosim;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment5Support.supplied;
import static ctosim.FieldworkAmendment6Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Changes JVM locale/time zone and form engine state")
class FieldworkAmendment6RandomnessTest {
    @Test void restrictingFailureReasonsDoesNotChangeRevisitedHouseholdOutcomesOrAttemptCounts() throws Exception {
        var plain = new Fieldwork(reasonForm("absent", "moved"),
                config6(90, 2, 1, 1, 0, 5, null, ""));
        var restricted = new Fieldwork(reasonForm("absent", "moved"),
                config6(90, 2, 1, 1, 0, 5, "[\"moved\"]", ""));
        plain.walkAll();
        restricted.walkAll();
        assertEquals(plain.visits(), restricted.visits(), "outcomes use a separate draw from the failure reason");
        assertEquals(plain.warnings(), restricted.warnings());
    }

    @Test void omittedAndEmptyFinalListsPreserveTheSameAmendment5Run() throws Exception {
        var omitted = new Fieldwork(reasonForm("absent", "moved"),
                config6(60, 2, 2, .5, .5, 4, null, ""));
        var empty = new Fieldwork(reasonForm("absent", "moved"),
                config6(60, 2, 2, .5, .5, 4, "[]", ""));
        sameRun(omitted.walkAll(), empty.walkAll());
        assertEquals(omitted.visits(), empty.visits());
        assertEquals(omitted.warnings(), empty.warnings());
    }

    @Test void finalSettingsDoNotAffectHouseholdsFoundOnTheirFirstVisit() throws Exception {
        var plain = new Fieldwork(reasonForm("moved", "deceased"),
                config6(18, 2, 3, 0, 0, 3, null, ""));
        var configured = new Fieldwork(reasonForm("moved", "deceased"),
                config6(18, 2, 3, 0, 0, 3, "[\"moved\",\"deceased\"]", ""));
        var rows = configured.walkAll();
        sameRun(plain.walkAll(), rows);
        assertEquals(plain.visits(), configured.visits());
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("yes", value(rows.get(i), "status"));
            assertEquals(1, configured.visits().get(i).attempt());
            assertTrue(configured.visits().get(i).found());
        }
        assertEquals(plain.warnings(), configured.warnings());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void standaloneWalkIgnoresFinalSettingsForBothFoundAndFailedVisits(boolean found) throws Exception {
        var plain = new FormWalker(reasonForm("absent", "moved"),
                config6(1, 1, 1, 1, 0, 3, null, ""));
        var configured = new FormWalker(reasonForm("absent", "moved"),
                config6(1, 1, 1, 1, 0, 3, "[\"moved\"]", ""));
        var seen = new java.util.HashSet<String>();
        for (int n = 1; n <= 120; n++) {
            var visit = supplied(1 + n % 3, found);
            var row = configured.walk(n, visit);
            same(plain.walk(n, visit), row);
            seen.add(value(row, "status"));
        }
        assertEquals(found ? java.util.Set.of("yes") : java.util.Set.of("absent", "moved"), seen);
    }

    @Test void replayAndFreshLoadResetFinalTerminationsAndWaitingCounts() throws Exception {
        var config = config6(79, 3, 3, .4, .6, 4, "[\"moved\"]", "");
        var plan = new Fieldwork(reasonForm("absent", "moved"), config);
        var rows = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        assertTrue(reasons(rows).contains("moved"));
        assertTrue(visits.stream().anyMatch(v -> v.attempt() > 1));
        sameRun(rows, plan.walkAll());
        assertEquals(visits, plan.visits());
        assertEquals(warnings, plan.warnings());
        var fresh = new Fieldwork(reasonForm("absent", "moved"), config);
        sameRun(rows, fresh.walkAll());
        assertEquals(visits, fresh.visits());
        assertEquals(warnings, fresh.warnings());
    }

    @Test void extendingThePlanPreservesFinalReasonsAndAllEarlierVisits() throws Exception {
        var config = config6(27, 2, 3, .5, .5, 4, "[\"moved\"]", "");
        var shorter = new Fieldwork(reasonForm("absent", "moved"), config);
        var longer = new Fieldwork(reasonForm("absent", "moved"), config.withSubmissions(73));
        sameRun(shorter.walkAll(), longer.walkAll().subList(0, 27));
        assertEquals(shorter.visits(), longer.visits().subList(0, 27));
    }

    @Test void unrelatedEarlierAnswersDoNotChangeFinalReasonDrawsOrHouseholdOutcomes() throws Exception {
        // Quota one isolates days from the additional question's duration.
        var config = config6(90, 2, 1, .5, .5, 4, "[\"moved\"]", "");
        var plain = new Fieldwork(reasonForm("absent", "busy", "moved"), config);
        var noisy = new Fieldwork(reasonForm(true, "absent", "busy", "moved"), config);
        assertEquals(reasons(plain.walkAll()), reasons(noisy.walkAll()));
        assertEquals(plain.visits(), noisy.visits());
        assertEquals(plain.warnings(), noisy.warnings());
    }

    @Test void differentSeedsCanChangeTheFinalFailureSequence() throws Exception {
        var config = config6(100, 1, 1, 0, 1, 4, "[\"moved\"]", "");
        var first = new Fieldwork(reasonForm("absent", "moved"), config);
        var second = new Fieldwork(reasonForm("absent", "moved"), config.withSeed(981273));
        assertNotEquals(reasons(first.walkAll()), reasons(second.walkAll()));
    }

    @Test void finalReasonDrawsAndTerminationsDoNotDependOnLocaleOrTimeZone() throws Exception {
        Locale locale = Locale.getDefault();
        TimeZone zone = TimeZone.getDefault();
        try {
            var config = config6(31, 2, 2, .5, .5, 4, "[\"décédé\"]", "");
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            var first = new Fieldwork(reasonForm("absent", "décédé"), config);
            var rows = List.copyOf(first.walkAll());
            var visits = List.copyOf(first.visits());
            var warnings = List.copyOf(first.warnings());
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var changed = new Fieldwork(reasonForm("absent", "décédé"), config);
            sameRun(rows, changed.walkAll());
            assertEquals(visits, changed.visits());
            assertEquals(warnings, changed.warnings());
        } finally {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }
}
