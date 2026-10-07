package ctosim;

/** Raised when a form definition cannot be loaded. See specs/01-form-loader.md. */
public class FormLoadException extends Exception {

    private static final long serialVersionUID = 1L;

    public FormLoadException(String message) {
        super(message);
    }

    public FormLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
