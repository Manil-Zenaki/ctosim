package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendment2UploadTimeTest {
    private static long delay(Submission submission) {
        assertNotNull(submission.submitted());
        Duration delay = Duration.between(submission.end(), submission.submitted());
        assertEquals(0, delay.getNano(), "Upload adds whole minutes");
        assertEquals(0, delay.getSeconds() % 60, "Upload preserves the end's seconds");
        long minutes = delay.toMinutes();
        assertTrue(minutes >= 1 && minutes <= 240, "Upload delay must be in 1..240: " + minutes);
        return minutes;
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 57, Integer.MAX_VALUE})
    void uploadedTimeAddsOneToTwoHundredFortyMinutesToTheEnd(int number) throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"86400.1\""),
                config("\"start\":\"2026-12-31T23:59:17\",\"fieldworkDays\":1,\"missingRate\":0"));
        Submission result = walker.walk(number);
        long minutes = delay(result);
        assertEquals(result.end().plusMinutes(minutes), result.submitted());
        assertEquals(86401, seconds(result), "Upload time is not interview time");
        assertEquals(2027, result.submitted().getYear());
        assertEquals(result.end().getSecond(), result.submitted().getSecond());
    }

    @Test void uniformUploadDelayIncludesBothEndpoints() throws Exception {
        var walker = new FormWalker(amended2("", "", ""), settings(0).withSeed(138931));
        int draws = 4800;
        int[] frequencies = new int[241];
        int[] quarters = new int[4];
        for (int n = 1; n <= draws; n++) {
            int minutes = (int) delay(walker.walk(n));
            frequencies[minutes]++;
            quarters[(minutes - 1) / 60]++;
        }
        assertTrue(frequencies[1] > 0, "1 minute is included");
        assertTrue(frequencies[240] > 0, "240 minutes is included");
        for (int m = 1; m <= 240; m++) assertUniformBin(frequencies[m], draws, 1.0 / 240, "minute " + m);
        for (int q = 0; q < 4; q++) assertUniformBin(quarters[q], draws, 0.25, "quarter " + q);
    }

    @Test void delayDependsOnlyOnSeedAndSubmissionNumberEvenWhenOtherRandomDrawsChange() throws Exception {
        var empty = new FormWalker(amended2("", "", ""), config("\"seed\":713,\"enumerators\":1"));
        var busy = new FormWalker(timed("ct:minimum-seconds=\"600\""), config("\"seed\":713,"
                + "\"start\":\"2040-02-28T23:59:17\",\"fieldworkDays\":1,\"timeZone\":\"+05:45\","
                + "\"enumerators\":120,\"submissions\":1,\"speedViolationRate\":1,\"missingRate\":0"));
        for (int n = 1; n <= 50; n++) assertEquals(delay(empty.walk(n)), delay(busy.walk(n)), "submission " + n);
    }

    @Test void uploadTimestampReplaysAcrossWalkOrderEqualConfigurationsAndSeparateLoads() throws Exception {
        var first = new FormWalker(timed("ct:minimum-seconds=\"70\""), settings(0.37));
        Submission expected = first.walk(17);
        first.walk(23);
        assertEquals(expected.submitted(), first.walk(17).submitted());
        var second = new FormWalker(timed("ct:minimum-seconds=\"70\""), settings(0.37));
        second.walk(1);
        assertEquals(expected.submitted(), second.walk(17).submitted());
        delay(expected);
    }

    @Test void uploadDelaysVaryWithSubmissionNumberAndSeedOverASample() throws Exception {
        LoadedForm form = amended2("", "", "");
        var first = new FormWalker(form, settings(0).withSeed(10));
        var second = new FormWalker(form, settings(0).withSeed(11));
        java.util.Set<Long> delays = new java.util.HashSet<>();
        boolean seedChanged = false;
        for (int n = 1; n <= 40; n++) {
            long a = delay(first.walk(n));
            delays.add(a);
            seedChanged |= a != delay(second.walk(n));
        }
        assertTrue(delays.size() > 1);
        assertTrue(seedChanged);
    }

    @ParameterizedTest @ValueSource(ints = {1, 31, Integer.MAX_VALUE})
    void submissionsBuiltByTheFactoryUseExactlyTheProvidedEndAsUploadTime(int number) {
        LocalDateTime start = LocalDateTime.of(2026, 12, 31, 23, 58, 5, 123456789);
        LocalDateTime end = start.plusMinutes(9).plusSeconds(17);
        Submission result = Submission.of(number, "<data/>", start, end);
        assertEquals(end, result.submitted(), "Factory must not synthesize a random upload delay or truncate precision");
    }

    @Test void aZeroLengthFactoryInterviewAlsoUsesItsEndAsSubmitted() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals(time, Submission.of(1, "", time, time).submitted());
    }
}
