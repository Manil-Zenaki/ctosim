package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment3SearchSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Observable fixtures and distance oracles for spec 13 Amendment 4 only. */
abstract class FieldworkAmendment4Support extends FieldworkAmendment1SearchSupport {
    static final String LOCATED = "\"villages\":{\"latitude\":\"lat_col\",\"longitude\":\"lon_col\"}";
    static final String AREA = "\"area\":{\"minLatitude\":-20,\"maxLatitude\":-19,"
            + "\"minLongitude\":-40,\"maxLongitude\":-39}";
    static final String FORCE_A = "\"fields\":{\"district\":{\"weights\":{\"D1\":1}},"
            + "\"village\":{\"weights\":{\"A\":1}}}";
    static final String VILLAGE_SEARCH = "search('a4-villages', 'matches', 'district', /data/district)";
    static final String ID_SEARCH = "search('a4-households', 'matches', 'village', /data/village)";

    static String resource4(String name) throws Exception {
        try (var in = FieldworkAmendment4Support.class.getResourceAsStream(
                "/ctosim/fieldwork-plan/amendment4/" + name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    void tables(String villages, String households) throws Exception {
        Files.writeString(media.resolve("a4-districts.csv"), resource4("districts.csv"));
        Files.writeString(media.resolve("a4-villages.csv"), resource4(villages));
        Files.writeString(media.resolve("a4-households.csv"), resource4(households));
    }

    void replaceTable(String name, String from, String to) throws Exception {
        var path = media.resolve("a4-" + name + ".csv");
        String original = Files.readString(path);
        assertTrue(original.contains(from), "fixture substitution must match: " + from);
        Files.writeString(path, original.replace(from, to));
    }

    LoadedForm cascade() throws Exception {
        return cascade(VILLAGE_SEARCH, ID_SEARCH, true, "");
    }

    LoadedForm cascade(String villageSearch, String idSearch, boolean gps, String villageAttrs) throws Exception {
        String instance = "<district/><village/><answer/>";
        String binds = bind("district", "string", "") + bind("village", "string", villageAttrs)
                + bind("answer", "string", "");
        String before = "", after = "";
        if (gps) {
            instance = "<before/><trace_before/><shape_before/>" + instance
                    + "<after/><trace_after/><shape_after/>";
            for (String name : List.of("before", "after", "trace_before", "trace_after", "shape_before", "shape_after")) {
                binds += bind(name, name.startsWith("trace") ? "geotrace"
                        : name.startsWith("shape") ? "geoshape" : "geopoint", "");
                if (name.endsWith("before")) before += input(name); else after += input(name);
            }
        }
        return attached(instance, binds, before
                + select("district", "search('a4-districts')", "code")
                + select("village", villageSearch, "code") + control(idSearch, "code") + after);
    }

    static String select(String name, String search, String... choices) {
        return selectControl("select1", name, search, items(choices));
    }

    static SimulationConfig config4(int n, int enumerators, int quota, String extra) throws Exception {
        return idConfig(n, "\"enumerators\":" + enumerators + ",\"interviewsPerDay\":" + quota
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static List<String> column(List<Submission> rows, String field) throws Exception {
        var values = new ArrayList<String>();
        for (var row : rows) values.add(value(row, field));
        return values;
    }

    static void near(Visit visit, double latitude, double longitude) {
        assertTrue(Double.isFinite(visit.latitude()) && Double.isFinite(visit.longitude()));
        assertTrue(distance(latitude, longitude, visit.latitude(), visit.longitude()) <= 1500.000001,
                () -> "visit " + visit + " must be within 1.5 km of " + latitude + ", " + longitude);
    }

    static void gpsFollowsVisit(Submission row, Visit visit, String... fields) throws Exception {
        for (String field : fields) {
            double[] p = point(value(row, field).split(";\\s*")[0]);
            assertTrue(distance(visit.latitude(), visit.longitude(), p[0], p[1]) <= 30.16,
                    field + " must follow the final visit even if asked before the village");
        }
    }

    static void inFallbackArea(Visit visit) {
        assertTrue(visit.latitude() >= -20 && visit.latitude() <= -19, visit.toString());
        assertTrue(visit.longitude() >= -40 && visit.longitude() <= -39, visit.toString());
    }

    static long travelMinutes(double lat1, double lon1, double lat2, double lon2) {
        return (long) Math.ceil(2 * distance(lat1, lon1, lat2, lon2) / 1000);
    }
}
