package ctosim;

import java.time.LocalDateTime;
import java.util.List;

/** One simulated submission. See specs/07-form-walker.md. */
public final class Submission {

    Submission() {
    }

    public int number() {
        throw new UnsupportedOperationException("not implemented");
    }

    public String instanceXml() {
        throw new UnsupportedOperationException("not implemented");
    }

    public LocalDateTime start() {
        throw new UnsupportedOperationException("not implemented");
    }

    public LocalDateTime end() {
        throw new UnsupportedOperationException("not implemented");
    }

    public int answered() {
        throw new UnsupportedOperationException("not implemented");
    }

    public List<String> warnings() {
        throw new UnsupportedOperationException("not implemented");
    }
}
