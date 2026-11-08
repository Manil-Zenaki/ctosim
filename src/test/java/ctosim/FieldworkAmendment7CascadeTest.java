package ctosim;

import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FieldworkAmendment7CascadeTest extends FieldworkAmendment1SearchSupport {
    @BeforeEach void copyCascadeTables() throws Exception {
        for (String name : List.of("districts", "villages", "households")) {
            try (var in = getClass().getResourceAsStream("/ctosim/fieldwork-plan/amendment5/" + name + ".csv")) {
                assertNotNull(in);
                Files.copy(in, media.resolve("a7-" + name + ".csv"));
            }
        }
    }

    @Test void aCascadeAfterAvailabilityStillReplaysItsIdsAndPlaceAlongsideEarlierIdentification() throws Exception {
        var form = attached("<attempt/><identity/><status/><district/><village/><answer/>",
                bind("attempt", "int", "") + bind("identity", "string", "") + bind("status", "string", "")
                + bind("district", "string", "") + bind("village", "string", "") + bind("answer", "string", ""),
                input("attempt") + input("identity") + choices("status", false, "yes", "absent")
                + searchControl("district", "search('a7-districts')")
                + searchControl("village", "search('a7-villages', 'matches', 'district', /data/district)")
                + searchControl("answer", "search('a7-households', 'matches', 'village', /data/village)"));
        var cfg = config7(4, 1, 2, 2, true, "\"fields\":{"
                + field("identity", expr("concat('visit-', /data/attempt)")) + "}");
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertEquals(List.of(1, 1, 2, 2), plan.visits().stream().map(Visit::attempt).toList());
        assertNotEquals(value(rows.get(0), "answer"), value(rows.get(1), "answer"), "two households must be exercised");
        for (int i = 0; i < 2; i++) {
            for (String path : List.of("answer", "district", "village")) {
                assertFalse(value(rows.get(i), path).isEmpty());
                assertEquals(value(rows.get(i), path), value(rows.get(i + 2), path),
                        "Amendment 5 remains authoritative for the cascade after availability");
            }
            assertEquals(value(rows.get(i), "identity"), value(rows.get(i + 2), "identity"));
        }
    }

    private static String searchControl(String name, String expression) {
        return FormWalkerAmendment3SearchSupport.selectControl("select1", name, expression,
                FormWalkerAmendment3SearchSupport.items("code"));
    }
}
