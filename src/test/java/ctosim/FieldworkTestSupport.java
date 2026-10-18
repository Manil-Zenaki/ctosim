package ctosim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box oracles from spec 13 and its referenced public contracts. */
final class FieldworkTestSupport {
    static final LocalDateTime START = LocalDateTime.of(2026, 3, 2, 8, 17, 23);
    static final long SEED = 731991L;

    private FieldworkTestSupport() { }

    static SimulationConfig settings(String members) throws Exception {
        return SimulationConfig.parse("{\"seed\":" + SEED
                + ",\"start\":\"" + START + "\",\"missingRate\":0,\"specialRate\":0"
                + (members.isEmpty() ? "" : "," + members) + "}", "fieldwork-config.json");
    }

    static LoadedForm fixture(String instance, String binds, String body) throws Exception {
        String template;
        try (var in = FieldworkTestSupport.class.getResourceAsStream(
                "/ctosim/fieldwork-plan/form-template.xml")) {
            assertNotNull(in, "fieldwork template exists");
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String xml = template.replace("__INSTANCE__", instance).replace("__BINDS__", binds)
                .replace("__BODY__", body);
        try (var in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return FormLoader.load(in, "fieldwork-fixture.xml");
        }
    }

    static LoadedForm question(String type, String attrs) throws Exception {
        return fixture("<answer/>", bind("answer", type, attrs), input("answer"));
    }

    static LoadedForm timed(int seconds) throws Exception {
        return question("string", "ct:minimum-seconds=\"" + seconds + "\"");
    }

    static LoadedForm metadata() throws Exception {
        return fixture(METADATA + "<username/><deviceid/><answer/><other/>", METADATA_BINDS
                + bind("username", "string", "jr:preload=\"property\" jr:preloadParams=\"username\"")
                + bind("deviceid", "string", "jr:preload=\"property\" jr:preloadParams=\"deviceid\"")
                + bind("answer", "int", "") + bind("other", "string", ""),
                input("answer") + input("other"));
    }

    static LoadedForm locations() throws Exception {
        return fixture("<gps/><gps2/><trace/><shape/>", bind("gps", "geopoint", "")
                + bind("gps2", "geopoint", "") + bind("trace", "geotrace", "")
                + bind("shape", "geoshape", ""),
                input("gps") + input("gps2") + input("trace") + input("shape"));
    }

    static LoadedForm choices() throws Exception {
        StringBuilder body = new StringBuilder("<select1 ref=\"/data/answer\"><label>ID</label>");
        for (String choice : List.of("a", "b", "c", "99", "-99")) {
            body.append("<item><label>").append(choice).append("</label><value>")
                    .append(choice).append("</value></item>");
        }
        return fixture("<answer/>", bind("answer", "string", ""), body + "</select1>");
    }

    static Visit visit(int enumerator) {
        return new Visit(enumerator, START.plusDays(100), 9.03, 38.74, null);
    }

    static void same(Submission expected, Submission actual) {
        assertSameSubmission(expected, actual);
        assertEquals(expected.submitted(), actual.submitted(), "upload time");
    }

    static void sameRun(List<Submission> expected, List<Submission> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) same(expected.get(i), actual.get(i));
    }

    static long wholeMinutes(LocalDateTime from, LocalDateTime to) {
        Duration duration = Duration.between(from, to);
        assertEquals(0, duration.getNano(), "whole minutes, no fractional second");
        assertEquals(0, duration.getSeconds() % 60, "whole minutes, no residual seconds");
        return duration.toMinutes();
    }

    static long day(SimulationConfig config, Visit visit) {
        return Math.floorDiv(Duration.between(config.start(), visit.start()).getSeconds(), 86400);
    }

    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 6_371_000 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
    }

    static double[] point(String text) {
        String[] parts = text.trim().split("\\s+");
        assertEquals(4, parts.length, text);
        double[] point = new double[4];
        for (int i = 0; i < 4; i++) {
            point[i] = Double.parseDouble(parts[i]);
            assertTrue(Double.isFinite(point[i]), text);
        }
        assertEquals(0, point[2], "altitude");
        assertEquals(5, point[3], "accuracy");
        return point;
    }

    static void warning(List<String> warnings, String field, int... numbers) {
        assertTrue(warnings.stream().anyMatch(w -> {
            if (field != null && !w.contains(field)) return false;
            for (int number : numbers) {
                if (!Pattern.compile("(?<![0-9])" + number + "(?![0-9])").matcher(w).find()) return false;
            }
            return true;
        }), () -> "Expected warning naming " + field + " and " + java.util.Arrays.toString(numbers)
                + "; actual " + warnings);
    }
}
