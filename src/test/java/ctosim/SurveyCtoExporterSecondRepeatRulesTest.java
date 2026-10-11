package ctosim;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static ctosim.SurveyCtoExporterSecondRealExportTest.TITLE;
import static org.junit.jupiter.api.Assertions.*;

/** Boundary checks for the four repeat/choice rules confirmed in Amendment 1. */
class SurveyCtoExporterSecondRepeatRulesTest {
    @TempDir Path temp;

    private SurveyCtoExporter exporter() throws Exception {
        return SurveyCtoExporterSecondRealExportTest.exporter();
    }

    private Submission input(int number, String main) {
        return Submission.of(number, "<data id=\"ctosim_coverage2\"><main>" + main
                + "</main><meta><instanceID>uuid:" + number + "</instanceID></meta></data>", START, END);
    }

    private List<Submission> gpsInputs() {
        return List.of(input(1, "<member><member_gps>1.00 -2.50 3.0 4.00</member_gps></member>"
                + "<member><member_gps>5 -6</member_gps></member><member><member_gps/></member>"),
                input(2, "<member/>"));
    }

    @Test void repeatGeopointComponentNamesPrecedeInstanceSuffixAndPreservePrecision() throws Exception {
        var text = exporter().wide(gpsInputs());
        var header = csv(text).get(0);
        int first = header.indexOf("member_name_1");
        assertTrue(first >= 0);
        assertEquals(List.of("member_name_1", "member_gps-Latitude_1", "member_gps-Longitude_1",
                "member_gps-Altitude_1", "member_gps-Accuracy_1", "member_activities_1"), header.subList(first, first + 6));
        var r = row(text, 1);
        assertEquals("1.00", r.get("member_gps-Latitude_1"));
        assertEquals("-2.50", r.get("member_gps-Longitude_1"));
        assertEquals("3.0", r.get("member_gps-Altitude_1"));
        assertEquals("4.00", r.get("member_gps-Accuracy_1"));
        assertFalse(header.contains("member_gps_1"));
    }

    @Test void partialRepeatGeopointLeavesOnlyMissingComponentsEmpty() throws Exception {
        var e = exporter();
        var wide = row(e.wide(gpsInputs()), 1);
        var member = row(e.longFiles(gpsInputs()).get(TITLE + "-main-member.csv"), 2);
        for (var component : List.of("Latitude", "Longitude", "Altitude", "Accuracy")) {
            String expected = switch (component) { case "Latitude" -> "5"; case "Longitude" -> "-6"; default -> ""; };
            assertEquals(expected, wide.get("member_gps-" + component + "_2"), component);
            assertEquals(expected, member.get("member_gps-" + component), component);
        }
    }

    @Test void emptyMissingAndPaddedRepeatGeopointsHaveEmptyComponentCells() throws Exception {
        var e = exporter();
        String wide = e.wide(gpsInputs());
        String members = e.longFiles(gpsInputs()).get(TITLE + "-main-member.csv");
        for (String component : List.of("Latitude", "Longitude", "Altitude", "Accuracy")) {
            assertEquals("", row(wide, 1).get("member_gps-" + component + "_3"));
            for (int k = 1; k <= 3; k++) assertEquals("", row(wide, 2).get("member_gps-" + component + "_" + k));
            assertEquals("", row(members, 3).get("member_gps-" + component));
            assertEquals("", row(members, 4).get("member_gps-" + component));
        }
    }

    private List<Submission> jobs() {
        return List.of(input(1, "<member><job><job_days>1 6</job_days></job></member>"
                + "<member><job><job_days>-99</job_days></job><job><job_days>6</job_days></job>"
                + "<job><job_days/></job><job/></member>"), input(2, "<member/>"));
    }

    @Test void nestedDummyNamesPutChoiceThenOutermostAndInnermostIndexes() throws Exception {
        var text = exporter().wide(jobs());
        var header = csv(text).get(0);
        for (int member = 1; member <= 2; member++) {
            for (int job = 1; job <= 4; job++) {
                String suffix = "_" + member + "_" + job;
                int position = header.indexOf("job_days" + suffix);
                assertTrue(position >= 0, suffix);
                assertEquals(List.of("job_days" + suffix, "job_days_1" + suffix,
                        "job_days_6" + suffix, "job_days__99" + suffix), header.subList(position, position + 4));
            }
        }
        var r = row(text, 1);
        assertEquals("1", r.get("job_days_1_1_1"));
        assertEquals("1", r.get("job_days_6_1_1"));
        assertEquals("0", r.get("job_days__99_1_1"));
        assertEquals("0", r.get("job_days_1_2_1"));
        assertEquals("0", r.get("job_days_6_2_1"));
        assertEquals("1", r.get("job_days__99_2_1"));
        assertEquals("0", r.get("job_days_1_2_2"));
        assertEquals("1", r.get("job_days_6_2_2"));
        assertEquals("0", r.get("job_days__99_2_2"));
    }

    @Test void blankAbsentAndPaddedNestedSelectsHaveEmptyDummyCells() throws Exception {
        var e = exporter();
        String wide = e.wide(jobs());
        String longJobs = e.longFiles(jobs()).get(TITLE + "-job.csv");
        for (String name : List.of("job_days", "job_days_1", "job_days_6", "job_days__99")) {
            var first = row(wide, 1);
            assertEquals("", first.get(name + "_2_3"), "Present empty selection");
            assertEquals("", first.get(name + "_2_4"), "Absent selection in present job");
            assertEquals("", first.get(name + "_1_2"), "Padded job");
            assertEquals("", row(wide, 2).get(name + "_2_1"), "Padded outer member");
            assertEquals("", row(longJobs, 4).get(name));
            assertEquals("", row(longJobs, 5).get(name));
        }
    }

    @Test void nestedLongSelectDummiesHaveNoRepeatSuffixAndKeepChoiceOrder() throws Exception {
        String text = exporter().longFiles(jobs()).get(TITLE + "-job.csv");
        assertEquals(List.of("job_title", "job_days", "job_days_1", "job_days_6", "job_days__99",
                "PARENT_KEY", "KEY", "SET-OF-job"), csv(text).get(0));
        assertEquals("uuid:1/main-member[2]/job[2]", row(text, 3).get("KEY"));
        assertEquals("uuid:1/main-member[2]", row(text, 3).get("PARENT_KEY"));
        assertEquals("6", row(text, 3).get("job_days"));
        assertEquals("1", row(text, 3).get("job_days_6"));
        assertEquals("0", row(text, 3).get("job_days__99"));
    }

    private List<Submission> children() {
        return List.of(input(1, "<member><n_children>9</n_children><child><child_name>A</child_name></child>"
                + "<child><child_name>B</child_name></child></member><member><n_children>9</n_children></member>"
                + "<member><n_children>9</n_children><child/></member>"), input(2, "<member/>"));
    }

    @Test void nestedCountIsAtTheRepeatPositionBeforeChildBlocksInEachOuterBlock() throws Exception {
        String text = exporter().wide(children());
        var header = csv(text).get(0);
        for (int k = 1; k <= 3; k++) {
            int position = header.indexOf("n_children_" + k);
            assertTrue(position >= 0);
            assertEquals(List.of("n_children_" + k, "child_count_" + k,
                    "child_name_" + k + "_1", "child_name_" + k + "_2"), header.subList(position, position + 4));
        }
        var r = row(text, 1);
        assertEquals("2", r.get("child_count_1"));
        assertEquals("0", r.get("child_count_2"));
        assertEquals("1", r.get("child_count_3"));
        assertEquals("0", row(text, 2).get("child_count_1"));
        assertEquals("", row(text, 2).get("child_count_2"));
        assertEquals("", row(text, 2).get("child_count_3"));
    }

    @Test void longMemberCountPrecedesSetOfChildAndUsesEachParentsActualInstances() throws Exception {
        String text = exporter().longFiles(children()).get(TITLE + "-main-member.csv");
        var header = csv(text).get(0);
        int position = header.indexOf("n_children");
        assertTrue(position >= 0);
        assertEquals(List.of("n_children", "child_count", "SET-OF-child", "SET-OF-job"), header.subList(position, position + 4));
        assertEquals("2", row(text, 1).get("child_count"));
        assertEquals("uuid:1/main-member[1]/child", row(text, 1).get("SET-OF-child"));
        assertEquals("0", row(text, 2).get("child_count"));
        assertEquals("", row(text, 2).get("SET-OF-child"));
        assertEquals("1", row(text, 3).get("child_count"));
        assertEquals("uuid:1/main-member[3]/child", row(text, 3).get("SET-OF-child"));
    }

    @Test void zeroGlobalChildMaximumRetainsCountsButNoChildInstanceColumns() throws Exception {
        var input = List.of(input(1, "<member><n_children>8</n_children></member>"));
        var e = exporter();
        String wide = e.wide(input);
        assertEquals("0", row(wide, 1).get("child_count_1"));
        assertFalse(csv(wide).get(0).stream().anyMatch(h -> h.startsWith("child_name_")));
        assertEquals(List.of(List.of("child_name", "PARENT_KEY", "KEY", "SET-OF-child")),
                csv(e.longFiles(input).get(TITLE + "-child.csv")));
    }

    @Test void instanceNameMetadataNeverAppearsInRepeatFiles() throws Exception {
        var files = exporter().longFiles(children());
        for (String name : List.of("-main-member.csv", "-child.csv", "-job.csv", "-main-plot.csv")) {
            assertFalse(csv(files.get(TITLE + name)).get(0).contains("instanceName"), name);
        }
    }

    @Test void everyHyphenInChoiceValueIsReplacedOnlyInDummyColumnNames() throws Exception {
        LoadedForm f = form("<transport/>", "<bind nodeset=\"/data/transport\" type=\"select\"/>",
                "<select ref=\"/data/transport\"><label>Transport</label>"
                + "<item><label>Taxi</label><value>moto-taxi</value></item>"
                + "<item><label>Many</label><value>-a--b-c-</value></item>"
                + "<item><label>Digit</label><value>x2</value></item></select>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Export", "v08",
                field("transport", "select_multiple transport", "transport", 2)), f, "example");
        var s = submission(1, "uuid:abc", "<transport>moto-taxi -a--b-c-</transport>");
        for (String text : List.of(e.wide(List.of(s)), e.longFiles(List.of(s)).get("Export.csv"))) {
            assertEquals(List.of("SubmissionDate", "transport", "transport_moto_taxi", "transport__a__b_c_",
                    "transport_x2", "instanceID", "formdef_version", "KEY"), csv(text).get(0));
            assertEquals("moto-taxi -a--b-c-", row(text, 1).get("transport"));
            assertEquals("1", row(text, 1).get("transport_moto_taxi"));
            assertEquals("1", row(text, 1).get("transport__a__b_c_"));
            assertEquals("0", row(text, 1).get("transport_x2"));
        }
    }
}
