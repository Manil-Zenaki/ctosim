package ctosim;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment3Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Fieldwork walks temporarily change the JVM time zone")
class InjectedAmendment3ScheduleTest {
    @Test void oneTargetWithoutASuccessorResumesAtNextDaysOpeningPlusWholeMinutes() throws Exception {
        var r = run(timeline(10, 90, 1), settings(1, 1, "").withProblems(left(1)));
        var s = r.submissions().get(0); var pause = interruption(s, 10);
        minutesBetween(opening(r, s).plusDays(1), pause.resume(), 0, 60);
        assertEquals("", record(r, 1).relatedKey());
        assertFalse(s.start().isBefore(opening(r, s))); assertFalse(s.start().isAfter(opening(r, s).plusHours(9)));
    }

    @Test void pauseOccursAfterTheFirstQuestionReachingTheDrawnFraction() throws Exception {
        var r = run(timeline(10, 90, 1), settings(80, 80, "").withProblems(left(1)));
        for (var s : r.submissions()) {
            var pause = interruption(s, 10);
            assertTrue(pause.question() >= 2 && pause.question() <= 8, "Threshold lies in [.2D,.8D]");
            assertEquals(pause.question() * 90L, pause.screenAtPause());
            assertEquals(900, total(s));
        }
    }

    @Test void singleQuestionCanTriggerItsPauseAtTheEndOfHandling() throws Exception {
        var r = run(timeline(1, 90, 1), settings(1, 1, "").withProblems(left(1)));
        var s = r.submissions().get(0); var pause = interruption(s, 1);
        assertEquals(1, pause.question()); assertEquals(90, total(s));
        assertEquals(pause.resume(), s.end());
    }

    @Test void fractionUsesScreenSecondsRatherThanTheFractionOfQuestionsAnswered() throws Exception {
        var r = run(timeline(new int[]{60, 60, 6000, 60, 60}, 1), settings(20, 20, "").withProblems(left(1)));
        for (var s : r.submissions()) {
            assertEquals(6240, total(s));
            var pause = interruption(s, 5);
            assertEquals(3, pause.question(), "Every threshold in [.2D,.8D] first crosses inside q3");
            assertEquals(6120, pause.screenAtPause(), "Pause follows the whole question, not partway through it");
        }
    }

    @Test void nextDayIsCountedFromWorkOpeningRatherThanTheCalendarDateOfThePause() throws Exception {
        var c = SimulationConfig.parse("{\"seed\":481,\"submissions\":1,\"enumerators\":1,"
                + "\"start\":\"2026-03-02T22:17:23\",\"timeZone\":\"+03:00\","
                + "\"missingRate\":0,\"specialRate\":0,\"speedViolationRate\":0}", "night-shift.json").withProblems(left(1));
        var r = run(timeline(1, 10800, 1), c); var a = r.submissions().get(0);
        var pause = interruption(a, 1);
        assertTrue(pause.begin().toLocalDate().isAfter(a.start().toLocalDate()));
        minutesBetween(c.start().plusDays(1), pause.resume(), 0, 60);
    }

    @Test void pausePreservesOrdinaryAnswersScreenDurationAndFinalisationMetadata() throws Exception {
        var r = run(timeline(10, 90, 1), settings(3, 3, "").withProblems(left(1)));
        for (var s : r.submissions()) {
            var clean = r.clean(s.number()); var pause = interruption(s, 10);
            assertEquals(total(clean), total(s)); assertEquals(clean.answered(), s.answered());
            for (int i = 1; i <= 10; i++) {
                assertEquals(value(clean, "q" + i), value(s, "q" + i));
                assertEquals(screen(clean, i), screen(s, i));
            }
            assertEquals(clean.end().plusSeconds(pause.seconds()), s.end());
            assertEquals(s.start(), OffsetDateTime.parse(value(s, "start_z")).toLocalDateTime());
            assertEquals(s.end(), OffsetDateTime.parse(value(s, "finished")).toLocalDateTime());
            assertEquals(Duration.between(clean.end(), clean.submitted()), Duration.between(s.end(), s.submitted()));
        }
    }

    @Test void sameDaySuccessorIsEntirelyInsideThePauseAndBothGapsAreWholeMinutes() throws Exception {
        var r = find(timeline(10, 90, 1), settings(2, 1, "").withProblems(left(.5)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        enclosed(r, 1, 2, 10);
        var a = r.submissions().get(0); var b = r.submissions().get(1);
        minutesBetween(interruption(a, 10).begin(), b.start(), 10, 40);
        assertEquals(day(r, 1), day(r, 2)); assertTrue(distance(r.visit(1), r.visit(2)) <= 3000.0001);
        ordinary(r, 2);
    }

    @Test void englobedInterviewIsTheEnumeratorsNextSubmissionRatherThanTheNextGlobalNumber() throws Exception {
        var r = find(timeline(4, 90, 1), settings(6, 3, "").withProblems(left(1.0 / 6)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        enclosed(r, 1, 4, 4);
        for (int n : new int[]{2, 3, 5, 6}) ordinary(r, n);
        var clean = run(r.form(), r.config().withProblems(null));
        for (int n : new int[]{2, 3, 5, 6}) {
            assertEquals(clean.visit(n), r.visit(n));
            FieldworkTestSupport.same(clean.submissions().get(n - 1), r.submissions().get(n - 1));
        }
    }

    @Test void afterEnglobingTheFollowingInterviewStartsAfterTheTargetsFinalisation() throws Exception {
        var r = find(timeline(10, 90, 1), settings(3, 1, "\"interviewsPerDay\":10").withProblems(left(1.0 / 3)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        enclosed(r, 1, 2, 10);
        minutesBetween(r.submissions().get(0).end(), r.submissions().get(2).start(), 10, 40);
        assertEquals(day(r, 1), day(r, 3)); assertTrue(distance(r.visit(1), r.visit(3)) <= 3000.0001);
        ordinary(r, 3);
    }

    @Test void englobedInterviewMayStartOnTheSameDayEvenWhenTheDailyQuotaIsOne() throws Exception {
        var r = find(timeline(4, 90, 1), settings(2, 1, "\"interviewsPerDay\":1").withProblems(left(.5)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        enclosed(r, 1, 2, 4);
        assertEquals(day(r, 1), day(r, 2));
        minutesBetween(interruption(r.submissions().get(0), 4).begin(), r.submissions().get(1).start(), 10, 40);
    }

    @Test void pauseAfterClosingPlacesTheEnglobedInterviewAtNextWorkingDaysOpening() throws Exception {
        // A single ten-hour question guarantees t is after closing, independently of the fraction.
        var r = find(timeline(1, 36000, 1), settings(3, 1, "\"interviewsPerDay\":10").withProblems(left(1.0 / 3)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        enclosed(r, 1, 2, 1);
        var a = r.submissions().get(0); var b = r.submissions().get(1); var c = r.submissions().get(2);
        minutesBetween(opening(r, a).plusDays(1), b.start(), 0, 60);
        assertTrue(c.start().isAfter(a.end().plusMinutes(9)));
        assertFalse(c.start().isAfter(opening(r, c).plusHours(9)));
        assertTrue(day(r, 3) >= 2, "A and B finalise after closing on day 1, so C must wait");
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void englobedStartAtClosingIsAllowedButOneSecondLaterMovesToTheNextDay(int extraSecond) throws Exception {
        var control = find(timeline(1, 90, 1), settings(2, 1, "").withProblems(left(.5)),
                x -> targets(x, "leftOpen").equals(Set.of(1)));
        var original = control.submissions().get(0);
        long gap = minutesBetween(interruption(original, 1).begin(), control.submissions().get(1).start(), 10, 40);
        var closing = opening(control, original).plusHours(9);
        int minimum = Math.toIntExact(Duration.between(original.start(), closing).getSeconds() - gap * 60 + extraSecond);
        // With one question, the pause always follows that question. The seed/number-only
        // start-gap draw is unchanged when its minimum duration puts the candidate at closing.
        var r = run(timeline(1, minimum, 1), control.config());
        enclosed(r, 1, 2, 1);
        if (extraSecond == 0) assertEquals(closing, r.submissions().get(1).start());
        else minutesBetween(opening(r, r.submissions().get(0)).plusDays(1), r.submissions().get(1).start(), 0, 60);
    }

    @Test void anOvernightResumptionContinuesSchedulingOnTheDayTheTargetEnded() throws Exception {
        // Select a target late enough in its own workday that its pause begins after closing,
        // but whose ordinary duration is short enough for B and A to finish next morning.
        var form = timeline(10, 90, 1);
        var r = find(form, settings(24, 1, "\"interviewsPerDay\":100").withProblems(left(1.0 / 24)), x -> {
            int a = targets(x, "leftOpen").iterator().next();
            if (a >= 23 || record(x, a).relatedKey().isEmpty()) return false;
            return day(x, a + 1) == day(x, a) + 1;
        });
        int a = targets(r, "leftOpen").iterator().next(); enclosed(r, a, a + 1, 10);
        var target = r.submissions().get(a - 1); var inner = r.submissions().get(a);
        minutesBetween(opening(r, target).plusDays(1), inner.start(), 0, 60);
        minutesBetween(target.end(), r.submissions().get(a + 1).start(), 10, 40);
        assertEquals(day(r, a + 1), day(r, a + 2));
        assertTrue(distance(r.visit(a + 1), r.visit(a + 2)) <= 3000.0001);
    }

    @ParameterizedTest @ValueSource(ints = {1, 4})
    void consecutiveLeftOpenTargetsAreNeverSkippedAndDoNotEnglobeEachOther(int enumerators) throws Exception {
        var r = run(timeline(4, 90, 1), settings(8, enumerators, "\"interviewsPerDay\":10").withProblems(left(1)));
        assertEquals(8, targets(r, "leftOpen").size());
        for (var s : r.submissions()) {
            assertEquals("", record(r, s.number()).relatedKey());
            var pause = interruption(s, 4);
            minutesBetween(opening(r, s).plusDays(1), pause.resume(), 0, 60);
            if (s.number() > enumerators) {
                var previous = r.submissions().get(s.number() - enumerators - 1);
                minutesBetween(previous.end(), s.start(), 10, 40);
            }
        }
    }
}
