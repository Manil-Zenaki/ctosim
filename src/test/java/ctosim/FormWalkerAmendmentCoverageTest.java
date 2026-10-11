package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendmentCoverageTest {
    private Submission coverage(int number) throws Exception {
        return new FormWalker(FormLoader.load(Path.of("samples/xforms/ctosim_coverage.xml"),
                Path.of("samples/coverage")), SimulationConfig.defaults()).walk(number);
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void coverageDurationEqualsTheWholeInterview(int number) throws Exception {
        Submission result = coverage(number);
        assertEquals(seconds(result), integer(result, "duration"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void coverageTimeMainWhenPresentIsAnElapsedWholeNumber(int number) throws Exception {
        Submission result = coverage(number);
        if (count(result, "main/time_main") > 0) {
            long elapsed = integer(result, "main/time_main");
            assertTrue(elapsed >= 1 && elapsed <= seconds(result), "time_main=" + elapsed);
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void coveragePreloadsMatchTheSelectedHouseholdRow(int number) throws Exception {
        Submission result = coverage(number);
        String hhid = value(result, "id_grp/hhid");
        List<String[]> matches = Files.readAllLines(Path.of("samples/coverage/hh_preload.csv"), StandardCharsets.UTF_8)
                .stream().skip(1).map(line -> line.split(",", -1)).filter(row -> row[0].equals(hhid)).toList();
        assertEquals(1, matches.size(), "The checked-in simple CSV must contain the selected hhid " + hhid);
        assertEquals(matches.get(0)[1], value(result, "id_grp/pl_head"));
        assertEquals(matches.get(0)[2], value(result, "id_grp/pl_size"));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void coverageUsesDefaultEnumeratorIdentityAndUtcTimestamp(int number) throws Exception {
        Submission result = coverage(number);
        assertTrue(value(result, "username").matches("enumerator0[1-5]"));
        assertTrue(value(result, "deviceid").matches("[0-9a-f]{16}"));
        String start = value(result, "starttime");
        assertTrue(start.endsWith("+00:00") || start.endsWith("Z"), start);
    }
}
