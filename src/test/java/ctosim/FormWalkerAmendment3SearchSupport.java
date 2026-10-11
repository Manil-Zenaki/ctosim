package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures and observable choice assertions for spec 07 Amendment 3. */
abstract class FormWalkerAmendment3SearchSupport {
    Path area;
    Path media;
    Path xform;

    @BeforeEach void prepareMedia() throws Exception {
        area = Files.createTempDirectory(Files.createDirectories(Path.of(".codex-runs")).toAbsolutePath(), "walker-search-");
        media = Files.createDirectory(area.resolve("media"));
        xform = area.resolve("search.xml");
        for (String name : List.of("search", "sorted", "quoted", "header-only")) {
            Files.writeString(media.resolve(name + ".csv"), resource("amendment3/" + name + ".csv"));
        }
    }

    @AfterEach void removeMedia() throws Exception {
        if (area != null) {
            // The directory is created by this test, strictly under the repository scratch area.
            assertTrue(area.normalize().startsWith(Path.of(".codex-runs").toAbsolutePath().normalize()));
            try (var paths = Files.walk(area)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }

    static String attribute(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    static String items(String... values) {
        StringBuilder xml = new StringBuilder();
        for (String v : values) xml.append("<item><label>Label for ").append(v)
                .append("</label><value>").append(v).append("</value></item>");
        return xml.toString();
    }

    static String selectControl(String control, String path, String appearance, String choices) {
        return "<" + control + " ref=\"/data/" + path + "\" appearance=\"" + attribute(appearance)
                + "\"><label>Search choices</label>" + choices + "</" + control + ">";
    }

    LoadedForm attached(String instance, String binds, String body, String secondary) throws Exception {
        Files.writeString(xform, resource("form-template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body)
                .replace("__SECONDARY__", secondary).replace("__ACTIONS__", ""));
        return FormLoader.load(xform, media);
    }

    LoadedForm search(String control, String appearance, String... choices) throws Exception {
        return attached("<answer/>", bind("answer", "string", ""),
                selectControl(control, "answer", appearance, items(choices)), "");
    }

    static SimulationConfig searchConfig(String fields) throws Exception {
        return config("\"seed\":813731,\"missingRate\":0,\"specialRate\":0,\"specialValues\":[],\"fields\":{" + fields + "}");
    }

    static Submission fixedChoice(LoadedForm form, String choice) throws Exception {
        return new FormWalker(form, searchConfig("\"answer\":{\"value\":\"" + choice + "\"}")).walk(17);
    }

    static void assertAccepted(LoadedForm form, String choice) throws Exception {
        Submission result = fixedChoice(form, choice);
        assertEquals(choice, value(result, "answer"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    static void assertRejected(LoadedForm form, String choice) throws Exception {
        Submission result = fixedChoice(form, choice);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", choice);
        assertElapsed(result, 1);
    }

    static void assertGeneratedChoices(LoadedForm form, List<String> expected) throws Exception {
        FormWalker walker = new FormWalker(form, searchConfig(""));
        var seen = new HashSet<String>();
        int largest = 0;
        for (int n = 1; n <= 120; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            if (expected.isEmpty()) {
                assertEquals("", answer);
                assertEquals(0, result.answered());
            } else {
                assertFalse(answer.isBlank());
                List<String> tokens = List.of(answer.split(" "));
                assertEquals(tokens.size(), new HashSet<>(tokens).size(), "No repeated answer values");
                int previous = -1;
                for (String token : tokens) {
                    int at = expected.indexOf(token);
                    assertTrue(at >= 0, "Unexpected choice: " + token);
                    assertTrue(at > previous, "Answer must preserve loaded/static choice order: " + answer);
                    previous = at;
                }
                seen.addAll(tokens);
                largest = Math.max(largest, tokens.size());
                assertEquals(1, result.answered());
            }
            assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        }
        assertEquals(new HashSet<>(expected), seen, "All offered values should be reachable across fixed-seed draws");
        assertEquals(Math.min(3, expected.size()), largest);
    }
}
