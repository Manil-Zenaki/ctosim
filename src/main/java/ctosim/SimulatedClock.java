package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** A clock that moves only when told to. See specs/06-simulated-sessions.md. */
public final class SimulatedClock {

    private LocalDateTime now;
    /** Time the clock moved during pauses: not screen time (spec 07, Amendment 12). */
    private Duration paused = Duration.ZERO;

    public SimulatedClock(LocalDateTime start) {
        this.now = Objects.requireNonNull(start, "start");
    }

    public LocalDateTime now() {
        return now;
    }

    public void advance(Duration amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("a clock cannot move backwards: " + amount);
        }
        now = now.plus(amount);
    }

    /** Moves the clock forward without adding screen time (spec 07, Amendment 12). */
    void pause(Duration amount) {
        advance(amount);
        paused = paused.plus(amount);
    }

    /** The time spent in pauses so far. */
    Duration paused() {
        return paused;
    }
}
