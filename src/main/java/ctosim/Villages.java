package ctosim;

import java.util.Objects;
import java.util.regex.Pattern;

/** The coordinate columns of the preloaded village list. See specs/04-simulation-config.md, Amendment 10. */
public record Villages(String latitude, String longitude) {

    private static final Pattern EDGE_SPACE = Pattern.compile("^[ \t\r\n\f]|[ \t\r\n\f]$");

    public Villages {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        for (String column : new String[] {latitude, longitude}) {
            if (column.isEmpty() || EDGE_SPACE.matcher(column).find()) {
                throw new IllegalArgumentException("a column must be a name without white space at its ends: '"
                        + column + "'");
            }
        }
        if (latitude.equals(longitude)) {
            throw new IllegalArgumentException("the latitude and longitude columns must differ: '" + latitude + "'");
        }
    }
}
