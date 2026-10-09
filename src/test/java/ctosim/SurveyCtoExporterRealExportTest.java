package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static XML fixtures transcribed from the published fictitious coverage exports, not exporter output. */
class SurveyCtoExporterRealExportTest {
    @TempDir Path temp;

    private SurveyCtoExporter exporter() throws Exception {
        return new SurveyCtoExporter(FieldCatalog.load(Path.of("samples/xforms/ctosim_coverage.fields.json")),
                FormLoader.load(Path.of("samples/xforms/ctosim_coverage.xml")), "example");
    }

    private List<Submission> submissions() throws Exception {
        return List.of(
                Submission.of(1, resource("coverage-submission-1.xml"), LocalDateTime.of(2026, 10, 9, 8, 28, 14), LocalDateTime.of(2026, 10, 9, 8, 32, 49)),
                Submission.of(2, resource("coverage-submission-2.xml"), LocalDateTime.of(2026, 10, 9, 8, 33, 7), LocalDateTime.of(2026, 10, 9, 8, 35, 21)),
                Submission.of(3, resource("coverage-submission-3.xml"), LocalDateTime.of(2026, 10, 9, 8, 35, 40), LocalDateTime.of(2026, 10, 9, 8, 37, 25)));
    }

    @Test void wideCsvIsByteIdenticalToPublishedSurveyCtoExport() throws Exception {
        var e = exporter();
        assertEquals("ctosim coverage test_WIDE.csv", e.wideFileName());
        assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage/wide/ctosim coverage test_WIDE.csv")),
                e.wide(submissions()).getBytes(StandardCharsets.UTF_8));
    }

    @Test void allFourLongFilesAreByteIdenticalAndInCatalogOrder() throws Exception {
        var files = exporter().longFiles(submissions());
        assertEquals(List.of("ctosim coverage test.csv", "ctosim coverage test-main-member.csv",
                "ctosim coverage test-job.csv", "ctosim coverage test-main-plot.csv"), new ArrayList<>(files.keySet()));
        for (var file : files.entrySet()) {
            assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage/long").resolve(file.getKey())),
                    file.getValue().getBytes(StandardCharsets.UTF_8), file.getKey());
        }
    }

    @Test void writtenFilesMatchPublishedExportsWithoutNormalizingLineEndingsOrEncoding() throws Exception {
        var e = exporter();
        e.writeAll(submissions(), temp);
        assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage/wide").resolve(e.wideFileName())), Files.readAllBytes(temp.resolve(e.wideFileName())));
        for (String file : List.of("ctosim coverage test.csv", "ctosim coverage test-main-member.csv",
                "ctosim coverage test-job.csv", "ctosim coverage test-main-plot.csv")) {
            assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage/long").resolve(file)), Files.readAllBytes(temp.resolve(file)), file);
        }
    }
}
