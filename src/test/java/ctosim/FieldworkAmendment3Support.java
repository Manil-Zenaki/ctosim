package ctosim;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment3SearchSupport.items;
import static ctosim.FormWalkerAmendment3SearchSupport.selectControl;
import static org.junit.jupiter.api.Assertions.*;

/** Fixture sets and an oracle based solely on earlier submitted IDs, not simulator internals. */
abstract class FieldworkAmendment3Support extends FieldworkAmendment1SearchSupport {
    static final String HOUSEHOLDS = "search('a3-households', 'matches', 'village', /data/village, 'cohort', 'main')";
    static final Map<String, Set<String>> POOLS = Map.of(
            "A", Set.of("0007", "É02"), "B", Set.of("B1", "B2", "B3"),
            "C", Set.of("SHARED", "C1"), "E", Set.of("SHARED", "E1"),
            "X", Set.of(), "Z", Set.of());
    static final Map<String, Set<String>> DISTRICTS = Map.of(
            "D1", Set.of("A", "B"), "D2", Set.of("C", "X"),
            "D3", Set.of("E"), "D0", Set.of("Z"));
    static final Map<String, Set<String>> REGIONS = Map.of(
            "R1", Set.of("D1", "D2"), "R2", Set.of("D3"), "R0", Set.of("D0"));

    @BeforeEach void copySteeringAttachments() throws Exception {
        for (String name : List.of("regions", "districts", "villages", "households", "rates")) {
            try (var in = getClass().getResourceAsStream(
                    "/ctosim/fieldwork-plan/amendment3/" + name + ".csv")) {
                assertNotNull(in, name);
                Files.copy(in, media.resolve("a3-" + name + ".csv"));
            }
        }
    }

    static String select(String name, String appearance, String... choices) {
        return selectControl("select1", name, appearance, items(choices));
    }

    LoadedForm simple() throws Exception {
        return simple("search('a3-villages')", HOUSEHOLDS, "", "code");
    }

    LoadedForm simple(String upperSearch, String idSearch, String upperAttributes,
            String... idChoices) throws Exception {
        return attached("<village/><answer/>", bind("village", "string", upperAttributes)
                        + bind("answer", "string", ""),
                select("village", upperSearch, "code") + control(idSearch, idChoices));
    }

    LoadedForm deep(boolean filterText) throws Exception {
        return attached("<region/><district/><village/><answer/>",
                bind("region", "string", "") + bind("district", "string", "")
                        + bind("village", "string", "") + bind("answer", "string", ""),
                select("region", "search('a3-regions')", "code")
                        + select("district", link("districts", "region", filterText), "code")
                        + select("village", link("villages", "district", filterText), "code")
                        + control(filterText
                                ? "search('a3-households', 'matches', 'cohort', 'main', 'village', /data/village)"
                                : HOUSEHOLDS, "code"));
    }

    static String link(String file, String parent, boolean filterText) {
        return filterText
                ? "search('a3-" + file + "', 'contains', 'code', '', '" + parent + "', /data/" + parent + ")"
                : "search('a3-" + file + "', 'matches', '" + parent + "', /data/" + parent + ")";
    }

    static Set<String> freeVillages(Map<String, Set<String>> pools, Set<String> received) {
        var free = new HashSet<String>();
        pools.forEach((v, ids) -> { if (ids.stream().anyMatch(id -> !received.contains(id))) free.add(v); });
        return free;
    }

    static Set<String> parentsWithFreeChildren(Map<String, Set<String>> children, Set<String> free) {
        var parents = new HashSet<String>();
        children.forEach((p, values) -> { if (values.stream().anyMatch(free::contains)) parents.add(p); });
        return parents;
    }

    static Set<String> allIds(Map<String, Set<String>> pools) {
        var all = new HashSet<String>();
        pools.values().forEach(all::addAll);
        return all;
    }

    static List<String> column(List<Submission> submissions, String field) throws Exception {
        var out = new ArrayList<String>();
        for (var submission : submissions) out.add(value(submission, field));
        return out;
    }

    static void assertSteered(List<Submission> submissions, Map<String, Set<String>> pools,
            boolean deep) throws Exception {
        var received = new HashSet<String>();
        for (var submission : submissions) {
            var viable = freeVillages(pools, received);
            String village = value(submission, "village");
            if (!viable.isEmpty()) assertTrue(viable.contains(village),
                    "submission " + submission.number() + ": viable villages " + viable + ", got " + village);
            if (deep) {
                String district = value(submission, "district");
                String region = value(submission, "region");
                assertTrue(REGIONS.getOrDefault(region, Set.of()).contains(district), "offered district");
                assertTrue(DISTRICTS.getOrDefault(district, Set.of()).contains(village), "offered village");
                var freeDistricts = parentsWithFreeChildren(DISTRICTS, viable);
                if (!freeDistricts.isEmpty()) {
                    assertTrue(freeDistricts.contains(district), "district must lead to a free ID");
                    assertTrue(parentsWithFreeChildren(REGIONS, freeDistricts).contains(region),
                            "region must recursively lead to a free ID");
                }
            }
            String id = value(submission, "answer");
            assertTrue(pools.getOrDefault(village, Set.of()).contains(id), "ID offered by selected village");
            if (!viable.isEmpty()) assertFalse(received.contains(id), "no reuse while a reachable ID is free");
            received.add(id);
        }
    }

    static void assertNoPlanIdWarning(Fieldwork plan, int n) {
        assertEquals(n, plan.visits().size());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertTrue(plan.configWarnings().isEmpty(), plan.configWarnings().toString());
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }
}
