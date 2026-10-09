package ctosim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldCatalogTest {
    @TempDir Path temp;

    @Test void fileValuesUnicodeFullTypesAndArrayOrderArePreserved() throws Exception {
        FieldCatalog c = catalogJson(temp, """
                {"form_id":"form_é","title":"  家庭 enquête  ","version":"0008",
                 "ignored":{"nested":[true,3]},"fields":[
                  {"name":"z","type":"select_multiple assets","xpath":"/data/g/z","row":19,"extra":42},
                  {"name":"é","type":"text audit","xpath":"/data/é","row":2},
                  {"name":"a","type":"AuDiO audit","xpath":"/data/a","row":8}]}
                """);
        assertAll(() -> assertEquals("form_é", c.formId()),
                () -> assertEquals("  家庭 enquête  ", c.title()),
                () -> assertEquals("0008", c.version()),
                () -> assertEquals(List.of(new FieldInfo("z", "select_multiple assets", "/data/g/z", 19),
                        new FieldInfo("é", "text audit", "/data/é", 2),
                        new FieldInfo("a", "AuDiO audit", "/data/a", 8)), c.fields()));
    }

    @ParameterizedTest @ValueSource(strings = {"", ",\"title\":null", ",\"title\":\"\""})
    void absentNullAndEmptyTitleFallBackToFormId(String title) throws Exception {
        assertEquals("fallback", catalogJson(temp, "{\"form_id\":\"fallback\",\"fields\":[]" + title + "}").title());
    }

    @ParameterizedTest @ValueSource(strings = {"", ",\"version\":null"})
    void missingAndNullVersionAreNull(String version) throws Exception {
        assertNull(catalogJson(temp, "{\"form_id\":\"f\",\"fields\":[]" + version + "}").version());
    }

    @Test void presentEmptyVersionIsPreserved() throws Exception {
        assertEquals("", catalog(temp, "T", "").version());
    }

    @Test void emptyFieldsArrayIsAcceptedAndCannotBePopulated() throws Exception {
        var fields = catalog(temp, "T", null).fields();
        assertTrue(fields.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> fields.add(field("v", "text", "v", 2)));
    }

    @Test void fieldsCannotBeAddedReplacedRemovedOrChangedThroughIterator() throws Exception {
        var f = field("v", "text", "v", 2);
        var fields = catalog(temp, "T", null, f).fields();
        assertAll(() -> assertThrows(UnsupportedOperationException.class, () -> fields.add(f)),
                () -> assertThrows(UnsupportedOperationException.class, () -> fields.set(0, f)),
                () -> assertThrows(UnsupportedOperationException.class, () -> fields.remove(0)),
                () -> assertThrows(UnsupportedOperationException.class, () -> { var it = fields.iterator(); it.next(); it.remove(); }));
        assertEquals(List.of(f), fields);
    }

    static Stream<Arguments> invalidCatalogs() {
        return Stream.of(
                Arguments.of("empty file", ""), Arguments.of("malformed", "{bad"),
                Arguments.of("not object", "[]"), Arguments.of("null root", "null"),
                Arguments.of("missing id", "{\"fields\":[]}"),
                Arguments.of("missing fields", "{\"form_id\":\"f\"}"),
                Arguments.of("null fields", "{\"form_id\":\"f\",\"fields\":null}"),
                Arguments.of("object fields", "{\"form_id\":\"f\",\"fields\":{}}"),
                Arguments.of("string fields", "{\"form_id\":\"f\",\"fields\":\"[]\"}"));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidCatalogs")
    void invalidCatalogThrowsIOExceptionNamingFile(String reason, String json) throws Exception {
        Path file = temp.resolve("invalid-catalog.json");
        Files.writeString(file, json);
        IOException e = assertThrows(IOException.class, () -> FieldCatalog.load(file));
        assertTrue(e.getMessage().contains(file.getFileName().toString()));
    }

    @ParameterizedTest @ValueSource(strings = {"name", "type", "xpath", "row"})
    void everyFieldRequiresEverySpecifiedKeyIncludingLaterFields(String missing) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var valid = mapper.readTree("{\"name\":\"v\",\"type\":\"text\",\"xpath\":\"/data/v\",\"row\":2}");
        var incomplete = valid.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete).remove(missing);
        Path file = temp.resolve("missing-" + missing + ".json");
        Files.writeString(file, "{\"form_id\":\"f\",\"fields\":[" + valid + "," + incomplete + "]}");
        IOException e = assertThrows(IOException.class, () -> FieldCatalog.load(file));
        assertTrue(e.getMessage().contains(file.getFileName().toString()));
    }

    @Test void nonexistentFileThrowsIOExceptionNamingFile() {
        Path missing = temp.resolve("does-not-exist.json");
        var e = assertThrows(IOException.class, () -> FieldCatalog.load(missing));
        assertTrue(e.getMessage().contains("does-not-exist.json"));
    }

    @Test void directoryInsteadOfReadableFileThrowsIOExceptionNamingDirectory() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("not-a-json-file"));
        var e = assertThrows(IOException.class, () -> FieldCatalog.load(directory));
        assertTrue(e.getMessage().contains("not-a-json-file"));
    }
}
