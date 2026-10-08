package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** A clock that moves only when told to. See specs/06-simulated-sessions.md. */
public final class SimulatedClock {

    private LocalDateTime now;

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
}
