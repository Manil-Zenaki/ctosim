package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** The form's consent questions and the consent rate. See specs/04-simulation-config.md, Amendment 8. */
public record Consent(List<String> fields, double rate, String yes, String no) {

    static final double DEFAULT_RATE = 0.9;
    static final String DEFAULT_YES = "1";
    static final String DEFAULT_NO = "0";
    private static final Pattern EDGE_SPACE = Pattern.compile("^[ \t\r\n\f]|[ \t\r\n\f]$");

    public Consent {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(yes, "yes");
        Objects.requireNonNull(no, "no");
        fields = List.copyOf(fields); // rejects null elements
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("consent needs at least one field");
        }
        for (String field : fields) {
            if (field.isEmpty() || EDGE_SPACE.matcher(field).find()) {
                throw new IllegalArgumentException("a consent field must be a name without white space at its ends: '"
                        + field + "'");
            }
        }
        if (new HashSet<>(fields).size() != fields.size()) {
            throw new IllegalArgumentException("a consent field is named twice: " + fields);
        }
        if (!(rate >= 0 && rate <= 1)) {
            throw new IllegalArgumentException("the consent rate must be between 0 and 1: " + rate);
        }
        if (yes.isEmpty() || no.isEmpty()) {
            throw new IllegalArgumentException("the consenting and refusing values must not be empty");
        }
        if (yes.equals(no)) {
            throw new IllegalArgumentException("the consenting and refusing values must differ: '" + yes + "'");
        }
    }
}
