package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsSearchTest extends FakeAttachmentsTestSupport {
    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 12})
    void treeEnumeratesAllPathsAndSwitchesFromThreeToTwoToOneChild(int depth) throws Exception {
        Table table = table(FakeAttachments.of(levels(depth), SEED), "places");
        // These are the spec's boundary results: 243*3 exceeds 500, 243*2 fits,
        // and neither 486*3 nor 486*2 fits. No implementation-derived oracle.
        int[] expectedSizes = {3, 9, 27, 81, 243, 486, 486, 486, 486, 486, 486, 486};
        assertEquals(expectedSizes[depth - 1], table.rows().size());
        List<String> expectedLeaves = List.of("1", "2", "3");
        for (int level = 1; level < depth; level++) {
            int children = level < 5 ? 3 : level == 5 ? 2 : 1;
            List<String> next = new ArrayList<>();
            for (String parent : expectedLeaves) for (int child = 1; child <= children; child++) next.add(parent + child);
            expectedLeaves = next;
        }
        List<String> leaves = table.column("code" + (depth - 1));
        assertEquals(new HashSet<>(expectedLeaves), new HashSet<>(leaves));
        assertEquals(leaves.size(), new HashSet<>(leaves).size(), "Exactly one row per leaf");
        for (int level = 0; level < depth; level++) {
            List<String> codes = table.column("code" + level);
            assertEquals(expectedSizes[level], new HashSet<>(codes).size());
            for (int row = 0; row < leaves.size(); row++) {
                assertEquals(leaves.get(row).substring(0, level + 1), codes.get(row), "Path prefix at level " + level);
            }
        }
    }

    @Test void eachLabelUsesItsOwnLevelCodeRatherThanTheLeafOrRowNumber() throws Exception {
        Table table = table(FakeAttachments.of(levels(4), SEED), "places");
        for (int level = 0; level < 4; level++) {
            List<String> labels = table.column("LABEL" + level);
            List<String> codes = table.column("code" + level);
            for (int row = 0; row < labels.size(); row++) assertEquals("LABEL" + level + " " + codes.get(row), labels.get(row));
        }
    }

    @Test void bodyOrderDeterminesLevelsAndColumnsEvenWhenBindAndInstanceOrderDiffer() throws Exception {
        var form = form("<a/><b/>", bind("a", "string", "") + bind("b", "string", ""),
                select("b", "search('places')", "z_code", "Z_LABEL")
                        + select("a", "search('places')", "a_code", "A_LABEL"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(List.of("z_code", "Z_LABEL", "a_code", "A_LABEL"), table.header());
        assertEquals(Set.of("1", "2", "3"), Set.copyOf(table.column("z_code")));
        assertEquals(9, new HashSet<>(table.column("a_code")).size());
    }

    @Test void duplicateValueColumnsDoNotCreateExtraLevelsOrHeaders() throws Exception {
        var form = form("<a/><b/><c/>", bind("a", "string", "") + bind("b", "string", "") + bind("c", "string", ""),
                select("a", "search('places')", "code", "LABEL")
                        + select("b", "search('places')", "code", "OTHER_LABEL")
                        + select("c", "search('places')", "child", "CHILD_LABEL"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(9, table.rows().size());
        assertEquals(List.of("code", "LABEL", "OTHER_LABEL", "child", "CHILD_LABEL"), table.header());
        for (int row = 0; row < 9; row++) assertEquals("OTHER_LABEL " + table.column("code").get(row), table.column("OTHER_LABEL").get(row));
    }

    @ParameterizedTest @ValueSource(strings = {"select1", "select"})
    void searchIsFoundAmongOtherAppearanceTokensForEitherSelectKind(String control) throws Exception {
        String select = select("q", "minimal search('places')", "code", "LABEL")
                .replace("<select1 ", "<" + control + " ").replace("</select1>", "</" + control + ">");
        var form = form("<q/>", bind("q", "string", ""), select);
        assertEquals(3, table(FakeAttachments.of(form, SEED), "places").rows().size());
    }

    @Test void firstNonnumericChoiceIsPlaceholderAndNumericStaticChoicesAddNoColumns() throws Exception {
        String body = select("q", "search('places')", "code", "LABEL")
                .replace("<item>", "<item><label>STATIC</label><value>-99</value></item><item>")
                .replace("</select1>", "<item><label>IGNORED</label><value>later_text</value></item></select1>");
        Table table = table(FakeAttachments.of(form("<q/>", bind("q", "string", ""), body), SEED), "places");
        assertEquals(List.of("code", "LABEL"), table.header());
    }

    @Test void emptyPlaceholderLabelDoesNotCreateAnEmptyColumn() throws Exception {
        var form = form("<q/>", bind("q", "string", ""), select("q", "search('places')", "code", ""));
        assertEquals(List.of("code"), table(FakeAttachments.of(form, SEED), "places").header());
    }

    @Test void translatedPlaceholderUsesTheFormsDefaultLanguage() throws Exception {
        String itext = """
                <itext>
                  <translation lang="English"><text id="column"><value>EN_LABEL</value></text></translation>
                  <translation lang="French" default=""><text id="column"><value>ÉTIQUETTE</value></text></translation>
                </itext>
                """;
        String body = select("q", "search('places')", "code", "unused")
                .replace("<label>unused</label>", "<label ref=\"jr:itext('column')\"/>");
        var form = load(xml("<q/>", bind("q", "string", ""), body, itext, ""));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(List.of("code", "ÉTIQUETTE"), table.header());
        for (int i = 0; i < 3; i++) assertEquals("ÉTIQUETTE " + table.column("code").get(i), table.column("ÉTIQUETTE").get(i));
    }

    @ParameterizedTest @ValueSource(strings = {"contains", "startswith", "endswith", "matches"})
    void fourArgumentSearchCopiesReferencedSelectCodeIntoEveryMatchColumn(String mode) throws Exception {
        var form = form("<parent/><child/>", bind("parent", "string", "") + bind("child", "string", ""),
                select("parent", "search('places')", "parent_code", "PARENT_LABEL")
                        + select("child", "search('places','" + mode + "','match_a,match_b',/data/parent)", "child_code", "CHILD_LABEL"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(table.column("parent_code"), table.column("match_a"));
        assertEquals(table.column("parent_code"), table.column("match_b"));
    }

    @Test void sixArgumentSearchUsesDifferentReferencedLevelsForMatchAndFilter() throws Exception {
        var form = form("<region/><district/><village/>", bind("region", "string", "")
                + bind("district", "string", "") + bind("village", "string", ""),
                select("region", "search('places')", "region_code", "REGION")
                        + select("district", "search('places')", "district_code", "DISTRICT")
                        + select("village", "search('places','matches','district_copy',/data/district,'region_copy',/data/region)", "village_code", "VILLAGE"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(table.column("district_code"), table.column("district_copy"));
        assertEquals(table.column("region_code"), table.column("region_copy"));
    }

    @Test void matchAndFilterColumnsWithoutReferencedSelectUseFallbackValues() throws Exception {
        var form = form("<key/><q/>", bind("key", "string", "") + bind("q", "string", ""), input("key")
                + select("q", "search('places','matches','match_col',/data/key,'filter_col','literal')", "code", "LABEL"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertTrue(table.column("match_col").stream().allMatch(Set.of("1", "2", "3")::contains));
        assertTrue(table.column("filter_col").stream().allMatch(Set.of("1", "2", "3")::contains));
    }

    @Test void nonliteralMatchAndFilterColumnNamesAreNotInvented() throws Exception {
        var form = form("<key/><q/>", bind("key", "string", "") + bind("q", "string", ""), input("key")
                + select("q", "search('places','matches',concat('a','b'),/data/key,concat('c','d'),/data/key)", "code", "LABEL"));
        assertEquals(List.of("code", "LABEL"), table(FakeAttachments.of(form, SEED), "places").header());
    }

    @Test void nonliteralSearchFileNameAddsNothing() throws Exception {
        var form = form("<q/>", bind("q", "string", ""), select("q", "search(concat('pla','ces'))", "code", "LABEL"));
        assertTrue(FakeAttachments.of(form, SEED).files().isEmpty());
    }

    @Test void staticOnlySelectDoesNotInventALevelInAnOtherwiseReadFile() throws Exception {
        var form = form("<key/><q/><v/>", bind("key", "string", "") + bind("q", "string", "")
                + calc("v", pull("places", "age", "id", "/data/key")), input("key")
                + select("q", "search('places')", "-99", "STATIC_LABEL"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(List.of("age", "id"), table.header());
        assertEquals(50, table.rows().size());
    }

    @Test void labelsContainingCsvSpecialCharactersAreEscapedInHeadersAndValues() throws Exception {
        String label = "Village, \"école\"\nName";
        var form = form("<q/>", bind("q", "string", ""), select("q", "search('places')", "code", label));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        for (int i = 0; i < 3; i++) assertEquals(label + " " + table.column("code").get(i), table.column(label).get(i));
    }
}
