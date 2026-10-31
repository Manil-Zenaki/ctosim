package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment4WarningsTest extends FieldworkAmendment4Support {
    @ParameterizedTest @ValueSource(strings = {"lat_col", "lon_col"})
    void eachMissingCoordinateColumnIsNamedWithItsFileOnlyOncePerRun(String missing) throws Exception {
        tables("two-villages.csv", "abundant-households.csv");
        replaceTable("villages", missing, "wrong_column");
        var plan = new Fieldwork(cascade(), config4(12, 3, 2, LOCATED + "," + AREA));
        assertTrue(plan.configWarnings().isEmpty());
        assertTrue(plan.warnings().isEmpty(), "runtime warnings are empty before walkAll");
        plan.walkAll();
        assertEquals(1, plan.warnings().size());
        assertColumnWarning(plan.warnings().get(0), missing);
        for (var visit : plan.visits()) inFallbackArea(visit);
        var expected = List.copyOf(plan.warnings());
        plan.walkAll();
        assertEquals(expected, plan.warnings(), "diagnostics reset instead of accumulating between runs");
    }

    @Test void twoMissingColumnsProduceTwoWarningsAfterOverflowAndReusedIdWarnings() throws Exception {
        tables("single-village.csv", "two-households.csv");
        replaceTable("villages", "lat_col,lon_col", "other_lat,other_lon");
        var plan = new Fieldwork(cascade(), config4(3, 1, 1, LOCATED + ",\"fieldworkDays\":1"));
        plan.walkAll();
        var warnings = plan.warnings();
        assertEquals(4, warnings.size(), warnings.toString());
        warning(List.of(warnings.get(0)), null, 3, 1);
        warning(List.of(warnings.get(1)), "answer", 2);
        for (String column : List.of("lat_col", "lon_col")) {
            assertEquals(1, warnings.subList(2, 4).stream()
                    .filter(w -> w.contains(column) && w.contains("a4-villages.csv")).count());
        }
        assertThrows(UnsupportedOperationException.class, () -> warnings.add("changed"));
    }

    @Test void columnNamesAreCaseSensitive() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "lat_col", "LAT_COL");
        var plan = new Fieldwork(cascade(), config4(1, 1, 4, LOCATED + "," + AREA));
        plan.walkAll();
        assertEquals(1, plan.warnings().size());
        assertColumnWarning(plan.warnings().get(0), "lat_col");
        inFallbackArea(plan.visits().get(0));
    }

    @Test void absentCoordinateConfigurationDoesNotWarnAboutMissingColumns() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        replaceTable("villages", "lat_col,lon_col", "other_lat,other_lon");
        var plan = new Fieldwork(cascade(), config4(2, 1, 4, ""));
        plan.walkAll();
        assertTrue(plan.warnings().isEmpty());
        assertTrue(plan.configWarnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"no-id", "text-id", "literal-filter", "expression-filter", "plain-select"})
    void aConfigurationWarningExplainsWhenTheFormHasNoVillageQuestion(String kind) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        LoadedForm form;
        SimulationConfig config = config4(1, 1, 4, LOCATED + "," + AREA);
        switch (kind) {
            case "no-id" -> {
                form = cascade();
                config = settings("\"submissions\":1," + LOCATED + "," + AREA);
            }
            case "text-id" -> form = question("string", "");
            case "literal-filter" -> form = cascade(VILLAGE_SEARCH,
                    "search('a4-households', 'matches', 'village', 'A')", false, "");
            case "expression-filter" -> form = cascade(VILLAGE_SEARCH,
                    "search('a4-households', 'matches', 'village', concat('', /data/village))", false, "");
            case "plain-select" -> form = cascade("", ID_SEARCH, false, "");
            default -> throw new AssertionError(kind);
        }
        var plan = new Fieldwork(form, config);
        assertNoVillageWarning(plan);
        plan.walkAll();
        inFallbackArea(plan.visits().get(0));
        assertNoVillageWarning(plan);
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"value\":\"A\"", "\"texts\":[\"A\"]", "\"expr\":\"'A'\"",
            "\"when\":[{\"if\":\"true()\",\"value\":\"A\"}]",
            "\"weights\":{\"A\":1},\"when\":[{\"if\":\"true()\",\"missingRate\":0}]"
    })
    void anUnsteeredVillageWarnsAndDisablesCoordinatePlacement(String override) throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var plan = new Fieldwork(cascade(), config4(2, 1, 4,
                LOCATED + "," + AREA + ",\"fields\":{\"village\":{" + override + "}}"));
        assertNoVillageWarning(plan);
        plan.walkAll();
        for (var visit : plan.visits()) inFallbackArea(visit);
    }

    @Test void anInapplicableRuleDoesNotDisableTheVillageOrItsCoordinates() throws Exception {
        tables("single-village.csv", "abundant-households.csv");
        var plan = new Fieldwork(cascade(), config4(2, 1, 4, LOCATED
                + ",\"fields\":{\"village\":{\"when\":[{\"if\":\"false()\",\"value\":\"A\"}]}}"));
        assertTrue(plan.configWarnings().isEmpty());
        plan.walkAll();
        for (var visit : plan.visits()) near(visit, 10, 20);
    }

    @Test void noVillageConfigurationDoesNotAddAnInapplicabilityWarning() throws Exception {
        var plan = new Fieldwork(question("string", ""), config4(1, 1, 4, ""));
        assertTrue(plan.configWarnings().isEmpty());
    }

    private static void assertColumnWarning(String warning, String column) {
        assertTrue(warning.contains(column), warning);
        assertTrue(warning.contains("a4-villages.csv"), warning);
    }

    private static void assertNoVillageWarning(Fieldwork plan) {
        assertEquals(1, plan.configWarnings().size(), plan.configWarnings().toString());
        assertTrue(plan.configWarnings().get(0).toLowerCase(java.util.Locale.ROOT).contains("village"),
                plan.configWarnings().toString());
    }
}
