package ctosim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment4PlaceTest extends FieldworkAmendment4Support {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void eachEnumeratorKeepsItsWholePlaceForTheDayWhileIdsRemain(boolean located) throws Exception {
        tables("route-villages.csv", "abundant-households.csv");
        var config = config4(12, 3, 2, located ? LOCATED : "");
        var plan = new Fieldwork(cascade(), config);
        var rows = plan.walkAll();
        var places = new HashMap<String, List<String>>();
        for (int i = 0; i < rows.size(); i++) {
            Visit visit = plan.visits().get(i);
            String key = visit.enumerator() + ":" + day(config, visit);
            var place = List.of(value(rows.get(i), "district"), value(rows.get(i), "village"));
            var previous = places.putIfAbsent(key, place);
            if (previous != null) assertEquals(previous, place, "place of enumerator/day " + key);
        }
        assertEquals(6, places.size(), "three enumerators work two days, twice each day");
        assertEquals(12, new HashSet<>(column(rows, "answer")).size());
    }

    @Test void anInterleavedEnumeratorDoesNotReplaceAnotherEnumeratorsPlace() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var plan = new Fieldwork(cascade(), config4(4, 2, 4, LOCATED + "," + FORCE_A));
        var rows = plan.walkAll();
        // Enumerator 2's first interview prefers B (D1 is still viable); enumerator 1
        // moves from exhausted A to nearest C, then enumerator 2 must keep B.
        assertEquals(List.of("A", "B", "C", "B"), column(rows, "village"));
        assertEquals(List.of(1, 2, 1, 2), plan.visits().stream().map(Visit::enumerator).toList());
    }

    @Test void aPlacePersistsAcrossMidnightWhenItIsStillTheSameWorkDay() throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        var config = SimulationConfig.parse("""
                {"seed":731991,"submissions":2,"enumerators":1,"interviewsPerDay":2,
                 "start":"2026-03-02T23:00:00","id":"answer","missingRate":0,"specialRate":0}
                """, "midnight-place.json");
        var plan = new Fieldwork(cascade(VILLAGE_SEARCH, ID_SEARCH, false,
                "ct:minimum-seconds=\"3600\""), config);
        var rows = plan.walkAll();
        assertEquals(0, day(config, plan.visits().get(1)));
        assertTrue(rows.get(1).start().toLocalDate().isAfter(rows.get(0).start().toLocalDate()));
        assertEquals(value(rows.get(0), "village"), value(rows.get(1), "village"));
    }

    @Test void movingReplacesThePlaceForFollowingInterviewsOnThatDay() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var plan = new Fieldwork(cascade(), config4(6, 1, 10, LOCATED + "," + FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of("A", "C", "C", "C", "B", "B"), column(rows, "village"));
        assertEquals(List.of("D1", "D2", "D2", "D2", "D1", "D1"), column(rows, "district"));
        assertEquals(6, new HashSet<>(column(rows, "answer")).size());
        assertTrue(plan.warnings().isEmpty(), plan.warnings().toString());
    }

    @Test void exhaustingEveryCandidateRestoresOrdinaryWeightsAndCountsReusedIds() throws Exception {
        tables("two-villages.csv", "two-households.csv");
        var plan = new Fieldwork(cascade(), config4(4, 1, 4, LOCATED + "," + FORCE_A));
        var rows = plan.walkAll();
        assertEquals(List.of("A", "B", "A", "A"), column(rows, "village"));
        assertEquals(List.of("A01", "B01", "A01", "A01"), column(rows, "answer"));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer", 2);
    }

    @Test void anExhaustedVillageCannotBeKeptJustBecauseAnIdWasUsedByAnotherEnumerator() throws Exception {
        tables("single-village.csv", "two-households.csv");
        var config = config4(2, 2, 4, LOCATED + ",\"fields\":{\"village\":{\"missingRate\":1}}");
        var plan = new Fieldwork(cascade(), config);
        assertEquals(List.of("A", ""), column(plan.walkAll(), "village"),
                "the second enumerator has no viable candidate and uses the ordinary missing rule");
    }

    @Test void aRememberedVillageIsNotReusedWhenItIsNoLongerOffered() throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        replaceTable("villages", "lon_col", "lon_col,gate");
        replaceTable("villages", "A,D1,10,20\n", "A,D1,10,20,1\n");
        replaceTable("villages", "B,D1,10,20.2\n", "B,D1,10,20.2,2\n");
        var form = attached("<gate/><district/><village/><answer/>", bind("gate", "int", "")
                        + bind("district", "string", "") + bind("village", "string", "") + bind("answer", "string", ""),
                input("gate") + select("district", "search('a4-districts')", "code")
                        + select("village", "search('a4-villages', 'matches', 'district', /data/district, 'gate', /data/gate)", "code")
                        + control(ID_SEARCH, "code"));
        var config = config4(12, 1, 4, "\"fields\":{\"gate\":{\"min\":1,\"max\":2}}");
        var rows = new Fieldwork(form, config).walkAll();
        for (var row : rows) assertEquals(value(row, "gate").equals("1") ? "A" : "B", value(row, "village"));
        assertEquals(2, new HashSet<>(column(rows, "gate")).size(), "exercise both offered sets");
    }

    @ParameterizedTest @ValueSource(strings = {
            "search('a4-households', 'matches', 'village', ../village)",
            "search('a4-households', 'contains', 'code', '', 'village', /data/village)"
    })
    void relativeReferencesAndFilterTextBothIdentifyTheVillageOfTheDay(String idSearch) throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        var plan = new Fieldwork(cascade(VILLAGE_SEARCH, idSearch, true, ""), config4(4, 1, 4, LOCATED));
        var rows = plan.walkAll();
        assertEquals(1, new HashSet<>(column(rows, "village")).size());
        for (int i = 0; i < rows.size(); i++) {
            near(plan.visits().get(i), 10, value(rows.get(i), "village").equals("A") ? 20 : 20.2);
        }
    }

    @Test void keepingAPlaceSuppressesMissingRatesForTheVillageAndItsUpperQuestions() throws Exception {
        tables("route-villages.csv", "abundant-households.csv");
        var plan = new Fieldwork(cascade(), config4(4, 1, 4, LOCATED + ",\"fields\":{"
                + "\"district\":{\"missingRate\":1},\"village\":{\"missingRate\":1},\"answer\":{\"missingRate\":1}}"));
        var rows = plan.walkAll();
        assertFalse(value(rows.get(0), "district").isEmpty());
        assertFalse(value(rows.get(0), "village").isEmpty());
        assertEquals(1, new HashSet<>(column(rows, "district")).size());
        assertEquals(1, new HashSet<>(column(rows, "village")).size());
        for (var row : rows) assertFalse(value(row, "answer").isEmpty());
    }

    @Test void aMoveChangesAndThenKeepsAllUpperAnswersInADeeperCascade() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        Files.writeString(media.resolve("a4-regions.csv"), resource4("regions.csv"));
        replaceTable("districts", "code\nD1\nD2\nD3\n", "code,region\nD1,R1\nD2,R2\nD3,R3\n");
        var form = attached("<region/><district/><village/><answer/>", bind("region", "string", "")
                        + bind("district", "string", "") + bind("village", "string", "") + bind("answer", "string", ""),
                select("region", "search('a4-regions')", "code")
                        + select("district", "search('a4-districts', 'matches', 'region', /data/region)", "code")
                        + select("village", VILLAGE_SEARCH, "code") + control(ID_SEARCH, "code"));
        var config = config4(4, 1, 4, LOCATED + ",\"fields\":{\"region\":{\"weights\":{\"R1\":1}},"
                + "\"district\":{\"weights\":{\"D1\":1}},\"village\":{\"weights\":{\"A\":1}}}");
        var rows = new Fieldwork(form, config).walkAll();
        assertEquals(List.of("A", "C", "C", "C"), column(rows, "village"));
        assertEquals(List.of("D1", "D2", "D2", "D2"), column(rows, "district"));
        assertEquals(List.of("R1", "R2", "R2", "R2"), column(rows, "region"));
    }
}
