package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.SurveyCtoExporterAmendment2Support.*;
import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterOutputAmendment2Test {
    @TempDir Path temp;

    private List<Submission> inputs() {
        return List.of(named(1, "uuid:a", "<value>Élodie 東京</value><choices/>"
                + "<comments>Comments-a.csv</comments><r><tags/><repeat_comments>r.csv</repeat_comments>"
                + "<s><nested_tags/><nested_comments>s.csv</nested_comments></s></r>",
                "<instanceName>C3-A   </instanceName>"));
    }

    @Test void writeAllPersistsTheAmendedValuesInWideMainAndBothRepeatFiles() throws Exception {
        var e = exporter();
        var input = inputs();
        Path output = temp.resolve("export/nested");
        e.writeAll(input, output);
        String wide = Files.readString(output.resolve("Amendment 2_WIDE.csv"));
        String main = Files.readString(output.resolve("Amendment 2.csv"));
        for (String text : List.of(wide, main)) {
            var actual = row(text, 1);
            selection(actual, "choices", "", "", "0", "0");
            assertEquals("C3-A", actual.get("instanceName"));
            assertEquals(BASE + "uuid%3Aa&file=Comments-a.csv", actual.get("comments"));
            assertEquals("Élodie 東京", actual.get("value"));
        }
        selection(row(wide, 1), "tags", "_1", "", "0", "0");
        selection(row(wide, 1), "nested_tags", "_1_1", "", "0", "0");
        var repeat = row(Files.readString(output.resolve("Amendment 2-r.csv")), 1);
        selection(repeat, "tags", "", "", "0", "0");
        assertEquals(BASE + "uuid%3Aa&file=r.csv", repeat.get("repeat_comments"));
        var nested = row(Files.readString(output.resolve("Amendment 2-s.csv")), 1);
        selection(nested, "nested_tags", "", "", "0", "0");
        assertEquals(BASE + "uuid%3Aa&file=s.csv", nested.get("nested_comments"));
        assertArrayEquals(e.wide(input).getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output.resolve(e.wideFileName())));
        for (var file : e.longFiles(input).entrySet())
            assertArrayEquals(file.getValue().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(output.resolve(file.getKey())));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void repeatedExportsAndFreshExportersProduceIdenticalAmendedText(Layout layout) throws Exception {
        var e = exporter();
        String expected = main(e, inputs(), layout);
        // Interleave contradictory presence states to detect leaked caches between exports.
        main(e, List.of(submission(2, "uuid:b", "")), layout);
        assertEquals(expected, main(e, inputs(), layout));
        assertEquals(expected, main(exporter(), inputs(), layout));
    }

    @Test void writingAbsentFieldsAfterBlankFieldsClearsPreviousZeroesAndAttachmentUrls() throws Exception {
        var e = exporter();
        e.writeAll(inputs(), temp);
        e.writeAll(List.of(submission(2, "uuid:b", "")), temp);
        for (String filename : List.of("Amendment 2_WIDE.csv", "Amendment 2.csv")) {
            String text = Files.readString(temp.resolve(filename));
            assertEquals(2, csv(text).size());
            var actual = row(text, 1);
            selection(actual, "choices", "", "", "", "");
            assertEquals("", actual.get("comments"));
            assertEquals("", actual.get("instanceName"));
        }
        assertEquals(1, csv(Files.readString(temp.resolve("Amendment 2-r.csv"))).size());
        assertEquals(1, csv(Files.readString(temp.resolve("Amendment 2-s.csv"))).size());
    }
}
