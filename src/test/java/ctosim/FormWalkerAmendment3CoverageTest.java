package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment3CoverageTest {
    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void coverageVillageIsAnAttachedValueOfTheSelectedRegion(int number) throws Exception {
        LoadedForm loaded = FormLoader.load(Path.of("samples/xforms/ctosim_coverage3.xml"), Path.of("samples/coverage3"));
        // Disable optional missingness so both fields exercise the stated acceptance criterion.
        Submission result = new FormWalker(loaded, config("\"missingRate\":0")).walk(number);
        String region = value(result, "selects/region");
        String village = value(result, "selects/village");
        var allowed = new HashSet<String>();
        // This published fixture contains three unquoted columns; no general CSV parser is implied.
        var lines = Files.readAllLines(Path.of("samples/coverage3/villages.csv"));
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            if (cells.length == 3 && cells[2].equals(region)) allowed.add(cells[0]);
        }
        assertFalse(allowed.isEmpty(), "Selected region must have villages");
        assertTrue(allowed.contains(village), "Village " + village + " must belong to region " + region);
        assertNotEquals("village_id", village);
    }

    @Test void coverageWithZeroPeopleHasNoPersonElement() throws Exception {
        LoadedForm loaded = FormLoader.load(Path.of("samples/xforms/ctosim_coverage3.xml"), Path.of("samples/coverage3"));
        Submission result = new FormWalker(loaded, config("\"fields\":{\"n_people\":{\"value\":0}}")).walk(3);
        assertEquals("0", value(result, "n_people"));
        assertEquals(0, count(result, "person"));
    }
}
