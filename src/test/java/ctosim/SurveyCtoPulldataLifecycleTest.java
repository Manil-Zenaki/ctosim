package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoPulldataLifecycleTest extends SurveyCtoPulldataTestSupport {
    private static final String LOOKUP = "pulldata('people', 'name', 'id', 'a')";

    @Test
    void explicitMediaDirectoryControlsLookupAndPreservesNormalLoadingMetadata() throws Exception {
        attach("people", "people.csv");
        Files.writeString(area.resolve("people.csv"), resource("people.csv").replace("Alice", "Wrong sibling"));
        calculate(LOOKUP);
        var loaded = loadMedia();
        assertEquals("attached.xml", loaded.sourceName());
        assertEquals("attached-data", loaded.formId());
        assertEquals("3", loaded.version());
        assertEquals("Attached data", loaded.title());
        var session = loaded.newEntryController();
        assertEquals(org.javarosa.form.api.FormEntryController.EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals("[Alice]", value(child(root(session), "result")));
    }

    @Test
    void defaultMediaDirectoryIsTheXformsParent() throws Exception {
        Files.writeString(area.resolve("people.csv"), resource("people.csv"));
        calculate(LOOKUP);
        var loaded = FormLoader.load(xform);
        assertTrue(loaded.missingAttachments().isEmpty());
        assertEquals("[Alice]", value(child(root(loaded.newEntryController()), "result")));
    }

    @Test
    void relativeXformPathAlsoUsesItsContainingDirectory() throws Exception {
        Files.writeString(area.resolve("people.csv"), resource("people.csv"));
        Path relative = Path.of("").toAbsolutePath().relativize(xform);
        assertFalse(relative.isAbsolute());
        assertLookup(FormLoader.load(relative).newEntryController(), LOOKUP, "Alice");
    }

    @Test
    void streamLoadHasNoMediaEvenWhenSourceNameNamesARealXformWithSiblingCsv() throws Exception {
        Files.writeString(area.resolve("people.csv"), resource("people.csv"));
        calculate(LOOKUP);
        try (var in = Files.newInputStream(xform)) {
            var loaded = FormLoader.load(in, xform.toString());
            assertEquals(java.util.Set.of("people"), loaded.missingAttachments());
            assertEquals("[]", value(child(root(loaded.newEntryController()), "result")));
        }
    }

    @Test
    void absentExplicitAttachmentDoesNotFallBackToXformSibling() throws Exception {
        Files.writeString(area.resolve("people.csv"), resource("people.csv"));
        assertLookup(loadMedia().newEntryController(), LOOKUP, "");
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "regular-file"})
    void invalidMediaDirectoryThrowsFormLoadExceptionNamingIt(String kind) throws Exception {
        Path invalid = area.resolve("invalid-media-" + kind);
        if (kind.equals("regular-file")) Files.writeString(invalid, "not a directory");
        var failure = assertThrows(FormLoadException.class, () -> FormLoader.load(xform, invalid));
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains(invalid.getFileName().toString()));
    }

    @Test
    void nullMediaDirectoryThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> FormLoader.load(xform, (Path) null));
    }

    @Test
    void nullXformPathInTheNewOverloadThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> FormLoader.load((Path) null, media));
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "empty", "malformed", "not-xform", "missing-id"})
    void newOverloadPreservesFormLoadingErrors(String problem) throws Exception {
        switch (problem) {
            case "missing" -> Files.delete(xform);
            case "empty" -> Files.writeString(xform, "");
            case "malformed" -> Files.writeString(xform, "<broken");
            case "not-xform" -> Files.writeString(xform, "<data id=\"plain-xml\"/>");
            case "missing-id" -> Files.writeString(xform, Files.readString(xform).replace(" id=\"attached-data\"", ""));
            default -> throw new AssertionError(problem);
        }
        var failure = assertThrows(FormLoadException.class, this::loadMedia);
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains(xform.getFileName().toString()));
    }

    @Test
    void sourceAlwaysGetsTheCsvSuffixIncludingWhenItAlreadyEndsInCsv() throws Exception {
        attach("people", "people.csv");
        attach("people.csv", "people.csv");
        Files.writeString(media.resolve("people.csv.csv"), resource("people.csv").replace("Alice", "Double suffix"));
        var session = loadMedia().newEntryController();
        assertLookup(session, call("people.csv", "name", "id", "a"), "Double suffix");
        assertLookup(session, LOOKUP, "Alice");
    }

    @Test
    void extensionlessOrSimilarFileNamesAreNotSubstitutesForTheCsv() throws Exception {
        Files.writeString(media.resolve("people"), resource("people.csv"));
        Files.writeString(media.resolve("people.csv.txt"), resource("people.csv"));
        assertLookup(loadMedia().newEntryController(), LOOKUP, "");
    }

    @Test
    void unicodeAndSpacesInSourceNamesAreUsedLiterally() throws Exception {
        attach(" 東京 café ", "people.csv");
        assertLookup(loadMedia().newEntryController(), call(" 東京 café ", "name", "id", "a"), "Alice");
    }

    @Test
    void emptySourceLooksForTheFileNamedDotCsv() throws Exception {
        attach("", "people.csv");
        assertLookup(loadMedia().newEntryController(), call("", "name", "id", "a"), "Alice");
    }

    @Test
    void attachmentIsNotReadAtLoadTimeEvenWhenLiterallyReferenced() throws Exception {
        Path csv = attach("people", "people.csv");
        calculate(LOOKUP);
        var loaded = loadMedia();
        Files.writeString(csv, resource("people.csv").replace("Alice", "Updated before first call"));
        assertEquals("[Updated before first call]", value(child(root(loaded.newEntryController()), "result")));
    }

    @Test
    void malformedAttachmentIsNotParsedUntilACallActuallyNeedsIt() throws Exception {
        Path csv = Files.writeString(media.resolve("people.csv"), "id,\"unclosed");
        calculate(LOOKUP);
        var loaded = loadMedia();
        assertTrue(loaded.missingAttachments().isEmpty());
        Files.writeString(csv, resource("people.csv"));
        assertEquals("[Alice]", value(child(root(loaded.newEntryController()), "result")));
    }

    @ParameterizedTest @ValueSource(strings = {"ordinary", "simulated", "properties"})
    void firstReadIsCachedAcrossLookupsAndAllLaterSessions(String firstKind) throws Exception {
        Path csv = attach("people", "people.csv");
        var loaded = loadMedia();
        var first = session(loaded, firstKind);
        assertLookup(first, LOOKUP, "Alice");
        Files.writeString(csv, resource("people.csv").replace("Alice", "Changed").replace("Bob", "Also changed"));
        assertLookup(first, LOOKUP, "Alice");
        // An unqueried row/column still comes from the first read, not a per-key cache.
        assertLookup(first, call("people", "name", "id", "b"), "Bob");
        Files.delete(csv);
        for (String kind : new String[]{"ordinary", "simulated", "properties"}) {
            assertLookup(session(loaded, kind), LOOKUP, "Alice");
        }
    }

    @Test
    void eachSourceIsReadIndependentlyWhenFirstNeeded() throws Exception {
        attach("people", "people.csv");
        Path other = attach("other", "people.csv");
        var loaded = loadMedia();
        var session = loaded.newEntryController();
        assertLookup(session, LOOKUP, "Alice");
        Files.writeString(other, resource("people.csv").replace("Alice", "Later source"));
        assertLookup(session, call("other", "name", "id", "a"), "Later source");
    }

    @ParameterizedTest @ValueSource(strings = {"no-row", "no-column", "header-only"})
    void aSuccessfullyReadFileIsCachedEvenWhenTheFirstLookupHasNoResult(String kind) throws Exception {
        Path csv = attach("people", kind.equals("header-only") ? "header-only.csv" : "people.csv");
        var session = loadMedia().newEntryController();
        String first = kind.equals("no-column") ? call("people", "absent", "id", "a")
                : call("people", "name", "id", "not-found");
        assertLookup(session, first, "");
        Files.writeString(csv, resource("people.csv").replace("Alice", "Changed after read"));
        assertLookup(session, LOOKUP, kind.equals("header-only") ? "" : "Alice");
    }

    @Test
    void newLoadedFormReadsChangesEvenWhileOldLoadedFormRetainsItsCache() throws Exception {
        Path csv = attach("people", "people.csv");
        var old = loadMedia();
        assertLookup(old.newEntryController(), LOOKUP, "Alice");
        Files.writeString(csv, resource("people.csv").replace("Alice", "New load"));
        var fresh = loadMedia();
        assertLookup(fresh.newEntryController(), LOOKUP, "New load");
        assertLookup(old.newEntryController(), LOOKUP, "Alice");
    }

    @Test
    void sameSourceNameInDifferentMediaDirectoriesNeverSharesCachedData() throws Exception {
        attach("people", "people.csv");
        Path secondDirectory = Files.createDirectory(area.resolve("second-media"));
        Files.writeString(secondDirectory.resolve("people.csv"), resource("people.csv").replace("Alice", "Other form"));
        var first = loadMedia();
        var second = FormLoader.load(xform, secondDirectory);
        assertLookup(first.newEntryController(), LOOKUP, "Alice");
        assertLookup(second.newEntryController(), LOOKUP, "Other form");
        assertLookup(first.newEntryController(), LOOKUP, "Alice");
    }
}
