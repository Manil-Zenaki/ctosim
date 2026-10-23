package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsAmendment1ValuesTest extends FakeAttachmentsAmendment1Support {
    @ParameterizedTest @CsvSource({
            "DISTRICT,district_code", "VILLAGE,village_code",
            "HOUSEHOLD,household_code", "MEMBER,member_code"
    })
    void labelsUseTheirOwnLevelIncludingPaddedIdsAndUnaryDescendants(String label, String code) throws Exception {
        Table actual = table(FakeAttachments.of(cascade(), SEED, "household", 82), "places");
        assertEquals(90, actual.rows().size());
        assertEquals(actual.column(code).stream().map(value -> label + " " + value).toList(), actual.column(label));
    }

    @ParameterizedTest @CsvSource({
            "district_match,district_code", "village_match,village_code",
            "household_match,household_code", "district_filter,district_code", "lookup_id,household_code"
    })
    void matchFilterAndSelectLookupKeysCopyTheExpandedPathCodes(String copy, String level) throws Exception {
        Table actual = table(FakeAttachments.of(cascade(), SEED, "household", 82), "places");
        assertEquals(90, actual.rows().size());
        assertEquals(actual.column(level), actual.column(copy));
    }

    @Test void integerKeyAnswersCoverAllExpandedRowsInNumericRowOrder() throws Exception {
        var fake = FakeAttachments.of(cascade(), SEED, "household", 501);
        Table actual = table(fake, "places");
        var expected = IntStream.rangeClosed(1, 504).mapToObj(Integer::toString).toList();
        assertEquals(expected, actual.column("int_id"));
        assertEquals(expected, fake.keyAnswers().get("intkey"));
    }

    @Test void textKeyAnswersCoverAllExpandedRowsWithFourDigitRowNumbers() throws Exception {
        var fake = FakeAttachments.of(cascade(), SEED, "household", 501);
        Table actual = table(fake, "places");
        var expected = IntStream.rangeClosed(1, 504)
                .mapToObj(n -> String.format(Locale.ROOT, "K%04d", n)).toList();
        assertEquals(expected, actual.column("text_id"));
        assertEquals(expected, fake.keyAnswers().get("textkey"));
        assertEquals(Set.of("intkey", "textkey"), fake.keyAnswers().keySet(), "Expanded selects are not TEXT key questions");
    }

    @Test void randomValueRulesStillApplyToRowsBeyondTheOldFiveHundredRowLimit() throws Exception {
        Table actual = table(FakeAttachments.of(cascade(), SEED, "household", 501), "places");
        assertEquals(504, actual.rows().size());
        for (String phone : actual.column("phone")) assertTrue(phone.matches("0[0-9]{9}"), phone);
        for (String latitude : actual.column("latitude")) {
            assertTrue(latitude.matches("-?[0-9]+\\.[0-9]{6}"), latitude);
            assertTrue(Double.parseDouble(latitude) >= -1 && Double.parseDouble(latitude) <= 1);
        }
        for (String area : actual.column("area")) {
            assertTrue(area.matches("[0-9]+\\.[0-9]{2}"), area);
            assertTrue(Double.parseDouble(area) >= 0.25 && Double.parseDouble(area) <= 5);
        }
        for (String age : actual.column("age")) {
            assertTrue(age.matches("[0-9]+"));
            assertTrue(Integer.parseInt(age) >= 1 && Integer.parseInt(age) <= 80);
        }
        assertTrue(actual.column("total").stream().allMatch(Set.of("1", "2", "3", "4")::contains));
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE, Long.MAX_VALUE, SEED})
    void expansionIsDeterministicAcrossRepeatedCallsAndIndependentLoads(long seed) throws Exception {
        var form = cascade();
        var first = FakeAttachments.of(form, seed, "household", 501);
        var second = FakeAttachments.of(form, seed, "household", 501);
        var reloaded = FakeAttachments.of(cascade(), seed, "household", 501);
        assertEquals(first.files(), second.files());
        assertEquals(first.files(), reloaded.files());
        assertEquals(first.keyAnswers(), second.keyAnswers());
        assertEquals(first.keyAnswers(), reloaded.keyAnswers());
    }

    @Test void seedChangesRandomValuesButNotExpandedCodesOrSuggestedKeys() throws Exception {
        var form = cascade();
        var first = FakeAttachments.of(form, 123, "household", 501);
        var second = FakeAttachments.of(form, 456, "household", 501);
        var a = table(first, "places");
        var b = table(second, "places");
        assertNotEquals(a.column("phone"), b.column("phone"));
        assertEquals(first.keyAnswers(), second.keyAnswers());
        for (String column : List.of("district_code", "village_code", "household_code", "member_code")) {
            assertEquals(Set.copyOf(a.column(column)), Set.copyOf(b.column(column)));
        }
    }

    @Test void requestsThatRoundToTheSameBranchCountProduceIdenticalResults() throws Exception {
        var form = cascade();
        var lower = FakeAttachments.of(form, SEED, "household", 82);
        var upper = FakeAttachments.of(form, SEED, "household", 90);
        assertEquals(90, table(lower, "places").rows().size());
        assertEquals(lower.files(), upper.files());
        assertEquals(lower.keyAnswers(), upper.keyAnswers());
    }

    @Test @ResourceLock("java.util.Locale") @ResourceLock("java.util.TimeZone")
    void paddingAndExpandedRandomValuesAreIndependentOfLocaleAndTimeZone() throws Exception {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var baseline = FakeAttachments.of(cascade(), SEED, "household", 892);
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
                var actual = FakeAttachments.of(cascade(), SEED, "household", 892);
                assertEquals(baseline.files(), actual.files(), locale.toString());
                assertEquals(baseline.keyAnswers(), actual.keyAnswers(), locale.toString());
            }
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }

    @Test void expandingOneFileLeavesOtherFilesByteForByteUnchanged() throws Exception {
        String withOthers = cascadeXml().replace("<phone/>", "<extra_a/><extra_z/><other/><phone/>")
                .replace("</model>", calc("extra_a", pull("aaa", "phone", "key", "'literal'"))
                        + calc("extra_z", pull("zzz", "phone", "key", "'literal'"))
                        + bind("other", "string", "") + "</model>")
                .replace("</h:body>", select("other", "search('other_places')", "other_code", "OTHER") + "</h:body>");
        var form = load(withOthers);
        var baseline = FakeAttachments.of(form, SEED);
        var expanded = FakeAttachments.of(form, SEED, "household", 501);
        assertEquals(List.of("aaa", "other_places", "places", "zzz"), new ArrayList<>(expanded.files().keySet()));
        for (String file : List.of("aaa", "other_places", "zzz")) {
            assertEquals(baseline.files().get(file), expanded.files().get(file), file);
        }
        assertEquals(504, table(expanded, "places").rows().size());
        assertEquals(FakeAttachments.of(cascade(), SEED, "household", 501).files().get("places"),
                expanded.files().get("places"), "Adding unrelated files does not change expanded random draws");
    }

    @ParameterizedTest @ValueSource(strings = {"Alpha", "zeta"})
    void sharedKeyQuestionStillUsesTheFirstFileInNaturalOrderAfterExpansion(String expandedFile) throws Exception {
        var form = form("<q/><key/><a/><z/>", bind("q", "string", "") + bind("key", "int", "")
                + calc("z", pull("zeta", "age", "id", "/data/key"))
                + calc("a", pull("Alpha", "age", "id", "/data/key")), input("key")
                + select("q", "search('" + expandedFile + "')", "code", "LABEL"));
        var fake = FakeAttachments.of(form, SEED, "q", 501);
        assertEquals(501, table(fake, expandedFile).rows().size());
        assertEquals(table(fake, "Alpha").column("id"), fake.keyAnswers().get("key"));
        assertEquals(expandedFile.equals("Alpha") ? 501 : 50, fake.keyAnswers().get("key").size());
    }

    @Test void expandedMapsAndKeyListsRemainUnmodifiable() throws Exception {
        var fake = FakeAttachments.of(cascade(), SEED, "household", 501);
        assertEquals(List.of("intkey", "textkey"), new ArrayList<>(fake.keyAnswers().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> fake.files().put("places", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> fake.files().entrySet().iterator().next().setValue("changed"));
        assertThrows(UnsupportedOperationException.class, () -> fake.keyAnswers().clear());
        assertThrows(UnsupportedOperationException.class, () -> fake.keyAnswers().get("intkey").set(0, "changed"));
        assertThrows(UnsupportedOperationException.class, () -> fake.keyAnswers().get("textkey").subList(0, 1).clear());
    }

    @Test void laterCallsDoNotMutateEarlierExpansionOrTheFormsOrdinaryResult() throws Exception {
        var form = cascade();
        var baseline = FakeAttachments.of(form, SEED);
        var baselineFiles = new TreeMap<>(baseline.files());
        var baselineAnswers = new TreeMap<>(baseline.keyAnswers());
        var expanded = FakeAttachments.of(form, SEED, "household", 82);
        var expandedFiles = new TreeMap<>(expanded.files());
        var expandedKeys = List.copyOf(expanded.keyAnswers().get("intkey"));
        FakeAttachments.of(form, -789, "district", 501);
        FakeAttachments.of(form, SEED, "member", 1000);
        assertEquals(expandedFiles, expanded.files());
        assertEquals(expandedKeys, expanded.keyAnswers().get("intkey"));
        assertEquals(baselineFiles, baseline.files());
        assertEquals(baselineAnswers, baseline.keyAnswers());
        assertEquals(baselineFiles, FakeAttachments.of(form, SEED).files());
        assertEquals(baselineAnswers, FakeAttachments.of(form, SEED).keyAnswers());
    }
}
