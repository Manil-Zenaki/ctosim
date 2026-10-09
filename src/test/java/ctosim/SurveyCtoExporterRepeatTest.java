package ctosim;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterRepeatTest {
    @TempDir Path temp;
    private static final List<String> JOB_COLUMNS = List.of("job_title", "tags", "tags_1", "tags__97",
            "gps-Latitude", "gps-Longitude", "gps-Altitude", "gps-Accuracy");

    @Test void wideHeaderUsesGlobalMaximumPerParentAndPlacesSuffixesAfterExpandedColumnNames() throws Exception {
        var e = repeats(temp);
        List<String> expected = new ArrayList<>(List.of("SubmissionDate", "before", "member_count"));
        // Hand-specified 2 x 2 shape: max member count and max jobs in any ONE member.
        for (int member = 1; member <= 2; member++) {
            expected.add("member_name_" + member);
            expected.add("job_count_" + member);
            for (int job = 1; job <= 2; job++) for (String column : JOB_COLUMNS) expected.add(column + "_" + member + "_" + job);
            expected.add("member_tail_" + member);
        }
        expected.addAll(List.of("crop_1", "crop_2", "after", "instanceID", "formdef_version", "KEY"));
        assertEquals(expected, csv(e.wide(repeatSubmissions())).get(0));
    }

    @Test void wideRowsFollowListOrderAndXmlInstanceOrderRatherThanSubmissionNumberOrKey() throws Exception {
        var rows = csv(repeats(temp).wide(repeatSubmissions()));
        assertEquals(5, rows.size());
        assertEquals(List.of("uuid:B", "uuid:A", "uuid:zero", "uuid:absent"), rows.subList(1, 5).stream().map(r -> r.get(r.size() - 1)).toList());
        var text = repeats(temp).wide(repeatSubmissions());
        assertEquals("First", row(text, 2).get("member_name_1"));
        assertEquals("Second", row(text, 2).get("member_name_2"));
        assertEquals("J1", row(text, 2).get("job_title_2_1"));
        assertEquals("J2", row(text, 2).get("job_title_2_2"));
        assertEquals("end first", row(text, 2).get("member_tail_1"));
        assertEquals("a", row(text, 2).get("after"));
    }

    @Test void countsComeFromStoredInstancesNotTheCountExpressionOrWideMaximum() throws Exception {
        var text = repeats(temp).wide(repeatSubmissions());
        assertEquals("1", row(text, 1).get("member_count"));
        assertEquals("1", row(text, 1).get("job_count_1"));
        assertEquals("2", row(text, 2).get("member_count"));
        assertEquals("0", row(text, 2).get("job_count_1"));
        assertEquals("2", row(text, 2).get("job_count_2"));
    }

    @Test void absentContainingGroupAndPresentButEmptyGroupHaveDifferentCountCells() throws Exception {
        var e = repeats(temp);
        var submissions = repeatSubmissions();
        assertEquals("0", row(e.wide(submissions), 3).get("member_count"));
        assertEquals("", row(e.wide(submissions), 4).get("member_count"));
        String main = e.longFiles(submissions).get("Repeats.csv");
        assertEquals("0", row(main, 3).get("member_count"));
        assertEquals("", row(main, 4).get("member_count"));
        assertEquals("", row(main, 3).get("SET-OF-member"));
        assertEquals("", row(main, 4).get("SET-OF-member"));
    }

    @Test void absentNestedContainingGroupLeavesCountEmptyWhilePresentEmptyGroupGivesZero() throws Exception {
        var e = repeats(temp);
        var s = submission(1, "uuid:x", "<main><member><member_name>A</member_name></member>"
                + "<member><member_name>B</member_name><info/></member></main>");
        String wide = e.wide(List.of(s));
        assertEquals("", row(wide, 1).get("job_count_1"));
        assertEquals("0", row(wide, 1).get("job_count_2"));
        String members = e.longFiles(List.of(s)).get("Repeats-main-member.csv");
        assertEquals("", row(members, 1).get("job_count"));
        assertEquals("0", row(members, 2).get("job_count"));
    }

    @Test void missingWideInstancesHaveOnlyEmptyCellsIncludingDummiesAndNestedCounts() throws Exception {
        var rows = csv(repeats(temp).wide(repeatSubmissions()));
        var header = rows.get(0);
        var first = rows.get(1);
        int firstMissingJob = header.indexOf("job_title_1_2");
        for (int i = firstMissingJob; i < firstMissingJob + JOB_COLUMNS.size(); i++) assertEquals("", first.get(i), header.get(i));
        int absentMember = header.indexOf("member_name_2");
        int endMember = header.indexOf("member_tail_2");
        for (int i = absentMember; i <= endMember; i++) assertEquals("", first.get(i), header.get(i));
        assertEquals("", row(repeats(temp).wide(repeatSubmissions()), 2).get("crop_2"));
    }

    @Test void nestedSelectAndGeopointCellsUseTheRightInstanceAndPreserveEmptyVersusZero() throws Exception {
        var text = repeats(temp).wide(repeatSubmissions());
        var first = row(text, 1);
        assertEquals("-97", first.get("tags_1_1"));
        assertEquals("0", first.get("tags_1_1_1"));
        assertEquals("1", first.get("tags__97_1_1"));
        assertEquals("1", first.get("gps-Latitude_1_1"));
        assertEquals("4", first.get("gps-Accuracy_1_1"));
        var second = row(text, 2);
        assertEquals("1", second.get("tags_1_2_1"));
        assertEquals("1", second.get("tags__97_2_1"));
        assertEquals("5", second.get("gps-Latitude_2_1"));
        assertEquals("6", second.get("gps-Longitude_2_1"));
        assertEquals("", second.get("gps-Altitude_2_1"));
        assertEquals("", second.get("tags_1_2_2"));
    }

    @Test void zeroGlobalRepeatMaximumRetainsCountsButHasNoWideInstanceColumns() throws Exception {
        var e = repeats(temp);
        String expected = "SubmissionDate,before,member_count,after,instanceID,formdef_version,KEY";
        assertEquals(expected, e.wide(List.of()));
        var text = e.wide(List.of(submission(1, "uuid:x", "<main/>")));
        assertEquals(expected, text.substring(0, text.indexOf('\n')));
        assertEquals("0", row(text, 1).get("member_count"));
    }

    @Test void maximaAreRecomputedForEachCallAndDoNotAccumulateAcrossExports() throws Exception {
        var e = repeats(temp);
        e.wide(repeatSubmissions());
        var smaller = e.wide(List.of(repeatSubmissions().get(0)));
        var header = csv(smaller).get(0);
        assertTrue(header.contains("job_title_1_1"));
        assertFalse(header.contains("job_title_1_2"));
        assertFalse(header.contains("member_name_2"));
        assertEquals("SubmissionDate,before,member_count,after,instanceID,formdef_version,KEY", e.wide(List.of()));
    }

    @Test void longFilesFollowBeginRepeatCatalogOrderAndUsePathsRelativeToNearestRepeat() throws Exception {
        assertEquals(List.of("Repeats.csv", "Repeats-main-member.csv", "Repeats-info-job.csv", "Repeats-main-plot.csv"),
                new ArrayList<>(repeats(temp).longFiles(repeatSubmissions()).keySet()));
    }

    @Test void longMainReplacesTopLevelRepeatsWithCountsAndSetLinksAtTheirCatalogPositions() throws Exception {
        String main = repeats(temp).longFiles(repeatSubmissions()).get("Repeats.csv");
        assertEquals(List.of("SubmissionDate", "before", "member_count", "SET-OF-member", "SET-OF-plot", "after", "instanceID", "formdef_version", "KEY"), csv(main).get(0));
        assertEquals(List.of(DATE, "B", "1", "uuid:B/main-member", "uuid:B/main-plot", "b", "uuid:B", "v08", "uuid:B"), csv(main).get(1));
        assertEquals(List.of(DATE, "A", "2", "uuid:A/main-member", "uuid:A/main-plot", "a", "uuid:A", "v08", "uuid:A"), csv(main).get(2));
    }

    @Test void memberLongRowsHaveUnsuffixedFieldsNestedLinksAndExactParentKeys() throws Exception {
        var rows = csv(repeats(temp).longFiles(repeatSubmissions()).get("Repeats-main-member.csv"));
        assertEquals(List.of(
                List.of("member_name", "job_count", "SET-OF-job", "member_tail", "PARENT_KEY", "KEY", "SET-OF-member"),
                List.of("Bee", "1", "uuid:B/main-member[1]/info-job", "end B", "uuid:B", "uuid:B/main-member[1]", "uuid:B/main-member"),
                List.of("First", "0", "", "end first", "uuid:A", "uuid:A/main-member[1]", "uuid:A/main-member"),
                List.of("Second", "2", "uuid:A/main-member[2]/info-job", "end second", "uuid:A", "uuid:A/main-member[2]", "uuid:A/main-member")), rows);
    }

    @Test void jobLongRowsFollowSubmissionThenParentThenInstanceAndRetainExpandedValues() throws Exception {
        var rows = csv(repeats(temp).longFiles(repeatSubmissions()).get("Repeats-info-job.csv"));
        var header = new ArrayList<>(JOB_COLUMNS);
        header.addAll(List.of("PARENT_KEY", "KEY", "SET-OF-job"));
        assertEquals(List.of(header,
                List.of("Only", "-97", "0", "1", "1", "2", "3", "4", "uuid:B/main-member[1]", "uuid:B/main-member[1]/info-job[1]", "uuid:B/main-member[1]/info-job"),
                List.of("J1", "1 -97", "1", "1", "5", "6", "", "", "uuid:A/main-member[2]", "uuid:A/main-member[2]/info-job[1]", "uuid:A/main-member[2]/info-job"),
                List.of("J2", "", "", "", "", "", "", "", "uuid:A/main-member[2]", "uuid:A/main-member[2]/info-job[2]", "uuid:A/main-member[2]/info-job")), rows);
    }

    @Test void uncountedSiblingRepeatHasNoCountColumnAndNumbersRestartPerSubmission() throws Exception {
        String plots = repeats(temp).longFiles(repeatSubmissions()).get("Repeats-main-plot.csv");
        assertEquals(List.of(List.of("crop", "PARENT_KEY", "KEY", "SET-OF-plot"),
                List.of("maize", "uuid:B", "uuid:B/main-plot[1]", "uuid:B/main-plot"),
                List.of("rice", "uuid:B", "uuid:B/main-plot[2]", "uuid:B/main-plot"),
                List.of("teff", "uuid:A", "uuid:A/main-plot[1]", "uuid:A/main-plot")), csv(plots));
    }

    @Test void everyRepeatGetsAHeaderOnlyLongFileEvenWithNoSubmissions() throws Exception {
        var files = repeats(temp).longFiles(List.of());
        assertEquals(4, files.size());
        assertEquals("member_name,job_count,SET-OF-job,member_tail,PARENT_KEY,KEY,SET-OF-member", files.get("Repeats-main-member.csv"));
        assertEquals(String.join(",", JOB_COLUMNS) + ",PARENT_KEY,KEY,SET-OF-job", files.get("Repeats-info-job.csv"));
        assertEquals("crop,PARENT_KEY,KEY,SET-OF-plot", files.get("Repeats-main-plot.csv"));
        var noInstances = repeats(temp).longFiles(List.of(submission(1, "uuid:x", "<main/>")));
        for (String name : files.keySet()) if (!name.equals("Repeats.csv")) assertEquals(files.get(name), noInstances.get(name));
    }

    @Test void presentEmptyRepeatElementCountsAsAnInstance() throws Exception {
        var e = repeats(temp);
        var s = submission(1, "uuid:x", "<main><member/><plot/></main>");
        assertEquals("1", row(e.wide(List.of(s)), 1).get("member_count"));
        var files = e.longFiles(List.of(s));
        assertEquals(2, csv(files.get("Repeats-main-member.csv")).size());
        assertEquals(List.of("", "uuid:x", "uuid:x/main-plot[1]", "uuid:x/main-plot"), csv(files.get("Repeats-main-plot.csv")).get(1));
    }

    @Test void emptyCountedRepeatDirectlyUnderRootHasZeroCountAndAnEmptyLongFile() throws Exception {
        var form = form("<limit>9</limit><r jr:template=\"\"><value/></r>",
                "<bind nodeset=\"/data/limit\" type=\"int\"/>",
                "<repeat nodeset=\"/data/r\" jr:count=\"/data/limit\"><input ref=\"/data/r/value\"><label>Value</label></input></repeat>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Root", null,
                field("r", "begin repeat", "r", 2), field("value", "text", "r/value", 3)), form, "example");
        var s = submission(1, "uuid:x", "<limit>9</limit>");
        assertEquals(List.of("SubmissionDate", "r_count", "instanceID", "formdef_version", "KEY"), csv(e.wide(List.of(s))).get(0));
        assertEquals("0", row(e.wide(List.of(s)), 1).get("r_count"));
        var files = e.longFiles(List.of(s));
        assertEquals("0", row(files.get("Root.csv"), 1).get("r_count"));
        assertEquals("", row(files.get("Root.csv"), 1).get("SET-OF-r"));
        assertEquals("value,PARENT_KEY,KEY,SET-OF-r", files.get("Root-r.csv"));
    }

    @Test void threeRepeatLevelsHaveAllIndexSuffixesAndRecursivelyLinkedKeys() throws Exception {
        var form = form("<r jr:template=\"\"><s jr:template=\"\"><t jr:template=\"\"><value/></t></s></r>", "",
                "<repeat nodeset=\"/data/r\"><repeat nodeset=\"/data/r/s\"><repeat nodeset=\"/data/r/s/t\">"
                        + "<input ref=\"/data/r/s/t/value\"><label>Value</label></input></repeat></repeat></repeat>", "");
        var c = catalog(temp, "Deep", null, field("r", "begin repeat", "r", 2),
                field("s", "begin repeat", "r/s", 3), field("t", "begin repeat", "r/s/t", 4), field("value", "text", "r/s/t/value", 5));
        var e = new SurveyCtoExporter(c, form, "example");
        var s = submission(1, "uuid:x", "<r><s><t><value>A</value></t></s><s><t><value>B</value></t><t><value>C</value></t></s></r>");
        assertEquals(List.of("SubmissionDate", "value_1_1_1", "value_1_1_2", "value_1_2_1", "value_1_2_2", "instanceID", "formdef_version", "KEY"), csv(e.wide(List.of(s))).get(0));
        assertEquals(List.of(DATE, "A", "", "B", "C", "uuid:x", "", "uuid:x"), csv(e.wide(List.of(s))).get(1));
        var files = e.longFiles(List.of(s));
        assertEquals(List.of("Deep.csv", "Deep-r.csv", "Deep-s.csv", "Deep-t.csv"), new ArrayList<>(files.keySet()));
        assertEquals(List.of("C", "uuid:x/r[1]/s[2]", "uuid:x/r[1]/s[2]/t[2]", "uuid:x/r[1]/s[2]/t"), csv(files.get("Deep-t.csv")).get(3));
        assertEquals("uuid:x/r[1]/s[2]/t", row(files.get("Deep-s.csv"), 2).get("SET-OF-t"));
    }

    @Test void repeatedDateAndAttachmentsUseSameCellRulesAndSubmissionIdRatherThanRepeatKey() throws Exception {
        var form = form("<r jr:template=\"\"><d/><photo/><audit/><note/></r>", "",
                "<repeat nodeset=\"/data/r\"><input ref=\"/data/r/d\"><label>Date</label></input></repeat>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Media", null,
                field("r", "begin repeat", "r", 2), field("d", "date", "r/d", 3),
                field("photo", "image", "r/photo", 4), field("audit", "text audit", "r/audit", 5),
                field("note", "note", "r/note", 6)), form, "example");
        var s = submission(1, "uuid:x", "<r><d>2026-10-07</d><photo>a b.jpg</photo><audit/><note>hidden</note></r><r/>");
        var wide = row(e.wide(List.of(s)), 1);
        assertEquals("Oct 7, 2026", wide.get("d_1"));
        assertEquals(BASE + "uuid%3Ax&file=a+b.jpg", wide.get("photo_1"));
        assertEquals(BASE + "uuid%3Ax&file=TA_x.csv", wide.get("audit_1"));
        assertEquals("", wide.get("audit_2"));
        var rows = csv(e.longFiles(List.of(s)).get("Media-r.csv"));
        assertEquals(List.of("d", "photo", "audit", "PARENT_KEY", "KEY", "SET-OF-r"), rows.get(0));
        assertEquals(List.of("Oct 7, 2026", BASE + "uuid%3Ax&file=a+b.jpg", BASE + "uuid%3Ax&file=TA_x.csv", "uuid:x", "uuid:x/r[1]", "uuid:x/r"), rows.get(1));
        assertEquals(List.of("", "", "", "uuid:x", "uuid:x/r[2]", "uuid:x/r"), rows.get(2));
    }
}
