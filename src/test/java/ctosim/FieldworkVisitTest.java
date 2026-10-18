package ctosim;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("FormWalker temporarily changes the JVM time zone")
class FieldworkVisitTest {
    @Test void nullVisitIsRejected() throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        assertThrows(NullPointerException.class, () -> walker.walk(1, null));
    }

    @Test void nullStartIsRejected() throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        // Construct inside the assertion: spec does not locate validation in the record or walker.
        assertThrows(NullPointerException.class, () -> walker.walk(1, new Visit(1, null, 0, 0, null)));
    }

    @ParameterizedTest @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void submissionNumbersBelowOneAreRejected(int number) throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(number, visit(1)));
    }

    @ParameterizedTest @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 4, Integer.MAX_VALUE})
    void enumeratorMustBelongToTheConfiguredRange(int enumerator) throws Exception {
        var walker = new FormWalker(metadata(), settings("\"enumerators\":3"));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(1, visit(enumerator)));
    }

    @ParameterizedTest
    @CsvSource({"-90.000001,0", "90.000001,0", "0,-180.000001", "0,180.000001",
            "Infinity,0", "-Infinity,0", "0,Infinity", "0,-Infinity"})
    void coordinatesOutsideTheSpecifiedIntervalsAreRejected(double lat, double lon) throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(1, new Visit(1, START, lat, lon, null)));
    }

    @ParameterizedTest @CsvSource({"-90,-180", "-90,180", "90,-180", "90,180", "0,0"})
    void coordinateEndpointsAreAccepted(double lat, double lon) throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        assertDoesNotThrow(() -> walker.walk(1, new Visit(1, START, lat, lon, null)));
    }

    @Test void positiveNumberNeedNotBeWithinConfiguredSubmissionCount() throws Exception {
        var walker = new FormWalker(metadata(), settings("\"submissions\":1"));
        assertEquals(Integer.MAX_VALUE, walker.walk(Integer.MAX_VALUE, visit(1)).number());
    }

    @Test void suppliedStartSetsBothTheClockAndTimestampMetadata() throws Exception {
        var config = settings("\"timeZone\":\"+03:00\"");
        Visit visit = new Visit(2, START.minusYears(4).plusSeconds(11), 9, 39, null);
        Submission result = new FormWalker(metadata(), config).walk(7, visit);
        assertEquals(visit.start(), result.start(), "a visit need not fall in the configured period");
        assertEquals(visit.start(), OffsetDateTime.parse(value(result, "started")).toLocalDateTime());
        assertEquals(result.end(), OffsetDateTime.parse(value(result, "finished")).toLocalDateTime());
        assertEquals(3 * 3600, OffsetDateTime.parse(value(result, "started")).getOffset().getTotalSeconds());
        assertElapsed(result, 2);
    }

    @ParameterizedTest @CsvSource({"1,1,enumerator01", "5,5,enumerator05", "120,7,enumerator007",
            "10000,10000,enumerator10000"})
    void visitEnumeratorDeterminesTheUsernameIncludingPadding(int count, int enumerator, String name) throws Exception {
        var walker = new FormWalker(metadata(), settings("\"enumerators\":" + count));
        assertEquals(name, value(walker.walk(1, visit(enumerator)), "username"));
    }

    @Test void deviceIdsDependOnEnumeratorAndSeedRatherThanSubmissionOrVisitTime() throws Exception {
        var walker = new FormWalker(metadata(), settings("\"enumerators\":4"));
        var devices = new HashSet<String>();
        for (int e = 1; e <= 4; e++) {
            String device = value(walker.walk(1, visit(e)), "deviceid");
            assertTrue(device.matches("[0-9a-f]{16}"), device);
            devices.add(device);
            assertEquals(device, value(walker.walk(19, new Visit(e, START, -9, -39, null)), "deviceid"));
        }
        assertEquals(4, devices.size());
    }

    @Test void aVisitKeepsOrdinaryAnswersClockAdvancesAndUploadDrawsForTheSameNumber() throws Exception {
        var walker = new FormWalker(metadata(), settings(""));
        for (int n = 1; n <= 30; n++) {
            Submission ordinary = walker.walk(n);
            Submission planned = walker.walk(n, visit(3));
            assertEquals(value(ordinary, "answer"), value(planned, "answer"));
            assertEquals(value(ordinary, "other"), value(planned, "other"));
            assertEquals(value(ordinary, "meta/instanceID"), value(planned, "meta/instanceID"));
            assertEquals(Duration.between(ordinary.start(), ordinary.end()), Duration.between(planned.start(), planned.end()));
            long upload = wholeMinutes(planned.end(), planned.submitted());
            assertTrue(upload >= 1 && upload <= 240);
            assertEquals(Duration.between(ordinary.end(), ordinary.submitted()), Duration.between(planned.end(), planned.submitted()));
            assertEquals(ordinary.answered(), planned.answered());
            assertEquals(ordinary.warnings(), planned.warnings());
        }
    }

    @Test void visitWalksAreReplayableInAnyOrderAndDoNotChangeLegacyWalks() throws Exception {
        LoadedForm form = metadata();
        var config = settings("");
        var walker = new FormWalker(form, config);
        Submission ordinary = walker.walk(11);
        Submission planned = walker.walk(11, visit(2));
        walker.walk(3, visit(5));
        walker.walk(11, visit(4));
        same(planned, walker.walk(11, visit(2)));
        same(planned, new FormWalker(metadata(), config).walk(11, visit(2)));
        same(ordinary, walker.walk(11));
    }

    @Test void visitIdReplacesAConfiguredValueAndAnExistingDefault() throws Exception {
        LoadedForm form = fixture("<answer>old-default</answer>", bind("answer", "string", "required=\"true()\""), input("answer"));
        var config = settings("\"id\":\"answer\",\"fields\":{\"answer\":{\"value\":\"old-override\",\"missingRate\":1}}");
        var result = new FormWalker(form, config).walk(1, new Visit(1, START, 0, 0, "家庭-é-42"));
        assertEquals("家庭-é-42", value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void theVisitIdAlsoReplacesAnInstanceDefaultWithoutAConfiguredOverride() throws Exception {
        LoadedForm form = fixture("<answer>old-default</answer>", bind("answer", "string", ""), input("answer"));
        var config = settings("\"id\":\"answer\"");
        var result = new FormWalker(form, config).walk(1, new Visit(1, START, 0, 0, "planned-id"));
        assertEquals("planned-id", value(result, "answer"));
    }

    @Test void visitIdAppliesToEveryQuestionInstanceOfTheNameInRepeats() throws Exception {
        var form = fixture("<row jr:template=\"\"><answer/></row>", bind("row/answer", "string", ""),
                "<repeat nodeset=\"/data/row\">" + input("row/answer") + "</repeat>");
        var config = settings("\"id\":\"answer\",\"repeats\":{\"min\":3,\"max\":3}");
        var result = new FormWalker(form, config).walk(1, new Visit(1, START, 0, 0, "same-household"));
        assertEquals(3, count(result, "row"));
        for (int i = 1; i <= 3; i++) assertEquals("same-household", value(result, "row[" + i + "]/answer"));
    }

    @Test void nullIdLeavesTheUsualOverrideInForce() throws Exception {
        var config = settings("\"id\":\"answer\",\"fields\":{\"answer\":{\"value\":\"ordinary\"}}");
        assertEquals("ordinary", value(new FormWalker(question("string", ""), config).walk(1, visit(1)), "answer"));
    }

    @Test void visitIdTargetsEveryQuestionWithTheNameAcrossSeparateGroups() throws Exception {
        var form = fixture("<first><answer/></first><second><answer/></second>",
                bind("first/answer", "string", "") + bind("second/answer", "string", ""),
                "<group ref=\"/data/first\"><label>First</label>" + input("first/answer") + "</group>"
                + "<group ref=\"/data/second\"><label>Second</label>" + input("second/answer") + "</group>");
        var result = new FormWalker(form, settings("\"id\":\"answer\"")).walk(1,
                new Visit(1, START, 0, 0, "shared-id"));
        assertEquals("shared-id", value(result, "first/answer"));
        assertEquals("shared-id", value(result, "second/answer"));
    }

    @Test void nonNullVisitIdHasNoEffectWithoutAConfiguredIdField() throws Exception {
        var config = settings("\"fields\":{\"answer\":{\"value\":\"ordinary\"}}");
        var result = new FormWalker(question("string", ""), config).walk(1, new Visit(1, START, 0, 0, "ignored"));
        assertEquals("ordinary", value(result, "answer"));
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"constraint", "conversion", "choice"})
    void rejectedVisitIdsFallBackToAnOrdinaryAnswerAndWarnWithFieldAndValue(String reason) throws Exception {
        LoadedForm form = reason.equals("choice") ? choices() : question("int", "constraint=\". = 7\"");
        String fallback = reason.equals("choice") ? "b" : "7";
        String rejected = reason.equals("constraint") ? "8" : "not-accepted";
        var config = settings("\"id\":\"answer\",\"fields\":{\"answer\":{\"value\":\"" + fallback + "\"}}");
        var result = new FormWalker(form, config).walk(1, new Visit(1, START, 0, 0, rejected));
        assertEquals(fallback, value(result, "answer"), "normal answering resumes after the visit ID is refused");
        assertWarning(result, "answer", rejected);
    }
}
