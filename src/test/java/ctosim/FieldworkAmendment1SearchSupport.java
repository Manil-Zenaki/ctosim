package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Attached-file fixtures; no production implementation or private state is inspected. */
abstract class FieldworkAmendment1SearchSupport {
    Path scratch;
    Path media;
    Path xform;

    @BeforeEach void prepareAttachments() throws Exception {
        scratch = Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")).toAbsolutePath(),
                "fieldwork-amendment1-");
        media = Files.createDirectory(scratch.resolve("media"));
        xform = scratch.resolve("search-id.xml");
        for (String name : List.of("households.csv", "opening.csv", "header-only.csv", "wrong-column.csv")) {
            try (var in = getClass().getResourceAsStream("/ctosim/fieldwork-plan/amendment1/" + name)) {
                assertNotNull(in, name);
                Files.copy(in, media.resolve(name));
            }
        }
    }

    @AfterEach void removeAttachments() throws Exception {
        if (scratch == null) return;
        assertTrue(scratch.normalize().startsWith(Path.of(".codex-runs").toAbsolutePath().normalize()));
        try (var paths = Files.walk(scratch)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    LoadedForm search(String appearance, String... choices) throws Exception {
        return attached("<answer/>", bind("answer", "string", ""), control(appearance, choices));
    }

    LoadedForm attached(String instance, String binds, String body) throws Exception {
        String template;
        try (var in = getClass().getResourceAsStream("/ctosim/fieldwork-plan/form-template.xml")) {
            assertNotNull(in);
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Files.writeString(xform, template.replace("__INSTANCE__", instance).replace("__BINDS__", binds)
                .replace("__BODY__", body));
        return FormLoader.load(xform, media);
    }

    static String control(String appearance, String... choices) {
        return FormWalkerAmendment3SearchSupport.selectControl("select1", "answer", appearance,
                FormWalkerAmendment3SearchSupport.items(choices));
    }

    static SimulationConfig idConfig(int n, String extra) throws Exception {
        return settings("\"submissions\":" + n + ",\"id\":\"answer\"" + (extra.isEmpty() ? "" : "," + extra));
    }

    static List<String> ids(Fieldwork plan) {
        return plan.visits().stream().map(Visit::id).toList();
    }

    static Fieldwork assertPool(LoadedForm form, SimulationConfig config, Set<String> pool,
            boolean assertStoredIds) throws Exception {
        assertFalse(pool.isEmpty(), "use assertEmptyPool for an empty pool");
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        var ids = ids(plan);
        assertEquals(config.submissions(), ids.size());
        assertEquals(config.submissions(), submissions.size());
        int prefix = Math.min(ids.size(), pool.size());
        assertEquals(prefix, new HashSet<>(ids.subList(0, prefix)).size(), "no repeats before exhaustion");
        assertTrue(pool.containsAll(ids), () -> "unexpected IDs: " + ids);
        if (ids.size() >= pool.size()) assertEquals(pool, new HashSet<>(ids.subList(0, pool.size())));
        for (int i = 0; i < ids.size(); i++) {
            assertEquals(ids.get(i % pool.size()), ids.get(i), "repeat the same permutation every K visits");
            if (assertStoredIds) assertEquals(ids.get(i), value(submissions.get(i), "answer"));
        }
        boolean exhausted = pool.size() < config.submissions();
        assertEquals(exhausted ? 1 : 0, plan.warnings().size(), "plan warnings exclude submission warnings");
        if (exhausted) warning(plan.warnings(), "answer", pool.size(), config.submissions());
        return plan;
    }

    static Fieldwork assertEmptyPool(LoadedForm form) throws Exception {
        var plan = new Fieldwork(form, idConfig(3, ""));
        plan.walkAll();
        assertEquals(3, plan.visits().size());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer");
        return plan;
    }
}
