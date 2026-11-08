package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment7Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FieldworkAmendment7ProtectionTest {
    private static ProblemRates problems(double outlier, String... faults) {
        return new ProblemRates(0, 0, 0, 0, 0, 0, 0, outlier, List.of(faults));
    }

    private static void assertReplayedAndUnrecorded(Fieldwork plan, List<Submission> rows, String field) throws Exception {
        Map<String, String> first = new HashMap<>();
        int revisits = 0;
        for (int i = 0; i < rows.size(); i++) {
            var v = plan.visits().get(i);
            if (v.attempt() == 1) first.put(v.id(), value(rows.get(i), field));
            else {
                revisits++;
                assertEquals(first.get(v.id()), value(rows.get(i), field), "recorded answer for household " + v.id());
                int number = i + 1;
                assertTrue(plan.problems().stream().noneMatch(p -> p.submission() == number && p.field().equals(field)),
                        "the answer key must not claim a new change to replayed " + field);
            }
        }
        assertTrue(revisits >= 10, "many protected answers must be exercised");
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void codeFaultsDoNotChangeOrRecordReplayedTextIncludingCodesRecordedAtFirstVisit(String fault) throws Exception {
        var form = form7("<identity/><after/>", bind("identity", "string", "") + bind("after", "string", ""),
                input("identity"), input("after"));
        var cfg = config7(160, 2, 1, 2, true, "\"dontKnowCodes\":[\"DK\"],\"refuseCodes\":[\"REF\"],"
                + "\"fields\":{" + field("identity", "\"value\":\"ordinary\"") + ","
                + field("after", "\"value\":\"ordinary\"") + "}").withProblems(problems(0, fault));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals(fault) && p.field().equals("identity")
                && plan.visits().get(p.submission() - 1).attempt() == 1), "fault must exercise first-visit identity answers");
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals(fault) && p.field().equals("after")
                && plan.visits().get(p.submission() - 1).attempt() > 1), "post-availability answers remain eligible");
        assertReplayedAndUnrecorded(plan, rows, "identity");
    }

    @Test void otherFaultDoesNotReplaceAReplayedSelectOrAddAnAnswerKeyRecord() throws Exception {
        var form = form7("<identity/><specify/><after/><after_specify/>", bind("identity", "string", "")
                + bind("specify", "string", "relevant=\"/data/identity = 'O'\"")
                + bind("after", "string", "")
                + bind("after_specify", "string", "relevant=\"/data/after = 'O'\""),
                choices("identity", false, "a", "O") + input("specify"),
                choices("after", false, "a", "O") + input("after_specify"));
        var cfg = config7(160, 2, 1, 2, true, "\"otherCodes\":[\"O\"],\"fields\":{"
                + field("identity", "\"value\":\"a\"") + "," + field("after", "\"value\":\"a\"") + "}")
                .withProblems(problems(0, "other"));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals("other") && p.field().equals("after")));
        assertReplayedAndUnrecorded(plan, rows, "identity");
        // If first-visit 'other' made specify relevant, that text is itself identification.
        Map<String, String> first = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            var v = plan.visits().get(i);
            if (v.attempt() == 1) first.put(v.id(), value(rows.get(i), "specify"));
            else if (!first.get(v.id()).isEmpty()) assertEquals(first.get(v.id()), value(rows.get(i), "specify"));
        }
    }

    @Test void skipModulesCannotChangeAReplayedGateOrRecordItAgain() throws Exception {
        var form = form7("<identity/><module><a/><b/><c/></module>", bind("identity", "string", "")
                + "<bind nodeset=\"/data/module\" relevant=\"/data/identity = 'yes'\"/>"
                + bind("module/a", "string", "") + bind("module/b", "string", "") + bind("module/c", "string", ""),
                choices("identity", false, "yes", "no"), "<group ref=\"/data/module\">"
                + input("module/a") + input("module/b") + input("module/c") + "</group>");
        var cfg = config7(120, 2, 1, 2, true, "\"fields\":{" + field("identity", "\"value\":\"yes\"") + "}")
                .withProblems(problems(0, "skipModules"));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals("skipModules")
                && plan.visits().get(p.submission() - 1).attempt() == 1), "the gate really controls an eligible module");
        assertReplayedAndUnrecorded(plan, rows, "identity");
    }

    @Test void outlierTargetsLeaveReplayedNumbersAloneAndMayChangeQuestionsAfterAvailability() throws Exception {
        var form = form7("<identity/><after/>", bind("identity", "decimal", "") + bind("after", "decimal", ""),
                input("identity"), input("after"));
        var cfg = config7(40, 2, 1, 2, true, "\"decimalRange\":{\"min\":10,\"max\":20}")
                .withProblems(problems(1));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals("outlier") && p.field().equals("after")
                && plan.visits().get(p.submission() - 1).attempt() > 1));
        assertReplayedAndUnrecorded(plan, rows, "identity");
    }

    @Test void aRevisitWithOnlyAReplayedNumericCandidateGetsNoOutlierRecordForIt() throws Exception {
        var form = one("decimal", "", input("identity"));
        var cfg = config7(24, 2, 1, 2, true, "\"decimalRange\":{\"min\":10,\"max\":20}")
                .withProblems(problems(1));
        var plan = new Fieldwork(form, cfg);
        var rows = plan.walkAll();
        assertTrue(plan.problems().stream().anyMatch(p -> p.problem().equals("outlier") && p.field().equals("identity")
                && plan.visits().get(p.submission() - 1).attempt() == 1));
        assertReplayedAndUnrecorded(plan, rows, "identity");
    }
}
