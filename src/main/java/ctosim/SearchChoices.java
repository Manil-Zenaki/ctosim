package ctosim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.javarosa.xpath.XPathException;

/**
 * Choices that a select question loads from an attached CSV file with a {@code search()} call in
 * its appearance (SurveyCTO's "dynamically load choices from pre-loaded data"). See
 * specs/07-form-walker.md, Amendment 3.
 */
final class SearchChoices {

    private static final Set<String> TYPES = Set.of("contains", "startswith", "endswith", "matches");

    /** The choices to offer, and the reason no row could be loaded (null when the call worked). */
    record Result(List<String> choices, String warning) { }

    private SearchChoices() {
    }

    /**
     * The choices of a question whose appearance is {@code appearance} and whose choice list is
     * {@code formChoices}, or null when it is an ordinary select (no {@code search()} call, or no
     * placeholder choice). {@code evaluate} gives the string value of an argument expression.
     */
    static Result load(String appearance, List<String> formChoices, Function<String, String> evaluate,
                       Attachments attachments) {
        List<String> args = searchArguments(appearance);
        int placeholder = placeholder(formChoices);
        if (args == null || placeholder < 0) {
            return null;
        }
        List<String> statics = new ArrayList<>(formChoices);
        statics.remove(placeholder);
        if (args.size() != 1 && args.size() != 4 && args.size() != 6) {
            return new Result(statics, "its search() call cannot be read (" + args.size() + " arguments)");
        }
        List<String> values = new ArrayList<>();
        try {
            for (String arg : args) {
                values.add(evaluate.apply(arg));
            }
        } catch (RuntimeException e) {
            return new Result(statics, "its search() call cannot be read (" + e.getMessage() + ")");
        }
        if (values.size() > 1 && !TYPES.contains(values.get(1))) {
            return new Result(statics, "its search() type '" + values.get(1) + "' is not one SurveyCTO knows");
        }
        String source = values.get(0);
        String file = source.endsWith(".csv") ? source : source + ".csv";
        Attachments.Table table;
        try {
            table = attachments.table(file.substring(0, file.length() - 4));
        } catch (XPathException e) {
            return new Result(statics, "search(): " + e.getMessage());
        }
        if (table == null) {
            return new Result(statics, "search(): the attached file " + file + " is missing");
        }
        String valueColumn = formChoices.get(placeholder);
        List<String> needed = new ArrayList<>(List.of(valueColumn));
        List<String> searched = new ArrayList<>();
        if (values.size() > 1) {
            for (String column : values.get(2).split(",")) {
                searched.add(column.strip());
            }
            needed.addAll(searched);
        }
        if (values.size() > 4) {
            needed.add(values.get(4));
        }
        for (String column : needed) {
            if (!table.header().contains(column)) {
                return new Result(statics, "search(): the attached file " + file + " has no column '" + column + "'");
            }
        }

        List<List<String>> rows = new ArrayList<>(table.rows());
        int sortby = table.header().indexOf("sortby");
        if (sortby >= 0) {
            rows.sort(Comparator.comparingDouble(row -> {
                double v = number(cell(row, sortby));
                return Double.isNaN(v) ? Double.POSITIVE_INFINITY : v;
            }));
        }
        Set<String> loaded = new LinkedHashSet<>();
        for (List<String> row : rows) {
            if (keep(row, table.header(), values, searched)) {
                String value = cell(row, table.header().indexOf(valueColumn));
                if (!value.isEmpty()) {
                    loaded.add(value);
                }
            }
        }
        List<String> choices = new ArrayList<>(formChoices.subList(0, placeholder));
        choices.addAll(loaded);
        choices.addAll(formChoices.subList(placeholder + 1, formChoices.size()));
        return new Result(choices, null);
    }

    private static boolean keep(List<String> row, List<String> header, List<String> values, List<String> searched) {
        if (values.size() == 1) {
            return true;
        }
        String type = values.get(1);
        String text = values.get(3);
        boolean match = false;
        for (String column : searched) {
            String cell = cell(row, header.indexOf(column));
            match |= switch (type) {
                case "contains" -> cell.contains(text);
                case "startswith" -> cell.startsWith(text);
                case "endswith" -> cell.endsWith(text);
                default -> cell.equals(text);
            };
        }
        return match && (values.size() < 6 || cell(row, header.indexOf(values.get(4))).equals(values.get(5)));
    }

    /** The arguments of the first search(...) call in the appearance, as written, or null. */
    static List<String> searchArguments(String appearance) {
        int at = -1;
        for (int i = appearance.indexOf("search"); i >= 0; i = appearance.indexOf("search", i + 1)) {
            int open = i + "search".length();
            while (open < appearance.length() && Character.isWhitespace(appearance.charAt(open))) {
                open++;
            }
            if ((i == 0 || Character.isWhitespace(appearance.charAt(i - 1)))
                    && open < appearance.length() && appearance.charAt(open) == '(') {
                at = open;
                break;
            }
        }
        if (at < 0) {
            return null;
        }
        int close = EngineExpressions.closingParen(appearance, at);
        if (close < 0) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        for (String arg : EngineExpressions.splitArguments(appearance.substring(at + 1, close))) {
            args.add(arg.strip());
        }
        return args;
    }

    /** The position of the first choice whose value is not a number, or -1. */
    private static int placeholder(List<String> choices) {
        for (int i = 0; i < choices.size(); i++) {
            if (Double.isNaN(number(choices.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i) : "";
    }
}
