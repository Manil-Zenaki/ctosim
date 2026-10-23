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

/** Spec-only fixtures and an independent, submission-order allocation oracle. */
abstract class FieldworkAmendment2Support extends FieldworkAmendment1SearchSupport {
    static final String DYNAMIC = "search('cascade-households', 'matches', 'village', /data/village)";
    static final Set<String> FIRST = Set.of("0007", "É02", "A03");
    static final Map<String, Set<String>> POOLS = Map.of(
            "1", FIRST, "2", Set.of("B01", "B02", "B03"),
            "3", Set.of("C01", "C02"), "4", Set.of("D01", "D02"));
    static final Map<String, Set<String>> OVERLAP = Map.of(
            "1", Set.of("SHARED", "ONLY1"), "2", Set.of("SHARED", "ONLY2"),
            "3", Set.of("SOLO"));

    @BeforeEach void copyCascadeAttachments() throws Exception {
        for (String name : List.of("households", "villages", "overlap")) {
            try (var in = getClass().getResourceAsStream(
                    "/ctosim/fieldwork-plan/amendment2/" + name + ".csv")) {
                assertNotNull(in, name);
                Files.copy(in, media.resolve("cascade-" + name + ".csv"));
            }
        }
    }

    LoadedForm cascade() throws Exception {
        return cascade(DYNAMIC, "", "", "code");
    }

    LoadedForm cascade(String appearance, String openingVillage, String idAttributes,
            String... choices) throws Exception {
        return attached("<village>" + openingVillage + "</village><answer/>",
                bind("village", "string", "required=\"true()\"") + bind("answer", "string", idAttributes),
                selectControl("select1", "village", "", items("1", "2", "3", "4"))
                        + control(appearance, choices));
    }

    static SimulationConfig fixedVillage(int n, String idOverride) throws Exception {
        return idConfig(n, "\"fields\":{\"village\":{\"value\":\"1\"}"
                + (idOverride.isEmpty() ? "" : ",\"answer\":{" + idOverride + "}") + "}");
    }

    static List<String> answers(List<Submission> submissions) throws Exception {
        var answers = new ArrayList<String>();
        for (var submission : submissions) answers.add(value(submission, "answer"));
        return answers;
    }

    static void deferredVisits(Fieldwork plan, int n) {
        assertEquals(n, plan.visits().size());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null),
                "answer-dependent search IDs must not be preallocated on visits");
        assertTrue(plan.configWarnings().isEmpty(), plan.configWarnings().toString());
    }

    /** Reconstruct only the offered sets from the fixture and earlier accepted IDs. */
    static int checkAllocation(List<Submission> submissions, Map<String, Set<String>> pools) throws Exception {
        var received = new HashSet<String>();
        int reused = 0;
        for (int i = 0; i < submissions.size(); i++) {
            var submission = submissions.get(i);
            assertEquals(i + 1, submission.number());
            String village = value(submission, "village");
            Set<String> offered = pools.get(village);
            assertNotNull(offered, "unexpected village " + village);
            String id = value(submission, "answer");
            assertTrue(offered.contains(id), "submission " + (i + 1) + " offered " + offered + " but got " + id);
            var unused = new HashSet<>(offered);
            unused.removeAll(received);
            if (!unused.isEmpty()) {
                assertTrue(unused.contains(id), "submission " + (i + 1) + " must use an unused offered ID: " + unused);
            } else {
                reused++;
            }
            received.add(id);
        }
        return reused;
    }

    static void reuseWarning(Fieldwork plan, int reused) {
        assertEquals(reused == 0 ? 0 : 1, plan.warnings().size(),
                "only one aggregate reuse warning, with no preallocation-pool warning");
        if (reused != 0) warning(plan.warnings(), "answer", reused);
    }
}
