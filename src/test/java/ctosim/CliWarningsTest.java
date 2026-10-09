package ctosim;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CliWarningsTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    static String warningConfig() {
        return """
                {"submissions":2,"fields":{"z_override":{"value":1},"a_override":{"value":1},
                 "bad":{"value":"invalid\rinteger\nvalue"}}}
                """.replace("invalid\rinteger\nvalue", "invalid\\rinteger\\nvalue");
    }

    static List<String> expectedWarnings(Input input, Path media, Path config) throws Exception {
        LoadedForm form = FormLoader.load(input.form(), media);
        FormWalker walker = new FormWalker(form, SimulationConfig.load(config));
        List<String> expected = new ArrayList<>();
        for (String name : form.missingAttachments()) expected.add("attached file " + name + ".csv not found");
        for (String name : form.stubbedFunctions()) expected.add("function " + name
                + "() is not implemented by the simulator; it returns a fixed value");
        expected.addAll(walker.configWarnings());
        expected.addAll(walker.walk(1).warnings());
        expected.addAll(walker.walk(2).warnings());
        return expected;
    }

    // Fixtures use isolated CR and LF; the spec leaves CRLF normalization ambiguous.
    static String singleLine(String warning) {
        return warning.replaceAll("\\r\\n|[\\r\\n]", " ");
    }

    @Test void warningsAreCollectedByCategoryThenSubmissionWithCorrectCountAndNoEmbeddedLineBreaks() throws Exception {
        Input input = fixture(temp, "warnings", "warnings.xml");
        Path config = config(temp, warningConfig());
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", config.toString());
        success(result);
        List<String> actual = warnings(output);
        assertEquals(List.of("attached file a_people.csv not found", "attached file z_people.csv not found",
                "function a_cli_unknown() is not implemented by the simulator; it returns a fixed value",
                "function z_cli_unknown() is not implemented by the simulator; it returns a fixed value"), actual.subList(0, 4));
        assertTrue(actual.get(4).contains("a_override"));
        assertTrue(actual.get(5).contains("z_override"));
        assertTrue(actual.get(6).startsWith("submission 1:"));
        assertTrue(actual.get(actual.size() - 1).startsWith("submission 2:"));
        assertEquals(expectedWarnings(input, temp, config).stream().map(CliWarningsTest::singleLine).toList(), actual);
        assertTrue(result.out().lines().toList().get(1).contains(Integer.toString(actual.size())), result.out());
        assertFalse(report(output).contains("\r"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void mediaIsReadFromFormDirectoryByDefaultOrExplicitDirectory(boolean explicit) throws Exception {
        Input input = fixture(temp.resolve("source"), "warnings", "warnings.xml");
        Path media = explicit ? temp.resolve("attached files") : input.form().getParent();
        write(media.resolve("a_people.csv"), "id,name\n1,Adama\n");
        write(media.resolve("z_people.csv"), "id,name\n1,Bishoftu\n");
        Path output = temp.resolve("separate-output");
        Result result = explicit
                ? simulate(input, output, "-n", "1", "--media", media.toString())
                : simulate(input, output, "-n", "1");
        success(result);
        assertEquals(List.of("Adama"), column(output.resolve("Warnings CLI_WIDE.csv"), "alookup"));
        assertEquals(List.of("Bishoftu"), column(output.resolve("Warnings CLI_WIDE.csv"), "zlookup"));
        assertTrue(warnings(output).stream().noneMatch(w -> w.startsWith("attached file ")));
    }

    @Test void explicitMediaOverridesAnExistingDifferentAttachmentBesideTheForm() throws Exception {
        Input input = fixture(temp.resolve("source"), "warnings", "warnings.xml");
        write(input.form().getParent().resolve("a_people.csv"), "id,name\n1,WRONG\n");
        Path media = temp.resolve("correct-media");
        write(media.resolve("a_people.csv"), "id,name\n1,RIGHT\n");
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "1", "-m", media.toString()));
        assertEquals(List.of("RIGHT"), column(output.resolve("Warnings CLI_WIDE.csv"), "alookup"));
        assertTrue(warnings(output).contains("attached file z_people.csv not found"));
        assertFalse(warnings(output).contains("attached file a_people.csv not found"));
    }
}
