package ctosim;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static ctosim.FormWalkerAmendment12Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks temporarily change the JVM default time zone")
class FormWalkerAmendment12ScreenTimeTest {
    @Test void ordinaryWalkSnapshotsAndFinalCalculationsEqualClockAdvances() throws Exception {
        var walker = new FormWalker(timeline(6, "0"), settings(""));
        for (int n = 1; n <= 12; n++) {
            Submission s = walker.walk(n);
            assertEquals(0, integer(s, "s0"));
            assertEquals(s.start(), wall(s, 0));
            long previous = 0;
            for (int i = 1; i <= 6; i++) {
                long current = integer(s, "s" + i);
                assertTrue(current - previous >= 5 && current - previous <= 60);
                assertEquals(s.start().plusSeconds(current), wall(s, i));
                previous = current;
            }
            assertEquals(previous, seconds(s));
            totals(s, previous);
            assertEquals(6, s.answered());
            metadata(s);
        }
    }

    @ParameterizedTest @CsvSource({"60,60", "60.0001,61", "90.1,91", "86400.1,86401"})
    void screenTimeIncludesRoundedMinimumsEvenAcrossMidnight(String minimum, long step) throws Exception {
        Submission s = new FormWalker(timeline(3, minimum), settings(""))
                .walk(1, new Visit(1, LocalDateTime.of(2026, 12, 31, 23, 59, 50), 0, 0, null));
        for (int i = 0; i <= 3; i++) {
            assertEquals(i * step, integer(s, "s" + i));
            assertEquals(s.start().plusSeconds(i * step), wall(s, i));
        }
        totals(s, 3 * step);
        assertEquals(3 * step, seconds(s));
    }

    @ParameterizedTest @ValueSource(strings = {"accepted", "blank", "conversion", "constraint", "search"})
    void eachHandledOutcomeAddsExactlyOneAdvanceToScreenTime(String outcome) throws Exception {
        String attrs = "ct:minimum-seconds=\"70.1\"";
        if (outcome.equals("constraint")) attrs += " constraint=\"false()\"";
        if (outcome.equals("search")) attrs += " constraint=\". mod 7 = 3\"";
        var form = fixture("<q/><snapshot/>", bind("q", "int", attrs) + screenBind("snapshot"),
                input("q") + input("snapshot"));
        String extra = outcome.equals("blank") ? "\"fields\":{\"q\":{\"missingRate\":1}}"
                : "\"fields\":{\"q\":{\"value\":" + (outcome.equals("conversion") ? "\"bad\"" : "7") + "}}";
        Submission s = new FormWalker(form, settings(extra)).walk(1);
        assertEquals(71, integer(s, "snapshot"));
        totals(s, 71);
        assertEquals(71, seconds(s));
        assertEquals(outcome.equals("accepted") || outcome.equals("search") ? 1 : 0, s.answered());
        if (outcome.equals("search")) assertEquals(3, integer(s, "q") % 7);
    }

    @ParameterizedTest @ValueSource(strings = {"readonly", "existing", "irrelevant", "unsupported", "calculateHere", "noControl"})
    void skippedQuestionsAddNoScreenTimeEvenWithAMinimum(String kind) throws Exception {
        String attrs = "ct:minimum-seconds=\"100\"";
        String initial = kind.equals("existing") ? "kept" : "";
        String body = input("q");
        switch (kind) {
            case "readonly" -> attrs += " readonly=\"true()\"";
            case "irrelevant" -> attrs += " relevant=\"false()\"";
            case "calculateHere" -> attrs += " ct:calculate-here=\"'computed'\"";
            case "noControl" -> body = "";
            case "unsupported" -> body = "<rank ref=\"/data/q\"><label>Rank</label>"
                    + "<item><label>A</label><value>a</value></item></rank>";
            default -> { }
        }
        var form = fixture("<q>" + initial + "</q><snapshot/>",
                bind("q", "string", attrs) + screenBind("snapshot"), body + input("snapshot"));
        Submission s = new FormWalker(form, settings("")).walk(1);
        totals(s, 0);
        assertEquals(0, integer(s, "snapshot"));
        assertEquals(s.start(), s.end());
        assertEquals(0, s.answered());
    }

    @Test void screenTimeStartsAtZeroForAFormWithNoQuestions() throws Exception {
        Submission s = new FormWalker(fixture("", "", ""), settings("")).walk(1);
        totals(s, 0);
        assertEquals(s.start(), s.end());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 3})
    void everyConcreteRepeatInstanceContributesToTheSameScreenCounter(int count) throws Exception {
        var form = fixture("<count>" + count + "</count><row jr:template=\"\"><q/><snapshot/><final/></row>",
                bind("count", "int", "") + bind("row/q", "int", "ct:minimum-seconds=\"60.1\"")
                + screenBind("row/snapshot") + bind("row/final", "int", "calculate=\"duration()\""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/count\">"
                + input("row/q") + input("row/snapshot") + "</repeat>");
        Submission s = new FormWalker(form, settings("")).walk(1);
        assertEquals(count, count(s, "row"));
        for (int i = 1; i <= count; i++) {
            assertEquals(i * 61L, integer(s, "row[" + i + "]/snapshot"));
            assertEquals(count * 61L, integer(s, "row[" + i + "]/final"));
        }
        totals(s, count * 61L);
    }

    @Test void screenCounterResetsForEveryWalkRegardlessOfCallOrder() throws Exception {
        var form = timeline(4, "90");
        var config = settings("");
        var walker = new FormWalker(form, config);
        Submission first = walker.walk(7);
        walker.walk(2);
        walker.walk(99);
        Submission replay = walker.walk(7);
        assertSameSubmission(first, replay);
        assertEquals(first.submitted(), replay.submitted());
        assertSameSubmission(first, new FormWalker(timeline(4, "90"), config).walk(7));
        totals(replay, 360);
        assertEquals(0, integer(replay, "s0"));
    }
}
