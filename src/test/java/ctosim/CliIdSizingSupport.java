package ctosim;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 10, Clarification of Amendment 4: public API orchestration only. */
final class CliIdSizingSupport {
    static final String FIXTURE = "id-sizing/cascade";
    static final String WIDE = "CLI sized ids_WIDE.csv";

    static Input input(Path directory) throws Exception {
        return fixture(directory, FIXTURE, "survey.xml");
    }

    static ObjectNode settings(String id, int count) throws Exception {
        ObjectNode result = CliAmendment4Support.settings();
        result.put("submissions", count).put("fieldworkDays", 365);
        if (id != null) result.put("id", id);
        return result;
    }

    static FakeAttachments generated(Input input, Path media, ObjectNode effective) throws Exception {
        return FakeAttachments.of(FormLoader.load(input.form(), media), effective.path("seed").longValue(),
                effective.hasNonNull("id") ? effective.get("id").textValue() : null,
                effective.get("submissions").intValue());
    }

    static List<List<String>> rows(Path output, String name) throws Exception {
        return CliAmendment2Support.fictitiousCsv(output.resolve("attachments/" + name + ".csv"));
    }

    static List<String> values(Path output, String file, String column) throws Exception {
        var rows = rows(output, file);
        int index = rows.get(0).indexOf(column);
        assertTrue(index >= 0, "Missing attachment column " + column);
        return rows.subList(1, rows.size()).stream().map(row -> row.get(index)).toList();
    }

    static void uniqueIds(Path output, String id, int count) throws Exception {
        var ids = column(output.resolve(WIDE), id);
        assertEquals(count, ids.size());
        assertFalse(ids.contains(""), "Assigned survey ids are never blank");
        assertEquals(count, new HashSet<>(ids).size(), "Sized file must supply a distinct id per submission");
        String file = id.equals("category") ? "categories" : "places";
        assertTrue(new HashSet<>(values(output, file, id)).containsAll(ids));
    }

    static void pipeline(Input input, Path originalMedia, Path output, Path oracle, ObjectNode effective)
            throws Exception {
        FakeAttachments fake = generated(input, originalMedia, effective);
        CliAmendment2Support.assertAttachmentBytes(fake, output);
        Path media = Files.createDirectories(oracle.resolve("media"));
        try (var files = Files.list(originalMedia)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".csv")).toList())
                Files.copy(file, media.resolve(file.getFileName()));
        }
        for (var file : fake.files().entrySet()) write(media.resolve(file.getKey() + ".csv"), file.getValue());
        // This fixture's pulldata key is a select, so no TEXT/INTEGER key suggestions apply.
        assertTrue(fake.keyAnswers().isEmpty());
        var expected = CliAmendment4Support.expected(input, media, effective);
        CliAmendment4Support.exports(input, output, expected, "example");
        assertEquals(CliAmendment4Support.expectedWarnings(expected), warnings(output));
    }
}
