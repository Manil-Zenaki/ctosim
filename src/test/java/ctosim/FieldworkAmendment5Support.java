package ctosim;

import java.util.ArrayList;
import java.util.List;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;

/** Spec 13 Amendment 5 fixtures; no production implementation is inspected. */
final class FieldworkAmendment5Support {
    static final String MINUTE = "ct:minimum-seconds=\"60\"";
    static final String SMALL_AREA = "\"area\":{\"minLatitude\":0,\"maxLatitude\":0,"
            + "\"minLongitude\":0,\"maxLongitude\":0}";

    private FieldworkAmendment5Support() { }

    static String visitSettings(double revisited, double unreached, int max) {
        return "\"visits\":{\"field\":\"status\",\"number\":\"attempt\","
                + "\"available\":\"yes\",\"revisitRate\":" + revisited
                + ",\"unreachedRate\":" + unreached + ",\"maxVisits\":" + max + "}";
    }

    static SimulationConfig config5(int n, int enumerators, int quota,
            double revisited, double unreached, int max, String extra) throws Exception {
        return settings("\"submissions\":" + n + ",\"enumerators\":" + enumerators
                + ",\"interviewsPerDay\":" + quota + ",\"fieldworkDays\":3650,\"id\":\"answer\","
                + "\"integerRange\":{\"min\":1,\"max\":10000},"
                + visitSettings(revisited, unreached, max) + (extra.isEmpty() ? "" : "," + extra));
    }

    static String choices(String path, String... codes) {
        StringBuilder body = new StringBuilder("<select1 ref=\"/data/" + path + "\"><label>Status</label>");
        for (String code : codes) body.append("<item><label>").append(code)
                .append("</label><value>").append(code).append("</value></item>");
        return body.append("</select1>").toString();
    }

    static LoadedForm simple() throws Exception { return simple(60, ""); }

    static LoadedForm simple(int statusSeconds, String extraBody) throws Exception {
        return fixture("<answer/><status/><attempt/><details/>",
                bind("answer", "int", MINUTE) + bind("status", "string",
                        "ct:minimum-seconds=\"" + statusSeconds + "\"")
                + bind("attempt", "int", MINUTE) + bind("details", "string", MINUTE
                        + " relevant=\"/data/status = 'yes'\""),
                input("answer") + choices("status", "yes", "absent", "busy")
                        + input("attempt") + input("details") + extraBody);
    }

    static Visit supplied(int attempt, boolean found) {
        return new Visit(1, START, 10, 20, null, attempt, found);
    }

    static List<String> column5(List<Submission> rows, String field) throws Exception {
        List<String> result = new ArrayList<>();
        for (Submission row : rows) result.add(value(row, field));
        return result;
    }
}
