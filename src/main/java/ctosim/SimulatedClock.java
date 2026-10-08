package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;

/** A clock that moves only when told to. See specs/06-simulated-sessions.md. */
public final class SimulatedClock {

    public SimulatedClock(LocalDateTime start) {
        throw new UnsupportedOperationException("not implemented");
    }

    public LocalDateTime now() {
        throw new UnsupportedOperationException("not implemented");
    }

    public void advance(Duration amount) {
        throw new UnsupportedOperationException("not implemented");
    }
}
