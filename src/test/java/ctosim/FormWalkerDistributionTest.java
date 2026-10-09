package ctosim;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed seeds, large samples, and seven-sigma tolerances avoid seed-specific golden sequences. */
@Timeout(180)
class FormWalkerDistributionTest {
    @Test void startDayAndMinuteOffsetsAreUniformAndIncludeBothMinuteEndpoints() throws Exception {
        SimulationConfig cfg = config("\"seed\":715937,\"start\":\"2040-02-28T23:59:37\",\"fieldworkDays\":3");
        FormWalker walker = new FormWalker(form("", "", ""), cfg);
        int draws = 9000;
        int[] days = new int[3];
        int[] minutes = new int[541];
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            assertStart(result, cfg);
            long offset = Duration.between(cfg.start(), result.start()).toMinutes();
            int day = (int) (offset / 1440), minute = (int) (offset % 1440);
            days[day]++;
            minutes[minute]++;
        }
        for (int day = 0; day < 3; day++) {
            assertUniformBin(days[day], draws, 1.0 / 3, "day " + day);
        }
        for (int minute = 0; minute <= 540; minute++) assertUniformBin(minutes[minute], draws, 1.0 / 541, "minute " + minute);
        assertTrue(minutes[0] > 0, "Zero minute offset is allowed");
        assertTrue(minutes[540] > 0, "540 minute offset is inclusive");
    }

    @Test void singleDayFieldworkUsesOnlyTheMinuteOffset() throws Exception {
        SimulationConfig cfg = config("\"seed\":189317,\"fieldworkDays\":1");
        FormWalker walker = new FormWalker(form("", "", ""), cfg);
        for (int n = 1; n <= 100; n++) {
            Submission result = walker.walk(n);
            assertStart(result, cfg);
            assertTrue(Duration.between(cfg.start(), result.start()).toMinutes() <= 540);
        }
    }

    @Test void clockAdvancesAreUniformWholeSecondsIncludingFiveAndSixty() throws Exception {
        FormWalker walker = new FormWalker(single("string", ""), completeConfig());
        int draws = 4000;
        int[] durations = new int[61];
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            assertElapsed(result, 1);
            durations[(int) seconds(result)]++;
        }
        for (int seconds = 5; seconds <= 60; seconds++) {
            assertTrue(durations[seconds] > 0, "Missing clock advance " + seconds);
            assertUniformBin(durations[seconds], draws, 1.0 / 56, "seconds " + seconds);
        }
    }

    @Test void fractionalRepeatOverrideProducesUniformInclusiveIntegerTargets() throws Exception {
        FormWalker walker = new FormWalker(repeat(false, false), config("""
            "seed":983713,"missingRate":0,"repeats":{"min":9,"max":9},
            "fields":{"row":{"min":0.2,"max":3.8}}
            """));
        int draws = 1200;
        int[] counts = new int[4];
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            int count = count(result, "row");
            assertTrue(count >= 1 && count <= 3);
            counts[count]++;
        }
        for (int count = 1; count <= 3; count++) assertUniformBin(counts[count], draws, 1.0 / 3, "repeat count " + count);
    }

    @Test void globalRepeatTargetsIncludeZeroAndAreUniform() throws Exception {
        FormWalker walker = new FormWalker(repeat(false, false), config("\"seed\":653821,\"missingRate\":0,\"repeats\":{\"min\":0,\"max\":3}"));
        int draws = 1200;
        int[] counts = new int[4];
        for (int n = 1; n <= draws; n++) {
            int count = count(walker.walk(n), "row");
            assertTrue(count >= 0 && count <= 3);
            counts[count]++;
        }
        for (int count = 0; count <= 3; count++) assertUniformBin(counts[count], draws, 0.25, "global count " + count);
    }

    @Test void nestedRepeatCountIsDrawnAgainForEachParentInstance() throws Exception {
        FormWalker walker = new FormWalker(repeat(false, true), config("""
            "seed":489317,"missingRate":0,"repeats":{"min":3,"max":3},"fields":{"child":{"min":0,"max":3}}
            """));
        int draws = 400;
        int[][] counts = new int[3][4];
        boolean differentWithinParentSet = false;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            assertEquals(3, count(result, "row"));
            Set<Integer> targets = new HashSet<>();
            for (int parent = 1; parent <= 3; parent++) {
                int children = count(result, "row[" + parent + "]/child");
                assertTrue(children >= 0 && children <= 3);
                counts[parent - 1][children]++;
                targets.add(children);
            }
            differentWithinParentSet |= targets.size() > 1;
        }
        assertTrue(differentWithinParentSet, "A single target must not be reused for every parent");
        for (int parent = 0; parent < 3; parent++) for (int count = 0; count <= 3; count++)
            assertUniformBin(counts[parent][count], draws, 0.25, "parent " + parent + " count " + count);
    }

    @Test void retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget() throws Exception {
        // Spec 09 gives no hints for disjunctions, so the constraint does not narrow proposals.
        // Each proposal succeeds with p=1/20. Exhaustion after 50 proposals has probability (19/20)^50.
        // This checks retry behavior without assuming a particular RNG implementation or seed mixer.
        FormWalker walker = new FormWalker(single("int", "constraint=\". = 1 or . = 1\""), config("""
            "seed":395173,"missingRate":0,"integerRange":{"min":1,"max":20}
            """));
        int draws = 2500, failures = 0;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            assertElapsed(result, 1);
            if (result.answered() == 0) {
                failures++;
                assertEquals("", value(result, "answer"));
                assertWarning(result, "answer", "constraint");
            } else {
                assertEquals(1, result.answered());
                assertEquals("1", value(result, "answer"));
                assertTrue(result.warnings().isEmpty(), "Rejected intermediate proposals are not final warnings");
            }
        }
        assertUniformBin(failures, draws, Math.pow(19.0 / 20, 50), "Exhausted retry budget");
    }

    @Test void fieldMissingRateOverridesGlobalRateDuringWalking() throws Exception {
        LoadedForm loaded = form("<global/><local/><required/>", bind("global", "string", "")
                + bind("local", "string", "") + bind("required", "string", "required=\"true()\""),
                input("global") + input("local") + input("required"));
        FormWalker walker = new FormWalker(loaded, config("\"seed\":186311,\"missingRate\":0.2,\"fields\":{\"local\":{\"missingRate\":0.7}}"));
        int draws = 2000, globalBlanks = 0, localBlanks = 0;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            boolean globalBlank = value(result, "global").isEmpty(), localBlank = value(result, "local").isEmpty();
            if (globalBlank) globalBlanks++;
            if (localBlank) localBlanks++;
            assertFalse(value(result, "required").isEmpty());
            assertEquals(3 - (globalBlank ? 1 : 0) - (localBlank ? 1 : 0), result.answered());
            assertElapsed(result, 3);
        }
        assertUniformBin(globalBlanks, draws, 0.2, "Global missing rate");
        assertUniformBin(localBlanks, draws, 0.7, "Field missing rate");
    }
}
