package ctosim;

/** Raised when a simulation configuration cannot be read or is invalid. See specs/04-simulation-config.md. */
public class ConfigException extends Exception {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
