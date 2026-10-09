package ctosim;

import java.nio.file.Files;
import java.util.stream.Stream;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoPulldataCsvTest extends SurveyCtoPulldataTestSupport {
    static Stream<Arguments> encodings() {
        return Stream.of("\n", "\r\n").flatMap(eol -> Stream.of(false, true)
                .flatMap(bom -> Stream.of(false, true).map(terminated -> Arguments.of(eol, bom, terminated))));
    }

    @ParameterizedTest(name = "CSV encoding case {index}") @MethodSource("encodings")
    void readsUtf8WithEitherLineEndingOptionalBomAndOptionalFinalNewline(String eol, boolean bom, boolean terminated)
            throws Exception {
        String csv = resource("people.csv").replace("\r\n", "\n").stripTrailing();
        Files.writeString(media.resolve("people.csv"), (bom ? "\uFEFF" : "")
                + csv.replace("\n", eol) + (terminated ? eol : ""));
        var session = loadMedia().newEntryController();
        assertLookup(session, call("people", "name", "id", "unicode"), "Élodie 東京 😀");
        // Matching the first column also checks that a BOM did not become part of its name.
        assertLookup(session, call("people", "name", "id", "a"), "Alice");
        assertLookup(session, call("people", "other", "id", "empty"), "blank");
    }

    static Stream<Arguments> quotedFields() {
        return Stream.of(Arguments.of("a,b", "contains, comma"),
                Arguments.of("say \"yes\"", "He said \"hello\"."),
                Arguments.of("spaces", "  keep me  "), Arguments.of("empty", ""),
                Arguments.of("unicode", "café 東京 😀"));
    }

    @ParameterizedTest @MethodSource("quotedFields")
    void quotedHeadersKeysAndValuesDecodeCommasQuotesSpacesAndUnicode(String key, String expected) throws Exception {
        attach("quoted", "quoted.csv");
        assertLookup(loadMedia().newEntryController(), call("quoted", "value,column", "key,column", key), expected);
    }

    @ParameterizedTest @ValueSource(strings = {"\n", "\r\n"})
    void lineBreaksInsideQuotedKeysAndValuesAreLiteral(String eol) throws Exception {
        Files.writeString(media.resolve("quoted.csv"), resource("quoted.csv").replace("\r\n", "\n").replace("\n", eol));
        assertLookup(loadMedia().newEntryController(),
                call("quoted", "value,column", "key,column", "two" + eol + "lines"), "first" + eol + "second");
    }

    static Stream<Arguments> raggedRows() {
        return Stream.of(Arguments.of("name", "id", "short", ""),
                Arguments.of("last", "id", "short", ""),
                Arguments.of("last", "id", "middle", ""),
                Arguments.of("name", "id", "middle", "Middle"),
                Arguments.of("id", "last", "", "short"),
                Arguments.of("name", "id", "extra", "Expected"),
                Arguments.of("last", "id", "extra", "Tail"),
                Arguments.of("ignored", "id", "extra", ""));
    }

    @ParameterizedTest @MethodSource("raggedRows")
    void missingColumnsAreEmptyAndExtraFieldsDoNotChangeHeaderColumns(String column, String keyColumn, String key,
            String expected) throws Exception {
        attach("ragged", "ragged.csv");
        assertLookup(loadMedia().newEntryController(), call("ragged", column, keyColumn, key), expected);
    }

    @Test
    void headerOnlyFileHasNoMatchingDataRowsEvenWhenTheKeyEqualsAHeader() throws Exception {
        attach("headers", "header-only.csv");
        var session = loadMedia().newEntryController();
        assertLookup(session, call("headers", "name", "id", "id"), "");
        assertLookup(session, call("headers", "name", "id", ""), "");
    }

    @Test
    void aSingleColumnAndSingleDataRowCanServeAsBothKeyAndResult() throws Exception {
        attach("single", "one-column.csv");
        assertLookup(loadMedia().newEntryController(), call("single", "id", "id", "singleton"), "singleton");
    }

    @ParameterizedTest @ValueSource(strings = {"\n", "\r\n"})
    void emptyLineAtEndDoesNotBecomeTheHeaderOrAnAdditionalValue(String eol) throws Exception {
        String csv = resource("one-column.csv").replace("\r\n", "\n").stripTrailing().replace("\n", eol);
        Files.writeString(media.resolve("single.csv"), csv + eol + eol);
        var session = loadMedia().newEntryController();
        assertLookup(session, call("single", "id", "id", "singleton"), "singleton");
        assertLookup(session, call("single", "id", "id", ""), "");
    }

    @ParameterizedTest @ValueSource(strings = {"header", "value", "after-match", "escaped-quote"})
    void unclosedQuotingFailsDuringTheLookupAndNamesTheFile(String kind) throws Exception {
        String csv = switch (kind) {
            case "header" -> "id,\"name\n";
            case "value" -> "id,name\na,\"never closed";
            case "after-match" -> "id,name\na,Alice\nb,\"never closed";
            default -> "id,name\na,\"escaped final quote\"\"";
        };
        Files.writeString(media.resolve("broken.csv"), csv);
        // Loading and initializing a form that does not call this source must not read it.
        var form = loadMedia().newEntryController().getModel().getForm();
        var expression = XPathParseTool.parseXPath(call("broken", "name", "id", "a"));
        var failure = assertThrows(Exception.class,
                () -> expression.eval(form.getMainInstance(), form.getEvaluationContext()));
        assertNamesFile(failure, "broken.csv");
    }

    @Test
    void existingAttachmentThatCannotBeReadAsAFileFailsAndNamesIt() throws Exception {
        // Portable on Windows and Unix, without changing permissions or relying on the current user's privileges.
        Files.createDirectory(media.resolve("unreadable.csv"));
        var form = loadMedia().newEntryController().getModel().getForm();
        var expression = XPathParseTool.parseXPath(call("unreadable", "name", "id", "a"));
        var failure = assertThrows(Exception.class,
                () -> expression.eval(form.getMainInstance(), form.getEvaluationContext()));
        assertNamesFile(failure, "unreadable.csv");
    }
}
