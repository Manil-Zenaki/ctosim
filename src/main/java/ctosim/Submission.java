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
    private final LocalDateTime submitted;
    private final int answered;
    private final List<String> warnings;
    private final List<UnsatisfiedConstraint> unsatisfiedConstraints;
    private final List<FormError> formErrors;

    Submission(int number, String instanceXml, LocalDateTime start, LocalDateTime end, LocalDateTime submitted,
               int answered, List<String> warnings) {
        this(number, instanceXml, start, end, submitted, answered, warnings, List.of(), List.of());
    }

    Submission(int number, String instanceXml, LocalDateTime start, LocalDateTime end, LocalDateTime submitted,
               int answered, List<String> warnings, List<UnsatisfiedConstraint> unsatisfiedConstraints,
               List<FormError> formErrors) {
        this.number = number;
        this.instanceXml = instanceXml;
        this.start = start;
        this.end = end;
        this.submitted = submitted;
        this.answered = answered;
        this.warnings = List.copyOf(warnings);
        this.unsatisfiedConstraints = List.copyOf(unsatisfiedConstraints);
        this.formErrors = List.copyOf(formErrors);
    }

    /** A submission built from an instance written elsewhere. See specs/08-surveycto-exporter.md. */
    public static Submission of(int number, String instanceXml, LocalDateTime start, LocalDateTime end) {
        Objects.requireNonNull(instanceXml, "instanceXml");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (number < 1) {
            throw new IllegalArgumentException("submission number must be at least 1: " + number);
        }
        return of(number, instanceXml, start, end, end);
    }

    /** A submission received at {@code submitted}. See specs/08-surveycto-exporter.md, Amendment 1. */
    public static Submission of(int number, String instanceXml, LocalDateTime start, LocalDateTime end,
                                LocalDateTime submitted) {
        Objects.requireNonNull(instanceXml, "instanceXml");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(submitted, "submitted");
        if (number < 1) {
            throw new IllegalArgumentException("submission number must be at least 1: " + number);
        }
        return new Submission(number, instanceXml, start, end, submitted, 0, List.of());
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

    /** When the server received the submission. See specs/07-form-walker.md, Amendment 2. */
    public LocalDateTime submitted() {
        return submitted;
    }

    /** Questions for which the engine accepted an answer from the walker. */
    public int answered() {
        return answered;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** The form's expressions the engine could not evaluate. See specs/07-form-walker.md, Amendment 10. */
    public List<FormError> formErrors() {
        return formErrors;
    }

    /** The questions left blank because no answer met their constraint. See specs/07-form-walker.md, Amendment 8. */
    public List<UnsatisfiedConstraint> unsatisfiedConstraints() {
        return unsatisfiedConstraints;
    }
}
