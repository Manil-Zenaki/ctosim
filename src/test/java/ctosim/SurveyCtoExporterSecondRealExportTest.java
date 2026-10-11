package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent static instances transcribed from coverage2, never from exporter output. */
class SurveyCtoExporterSecondRealExportTest {
    @TempDir Path temp;
    static final String TITLE = "ctosim coverage test 2";
    static final List<String> LONG_NAMES = List.of(TITLE + ".csv", TITLE + "-main-member.csv",
            TITLE + "-child.csv", TITLE + "-job.csv", TITLE + "-main-plot.csv");

    static SurveyCtoExporter exporter() throws Exception {
        return new SurveyCtoExporter(FieldCatalog.load(Path.of("samples/xforms/ctosim_coverage2.fields.json")),
                FormLoader.load(Path.of("samples/xforms/ctosim_coverage2.xml")), "example");
    }

    static List<Submission> submissions() throws Exception {
        return List.of(
                Submission.of(1, resource("coverage2-submission-1.xml"),
                        LocalDateTime.of(2026, 10, 10, 23, 25, 9), LocalDateTime.of(2026, 10, 10, 23, 25, 31), LocalDateTime.of(2026, 10, 10, 23, 32, 50)),
                Submission.of(2, resource("coverage2-submission-2.xml"),
                        LocalDateTime.of(2026, 10, 10, 23, 25, 39), LocalDateTime.of(2026, 10, 10, 23, 31, 29), LocalDateTime.of(2026, 10, 10, 23, 32, 51)),
                Submission.of(3, resource("coverage2-submission-3.xml"),
                        LocalDateTime.of(2026, 10, 10, 23, 31, 37), LocalDateTime.of(2026, 10, 10, 23, 32, 39), LocalDateTime.of(2026, 10, 10, 23, 32, 51)));
    }

    @Test void wideIsByteIdenticalToSecondPublishedExport() throws Exception {
        var e = exporter();
        assertEquals(TITLE + "_WIDE.csv", e.wideFileName());
        assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage2/wide", TITLE + "_WIDE.csv")),
                e.wide(submissions()).getBytes(StandardCharsets.UTF_8));
    }

    @Test void longFileNamesAndIterationOrderIncludeCountedChildBeforeJob() throws Exception {
        assertEquals(LONG_NAMES, new ArrayList<>(exporter().longFiles(submissions()).keySet()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "-main-member", "-child", "-job", "-main-plot"})
    void eachLongFileIsByteIdenticalToSecondPublishedExport(String suffix) throws Exception {
        String name = TITLE + suffix + ".csv";
        String actual = exporter().longFiles(submissions()).get(name);
        assertNotNull(actual, name);
        assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage2/long", name)),
                actual.getBytes(StandardCharsets.UTF_8), name);
    }

    @Test void writeAllMatchesAllSixPublishedFilesWithoutNormalizingBytes() throws Exception {
        exporter().writeAll(submissions(), temp);
        try (var files = Files.list(temp)) {
            assertEquals(6, files.count());
        }
        assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage2/wide", TITLE + "_WIDE.csv")),
                Files.readAllBytes(temp.resolve(TITLE + "_WIDE.csv")));
        for (String name : LONG_NAMES) {
            assertArrayEquals(Files.readAllBytes(Path.of("samples/coverage2/long", name)),
                    Files.readAllBytes(temp.resolve(name)), name);
        }
    }
}
