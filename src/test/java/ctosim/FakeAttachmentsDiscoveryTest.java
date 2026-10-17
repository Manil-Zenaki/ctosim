package ctosim;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsDiscoveryTest extends FakeAttachmentsTestSupport {
    @ParameterizedTest @ValueSource(strings = {"calculate", "relevant", "constraint", "required", "readonly"})
    void everySpecifiedBindAttributeContributesColumns(String attribute) throws Exception {
        var form = form("<key/><target/>", bind("key", "string", "")
                + bind("target", "string", attribute + "=\"" + attr(pull("people", "age", "id", "/data/key") + " = '1'") + "\""),
                input("key") + input("target"));
        assertEquals(List.of("age", "id"), table(FakeAttachments.of(form, SEED), "people").header());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void setvalueActionsInModelAndBodyContributeColumns(boolean inBody) throws Exception {
        String action = "<setvalue event=\"" + (inBody ? "xforms-value-changed" : "odk-instance-first-load")
                + "\" ref=\"/data/target\" value=\"" + attr(pull("people", "age", "id", "/data/key")) + "\"/>";
        String body = inBody ? input("key").replace("</input>", action + "</input>") : input("key");
        var form = load(xml("<key/><target/>", bind("key", "string", "") + bind("target", "string", ""),
                body, "", inBody ? "" : action));
        assertEquals(List.of("age", "id"), table(FakeAttachments.of(form, SEED), "people").header());
    }

    @Test void nestedCallsAndCallsInUnchosenBranchesAreDiscovered() throws Exception {
        String expression = "if(false(), " + pull("outer", "age", "id", pull("inner", "status", "code", "/data/key")) + ", '')";
        var fake = FakeAttachments.of(form("<key/><v/>", bind("key", "string", "") + calc("v", expression), input("key")), SEED);
        assertEquals(Set.of("outer", "inner"), fake.files().keySet());
        assertEquals(Set.of("age", "id"), Set.copyOf(table(fake, "outer").header()));
        assertEquals(Set.of("status", "code"), Set.copyOf(table(fake, "inner").header()));
    }

    @ParameterizedTest @ValueSource(strings = {"'people'", "\"people\"", "  'people'  "})
    void bothLiteralQuoteStylesAndWhitespaceAroundCallsAreAccepted(String source) throws Exception {
        var form = form("<key/><v/>", bind("key", "string", "")
                + calc("v", "pulldata ( " + source + ", \"age\", 'id', /data/key )"), input("key"));
        assertEquals(List.of("age", "id"), table(FakeAttachments.of(form, SEED), "people").header());
    }

    @ParameterizedTest @ValueSource(strings = {"/data/key", "concat('peo','ple')", "string('people')", "123"})
    void computedPulldataFileNamesAddNothing(String expression) throws Exception {
        var fake = FakeAttachments.of(form("<key/><v/>", bind("key", "string", "")
                + calc("v", "pulldata(" + expression + ",'age','id',/data/key)"), input("key")), SEED);
        assertTrue(fake.files().isEmpty());
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @Test void nonliteralValueColumnDoesNotDiscardTheLiteralKeyColumn() throws Exception {
        var form = form("<key/><v/>", bind("key", "string", "")
                + calc("v", "pulldata('people',concat('a','ge'),'id',/data/key)"), input("key"));
        assertEquals(List.of("id"), table(FakeAttachments.of(form, SEED), "people").header());
    }

    @Test void nonliteralKeyColumnDoesNotDiscardTheLiteralValueColumn() throws Exception {
        var form = form("<key/><v/>", bind("key", "string", "")
                + calc("v", "pulldata('people','age',concat('i','d'),/data/key)"), input("key"));
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(List.of("age"), table(fake, "people").header());
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @Test void columnsFollowBindDocumentOrderAndAreDeduplicated() throws Exception {
        var form = form("<key/><a/><b/><c/>", bind("key", "string", "")
                + calc("c", pull("people", "zeta", "key2", "/data/key"))
                + calc("a", pull("people", "alpha", "key1", "/data/key"))
                + calc("b", pull("people", "zeta", "key1", "/data/key")), input("key"));
        assertEquals(List.of("zeta", "key2", "alpha", "key1"), table(FakeAttachments.of(form, SEED), "people").header());
    }

    @Test void callsWrittenInsideStringsLabelsCommentsOrInstanceDataAreNotRead() throws Exception {
        String decoy = "pulldata('decoy','age','id','x')";
        String xml = xml("<key>" + decoy + "</key><v/>", calc("v", "\"" + decoy + "\""),
                input("key").replace("<label>key</label>", "<label>" + decoy + "</label>"), "<!-- " + decoy + " -->", "");
        var fake = FakeAttachments.of(load(xml), SEED);
        assertTrue(fake.files().isEmpty());
        assertTrue(fake.keyAnswers().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"pulldata()", "pulldata('good')", "pulldata('good','bad_value','bad_key')", "pulldata('good','bad_value','bad_key','x','extra')"})
    void unreadablePulldataCallsDoNotThrowOrLoseOtherReadableCalls(String bad) throws Exception {
        var form = form("<key/><a/><b/>", bind("key", "string", "") + calc("a", bad)
                + calc("b", pull("good", "age", "id", "/data/key")), input("key"));
        var fake = assertDoesNotThrow(() -> FakeAttachments.of(form, SEED));
        assertEquals(List.of("age", "id"), table(fake, "good").header());
    }

    @ParameterizedTest @ValueSource(strings = {"search(", "search()", "search('good','matches')", "search('good','unknown','x','y')"})
    void unreadableSearchCallsDoNotThrowOrLoseOtherReadableCalls(String appearance) throws Exception {
        var form = form("<key/><q/><v/>", bind("key", "string", "") + bind("q", "string", "")
                + calc("v", pull("good", "age", "id", "/data/key")), input("key")
                + select("q", appearance, "code", "LABEL"));
        var fake = assertDoesNotThrow(() -> FakeAttachments.of(form, SEED));
        assertEquals(List.of("age", "id"), table(fake, "good").header());
    }

    @ParameterizedTest @ValueSource(strings = {"école", "survey data", "a,b(c)", "Zebra"})
    void literalFileNamesArePreservedExactlyWithoutAddingCsvSuffix(String file) throws Exception {
        var form = form("<key/><v/>", bind("key", "string", "") + calc("v", pull(file, "age", "id", "/data/key")), input("key"));
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(Set.of(file), fake.files().keySet());
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "with space", "café東京", "with,comma", "with\"quote", "with\nnewline", "with\rcarriage"})
    void csvPreservesAndMinimallyQuotesUnusualColumnNames(String column) throws Exception {
        var table = table(FakeAttachments.of(pulls("string", column), SEED), "people");
        assertEquals(List.of(column, "id"), table.header());
        assertEquals(50, table.rows().size());
    }
}
