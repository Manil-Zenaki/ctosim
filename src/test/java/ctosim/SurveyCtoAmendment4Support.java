package ctosim;

import static ctosim.SurveyCtoTestSupport.*;

/** Test data and public session entry points only; no implementation oracle. */
final class SurveyCtoAmendment4Support {
    private SurveyCtoAmendment4Support() { }

    static final String[][] THREE = {{"10", "odd", "first"}, {"20", "even", "second"},
            {"30", "odd", "third"}};
    static final String ODD = "/data/row/kind = 'odd'";

    static LoadedForm repeats(String expression, String[]... rows) throws Exception {
        StringBuilder xml = new StringBuilder();
        for (String[] row : rows) xml.append("<row><score>").append(attribute(row[0]))
                .append("</score><kind>").append(attribute(row[1])).append("</kind><label>")
                .append(attribute(row[2])).append("</label></row>");
        return load(fixture("amendment4-repeat.xml").replace("__ROWS__", xml)
                .replace("__EXPRESSION__", attribute(expression)));
    }

    static String conditional(String function, String predicate) {
        return switch (function) {
            case "count" -> "count-if(/data/row, " + predicate + ")";
            case "join" -> "join-if('|', /data/row/score, " + predicate + ")";
            case "rank-index" -> "rank-index-if(1, /data/row/score, " + predicate + ")";
            default -> function + "-if(/data/row/score, " + predicate + ")";
        };
    }

    static String nativeCall(String function, String filter) {
        String rows = "/data/row" + filter;
        return switch (function) {
            case "count" -> "count(" + rows + ")";
            case "join" -> "join('|', " + rows + "/score)";
            default -> function + "(" + rows + "/score)";
        };
    }

    static LoadedForm calculation(String expression) throws Exception {
        return FormWalkerTestSupport.form("<tick>0</tick><result/><observed/>",
                FormWalkerTestSupport.bind("tick", "int", "")
                + FormWalkerTestSupport.bind("result", "string", "calculate=\"" + attribute(expression) + "\"")
                + FormWalkerTestSupport.bind("observed", "string", "calculate=\"concat('[', /data/result, ']')\""),
                FormWalkerTestSupport.input("tick"));
    }
}
