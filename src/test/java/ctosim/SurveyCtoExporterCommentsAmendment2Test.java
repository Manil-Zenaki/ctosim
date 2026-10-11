package ctosim;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterAmendment2Support.*;
import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterCommentsAmendment2Test {
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings = {"Comments-abc.csv", "different.csv", "TA_abc.csv", "AA_abc_AFTER_0S.m4a"})
    void commentsUseTheStoredFilenameWithoutSynthesizingAnAuditOrCommentsName(String filename) throws Exception {
        assertValueBoth(single(temp, "comments"), value(filename), BASE + "uuid%3Aabc&file=" + filename);
    }

    @ParameterizedTest @ValueSource(strings = {"", "<value/>", "<value></value>"})
    void missingOrEmptyCommentsDoNotGenerateAnAttachment(String content) throws Exception {
        assertValueBoth(single(temp, "comments"), submission(1, "uuid:abc", content), "");
    }

    @ParameterizedTest @ValueSource(strings = {"comments", "COMMENTS", "CoMmEnTs"})
    void commentsTypeRecognitionUsesTheSpecifiedCaseInsensitiveComparison(String type) throws Exception {
        assertValueBoth(single(temp, type), value("Comments-abc.csv"), BASE + "uuid%3Aabc&file=Comments-abc.csv");
    }

    @Test void commentsEncodeBothUrlParametersUsingUtf8FormEncoding() throws Exception {
        var s = submission(1, "uuid:é /+&?", "<value>" + xmlText("Commentaires 東京,+&?/#%\".csv") + "</value>");
        assertValueBoth(single(temp, "comments"), s,
                BASE + "uuid%3A%C3%A9+%2F%2B%26%3F&file=Commentaires+%E6%9D%B1%E4%BA%AC%2C%2B%26%3F%2F%23%25%22.csv");
    }

    @Test void commentsPreserveFilenameSpacesBeforeEncoding() throws Exception {
        assertValueBoth(single(temp, "comments"), value(" Comments-abc.csv "),
                BASE + "uuid%3Aabc&file=+Comments-abc.csv+");
    }

    @ParameterizedTest @CsvSource({"uuid:abc,uuid%3Aabc", "abc,abc", "uuid:uuid:abc,uuid%3Auuid%3Aabc"})
    void commentsKeepTheFullSubmissionIdAndDoNotDeriveTheFilenameFromIt(String id, String encoded) throws Exception {
        assertValueBoth(single(temp, "comments"), submission(1, id, "<value>Comments-other.csv</value>"),
                BASE + encoded + "&file=Comments-other.csv");
    }

    @Test void commentsUseTheConfiguredServer() throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", "v08", field("value", "comments", "value", 2)),
                simpleForm(), "Test-42");
        assertValueBoth(e, value("Comments-abc.csv"),
                "https://Test-42.surveycto.com/view/submission-attachment/?uuid=uuid%3Aabc&file=Comments-abc.csv");
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void commentsAreResolvedPerSubmissionIncludingBlankAndAbsentValues(Layout layout) throws Exception {
        var input = List.of(submission(1, "uuid:a", "<comments>Comments-a.csv</comments>"),
                submission(2, "uuid:b", "<comments/>"), submission(3, "uuid:c", ""),
                submission(4, "uuid:d", "<comments>stored.csv</comments>"));
        String text = main(exporter(), input, layout);
        assertEquals(BASE + "uuid%3Aa&file=Comments-a.csv", row(text, 1).get("comments"));
        assertEquals("", row(text, 2).get("comments"));
        assertEquals("", row(text, 3).get("comments"));
        assertEquals(BASE + "uuid%3Ad&file=stored.csv", row(text, 4).get("comments"));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void groupedCommentsUseTheirFullPathAndIgnoreDecoys(Layout layout) throws Exception {
        var input = List.of(submission(1, "uuid:a", "<g><group_comments>real.csv</group_comments></g>"),
                submission(2, "uuid:b", "<g/><group_comments>decoy.csv</group_comments>"),
                submission(3, "uuid:c", "<group_comments>decoy.csv</group_comments>"));
        String text = main(exporter(), input, layout);
        assertEquals(BASE + "uuid%3Aa&file=real.csv", row(text, 1).get("group_comments"));
        assertEquals("", row(text, 2).get("group_comments"));
        assertEquals("", row(text, 3).get("group_comments"));
    }

    private List<Submission> repeated() {
        return List.of(submission(1, "uuid:a", "<r><repeat_comments>one.csv</repeat_comments>"
                + "<s><nested_comments>nested.csv</nested_comments></s><s><nested_comments/></s><s/></r>"
                + "<r><repeat_comments/></r><r/>"),
                submission(2, "uuid:b", "<r><repeat_comments>two.csv</repeat_comments>"
                        + "<s><nested_comments>other.csv</nested_comments></s></r>"));
    }

    @Test void wideRepeatedCommentsUseSubmissionIdsAndLeaveEmptyAbsentAndPaddedCellsEmpty() throws Exception {
        String text = exporter().wide(repeated());
        assertEquals(BASE + "uuid%3Aa&file=one.csv", row(text, 1).get("repeat_comments_1"));
        assertEquals(BASE + "uuid%3Ab&file=two.csv", row(text, 2).get("repeat_comments_1"));
        for (int row = 1; row <= 2; row++) for (int k = 2; k <= 3; k++)
            assertEquals("", row(text, row).get("repeat_comments_" + k));
    }

    @Test void longRepeatedCommentsUseSubmissionIdsRatherThanRepeatKeys() throws Exception {
        String text = exporter().longFiles(repeated()).get("Amendment 2-r.csv");
        assertEquals(5, csv(text).size());
        assertEquals(BASE + "uuid%3Aa&file=one.csv", row(text, 1).get("repeat_comments"));
        assertEquals("", row(text, 2).get("repeat_comments"));
        assertEquals("", row(text, 3).get("repeat_comments"));
        assertEquals(BASE + "uuid%3Ab&file=two.csv", row(text, 4).get("repeat_comments"));
    }

    @Test void nestedWideCommentsRespectBothRepeatIndexesAndPadding() throws Exception {
        String text = exporter().wide(repeated());
        assertEquals(BASE + "uuid%3Aa&file=nested.csv", row(text, 1).get("nested_comments_1_1"));
        assertEquals(BASE + "uuid%3Ab&file=other.csv", row(text, 2).get("nested_comments_1_1"));
        for (int row = 1; row <= 2; row++) for (int outer = 1; outer <= 3; outer++)
            for (int inner = 1; inner <= 3; inner++) {
                if (outer == 1 && inner == 1) continue;
                assertEquals("", row(text, row).get("nested_comments_" + outer + "_" + inner));
            }
    }

    @Test void nestedLongCommentsUseOriginalSubmissionIdsAndActualInstances() throws Exception {
        String text = exporter().longFiles(repeated()).get("Amendment 2-s.csv");
        assertEquals(5, csv(text).size());
        assertEquals(BASE + "uuid%3Aa&file=nested.csv", row(text, 1).get("nested_comments"));
        assertEquals("", row(text, 2).get("nested_comments"));
        assertEquals("", row(text, 3).get("nested_comments"));
        assertEquals(BASE + "uuid%3Ab&file=other.csv", row(text, 4).get("nested_comments"));
    }
}
