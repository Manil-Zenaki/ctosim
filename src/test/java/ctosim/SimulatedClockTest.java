package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SimulatedClockTest {
    private static final LocalDateTime START = LocalDateTime.of(2040, 2, 28, 23, 59, 59, 999_999_998);

    @Test void nullStartIsRejected() {
        assertThrows(NullPointerException.class, () -> new SimulatedClock(null));
    }

    @Test void nowInitiallyReturnsTheExactStartIncludingNanoseconds() {
        assertEquals(START, new SimulatedClock(START).now());
    }

    @Test void readingTheClockRepeatedlyDoesNotAdvanceIt() {
        var clock = new SimulatedClock(START);
        for (int i = 0; i < 10_000; i++) assertEquals(START, clock.now());
    }

    @Test void realElapsedTimeDoesNotAdvanceTheClock() throws InterruptedException {
        var clock = new SimulatedClock(START);
        Thread.sleep(25);
        assertEquals(START, clock.now());
    }

    @Test void zeroDurationIsAllowedAndLeavesTheClockUnchanged() {
        var clock = new SimulatedClock(START);
        assertDoesNotThrow(() -> clock.advance(Duration.ZERO));
        assertEquals(START, clock.now());
    }

    @Test void nullAdvanceIsRejectedWithoutChangingTheClock() {
        var clock = new SimulatedClock(START);
        assertThrows(NullPointerException.class, () -> clock.advance(null));
        assertEquals(START, clock.now());
    }

    static Stream<Duration> negativeDurations() {
        return Stream.of(Duration.ofNanos(-1), Duration.ofNanos(-999_999_999),
                Duration.ofSeconds(-1), Duration.ofSeconds(-1, 1), Duration.ofDays(-100_000));
    }

    @ParameterizedTest @MethodSource("negativeDurations")
    void everyNegativeDurationIsRejectedWithoutChangingTheClock(Duration amount) {
        var clock = new SimulatedClock(START);
        clock.advance(Duration.ofSeconds(3));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(amount));
        assertEquals(START.plusSeconds(3), clock.now());
        clock.advance(Duration.ofNanos(1));
        assertEquals(START.plusSeconds(3).plusNanos(1), clock.now());
    }

    @ParameterizedTest @ValueSource(longs = {1, 2, 999_999_999, 1_000_000_000, 1_000_000_001})
    void advancesPreserveNanosecondsAcrossSecondAndDateBoundaries(long nanos) {
        var clock = new SimulatedClock(START);
        clock.advance(Duration.ofNanos(nanos));
        assertEquals(START.plusNanos(nanos), clock.now());
    }

    @Test void repeatedSmallAdvancesAccumulateWithoutTruncation() {
        var clock = new SimulatedClock(START);
        for (int i = 0; i < 10_000; i++) clock.advance(Duration.ofNanos(101));
        assertEquals(START.plusNanos(1_010_000), clock.now());
    }

    @ParameterizedTest @ValueSource(strings = {
            "2000-02-28T23:59:59", "2000-02-29T23:59:59", "2100-02-28T23:59:59", "1999-12-31T23:59:59"})
    void calendarRolloversFollowLocalDateTimeArithmetic(String start) {
        var initial = LocalDateTime.parse(start);
        var clock = new SimulatedClock(initial);
        clock.advance(Duration.ofSeconds(1));
        assertEquals(initial.plusSeconds(1), clock.now());
    }

    @Test void largeValidDurationsDoNotOverflowAnIntSecondCounter() {
        var clock = new SimulatedClock(START);
        var amount = Duration.ofSeconds((long) Integer.MAX_VALUE + 123, 17);
        clock.advance(amount);
        assertEquals(START.plus(amount), clock.now());
    }

    @Test void advancingOneClockDoesNotChangeAnother() {
        var first = new SimulatedClock(START);
        var second = new SimulatedClock(START);
        first.advance(Duration.ofDays(2));
        assertEquals(START, second.now());
        second.advance(Duration.ofNanos(1));
        assertEquals(START.plusDays(2), first.now());
    }
}
