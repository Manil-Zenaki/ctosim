package ctosim;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsAmendment1RowsTest extends FakeAttachmentsAmendment1Support {
    static Stream<Arguments> earlyIdExpansionsWithinOrdinaryLeafCount() {
        return Stream.of(
                Arguments.of(0, 4, new int[] {4, 1, 1}),
                Arguments.of(0, 20, new int[] {20, 1, 1}),
                Arguments.of(0, 26, new int[] {26, 1, 1}),
                Arguments.of(0, 27, new int[] {27, 1, 1}),
                Arguments.of(1, 10, new int[] {3, 4, 1}),
                Arguments.of(1, 26, new int[] {3, 9, 1}),
                Arguments.of(1, 27, new int[] {3, 9, 1}),
                Arguments.of(0, 486, new int[] {486, 1, 1, 1, 1, 1, 1, 1}));
    }

    @ParameterizedTest @MethodSource("earlyIdExpansionsWithinOrdinaryLeafCount")
    void submissionsAboveIdCodeCountExpandEvenWhenOrdinaryFileHasEnoughRows(
            int idLevel, int submissions, int[] branches) throws Exception {
        Table actual = table(FakeAttachments.of(levels(branches.length), SEED,
                "q" + idLevel, submissions), "places");
        assertPaths(actual, idLevel, submissions, branches);
    }

    @ParameterizedTest @ValueSource(ints = {4, 9, 10, 12, 99, 100, 501, 1001})
    void firstAndOnlyLevelHasExactlyTheRequestedNumberOfDistinctCodes(int submissions) throws Exception {
        Table actual = table(FakeAttachments.of(levels(1), SEED, "q0", submissions), "places");
        assertPaths(actual, 0, submissions, submissions);
    }

    @ParameterizedTest @CsvSource({
            "28,4", "35,4", "36,4", "37,5", "81,9", "82,10", "108,12",
            "499,56", "500,56", "501,56", "810,90", "891,99", "892,100"
    })
    void lastLevelRoundsUpPerParentAndPadsOnlyWhenBranchCountExceedsNine(int submissions, int children)
            throws Exception {
        Table actual = table(FakeAttachments.of(levels(3), SEED, "q2", submissions), "places");
        assertPaths(actual, 2, submissions, 3, 3, children);
    }

    @ParameterizedTest @ValueSource(ints = {28, 100, 501})
    void expandedFirstLevelHasOneChildAtEachFollowingLevel(int submissions) throws Exception {
        Table actual = table(FakeAttachments.of(levels(3), SEED, "q0", submissions), "places");
        assertPaths(actual, 0, submissions, submissions, 1, 1);
    }

    @ParameterizedTest @CsvSource({"28,10", "30,10", "31,11", "297,99", "298,100", "501,167"})
    void expandedMiddleLevelKeepsEarlierBranchesAndMakesLaterLevelsUnary(int submissions, int children)
            throws Exception {
        Table actual = table(FakeAttachments.of(levels(3), SEED, "q1", submissions), "places");
        assertPaths(actual, 1, submissions, 3, children, 1);
    }

    static Stream<Arguments> deepTrees() {
        return Stream.of(
                Arguments.of(5, 487, new int[] {3, 3, 3, 3, 3, 3}),
                Arguments.of(5, 730, new int[] {3, 3, 3, 3, 3, 4}),
                Arguments.of(6, 487, new int[] {3, 3, 3, 3, 3, 2, 2}),
                Arguments.of(7, 487, new int[] {3, 3, 3, 3, 3, 2, 1, 2}),
                Arguments.of(5, 487, new int[] {3, 3, 3, 3, 3, 3, 1, 1}),
                Arguments.of(6, 972, new int[] {3, 3, 3, 3, 3, 2, 2}),
                Arguments.of(6, 973, new int[] {3, 3, 3, 3, 3, 2, 3}));
    }

    @ParameterizedTest @MethodSource("deepTrees")
    void expansionRemovesTheCapButPreservesOrdinaryTwoAndOneChildAncestors(
            int idLevel, int submissions, int[] branches) throws Exception {
        Table actual = table(FakeAttachments.of(levels(branches.length), SEED, "q" + idLevel, submissions), "places");
        assertPaths(actual, idLevel, submissions, branches);
    }

    @Test void idLevelFollowsBodyOrderRatherThanBindOrderOrQuestionName() throws Exception {
        var form = form("<a/><b/><c/>", bind("a", "string", "") + bind("b", "string", "")
                + bind("c", "string", ""),
                select("c", "search('places')", "code0", "LABEL0")
                + select("a", "search('places')", "code1", "LABEL1")
                + select("b", "search('places')", "code2", "LABEL2"));
        assertPaths(table(FakeAttachments.of(form, SEED, "a", 28), "places"), 1, 28, 3, 10, 1);
    }

    @Test void duplicateSearchValueColumnIdentifiesTheExistingLevelWithoutAddingAnotherLevel() throws Exception {
        var form = form("<first/><alias/><child/>", bind("first", "string", "")
                + bind("alias", "string", "") + bind("child", "string", ""),
                select("first", "search('places')", "code0", "FIRST")
                + select("alias", "search('places')", "code0", "ALIAS")
                + select("child", "search('places')", "code1", "CHILD"));
        var fake = FakeAttachments.of(form, SEED, "alias", 10);
        assertPaths(table(fake, "places"), 0, 10, 10, 1);
        assertEquals(FakeAttachments.of(form, SEED, "first", 10).files(), fake.files());
    }

    @Test void firstLevelPaddingUsesAsciiDigitsAndEveryCodeHasTheSameWidth() throws Exception {
        var codes = table(FakeAttachments.of(levels(1), SEED, "q0", 12), "places").column("code0");
        assertEquals(Set.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"), Set.copyOf(codes));
    }

    @Test void unicodeQuestionNameCanIdentifyTheExpandedLevel() throws Exception {
        var form = form("<m\u00e9nage/>", bind("m\u00e9nage", "string", ""),
                select("m\u00e9nage", "search('places')", "code0", "LABEL"));
        assertPaths(table(FakeAttachments.of(form, SEED, "m\u00e9nage", 12), "places"), 0, 12, 12);
    }

    @Test void integerBoundSelectOneIsStillEligibleForExpandedCodes() throws Exception {
        var form = form("<q/>", bind("q", "int", ""), select("q", "search('places')", "code0", "LABEL"));
        assertPaths(table(FakeAttachments.of(form, SEED, "q", 12), "places"), 0, 12, 12);
    }

    @Test void aPaddedIdHasExactlyOneUnpaddedChildSuffixAtEveryLaterLevel() throws Exception {
        Table actual = table(FakeAttachments.of(levels(4), SEED, "q1", 82), "places");
        assertPaths(actual, 1, 82, 3, 28, 1, 1);
        for (int row = 0; row < actual.rows().size(); row++) {
            assertEquals(actual.column("code1").get(row) + "1", actual.column("code2").get(row));
            assertEquals(actual.column("code1").get(row) + "11", actual.column("code3").get(row));
        }
    }
}
