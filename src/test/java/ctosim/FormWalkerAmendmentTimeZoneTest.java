package ctosim;

import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendmentTimeZoneTest {
    private LoadedForm timestamps() throws Exception {
        return amendedForm(METADATA + "<opened/><datetime_answer/><time_answer/><last_now/>",
                METADATA_BINDS + bind("opened", "dateTime", "calculate=\"once(now())\"")
                + bind("datetime_answer", "dateTime", "") + bind("time_answer", "time", "")
                + here("last_now", "dateTime", "now()", "readonly=\"true()\""),
                input("datetime_answer") + input("time_answer") + input("last_now"));
    }

    @ParameterizedTest @ValueSource(strings = {"UTC", "Z", "+03:00", "-04:30", "+05:45", "+14:00", "-12:00"})
    void storedTimestampsAndGeneratedAnswersUseConfiguredOffset(String zone) throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            Submission result = walk(timestamps(), "\"timeZone\":\"" + zone + "\",\"missingRate\":0");
            ZoneOffset expected = zone.equals("UTC") ? ZoneOffset.UTC : ZoneOffset.of(zone);
            for (String path : new String[]{"started", "finished", "datetime_answer"}) {
                assertEquals(expected, OffsetDateTime.parse(value(result, path)).getOffset(), path);
            }
            assertEquals(expected, OffsetTime.parse(value(result, "time_answer")).getOffset());
            assertEquals(result.start(), OffsetDateTime.parse(value(result, "started")).toLocalDateTime());
            assertEquals(result.start().toLocalDate().toString(), value(result, "opened"));
            assertEquals(result.end(), OffsetDateTime.parse(value(result, "finished")).toLocalDateTime());
            assertEquals(result.end().toLocalDate().toString(), value(result, "last_now"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest @CsvSource({
        "America/New_York,2026-01-05T08:00:00,-05:00",
        "America/New_York,2026-07-05T08:00:00,-04:00",
        "Africa/Addis_Ababa,2026-01-05T08:00:00,+03:00",
        "Asia/Kathmandu,2026-01-05T08:00:00,+05:45"
    })
    void regionOffsetsFollowTheSimulatedDate(String zone, String start, String offset) throws Exception {
        Submission result = walk(timestamps(), "\"timeZone\":\"" + zone + "\",\"start\":\"" + start
                + "\",\"fieldworkDays\":1,\"missingRate\":0");
        for (String path : new String[]{"started", "finished"}) {
            assertEquals(ZoneOffset.of(offset), OffsetDateTime.parse(value(result, path)).getOffset(), path);
        }
        assertEquals(result.start().toLocalDate().toString(), value(result, "opened"));
        assertEquals(result.end().toLocalDate().toString(), value(result, "last_now"));
    }

    @Test void completeSubmissionIsIndependentOfTheMachinesDefaultZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            var settings = config("\"seed\":937,\"timeZone\":\"+05:45\",\"missingRate\":0");
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            Submission first = new FormWalker(timestamps(), settings).walk(29);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var walker = new FormWalker(timestamps(), settings);
            walker.walk(2);
            assertSameSubmission(first, walker.walk(29));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test void omittedZoneUsesUtcEvenOnANonUtcMachine() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            Submission result = new FormWalker(timestamps(), SimulationConfig.defaults()).walk(1);
            assertEquals(ZoneOffset.UTC, OffsetDateTime.parse(value(result, "started")).getOffset());
            assertEquals(ZoneOffset.UTC, OffsetDateTime.parse(value(result, "finished")).getOffset());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test void normalReturnRestoresThePreviousZoneIncludingCustomRules() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone sentinel = new SimpleTimeZone(123 * 60_000, "test-custom-default");
        try {
            TimeZone.setDefault(sentinel);
            walk(timestamps(), "\"timeZone\":\"+03:00\",\"missingRate\":0");
            assertEquals(sentinel, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test void rejectedSubmissionNumberRestoresThePreviousZone() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone sentinel = TimeZone.getTimeZone("Asia/Kathmandu");
        try {
            var walker = new FormWalker(timestamps(), config("\"timeZone\":\"-04:00\""));
            TimeZone.setDefault(sentinel);
            assertThrows(IllegalArgumentException.class, () -> walker.walk(0));
            assertEquals(sentinel, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test @Timeout(120)
    void failureAfterSessionOpeningAlsoRestoresThePreviousZone() throws Exception {
        // A guaranteed public-contract exception inside a walk, not just input validation.
        LoadedForm form = amendedForm("<row jr:template=\"\"><note>kept</note></row>",
                bind("row/note", "string", "readonly=\"true()\""),
                "<repeat nodeset=\"/data/row\">" + input("row/note") + "</repeat>");
        var walker = new FormWalker(form, config("\"timeZone\":\"+03:00\","
                + "\"fields\":{\"row\":{\"min\":200001,\"max\":200001}}"));
        TimeZone original = TimeZone.getDefault();
        TimeZone sentinel = TimeZone.getTimeZone("America/Los_Angeles");
        try {
            TimeZone.setDefault(sentinel);
            assertThrows(IllegalStateException.class, () -> walker.walk(731));
            assertEquals(sentinel, TimeZone.getDefault());
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
