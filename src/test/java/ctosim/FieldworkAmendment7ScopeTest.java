package ctosim;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FieldworkAmendment7ScopeTest {
    @Test void theBoundaryUsesWalkOrderRatherThanInstanceOrBindOrder() throws Exception {
        var form = form7("<after/><before/>", bind("after", "string", "") + bind("before", "string", ""),
                input("before"), input("after"));
        String changing = expr("concat('visit-', /data/attempt)");
        var rows = three(form, field("before", changing) + "," + field("after", changing));
        values(rows, "before", "visit-1", "visit-1", "visit-1");
        values(rows, "after", "visit-1", "visit-2", "visit-3");
    }

    @Test void availabilityAndVisitNumberAreNotReplayedEvenWhenTheNumberIsBeforeAvailability() throws Exception {
        var plan = new Fieldwork(textForm(), config7(2, 1, 1, 2, true,
                "\"fields\":{" + field("identity", byAttempt("first", "later")) + "}"));
        var rows = plan.walkAll();
        values(rows, "identity", "first", "first");
        values(rows, "attempt", "1", "2");
        values(rows, "status", "absent", "yes");
    }

    @Test void questionsAskedOnlyOnRevisitsUseTheirCurrentOverride() throws Exception {
        var form = one("string", "relevant=\"/data/attempt > 1\"", input("identity"));
        values(three(form, field("identity", expr("concat('visit-', /data/attempt)"))),
                "identity", "", "visit-2", "visit-3");
    }

    @Test void replayDoesNotForceAQuestionThatIsIrrelevantOnTheRevisit() throws Exception {
        var form = one("string", "relevant=\"/data/attempt = 1\"", input("identity"));
        values(three(form, field("identity", byAttempt("first", "later"))), "identity", "first", "", "");
    }

    @Test void aQuestionSkippedOnOneRevisitStillRecoversTheFirstAnswerOnALaterRevisit() throws Exception {
        var form = one("string", "relevant=\"/data/attempt != 2\"", input("identity"));
        values(three(form, field("identity", byAttempt("first", "later"))), "identity", "first", "", "first");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void questionsInsideRepeatsIncludingNestedGroupsAreAnsweredAsUsual(boolean nested) throws Exception {
        String path = nested ? "row/group/identity" : "row/identity";
        String contents = nested ? "<group><identity/></group>" : "<identity/>";
        String body = nested ? "<group ref=\"/data/row/group\">" + input(path) + "</group>" : input(path);
        var form = form7("<size>2</size><row jr:template=\"\">" + contents + "</row>",
                bind("size", "int", "") + bind(path, "string", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + body + "</repeat>", "");
        var rows = three(form, field("identity", expr("concat('visit-', /data/attempt)")));
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(2, count(rows.get(i), "row"));
            for (int j = 1; j <= 2; j++) assertEquals("visit-" + (i + 1),
                    value(rows.get(i), path.replace("row/", "row[" + j + "]/")));
        }
    }

    @Test void repeatExclusionDoesNotExcludeAQuestionFollowingTheRepeat() throws Exception {
        var form = form7("<size>1</size><row jr:template=\"\"><member/></row><identity/>",
                bind("size", "int", "") + bind("row/member", "string", "") + bind("identity", "string", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/member")
                + "</repeat>" + input("identity"), "");
        String changing = expr("concat('visit-', /data/attempt)");
        var rows = three(form, field("member", changing) + "," + field("identity", changing));
        values(rows, "row[1]/member", "visit-1", "visit-2", "visit-3");
        values(rows, "identity", "visit-1", "visit-1", "visit-1");
    }

    static Stream<Arguments> excludedTypes() {
        return Stream.of(
                Arguments.of("date", "2001-01-01", "2002-02-02", ""),
                Arguments.of("time", "01:02:03", "04:05:06", ""),
                Arguments.of("dateTime", "2001-01-01T01:02:03Z", "2002-02-02T04:05:06Z", ""),
                Arguments.of("geopoint", "1 2 0 5", "3 4 0 5", ""),
                Arguments.of("geotrace", "1 2 0 5;1.001 2.001 0 5", "3 4 0 5;3.001 4.001 0 5", ""),
                Arguments.of("geoshape", "1 2 0 5;1.001 2 0 5;1 2.001 0 5;1 2 0 5",
                        "3 4 0 5;3.001 4 0 5;3 4.001 0 5;3 4 0 5", ""),
                Arguments.of("binary", "first.jpg", "later.jpg", "image/*"),
                Arguments.of("binary", "first.mp3", "later.mp3", "audio/*"),
                Arguments.of("binary", "first.mp4", "later.mp4", "video/*"),
                Arguments.of("binary", "first.pdf", "later.pdf", "application/pdf"));
    }

    @ParameterizedTest @MethodSource("excludedTypes")
    void eachExcludedTypeUsesItsOrdinaryCurrentAnswer(String type, String first, String later, String media) throws Exception {
        String control = media.isEmpty() ? input("identity")
                : "<upload ref=\"/data/identity\" mediatype=\"" + media + "\"><label>Attachment</label></upload>";
        var form = one(type, "", control);
        var cfg = config7(field("identity", byAttempt(first, later)));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        var walker = new FormWalker(form, cfg);
        for (int i = 0; i < rows.size(); i++) {
            var ordinary = walker.walk(i + 1, plan.visits().get(i));
            assertFalse(value(ordinary, "identity").isEmpty(), "the ordinary excluded answer must be accepted");
            assertEquals(value(ordinary, "identity"), value(rows.get(i), "identity"));
        }
        assertNotEquals(value(rows.get(0), "identity"), value(rows.get(1), "identity"),
                "different fixed values expose accidental replay");
    }

    @Test void sameNamedQuestionsInSeparateGroupsRetainTheirOwnFirstAnswers() throws Exception {
        var form = form7("<left><marker>left</marker><identity/></left><right><marker>right</marker><identity/></right>",
                bind("left/marker", "string", "") + bind("right/marker", "string", "")
                + bind("left/identity", "string", "") + bind("right/identity", "string", ""),
                "<group ref=\"/data/left\">" + input("left/identity") + "</group>"
                + "<group ref=\"/data/right\">" + input("right/identity") + "</group>", "");
        var rows = three(form, field("identity", expr("concat(../marker, '-', /data/attempt)")));
        values(rows, "left/identity", "left-1", "left-1", "left-1");
        values(rows, "right/identity", "right-1", "right-1", "right-1");
    }

    @Test void aSecondAvailabilityQuestionDoesNotExtendTheIdentificationBoundary() throws Exception {
        var form = form7("<middle/><again><status/></again>", bind("middle", "string", "")
                + bind("again/status", "string", ""), "", input("middle")
                + "<group ref=\"/data/again\">" + choices("again/status", false, "yes", "absent") + "</group>");
        values(three(form, field("middle", expr("concat('visit-', /data/attempt)"))),
                "middle", "visit-1", "visit-2", "visit-3");
    }

    @Test void anEarlierSkippedAvailabilityQuestionDoesNotEndTheRecordedPrefix() throws Exception {
        var form = form7("<skipped><status/></skipped><identity/>",
                bind("skipped/status", "string", "relevant=\"false()\"") + bind("identity", "string", ""),
                "<group ref=\"/data/skipped\">" + choices("skipped/status", false, "yes", "absent")
                + "</group>" + input("identity"), "");
        values(three(form, field("identity", byAttempt("first", "later"))), "identity", "first", "first", "first");
    }

    @Test void anEmptyIdentificationPrefixStillAllowsNormalRevisits() throws Exception {
        var form = form7("<later/>", bind("later", "string", ""), "", input("later"));
        var rows = three(form, field("later", expr("concat('visit-', /data/attempt)")));
        values(rows, "later", "visit-1", "visit-2", "visit-3");
        values(rows, "attempt", "1", "2", "3");
    }

    @Test void standaloneWalksDoNotRememberIdentificationFromEarlierCallsOrThePlan() throws Exception {
        var form = textForm();
        var cfg = config7(field("identity", expr("concat('visit-', /data/attempt)")));
        var plan = new Fieldwork(form, cfg);
        plan.walkAll();
        var walker = new FormWalker(form, cfg);
        for (int i : List.of(2, 0, 1, 2)) {
            var row = walker.walk(i + 1, plan.visits().get(i));
            assertEquals("visit-" + (i + 1), value(row, "identity"));
        }
    }

    @ParameterizedTest @ValueSource(ints = {1, 12})
    void firstVisitsUseTheOrdinaryWalkerEvenWhenVisitConfigurationIsPresent(int n) throws Exception {
        var form = textForm();
        var cfg = FieldworkAmendment5Support.config5(n, 2, 2, 0, 0, 3, "");
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        var walker = new FormWalker(form, cfg);
        for (int i = 0; i < rows.size(); i++) {
            assertEquals(1, plan.visits().get(i).attempt());
            same(walker.walk(i + 1, plan.visits().get(i)), rows.get(i));
        }
    }

    @Test void staticSurveyIdAfterAvailabilityStillUsesTheHouseholdsId() throws Exception {
        var form = form("<attempt/><identity/><status/><answer/>", bind("attempt", "int", "")
                + bind("identity", "string", "") + bind("status", "string", "") + bind("answer", "int", ""),
                input("attempt") + input("identity") + choices("status", false, "yes", "absent") + input("answer"));
        var plan = new Fieldwork(form, config7(field("identity", byAttempt("first", "later"))));
        var rows = plan.walkAll();
        for (int i = 0; i < rows.size(); i++) assertEquals(plan.visits().get(0).id(), value(rows.get(i), "answer"));
        values(rows, "identity", "first", "first", "first");
    }

    @Test void withoutVisitConfigurationEveryPlannedRowMatchesItsStandaloneWalk() throws Exception {
        var form = textForm();
        var cfg = settings("\"submissions\":12,\"enumerators\":2,\"interviewsPerDay\":2,\"id\":\"answer\"");
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        var walker = new FormWalker(form, cfg);
        for (int i = 0; i < rows.size(); i++) same(walker.walk(i + 1, plan.visits().get(i)), rows.get(i));
    }
}
