package ctosim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Amendment 1 fixtures and a path oracle driven by explicit spec-derived branch counts. */
abstract class FakeAttachmentsAmendment1Support extends FakeAttachmentsTestSupport {
    static String cascadeXml() throws Exception {
        try (var in = FakeAttachmentsAmendment1Support.class.getResourceAsStream(
                "/ctosim/fake-attachments/amendment1/cascade.xml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static LoadedForm cascade() throws Exception {
        return load(cascadeXml());
    }

    static void assertUnchanged(LoadedForm form, String idField, int submissions) {
        var baseline = FakeAttachments.of(form, SEED);
        var actual = FakeAttachments.of(form, SEED, idField, submissions);
        assertEquals(baseline.files(), actual.files(), "Ineligible or unnecessary expansion preserves exact CSV bytes");
        assertEquals(baseline.keyAnswers(), actual.keyAnswers());
    }

    static void assertPaths(Table actual, int idLevel, int submissions, int... branches) {
        // The caller supplies fixed expected branch counts, not a reimplementation
        // of the production decision about when/how far to expand.
        List<List<String>> expected = List.of(List.of());
        for (int count : branches) {
            List<List<String>> next = new ArrayList<>();
            int width = Integer.toString(count).length();
            for (List<String> path : expected) {
                String parent = path.isEmpty() ? "" : path.get(path.size() - 1);
                for (int child = 1; child <= count; child++) {
                    List<String> extended = new ArrayList<>(path);
                    extended.add(parent + String.format(Locale.ROOT, "%0" + width + "d", child));
                    next.add(List.copyOf(extended));
                }
            }
            expected = next;
        }
        List<List<String>> observed = new ArrayList<>();
        for (int row = 0; row < actual.rows().size(); row++) {
            List<String> path = new ArrayList<>();
            for (int level = 0; level < branches.length; level++) {
                int column = actual.header().indexOf("code" + level);
                assertTrue(column >= 0);
                path.add(actual.rows().get(row).get(column));
            }
            observed.add(path);
        }
        assertEquals(expected.size(), observed.size(), "Exactly one row per expected terminal code");
        assertEquals(new HashSet<>(expected), new HashSet<>(observed), "Complete paths, independent of CSV row order");
        assertEquals(observed.size(), new HashSet<>(actual.column("code" + idLevel)).size(),
                "One distinct survey id per row even when levels follow the id");
        assertTrue(observed.size() >= submissions);
    }
}
