package ctosim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javarosa.xpath.XPathException;

/**
 * The coordinates of the choices of the village question, read from the CSV file its search() call
 * reads, in the columns the configuration names. See specs/13-fieldwork-plan.md, Amendment 4.
 */
final class VillageMap {

    private final Map<String, double[]> coordinates = new HashMap<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    /**
     * Reads the file of a village question whose appearance is {@code appearance} and whose choice
     * list is {@code formChoices}; a file named by an expression, or missing, locates no village.
     */
    VillageMap(String appearance, List<String> formChoices, Villages columns, Attachments attachments) {
        List<String> args = SearchChoices.searchArguments(appearance == null ? "" : appearance);
        String valueColumn = SearchChoices.valueColumn(formChoices);
        String source = args == null || args.isEmpty() ? null : literal(args.get(0));
        if (source == null || valueColumn == null) {
            return;
        }
        String file = source.endsWith(".csv") ? source : source + ".csv";
        Attachments.Table table;
        try {
            table = attachments.table(file.substring(0, file.length() - 4));
        } catch (XPathException e) {
            return;
        }
        if (table == null) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String column : List.of(columns.latitude(), columns.longitude())) {
            if (!table.header().contains(column)) {
                missing.add(column);
                warnings.add("fieldwork: the village list " + file + " has no column '" + column
                        + "'; its villages are not located");
            }
        }
        int value = table.header().indexOf(valueColumn);
        if (!missing.isEmpty() || value < 0) {
            return;
        }
        int lat = table.header().indexOf(columns.latitude());
        int lon = table.header().indexOf(columns.longitude());
        Set<String> seen = new java.util.HashSet<>();
        for (List<String> row : table.rows()) {
            String code = cell(row, value);
            if (!seen.add(code)) {
                continue; // the first row of a value gives its coordinates
            }
            double latitude = number(cell(row, lat));
            double longitude = number(cell(row, lon));
            if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                coordinates.put(code, new double[] {latitude, longitude});
            }
        }
    }

    /** The coordinates of a village, or null when it is not located. */
    double[] of(String village) {
        return village == null ? null : coordinates.get(village);
    }

    /** One warning for each coordinate column the file lacks. */
    List<String> warnings() {
        return List.copyOf(warnings);
    }

    private static String literal(String arg) {
        String s = arg.strip();
        if (s.length() >= 2 && (s.charAt(0) == '\'' || s.charAt(0) == '"') && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return null;
    }

    private static final java.util.regex.Pattern DECIMAL =
            java.util.regex.Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");

    private static double number(String s) {
        if (!DECIMAL.matcher(s.strip()).matches()) {
            return Double.NaN;
        }
        try {
            double v = Double.parseDouble(s.strip());
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i) : "";
    }
}
