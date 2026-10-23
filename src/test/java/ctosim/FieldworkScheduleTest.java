package ctosim;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.assertUniformBin;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("FormWalker temporarily changes the JVM time zone")
class FieldworkScheduleTest {
    @ParameterizedTest
    @CsvSource({"1,1,1", "13,3,2", "12,3,2", "7,2,3", "3,8,30", "3,10000,3650"})
    void roundRobinAssignmentsAndConfiguredQuotaDetermineShortInterviewDays(int n, int e, int d) throws Exception {
        var config = settings("\"submissions\":" + n + ",\"enumerators\":" + e + ",\"fieldworkDays\":" + d);
        var plan = new Fieldwork(timed(60), config);
        var submissions = plan.walkAll();
        assertEquals(n, submissions.size());
        assertEquals(n, plan.visits().size());
        int q = config.interviewsPerDay();
        for (int i = 0; i < n; i++) {
            Visit visit = plan.visits().get(i);
            assertEquals(i + 1, submissions.get(i).number());
            assertEquals(i % e + 1, visit.enumerator());
            assertEquals((i / e) / q, day(config, visit), "configured quota, counted separately per enumerator");
            assertEquals(visit.start(), submissions.get(i).start());
        }
        assertSchedule(plan, config, submissions);
        assertTrue(plan.warnings().isEmpty(), () -> plan.warnings().toString());
    }

    @Test void travelStartsFromTheActualEndAndNotFromStartOrUpload() throws Exception {
        var config = settings("\"submissions\":24,\"enumerators\":3,\"fieldworkDays\":2");
        var plan = new Fieldwork(timed(300), config);
        var submissions = plan.walkAll();
        assertSchedule(plan, config, submissions);
        for (Submission submission : submissions) {
            assertEquals(Duration.ofSeconds(300), Duration.between(submission.start(), submission.end()));
        }
    }

    @Test void minuteDrawsAreIndependentOfHowManyQuestionsTheWalkerAnswers() throws Exception {
        var config = settings("\"submissions\":18,\"enumerators\":3,\"fieldworkDays\":2");
        var oneQuestion = new Fieldwork(timed(60), config);
        var twoQuestions = new Fieldwork(metadata(), config);
        var first = oneQuestion.walkAll();
        var second = twoQuestions.walkAll();
        for (int i = 0; i < first.size(); i++) {
            Visit a = oneQuestion.visits().get(i);
            Visit b = twoQuestions.visits().get(i);
            assertEquals(day(config, a), day(config, b));
            assertEquals(a.latitude(), b.latitude());
            assertEquals(a.longitude(), b.longitude());
            if (i < 3 || day(config, a) != day(config, oneQuestion.visits().get(i - 3))) {
                assertEquals(a.start(), b.start(), "opening minute is drawn from number alone");
            } else {
                assertEquals(wholeMinutes(first.get(i - 3).end(), a.start()),
                        wholeMinutes(second.get(i - 3).end(), b.start()), "travel minute is drawn from number alone");
            }
        }
    }

    @Test void workHoursCanForceANewDayBeforeTheQuotaIsReached() throws Exception {
        var config = settings("\"submissions\":9,\"enumerators\":2,\"fieldworkDays\":1");
        var plan = new Fieldwork(timed(5 * 3600), config);
        var submissions = plan.walkAll();
        for (int i = 0; i < 9; i++) assertEquals((i / 2) / 2, day(config, plan.visits().get(i)));
        assertSchedule(plan, config, submissions);
        assertEquals(1, plan.warnings().size(), "one overflow warning across all enumerators");
        warning(plan.warnings(), null, 3, 1);
    }

    @Test void interviewsMayFinishAfterWorkButTheFollowingInterviewStartsNextDay() throws Exception {
        var config = settings("\"submissions\":5,\"enumerators\":1,\"fieldworkDays\":2");
        var plan = new Fieldwork(timed(9 * 3600), config);
        var submissions = plan.walkAll();
        for (int i = 0; i < 5; i++) {
            assertEquals(i, day(config, plan.visits().get(i)));
            assertFalse(submissions.get(i).end().isBefore(config.start().plusDays(i).plusHours(9)));
        }
        assertSchedule(plan, config, submissions);
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 5, 2);
    }

    @Test void aCandidateAtExactlyClosingTimeStaysTodayButOneSecondLaterMovesTomorrow() throws Exception {
        var config = settings("\"submissions\":2,\"enumerators\":1,\"fieldworkDays\":1");
        var probe = new Fieldwork(timed(60), config);
        var initial = probe.walkAll();
        long gap = wholeMinutes(initial.get(0).end(), initial.get(1).start());
        int duration = (int) Duration.between(initial.get(0).start(), config.start().plusHours(9)).getSeconds()
                - (int) gap * 60;
        var atClosing = new Fieldwork(timed(duration), config);
        assertEquals(config.start().plusHours(9), atClosing.walkAll().get(1).start(),
                "only a candidate AFTER closing moves to tomorrow; minutes depend on number alone");
        assertTrue(atClosing.warnings().isEmpty());
        var afterClosing = new Fieldwork(timed(duration + 1), config);
        afterClosing.walkAll();
        assertEquals(1, day(config, afterClosing.visits().get(1)));
        warning(afterClosing.warnings(), null, 2, 1);
    }

    @Test void startingOnTheLastConfiguredDayDoesNotProduceAnOverflowWarning() throws Exception {
        var config = settings("\"submissions\":3,\"enumerators\":1,\"fieldworkDays\":3");
        var plan = new Fieldwork(timed(9 * 3600), config);
        plan.walkAll();
        assertEquals(2, day(config, plan.visits().get(2)));
        assertTrue(plan.warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"6100,1", "100,61"})
    void firstInterviewMinutesOnInitialAndLaterDaysAreUniformIncludingBothEndpoints(int enumerators, int days) throws Exception {
        int draws = 6100;
        var config = settings("\"submissions\":" + draws + ",\"enumerators\":" + enumerators
                + ",\"fieldworkDays\":" + days + ",\"interviewsPerDay\":1");
        var plan = new Fieldwork(timed(60), config);
        plan.walkAll();
        int[] bins = new int[61];
        for (Visit visit : plan.visits()) {
            long minute = wholeMinutes(config.start().plusDays(day(config, visit)), visit.start());
            assertTrue(minute >= 0 && minute <= 60);
            bins[(int) minute]++;
        }
        for (int m = 0; m <= 60; m++) {
            assertUniformBin(bins[m], draws, 1.0 / 61, "first minute " + m);
            assertTrue(bins[m] > 0, "every whole minute is attainable");
        }
    }

    @Test void sameDayTravelMinutesAreUniformIncludingBothEndpoints() throws Exception {
        int enumerators = 3100;
        var config = settings("\"submissions\":" + 2 * enumerators
                + ",\"enumerators\":" + enumerators + ",\"fieldworkDays\":1");
        var plan = new Fieldwork(timed(60), config);
        var submissions = plan.walkAll();
        int[] bins = new int[31];
        for (int i = enumerators; i < submissions.size(); i++) {
            long minutes = wholeMinutes(submissions.get(i - enumerators).end(), submissions.get(i).start());
            assertTrue(minutes >= 10 && minutes <= 40);
            bins[(int) minutes - 10]++;
        }
        for (int m = 0; m < 31; m++) {
            assertUniformBin(bins[m], enumerators, 1.0 / 31, "travel minute " + (m + 10));
            assertTrue(bins[m] > 0);
        }
    }

    private static void assertSchedule(Fieldwork plan, SimulationConfig config, List<Submission> submissions) {
        var previous = new HashMap<Integer, Integer>();
        var counts = new HashMap<String, Integer>();
        int quota = config.interviewsPerDay();
        for (int i = 0; i < submissions.size(); i++) {
            Visit visit = plan.visits().get(i);
            long day = day(config, visit);
            var opening = config.start().plusDays(day);
            assertTrue(day >= 0);
            assertFalse(visit.start().isBefore(opening));
            assertFalse(visit.start().isAfter(opening.plusHours(9)));
            assertEquals(visit.start(), submissions.get(i).start());
            String key = visit.enumerator() + "/" + day;
            int count = counts.merge(key, 1, Integer::sum);
            assertTrue(count <= quota, "daily cap per enumerator");
            Integer prior = previous.put(visit.enumerator(), i);
            if (prior == null) {
                assertEquals(0, day);
                assertOpeningDelay(opening, visit.start());
            } else {
                var end = submissions.get(prior).end();
                assertFalse(visit.start().isBefore(end.plusMinutes(10)), "no overlap, at least ten minutes rest");
                long oldDay = day(config, plan.visits().get(prior));
                if (day == oldDay) {
                    long gap = wholeMinutes(end, visit.start());
                    assertTrue(gap >= 10 && gap <= 40);
                } else {
                    assertEquals(oldDay + 1, day, "advance exactly one workday");
                    assertOpeningDelay(opening, visit.start());
                    boolean quotaReached = counts.get(visit.enumerator() + "/" + oldDay) == quota;
                    assertTrue(quotaReached || end.plusMinutes(40).isAfter(config.start().plusDays(oldDay).plusHours(9)),
                            "a new day requires quota exhaustion or a late candidate");
                }
            }
        }
    }

    private static void assertOpeningDelay(java.time.LocalDateTime opening, java.time.LocalDateTime start) {
        long delay = wholeMinutes(opening, start);
        assertTrue(delay >= 0 && delay <= 60, "first interview of each day starts 0..60 minutes after opening");
    }
}
