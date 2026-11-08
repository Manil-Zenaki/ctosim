package ctosim;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.FieldworkAmendment5Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment5PlanTest {
    @Test void noVisitSettingRetainsFirstAttemptFoundVisits() throws Exception {
        var plan = new Fieldwork(simple(), settings("\"submissions\":8"));
        plan.walkAll();
        assertEquals(8, plan.visits().size());
        for (Visit visit : plan.visits()) {
            assertEquals(1, visit.attempt());
            assertTrue(visit.found());
        }
    }

    @Test void zeroOutcomeRatesFindEveryNewHouseholdImmediately() throws Exception {
        var plan = new Fieldwork(simple(), config5(24, 3, 4, 0, 0, 3, SMALL_AREA));
        var rows = plan.walkAll();
        assertEquals(24, plan.visits().stream().map(Visit::id).distinct().count());
        for (int i = 0; i < 24; i++) {
            assertEquals(1, plan.visits().get(i).attempt());
            assertTrue(plan.visits().get(i).found());
            assertEquals("yes", value(rows.get(i), "status"));
        }
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void ratesSummingToOneLeaveNoHouseholdFoundAtItsFirstVisit() throws Exception {
        var config = config5(180, 3, 4, .65, .35, 4, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        checkQueue(plan, config, rows, 4);
        assertTrue(plan.visits().stream().filter(v -> v.attempt() == 1).noneMatch(Visit::found));
        assertTrue(plan.visits().stream().anyMatch(Visit::found), "revisited households eventually succeed");
        assertTrue(plan.visits().stream().anyMatch(v -> v.attempt() == 4 && !v.found()), "unreached households exhaust visits");
    }

    @ParameterizedTest @CsvSource({"1,1,2", "1,4,3", "3,4,3", "4,2,10"})
    void waitingHouseholdsAreRevisitedInLatestVisitOrderBeforeAnyNewHousehold(int enumerators, int quota, int max) throws Exception {
        var config = config5(97, enumerators, quota, 0, 1, max, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        checkQueue(plan, config, rows, max);
        assertTrue(plan.visits().stream().noneMatch(Visit::found));
        assertTrue(plan.visits().stream().anyMatch(v -> v.attempt() == max));
    }

    @Test void aFailedVisitNeverQueuesASecondVisitOnTheSameDay() throws Exception {
        var config = config5(12, 1, 6, 1, 0, 2, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        for (int i = 0; i < 6; i++) {
            assertEquals(0, day(config, plan.visits().get(i)));
            assertEquals(1, plan.visits().get(i).attempt());
            assertFalse(plan.visits().get(i).found());
            assertEquals(1, day(config, plan.visits().get(i + 6)));
            assertEquals(2, plan.visits().get(i + 6).attempt());
            assertTrue(plan.visits().get(i + 6).found());
            assertEquals(plan.visits().get(i).id(), plan.visits().get(i + 6).id());
        }
        checkQueue(plan, config, rows, 2);
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void terminalUnreachedHouseholdsStopAtMaxVisitsAndFreeSlotsForNewHouseholds() throws Exception {
        var config = config5(8, 1, 2, 0, 1, 3, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        plan.walkAll();
        assertEquals(List.of(1, 1, 2, 2, 3, 3, 1, 1), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(4, plan.visits().stream().map(Visit::id).distinct().count());
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), null, 2);
    }

    @Test void successfulRevisitsLeaveTheQueueAndAreNeverVisitedAgain() throws Exception {
        var config = config5(8, 1, 2, 1, 0, 2, SMALL_AREA);
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        assertEquals(List.of(1, 1, 2, 2, 1, 1, 2, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(List.of(false, false, true, true, false, false, true, true),
                plan.visits().stream().map(Visit::found).toList());
        checkQueue(plan, config, rows, 2);
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void aClosingTimeRolloverReclassifiesTheNextSubmissionAsARevisit() throws Exception {
        var config = config5(4, 1, 10, 1, 0, 2, SMALL_AREA);
        // Two starts fit; the third cannot. Scheduling must choose from the new day's queue.
        var plan = new Fieldwork(simple(18000, ""), config);
        plan.walkAll();
        assertEquals(List.of(0L, 0L, 1L, 1L), plan.visits().stream().map(v -> day(config, v)).toList());
        assertEquals(List.of(1, 1, 2, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(plan.visits().get(0).id(), plan.visits().get(2).id());
        assertEquals(plan.visits().get(1).id(), plan.visits().get(3).id());
    }

    @Test void workdaysAreMeasuredFromConfiguredStartEvenAcrossMidnight() throws Exception {
        var config = SimulationConfig.parse("{\"seed\":731991,\"start\":\"2026-03-02T23:00:17\","
                + "\"submissions\":8,\"enumerators\":1,\"interviewsPerDay\":2,\"missingRate\":0,"
                + "\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":100},"
                + SMALL_AREA + "," + visitSettings(1, 0, 2) + "}", "night.json");
        var plan = new Fieldwork(simple(), config);
        checkQueue(plan, config, plan.walkAll(), 2);
        assertEquals(List.of(0L, 0L, 1L, 1L, 2L, 2L, 3L, 3L),
                plan.visits().stream().map(v -> day(config, v)).toList());
    }

    @Test void revisitsKeepTheirExactHouseholdPointAndEnumerator() throws Exception {
        var config = config5(60, 3, 4, 0, 1, 3, "");
        var plan = new Fieldwork(simple(), config);
        var rows = plan.walkAll();
        checkQueue(plan, config, rows, 3);
        assertTrue(plan.visits().stream().anyMatch(v -> v.attempt() > 1));
    }

    @Test void newHouseholdsUseTheFirstRevisitsPointAsThatDaysVillage() throws Exception {
        var config = config5(7, 1, 4, 1, 0, 2, "");
        // Day zero has exactly two starts, both failures. Day one's short successful
        // revisits leave room for fresh households on the same day.
        var form = fixture("<answer/><status/><attempt/><longFailure/>",
                bind("answer", "int", MINUTE) + bind("status", "string", MINUTE)
                        + bind("attempt", "int", MINUTE)
                        + bind("longFailure", "string", "relevant=\"/data/status != 'yes'\" ct:minimum-seconds=\"18000\""),
                input("answer") + choices("status", "yes", "absent") + input("attempt") + input("longFailure"));
        var plan = new Fieldwork(form, config);
        plan.walkAll();
        Visit firstRevisit = plan.visits().get(2);
        Visit fresh = plan.visits().get(4);
        assertEquals(2, firstRevisit.attempt());
        assertEquals(1, fresh.attempt());
        assertEquals(day(config, firstRevisit), day(config, fresh));
        assertTrue(distance(firstRevisit.latitude(), firstRevisit.longitude(), fresh.latitude(), fresh.longitude()) <= 1500.000001);
    }

    @ParameterizedTest @CsvSource({"4,2,0", "6,3,1", "7,4,1"})
    void staticIdExhaustionComparesPoolSizeWithHouseholdsInsteadOfSubmissions(int n, int households, int warnings) throws Exception {
        var config = config5(n, 1, 1, 1, 0, 2,
                SMALL_AREA + ",\"fields\":{\"answer\":{\"texts\":[\"0007\",\"é02\"]}}");
        var form = fixture("<answer/><status/><attempt/>", bind("answer", "string", MINUTE)
                + bind("status", "string", MINUTE) + bind("attempt", "int", MINUTE),
                input("answer") + choices("status", "yes", "absent") + input("attempt"));
        var plan = new Fieldwork(form, config);
        var rows = plan.walkAll();
        List<String> first = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Visit visit = plan.visits().get(i);
            assertEquals(visit.id(), value(rows.get(i), "answer"));
            if (visit.attempt() == 1) first.add(visit.id());
            else assertEquals(plan.visits().get(i - 1).id(), visit.id());
        }
        assertEquals(households, first.size());
        assertNotEquals(first.get(0), first.get(1));
        for (int i = 2; i < first.size(); i++) assertEquals(first.get(i % 2), first.get(i));
        int pending = n % 2;
        assertEquals(warnings + pending, plan.warnings().size());
        if (warnings > 0) warning(List.of(plan.warnings().get(0)), "answer", 2, households);
        if (pending > 0) warning(List.of(plan.warnings().get(plan.warnings().size() - 1)), null, 1);
    }

    @Test void revisitsDoNotRequireAConfiguredIdField() throws Exception {
        var config = settings("\"submissions\":4,\"enumerators\":1,\"interviewsPerDay\":1,"
                + SMALL_AREA + "," + visitSettings(1, 0, 2));
        var plan = new Fieldwork(simple(), config);
        plan.walkAll();
        assertEquals(List.of(1, 2, 1, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void aTextIdWithoutAPoolStillAllowsRevisitsAndKeepsTheNoPoolWarning() throws Exception {
        var form = fixture("<answer/><status/><attempt/>", bind("answer", "string", MINUTE)
                        + bind("status", "string", MINUTE) + bind("attempt", "int", MINUTE),
                input("answer") + choices("status", "yes", "absent") + input("attempt"));
        var plan = new Fieldwork(form, config5(2, 1, 1, 1, 0, 2, SMALL_AREA));
        plan.walkAll();
        assertEquals(List.of(1, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer");
    }

    @ParameterizedTest @CsvSource({"1,1", "3,3", "4,2", "6,0", "7,1"})
    void finalWaitingWarningCountsHouseholdsNotFailedVisitsOrRemainingAttempts(int n, int waiting) throws Exception {
        var plan = new Fieldwork(simple(), config5(n, 1, 3, 0, 1, 2, SMALL_AREA));
        plan.walkAll();
        assertEquals(waiting == 0 ? 0 : 1, plan.warnings().size());
        if (waiting > 0) warning(plan.warnings(), null, waiting);
    }

    @Test void waitingWarningComesAfterOverflowAndHouseholdIdExhaustionWarnings() throws Exception {
        var config = settings("\"submissions\":5,\"enumerators\":1,\"interviewsPerDay\":1,"
                + "\"fieldworkDays\":1,\"id\":\"answer\",\"fields\":{\"answer\":{\"value\":7}},"
                + SMALL_AREA + "," + visitSettings(1, 0, 2));
        var plan = new Fieldwork(simple(), config);
        plan.walkAll();
        assertEquals(3, plan.warnings().size());
        warning(List.of(plan.warnings().get(0)), null, 5, 1);
        warning(List.of(plan.warnings().get(1)), "answer", 1, 3);
        warning(List.of(plan.warnings().get(2)), null, 1);
    }

    @Test void absentAvailabilityDisablesFailuresAndAppendsItsConfigurationWarning() throws Exception {
        var config = settings("\"submissions\":6,\"id\":\"unknown_id\","
                + "\"fields\":{\"unknown_override\":{\"value\":1}},"
                + "\"visits\":{\"field\":\"unknown_status\",\"revisitRate\":0,\"unreachedRate\":1}");
        var form = simple();
        var baseline = new Fieldwork(form, settings("\"submissions\":6,\"id\":\"unknown_id\","
                + "\"fields\":{\"unknown_override\":{\"value\":1}}"));
        var plan = new Fieldwork(form, config);
        assertEquals(baseline.configWarnings(), plan.configWarnings().subList(0, baseline.configWarnings().size()));
        assertEquals(baseline.configWarnings().size() + 1, plan.configWarnings().size());
        warning(List.of(plan.configWarnings().get(plan.configWarnings().size() - 1)), "unknown_status");
        plan.walkAll();
        assertTrue(plan.visits().stream().allMatch(v -> v.attempt() == 1 && v.found()));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void absentNumberWarnsButDoesNotDisableRevisits() throws Exception {
        var config = settings("\"submissions\":2,\"enumerators\":1,\"interviewsPerDay\":1,"
                + SMALL_AREA + ",\"visits\":{\"field\":\"status\",\"available\":\"yes\","
                + "\"number\":\"missing_attempt\",\"revisitRate\":1,\"unreachedRate\":0,\"maxVisits\":2}");
        var plan = new Fieldwork(simple(), config);
        assertEquals(1, plan.configWarnings().size());
        warning(plan.configWarnings(), "missing_attempt");
        plan.walkAll();
        assertEquals(List.of(1, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(List.of(false, true), plan.visits().stream().map(Visit::found).toList());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void bothAbsentNamesAreReportedAfterEarlierConfigurationWarnings() throws Exception {
        var config = settings("\"id\":\"missing_id\",\"visits\":{\"field\":\"missing_status\","
                + "\"number\":\"missing_attempt\"}");
        var warnings = new Fieldwork(simple(), config).configWarnings();
        assertEquals(3, warnings.size());
        assertTrue(warnings.get(0).contains("missing_id"));
        warning(warnings.subList(1, 3), "missing_status");
        warning(warnings.subList(1, 3), "missing_attempt");
    }

    /** Independent queue oracle: only observed visits, workday and the stated terminal rule. */
    static void checkQueue(Fieldwork plan, SimulationConfig config, List<Submission> rows, int max) throws Exception {
        Map<String, Visit> first = new HashMap<>();
        Map<String, Integer> latest = new LinkedHashMap<>();
        Map<String, Integer> waiting = new HashMap<>();
        List<Visit> visits = plan.visits();
        assertEquals(config.submissions(), visits.size());
        assertEquals(visits.size(), rows.size());
        for (int i = 0; i < visits.size(); i++) {
            Visit visit = visits.get(i);
            long today = day(config, visit);
            int position = i;
            String expected = waiting.entrySet().stream()
                    .filter(e -> visits.get(e.getValue()).enumerator() == visit.enumerator())
                    .filter(e -> day(config, visits.get(e.getValue())) < today)
                    .min(Comparator.comparingInt(Map.Entry::getValue)).map(Map.Entry::getKey).orElse(null);
            assertEquals(i % config.enumerators() + 1, visit.enumerator());
            assertEquals(i + 1, rows.get(i).number());
            assertEquals(visit.start(), rows.get(i).start());
            assertEquals(visit.id(), value(rows.get(i), "answer"));
            assertEquals(Integer.toString(visit.attempt()), value(rows.get(i), "attempt"));
            assertEquals(visit.found(), value(rows.get(i), "status").equals("yes"));
            if (expected == null) {
                assertEquals(1, visit.attempt(), "no household eligible for a revisit at " + position);
                assertFalse(first.containsKey(visit.id()), "unique pool must allocate a fresh household");
                first.put(visit.id(), visit);
            } else {
                assertEquals(expected, visit.id(), "oldest latest visit has priority at " + position);
                Visit previous = visits.get(latest.get(expected));
                Visit origin = first.get(expected);
                assertEquals(previous.attempt() + 1, visit.attempt());
                assertTrue(day(config, previous) < today);
                assertEquals(origin.enumerator(), visit.enumerator());
                assertEquals(origin.latitude(), visit.latitude());
                assertEquals(origin.longitude(), visit.longitude());
            }
            assertTrue(visit.attempt() >= 1 && visit.attempt() <= max);
            latest.put(visit.id(), i);
            if (!visit.found() && visit.attempt() < max) waiting.put(visit.id(), i);
            else waiting.remove(visit.id());
            long offset = Duration.between(config.start().plusDays(today), visit.start()).toSeconds();
            assertTrue(offset >= 0 && offset <= 9 * 3600);
            if (i >= config.enumerators()) assertTrue(Duration.between(
                    rows.get(i - config.enumerators()).end(), visit.start()).toMinutes() >= 10);
        }
        assertEquals(waiting.isEmpty() ? 0 : 1, plan.warnings().size());
        if (!waiting.isEmpty()) warning(plan.warnings(), null, waiting.size());
    }
}
