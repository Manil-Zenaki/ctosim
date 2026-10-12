package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5ConstraintOracle.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment5AcceptanceTest {
    static Stream<Arguments> publishedSubmissions() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("samples/xforms"))) {
            var forms = files.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
            assertFalse(forms.isEmpty(), "Acceptance must discover real forms");
            return forms.stream().flatMap(path -> IntStream.rangeClosed(1, 20).mapToObj(n -> Arguments.of(path, n)));
        }
    }

    @ParameterizedTest(name = "{0}: submission {1}") @MethodSource("publishedSubmissions") @Timeout(180)
    void everyPopulatedQuestionInEveryPublishedSubmissionSatisfiesItsFinalConstraint(Path path, int number) throws Exception {
        LoadedForm form = path.getFileName().toString().equals("ctosim_coverage3.xml")
                ? FormLoader.load(path, Path.of("samples/coverage3")) : FormLoader.load(path);
        Submission result = new FormWalker(form, SimulationConfig.defaults()).walk(number);
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC")); // default configuration's session zone
            assertConstraints(parse(Files.readString(path)), result.instanceXml(), result.end());
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test void oracleRejectsAnInvalidSecondRepeatInstanceAndAnInvalidCrossFieldConstraint() throws Exception {
        String definition = resource("amendment5/template.xml")
                .replace("__INSTANCE__", "<limit>5</limit><row><answer/></row>")
                .replace("__BINDS__", bind("limit", "int", "") + bind("row/answer", "int", "constraint=\". &lt;= /data/limit\""))
                .replace("__ACTIONS__", "").replace("__BODY__", input("row/answer"));
        var parsed = parse(definition);
        String valid = "<data id=\"oracle\"><limit>5</limit><row><answer>3</answer></row><row><answer>5</answer></row></data>";
        assertEquals(2, assertConstraints(parsed, valid, LocalDateTime.of(2040, 1, 1, 12, 0)));
        AssertionError failure = assertThrows(AssertionError.class, () -> assertConstraints(parsed,
                valid.replace("<answer>5</answer>", "<answer>6</answer>"), LocalDateTime.of(2040, 1, 1, 12, 0)));
        assertTrue(failure.getMessage().contains("violates"));
        assertThrows(AssertionError.class, () -> assertConstraints(parsed, valid.replace("<limit>5", "<limit>2"),
                LocalDateTime.of(2040, 1, 1, 12, 0)));
    }

    @Test void oracleDoesNotRegenerateOrRecalculateTheSubmittedSnapshot() throws Exception {
        String definition = resource("amendment5/template.xml").replace("__INSTANCE__", "<answer/><limit/>")
                .replace("__BINDS__", bind("answer", "int", "constraint=\". &lt;= /data/limit\"")
                        + bind("limit", "int", "calculate=\"100\""))
                .replace("__ACTIONS__", "").replace("__BODY__", input("answer"));
        assertThrows(AssertionError.class, () -> assertConstraints(parse(definition),
                "<data id=\"oracle\"><answer>6</answer><limit>5</limit></data>", LocalDateTime.of(2040, 1, 1, 12, 0)));
    }

    @Test void oracleUsesTheEnclosingRepeatIndexThroughOrdinaryGroups() throws Exception {
        String definition = resource("amendment5/template.xml")
                .replace("__INSTANCE__", "<row jr:template=\"\"><group><age/></group></row>")
                .replace("__BINDS__", bind("row/group/age", "int",
                        "constraint=\"if(index() = 1, . &gt;= 18, . &gt;= 3 and . &lt;= 6)\""))
                .replace("__ACTIONS__", "").replace("__BODY__", "<repeat nodeset=\"/data/row\">"
                        + "<group ref=\"/data/row/group\">" + input("row/group/age") + "</group></repeat>");
        String valid = "<data id=\"oracle\"><row><group><age>18</age></group></row>"
                + "<row><group><age>6</age></group></row></data>";
        var parsed = parse(definition);
        var end = LocalDateTime.of(2040, 1, 1, 12, 0);
        assertEquals(2, assertConstraints(parsed, valid, end));
        AssertionError failure = assertThrows(AssertionError.class,
                () -> assertConstraints(parsed, valid.replace("<age>6</age>", "<age>18</age>"), end));
        assertTrue(failure.getMessage().contains("violates"));
    }

    @Test void oracleUsesTheInnermostRepeatIndexWithoutRecreatingCountedInstances() throws Exception {
        String definition = resource("amendment5/template.xml")
                .replace("__INSTANCE__", "<total/><outer jr:template=\"\"><answer/>"
                        + "<inner jr:template=\"\"><answer/></inner></outer>")
                .replace("__BINDS__", bind("total", "int",
                        "constraint=\". = count(/data/outer) and count(/data/outer/inner) = 3\"")
                        + bind("outer/answer", "int", "constraint=\". = index()\"")
                        + bind("outer/inner/answer", "int", "constraint=\". = index()\""))
                .replace("__ACTIONS__", "").replace("__BODY__", input("total") + "<repeat nodeset=\"/data/outer\" jr:count=\"9\">"
                        + input("outer/answer") + "<repeat nodeset=\"/data/outer/inner\" jr:count=\"9\">"
                        + input("outer/inner/answer") + "</repeat></repeat>");
        String valid = "<data id=\"oracle\"><total>3</total><outer><answer>1</answer><inner><answer>1</answer></inner>"
                + "<inner><answer>2</answer></inner></outer><outer><answer>2</answer>"
                + "<inner><answer>1</answer></inner></outer><outer><answer>3</answer></outer></data>";
        var parsed = parse(definition);
        var end = LocalDateTime.of(2040, 1, 1, 12, 0);
        assertEquals(7, assertConstraints(parsed, valid, end));
        AssertionError failure = assertThrows(AssertionError.class, () -> assertConstraints(parsed,
                valid.replace("<inner><answer>2</answer>", "<inner><answer>1</answer>"), end));
        assertTrue(failure.getMessage().contains("violates"));
    }
}
