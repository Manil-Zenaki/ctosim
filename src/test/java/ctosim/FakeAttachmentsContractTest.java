package ctosim;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsContractTest extends FakeAttachmentsTestSupport {
    @Test void nullFormIsRejected() {
        assertThrows(NullPointerException.class, () -> FakeAttachments.of(null, SEED));
    }

    @Test void ordinaryFormHasNoFilesOrKeyAnswers() throws Exception {
        var fake = FakeAttachments.of(form("<key/>", bind("key", "string", ""), input("key")), SEED);
        assertEquals(Map.of(), fake.files());
        assertEquals(Map.of(), fake.keyAnswers());
        assertThrows(UnsupportedOperationException.class, () -> fake.files().put("x", "x\n"));
        assertThrows(UnsupportedOperationException.class, () -> fake.keyAnswers().put("x", List.of("x")));
    }

    @Test void mapsIterateInNaturalStringOrderIncludingCaseAndUnicode() throws Exception {
        StringBuilder instance = new StringBuilder();
        StringBuilder binds = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String name : List.of("zebra", "école", "alpha", "Zebra")) {
            instance.append('<').append(name).append("/><v_").append(name).append("/>");
            binds.append(bind(name, "string", ""));
            binds.append(calc("v_" + name, pull(name, "age", "id", "/data/" + name)));
            body.append(input(name));
        }
        var fake = FakeAttachments.of(form(instance.toString(), binds.toString(), body.toString()), SEED);
        assertEquals(List.of("Zebra", "alpha", "zebra", "école"), new ArrayList<>(fake.files().keySet()));
        assertEquals(List.of("Zebra", "alpha", "zebra", "école"), new ArrayList<>(fake.keyAnswers().keySet()));
    }

    static Stream<Arguments> mutations() {
        return Stream.of(
                Arguments.of("put", (Consumer<SortedMap<String, Object>>) m -> m.put("new", "x")),
                Arguments.of("remove", (Consumer<SortedMap<String, Object>>) m -> m.remove(m.firstKey())),
                Arguments.of("clear", (Consumer<SortedMap<String, Object>>) Map::clear),
                Arguments.of("entry.setValue", (Consumer<SortedMap<String, Object>>) m -> m.entrySet().iterator().next().setValue("x")),
                Arguments.of("iterator.remove", (Consumer<SortedMap<String, Object>>) m -> {
                    var iterator = m.entrySet().iterator(); iterator.next(); iterator.remove();
                }),
                Arguments.of("values.clear", (Consumer<SortedMap<String, Object>>) m -> m.values().clear()),
                Arguments.of("keySet.remove", (Consumer<SortedMap<String, Object>>) m -> m.keySet().remove(m.firstKey())),
                Arguments.of("tailMap.clear", (Consumer<SortedMap<String, Object>>) m -> m.tailMap(m.firstKey()).clear()),
                Arguments.of("subMap.clear", (Consumer<SortedMap<String, Object>>) m -> m.subMap(m.firstKey(), "zzzz").clear()),
                Arguments.of("headMap.clear", (Consumer<SortedMap<String, Object>>) m -> m.headMap("zzzz").clear()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest @MethodSource("mutations")
    void returnedMapsAndTheirViewsCannotBeMutated(String operation, Consumer<SortedMap<String, Object>> mutation) throws Exception {
        var fake = FakeAttachments.of(pulls("string", "age"), SEED);
        var filesBefore = new java.util.TreeMap<>(fake.files());
        var answersBefore = new java.util.TreeMap<>(fake.keyAnswers());
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept((SortedMap) fake.files()), operation);
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept((SortedMap) fake.keyAnswers()), operation);
        assertEquals(filesBefore, fake.files());
        assertEquals(answersBefore, fake.keyAnswers());
    }

    @Test void keyAnswerListsAndTheirViewsCannotBeMutated() throws Exception {
        var fake = FakeAttachments.of(pulls("string", "age"), SEED);
        List<String> keys = fake.keyAnswers().get("key");
        assertNotNull(keys);
        List<String> before = List.copyOf(keys);
        assertThrows(UnsupportedOperationException.class, () -> keys.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> keys.set(0, "x"));
        assertThrows(UnsupportedOperationException.class, () -> keys.remove(0));
        assertThrows(UnsupportedOperationException.class, keys::clear);
        assertThrows(UnsupportedOperationException.class, () -> keys.subList(0, 1).clear());
        assertThrows(UnsupportedOperationException.class, () -> {
            var iterator = keys.listIterator(); iterator.next(); iterator.set("x");
        });
        assertEquals(before, fake.keyAnswers().get("key"));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, SEED})
    void sameBytesAndSeedGiveEqualResultsAcrossLoadsAndCalls(long seed) throws Exception {
        var form = pulls("string", "phone", "lat", "lon", "name", "age", "area", "total", "status");
        var first = FakeAttachments.of(form, seed);
        var second = FakeAttachments.of(form, seed);
        var reloaded = FakeAttachments.of(pulls("string", "phone", "lat", "lon", "name", "age", "area", "total", "status"), seed);
        assertEquals(first.files(), second.files());
        assertEquals(first.keyAnswers(), second.keyAnswers());
        assertEquals(first.files(), reloaded.files());
        assertEquals(first.keyAnswers(), reloaded.keyAnswers());
    }

    @Test void differentSeedsChangeRandomValuesWithoutChangingKeys() throws Exception {
        var form = pulls("string", "phone", "lat", "age");
        var a = FakeAttachments.of(form, 123);
        var b = FakeAttachments.of(form, 456);
        assertNotEquals(a.files(), b.files());
        assertEquals(a.keyAnswers(), b.keyAnswers());
    }

    @Test void laterGenerationsDoNotChangePreviouslyReturnedMapsOrLists() throws Exception {
        var first = FakeAttachments.of(pulls("string", "phone"), SEED);
        var files = first.files();
        var answers = first.keyAnswers();
        var keys = answers.get("key");
        assertNotNull(keys);
        var filesBefore = new java.util.TreeMap<>(files);
        var keysBefore = List.copyOf(keys);
        FakeAttachments.of(pulls("int", "age", "latitude"), -789);
        FakeAttachments.of(levels(3), 456);
        assertEquals(filesBefore, files);
        assertEquals(keysBefore, keys);
        assertEquals(Map.of("key", keysBefore), answers);
    }

    @Test @ResourceLock("java.util.Locale") @ResourceLock("java.util.TimeZone")
    void localeAndTimeZoneDoNotChangeFilesOrKeys() throws Exception {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var baseline = FakeAttachments.of(pulls("string", "PHONE", "latitude", "longitude", "area", "NAME"), SEED);
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale);
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
                var other = FakeAttachments.of(pulls("string", "PHONE", "latitude", "longitude", "area", "NAME"), SEED);
                assertEquals(baseline.files(), other.files(), locale.toString());
                assertEquals(baseline.keyAnswers(), other.keyAnswers(), locale.toString());
            }
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }

    @Test void addingFilesBeforeAndAfterAnExistingFileDoesNotChangeItsRandomSequence() throws Exception {
        String base = calc("v", pull("middle", "phone", "id", "/data/key"));
        var first = FakeAttachments.of(form("<key/><v/>", bind("key", "string", "") + base, input("key")), SEED);
        String more = calc("a", pull("aaa", "phone", "id", "/data/key")) + base
                + calc("z", pull("zzz", "phone", "id", "/data/key"));
        var expanded = FakeAttachments.of(form("<key/><v/><a/><z/>", bind("key", "string", "") + more, input("key")), SEED);
        assertEquals(first.files().get("middle"), expanded.files().get("middle"));
    }

    @Test void fileNameContributesToRandomSequence() throws Exception {
        var form = form("<key/><a/><b/>", bind("key", "string", "")
                + calc("a", pull("alpha", "phone", "id", "/data/key"))
                + calc("b", pull("beta", "phone", "id", "/data/key")), input("key"));
        var fake = FakeAttachments.of(form, SEED);
        assertNotEquals(fake.files().get("alpha"), fake.files().get("beta"));
    }

    @Test void existingAttachmentsAreExcludedAndNeverOverwritten() throws Exception {
        var directory = scratch();
        String real = "age,id\n79,REAL\n";
        Files.writeString(directory.resolve("present.csv"), real);
        Files.writeString(directory.resolve("form.xml"), xml("<key/><a/><b/>", bind("key", "string", "")
                + calc("a", pull("present", "age", "id", "/data/key"))
                + calc("b", pull("missing", "age", "id", "/data/key")), input("key"), "", ""));
        var form = FormLoader.load(directory.resolve("form.xml"), directory);
        assertEquals(java.util.Set.of("missing"), form.missingAttachments());
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(java.util.Set.of("missing"), fake.files().keySet());
        assertEquals(real, Files.readString(directory.resolve("present.csv")));
        assertFalse(Files.exists(directory.resolve("missing.csv")), "API returns contents; it does not write files");
    }
}
