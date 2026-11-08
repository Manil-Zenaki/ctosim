package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ctosim.FieldworkAmendment5Support.SMALL_AREA;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 13 Amendment 6: only public observations, no implementation oracle. */
final class FieldworkAmendment6Support {
    private FieldworkAmendment6Support() { }

    // finals is a JSON array, or null to omit the member entirely.
    static SimulationConfig config6(int n, int enumerators, int quota, double revisited,
            double unreached, int max, String finals, String extra) throws Exception {
        return settings("\"submissions\":" + n + ",\"enumerators\":" + enumerators
                + ",\"interviewsPerDay\":" + quota + ",\"fieldworkDays\":3650,"
                + "\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":10000}," + SMALL_AREA
                + ",\"visits\":{\"field\":\"status\",\"available\":\"yes\",\"number\":\"attempt\","
                + "\"revisitRate\":" + revisited + ",\"unreachedRate\":" + unreached
                + ",\"maxVisits\":" + max + (finals == null ? "" : ",\"final\":" + finals) + "}"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static LoadedForm reasonForm(String... reasons) throws Exception {
        return reasonForm(false, reasons);
    }

    static LoadedForm reasonForm(boolean noise, String... reasons) throws Exception {
        StringBuilder choices = new StringBuilder(item("yes"));
        for (String code : reasons) choices.append(item(code));
        return load(choices.toString(), "", noise);
    }

    private static String item(String code) {
        // Callers use XML-safe test literals, including Unicode and leading zeros.
        return "<item><label>" + code + "</label><value>" + code + "</value></item>";
    }

    static LoadedForm finalOnlyAfterFirstAttempt() throws Exception {
        return load("<itemset nodeset=\"instance('reasons')/root/item[stage = 0 or stage = /data/attempt]\">"
                + "<value ref=\"code\"/><label ref=\"code\"/></itemset>",
                "<instance id=\"reasons\"><root>"
                + "<item><code>yes</code><stage>0</stage></item>"
                + "<item><code>absent</code><stage>1</stage></item>"
                + "<item><code>moved</code><stage>2</stage></item>"
                + "</root></instance>", false);
    }

    private static LoadedForm load(String choices, String secondary, boolean noise) throws Exception {
        try (var in = FieldworkAmendment6Support.class.getResourceAsStream(
                "/ctosim/fieldwork-plan/amendment6/form-template.xml")) {
            assertNotNull(in, "Amendment 6 fixture exists");
            return loadXml(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("__CHOICES__", choices).replace("__SECONDARY__", secondary)
                    .replace("__NOISE__", noise ? input("noise") : ""));
        }
    }

    static List<String> reasons(List<Submission> rows) throws Exception {
        List<String> result = new ArrayList<>();
        for (Submission row : rows) result.add(value(row, "status"));
        return result;
    }

    static Map<String, List<Integer>> households(List<Visit> visits) {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (int i = 0; i < visits.size(); i++) {
            assertNotNull(visits.get(i).id(), "unique static IDs make household identity observable");
            result.computeIfAbsent(visits.get(i).id(), ignored -> new ArrayList<>()).add(i);
        }
        return result;
    }
}
