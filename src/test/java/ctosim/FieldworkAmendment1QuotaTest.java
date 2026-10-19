package ctosim;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 13 Amendment 1: the quota is a configured cap, not N / (E * D). */
@Isolated("FormWalker temporarily changes the JVM time zone")
class FieldworkAmendment1QuotaTest {
    @Test void defaultPlanDoesFourInterviewsPerEnumeratorPerDayAndFinishesInFiveDays() throws Exception {
        var config = SimulationConfig.defaults();
        var plan = new Fieldwork(timed(60), config);
        var submissions = plan.walkAll();
        assertEquals(100, submissions.size());
        assertShortSchedule(plan, config, submissions, 4);
        assertEquals(4, day(config, plan.visits().get(99)));
        assertTrue(plan.warnings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "3,1,30", "4,1,30", "5,1,30", "17,2,30", "41,3,1", "3,8,3650"})
    void omittedQuotaRemainsFourWhateverSubmissionEnumeratorAndExpectedDayCounts(int n, int e, int d)
            throws Exception {
        var config = settings("\"submissions\":" + n + ",\"enumerators\":" + e + ",\"fieldworkDays\":" + d);
        var plan = new Fieldwork(timed(60), config);
        assertShortSchedule(plan, config, plan.walkAll(), 4);
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "2,1,1", "2,1,2", "3,1,2", "8,2,4", "9,2,4",
            "25,3,4", "43,3,6", "3,8,1"})
    void explicitQuotaIsCountedSeparatelyForEachEnumeratorIncludingPartialFinalDays(int n, int e, int q)
            throws Exception {
        var config = quota(n, e, 30, q);
        var plan = new Fieldwork(timed(60), config);
        assertShortSchedule(plan, config, plan.walkAll(), q);
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void increasingSubmissionCountPreservesTheEntireEarlierSchedule() throws Exception {
        var config = quota(7, 2, 1, 3);
        var shortPlan = new Fieldwork(timed(60), config);
        var longPlan = new Fieldwork(timed(60), config.withSubmissions(31));
        var expected = shortPlan.walkAll();
        var actual = longPlan.walkAll();
        assertEquals(shortPlan.visits(), longPlan.visits().subList(0, 7));
        sameRun(expected, actual.subList(0, 7));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3650})
    void expectedFieldworkDaysChangeOnlyTheOverflowWarning(int expectedDays) throws Exception {
        var baseline = new Fieldwork(timed(60), quota(19, 2, 30, 3));
        var plan = new Fieldwork(timed(60), quota(19, 2, expectedDays, 3));
        var expected = baseline.walkAll();
        var actual = plan.walkAll();
        assertEquals(baseline.visits(), plan.visits());
        sameRun(expected, actual);
        assertEquals(expectedDays < 4 ? 1 : 0, plan.warnings().size());
        if (expectedDays < 4) warning(plan.warnings(), null, 4, expectedDays);
    }

    @ParameterizedTest @CsvSource({"12,0", "13,1"})
    void overflowBeginsWithTheFirstInterviewPastTheLastExpectedDay(int n, int warnings) throws Exception {
        var config = quota(n, 2, 2, 3);
        var plan = new Fieldwork(timed(60), config);
        assertShortSchedule(plan, config, plan.walkAll(), 3);
        assertEquals(warnings, plan.warnings().size());
        if (warnings != 0) warning(plan.warnings(), null, 3, 2);
    }

    @ParameterizedTest @ValueSource(ints = {4, 1000})
    void closingTimeCanReduceTheDailyCountBelowEvenTheMaximumLegalQuota(int q) throws Exception {
        var config = quota(9, 2, 1, q);
        var plan = new Fieldwork(timed(5 * 3600), config);
        var submissions = plan.walkAll();
        // Even the latest opening and longest travel allow two starts, never three.
        assertShortSchedule(plan, config, submissions, 2);
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 3, 1);
    }

    @Test void aQuotaOfOneEndsTheDayDespiteTimeForAnotherInterview() throws Exception {
        var config = quota(4, 1, 1, 1);
        var plan = new Fieldwork(timed(60), config);
        assertShortSchedule(plan, config, plan.walkAll(), 1);
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 4, 1);
    }

    @Test void anInterviewEndingAfterClosingDoesNotItselfCountAsAnotherFieldworkDay() throws Exception {
        var config = quota(1, 1, 1, 1000);
        var plan = new Fieldwork(timed(10 * 3600), config);
        var submission = plan.walkAll().get(0);
        assertTrue(submission.end().isAfter(config.start().plusHours(9)));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void exactlyClosingTimeIsAllowedBelowQuotaButOneSecondLaterMovesToTomorrow() throws Exception {
        var config = quota(2, 1, 1, 4);
        var probe = new Fieldwork(timed(60), config);
        var initial = probe.walkAll();
        long gap = wholeMinutes(initial.get(0).end(), initial.get(1).start());
        int duration = Math.toIntExact(Duration.between(initial.get(0).start(),
                config.start().plusHours(9)).getSeconds() - gap * 60);
        var atClosing = new Fieldwork(timed(duration), config);
        assertEquals(config.start().plusHours(9), atClosing.walkAll().get(1).start());
        assertTrue(atClosing.warnings().isEmpty());
        var afterClosing = new Fieldwork(timed(duration + 1), config);
        afterClosing.walkAll();
        assertEquals(1, day(config, afterClosing.visits().get(1)));
        assertEquals(1, afterClosing.warnings().size());
        warning(afterClosing.warnings(), null, 2, 1);
    }

    @Test void replayWithAnExplicitQuotaReproducesVisitsSubmissionsAndOneOverflowWarning() throws Exception {
        var form = timed(60);
        var config = quota(17, 2, 1, 2);
        var plan = new Fieldwork(form, config);
        var first = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        sameRun(first, plan.walkAll());
        assertEquals(visits, plan.visits());
        assertEquals(warnings, plan.warnings());
        assertEquals(1, warnings.size());
        var walker = new FormWalker(form, config);
        for (int i = 0; i < first.size(); i++) same(first.get(i), walker.walk(i + 1, visits.get(i)));
    }

    private static SimulationConfig quota(int n, int e, int d, int q) throws Exception {
        return settings("\"submissions\":" + n + ",\"enumerators\":" + e + ",\"fieldworkDays\":" + d
                + ",\"interviewsPerDay\":" + q);
    }

    private static void assertShortSchedule(Fieldwork plan, SimulationConfig config,
            List<Submission> submissions, int effectiveQuota) {
        assertEquals(config.submissions(), submissions.size());
        assertEquals(submissions.size(), plan.visits().size());
        for (int i = 0; i < submissions.size(); i++) {
            var visit = plan.visits().get(i);
            int interview = i / config.enumerators();
            long expectedDay = interview / effectiveQuota;
            assertEquals(i + 1, submissions.get(i).number());
            assertEquals(i % config.enumerators() + 1, visit.enumerator());
            assertEquals(expectedDay, day(config, visit), "day for submission " + (i + 1));
            assertEquals(visit.start(), submissions.get(i).start());
            var opening = config.start().plusDays(expectedDay);
            assertFalse(visit.start().isBefore(opening));
            assertFalse(visit.start().isAfter(opening.plusHours(9)));
            if (interview % effectiveQuota == 0) {
                long delay = wholeMinutes(opening, visit.start());
                assertTrue(delay >= 0 && delay <= 60);
            } else {
                long travel = wholeMinutes(submissions.get(i - config.enumerators()).end(), visit.start());
                assertTrue(travel >= 10 && travel <= 40, "travel from the actual previous end");
            }
            if (interview > 0) assertFalse(visit.start().isBefore(
                    submissions.get(i - config.enumerators()).end().plusMinutes(10)));
        }
    }
}
