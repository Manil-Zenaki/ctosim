package ctosim;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment5Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment5CascadeTest extends FieldworkAmendment4Support {
    @BeforeEach void copyVisitTables() throws Exception {
        for (String name : List.of("districts", "villages", "households")) {
            try (var in = getClass().getResourceAsStream("/ctosim/fieldwork-plan/amendment5/" + name + ".csv")) {
                assertNotNull(in);
                Files.copy(in, media.resolve("a5-" + name + ".csv"));
            }
        }
    }

    private LoadedForm visitCascade() throws Exception {
        return visitCascade(0);
    }

    private LoadedForm visitCascade(int delaySeconds) throws Exception {
        return attached("<before/><district/><village/><answer/><status/><attempt/><after/>"
                        + (delaySeconds > 0 ? "<delay/>" : ""),
                bind("before", "geopoint", MINUTE) + bind("district", "string", MINUTE)
                        + bind("village", "string", MINUTE) + bind("answer", "string", MINUTE)
                        + bind("status", "string", MINUTE) + bind("attempt", "int", MINUTE)
                        + bind("after", "geopoint", MINUTE)
                        + (delaySeconds > 0 ? bind("delay", "string", "relevant=\"/data/attempt = 2 and /data/answer = '0007'\""
                                + " ct:minimum-seconds=\"" + delaySeconds + "\"") : ""),
                input("before") + select("district", "search('a5-districts')", "code")
                        + select("village", "search('a5-villages', 'matches', 'district', /data/district)", "code")
                        + control("search('a5-households', 'matches', 'village', /data/village)", "code")
                        + choices("status", "yes", "absent") + input("attempt") + input("after")
                        + (delaySeconds > 0 ? input("delay") : ""));
    }

    private void villageB(double longitude) throws Exception {
        var path = media.resolve("a5-villages.csv");
        Files.writeString(path, Files.readString(path).replace("B,D2,0,0.2", "B,D2,0," + longitude));
    }

    @ParameterizedTest @ValueSource(strings = {"left", "right"})
    void failedAvailabilityUsesTheChoicesOfferedAfterEarlierAnswers(String route) throws Exception {
        try (var in = getClass().getResourceAsStream("/ctosim/fieldwork-plan/amendment5/statuses.csv")) {
            assertNotNull(in);
            Files.copy(in, media.resolve("a5-statuses.csv"));
        }
        var form = attached("<route/><status/>", bind("route", "string", "") + bind("status", "string", ""),
                input("route") + select("status", "search('a5-statuses', 'matches', 'route', /data/route)", "code"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"route\":{\"value\":\"" + route + "\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(1, false));
        assertEquals(route.equals("left") ? "absent" : "busy", value(row, "status"));
    }

    @Test void cascadeRevisitsReplayTheHouseholdsIdAndEveryUpperAnswer() throws Exception {
        var config = config5(6, 1, 2, 0, 1, 3, LOCATED + "," + FORCE_A);
        var plan = new Fieldwork(visitCascade(), config);
        var rows = plan.walkAll();
        assertEquals(List.of("A", "B", "A", "B", "A", "B"), column5(rows, "village"));
        assertEquals(List.of("D1", "D2", "D1", "D2", "D1", "D2"), column5(rows, "district"));
        assertEquals(List.of("0007", "é02", "0007", "é02", "0007", "é02"), column5(rows, "answer"));
        for (int i = 0; i < 6; i++) {
            Visit visit = plan.visits().get(i);
            assertEquals(i / 2 + 1, visit.attempt());
            assertFalse(visit.found());
            if (i < 2) assertNull(visit.id(), "first cascade IDs are given at the question");
            else {
                assertEquals(value(rows.get(i), "answer"), visit.id(), "revisits carry the household's ID");
                assertEquals(plan.visits().get(i % 2).latitude(), visit.latitude());
                assertEquals(plan.visits().get(i % 2).longitude(), visit.longitude());
            }
            gpsFollowsVisit(rows.get(i), visit, "before", "after");
        }
        assertTrue(plan.warnings().isEmpty(), "revisiting an existing household is not ID exhaustion");
    }

    @Test void failedFirstVisitsReceiveIdsImmediatelySoAnotherHouseholdCannotTakeThem() throws Exception {
        var plan = new Fieldwork(visitCascade(), config5(2, 1, 2, 0, 1, 3, FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of("0007", "é02"), column5(rows, "answer"));
        assertEquals(List.of(1, 1), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(1, plan.warnings().size(), "only the waiting warning, not an ID-reuse warning");
        warning(plan.warnings(), null, 2);
    }

    @Test void finishingRevisitsDoesNotCountThemInTheCascadeReuseWarning() throws Exception {
        var plan = new Fieldwork(visitCascade(), config5(8, 1, 2, 1, 0, 2, FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of(1, 1, 2, 2, 1, 1, 2, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer", 2);
        for (int first : List.of(0, 1, 4, 5)) {
            assertEquals(value(rows.get(first), "answer"), value(rows.get(first + 2), "answer"));
        }
    }

    @Test void revisitsRecoverEachHouseholdsPlaceEvenWhenTheDaysPlacesDiffer() throws Exception {
        // A has one id, B two: on day one A is exhausted, whereas B remains viable.
        Files.writeString(media.resolve("a5-households.csv"), "code,village\n0007,A\né02,B\nB2,B\n");
        var form = visitCascade();
        // Three slots: day zero A,B,B. Day one must recover all three original places.
        var plan = new Fieldwork(form, config5(6, 1, 3, 1, 0, 2, FORCE_A));
        var rows = plan.walkAll();
        for (int i = 0; i < 3; i++) {
            assertEquals(value(rows.get(i), "village"), value(rows.get(i + 3), "village"));
            assertEquals(value(rows.get(i), "district"), value(rows.get(i + 3), "district"));
        }
        assertTrue(plan.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1})
    void onlyATripStrictlyPastClosingMovesARevisitToTheNextDay(int secondsAfterClosing) throws Exception {
        var config = config5(4, 1, 2, 0, 1, 3, LOCATED + "," + FORCE_A);
        var probe = new Fieldwork(visitCascade(), config);
        var baseline = probe.walkAll();
        assertEquals(1, day(config, probe.visits().get(3)));
        // The extra question is relevant only in submission 3. Its exact minimum
        // increases that interview's duration without changing earlier household points.
        long remaining = java.time.Duration.between(baseline.get(3).start(),
                config.start().plusDays(1).plusHours(9)).getSeconds();
        assertTrue(remaining > 60);
        var plan = new Fieldwork(visitCascade(Math.toIntExact(remaining + secondsAfterClosing)), config);
        var rows = plan.walkAll();
        assertEquals("é02", value(rows.get(3), "answer"));
        assertEquals(2, plan.visits().get(3).attempt());
        if (secondsAfterClosing <= 0) {
            assertEquals(config.start().plusDays(1).plusHours(9).plusSeconds(secondsAfterClosing), rows.get(3).start());
        } else {
            assertEquals(2, day(config, plan.visits().get(3)));
            long minutes = wholeMinutes(config.start().plusDays(2), rows.get(3).start());
            assertTrue(minutes >= 0 && minutes <= 60);
        }
    }

    @Test void sameDayRevisitTripsUseHouseholdPointsAndCeilingOfTwoMinutesPerKilometre() throws Exception {
        var form = visitCascade();
        var baseline = new Fieldwork(form, config5(6, 1, 2, 0, 1, 3, FORCE_A + "," + SMALL_AREA));
        var baselineRows = baseline.walkAll();
        var located = new Fieldwork(form, config5(6, 1, 2, 0, 1, 3, LOCATED + "," + FORCE_A));
        var rows = located.walkAll();
        for (int i : List.of(3, 5)) {
            Visit previous = located.visits().get(i - 1);
            Visit next = located.visits().get(i);
            assertEquals(day(config5(6, 1, 2, 0, 1, 3, ""), previous),
                    day(config5(6, 1, 2, 0, 1, 3, ""), next));
            double metres = distance(previous.latitude(), previous.longitude(), next.latitude(), next.longitude());
            assertTrue(metres > 3000);
            long ordinaryGap = wholeMinutes(baselineRows.get(i - 1).end(), baselineRows.get(i).start());
            long charge = (long) Math.ceil(2 * metres / 1000);
            assertEquals(ordinaryGap + charge, wholeMinutes(rows.get(i - 1).end(), rows.get(i).start()));
        }
    }

    @Test void tripsOfAtMostThreeKilometresDoNotAddTravelTime() throws Exception {
        villageB(0);
        var form = visitCascade();
        var baseline = new Fieldwork(form, config5(4, 1, 2, 0, 1, 2, FORCE_A + "," + SMALL_AREA));
        var ordinary = baseline.walkAll();
        var located = new Fieldwork(form, config5(4, 1, 2, 0, 1, 2, LOCATED + "," + FORCE_A));
        var rows = located.walkAll();
        Visit a = located.visits().get(2), b = located.visits().get(3);
        assertTrue(distance(a.latitude(), a.longitude(), b.latitude(), b.longitude()) <= 3000.000001);
        assertEquals(wholeMinutes(ordinary.get(2).end(), ordinary.get(3).start()),
                wholeMinutes(rows.get(2).end(), rows.get(3).start()));
    }

    @Test void aMovePastClosingRechecksWaitingHouseholdsOnTheFinalScheduledDay() throws Exception {
        villageB(20); // A to B cannot fit in a workday at 30 km/h.
        var config = config5(2, 1, 4, 1, 0, 2, LOCATED + "," + FORCE_A);
        var plan = new Fieldwork(visitCascade(), config);
        var rows = plan.walkAll();
        assertEquals(1, day(config, plan.visits().get(1)));
        assertEquals(2, plan.visits().get(1).attempt(), "the deferred new B household is replaced by waiting A");
        assertEquals(List.of("A", "A"), column5(rows, "village"));
        assertEquals(value(rows.get(0), "answer"), value(rows.get(1), "answer"));
        assertEquals(plan.visits().get(0).latitude(), plan.visits().get(1).latitude());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void waitingWarningFollowsMissingVillageColumnWarnings() throws Exception {
        Files.writeString(media.resolve("a5-villages.csv"), "code,district\nA,D1\nB,D2\n");
        var plan = new Fieldwork(visitCascade(), config5(2, 1, 2, 0, 1, 2, LOCATED + "," + FORCE_A));
        plan.walkAll();
        assertEquals(3, plan.warnings().size());
        warning(plan.warnings().subList(0, 2), "lat_col");
        warning(plan.warnings().subList(0, 2), "lon_col");
        warning(List.of(plan.warnings().get(2)), null, 2);
    }
}
