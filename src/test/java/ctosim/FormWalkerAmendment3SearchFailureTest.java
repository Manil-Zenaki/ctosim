package ctosim;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment3SearchFailureTest extends FormWalkerAmendment3SearchSupport {
    @ParameterizedTest @ValueSource(strings = {
            "search()", "search('search', 'matches')", "search('search', 'matches', 'name')",
            "search('search', 'matches', 'name', 'Alpha', 'region')",
            "search('search', 'matches', 'name', 'Alpha', 'region', '1', 'extra')",
            "search('search', 'regex', 'name', 'Alpha')",
            "search('search', 'MATCHES', 'name', 'Alpha')",
            "search('search', '', 'name', 'Alpha')",
            "search('search', 'matches', 'missing', 'Alpha')",
            "search('search', 'matches', 'name,missing', 'Alpha')",
            "search('search', 'matches', 'Name', 'Alpha')",
            "search('search', 'matches', 'name', 'Alpha', 'missing', '1')",
            "search('search', 'matches', 'name', 'Alpha', 'Region', '1')"
    })
    void invalidCallOrMissingColumnWarnsAndKeepsOnlyStaticChoices(String appearance) throws Exception {
        LoadedForm loaded = search("select1", appearance, "7", "code", "8");
        FormWalker walker = new FormWalker(loaded, searchConfig(""));
        var seen = new java.util.HashSet<String>();
        for (int n = 1; n <= 24; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            assertTrue(answer.equals("7") || answer.equals("8"), answer);
            seen.add(answer);
            assertEquals(1, result.answered());
            assertWarning(result, "answer");
            assertTrue(result.warnings().stream().allMatch(w -> w.startsWith("submission " + result.number() + ":")));
        }
        assertEquals(java.util.Set.of("7", "8"), seen);
    }

    @Test void missingPlaceholderColumnWarnsAndPreservesStaticChoices() throws Exception {
        Submission result = fixedChoice(search("select1", "search('search')", "7", "unknown_column", "8"), "8");
        assertEquals("8", value(result, "answer"));
        assertWarning(result, "answer");
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "absent.csv"})
    void missingAttachmentWarningNamesQuestionAndFile(String source) throws Exception {
        Submission result = fixedChoice(search("select1", "search('" + source + "')", "7", "code"), "7");
        assertEquals("7", value(result, "answer"));
        assertWarning(result, "answer", "absent.csv");
        assertEquals(1, result.answered());
    }

    @Test void streamLoadedFormHasNoMediaDirectoryAndWarnsForSearchFile() throws Exception {
        search("select1", "search('search')", "7", "code");
        LoadedForm loaded = loadXml(Files.readString(xform));
        Submission result = fixedChoice(loaded, "7");
        assertEquals("7", value(result, "answer"));
        assertWarning(result, "answer", "search.csv");
    }

    @Test void pathOnlyLoadUsesTheFormsDirectoryForAttachments() throws Exception {
        search("select1", "search('search', 'matches', 'name', 'Alpha')", "code");
        Files.copy(media.resolve("search.csv"), area.resolve("search.csv"));
        assertAccepted(FormLoader.load(xform), "V03");
    }

    @Test void missingFileWithoutStaticChoicesLeavesBlankAndContinues() throws Exception {
        LoadedForm loaded = attached("<answer/><after/>", bind("answer", "string", "") + bind("after", "string", ""),
                selectControl("select1", "answer", "search('absent')", items("code")) + input("after"), "");
        Submission result = new FormWalker(loaded, searchConfig("\"after\":{\"value\":\"continued\"}")).walk(31);
        assertEquals("", value(result, "answer"));
        assertEquals("continued", value(result, "after"));
        assertEquals(1, result.answered());
        assertElapsed(result, 2);
        assertWarning(result, "answer", "absent.csv");
    }

    @Test void failedSearchCannotConvertItsPlaceholderAsAnAnswer() throws Exception {
        Submission result = fixedChoice(search("select1", "search('absent')", "7", "code"), "code");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "absent.csv");
        assertWarning(result, "answer", "code");
    }

    @ParameterizedTest @ValueSource(strings = {"select1", "select"})
    void requiredSearchWithUnconvertibleOverrideAlsoWarnsThatItIsRequired(String control) throws Exception {
        LoadedForm loaded = attached("<answer/>", bind("answer", "string", "required=\"true()\""),
                selectControl(control, "answer", "search('search')", items("code")), "");
        Submission result = fixedChoice(loaded, "UNKNOWN");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "UNKNOWN");
        assertWarning(result, "answer", "required");
    }

    @Test void loadedAnswerStillHasToSatisfyTheQuestionsConstraint() throws Exception {
        LoadedForm loaded = attached("<answer/>", bind("answer", "string", "constraint=\"false()\""),
                selectControl("select1", "answer", "search('search')", items("code")), "");
        Submission result = fixedChoice(loaded, "V03");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
        assertElapsed(result, 1);
    }

    @Test void irrelevantSearchQuestionDoesNotLoadMissingFileOrWarn() throws Exception {
        LoadedForm loaded = attached("<answer/>", bind("answer", "string", "relevant=\"false()\""),
                selectControl("select1", "answer", "search('absent')", items("code")), "");
        Submission result = new FormWalker(loaded, searchConfig("")).walk(1);
        assertEquals(0, count(result, "answer"));
        assertEquals(0, result.answered());
        assertEquals(result.start(), result.end());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void searchWarningsFollowQuestionOrderAndRemainUnmodifiable() throws Exception {
        LoadedForm loaded = attached("<z_first/><a_second/>", bind("z_first", "string", "") + bind("a_second", "string", ""),
                selectControl("select1", "z_first", "search('absent1')", items("7", "code"))
                + selectControl("select1", "a_second", "search('absent2')", items("7", "code")), "");
        Submission result = new FormWalker(loaded, searchConfig("")).walk(42);
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("z_first"));
        assertTrue(result.warnings().get(1).contains("a_second"));
        assertTrue(result.warnings().stream().allMatch(w -> w.startsWith("submission 42:")));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("changed"));
    }
}
