package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.SurveyCtoExporterAmendment2Support.Layout;
import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Compare only Amendment 2 columns to the published third export. */
class SurveyCtoExporterThirdExportAmendment2Test {
    private static final String TITLE = "ctosim coverage test 3";

    private SurveyCtoExporter exporter() throws Exception {
        return new SurveyCtoExporter(FieldCatalog.load(Path.of("samples/xforms/ctosim_coverage3.fields.json")),
                FormLoader.load(Path.of("samples/xforms/ctosim_coverage3.xml")), "example");
    }

    private Submission input(int number, String id, String name, String comments) throws Exception {
        return Submission.of(number, resource("coverage3-amendment2-instance.xml")
                .replace("__ID__", id).replace("__NAME__", name).replace("__COMMENTS__", comments), START, END);
    }

    private List<Submission> inputs() throws Exception {
        return List.of(
                input(1, "uuid:655c3cef-d3ce-4b4d-be7f-0a56943af7c6", "C3-B", "Comments-655c3cef-d3ce-4b4d-be7f-0a56943af7c6.csv"),
                input(2, "uuid:8ff7d354-2e94-4892-8097-ab182e12fbce", "C3-A", "Comments-8ff7d354-2e94-4892-8097-ab182e12fbce.csv"),
                input(3, "uuid:7a102182-7299-46f6-97f3-6963d20ddcde", "C3-C", ""),
                input(4, "uuid:fdd91423-b485-45d9-8e67-658e621b1935", "C3-D", ""));
    }

    private String actual(Layout layout) throws Exception {
        var e = exporter();
        return layout == Layout.WIDE ? e.wide(inputs()) : e.longFiles(inputs()).get(TITLE + ".csv");
    }

    private String published(Layout layout) throws Exception {
        return Files.readString(Path.of("samples/coverage3", layout == Layout.WIDE ? "wide" : "long",
                TITLE + (layout == Layout.WIDE ? "_WIDE.csv" : ".csv")));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void allTwelveBlankChoiceDummiesMatchTheThirdPublishedExport(Layout layout) throws Exception {
        String actual = actual(layout);
        String expected = published(layout);
        assertEquals(5, csv(actual).size());
        for (int i = 1; i <= 4; i++) {
            assertEquals(row(expected, i).get("m_blank"), row(actual, i).get("m_blank"));
            for (int choice = 1; choice <= 12; choice++) {
                String column = "m_blank_" + choice;
                assertEquals(row(expected, i).get(column), row(actual, i).get(column), "row " + i + ", " + column);
            }
        }
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void trimmedNamesMatchTheThirdPublishedExport(Layout layout) throws Exception {
        String actual = actual(layout);
        String expected = published(layout);
        for (int i = 1; i <= 4; i++)
            assertEquals(row(expected, i).get("instanceName"), row(actual, i).get("instanceName"), "row " + i);
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void commentsAttachmentsAndEmptyCommentsMatchTheThirdPublishedExport(Layout layout) throws Exception {
        String actual = actual(layout);
        String expected = published(layout);
        for (int i = 1; i <= 4; i++)
            assertEquals(row(expected, i).get("field_comments"), row(actual, i).get("field_comments"), "row " + i);
    }
}
