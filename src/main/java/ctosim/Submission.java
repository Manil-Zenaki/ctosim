package ctosim;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** One simulated submission. See specs/07-form-walker.md. */
public final class Submission {

    private final int number;
    private final String instanceXml;
    private final LocalDateTime start;
    private final LocalDateTime end;
    private final int answered;
    private final List<String> warnings;

    Submission(int number, String instanceXml, LocalDateTime start, LocalDateTime end, int answered,
               List<String> warnings) {
        this.number = number;
        this.instanceXml = instanceXml;
        this.start = start;
        this.end = end;
        this.answered = answered;
        this.warnings = List.copyOf(warnings);
    }

    /** A submission built from an instance written elsewhere. See specs/08-surveycto-exporter.md. */
    public static Submission of(int number, String instanceXml, LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(instanceXml, "instanceXml");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (number < 1) {
            throw new IllegalArgumentException("submission number must be at least 1: " + number);
        }
        return new Submission(number, instanceXml, start, end, 0, List.of());
    }

    public int number() {
        return number;
    }

    /** The instance as serialised by the engine, as UTF-8 text. */
    public String instanceXml() {
        return instanceXml;
    }

    public LocalDateTime start() {
        return start;
    }

    public LocalDateTime end() {
        return end;
    }

    /** Questions for which the engine accepted an answer from the walker. */
    public int answered() {
        return answered;
    }

    public List<String> warnings() {
        return warnings;
    }
}
