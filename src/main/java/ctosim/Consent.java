package ctosim;

import java.util.List;

/** The form's consent questions and the consent rate. See specs/04-simulation-config.md, Amendment 8. */
public record Consent(List<String> fields, double rate, String yes, String no) {
}
