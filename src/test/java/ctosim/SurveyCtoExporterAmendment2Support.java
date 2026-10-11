package ctosim;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Shared fixtures for Amendment 2 only; no production implementation is consulted. */
final class SurveyCtoExporterAmendment2Support {
    enum Layout { WIDE, LONG }

    private SurveyCtoExporterAmendment2Support() { }

    static SurveyCtoExporter exporter() throws Exception {
        var url = SurveyCtoExporterAmendment2Support.class.getResource(
                "/ctosim/surveycto-exporter/amendment2.fields.json");
        assertNotNull(url);
        return new SurveyCtoExporter(FieldCatalog.load(Path.of(url.toURI())),
                load(resource("amendment2.xml")), "example");
    }

    static Submission named(int number, String id, String content, String nameElement) {
        return Submission.of(number, "<data id=\"export_test\">" + content
                + "<meta><instanceID>" + xmlText(id) + "</instanceID>" + nameElement
                + "</meta></data>", START, END);
    }

    static String main(SurveyCtoExporter e, List<Submission> input, Layout layout) {
        return layout == Layout.WIDE ? e.wide(input) : e.longFiles(input).get("Amendment 2.csv");
    }

    static void selection(Map<String, String> row, String name, String suffix,
                          String stored, String one, String other) {
        assertAll(name + suffix,
                () -> assertEquals(stored, row.get(name + suffix), "Own column"),
                () -> assertEquals(one, row.get(name + "_1" + suffix), "Choice 1"),
                () -> assertEquals(other, row.get(name + "__99" + suffix), "Choice -99"));
    }
}
