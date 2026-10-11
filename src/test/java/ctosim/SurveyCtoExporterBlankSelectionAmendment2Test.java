package ctosim;

import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterAmendment2Support.*;
import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterBlankSelectionAmendment2Test {
    @TempDir Path temp;

    @Test void blankSelectionZeroesEveryDistinctChoiceEvenWhenFilteredOut() throws Exception {
        var f = form("<value/>", "<bind nodeset=\"/data/value\" type=\"select\"/>",
                "<select ref=\"/data/value\"><label>Choices</label>"
                        + "<itemset nodeset=\"instance('options')/root/item[enabled='yes']\">"
                        + "<value ref=\"name\"/><label ref=\"label\"/></itemset></select>",
                "<instance id=\"options\"><root>"
                        + "<item><name>2</name><label>Two</label><enabled>yes</enabled></item>"
                        + "<item><name>-99</name><label>None</label><enabled>no</enabled></item>"
                        + "<item><name>2</name><label>Duplicate</label><enabled>no</enabled></item>"
                        + "<item><name>moto-taxi</name><label>Taxi</label><enabled>no</enabled></item>"
                        + "</root></instance>");
        var e = new SurveyCtoExporter(catalog(temp, "Export", null,
                field("value", "SeLeCt_MuLtIpLe options", "value", 2)), f, "example");
        for (String text : List.of(e.wide(List.of(value(""))), e.longFiles(List.of(value(""))).get("Export.csv"))) {
            assertEquals(List.of("SubmissionDate", "value", "value_2", "value__99", "value_moto_taxi",
                    "instanceID", "formdef_version", "KEY"), csv(text).get(0));
            assertEquals(List.of("", "0", "0", "0"), csv(text).get(1).subList(1, 5));
        }
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void presentBlankSelectionHasZeroForEveryChoiceAndAnEmptyOwnCell(Layout layout) throws Exception {
        var input = List.of(submission(1, "uuid:a", "<choices/>"));
        var actual = row(main(exporter(), input, layout), 1);
        selection(actual, "choices", "", "", "0", "0");
        assertEquals("0", actual.get("choices_moto_taxi"));
    }

    @ParameterizedTest @ValueSource(strings = {"<choices/>", "<choices></choices>", "<choices><![CDATA[]]></choices>"})
    void equivalentEmptyXmlRepresentationsProduceZeroDummies(String element) throws Exception {
        var e = exporter();
        for (Layout layout : Layout.values()) {
            var actual = row(main(e, List.of(submission(1, "uuid:a", element)), layout), 1);
            selection(actual, "choices", "", "", "0", "0");
            assertEquals("0", actual.get("choices_moto_taxi"));
        }
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentSelectionKeepsAllDummiesEmptyDespiteDefinitionDefault(Layout layout) throws Exception {
        var actual = row(main(exporter(), List.of(submission(1, "uuid:a", "")), layout), 1);
        selection(actual, "choices", "", "", "", "");
        assertEquals("", actual.get("choices_moto_taxi"));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void blankAbsentAndAnsweredSelectionsAreResolvedPerSubmission(Layout layout) throws Exception {
        var inputs = List.of(submission(9, "uuid:blank", "<choices/>"),
                submission(2, "uuid:absent", ""),
                submission(7, "uuid:answered", "<choices>-99 moto-taxi</choices>"),
                submission(1, "uuid:blank-again", "<choices/>"));
        String text = main(exporter(), inputs, layout);
        selection(row(text, 1), "choices", "", "", "0", "0");
        selection(row(text, 2), "choices", "", "", "", "");
        selection(row(text, 3), "choices", "", "-99 moto-taxi", "0", "1");
        assertEquals("1", row(text, 3).get("choices_moto_taxi"));
        selection(row(text, 4), "choices", "", "", "0", "0");
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void missingGroupOrMissingChildDiffersFromPresentEmptyChild(Layout layout) throws Exception {
        var inputs = List.of(submission(1, "uuid:empty", "<g><group_choices/></g>"),
                submission(2, "uuid:child-absent", "<g/>"),
                submission(3, "uuid:group-absent", "<group_choices/>"));
        String text = main(exporter(), inputs, layout);
        selection(row(text, 1), "group_choices", "", "", "0", "0");
        selection(row(text, 2), "group_choices", "", "", "", "");
        selection(row(text, 3), "group_choices", "", "", "", "");
    }

    private List<Submission> repeated() {
        return List.of(submission(1, "uuid:a", "<r><tags/></r><r/><r><tags>-99</tags></r>"),
                submission(2, "uuid:b", "<r><tags/></r>"), submission(3, "uuid:c", ""));
    }

    @Test void wideRepeatDistinguishesBlankFieldFromAbsentFieldAndPaddedInstances() throws Exception {
        String text = exporter().wide(repeated());
        selection(row(text, 1), "tags", "_1", "", "0", "0");
        selection(row(text, 1), "tags", "_2", "", "", "");
        selection(row(text, 1), "tags", "_3", "-99", "0", "1");
        selection(row(text, 2), "tags", "_1", "", "0", "0");
        for (int k = 2; k <= 3; k++) selection(row(text, 2), "tags", "_" + k, "", "", "");
        for (int k = 1; k <= 3; k++) selection(row(text, 3), "tags", "_" + k, "", "", "");
    }

    @Test void longRepeatDistinguishesBlankFromAbsentWithoutInventingPaddedRows() throws Exception {
        String text = exporter().longFiles(repeated()).get("Amendment 2-r.csv");
        assertEquals(5, csv(text).size());
        selection(row(text, 1), "tags", "", "", "0", "0");
        selection(row(text, 2), "tags", "", "", "", "");
        selection(row(text, 3), "tags", "", "-99", "0", "1");
        selection(row(text, 4), "tags", "", "", "0", "0");
    }

    private List<Submission> nested() {
        return List.of(submission(1, "uuid:a", "<r><s><nested_tags/></s><s/></r><r/>"),
                submission(2, "uuid:b", "<r><s><nested_tags>-99</nested_tags></s></r>"));
    }

    @Test void nestedWideBlankDoesNotTurnMissingInnerOrOuterInstancesIntoZeroes() throws Exception {
        String text = exporter().wide(nested());
        selection(row(text, 1), "nested_tags", "_1_1", "", "0", "0");
        selection(row(text, 1), "nested_tags", "_1_2", "", "", "");
        selection(row(text, 2), "nested_tags", "_1_1", "-99", "0", "1");
        selection(row(text, 2), "nested_tags", "_1_2", "", "", "");
        for (int row = 1; row <= 2; row++) for (int k = 1; k <= 2; k++)
            selection(row(text, row), "nested_tags", "_2_" + k, "", "", "");
    }

    @Test void nestedLongBlankAndAbsentCellsUseEachActualInstance() throws Exception {
        String text = exporter().longFiles(nested()).get("Amendment 2-s.csv");
        assertEquals(4, csv(text).size());
        selection(row(text, 1), "nested_tags", "", "", "0", "0");
        selection(row(text, 2), "nested_tags", "", "", "", "");
        selection(row(text, 3), "nested_tags", "", "-99", "0", "1");
    }
}
