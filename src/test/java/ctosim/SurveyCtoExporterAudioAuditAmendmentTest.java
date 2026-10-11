package ctosim;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterAudioAuditAmendmentTest {
    @TempDir Path temp;
    private static final String AUDIO_URL = BASE + "uuid%3Aabc&file=AA_abc_AFTER_0S.m4a";

    @ParameterizedTest @ValueSource(strings = {"", "ignored.wav", "TA_wrong.csv", "x, \"y\" & \u00e9"})
    void presentAudioAuditGeneratesAnAaFilenameRegardlessOfStoredValue(String stored) throws Exception {
        assertValueBoth(single(temp, "audio audit"), value(stored), AUDIO_URL);
    }

    @Test void absentAudioAuditHasAnEmptyCell() throws Exception {
        assertValueBoth(single(temp, "audio audit"), submission(1, "uuid:abc", ""), "");
    }

    @Test void selfClosingAudioAuditStillGeneratesAnAttachmentUrl() throws Exception {
        assertValueBoth(single(temp, "audio audit"), submission(1, "uuid:abc", "<value/>"), AUDIO_URL);
    }

    @ParameterizedTest @ValueSource(strings = {"audio audit", "AuDiO audit", "AUDIO AUDIT"})
    void audioAuditRecognitionRetainsCaseInsensitiveTypeComparison(String type) throws Exception {
        assertValueBoth(single(temp, type), value(""), AUDIO_URL);
    }

    @ParameterizedTest @CsvSource(delimiter = '|', value = {
            "abc|abc|AA_abc_AFTER_0S.m4a",
            "uuid:abc|uuid%3Aabc|AA_abc_AFTER_0S.m4a",
            "uuid:uuid:abc|uuid%3Auuid%3Aabc|AA_uuid%3Aabc_AFTER_0S.m4a",
            "xuuid:abc|xuuid%3Aabc|AA_xuuid%3Aabc_AFTER_0S.m4a",
            "uuid:\u00e9 /+&?|uuid%3A%C3%A9+%2F%2B%26%3F|AA_%C3%A9+%2F%2B%26%3F_AFTER_0S.m4a"})
    void audioAuditStripsOnlyLeadingUuidPrefixAndFormEncodesBothUrlParameters(String id, String encodedId, String encodedFile) throws Exception {
        assertValueBoth(single(temp, "audio audit"), submission(1, id, "<value/>"), BASE + encodedId + "&file=" + encodedFile);
    }

    @Test void regularAudioStillUsesItsStoredFilename() throws Exception {
        assertValueBoth(single(temp, "audio"), value("spoken answer.m4a"),
                BASE + "uuid%3Aabc&file=spoken+answer.m4a");
    }

    @Test void textAuditKeepsItsTaFilename() throws Exception {
        assertValueBoth(single(temp, "text audit"), value(""), BASE + "uuid%3Aabc&file=TA_abc.csv");
    }

    @Test void presenceAndIdentifierAreResolvedSeparatelyForEachSubmission() throws Exception {
        var e = single(temp, "audio audit");
        var input = List.of(submission(1, "uuid:abc", "<value/>"), submission(2, "uuid:missing", ""),
                submission(3, "uuid:xyz", "<value>ignored</value>"));
        for (String text : List.of(e.wide(input), e.longFiles(input).get("Export.csv"))) {
            assertEquals(AUDIO_URL, row(text, 1).get("value"));
            assertEquals("", row(text, 2).get("value"));
            assertEquals(BASE + "uuid%3Axyz&file=AA_xyz_AFTER_0S.m4a", row(text, 3).get("value"));
        }
    }

    @Test void audioAuditUsesTheConfiguredServer() throws Exception {
        var e = new SurveyCtoExporter(catalog(temp, "Export", "v08", field("value", "audio audit", "value", 2)),
                simpleForm(), "Test-42");
        assertValueBoth(e, value(""), "https://Test-42.surveycto.com/view/submission-attachment/?uuid=uuid%3Aabc&file=AA_abc_AFTER_0S.m4a");
    }

    @Test void auditInsideAnAbsentGroupIsEmptyAndIgnoresSameNamedElementsElsewhere() throws Exception {
        LoadedForm f = form("<g><value/></g><value/>", "<bind nodeset=\"/data/g/value\" type=\"string\"/>",
                "<group ref=\"/data/g\"><label>Group</label><input ref=\"value\"><label>Audit</label></input></group>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Export", "v08",
                field("g", "begin group", "g", 2), field("value", "audio audit", "g/value", 3)), f, "example");
        assertValueBoth(e, submission(1, "uuid:abc", "<value>decoy</value>"), "");
        assertValueBoth(e, submission(1, "uuid:abc", "<g/><value>decoy</value>"), "");
        assertValueBoth(e, submission(1, "uuid:abc", "<g><value/></g>"), AUDIO_URL);
    }

    @Test void repeatedAudioAuditUsesSubmissionIdAndChecksPresenceInEachInstance() throws Exception {
        LoadedForm f = form("<r jr:template=\"\"><value/></r>", "<bind nodeset=\"/data/r/value\" type=\"string\"/>",
                "<group><label>Repeats</label><repeat nodeset=\"/data/r\"><input ref=\"value\"><label>Audit</label></input></repeat></group>", "");
        var e = new SurveyCtoExporter(catalog(temp, "Export", "v08",
                field("r", "begin repeat", "r", 2), field("value", "audio audit", "r/value", 3)), f, "example");
        var input = List.of(submission(1, "uuid:abc", "<r><value/></r><r/><r><value>ignored.wav</value></r>"),
                submission(2, "uuid:xyz", "<r><value/></r>"));
        var first = row(e.wide(input), 1);
        assertEquals(AUDIO_URL, first.get("value_1"));
        assertEquals("", first.get("value_2"));
        assertEquals(AUDIO_URL, first.get("value_3"));
        var second = row(e.wide(input), 2);
        assertEquals(BASE + "uuid%3Axyz&file=AA_xyz_AFTER_0S.m4a", second.get("value_1"));
        assertEquals("", second.get("value_2"));
        assertEquals("", second.get("value_3"));
        String repeats = e.longFiles(input).get("Export-r.csv");
        assertEquals(AUDIO_URL, row(repeats, 1).get("value"));
        assertEquals("", row(repeats, 2).get("value"));
        assertEquals(AUDIO_URL, row(repeats, 3).get("value"));
        assertEquals(BASE + "uuid%3Axyz&file=AA_xyz_AFTER_0S.m4a", row(repeats, 4).get("value"));
    }
}
