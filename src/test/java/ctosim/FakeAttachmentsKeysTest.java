package ctosim;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsKeysTest extends FakeAttachmentsTestSupport {
    @Test void fileWithoutSearchLevelsHasFiftyRowsWithOneBasedIntegerKeys() throws Exception {
        var fake = FakeAttachments.of(pulls("int", "age"), SEED);
        Table table = table(fake, "people");
        List<String> expected = IntStream.rangeClosed(1, 50).mapToObj(Integer::toString).toList();
        assertEquals(50, table.rows().size());
        assertEquals(expected, table.column("id"));
        assertEquals(expected, fake.keyAnswers().get("key"), "Numeric row order, not lexical order 1,10,...");
    }

    @Test void textKeysUseKAndFourDigitZeroPaddedRowNumbers() throws Exception {
        var fake = FakeAttachments.of(pulls("string", "age"), SEED);
        Table table = table(fake, "people");
        List<String> expected = IntStream.rangeClosed(1, 50).mapToObj(i -> String.format(java.util.Locale.ROOT, "K%04d", i)).toList();
        assertEquals(expected, table.column("id"));
        assertEquals(expected, fake.keyAnswers().get("key"));
    }

    @ParameterizedTest @ValueSource(strings = {"decimal", "date", "time", "dateTime", "geopoint"})
    void otherInputTypesHaveTextualRowKeysButNoSuggestedKeyAnswers(String type) throws Exception {
        var fake = FakeAttachments.of(pulls(type, "age"), SEED);
        List<String> keys = table(fake, "people").column("id");
        assertEquals("K0001", keys.get(0));
        assertEquals("K0050", keys.get(49));
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"'literal'", "concat(/data/key,'x')", "string(/data/key)", "/data/key + 1", "7"})
    void compoundAndLiteralKeyExpressionsDoNotSuggestQuestionAnswers(String expression) throws Exception {
        var form = form("<key/><v/>", bind("key", "string", "") + calc("v", pull("people", "age", "id", expression)), input("key"));
        var fake = FakeAttachments.of(form, SEED);
        assertTrue(fake.keyAnswers().isEmpty());
        assertEquals("K0001", table(fake, "people").column("id").get(0));
    }

    @Test void calculatedKeyFieldIsNotAQuestionAndHasNoKeyAnswers() throws Exception {
        var form = form("<key/><v/><visible/>", calc("key", "'fixed'")
                + calc("v", pull("people", "age", "id", "/data/key")) + bind("visible", "string", ""), input("visible"));
        var fake = FakeAttachments.of(form, SEED);
        assertTrue(fake.keyAnswers().isEmpty());
        assertEquals(50, table(fake, "people").rows().size());
    }

    @Test void multipleReadsOfOneQuestionDoNotDuplicateItsKeyList() throws Exception {
        var fake = FakeAttachments.of(pulls("string", "age", "phone", "area"), SEED);
        assertEquals(Set.of("key"), fake.keyAnswers().keySet());
        assertEquals(table(fake, "people").column("id"), fake.keyAnswers().get("key"));
        assertEquals(50, fake.keyAnswers().get("key").size());
    }

    @Test void sharedQuestionUsesFirstFileInNaturalOrderRatherThanDiscoveryOrder() throws Exception {
        var form = form("<key/><z/><a/><q/>", bind("key", "string", "") + bind("q", "string", "")
                + calc("z", pull("zeta", "age", "id", "/data/key"))
                + calc("a", pull("Alpha", "age", "id", "/data/key")), input("key")
                + select("q", "search('Alpha')", "code", "LABEL"));
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(50, table(fake, "zeta").rows().size());
        assertEquals(3, table(fake, "Alpha").rows().size());
        assertEquals(table(fake, "Alpha").column("id"), fake.keyAnswers().get("key"));
    }

    @Test void distinctQuestionsKeepTheirOwnKeyColumnsAndTypes() throws Exception {
        var form = form("<textkey/><intkey/><a/><b/>", bind("textkey", "string", "") + bind("intkey", "int", "")
                + calc("a", pull("people", "age", "text_id", "/data/textkey"))
                + calc("b", pull("people", "phone", "int_id", "/data/intkey")), input("textkey") + input("intkey"));
        var fake = FakeAttachments.of(form, SEED);
        Table table = table(fake, "people");
        assertEquals(Set.of("textkey", "intkey"), fake.keyAnswers().keySet());
        assertEquals(table.column("text_id"), fake.keyAnswers().get("textkey"));
        assertEquals(table.column("int_id"), fake.keyAnswers().get("intkey"));
        assertEquals("K0050", table.column("text_id").get(49));
        assertEquals("50", table.column("int_id").get(49));
    }

    @Test void simpleRelativeReferencesResolveToTheQuestionName() throws Exception {
        var form = form("<group><key/><v/></group>", bind("group/key", "int", "")
                + calc("group/v", pull("people", "age", "id", "../key")), "<group ref=\"/data/group\"><label>Group</label>"
                + input("group/key") + "</group>");
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(Set.of("key"), fake.keyAnswers().keySet());
        assertEquals(table(fake, "people").column("id"), fake.keyAnswers().get("key"));
    }

    @Test void keyReferenceToSelectFromSameFileCopiesItsLevelCodeOnEveryRow() throws Exception {
        var form = form("<parent/><child/><v/>", bind("parent", "string", "") + bind("child", "string", "")
                + calc("v", pull("places", "age", "lookup_id", "/data/parent")),
                select("parent", "search('places')", "parent_code", "PARENT_LABEL")
                        + select("child", "search('places')", "child_code", "CHILD_LABEL"));
        var fake = FakeAttachments.of(form, SEED);
        Table table = table(fake, "places");
        assertEquals(table.column("parent_code"), table.column("lookup_id"));
        assertEquals(3, Set.copyOf(table.column("lookup_id")).size());
        assertTrue(fake.keyAnswers().isEmpty(), "A select is not answered as TEXT or INTEGER");
    }

    @Test void referenceToSelectLoadedFromAnotherFileUsesUniqueRowKeys() throws Exception {
        var form = form("<q/><v/>", bind("q", "string", "")
                + calc("v", pull("people", "age", "id", "/data/q")), select("q", "search('places')", "code", "LABEL"));
        var fake = FakeAttachments.of(form, SEED);
        List<String> keys = table(fake, "people").column("id");
        assertEquals(50, Set.copyOf(keys).size());
        assertEquals("K0001", keys.get(0));
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @Test void keyAnswersRetainRepeatedLevelKeysInCsvRowOrder() throws Exception {
        var form = form("<key/><q/><child/><v/>", bind("key", "string", "") + bind("q", "string", "")
                + bind("child", "string", "") + calc("v", pull("places", "age", "code", "/data/key")),
                input("key") + select("q", "search('places')", "code", "LABEL")
                        + select("child", "search('places')", "child_code", "CHILD_LABEL"));
        var fake = FakeAttachments.of(form, SEED);
        List<String> keys = fake.keyAnswers().get("key");
        assertEquals(table(fake, "places").column("code"), keys);
        assertEquals(9, keys.size());
        assertEquals(3, Set.copyOf(keys).size(), "Repeated keys must not be deduplicated");
    }
}
