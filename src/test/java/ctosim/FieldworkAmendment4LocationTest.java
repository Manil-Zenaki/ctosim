package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment4LocationTest extends FieldworkAmendment4Support {
    @Test void locatedVisitsIgnoreTheGlobalAreaAndAllGpsQuestionsFollowTheFinalVillage() throws Exception {
        tables("route-villages.csv", "route-households.csv");
        var plan = new Fieldwork(cascade(), config4(6, 1, 10, LOCATED + "," + AREA + "," + FORCE_A));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++) {
            double lon = switch (value(rows.get(i), "village")) {
                case "A" -> 20; case "B" -> 20.2; case "C" -> 20.01;
                default -> throw new AssertionError("unexpected village");
            };
            Visit visit = plan.visits().get(i);
            near(visit, 10, lon);
            gpsFollowsVisit(rows.get(i), visit, "before", "after", "trace_before", "trace_after", "shape_before", "shape_after");
        }
    }

    @ParameterizedTest @CsvSource({"90,180", "-90,-180", "0,180", "0,-180", "85,179.99999", "-35.2,-71.4"})
    void inclusiveCoordinateLimitsAndTheAntimeridianUseGreatCircleDistances(double lat, double lon) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1," + lat + "," + lon);
        var plan = new Fieldwork(cascade(), config4(8, 2, 4, LOCATED));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++) {
            near(plan.visits().get(i), lat, lon);
            gpsFollowsVisit(rows.get(i), plan.visits().get(i), "before", "after");
        }
    }

    @ParameterizedTest @CsvSource(value = {
            "90.000001|20", "-90.000001|20", "10|180.000001", "10|-180.000001",
            "not-a-number|20", "10|not-a-number", "|20", "10|", "|",
            "NaN|20", "10|NaN", "Infinity|20", "10|-Infinity"
    }, delimiter = '|', nullValues = "")
    void unusableCoordinateRowsFallBackToTheConfiguredAreaWithoutMissingColumnWarnings(String lat, String lon) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1," + (lat == null ? "" : lat) + "," + (lon == null ? "" : lon));
        var plan = new Fieldwork(cascade(), config4(4, 1, 4, LOCATED + "," + AREA));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++) {
            assertEquals("A", value(rows.get(i), "village"), "bad coordinates do not remove choices");
            inFallbackArea(plan.visits().get(i));
            gpsFollowsVisit(rows.get(i), plan.visits().get(i), "before", "after");
        }
        assertTrue(plan.warnings().isEmpty(), "invalid cells are not absent columns: " + plan.warnings());
    }

    @Test void withoutVillagesConfigurationEvenNumericRowsUseTheOriginalArea() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var plan = new Fieldwork(cascade(), config4(4, 1, 4, AREA));
        plan.walkAll();
        for (var visit : plan.visits()) inFallbackArea(visit);
    }

    @Test void anUnlocatedVillageWithoutAnAreaUsesTheDefaultRectangle() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1,bad,20");
        var plan = new Fieldwork(cascade(), config4(4, 1, 4, LOCATED));
        plan.walkAll();
        for (var visit : plan.visits()) {
            assertTrue(visit.latitude() >= -1 && visit.latitude() <= 1);
            assertTrue(visit.longitude() >= -1 && visit.longitude() <= 1);
        }
    }

    @Test void theFirstMatchingCsvRowDeterminesCoordinatesEvenIfALaterRowDisagrees() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1,10,20\nA,D1,-45,-100");
        var plan = new Fieldwork(cascade(), config4(3, 1, 4, LOCATED));
        plan.walkAll();
        for (var visit : plan.visits()) near(visit, 10, 20);
    }

    @Test void aValidLaterDuplicateCannotRepairAnInvalidFirstRow() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D1,bad,20\nA,D1,10,20");
        var plan = new Fieldwork(cascade(), config4(3, 1, 4, LOCATED + "," + AREA));
        plan.walkAll();
        for (var visit : plan.visits()) inFallbackArea(visit);
    }

    @Test void coordinateLookupUsesTheFirstMatchingCodeInTheFileNotOnlyFilteredRows() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "A,D1,10,20", "A,D2,-35,150\nA,D1,10,20");
        var form = cascade("search('a4-villages', 'matches', 'district', 'D1')", ID_SEARCH, false, "");
        var plan = new Fieldwork(form, config4(1, 1, 4, LOCATED));
        assertEquals("A", value(plan.walkAll().get(0), "village"));
        near(plan.visits().get(0), -35, 150);
    }

    @ParameterizedTest @ValueSource(strings = {"0007", "village-\u00c9\u6771"})
    void thePlaceholderColumnMatchesExactChoiceCodesIncludingUnicodeAndLeadingZeros(String code) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "code,district", "village_key,district");
        replaceTable("villages", "A,D1,10,20", code + ",D1,10,20");
        replaceTable("households", ",A\n", "," + code + "\n");
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                select("village", "search('a4-villages')", "village_key") + control(ID_SEARCH, "code"));
        var plan = new Fieldwork(form, config4(2, 1, 4, LOCATED));
        assertEquals(List.of(code, code), column(plan.walkAll(), "village"));
        for (var visit : plan.visits()) near(visit, 10, 20);
    }

    @Test void configuredColumnNamesAreUsedRatherThanAssumedLatitudeOrLongitudeNames() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "lat_col,lon_col", "North_\u00c9,East_\u6771");
        var plan = new Fieldwork(cascade(), config4(2, 1, 4,
                "\"villages\":{\"latitude\":\"North_\u00c9\",\"longitude\":\"East_\u6771\"}"));
        plan.walkAll();
        for (var visit : plan.visits()) near(visit, 10, 20);
        assertTrue(plan.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"before", "trace_before", "shape_after"})
    void aLocationFieldsOwnAreaStillOverridesTheLocatedVisit(String field) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var config = config4(2, 1, 4, LOCATED + ",\"fields\":{\"" + field + "\":{" + AREA + "}}");
        var plan = new Fieldwork(cascade(), config);
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++) {
            near(plan.visits().get(i), 10, 20);
            double[] p = point(value(rows.get(i), field).split(";\\s*")[0]);
            assertTrue(p[0] >= -20 && p[0] <= -19);
            assertTrue(p[1] >= -40 && p[1] <= -39);
            gpsFollowsVisit(rows.get(i), plan.visits().get(i), "after");
        }
    }

    @Test void aStaticVillageChoiceWithoutAMatchingCsvRowRemainsUnlocated() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("households", ",B\n", ",8\n");
        var form = attached("<village/><answer/>", bind("village", "string", "") + bind("answer", "string", ""),
                select("village", "search('a4-villages')", "code", "8") + control(ID_SEARCH, "code"));
        var plan = new Fieldwork(form, config4(2, 1, 4, LOCATED + "," + AREA
                + ",\"fields\":{\"village\":{\"weights\":{\"8\":1}}}"));
        assertEquals(List.of("8", "8"), column(plan.walkAll(), "village"));
        for (var visit : plan.visits()) inFallbackArea(visit);
    }
}
