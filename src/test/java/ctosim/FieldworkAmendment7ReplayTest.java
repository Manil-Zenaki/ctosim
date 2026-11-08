package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FieldworkAmendment7ReplayTest {
    @ParameterizedTest
    @CsvSource({"string,first,later", "int,-17,23", "long,123456,654321", "decimal,-2.75,8.5",
            "barcode,000123,000456"})
    void identificationKindsKeepTheFirstAnswerInsteadOfTheCurrentOverride(
            String type, String first, String later) throws Exception {
        var rows = three(one(type, "", input("identity")), field("identity", byAttempt(first, later)));
        values(rows, "identity", first, first, first);
        for (var row : rows) assertTrue(row.warnings().isEmpty(), row.warnings().toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectAnswersIncludingMultipleSelectionsAreRepeated(boolean multiple) throws Exception {
        String first = multiple ? "b a" : "b";
        var rows = three(one("string", "", choices("identity", multiple, "b", "a", "c")),
                field("identity", byAttempt(first, "c")));
        assertFalse(value(rows.get(0), "identity").isEmpty());
        repeatsFirst(rows, "identity");
        if (multiple) assertEquals(Set.of("a", "b"), Set.of(value(rows.get(0), "identity").split(" ")));
        else assertEquals("b", value(rows.get(0), "identity"));
    }

    @Test void unicodeLeadingZerosAndLongTextArePreservedExactly() throws Exception {
        String name = "0007 Élodie 李 👩🏾‍🌾 " + "household-".repeat(200);
        values(three(textForm(), field("identity", byAttempt(name, "different"))),
                "identity", name, name, name);
    }

    @ParameterizedTest @ValueSource(strings = {"value", "texts", "expr", "weights", "range"})
    void replayReplacesEveryApplicableOverrideMechanism(String kind) throws Exception {
        String first, later;
        LoadedForm form;
        switch (kind) {
            case "value" -> { first = "\"value\":\"a\""; later = "\"value\":\"b\""; form = textForm(); }
            case "texts" -> { first = "\"texts\":[\"a\"]"; later = "\"texts\":[\"b\"]"; form = textForm(); }
            case "expr" -> { first = expr("concat('visit-', /data/attempt)"); later = first; form = textForm(); }
            case "weights" -> {
                first = "\"weights\":{\"a\":1,\"b\":0}"; later = "\"weights\":{\"a\":0,\"b\":1}";
                form = one("string", "", choices("identity", false, "a", "b"));
            }
            default -> { first = "\"min\":17,\"max\":17"; later = "\"min\":23,\"max\":23";
                form = one("int", "", input("identity")); }
        }
        var rows = three(form, field("identity", first + ",\"when\":[{\"if\":\"/data/attempt > 1\"," + later + "}]"));
        assertEquals(kind.equals("range") ? "17" : kind.equals("expr") ? "visit-1" : "a", value(rows.get(0), "identity"));
        repeatsFirst(rows, "identity");
    }

    @Test void aRecordedNonblankAnswerBypassesTheRevisitsMissingRate() throws Exception {
        String settings = "\"value\":\"first\",\"when\":[{\"if\":\"/data/attempt > 1\",\"missingRate\":1}]";
        values(three(textForm(), field("identity", settings)), "identity", "first", "first", "first");
    }

    @Test void aRecordedBlankStaysBlankWhenTheRevisitWouldDrawANonblankAnswer() throws Exception {
        String settings = "\"value\":\"later\",\"missingRate\":1,\"when\":[{\"if\":\"/data/attempt > 1\",\"missingRate\":0}]";
        values(three(textForm(), field("identity", settings)), "identity", "", "", "");
    }

    @Test void answeredQuestionsInsideOrdinaryNestedGroupsAreIdentification() throws Exception {
        var form = form7("<outer><inner><identity/></inner></outer>", bind("outer/inner/identity", "string", ""),
                "<group ref=\"/data/outer\"><group ref=\"/data/outer/inner\">"
                + input("outer/inner/identity") + "</group></group>", "");
        values(three(form, field("identity", byAttempt("first", "later"))),
                "outer/inner/identity", "first", "first", "first");
    }

    @Test void replayedAnswersAreAvailableToLaterCalculationsAndRelevance() throws Exception {
        var form = form7("<identity/><copy/><detail/>", bind("identity", "string", "")
                + bind("copy", "string", "calculate=\"/data/identity\"")
                + bind("detail", "string", "relevant=\"/data/identity = 'first'\""),
                input("identity"), input("detail"));
        var rows = three(form, field("identity", byAttempt("first", "later")) + ","
                + field("detail", expr("concat('detail-', /data/attempt)")));
        values(rows, "copy", "first", "first", "first");
        values(rows, "detail", "detail-1", "detail-2", "detail-3");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void replayUsesEachHouseholdsFirstVisitAcrossInterleavedEnumerators(boolean found) throws Exception {
        var cfg = config7(36, 3, 2, found ? 2 : 3, found,
                "\"fields\":{" + field("identity", expr("concat(/data/answer, '-visit-', /data/attempt)")) + "}");
        var plan = new Fieldwork(textForm(), cfg);
        var rows = plan.walkAll();
        Map<String, String> originals = new HashMap<>();
        int revisits = 0;
        for (int i = 0; i < rows.size(); i++) {
            var visit = plan.visits().get(i);
            String answer = value(rows.get(i), "identity");
            if (visit.attempt() == 1) {
                assertEquals(visit.id() + "-visit-1", answer);
                originals.put(visit.id(), answer);
            } else {
                revisits++;
                assertEquals(originals.get(visit.id()), answer, "household " + visit.id());
            }
        }
        assertTrue(originals.size() >= 6);
        assertTrue(revisits >= 12, "must exercise interleaved revisits");
    }

    @Test void reusedSurveyIdsDoNotMakeNewHouseholdsInheritOldIdentification() throws Exception {
        var form = form7("<identity/><today/>", bind("identity", "string", "")
                + bind("today", "date", ""), input("today") + input("identity"), "");
        var cfg = config7(6, 1, 1, 2, true, "\"fields\":{"
                + field("answer", "\"value\":7") + "," + field("today", expr("today()"))
                + "," + field("identity", expr("string(/data/today)")) + "}");
        var rows = new Fieldwork(form, cfg).walkAll();
        Set<String> householdNames = new HashSet<>();
        for (int i = 0; i < 6; i += 2) {
            String first = value(rows.get(i), "identity");
            assertFalse(first.isEmpty());
            assertEquals(first, value(rows.get(i + 1), "identity"));
            householdNames.add(first);
        }
        assertEquals(3, householdNames.size(), "three households sharing survey id 7 still have separate histories");
    }

    @Test void identificationIsRetainedWhenNoSurveyIdIsConfigured() throws Exception {
        var cfg = settings("\"submissions\":3,\"enumerators\":1,\"interviewsPerDay\":1,"
                + "\"fieldworkDays\":30," + FieldworkAmendment5Support.visitSettings(0, 1, 3)
                + ",\"fields\":{" + field("identity", byAttempt("first", "later")) + "}");
        var plan = new Fieldwork(textForm(), cfg);
        var rows = plan.walkAll();
        assertEquals(List.of(1, 2, 3), plan.visits().stream().map(Visit::attempt).toList());
        values(rows, "identity", "first", "first", "first");
    }
}
