package ctosim;

import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsAmendment1ContractTest extends FakeAttachmentsAmendment1Support {
    @ParameterizedTest @ValueSource(ints = {0, 1, 501, Integer.MAX_VALUE})
    void nullFormIsRejectedByTheNewOverload(int submissions) {
        assertThrows(NullPointerException.class, () -> FakeAttachments.of(null, SEED, "q0", submissions));
    }

    @Test void nullFormIsAlsoRejectedWithDefaultArguments() {
        assertThrows(NullPointerException.class, () -> FakeAttachments.of(null, SEED, null, 0));
    }

    static Stream<Arguments> negativeCounts() {
        return Stream.of(-1, -50, Integer.MIN_VALUE)
                .flatMap(n -> Stream.of(Arguments.of("q0", n), Arguments.of(null, n), Arguments.of("missing", n)));
    }

    @ParameterizedTest @MethodSource("negativeCounts")
    void negativeSubmissionsAreRejectedEvenWhenNoExpansionCouldApply(String id, int submissions) throws Exception {
        var form = levels(1);
        assertThrows(IllegalArgumentException.class, () -> FakeAttachments.of(form, SEED, id, submissions));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, SEED})
    void oldOverloadEqualsNewOverloadWithNullIdAndZeroSubmissions(long seed) throws Exception {
        var form = cascade();
        var old = FakeAttachments.of(form, seed);
        var explicit = FakeAttachments.of(form, seed, null, 0);
        assertEquals(old.files(), explicit.files());
        assertEquals(old.keyAnswers(), explicit.keyAnswers());
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"", "unknown", "Q0", "code0"})
    void absentOrUnknownQuestionNameLeavesFilesAndKeysUnchanged(String id) throws Exception {
        assertUnchanged(levels(3), id, 1000);
    }

    @Test void maximumSubmissionCountWithNoIdDoesNotExpandOrAllocateRows() throws Exception {
        assertUnchanged(levels(1), null, Integer.MAX_VALUE);
    }

    @ParameterizedTest @CsvSource({
            "1,q0,0", "1,q0,1", "1,q0,2", "1,q0,3",
            "3,q0,0", "3,q0,1", "3,q0,2", "3,q0,3",
            "3,q1,0", "3,q1,1", "3,q1,8", "3,q1,9",
            "3,q2,0", "3,q2,1", "3,q2,26", "3,q2,27",
            "6,q5,485", "6,q5,486", "8,q0,2", "8,q0,3", "8,q7,486"
    })
    void countsAtOrBelowOrdinaryIdCodeCountPreserveBytes(
            int depth, String id, int submissions) throws Exception {
        assertUnchanged(levels(depth), id, submissions);
    }

    @ParameterizedTest @ValueSource(strings = {"textkey", "intkey", "phone"})
    void textIntegerAndCalculateFieldsDoNotTriggerExpansion(String id) throws Exception {
        assertUnchanged(cascade(), id, 1000);
    }

    @ParameterizedTest @ValueSource(strings = {"string", "int"})
    void pulldataOnlyFixtureIsNotExpandedEvenWhenItsKeyQuestionIsTheId(String type) throws Exception {
        assertUnchanged(pulls(type, "phone"), "key", 1000);
    }

    @Test void staticSelectDoesNotTriggerExpansionOfOtherSearchFiles() throws Exception {
        var form = form("<fixed/><dynamic/>", bind("fixed", "string", "") + bind("dynamic", "string", ""),
                select("fixed", "", "1", "Static") + select("dynamic", "search('places')", "code", "LABEL"));
        assertUnchanged(form, "fixed", 1000);
    }

    @Test void selectMultipleWithSearchDoesNotQualifyAsTheIdQuestion() throws Exception {
        String body = select("many", "search('places')", "code", "LABEL")
                .replace("<select1 ", "<select ").replace("</select1>", "</select>");
        assertUnchanged(form("<many/>", bind("many", "string", ""), body), "many", 1000);
    }

    @Test void staticOnlySearchPlaceholderDoesNotCreateAnIdLevel() throws Exception {
        var form = form("<q/><key/><v/>", bind("q", "string", "") + bind("key", "string", "")
                + calc("v", pull("places", "age", "id", "/data/key")), input("key")
                + select("q", "search('places')", "-99", "STATIC"));
        assertUnchanged(form, "q", 1000);
        assertEquals(50, table(FakeAttachments.of(form, SEED, "q", 1000), "places").rows().size());
    }

    @ParameterizedTest @ValueSource(strings = {
            "search(concat('pla','ces'))", "search('places','matches')",
            "search('places','unknown','id','x')"
    })
    void unreadableSearchDoesNotTriggerExpansion(String appearance) throws Exception {
        var form = form("<q/><key/><v/>", bind("q", "string", "") + bind("key", "string", "")
                + calc("v", pull("places", "age", "id", "/data/key")), input("key")
                + select("q", appearance, "code", "LABEL"));
        assertUnchanged(form, "q", 1000);
    }

    @Test void formWithoutAnyAttachmentReadsStillReturnsEmptyMaps() throws Exception {
        var form = form("<id/>", bind("id", "string", ""), input("id"));
        var fake = FakeAttachments.of(form, SEED, "id", 1000);
        assertTrue(fake.files().isEmpty());
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @Test void realSearchFileIsNotExpandedOrReplacedAndOtherFilesStayUnchanged() throws Exception {
        var directory = scratch();
        String real = "code,LABEL\nREAL,Real household\n";
        Files.writeString(directory.resolve("present.csv"), real);
        Files.writeString(directory.resolve("form.xml"), xml("<q/><other/>", bind("q", "string", "")
                + bind("other", "string", ""), select("q", "search('present')", "code", "LABEL")
                + select("other", "search('missing')", "other_code", "OTHER"), "", ""));
        var form = FormLoader.load(directory.resolve("form.xml"), directory);
        assertUnchanged(form, "q", 1000);
        var fake = FakeAttachments.of(form, SEED, "q", 1000);
        assertEquals(Set.of("missing"), fake.files().keySet());
        assertEquals(real, Files.readString(directory.resolve("present.csv")));
        assertFalse(Files.exists(directory.resolve("missing.csv")));
    }

    @Test void missingSearchOnlyFileExpandsWithoutBeingListedByPulldataDiscovery() throws Exception {
        var form = levels(1);
        assertEquals(12, table(FakeAttachments.of(form, SEED, "q0", 12), "places").rows().size());
    }
}
