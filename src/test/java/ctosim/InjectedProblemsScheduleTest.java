package ctosim;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change JVM time zone")
class InjectedProblemsScheduleTest {
    @ParameterizedTest @ValueSource(strings = {"offhours", "overlap"})
    void everyStartFieldProducesAnOrderedRecordUsingWideExportFormatting(String name) throws Exception {
        var r = run(ordinary(600, true, false, false), config(20, 10, "\"id\":\"id\"").withProblems(only(name, .25)));
        assertEquals(5, targets(r, name).size()); assertEquals(10, r.plan().problems().size());
        for (int n : targets(r, name)) {
            var records = r.plan().problems().stream().filter(p -> p.submission() == n).toList();
            assertEquals(List.of("start_z", "start_a"), records.stream().map(InjectedProblem::field).toList());
            for (var p : records) {
                assertEquals(r.target(p).start().format(WIDE_TIME), p.value()); assertEquals("", p.cleanValue());
                if (name.equals("offhours")) assertEquals("", p.relatedKey());
                else assertEquals(key(r.submissions().get(n - 11)), p.relatedKey());
            }
        }
        assertMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"offhours", "overlap"})
    void noStartFieldUsesOneIsoTimestampRecordIncludingSeconds(String name) throws Exception {
        var r = run(singleTimed(600), config(2, 1, "").withProblems(only(name, .5)));
        assertEquals(1, r.plan().problems().size()); var p = r.plan().problems().get(0);
        assertEquals("", p.field()); assertEquals(r.target(p).start().format(FALLBACK_TIME), p.value());
        assertEquals("", p.cleanValue());
    }
    @Test void offhoursStartsTwoToFourHoursAfterClosingAndKeepsThePreviousVillage() throws Exception {
        var r = run(ordinary(600, false, false, true), config(80, 40, "").withProblems(only("offhours", .5)));
        assertEquals(40, targets(r, "offhours").size());
        for (var p : representatives(r)) {
            int previous = p.submission() - r.config().enumerators(); var s = r.target(p);
            var closing = r.config().start().plusDays(day(r, previous)).plusHours(9);
            assertFalse(s.start().isBefore(closing.plusHours(2))); assertFalse(s.start().isAfter(closing.plusHours(4)));
            assertFalse(s.start().isBefore(r.submissions().get(previous - 1).end().plusMinutes(10)));
            assertTrue(distance(r.visit(previous), r.visit(p.submission())) <= 3000.0001);
            assertEquals(r.visit(previous).enumerator(), r.visit(p.submission()).enumerator()); ordinary(r, p.submission());
        }
    }
    @Test void offhoursWaitsAtLeastTenMinutesAfterALatePreviousEnd() throws Exception {
        var r = run(singleTimed(13 * 3600), config(2, 1, "").withProblems(only("offhours", .5)));
        assertEquals(Set.of(2), targets(r, "offhours"));
        assertEquals(r.submissions().get(0).end().plusMinutes(10), r.submissions().get(1).start());
    }
    @Test void offhoursWithinNextDaysWorkIsSkippedWithAnOrdinaryVisitAndWarning() throws Exception {
        var f = singleTimed(25 * 3600); var c = config(2, 1, "");
        var clean = run(f, c); var r = run(f, c.withProblems(only("offhours", .5)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void overlapSharesAtLeastFiveMinutesWithPreviousInterviewAndKeepsOwnId() throws Exception {
        var r = run(ordinary(600, false, false, true), config(60, 30, "\"id\":\"id\"").withProblems(only("overlap", .5)));
        assertEquals(30, targets(r, "overlap").size());
        for (var p : representatives(r)) {
            var previous = r.related(p); var target = r.target(p);
            assertEquals(p.submission() - r.config().enumerators(), previous.number());
            Duration offset = Duration.between(previous.start(), target.start());
            assertEquals(0, offset.getNano()); assertFalse(offset.isNegative());
            assertTrue(offset.getSeconds() <= seconds(previous) - 300);
            var commonEnd = previous.end().isBefore(target.end()) ? previous.end() : target.end();
            assertTrue(Duration.between(target.start(), commonEnd).getSeconds() >= 300);
            assertEquals(day(r, previous.number()), day(r, target.number()));
            assertTrue(distance(r.visit(previous.number()), r.visit(target.number())) <= 3000.0001);
            assertNotEquals(value(previous, "id"), value(target, "id")); ordinary(r, target.number());
        }
        assertMetadata(r);
    }
    @Test void previousDurationExactlyFiveMinutesAllowsOnlyItsStart() throws Exception {
        var r = run(singleTimed(300), config(2, 1, "").withProblems(only("overlap", .5)));
        assertEquals(Set.of(2), targets(r, "overlap"));
        assertEquals(r.submissions().get(0).start(), r.submissions().get(1).start());
    }
    @ParameterizedTest @ValueSource(ints = {0, 299})
    void previousDurationBelowFiveMinutesSkipsOverlapAndWarns(int minimum) throws Exception {
        var f = singleTimed(minimum); var c = config(2, 1, "");
        var clean = run(f, c); var r = run(f, c.withProblems(only("overlap", .5)));
        assertTrue(r.plan().problems().isEmpty()); assertWarning(r, null, 2);
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions()); assertEquals(clean.plan().visits(), r.plan().visits());
    }
    @Test void interviewAfterOffhoursIsFirstOfTheNextDayEvenWhenQuotaRemains() throws Exception {
        int checked = 0; var f = singleTimed(600); var c = config(3, 1, "\"interviewsPerDay\":10");
        for (int seed = 40; seed < 65; seed++) {
            var r = run(f, c.withSeed(seed).withProblems(only("offhours", 1.0 / 3)));
            if (!targets(r, "offhours").equals(Set.of(2))) continue;
            checked++; assertEquals(1, day(r, 3));
            long delay = FieldworkTestSupport.wholeMinutes(c.start().plusDays(1), r.submissions().get(2).start());
            assertTrue(delay >= 0 && delay <= 60); ordinary(r, 3);
        }
        assertTrue(checked > 0);
    }
    @Test void afterOverlapTheScheduleUsesTheLaterOfBothEnds() throws Exception {
        // Compare against the later end, without prescribing which interval ends last.
        var f = ordinary(600, false, false, false); var c = config(3, 1, "\"interviewsPerDay\":10");
        int checked = 0;
        for (int seed = 100; seed < 140; seed++) {
            var r = run(f, c.withSeed(seed).withProblems(only("overlap", 1.0 / 3)));
            if (!targets(r, "overlap").equals(Set.of(2))) continue;
            checked++; var first = r.submissions().get(0); var second = r.submissions().get(1); var third = r.submissions().get(2);
            var later = first.end().isAfter(second.end()) ? first.end() : second.end();
            long minutes = FieldworkTestSupport.wholeMinutes(later, third.start());
            assertTrue(minutes >= 10 && minutes <= 40); assertEquals(0, day(r, 3)); ordinary(r, 3);
        }
        assertTrue(checked > 0);
    }
    @Test void warningsForScheduleProblemsFollowTableOrderAfterPlanWarnings() throws Exception {
        // Quota 1 forces fieldwork overflow; short ordinary interviews cannot overlap.
        var c = SimulationConfig.parse("{\"seed\":91,\"submissions\":12,\"enumerators\":1,\"fieldworkDays\":1,"
                + "\"interviewsPerDay\":1,\"missingRate\":0,\"specialRate\":0}", "warnings.json");
        var clean = run(singleTimed(0), c); assertEquals(1, clean.plan().warnings().size());
        var r = run(singleTimed(0), c.withProblems(new ProblemRates(0, 0, 0, 0, 0, .25, .25)));
        assertEquals(clean.plan().warnings(), r.plan().warnings().subList(0, clean.plan().warnings().size()));
        assertEquals(5, r.plan().warnings().size());
        assertTrue(r.plan().warnings().get(4).contains("travel"));
        assertTrue(r.plan().problems().isEmpty());
    }
    @Test void offhoursAndOverlapOffsetsAreUniformAcrossTheirAllowedRanges() throws Exception {
        for (String name : List.of("offhours", "overlap")) {
            int draws = 400; var r = run(singleTimed(900), config(draws * 2, draws, "").withProblems(only(name, .5)));
            assertEquals(draws, targets(r, name).size()); int[] bins = new int[4];
            for (var p : representatives(r)) {
                double fraction;
                if (name.equals("offhours")) {
                    fraction = Duration.between(r.config().start().plusHours(11), r.target(p).start()).getSeconds() / 7200.0;
                } else fraction = Duration.between(r.related(p).start(), r.target(p).start()).getSeconds() / 600.0;
                assertTrue(fraction >= 0 && fraction <= 1); bins[Math.min(3, (int) (fraction * 4))]++;
            }
            for (int bin = 0; bin < 4; bin++) assertUniformBin(bins[bin], draws, .25, name + " time quartile " + bin);
        }
    }
}
