package ctosim;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 04 Amendment 4 is the configuration contract explicitly incorporated by spec 13. */
class FieldworkIdConfigTest {
    @Test void idIsAbsentByDefaultAndForAnEmptyConfiguration() throws Exception {
        assertEquals(Optional.empty(), SimulationConfig.defaults().id());
        assertEquals(Optional.empty(), SimulationConfig.parse("{}", "id.json").id());
    }

    @ParameterizedTest @ValueSource(strings = {"survey_id", "household", "家庭_é", "id"})
    void validNamesArePreservedWithoutCheckingAForm(String name) throws Exception {
        var config = SimulationConfig.parse("{\"id\":\"" + name + "\"}", "id.json");
        assertEquals(Optional.of(name), config.id());
    }

    @ParameterizedTest @ValueSource(strings = {"null", "1", "true", "[]", "{}", "\"\"", "\" answer\"", "\"answer \"",
            "\"\\tanswer\"", "\"answer\\n\""})
    void invalidIdsAreConfigurationErrorsNamingTheSourceAndKey(String json) {
        var error = assertThrows(ConfigException.class,
                () -> SimulationConfig.parse("{\"id\":" + json + "}", "fieldwork-settings.json"));
        assertTrue(error.getMessage().contains("fieldwork-settings.json"));
        assertTrue(error.getMessage().matches("(?s).*\\bid\\b.*"), "diagnostic names the key independently of the source");
    }

    @Test void seedAndSubmissionCopiesPreserveTheConfiguredId() throws Exception {
        var original = SimulationConfig.parse("{\"id\":\"survey_id\",\"seed\":4,\"submissions\":8}", "id.json");
        var seedCopy = original.withSeed(91);
        var countCopy = original.withSubmissions(3);
        assertEquals(Optional.of("survey_id"), seedCopy.id());
        assertEquals(Optional.of("survey_id"), countCopy.id());
        assertEquals(Optional.of("survey_id"), original.id());
        assertEquals(4, original.seed());
        assertEquals(8, original.submissions());
    }
}
