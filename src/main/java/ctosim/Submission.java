package ctosim;

import java.time.LocalDateTime;
import java.util.List;

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
        throw new UnsupportedOperationException("not implemented");
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
